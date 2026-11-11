package com.vendorfraud.audit.dto;

import com.vendorfraud.audit.AuditAction;
import com.vendorfraud.audit.AuditLog;
import java.time.Instant;

/**
 * Audit row as exposed to an Administrator. Sensitive references in the
 * free-text reason are masked before they leave the service layer.
 */
public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorUsername,
        AuditAction action,
        String entityType,
        Long entityId,
        String workflowState,
        String reason,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log, String maskedReason) {
        return new AuditLogResponse(log.getId(), log.getActorId(), log.getActorUsername(),
                log.getAction(), log.getEntityType(), log.getEntityId(), log.getWorkflowState(),
                maskedReason, log.getCreatedAt());
    }
}
