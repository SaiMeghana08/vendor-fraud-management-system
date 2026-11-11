package com.vendorfraud.override;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyOverrideRepository extends JpaRepository<EmergencyOverride, Long> {

    Optional<EmergencyOverride> findByOverrideReference(String reference);

    Page<EmergencyOverride> findByStatusOrderByIdDesc(OverrideStatus status, Pageable pageable);

    Page<EmergencyOverride> findAllByOrderByIdDesc(Pageable pageable);

    List<EmergencyOverride> findByBankChangeRequestIdOrderByIdDesc(Long requestId);

    boolean existsByBankChangeRequestIdAndStatus(Long requestId, OverrideStatus status);
}
