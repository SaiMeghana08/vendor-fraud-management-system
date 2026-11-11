package com.vendorfraud.bankchange;

import com.vendorfraud.common.exception.InvalidStateTransitionException;
import com.vendorfraud.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

/**
 * One approval stage assigned to one approver.
 *
 * <p>A completed decision is never modified and never deleted. A
 * reassignment closes the pending task with status {@code REASSIGNED}
 * and opens a fresh one, so the trail shows who held the task and when.
 */
@Entity
@Table(name = "approval_tasks",
        uniqueConstraints = @UniqueConstraint(name = "uk_at_one_open",
                columnNames = {"bank_change_request_id", "stage", "open_marker"}))
public class ApprovalTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_change_request_id", nullable = false)
    private BankChangeRequest bankChangeRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 10)
    private ApprovalStage stage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignee_id", nullable = false)
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalTaskStatus status;

    /** 1 while PENDING, NULL once decided. Backs the duplicate-assignment guard. */
    @Column(name = "open_marker")
    private Integer openMarker;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ApprovalTask() {
    }

    public ApprovalTask(BankChangeRequest request, ApprovalStage stage, User assignee) {
        this.bankChangeRequest = request;
        this.stage = stage;
        this.assignee = assignee;
        this.status = ApprovalTaskStatus.PENDING;
        this.openMarker = 1;
        this.assignedAt = Instant.now();
    }

    private void requirePending(String operation) {
        if (this.status != ApprovalTaskStatus.PENDING) {
            throw new InvalidStateTransitionException("TASK_ALREADY_DECIDED",
                    "Approval task %d is %s; %s is not possible".formatted(id, status, operation));
        }
    }

    public void approve(String comment) {
        requirePending("approval");
        this.status = ApprovalTaskStatus.APPROVED;
        this.openMarker = null;
        this.decisionComment = comment;
        this.decidedAt = Instant.now();
    }

    public void reject(String comment) {
        requirePending("rejection");
        this.status = ApprovalTaskStatus.REJECTED;
        this.openMarker = null;
        this.decisionComment = comment;
        this.decidedAt = Instant.now();
    }

    public void closeForReassignment(String reason) {
        requirePending("reassignment");
        this.status = ApprovalTaskStatus.REASSIGNED;
        this.openMarker = null;
        this.decisionComment = reason;
        this.decidedAt = Instant.now();
    }

    public boolean isPending() {
        return status == ApprovalTaskStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public BankChangeRequest getBankChangeRequest() {
        return bankChangeRequest;
    }

    public ApprovalStage getStage() {
        return stage;
    }

    public User getAssignee() {
        return assignee;
    }

    public ApprovalTaskStatus getStatus() {
        return status;
    }

    public Integer getOpenMarker() {
        return openMarker;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
