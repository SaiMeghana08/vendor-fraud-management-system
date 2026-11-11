package com.vendorfraud.bankchange;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankChangeRequestRepository extends JpaRepository<BankChangeRequest, Long> {

    Optional<BankChangeRequest> findByRequestReference(String requestReference);

    Page<BankChangeRequest> findByMakerIdOrderByIdDesc(Long makerId, Pageable pageable);

    Page<BankChangeRequest> findAllByOrderByIdDesc(Pageable pageable);

    Page<BankChangeRequest> findByStatusOrderByIdDesc(BankChangeStatus status, Pageable pageable);

    /** Duplicate-request guard, checked before the unique index has to fire. */
    @Query("""
           select r from BankChangeRequest r
            where r.vendor.id = :vendorId
              and r.status not in (com.vendorfraud.bankchange.BankChangeStatus.ACTIVATED,
                                   com.vendorfraud.bankchange.BankChangeStatus.REJECTED,
                                   com.vendorfraud.bankchange.BankChangeStatus.CANCELLED)
           """)
    List<BankChangeRequest> findOpenByVendor(@Param("vendorId") Long vendorId);

    /**
     * Candidates for cooling-off completion. The scheduler re-reads and
     * re-checks every row inside its own transaction, so a row that was
     * already processed is simply skipped.
     */
    @Query("""
           select r from BankChangeRequest r
            where r.status = com.vendorfraud.bankchange.BankChangeStatus.COOLING_OFF
              and r.coolingOffEndAt <= :now
            order by r.coolingOffEndAt asc
           """)
    List<BankChangeRequest> findCoolingOffDue(@Param("now") Instant now, Pageable pageable);

    /** Row-level lock used by the scheduler and by concurrent decisions. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BankChangeRequest r where r.id = :id")
    Optional<BankChangeRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
           select count(r) from BankChangeRequest r
            where r.status = com.vendorfraud.bankchange.BankChangeStatus.ACTIVATION_BLOCKED
           """)
    long countBlocked();
}
