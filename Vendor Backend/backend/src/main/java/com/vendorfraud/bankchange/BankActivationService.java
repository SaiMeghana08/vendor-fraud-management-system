package com.vendorfraud.bankchange;

import com.vendorfraud.audit.AuditAction;
import com.vendorfraud.audit.AuditService;
import com.vendorfraud.notification.NotificationService;
import com.vendorfraud.notification.NotificationType;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.User;
import com.vendorfraud.user.UserRepository;
import com.vendorfraud.vendor.BankAccountStatus;
import com.vendorfraud.vendor.VendorBankAccount;
import com.vendorfraud.vendor.VendorBankAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activation of approved banking details - the last gate in the
 * workflow.
 *
 * <p>Revalidation happens here, after cooling-off, not at approval
 * time. Conditions can change during the window: the vendor may be
 * deactivated, an approval may be missing, the window may not actually
 * have elapsed. Any failure moves the request to
 * {@code ACTIVATION_BLOCKED} for an exception decision rather than
 * forcing it through.
 *
 * <p>Activation is idempotent by construction: it only accepts a
 * request still in {@code COOLING_OFF} or {@code ACTIVATION_BLOCKED},
 * so a second attempt on an already-activated request is a no-op that
 * reports what happened.
 */
@Service
public class BankActivationService {

    private static final Logger log = LoggerFactory.getLogger(BankActivationService.class);

