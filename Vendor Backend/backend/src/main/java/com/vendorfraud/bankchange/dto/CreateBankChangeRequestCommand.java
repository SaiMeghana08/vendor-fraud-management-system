package com.vendorfraud.bankchange.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.vendorfraud.common.exception.UnexpectedFieldException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * The only fields a Maker may send.
 *
 * <p>There is deliberately no approver id, maker id, request reference
 * or status field here. {@link #absorbUnexpected} rejects them loudly
 * if a client sends one anyway, rather than ignoring them silently -
 * a request that tries to pick its own approver should fail, not
 * half-succeed.
 */
public class CreateBankChangeRequestCommand {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(CreateBankChangeRequestCommand.class);

    /** Fields a client is never allowed to control. */
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "firstapproverid", "secondapproverid", "approverid", "approverids",
            "makerid", "maker", "requestreference", "reference", "status",
            "approvalstage", "stage", "paymentreference", "coolingoffendat",
            "coolingoffstartat", "id");

    @NotNull(message = "A vendor must be selected")
    private Long vendorId;

    @NotBlank(message = "Proposed account holder name is required")
    @Size(max = 160)
    private String proposedAccountHolderName;

    @NotBlank(message = "Proposed bank name is required")
    @Size(max = 160)
    private String proposedBankName;

    @NotBlank(message = "Proposed account reference is required")
    @Size(max = 60)
    @Pattern(regexp = "^[A-Za-z0-9\\-]{6,60}$",
            message = "Account reference may contain letters, digits and hyphens only")
    private String proposedAccountReference;

    @NotBlank(message = "Routing code is required")
    @Size(max = 40)
    @Pattern(regexp = "^[A-Za-z0-9\\-]{4,40}$",
            message = "Routing code may contain letters, digits and hyphens only")
    private String proposedRoutingCode;

    @NotBlank(message = "A reason for the change is required")
    @Size(min = 10, max = 1000, message = "Please give a meaningful reason (at least 10 characters)")
    private String reason;

    @NotBlank(message = "An evidence reference is required")
    @Size(max = 120)
    private String evidenceReference;

    /**
     * Any property that is not one of the six fields above is a
     * rejection, not a no-op. Server-controlled fields are named
     * explicitly in {@link #FORBIDDEN_FIELDS} so the log makes clear
     * when a client tried to steer the workflow rather than simply
     * sending a typo.
     */
    @JsonAnySetter
    public void absorbUnexpected(String name, Object value) {
        String normalised = name.toLowerCase().replace("_", "");
        if (FORBIDDEN_FIELDS.contains(normalised)) {
            LOG.warn("Rejected client attempt to supply server-controlled field '{}'", name);
        }
        throw new UnexpectedFieldException(name);
    }

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public String getProposedAccountHolderName() {
        return proposedAccountHolderName;
    }

    public void setProposedAccountHolderName(String proposedAccountHolderName) {
        this.proposedAccountHolderName = proposedAccountHolderName;
    }

    public String getProposedBankName() {
        return proposedBankName;
    }

    public void setProposedBankName(String proposedBankName) {
        this.proposedBankName = proposedBankName;
    }

    public String getProposedAccountReference() {
        return proposedAccountReference;
    }

    public void setProposedAccountReference(String proposedAccountReference) {
        this.proposedAccountReference = proposedAccountReference;
    }

    public String getProposedRoutingCode() {
        return proposedRoutingCode;
    }

    public void setProposedRoutingCode(String proposedRoutingCode) {
        this.proposedRoutingCode = proposedRoutingCode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEvidenceReference() {
        return evidenceReference;
    }

    public void setEvidenceReference(String evidenceReference) {
        this.evidenceReference = evidenceReference;
    }
}
