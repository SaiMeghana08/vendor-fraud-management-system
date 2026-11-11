package com.vendorfraud.payment;

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
 * A block on paying one payment while a bank-change request for that
 * vendor is in flight.
 *
 * <p>A hold is released only by an explicit, justified Accounts Payable
 * action. Nothing in the workflow - not activation, not rejection, and
 * above all not an approved emergency override - releases a hold on its
 * own.
 */
@Entity
@Table(name = "payment_holds",
        uniqueConstraints = @UniqueConstraint(name = "uk_ph_one_active",
                columnNames = {"payment_id", "bank_change_request_id", "active_marker"}))
public class PaymentHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "bank_change_request_id")
    private Long bankChangeRequestId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HoldStatus status;

    /** 1 while ACTIVE, NULL once released. Backs the one-active-hold index. */
    @Column(name = "active_marker")
    private Integer activeMarker;

    @Column(name = "release_justification", length = 1000)
    private String releaseJustification;

    @Column(name = "released_by")
    private Long releasedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected PaymentHold() {
    }

    public PaymentHold(Long paymentId, Long bankChangeRequestId, String reason) {
        this.paymentId = paymentId;
        this.bankChangeRequestId = bankChangeRequestId;
        this.reason = reason;
        this.status = HoldStatus.ACTIVE;
        this.activeMarker = 1;
    }

    public void release(Long actorId, String justification) {
        if (this.status != HoldStatus.ACTIVE) {
            throw new InvalidStateTransitionException("HOLD_ALREADY_RELEASED",
                    "Payment hold %d has already been released".formatted(id));
        }
        this.status = HoldStatus.RELEASED;
        this.activeMarker = null;
        this.releasedBy = actorId;
        this.releaseJustification = justification;
        this.releasedAt = Instant.now();
    }

    public boolean isActive() {
        return status == HoldStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getBankChangeRequestId() {
        return bankChangeRequestId;
    }

    public String getReason() {
        return reason;
    }

    public HoldStatus getStatus() {
        return status;
    }

    public Integer getActiveMarker() {
        return activeMarker;
    }

    public String getReleaseJustification() {
        return releaseJustification;
    }

    public Long getReleasedBy() {
        return releasedBy;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