    private final VendorBankAccountRepository bankAccountRepository;
    private final BankChangeRequestRepository requestRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public BankActivationService(VendorBankAccountRepository bankAccountRepository,
                                 BankChangeRequestRepository requestRepository,
                                 ApprovalTaskRepository approvalTaskRepository,
                                 NotificationService notificationService,
                                 AuditService auditService,
                                 UserRepository userRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.requestRepository = requestRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    /** The outcome of one activation attempt. */
    public enum Outcome {
        ACTIVATED,
        BLOCKED,
        SKIPPED
    }

    /**
     * Revalidates and, if everything still holds, activates.
     *
     * <p>Runs in its own transaction so that one failing request in a
     * scheduler batch cannot roll back the requests processed
     * alongside it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome revalidateAndActivate(Long requestId) {
        Optional<BankChangeRequest> locked = requestRepository.findByIdForUpdate(requestId);
        if (locked.isEmpty()) {
            return Outcome.SKIPPED;
        }
        BankChangeRequest request = locked.get();

        // Another worker may have handled this between selection and
        // locking. Not an error; just nothing left to do.
        if (request.getStatus() != BankChangeStatus.COOLING_OFF
                && request.getStatus() != BankChangeStatus.ACTIVATION_BLOCKED) {
            log.debug("Request {} is {}; activation skipped", request.getRequestReference(),
                    request.getStatus());
            return Outcome.SKIPPED;
        }

        String failure = revalidate(request);
        if (failure != null) {
            if (request.getStatus() == BankChangeStatus.COOLING_OFF) {
                request.markActivationBlocked(failure);
                requestRepository.saveAndFlush(request);
                auditService.recordSystem(AuditAction.BANK_ACTIVATION_BLOCKED,
                        "BANK_CHANGE_REQUEST", request.getId(), request.getStatus().name(), failure);
                notifyExceptionWatchers(request, failure);
            }
            log.warn("Activation blocked for {}: {}", request.getRequestReference(), failure);
            return Outcome.BLOCKED;
        }

        // Supersede the old record, then insert the new one. Order
        // matters: the unique index permits only one active record per
        // vendor, so the release has to happen first.
        bankAccountRepository
                .findByVendorIdAndStatus(request.getVendor().getId(), BankAccountStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.supersede();
                    bankAccountRepository.saveAndFlush(existing);
                });

        VendorBankAccount activated = new VendorBankAccount(request.getVendor(),
                request.getProposedAccountHolderName(), request.getProposedBankName(),
                request.getProposedAccountReference(), request.getProposedRoutingCode(),
                request.getId());
        bankAccountRepository.saveAndFlush(activated);

        request.markActivated(activated);
        requestRepository.saveAndFlush(request);

        auditService.recordSystem(AuditAction.COOLING_OFF_COMPLETED, "BANK_CHANGE_REQUEST",
                request.getId(), request.getStatus().name(),
                "Cooling-off window closed at %s".formatted(request.getCoolingOffEndAt()));
        auditService.recordSystem(AuditAction.BANK_DETAILS_ACTIVATED, "BANK_CHANGE_REQUEST",
                request.getId(), request.getStatus().name(),
                ("Banking record %d activated for vendor %s; previous record retained as "
                        + "SUPERSEDED. Payment holds remain under Accounts Payable control.")
                        .formatted(activated.getId(), request.getVendor().getVendorCode()));

        notifyActivation(request);

        log.info("Activated banking details for vendor {} from request {}",
                request.getVendor().getVendorCode(), request.getRequestReference());
        return Outcome.ACTIVATED;
    }

    /**
     * Every condition that must still hold at activation time.
     *
     * @return null when activation may proceed, otherwise the reason it
     *         may not
     */
    private String revalidate(BankChangeRequest request) {
        if (!request.isCoolingOffElapsed(Instant.now())) {
            return "The cooling-off window has not elapsed";
        }
        if (!request.getVendor().isActive() || request.getVendor().getDeletedAt() != null) {
            return "The vendor is inactive or has been soft-deleted";
        }
        if (!request.getMaker().isActive()) {
            // Worth flagging, not worth voiding: the approvals stand on
            // their own. Recorded so an exception reviewer sees it.
            log.info("Maker {} of request {} is inactive at activation time",
                    request.getMaker().getId(), request.getRequestReference());
        }
        if (approvalTaskRepository.countApprovedForStage(request.getId(), ApprovalStage.FIRST) == 0) {
            return "First-stage approval is missing";
        }
        if (approvalTaskRepository.countApprovedForStage(request.getId(), ApprovalStage.SECOND) == 0) {
            return "Second-stage approval is missing";
        }
        List<ApprovalTask> approved = approvalTaskRepository
                .findByBankChangeRequestIdOrderByIdAsc(request.getId()).stream()
                .filter(t -> t.getStatus() == ApprovalTaskStatus.APPROVED)
                .toList();
        long distinctApprovers = approved.stream()
                .map(t -> t.getAssignee().getId())
                .distinct()
                .count();
        if (distinctApprovers < 2) {
            return "The two approvals were not made by two independent approvers";
        }
        if (approved.stream().anyMatch(
                t -> t.getAssignee().getId().equals(request.getMaker().getId()))) {
            return "An approval was recorded against the Maker of the request";
        }
        return null;
    }

    private void notifyActivation(BankChangeRequest request) {
        List<Long> recipients = new java.util.ArrayList<>(
                approvalTaskRepository.findAllInvolvedAssigneeIds(request.getId()));
        recipients.add(request.getMaker().getId());
        recipients.addAll(userRepository.findActiveByRole(RoleName.ACCOUNTS_PAYABLE).stream()
                .map(User::getId).toList());

        notificationService.notifyUsers(recipients.stream().distinct().toList(),
                NotificationType.BANK_DETAILS_ACTIVATED,
                "Vendor banking details activated",
                ("Request %s is complete: the approved banking details for %s are now active. "
                        + "Payment holds are unchanged and remain an Accounts Payable decision.")
                        .formatted(request.getRequestReference(), request.getVendor().getName()),
                "BANK_CHANGE_REQUEST", request.getId());
    }

    private void notifyExceptionWatchers(BankChangeRequest request, String failure) {
        List<Long> recipients = new java.util.ArrayList<>();
        recipients.addAll(userRepository.findActiveByRole(RoleName.ADMINISTRATOR).stream()
                .map(User::getId).toList());
        recipients.addAll(userRepository.findActiveByRole(RoleName.ACCOUNTS_PAYABLE).stream()
                .map(User::getId).toList());
        recipients.add(request.getMaker().getId());

        notificationService.notifyUsers(recipients.stream().distinct().toList(),
                NotificationType.ACTIVATION_BLOCKED,
                "Bank activation blocked",
                "Request %s could not be activated: %s"
                        .formatted(request.getRequestReference(), failure),
                "BANK_CHANGE_REQUEST", request.getId());
    }
}
