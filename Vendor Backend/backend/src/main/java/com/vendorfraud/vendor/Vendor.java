package com.vendorfraud.vendor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "vendors")
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendor_code", nullable = false, length = 40, unique = true)
    private String vendorCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "tax_reference", length = 60)
    private String taxReference;

    @Column(name = "contact_email", length = 160)
    private String contactEmail;

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

    protected Vendor() {
    }

    public Vendor(String vendorCode, String name, String taxReference, String contactEmail) {
        this.vendorCode = vendorCode;
        this.name = name;
        this.taxReference = taxReference;
        this.contactEmail = contactEmail;
        this.active = true;
    }

    public void deactivate(Long actorId) {
        this.active = false;
        this.deletedAt = Instant.now();
        this.deletedBy = actorId;
    }

    public Long getId() {
        return id;
    }

    public String getVendorCode() {
        return vendorCode;
    }

    public String getName() {
        return name;
    }

    public String getTaxReference() {
        return taxReference;
    }

    public String getContactEmail() {
        return contactEmail;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
