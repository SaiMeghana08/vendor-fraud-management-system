package com.vendorfraud.bankchange;

import com.vendorfraud.audit.AuditAction;
import com.vendorfraud.audit.AuditService;
import com.vendorfraud.bankchange.dto.ApprovalDecisionCommand;
import com.vendorfraud.bankchange.dto.ApprovalTaskResponse;
import com.vendorfraud.bankchange.dto.BankChangeRequestResponse;
import com.vendorfraud.bankchange.dto.ReassignmentCommand;
import com.vendorfraud.common.exception.BusinessRuleException;
import com.vendorfraud.common.exception.ForbiddenOperationException;
import com.vendorfraud.common.exception.InvalidStateTransitionException;
import com.vendorfraud.common.exception.ResourceNotFoundException;
import com.vendorfraud.config.CoolingOffProperties;
import com.vendorfraud.notification.NotificationService;
import com.vendorfraud.notification.NotificationType;
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
 * The approval stages.
 *
 * <p>Separation of duties is enforced here, against the authenticated
 * principal and the persisted task - never against anything in the
 * request body. A decision takes a pessimistic write lock on the task
 * and on the request, so two approvers clicking at the same moment
 * cannot both succeed.
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalTaskRepository taskRepository;
    private final BankChangeRequestRepository requestRepository;
    private final ApproverAssignmentService assignmentService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final CoolingOffProperties coolingOffProperties;
    private final BankChangeQueryService queryService;
    private final UserRepository userRepository;

    public ApprovalService(ApprovalTaskRepository taskRepository,
                           BankChangeRequestRepository requestRepository,
                           ApproverAssignmentService assignmentService,
                           NotificationService notificationService,
                           AuditService auditService,
                           CurrentUserService currentUserService,
                           CoolingOffProperties coolingOffProperties,
                           BankChangeQueryService queryService,
                           UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.requestRepository = requestRepository;
        this.assignmentService = assignmentService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.coolingOffProperties = coolingOffProperties;
        this.queryService = queryService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<ApprovalTaskResponse> listMyPendingTasks(int page, int size) {
        Long me = currentUserService.currentUserId();
        return taskRepository.findByAssigneeIdAndStatusOrderByIdDesc(me,
                        ApprovalTaskStatus.PENDING, PageRequest.of(page, size))
                .map(ApprovalTaskResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalTaskResponse> listMyDecisionHistory(int page, int size) {
        Long me = currentUserService.currentUserId();
        return taskRepository.findByAssigneeIdOrderByIdDesc(me, PageRequest.of(page, size))
                .map(ApprovalTaskResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalTaskResponse> listAllPendingTasks(int page, int size) {
        return taskRepository.findByStatusOrderByIdAsc(ApprovalTaskStatus.PENDING,
                        PageRequest.of(page, size))
                .map(ApprovalTaskResponse::from);
    }

    /**
     * Records an approve or reject decision on one task.
     *
     * <p>Idempotency: a repeated submission of the same decision finds
     * the task no longer PENDING and is rejected with 409 rather than
     * silently applied twice. The caller can safely retry a request
     * whose response it never saw - the second attempt either lands on
     * a still-pending task or reports the conflict.
     */
    @Transactional
    public BankChangeRequestResponse decide(Long taskId, ApprovalDecisionCommand command) {
        User actor = currentUserService.requireCurrentUser();

        ApprovalTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalTask", taskId));

        // Lock the request too: the stage transition touches both rows.
        BankChangeRequest request = requestRepository
                .findByIdForUpdate(task.getBankChangeRequest().getId())
                .orElseThrow(() -> new ResourceNotFoundException("BankChangeRequest",
                        task.getBankChangeRequest().getId()));

        validateDecisionEligibility(task, request, actor, command);

        boolean approved = Boolean.TRUE.equals(command.approved());
        if (approved) {
            task.approve(command.comment());
        } else {
            task.reject(command.comment());
        }
        taskRepository.saveAndFlush(task);

        if (!approved) {
            return handleRejection(task, request, command.comment());
        }
        return task.getStage() == ApprovalStage.FIRST
                ? handleFirstApproval(task, request, command.comment())
                : handleSecondApproval(task, request, command.comment());
    }

    private void validateDecisionEligibility(ApprovalTask task, BankChangeRequest request,
                                             User actor, ApprovalDecisionCommand command) {
        if (!actor.hasRole(RoleName.APPROVER)) {
            throw new ForbiddenOperationException("NOT_AN_APPROVER",
                    "Only a user holding the APPROVER role may decide an approval task");
        }

        // A Maker can never approve their own request, whatever roles
        // they also hold.
        if (request.getMaker().getId().equals(actor.getId())) {
            auditService.record(AuditAction.SEPARATION_OF_DUTIES_VIOLATION_BLOCKED,
                    "BANK_CHANGE_REQUEST", request.getId(), request.getStatus().name(),
                    "Maker %d attempted to approve their own request".formatted(actor.getId()));
            throw new ForbiddenOperationException("MAKER_CANNOT_APPROVE",
                    "The Maker of a request may never approve it");
        }

        // Only the assigned approver decides.
        if (!task.getAssignee().getId().equals(actor.getId())) {
            auditService.record(AuditAction.SEPARATION_OF_DUTIES_VIOLATION_BLOCKED,
                    "APPROVAL_TASK", task.getId(), request.getStatus().name(),
                    "User %d attempted to decide a task assigned to user %d"
                            .formatted(actor.getId(), task.getAssignee().getId()));
            throw new ForbiddenOperationException("NOT_ASSIGNED_APPROVER",
                    "This approval task is assigned to a different approver");
        }

        if (!task.isPending()) {
            throw new InvalidStateTransitionException("TASK_ALREADY_DECIDED",
                    "This approval task has already been decided");
        }

        // The stage must match where the request actually is: a second
        // approval submitted before the first is out of order.
        BankChangeStatus expected = task.getStage() == ApprovalStage.FIRST
                ? BankChangeStatus.PENDING_FIRST_APPROVAL
                : BankChangeStatus.PENDING_SECOND_APPROVAL;
        if (request.getStatus() != expected) {
            throw new InvalidStateTransitionException("OUT_OF_ORDER_APPROVAL",
                    "Request %s is %s; a %s-stage decision is not valid now".formatted(
                            request.getRequestReference(), request.getStatus(),
                            task.getStage().name().toLowerCase()));
        }

        // The same person approving both stages is impossible by
        // construction, but a direct check costs nothing and documents
        // the rule.
        if (task.getStage() == ApprovalStage.SECOND) {
            boolean alreadyApprovedFirst = taskRepository
                    .findByBankChangeRequestIdOrderByIdAsc(request.getId()).stream()
                    .anyMatch(t -> t.getStage() == ApprovalStage.FIRST
                            && t.getStatus() == ApprovalTaskStatus.APPROVED
                            && t.getAssignee().getId().equals(actor.getId()));
            if (alreadyApprovedFirst) {
                throw new ForbiddenOperationException("SAME_APPROVER_BOTH_STAGES",
                        "The first-stage approver may not also approve the second stage");
            }
        }

        boolean rejecting = !Boolean.TRUE.equals(command.approved());
        if (rejecting && (command.comment() == null || command.comment().trim().length() < 10)) {
            throw new BusinessRuleException("REJECTION_COMMENT_REQUIRED",
                    "A rejection requires a meaningful comment of at least 10 characters");
        }
    }

    private BankChangeRequestResponse handleFirstApproval(ApprovalTask task,
                                                          BankChangeRequest request,
                                                          String comment) {
        request.markFirstApproved();
        requestRepository.saveAndFlush(request);

        auditService.record(AuditAction.FIRST_APPROVAL_GRANTED, "BANK_CHANGE_REQUEST",
                request.getId(), request.getStatus().name(),
                "First approval by user %d".formatted(task.getAssignee().getId()));

        notificationService.notifyUser(request.getMaker().getId(),
                NotificationType.FIRST_APPROVAL_GRANTED,
                "First approval granted",
                "Request %s passed first approval and is now awaiting an independent second approver."
                        .formatted(request.getRequestReference()),
                "BANK_CHANGE_REQUEST", request.getId());

        // Assigning the second approver here, inside the same
        // transaction, is what makes the stages sequential rather than
        // merely numbered.
        assignmentService.assignSecondApprover(request);

        log.info("Request {} moved to second approval", request.getRequestReference());
        return queryService.toResponse(request);
    }

    private BankChangeRequestResponse handleSecondApproval(ApprovalTask task,
                                                           BankChangeRequest request,
                                                           String comment) {
        request.startCoolingOff(coolingOffProperties.duration());
        requestRepository.saveAndFlush(request);

        auditService.record(AuditAction.SECOND_APPROVAL_GRANTED, "BANK_CHANGE_REQUEST",
                request.getId(), request.getStatus().name(),
                "Second approval by user %d; dual approval complete"
                        .formatted(task.getAssignee().getId()));
        auditService.record(AuditAction.COOLING_OFF_STARTED, "BANK_CHANGE_REQUEST",
                request.getId(), request.getStatus().name(),
                "Cooling-off window ends at %s".formatted(request.getCoolingOffEndAt()));

        List<Long> recipients = interestedParties(request);
        notificationService.notifyUsers(recipients, NotificationType.DUAL_APPROVAL_COMPLETED,
                "Dual approval completed",
                ("Request %s has both approvals. Proposed banking details stay inactive "
                        + "until the cooling-off window closes at %s.")
                        .formatted(request.getRequestReference(), request.getCoolingOffEndAt()),
                "BANK_CHANGE_REQUEST", request.getId());

        log.info("Request {} entered cooling-off until {}", request.getRequestReference(),
                request.getCoolingOffEndAt());
        return queryService.toResponse(request);
    }

    private BankChangeRequestResponse handleRejection(ApprovalTask task, BankChangeRequest request,
                                                      String comment) {
        ApprovalStage stage = task.getStage();
        request.markRejected(comment);
        requestRepository.saveAndFlush(request);

        auditService.record(stage == ApprovalStage.FIRST
                        ? AuditAction.FIRST_APPROVAL_REJECTED
                        : AuditAction.SECOND_APPROVAL_REJECTED,
                "BANK_CHANGE_REQUEST", request.getId(), request.getStatus().name(), comment);

        notificationService.notifyUsers(interestedParties(request),
                NotificationType.REQUEST_REJECTED,
                "Bank-change request rejected",
                "Request %s was rejected at the %s approval stage."
                        .formatted(request.getRequestReference(), stage.name().toLowerCase()),
                "BANK_CHANGE_REQUEST", request.getId());

        // Holds are NOT released here. A rejected bank-change request
        // ends the approval workflow; whether the vendor's payments may
        // resume is a separate Accounts Payable decision.
        log.info("Request {} rejected at {} stage", request.getRequestReference(), stage);
        return queryService.toResponse(request);
    }

    /** Reassignment entry point. Role enforcement happens at the controller. */
    @Transactional
    public ApprovalTaskResponse reassign(Long taskId, ReassignmentCommand command) {
        ApprovalTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalTask", taskId));
        ApprovalTask replacement = assignmentService.reassign(task, command.replacementUserId(),
                command.reason());
        return ApprovalTaskResponse.from(replacement);
    }

    @Transactional(readOnly = true)
    public List<com.vendorfraud.bankchange.dto.ApproverOptionResponse> eligibleReplacements(Long taskId) {
        ApprovalTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalTask", taskId));
        return assignmentService.eligibleReplacements(task.getBankChangeRequest()).stream()
                .filter(u -> !u.getId().equals(task.getAssignee().getId()))
                .map(com.vendorfraud.bankchange.dto.ApproverOptionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalTask requireTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalTask", taskId));
    }

    /** Maker plus everyone who has held a stage of this request. */
    private List<Long> interestedParties(BankChangeRequest request) {
        List<Long> ids = new java.util.ArrayList<>(
                taskRepository.findAllInvolvedAssigneeIds(request.getId()));
        ids.add(request.getMaker().getId());
        ids.addAll(userRepository.findActiveByRole(RoleName.ACCOUNTS_PAYABLE).stream()
                .map(User::getId).toList());
        return ids.stream().distinct().toList();
    }
}
