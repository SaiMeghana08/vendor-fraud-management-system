package com.vendorfraud.payment;

import com.vendorfraud.common.exception.InvalidStateTransitionException;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A vendor payment. The reference, the identifier, the initial status
 * and the creation timestamp are all produced by the backend; a client
 * supplies only the business fields.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_reference", nullable = false, length = 40, unique = true)
    private String paymentReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_bank_account_id")
    private VendorBankAccount vendorBankAccount;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processed_by")
    private Long processedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Payment() {
    }

    public Payment(String paymentReference, Vendor vendor, VendorBankAccount vendorBankAccount,
                   BigDecimal amount, String currency, LocalDate dueDate, String description,
                   Long createdBy) {
        this.paymentReference = paymentReference;
        this.vendor = vendor;
        this.vendorBankAccount = vendorBankAccount;
        this.amount = amount;
        this.currency = currency;
        this.dueDate = dueDate;
        this.description = description;
        this.createdBy = createdBy;
        this.status = PaymentStatus.CREATED;
    }

    public void markOnHold() {
        if (status.isTerminal()) {
            throw new InvalidStateTransitionException(
                    "A %s payment cannot be placed on hold".formatted(status));
        }
        this.status = PaymentStatus.ON_HOLD;
    }

    /** Called only once every hold on the payment is released. */
    public void markPayable() {
        if (status.isTerminal()) {
            throw new InvalidStateTransitionException(
                    "A %s payment cannot return to a payable state".formatted(status));
        }
        this.status = PaymentStatus.CREATED;
    }

    public void markProcessed(Long actorId, VendorBankAccount payToAccount) {
        if (status != PaymentStatus.CREATED) {
            throw new InvalidStateTransitionException("PAYMENT_NOT_PAYABLE",
                    "Payment %s is %s and cannot be processed".formatted(paymentReference, status));
        }
        this.status = PaymentStatus.PROCESSED;
        this.processedAt = Instant.now();
        this.processedBy = actorId;
        this.vendorBankAccount = payToAccount;
    }

    public void cancel() {
        if (status.isTerminal()) {
            throw new InvalidStateTransitionException(
                    "A %s payment cannot be cancelled".formatted(status));
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public VendorBankAccount getVendorBankAccount() {
        return vendorBankAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getDescription() {
        return description;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Long getProcessedBy() {
        return processedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
