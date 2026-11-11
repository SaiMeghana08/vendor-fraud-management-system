package com.vendorfraud.payment.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.vendorfraud.common.exception.UnexpectedFieldException;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * The five business fields of the payment creation form. The payment
 * id, reference, status and hold status are all server-side; sending
 * any of them is an error, not a suggestion.
 */
public class CreatePaymentCommand {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(CreatePaymentCommand.class);

    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "id", "paymentid", "paymentreference", "reference", "status",
            "holdstatus", "vendorbankaccountid", "bankaccountid", "createdby",
            "createdat", "processedat");

    @NotNull(message = "A vendor must be selected")
    private Long vendorId;

    @NotNull(message = "An amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal amount;

    @NotBlank(message = "A currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a three-letter ISO code")
    private String currency;

    @NotNull(message = "A due date is required")
    private LocalDate dueDate;

    @NotBlank(message = "A business description is required")
    @Size(min = 3, max = 500)
    private String description;

    @JsonAnySetter
    public void absorbUnexpected(String name, Object value) {
        if (FORBIDDEN_FIELDS.contains(name.toLowerCase().replace("_", ""))) {
            LOG.warn("Rejected client attempt to supply server-controlled payment field '{}'", name);
        }
        throw new UnexpectedFieldException(name);
    }

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
