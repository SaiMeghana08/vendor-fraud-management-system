package com.vendorfraud.bankchange;

import com.vendorfraud.audit.AuditAction;
import com.vendorfraud.audit.AuditService;
import com.vendorfraud.bankchange.dto.BankChangeRequestResponse;
import com.vendorfraud.bankchange.dto.CreateBankChangeRequestCommand;
import com.vendorfraud.common.exception.BusinessRuleException;
import com.vendorfraud.common.exception.ForbiddenOperationException;
import com.vendorfraud.common.exception.ResourceNotFoundException;
import com.vendorfraud.payment.PaymentHoldService;
import com.vendorfraud.security.CurrentUserService;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.User;
import com.vendorfraud.vendor.Vendor;
import com.vendorfraud.vendor.VendorBankAccount;
import com.vendorfraud.vendor.VendorService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creation and retrieval of bank-change requests.
 *
 * <p>Creating a request is one transaction that does five things and
 * either all of them happen or none do: persist the request with a
 * server-generated reference, derive the Maker from the JWT principal,
 * leave the live banking record untouched, place payment holds, and
 * assign the first approver.
 */
@Service
public class BankChangeRequestService {

    private static final Logger log = LoggerFactory.getLogger(BankChangeRequestService.class);

    private final BankChangeRequestRepository requestRepository;
    private final com.vendorfraud.reference.ReferenceGeneratorService referenceGenerator;
    private final VendorService vendorService;
    private final CurrentUserService currentUserService;
    private final ApproverAssignmentService approverAssignmentService;
    private final PaymentHoldService paymentHoldService;
    private final AuditService auditService;
    private final BankChangeQueryService queryService;

    public BankChangeRequestService(BankChangeRequestRepository requestRepository,
                                    com.vendorfraud.reference.ReferenceGeneratorService referenceGenerator,
                                    VendorService vendorService,
                                    CurrentUserService currentUserService,
                                    ApproverAssignmentService approverAssignmentService,
                                    PaymentHoldService paymentHoldService,
                                    AuditService auditService,
                                    BankChangeQueryService queryService) {
        this.requestRepository = requestRepository;
        this.referenceGenerator = referenceGenerator;
        this.vendorService = vendorService;
        this.currentUserService = currentUserService;
        this.approverAssignmentService = approverAssignmentService;
        this.paymentHoldService = paymentHoldService;
        this.auditService = auditService;
        this.queryService = queryService;
    }

    @Transactional
    public BankChangeRequestResponse create(CreateBankChangeRequestCommand command) {
        User maker = currentUserService.requireCurrentUser();
        if (!maker.hasRole(RoleName.MAKER)) {
            throw new ForbiddenOperationException("NOT_A_MAKER",
                    "Only a user holding the MAKER role may raise a bank-change request");
        }

        Vendor vendor = vendorService.requireActiveVendor(command.getVendorId());

        // Duplicate-request guard. The unique index on
        // (vendor_id, open_marker) is the backstop if two requests race.
        if (!requestRepository.findOpenByVendor(vendor.getId()).isEmpty()) {
            throw new BusinessRuleException("DUPLICATE_OPEN_REQUEST",
                    "An open bank-change request already exists for this vendor");
        }

        VendorBankAccount current = vendorService.findActiveBankAccount(vendor.getId()).orElse(null);

        if (current != null
                && current.getAccountReference().equalsIgnoreCase(command.getProposedAccountReference())
                && current.getRoutingCode().equalsIgnoreCase(command.getProposedRoutingCode())) {
            throw new BusinessRuleException("NO_CHANGE_PROPOSED",
                    "The proposed banking details are identical to the current ones");
        }

        String reference = referenceGenerator.nextBankChangeRequestReference();

        BankChangeRequest request = new BankChangeRequest(reference, vendor, maker, current,
                command.getProposedAccountHolderName().trim(),
                command.getProposedBankName().trim(),
                command.getProposedAccountReference().trim(),
                command.getProposedRoutingCode().trim(),
                command.getReason().trim(),
                command.getEvidenceReference().trim());

        requestRepository.save(request);
        requestRepository.flush();

        auditService.record(AuditAction.BANK_CHANGE_REQUEST_CREATED, "BANK_CHANGE_REQUEST",
                request.getId(), request.getStatus().name(),
                "Raised by maker %d for vendor %s; live banking details unchanged"
                        .formatted(maker.getId(), vendor.getVendorCode()));

        // Payment holds first: the money stops moving before anyone is
        // asked to look at the request.
        paymentHoldService.placeHoldsForPendingBankChange(request);

        approverAssignmentService.assignFirstApprover(request);

        log.info("Bank-change request {} created for vendor {} by maker {}",
                reference, vendor.getVendorCode(), maker.getUsername());
        return queryService.toResponse(request);
    }

    @Transactional(readOnly = true)
    public Page<BankChangeRequestResponse> listMyRequests(int page, int size) {
        Long makerId = currentUserService.currentUserId();
        return requestRepository.findByMakerIdOrderByIdDesc(makerId, PageRequest.of(page, size))
                .map(queryService::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BankChangeRequestResponse> listAll(BankChangeStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<BankChangeRequest> requests = status == null
                ? requestRepository.findAllByOrderByIdDesc(pageable)
                : requestRepository.findByStatusOrderByIdDesc(status, pageable);
        return requests.map(queryService::toResponse);
    }

    /**
     * A Maker may read their own requests; approvers, Accounts Payable
     * and Administrators may read any. A Maker asking for someone
     * else's request gets a 403 rather than a hint that it exists.
     */
    @Transactional(readOnly = true)
    public BankChangeRequestResponse getVisibleRequest(Long requestId) {
        BankChangeRequest request = requireRequest(requestId);
        User caller = currentUserService.requireCurrentUser();

        boolean privileged = caller.hasRole(RoleName.APPROVER)
                || caller.hasRole(RoleName.ACCOUNTS_PAYABLE)
                || caller.hasRole(RoleName.ADMINISTRATOR);
        boolean isOwner = request.getMaker().getId().equals(caller.getId());

        if (!privileged && !isOwner) {
            throw new ForbiddenOperationException("NOT_VISIBLE",
                    "You are not permitted to view this request");
        }
        return queryService.toResponse(request);
    }

    @Transactional(readOnly = true)
    public BankChangeRequest requireRequest(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("BankChangeRequest", requestId));
    }

    @Transactional(readOnly = true)
    public List<BankChangeRequestResponse> listBlocked() {
        return requestRepository
                .findByStatusOrderByIdDesc(BankChangeStatus.ACTIVATION_BLOCKED,
                        PageRequest.of(0, 200))
                .map(queryService::toResponse)
                .getContent();
    }
}
