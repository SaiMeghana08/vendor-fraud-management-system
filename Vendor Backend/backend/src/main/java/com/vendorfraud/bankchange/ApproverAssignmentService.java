package com.vendorfraud.bankchange;

import com.vendorfraud.audit.AuditAction;
import com.vendorfraud.audit.AuditService;
import com.vendorfraud.common.exception.BusinessRuleException;
import com.vendorfraud.common.exception.ForbiddenOperationException;
import com.vendorfraud.notification.NotificationService;
import com.vendorfraud.notification.NotificationType;
import com.vendorfraud.security.CurrentUserService;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.User;
import com.vendorfraud.user.UserRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chooses approvers. Nothing outside this class ever decides who
 * approves a request - not the frontend, not the Maker, not the request
 * body.
 *
 * <p>Selection is deterministic: among eligible approvers, the one with
 * the fewest pending tasks wins, and ties break on the lowest user id.
 * The same inputs always produce the same assignment, which is what
 * makes the behaviour testable and an assignment reviewable after the
 * fact.
 *
 * <p>Eligibility, in order:
 * <ol>
 *   <li>active user holding the APPROVER role;</li>
 *   <li>not the Maker of this request;</li>
 *   <li>for the second stage, nobody who has already held or decided
 *       any stage of this request.</li>
 * </ol>
 */
