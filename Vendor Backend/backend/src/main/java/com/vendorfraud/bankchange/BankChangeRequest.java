package com.vendorfraud.bankchange;

import com.vendorfraud.common.exception.InvalidStateTransitionException;
import com.vendorfraud.user.User;
import com.vendorfraud.vendor.Vendor;
import com.vendorfraud.vendor.VendorBankAccount;
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
import java.time.Duration;
import java.time.Instant;

/**
 * A proposed change to a vendor's banking details.
 *
 * <p>Creating one of these never touches the vendor's live banking
 * record. The proposed values sit here, inert, until two independent
 * approvals and a cooling-off window have both completed and
 * revalidation has passed.
 */
@Entity
@Table(name = "bank_change_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_bcr_one_open_per_vendor",
                columnNames = {"vendor_id", "open_marker"}))
public class BankChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_reference", nullable = false, length = 40, unique = true)
    private String requestReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maker_id", nullable = false)
    private User maker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_bank_account_id")
    private VendorBankAccount currentBankAccount;

    @Column(name = "proposed_account_holder_name", nullable = false, length = 160)
    private String proposedAccountHolderName;

    @Column(name = "proposed_bank_name", nullable = false, length = 160)
    private String proposedBankName;

    @Column(name = "proposed_account_reference", nullable = false, length = 60)
    private String proposedAccountReference;

    @Column(name = "proposed_routing_code", nullable = false, length = 40)
    private String proposedRoutingCode;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "evidence_reference", nullable = false, length = 120)
    private String evidenceReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BankChangeStatus status;

    /**
     * 1 while the request is open, NULL once terminal. Backs the unique
     * index that makes a second open request for the same vendor
     * impossible - the duplicate-request guard.
     */
    @Column(name = "open_marker")
    private Integer openMarker;

    @Column(name = "cooling_off_hours")
    private Integer coolingOffHours;

    @Column(name = "cooling_off_start_at")
    private Instant coolingOffStartAt;

    @Column(name = "cooling_off_end_at")
    private Instant coolingOffEndAt;

    @Column(name = "cooling_off_override_id")
    private Long coolingOffOverrideId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activated_bank_account_id")
    private VendorBankAccount activatedBankAccount;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "terminal_reason", length = 1000)
    private String terminalReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected BankChangeRequest() {
    }

    public BankChangeRequest(String requestReference, Vendor vendor, User maker,
                             VendorBankAccount currentBankAccount,
                             String proposedAccountHolderName, String proposedBankName,
                             String proposedAccountReference, String proposedRoutingCode,
                             String reason, String evidenceReference) {
        this.requestReference = requestReference;
        this.vendor = vendor;
        this.maker = maker;
        this.currentBankAccount = currentBankAccount;
        this.proposedAccountHolderName = proposedAccountHolderName;
        this.proposedBankName = proposedBankName;
        this.proposedAccountReference = proposedAccountReference;
        this.proposedRoutingCode = proposedRoutingCode;
        this.reason = reason;
        this.evidenceReference = evidenceReference;
        this.status = BankChangeStatus.PENDING_FIRST_APPROVAL;
        this.openMarker = 1;
    }

    /**
     * The single place a status changes. Guarding here means no service
     * can quietly move a request sideways.
     */
    private void transitionTo(BankChangeStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Cannot move request %s from %s to %s".formatted(requestReference, status, target));
        }
        this.status = target;
        this.openMarker = target.isOpen() ? 1 : null;
    }

    public void markFirstApproved() {
        transitionTo(BankChangeStatus.PENDING_SECOND_APPROVAL);
    }

    public void startCoolingOff(Duration duration) {
        transitionTo(BankChangeStatus.COOLING_OFF);
        Instant now = Instant.now();
        this.coolingOffStartAt = now;
        this.coolingOffEndAt = now.plus(duration);
        this.coolingOffHours = (int) duration.toHours();
    }

    /** An approved emergency override shortens only the cooling-off window. */
    public void applyCoolingOffException(Long overrideId) {
        if (this.status != BankChangeStatus.COOLING_OFF) {
            throw new InvalidStateTransitionException("COOLING_OFF_EXCEPTION_NOT_APPLICABLE",
                    "A cooling-off exception applies only while the request is in COOLING_OFF");
        }
        this.coolingOffOverrideId = overrideId;
        this.coolingOffEndAt = Instant.now();
    }

    public void markRejected(String comment) {
        transitionTo(BankChangeStatus.REJECTED);
        this.terminalReason = comment;
    }

    public void markCancelled(String comment) {
        transitionTo(BankChangeStatus.CANCELLED);
        this.terminalReason = comment;
    }

    public void markActivated(VendorBankAccount newAccount) {
        transitionTo(BankChangeStatus.ACTIVATED);
        this.activatedBankAccount = newAccount;
        this.activatedAt = Instant.now();
    }

    public void markActivationBlocked(String reason) {
        transitionTo(BankChangeStatus.ACTIVATION_BLOCKED);
        this.terminalReason = reason;
    }

    public boolean isCoolingOffElapsed(Instant at) {
        return coolingOffEndAt != null && !at.isBefore(coolingOffEndAt);
    }

    public Long getId() {
        return id;
    }

    public String getRequestReference() {
        return requestReference;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public User getMaker() {
        return maker;
    }

    public VendorBankAccount getCurrentBankAccount() {
        return currentBankAccount;
    }

    public String getProposedAccountHolderName() {
        return proposedAccountHolderName;
    }

    public String getProposedBankName() {
        return proposedBankName;
    }

    public String getProposedAccountReference() {
        return proposedAccountReference;
    }

    public String getProposedRoutingCode() {
        return proposedRoutingCode;
    }

    public String getReason() {
        return reason;
    }

    public String getEvidenceReference() {
        return evidenceReference;
    }

    public BankChangeStatus getStatus() {
        return status;
    }

    public Integer getOpenMarker() {
        return openMarker;
    }

    public Integer getCoolingOffHours() {
        return coolingOffHours;
    }

    public Instant getCoolingOffStartAt() {
        return coolingOffStartAt;
    }

    public Instant getCoolingOffEndAt() {
        return coolingOffEndAt;
    }

    public Long getCoolingOffOverrideId() {
        return coolingOffOverrideId;
    }

    public VendorBankAccount getActivatedBankAccount() {
        return activatedBankAccount;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public String getTerminalReason() {
        return terminalReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
