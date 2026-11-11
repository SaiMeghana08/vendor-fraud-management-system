package com.vendorfraud.override;

import com.vendorfraud.common.exception.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

/**
 * A documented request to shorten the cooling-off window on one
 * bank-change request - and nothing else.
 *
 * <p>An approved override does not bypass dual approval, does not
 * activate unapproved details, does not release payment holds and does
 * not relax payment eligibility. Its entire effect is to bring forward
 * the cooling-off deadline of a request that already has two
 * independent approvals.
 */
@Entity
@Table(name = "emergency_overrides",
        uniqueConstraints = @UniqueConstraint(name = "uk_eo_one_open",
                columnNames = {"bank_change_request_id", "open_marker"}))
public class EmergencyOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "override_reference", nullable = false, length = 40, unique = true)
    private String overrideReference;

    @Column(name = "bank_change_request_id", nullable = false)
    private Long bankChangeRequestId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "justification", nullable = false, length = 1000)
    private String justification;

    @Column(name = "evidence_reference", nullable = false, length = 120)
    private String evidenceReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OverrideStatus status;

    /** 1 while PENDING, NULL once decided: one open override per request. */
    @Column(name = "open_marker")
    private Integer openMarker;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected EmergencyOverride() {
    }

    public EmergencyOverride(String overrideReference, Long bankChangeRequestId, Long requestedBy,
                             String justification, String evidenceReference) {
        this.overrideReference = overrideReference;
        this.bankChangeRequestId = bankChangeRequestId;
        this.requestedBy = requestedBy;
        this.justification = justification;
        this.evidenceReference = evidenceReference;
        this.status = OverrideStatus.PENDING;
        this.openMarker = 1;
    }

    public void decide(boolean approved, Long administratorId, String comment) {
        if (this.status != OverrideStatus.PENDING) {
            throw new InvalidStateTransitionException("OVERRIDE_ALREADY_DECIDED",
                    "Emergency override %s has already been decided".formatted(overrideReference));
        }
        this.status = approved ? OverrideStatus.APPROVED : OverrideStatus.REJECTED;
        this.openMarker = null;
        this.decidedBy = administratorId;
        this.decidedAt = Instant.now();
        this.decisionComment = comment;
    }

    public Long getId() {
        return id;
    }

    public String getOverrideReference() {
        return overrideReference;
    }

    public Long getBankChangeRequestId() {
        return bankChangeRequestId;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public String getJustification() {
        return justification;
    }

    public String getEvidenceReference() {
        return evidenceReference;
    }

    public OverrideStatus getStatus() {
        return status;
    }

    public Integer getOpenMarker() {
        return openMarker;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
