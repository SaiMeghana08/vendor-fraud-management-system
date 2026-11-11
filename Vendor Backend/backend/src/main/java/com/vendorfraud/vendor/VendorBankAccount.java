package com.vendorfraud.vendor;

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
 * A vendor's banking record. Activating a new record supersedes the
 * previous one; nothing is overwritten and nothing is deleted, so the
 * full banking history of every vendor is reconstructible.
 */
@Entity
@Table(name = "vendor_bank_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_vba_one_active",
                columnNames = {"vendor_id", "active_marker"}))
public class VendorBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "account_holder_name", nullable = false, length = 160)
    private String accountHolderName;

    @Column(name = "bank_name", nullable = false, length = 160)
    private String bankName;

    @Column(name = "account_reference", nullable = false, length = 60)
    private String accountReference;

    @Column(name = "routing_code", nullable = false, length = 40)
    private String routingCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BankAccountStatus status;

    /**
     * Holds 1 while this is the vendor's active record and NULL once it
     * is superseded. Backs the unique index that makes two active
     * records per vendor impossible at the database level.
     */
    @Column(name = "active_marker")
    private Integer activeMarker;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "source_request_id")
    private Long sourceRequestId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected VendorBankAccount() {
    }

    public VendorBankAccount(Vendor vendor, String accountHolderName, String bankName,
                             String accountReference, String routingCode, Long sourceRequestId) {
        this.vendor = vendor;
        this.accountHolderName = accountHolderName;
        this.bankName = bankName;
        this.accountReference = accountReference;
        this.routingCode = routingCode;
        this.sourceRequestId = sourceRequestId;
        this.status = BankAccountStatus.ACTIVE;
        this.activeMarker = 1;
        this.activatedAt = Instant.now();
        this.active = true;
    }

    public void supersede() {
        this.status = BankAccountStatus.SUPERSEDED;
        this.activeMarker = null;
        this.supersededAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public String getBankName() {
        return bankName;
    }

    public String getAccountReference() {
        return accountReference;
    }

    public String getRoutingCode() {
        return routingCode;
    }

    public BankAccountStatus getStatus() {
        return status;
    }

    public Integer getActiveMarker() {
        return activeMarker;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public Long getSourceRequestId() {
        return sourceRequestId;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Long getDeletedBy() {
        return deletedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
