package com.vendorfraud.vendor;

import com.vendorfraud.audit.AuditAction;
import com.vendorfraud.audit.AuditService;
import com.vendorfraud.common.exception.BusinessRuleException;
import com.vendorfraud.common.exception.ResourceNotFoundException;
import com.vendorfraud.security.CurrentUserService;
import com.vendorfraud.vendor.dto.VendorBankAccountResponse;
import com.vendorfraud.vendor.dto.VendorResponse;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorService {

    private static final Logger log = LoggerFactory.getLogger(VendorService.class);

    private final VendorRepository vendorRepository;
    private final VendorBankAccountRepository bankAccountRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public VendorService(VendorRepository vendorRepository,
                         VendorBankAccountRepository bankAccountRepository,
                         AuditService auditService,
                         CurrentUserService currentUserService) {
        this.vendorRepository = vendorRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    /** Feeds the searchable active-vendor dropdown. Never returns inactive vendors. */
    @Transactional(readOnly = true)
    public List<VendorResponse> searchActiveVendors(String term) {
        String normalised = (term == null || term.isBlank()) ? null : term.trim();
        return vendorRepository.searchActive(normalised).stream()
                .map(VendorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VendorResponse getActiveVendor(Long vendorId) {
        return VendorResponse.from(requireActiveVendor(vendorId));
    }

    /**
     * The masked current banking reference shown read-only on the Maker
     * form. A vendor with no banking record yet returns {@code null}.
     */
    @Transactional(readOnly = true)
    public VendorBankAccountResponse getCurrentBankAccount(Long vendorId) {
        requireActiveVendor(vendorId);
        return bankAccountRepository.findByVendorIdAndStatus(vendorId, BankAccountStatus.ACTIVE)
                .map(VendorBankAccountResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<VendorBankAccountResponse> getBankAccountHistory(Long vendorId) {
        requireActiveVendor(vendorId);
        return bankAccountRepository.findHistoryByVendor(vendorId).stream()
                .map(VendorBankAccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Vendor requireActiveVendor(Long vendorId) {
        return vendorRepository.findByIdAndActiveTrueAndDeletedAtIsNull(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active vendor not found or vendor is inactive: " + vendorId));
    }

    @Transactional(readOnly = true)
    public Optional<VendorBankAccount> findActiveBankAccount(Long vendorId) {
        return bankAccountRepository.findByVendorIdAndStatus(vendorId, BankAccountStatus.ACTIVE);
    }

    /**
     * Soft-deletes a vendor. Deactivation is refused while work is in
     * flight, because an inactive vendor with an open bank-change
     * request or an unreleased payment hold would leave the workflow
     * stranded.
     *
     * @param hasUnresolvedWork supplied by the caller that can see
     *                          cross-module state
     */
    @Transactional
    public VendorResponse deactivateVendor(Long vendorId, boolean hasUnresolvedWork, String reason) {
        Vendor vendor = requireActiveVendor(vendorId);
        if (hasUnresolvedWork) {
            throw new BusinessRuleException("VENDOR_HAS_UNRESOLVED_WORK",
                    "The vendor has an open bank-change request or an active payment hold "
                            + "and cannot be deactivated yet");
        }
        vendor.deactivate(currentUserService.currentUserId());
        auditService.record(AuditAction.VENDOR_DEACTIVATED, "VENDOR", vendorId, "INACTIVE", reason);
        log.info("Vendor {} deactivated", vendorId);
        return VendorResponse.from(vendor);
    }
}
