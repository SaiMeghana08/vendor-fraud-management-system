package com.vendorfraud.payment;

import com.vendorfraud.audit.AuditAction;
import com.vendorfraud.audit.AuditService;
import com.vendorfraud.bankchange.BankChangeRequest;
import com.vendorfraud.common.exception.BusinessRuleException;
import com.vendorfraud.common.exception.ResourceNotFoundException;
import com.vendorfraud.notification.NotificationService;
import com.vendorfraud.notification.NotificationType;
import com.vendorfraud.payment.dto.PaymentHoldResponse;
import com.vendorfraud.security.CurrentUserService;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.User;
import com.vendorfraud.user.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment holds - the control that stops money moving while a vendor's
 * banking details are in question.
 *
 * <p>Holds are deliberately decoupled from the bank-change workflow's
 * own outcome. Activation does not release them. Rejection does not
 * release them. An approved emergency override does not release them.
 * Only a justified Accounts Payable release does, and that release
 * re-checks live state before it takes effect.
 */
@Service
public class PaymentHoldService {

    private static final Logger log = LoggerFactory.getLogger(PaymentHoldService.class);

    private final PaymentHoldRepository holdRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    public PaymentHoldService(PaymentHoldRepository holdRepository,
                              PaymentRepository paymentRepository,
                              NotificationService notificationService,
                              AuditService auditService,
                              CurrentUserService currentUserService,
                              UserRepository userRepository) {
        this.holdRepository = holdRepository;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
    }

