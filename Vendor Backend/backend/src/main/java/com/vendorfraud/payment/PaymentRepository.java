package com.vendorfraud.payment;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    Page<Payment> findAllByOrderByIdDesc(Pageable pageable);

    Page<Payment> findByStatusOrderByIdDesc(PaymentStatus status, Pageable pageable);

    Page<Payment> findByVendorIdOrderByIdDesc(Long vendorId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    /** Payments that a new bank-change request must freeze. */
    @Query("""
           select p from Payment p
            where p.vendor.id = :vendorId
              and p.status in (com.vendorfraud.payment.PaymentStatus.CREATED,
                               com.vendorfraud.payment.PaymentStatus.ON_HOLD)
           """)
    List<Payment> findHoldablePaymentsByVendor(@Param("vendorId") Long vendorId);
}
