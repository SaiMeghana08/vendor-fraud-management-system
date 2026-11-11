package com.vendorfraud.payment;

import com.vendorfraud.audit.AuditAction;
import com.vendorfraud.audit.AuditService;
import com.vendorfraud.bankchange.BankChangeRequest;
import com.vendorfraud.bankchange.BankChangeRequestRepository;
import com.vendorfraud.common.exception.BusinessRuleException;
import com.vendorfraud.common.exception.ForbiddenOperationException;
import com.vendorfraud.common.exception.ResourceNotFoundException;
import com.vendorfraud.payment.dto.CreatePaymentCommand;
import com.vendorfraud.payment.dto.PaymentEligibilityResponse;
import com.vendorfraud.payment.dto.PaymentResponse;
import com.vendorfraud.reference.ReferenceGeneratorService;
import com.vendorfraud.security.CurrentUserService;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.User;
import com.vendorfraud.vendor.Vendor;
import com.vendorfraud.vendor.VendorBankAccount;
import com.vendorfraud.vendor.VendorService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment creation and processing, restricted to Accounts Payable.
 *
 * <p>Eligibility is evaluated from live state on every payment
 * operation - never from a flag written earlier - so a hold placed a
 * second ago is honoured.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentHoldService holdService;
    private final PaymentHoldRepository holdRepository;
    private final ReferenceGeneratorService referenceGenerator;
    private final VendorService vendorService;
    private final BankChangeRequestRepository bankChangeRequestRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentHoldService holdService,
                          PaymentHoldRepository holdRepository,
                          ReferenceGeneratorService referenceGenerator,
                          VendorService vendorService,
                          BankChangeRequestRepository bankChangeRequestRepository,
                          CurrentUserService currentUserService,
                          AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.holdService = holdService;
        this.holdRepository = holdRepository;
        this.referenceGenerator = referenceGenerator;
        this.vendorService = vendorService;
        this.bankChangeRequestRepository = bankChangeRequestRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional
    public PaymentResponse create(CreatePaymentCommand command) {
        User actor = requireAccountsPayable();
        Vendor vendor = vendorService.requireActiveVendor(command.getVendorId());

        VendorBankAccount payTo = vendorService.findActiveBankAccount(vendor.getId()).orElse(null);
        if (payTo == null) {
            throw new BusinessRuleException("VENDOR_HAS_NO_ACTIVE_BANK_ACCOUNT",
                    "The vendor has no active banking record to pay against");
        }

        // The backend generates the reference. The client cannot send
        // one - CreatePaymentCommand rejects the field outright.
        String reference = referenceGenerator.nextPaymentReference();

        Payment payment = new Payment(reference, vendor, payTo, command.getAmount(),
                command.getCurrency().toUpperCase(), command.getDueDate(),
                command.getDescription().trim(), actor.getId());
        paymentRepository.save(payment);
        paymentRepository.flush();

        auditService.record(AuditAction.PAYMENT_CREATED, "PAYMENT", payment.getId(),
                payment.getStatus().name(),
                "Payment %s created for vendor %s".formatted(reference, vendor.getVendorCode()));

        // A payment raised while a bank change is in flight is held
        // immediately rather than being left payable by timing luck.
        List<BankChangeRequest> openRequests = bankChangeRequestRepository
                .findOpenByVendor(vendor.getId());
        if (!openRequests.isEmpty()) {
            holdService.placeHoldForNewPayment(payment, openRequests.get(0));
        }

        log.info("Payment {} created by {}", reference, actor.getUsername());
        return toResponse(payment);
    }

    /**
     * Processes a payment. This is the gate that makes a hold mean
     * something: eligibility is recomputed here, inside the same
     * transaction and under a row lock, immediately before the state
     * change.
     */
    @Transactional
    public PaymentResponse process(Long paymentId) {
        User actor = requireAccountsPayable();

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        PaymentEligibilityResponse eligibility = evaluateEligibility(payment);
        if (!eligibility.eligible()) {
            auditService.record(AuditAction.PAYMENT_BLOCKED_BY_HOLD, "PAYMENT", payment.getId(),
                    payment.getStatus().name(),
                    "Processing blocked: %s".formatted(eligibility.reason()));
            throw new BusinessRuleException("PAYMENT_NOT_ELIGIBLE", eligibility.reason());
        }

        VendorBankAccount payTo = vendorService.findActiveBankAccount(payment.getVendor().getId())
                .orElseThrow(() -> new BusinessRuleException("VENDOR_HAS_NO_ACTIVE_BANK_ACCOUNT",
                        "The vendor has no active banking record to pay against"));

        payment.markProcessed(actor.getId(), payTo);
        paymentRepository.saveAndFlush(payment);

        auditService.record(AuditAction.PAYMENT_PROCESSED, "PAYMENT", payment.getId(),
                payment.getStatus().name(),
                "Processed by user %d against bank account %d".formatted(actor.getId(), payTo.getId()));

        log.info("Payment {} processed by {}", payment.getPaymentReference(), actor.getUsername());
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentEligibilityResponse checkEligibility(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        return evaluateEligibility(payment);
    }

    /**
     * The one definition of payment eligibility, used by the processing
     * path and by the UI alike so the two can never disagree.
     */
    private PaymentEligibilityResponse evaluateEligibility(Payment payment) {
        long activeHolds = holdRepository.countByPaymentIdAndStatus(payment.getId(), HoldStatus.ACTIVE);

        if (payment.getStatus() == PaymentStatus.PROCESSED) {
            return ineligible(payment, activeHolds, "The payment has already been processed");
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return ineligible(payment, activeHolds, "The payment has been cancelled");
        }
        if (activeHolds > 0) {
            return ineligible(payment, activeHolds,
                    "%d active payment hold(s) block this payment".formatted(activeHolds));
        }
        if (!payment.getVendor().isActive()) {
            return ineligible(payment, activeHolds, "The vendor is inactive");
        }
        Optional<VendorBankAccount> active = vendorService
                .findActiveBankAccount(payment.getVendor().getId());
        if (active.isEmpty()) {
            return ineligible(payment, activeHolds,
                    "The vendor has no active banking record to pay against");
        }
        // An open bank-change request keeps the vendor's payments frozen
        // even if, for any reason, no hold row exists for this payment.
        if (!bankChangeRequestRepository.findOpenByVendor(payment.getVendor().getId()).isEmpty()) {
            return ineligible(payment, activeHolds,
                    "The vendor has an open bank-change request");
        }
        return new PaymentEligibilityResponse(payment.getId(), payment.getPaymentReference(),
                true, null, activeHolds);
    }

    private PaymentEligibilityResponse ineligible(Payment payment, long activeHolds, String reason) {
        return new PaymentEligibilityResponse(payment.getId(), payment.getPaymentReference(),
                false, reason, activeHolds);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(PaymentStatus status, Long vendorId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Payment> payments;
        if (status != null) {
            payments = paymentRepository.findByStatusOrderByIdDesc(status, pageable);
        } else if (vendorId != null) {
            payments = paymentRepository.findByVendorIdOrderByIdDesc(vendorId, pageable);
        } else {
            payments = paymentRepository.findAllByOrderByIdDesc(pageable);
        }
        return payments.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long paymentId) {
        return toResponse(paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId)));
    }

    private PaymentResponse toResponse(Payment payment) {
        PaymentEligibilityResponse eligibility = evaluateEligibility(payment);
        return PaymentResponse.from(payment, holdService.listHoldsForPayment(payment.getId()),
                eligibility.eligible(), eligibility.reason(), eligibility.activeHoldCount());
    }

    private User requireAccountsPayable() {
        User actor = currentUserService.requireCurrentUser();
        if (!actor.hasRole(RoleName.ACCOUNTS_PAYABLE)) {
            throw new ForbiddenOperationException("NOT_ACCOUNTS_PAYABLE",
                    "Only Accounts Payable may create or manage payments");
        }
        return actor;
    }
}