    /**
     * Places a hold on every payable payment of the vendor named in a
     * newly created bank-change request.
     *
     * <p>Idempotent: a payment that already carries an active hold for
     * this request is skipped, so a retried creation never doubles up.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<PaymentHold> placeHoldsForPendingBankChange(BankChangeRequest request) {
        List<Payment> payments = paymentRepository
                .findHoldablePaymentsByVendor(request.getVendor().getId());

        String reason = "Pending bank-change request %s for vendor %s"
                .formatted(request.getRequestReference(), request.getVendor().getVendorCode());

        List<PaymentHold> created = new java.util.ArrayList<>();
        for (Payment payment : payments) {
            boolean alreadyHeld = holdRepository
                    .findByPaymentIdAndBankChangeRequestIdAndStatus(
                            payment.getId(), request.getId(), HoldStatus.ACTIVE)
                    .isPresent();
            if (alreadyHeld) {
                continue;
            }
            PaymentHold hold = new PaymentHold(payment.getId(), request.getId(), reason);
            holdRepository.save(hold);
            payment.markOnHold();
            paymentRepository.save(payment);
            created.add(hold);

            auditService.record(AuditAction.PAYMENT_HOLD_CREATED, "PAYMENT_HOLD", hold.getId(),
                    payment.getStatus().name(),
                    "Hold placed on payment %s: %s".formatted(payment.getPaymentReference(), reason));
        }
        holdRepository.flush();
        paymentRepository.flush();

        if (!created.isEmpty()) {
            notifyAccountsPayable(NotificationType.PAYMENT_HELD,
                    "Payments held for a pending bank change",
                    "%d payment(s) for vendor %s were held pending request %s."
                            .formatted(created.size(), request.getVendor().getName(),
                                    request.getRequestReference()),
                    "BANK_CHANGE_REQUEST", request.getId());
        }

        log.info("Placed {} payment hold(s) for request {}", created.size(),
                request.getRequestReference());
        return created;
    }

    /**
     * Places a hold on a single payment created while a bank-change
     * request for its vendor is still open.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void placeHoldForNewPayment(Payment payment, BankChangeRequest openRequest) {
        String reason = "Vendor has an open bank-change request %s"
                .formatted(openRequest.getRequestReference());
        PaymentHold hold = new PaymentHold(payment.getId(), openRequest.getId(), reason);
        holdRepository.save(hold);
        payment.markOnHold();
        paymentRepository.save(payment);
        auditService.record(AuditAction.PAYMENT_HOLD_CREATED, "PAYMENT_HOLD", hold.getId(),
                payment.getStatus().name(), reason);
        log.info("New payment {} held: {}", payment.getPaymentReference(), reason);
    }

    /**
     * Releases one hold.
     *
     * <p>Every condition is re-checked at release time, not at request
     * time: the actor must hold ACCOUNTS_PAYABLE, the hold must still be
     * active, and the payment must still exist and not be terminal.
     * Releasing the last active hold returns the payment to a payable
     * state; while any other hold remains, the payment stays on hold.
     */
    @Transactional
    public PaymentHoldResponse releaseHold(Long holdId, String justification) {
        User actor = currentUserService.requireCurrentUser();
        if (!actor.hasRole(RoleName.ACCOUNTS_PAYABLE)) {
            throw new com.vendorfraud.common.exception.ForbiddenOperationException(
                    "NOT_ACCOUNTS_PAYABLE",
                    "Only Accounts Payable may release a payment hold");
        }
        if (justification == null || justification.trim().length() < 10) {
            throw new BusinessRuleException("RELEASE_JUSTIFICATION_REQUIRED",
                    "A release justification of at least 10 characters is required");
        }

        PaymentHold hold = holdRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentHold", holdId));

        Payment payment = paymentRepository.findByIdForUpdate(hold.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", hold.getPaymentId()));

        if (payment.getStatus().isTerminal()) {
            throw new BusinessRuleException("PAYMENT_TERMINAL",
                    "Payment %s is %s; releasing a hold on it has no effect"
                            .formatted(payment.getPaymentReference(), payment.getStatus()));
        }

        hold.release(actor.getId(), justification.trim());
        holdRepository.saveAndFlush(hold);

        long remaining = holdRepository.countByPaymentIdAndStatus(payment.getId(), HoldStatus.ACTIVE);
        if (remaining == 0) {
            payment.markPayable();
            paymentRepository.saveAndFlush(payment);
        }

        auditService.record(AuditAction.PAYMENT_HOLD_RELEASED, "PAYMENT_HOLD", hold.getId(),
                payment.getStatus().name(),
                "Released by user %d; %d active hold(s) remain. Justification: %s"
                        .formatted(actor.getId(), remaining, justification.trim()));

        notifyAccountsPayable(NotificationType.PAYMENT_HOLD_RELEASED,
                "Payment hold released",
                "A hold on payment %s was released by %s. %d active hold(s) remain."
                        .formatted(payment.getPaymentReference(), actor.getFullName(), remaining),
                "PAYMENT", payment.getId());

        log.info("Hold {} on payment {} released by {}; {} remaining",
                holdId, payment.getPaymentReference(), actor.getUsername(), remaining);
        return PaymentHoldResponse.from(hold);
    }

    @Transactional(readOnly = true)
    public Page<PaymentHoldResponse> listActiveHolds(int page, int size) {
        return holdRepository.findByStatusOrderByIdDesc(HoldStatus.ACTIVE, PageRequest.of(page, size))
                .map(PaymentHoldResponse::from);
    }

    @Transactional(readOnly = true)
    public List<PaymentHoldResponse> listHoldsForPayment(Long paymentId) {
        return holdRepository.findByPaymentIdOrderByIdDesc(paymentId).stream()
                .map(PaymentHoldResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countActiveHolds(Long paymentId) {
        return holdRepository.countByPaymentIdAndStatus(paymentId, HoldStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveHoldForRequest(Long requestId) {
        return holdRepository.existsByBankChangeRequestIdAndStatus(requestId, HoldStatus.ACTIVE);
    }

    private void notifyAccountsPayable(NotificationType type, String title, String message,
                                       String referenceType, Long referenceId) {
        List<Long> recipients = userRepository.findActiveByRole(RoleName.ACCOUNTS_PAYABLE).stream()
                .map(User::getId)
                .toList();
        notificationService.notifyUsers(recipients, type, title, message, referenceType, referenceId);
    }
}