@Service
public class ApproverAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(ApproverAssignmentService.class);

    private final UserRepository userRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final ApprovalAssignmentHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public ApproverAssignmentService(UserRepository userRepository,
                                     ApprovalTaskRepository approvalTaskRepository,
                                     ApprovalAssignmentHistoryRepository historyRepository,
                                     NotificationService notificationService,
                                     AuditService auditService,
                                     CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    /**
     * Assigns the first-stage approver at creation time.
     *
     * <p>Runs inside the caller's transaction: if request creation rolls
     * back, so does the assignment, its history row and its
     * notification.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovalTask assignFirstApprover(BankChangeRequest request) {
        guardNoPendingTask(request, ApprovalStage.FIRST);
        Set<Long> excluded = new HashSet<>();
        excluded.add(request.getMaker().getId());
        User chosen = selectApprover(excluded, ApprovalStage.FIRST, request);
        return createTask(request, ApprovalStage.FIRST, chosen,
                NotificationType.FIRST_APPROVAL_ASSIGNED);
    }

    /**
     * Assigns the second-stage approver, after - and only after - the
     * first stage has been approved. The exclusion set is built from
     * everyone who has ever been assigned to this request, so the same
     * person can never approve both stages even through a reassignment.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovalTask assignSecondApprover(BankChangeRequest request) {
        if (approvalTaskRepository.countApprovedForStage(request.getId(), ApprovalStage.FIRST) == 0) {
            throw new BusinessRuleException("FIRST_APPROVAL_NOT_COMPLETE",
                    "The second approver cannot be assigned before first-stage approval");
        }
        guardNoPendingTask(request, ApprovalStage.SECOND);

        Set<Long> excluded = new HashSet<>(
                approvalTaskRepository.findAllInvolvedAssigneeIds(request.getId()));
        excluded.add(request.getMaker().getId());

        User chosen = selectApprover(excluded, ApprovalStage.SECOND, request);
        return createTask(request, ApprovalStage.SECOND, chosen,
                NotificationType.SECOND_APPROVAL_ASSIGNED);
    }

    /**
     * Reassigns a pending task to a named replacement. Only Accounts
     * Payable and Administrator reach this method; the role check is
     * enforced by the calling controller, and every eligibility rule is
     * re-checked here.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovalTask reassign(ApprovalTask task, Long replacementUserId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("REASSIGNMENT_REASON_REQUIRED",
                    "A reassignment reason is mandatory");
        }
        if (!task.isPending()) {
            throw new BusinessRuleException("TASK_NOT_PENDING",
                    "Only a pending approval task can be reassigned");
        }

        BankChangeRequest request = task.getBankChangeRequest();
        User replacement = userRepository.findById(replacementUserId)
                .orElseThrow(() -> new BusinessRuleException("REPLACEMENT_NOT_FOUND",
                        "The replacement user does not exist"));

        if (!replacement.isActive()) {
            throw new BusinessRuleException("REPLACEMENT_INACTIVE",
                    "The replacement user is not active");
        }
        if (!replacement.hasRole(RoleName.APPROVER)) {
            throw new BusinessRuleException("REPLACEMENT_NOT_APPROVER",
                    "The replacement user does not hold the APPROVER role");
        }
        if (replacement.getId().equals(request.getMaker().getId())) {
            throw new ForbiddenOperationException("REPLACEMENT_IS_MAKER",
                    "The Maker of a request can never be an approver on it");
        }
        if (replacement.getId().equals(task.getAssignee().getId())) {
            throw new BusinessRuleException("REPLACEMENT_IS_CURRENT_ASSIGNEE",
                    "The replacement is already assigned to this task");
        }
        List<Long> involved = approvalTaskRepository.findAllInvolvedAssigneeIds(request.getId());
        if (involved.contains(replacement.getId())) {
            throw new ForbiddenOperationException("REPLACEMENT_ALREADY_INVOLVED",
                    "The replacement has already held or decided a stage of this request");
        }

        Long previousAssigneeId = task.getAssignee().getId();
        ApprovalStage stage = task.getStage();
        task.closeForReassignment(reason);
        approvalTaskRepository.saveAndFlush(task);

        ApprovalTask newTask = new ApprovalTask(request, stage, replacement);
        approvalTaskRepository.save(newTask);
        approvalTaskRepository.flush();

        historyRepository.save(new ApprovalAssignmentHistory(newTask.getId(),
                AssignmentAction.REASSIGNED, previousAssigneeId, replacement.getId(),
                currentUserService.currentPrincipal().map(p -> p.getId()).orElse(null), reason));

        notificationService.notifyUser(replacement.getId(),
                NotificationType.APPROVAL_TASK_REASSIGNED,
                "Approval task reassigned to you",
                "You are now the %s-stage approver for request %s."
                        .formatted(stage.name().toLowerCase(), request.getRequestReference()),
                "BANK_CHANGE_REQUEST", request.getId());
        notificationService.notifyUser(previousAssigneeId,
                NotificationType.APPROVAL_TASK_REASSIGNED,
                "Approval task reassigned away",
                "Request %s is no longer assigned to you."
                        .formatted(request.getRequestReference()),
                "BANK_CHANGE_REQUEST", request.getId());

        auditService.record(AuditAction.APPROVAL_TASK_REASSIGNED, "APPROVAL_TASK",
                newTask.getId(), request.getStatus().name(),
                "Reassigned from user %d to user %d: %s"
                        .formatted(previousAssigneeId, replacement.getId(), reason));

        log.info("Approval task for request {} stage {} reassigned from {} to {}",
                request.getRequestReference(), stage, previousAssigneeId, replacement.getId());
        return newTask;
    }

    /** Candidate list for the reassignment dialog. */
    @Transactional(readOnly = true)
    public List<User> eligibleReplacements(BankChangeRequest request) {
        Set<Long> excluded = new HashSet<>(
                approvalTaskRepository.findAllInvolvedAssigneeIds(request.getId()));
        excluded.add(request.getMaker().getId());
        return userRepository.findActiveByRole(RoleName.APPROVER).stream()
                .filter(u -> !excluded.contains(u.getId()))
                .toList();
    }

    // ----------------------------------------------------------------- //

    private ApprovalTask createTask(BankChangeRequest request, ApprovalStage stage, User approver,
                                    NotificationType notificationType) {
        ApprovalTask task = new ApprovalTask(request, stage, approver);
        approvalTaskRepository.save(task);
        approvalTaskRepository.flush();

        historyRepository.save(new ApprovalAssignmentHistory(task.getId(),
                AssignmentAction.ASSIGNED, null, approver.getId(), null,
                "Automatic %s-stage assignment".formatted(stage.name().toLowerCase())));

        notificationService.notifyUser(approver.getId(), notificationType,
                "%s approval assigned to you".formatted(
                        stage == ApprovalStage.FIRST ? "First" : "Second"),
                "Request %s for vendor %s is waiting for your decision."
                        .formatted(request.getRequestReference(), request.getVendor().getName()),
                "BANK_CHANGE_REQUEST", request.getId());

        auditService.record(AuditAction.APPROVAL_TASK_ASSIGNED, "APPROVAL_TASK", task.getId(),
                request.getStatus().name(),
                "%s-stage approver automatically assigned: user %d"
                        .formatted(stage.name().toLowerCase(), approver.getId()));

        log.info("Assigned {} approver {} to request {}", stage, approver.getId(),
                request.getRequestReference());
        return task;
    }

    /**
     * Deterministic least-pending-task selection.
     *
     * <p>An empty eligible set is a workflow stop, not a bypass: the
     * request stays exactly where it is and someone has to add or
     * reactivate an approver. Nothing auto-approves and no stage is
     * skipped.
     */
    private User selectApprover(Set<Long> excludedUserIds, ApprovalStage stage,
                                BankChangeRequest request) {
        List<User> eligible = userRepository.findActiveByRole(RoleName.APPROVER).stream()
                .filter(u -> !excludedUserIds.contains(u.getId()))
                .toList();

        if (eligible.isEmpty()) {
            log.warn("No eligible approver for {} stage of request {}", stage,
                    request.getRequestReference());
            throw new BusinessRuleException("NO_ELIGIBLE_APPROVER",
                    ("No independent approver is available for the %s approval stage. "
                            + "The request remains at its current stage until an eligible "
                            + "approver is available.").formatted(stage.name().toLowerCase()));
        }

        Map<Long, Long> pendingCounts = approvalTaskRepository.countPendingByAssignee().stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()));

        return eligible.stream()
                .min(Comparator
                        .comparingLong((User u) -> pendingCounts.getOrDefault(u.getId(), 0L))
                        .thenComparingLong(User::getId))
                .orElseThrow();
    }

    /** Prevents a duplicate pending task for the same stage. */
    private void guardNoPendingTask(BankChangeRequest request, ApprovalStage stage) {
        if (request.getId() != null && approvalTaskRepository
                .findByBankChangeRequestIdAndStageAndStatus(request.getId(), stage,
                        ApprovalTaskStatus.PENDING)
                .isPresent()) {
            throw new BusinessRuleException("DUPLICATE_ASSIGNMENT",
                    "A pending %s-stage task already exists for this request"
                            .formatted(stage.name().toLowerCase()));
        }
    }
}
