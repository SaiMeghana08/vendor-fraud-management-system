package com.vendorfraud.bankchange;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

/** Append-only record of every assignment and reassignment. */
@Entity
@Table(name = "approval_assignment_history")
public class ApprovalAssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "approval_task_id", nullable = false)
    private Long approvalTaskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AssignmentAction action;

    @Column(name = "previous_assignee_id")
    private Long previousAssigneeId;

    @Column(name = "new_assignee_id", nullable = false)
    private Long newAssigneeId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "reason", length = 1000)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApprovalAssignmentHistory() {
    }

    public ApprovalAssignmentHistory(Long approvalTaskId, AssignmentAction action,
                                     Long previousAssigneeId, Long newAssigneeId,
                                     Long actorId, String reason) {
        this.approvalTaskId = approvalTaskId;
        this.action = action;
        this.previousAssigneeId = previousAssigneeId;
        this.newAssigneeId = newAssigneeId;
        this.actorId = actorId;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getApprovalTaskId() {
        return approvalTaskId;
    }

    public AssignmentAction getAction() {
        return action;
    }

    public Long getPreviousAssigneeId() {
        return previousAssigneeId;
    }

    public Long getNewAssigneeId() {
        return newAssigneeId;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
