package com.vendorfraud.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentHoldRepository extends JpaRepository<PaymentHold, Long> {

    List<PaymentHold> findByPaymentIdAndStatus(Long paymentId, HoldStatus status);

    List<PaymentHold> findByPaymentIdOrderByIdDesc(Long paymentId);

    List<PaymentHold> findByBankChangeRequestIdAndStatus(Long requestId, HoldStatus status);

    Page<PaymentHold> findByStatusOrderByIdDesc(HoldStatus status, Pageable pageable);

    Optional<PaymentHold> findByPaymentIdAndBankChangeRequestIdAndStatus(
            Long paymentId, Long bankChangeRequestId, HoldStatus status);

    long countByPaymentIdAndStatus(Long paymentId, HoldStatus status);

    boolean existsByBankChangeRequestIdAndStatus(Long requestId, HoldStatus status);
}
