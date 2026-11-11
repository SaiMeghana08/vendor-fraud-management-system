package com.vendorfraud.override;

import com.vendorfraud.audit.AuditAction;
import com.vendorfraud.audit.AuditService;
import com.vendorfraud.bankchange.ApprovalStage;
import com.vendorfraud.bankchange.ApprovalTaskRepository;
import com.vendorfraud.bankchange.BankChangeRequest;
import com.vendorfraud.bankchange.BankChangeRequestRepository;
import com.vendorfraud.bankchange.BankChangeStatus;
import com.vendorfraud.common.exception.BusinessRuleException;
import com.vendorfraud.common.exception.ForbiddenOperationException;
import com.vendorfraud.common.exception.ResourceNotFoundException;
import com.vendorfraud.notification.NotificationService;
import com.vendorfraud.notification.NotificationType;
import com.vendorfraud.override.dto.EmergencyOverrideResponse;
import com.vendorfraud.override.dto.OverrideDecisionCommand;
import com.vendorfraud.override.dto.RequestOverrideCommand;
import com.vendorfraud.reference.ReferenceGeneratorService;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * The emergency override.
 *
 * <p>Requested by Accounts Payable, decided by an Administrator, and
 * never by the same person in both roles. Its one documented effect is
 * the cooling-off exception:
 *
 * <pre>
 *   COOLING_OFF (cooling_off_end_at in the future)
 *     --[override APPROVED]--&gt;
 *   COOLING_OFF (cooling_off_end_at = now, cooling_off_override_id set)
 *     --[scheduler revalidates, as for any other request]--&gt;
 *   ACTIVATED | ACTIVATION_BLOCKED
 * </pre>
 *
 * <p>The request does not jump to ACTIVATED. It becomes due, and the
 * scheduler then applies exactly the same revalidation it applies to
 * every other request. Payment holds are untouched.
 */
