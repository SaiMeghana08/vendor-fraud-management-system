package com.vendorfraud.vendor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorBankAccountRepository extends JpaRepository<VendorBankAccount, Long> {

    Optional<VendorBankAccount> findByVendorIdAndStatus(Long vendorId, BankAccountStatus status);

    @Query("""
           select a from VendorBankAccount a
            where a.vendor.id = :vendorId
            order by a.id desc
           """)
    List<VendorBankAccount> findHistoryByVendor(@Param("vendorId") Long vendorId);
}
