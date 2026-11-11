package com.vendorfraud.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read and insert only. The interface deliberately exposes no delete or
 * update helper beyond what JpaRepository declares, and no controller
 * calls those.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByIdDesc(String entityType, Long entityId,
                                                            Pageable pageable);

    Page<AuditLog> findByActionOrderByIdDesc(AuditAction action, Pageable pageable);

    Page<AuditLog> findAllByOrderByIdDesc(Pageable pageable);
}