@Service
public class EmergencyOverrideService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyOverrideService.class);

    private final EmergencyOverrideRepository overrideRepository;
    private final BankChangeRequestRepository requestRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final ReferenceGeneratorService referenceGenerator;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public EmergencyOverrideService(EmergencyOverrideRepository overrideRepository,
                                    BankChangeRequestRepository requestRepository,
                                    ApprovalTaskRepository approvalTaskRepository,
                                    ReferenceGeneratorService referenceGenerator,
                                    CurrentUserService currentUserService,
                                    UserRepository userRepository,
                                    NotificationService notificationService,
                                    AuditService auditService) {
        this.overrideRepository = overrideRepository;
        this.requestRepository = requestRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.referenceGenerator = referenceGenerator;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional
    public EmergencyOverrideResponse request(RequestOverrideCommand command) {
        User requester = currentUserService.requireCurrentUser();
        if (!requester.hasRole(RoleName.ACCOUNTS_PAYABLE)) {
            throw new ForbiddenOperationException("NOT_ACCOUNTS_PAYABLE",
                    "Only Accounts Payable may request an emergency override");
        }

        BankChangeRequest request = requestRepository.findById(command.bankChangeRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("BankChangeRequest",
                        command.bankChangeRequestId()));

        // Dual approval is a precondition, not something an override can
        // substitute for.
        requireCompletedDualApproval(request);

        if (request.getStatus() != BankChangeStatus.COOLING_OFF) {
            throw new BusinessRuleException("OVERRIDE_NOT_APPLICABLE",
                    ("An emergency override applies only to a request in cooling-off. "
                            + "Request %s is %s.").formatted(request.getRequestReference(),
                            request.getStatus()));
        }
        if (overrideRepository.existsByBankChangeRequestIdAndStatus(request.getId(),
                OverrideStatus.PENDING)) {
            throw new BusinessRuleException("DUPLICATE_OVERRIDE",
                    "An emergency override for this request is already awaiting a decision");
        }

        String reference = referenceGenerator.nextEmergencyOverrideReference();
        EmergencyOverride override = new EmergencyOverride(reference, request.getId(),
                requester.getId(), command.justification().trim(),
                command.evidenceReference().trim());
        overrideRepository.save(override);
        overrideRepository.flush();

        auditService.record(AuditAction.EMERGENCY_OVERRIDE_REQUESTED, "EMERGENCY_OVERRIDE",
                override.getId(), request.getStatus().name(),
                "Cooling-off exception requested for %s by user %d: %s"
                        .formatted(request.getRequestReference(), requester.getId(),
                                command.justification().trim()));

        notificationService.notifyUsers(administratorIds(),
                NotificationType.EMERGENCY_OVERRIDE_REQUESTED,
                "Emergency override awaiting your decision",
                "%s requests a cooling-off exception for request %s."
                        .formatted(requester.getFullName(), request.getRequestReference()),
                "EMERGENCY_OVERRIDE", override.getId());

        log.info("Emergency override {} requested for {} by {}", reference,
                request.getRequestReference(), requester.getUsername());
        return toResponse(override);
    }

    /**
     * The Administrator's decision. Approval brings the cooling-off
     * deadline forward to now; it changes nothing else.
     */
    @Transactional
    public EmergencyOverrideResponse decide(Long overrideId, OverrideDecisionCommand command) {
        User administrator = currentUserService.requireCurrentUser();
        if (!administrator.hasRole(RoleName.ADMINISTRATOR)) {
            throw new ForbiddenOperationException("NOT_ADMINISTRATOR",
                    "Only an Administrator may decide an emergency override");
        }

        EmergencyOverride override = overrideRepository.findById(overrideId)
                .orElseThrow(() -> new ResourceNotFoundException("EmergencyOverride", overrideId));

        // The requester and the deciding Administrator must be
        // different people, whatever roles one account happens to hold.
        if (override.getRequestedBy().equals(administrator.getId())) {
            auditService.record(AuditAction.SEPARATION_OF_DUTIES_VIOLATION_BLOCKED,
                    "EMERGENCY_OVERRIDE", override.getId(), override.getStatus().name(),
                    "User %d attempted to decide their own override request"
                            .formatted(administrator.getId()));
            throw new ForbiddenOperationException("OVERRIDE_SELF_APPROVAL",
                    "The requester of an emergency override may not decide it");
        }

        BankChangeRequest request = requestRepository
                .findByIdForUpdate(override.getBankChangeRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("BankChangeRequest",
                        override.getBankChangeRequestId()));

        boolean approved = Boolean.TRUE.equals(command.approved());

        if (approved) {
            // Re-check at decision time: the request may have moved on
            // while the override sat in the queue.
            requireCompletedDualApproval(request);
            if (request.getStatus() != BankChangeStatus.COOLING_OFF) {
                throw new BusinessRuleException("OVERRIDE_NOT_APPLICABLE",
                        ("Request %s is %s; a cooling-off exception can no longer be applied.")
                                .formatted(request.getRequestReference(), request.getStatus()));
            }
        }

        override.decide(approved, administrator.getId(), command.comment());
        overrideRepository.saveAndFlush(override);

        if (approved) {
            request.applyCoolingOffException(override.getId());
            requestRepository.saveAndFlush(request);
        }

        auditService.record(approved
                        ? AuditAction.EMERGENCY_OVERRIDE_APPROVED
                        : AuditAction.EMERGENCY_OVERRIDE_REJECTED,
                "EMERGENCY_OVERRIDE", override.getId(), request.getStatus().name(),
                approved
                        ? ("Cooling-off exception approved by user %d. Cooling-off now due; "
                                + "dual approval unchanged and payment holds NOT released.")
                                .formatted(administrator.getId())
                        : "Override rejected by user %d: %s".formatted(administrator.getId(),
                                command.comment()));

        List<Long> recipients = new java.util.ArrayList<>();
        recipients.add(override.getRequestedBy());
        recipients.add(request.getMaker().getId());
        notificationService.notifyUsers(recipients.stream().distinct().toList(),
                NotificationType.EMERGENCY_OVERRIDE_DECIDED,
                "Emergency override %s".formatted(approved ? "approved" : "rejected"),
                approved
                        ? ("Override %s was approved. Request %s is now due for revalidation. "
                                + "Payment holds remain in place.")
                                .formatted(override.getOverrideReference(),
                                        request.getRequestReference())
                        : "Override %s was rejected.".formatted(override.getOverrideReference()),
                "EMERGENCY_OVERRIDE", override.getId());

        log.info("Emergency override {} {} by {}", override.getOverrideReference(),
                approved ? "approved" : "rejected", administrator.getUsername());
        return toResponse(override);
    }

    private void requireCompletedDualApproval(BankChangeRequest request) {
        long first = approvalTaskRepository.countApprovedForStage(request.getId(), ApprovalStage.FIRST);
        long second = approvalTaskRepository.countApprovedForStage(request.getId(), ApprovalStage.SECOND);
        if (first == 0 || second == 0) {
            throw new BusinessRuleException("DUAL_APPROVAL_INCOMPLETE",
                    ("An emergency override requires two completed independent approvals. "
                            + "Request %s does not have them.")
                            .formatted(request.getRequestReference()));
        }
    }

    @Transactional(readOnly = true)
    public Page<EmergencyOverrideResponse> list(OverrideStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<EmergencyOverride> overrides = status == null
                ? overrideRepository.findAllByOrderByIdDesc(pageable)
                : overrideRepository.findByStatusOrderByIdDesc(status, pageable);
        return overrides.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EmergencyOverrideResponse get(Long overrideId) {
        return toResponse(overrideRepository.findById(overrideId)
                .orElseThrow(() -> new ResourceNotFoundException("EmergencyOverride", overrideId)));
    }

    private EmergencyOverrideResponse toResponse(EmergencyOverride override) {
        String requestReference = requestRepository.findById(override.getBankChangeRequestId())
                .map(BankChangeRequest::getRequestReference)
                .orElse(null);
        String requesterName = userRepository.findById(override.getRequestedBy())
                .map(User::getFullName)
                .orElse(null);
        String administratorName = override.getDecidedBy() == null ? null
                : userRepository.findById(override.getDecidedBy())
                        .map(User::getFullName)
                        .orElse(null);
        return EmergencyOverrideResponse.from(override, requestReference, requesterName,
                administratorName);
    }

    private List<Long> administratorIds() {
        return userRepository.findActiveByRole(RoleName.ADMINISTRATOR).stream()
                .map(User::getId)
                .toList();
    }
}
