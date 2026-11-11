package com.vendorfraud.audit;

import com.vendorfraud.security.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit events.
 *
 * <p>Business audit events join the caller's transaction, so an audit
 * row never survives a rolled-back business operation. Security events
 * such as a failed login use their own transaction, because they must
 * persist exactly when the surrounding operation fails.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final CurrentUserService currentUserService;

    public AuditService(AuditLogRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, String entityType, Long entityId,
                       String workflowState, String reason) {
        Long actorId = currentUserService.currentPrincipal().map(p -> p.getId()).orElse(null);
        String username = currentUserService.currentUsername();
        persist(actorId, username, action, entityType, entityId, workflowState, reason);
    }

    /** For scheduled work, where there is no authenticated principal. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSystem(AuditAction action, String entityType, Long entityId,
                             String workflowState, String reason) {
        persist(null, "system-scheduler", action, entityType, entityId, workflowState, reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(Long actorId, String username, AuditAction action, String reason) {
        persist(actorId, username, action, "USER", actorId, null, reason);
    }

    private void persist(Long actorId, String username, AuditAction action, String entityType,
                         Long entityId, String workflowState, String reason) {
        repository.save(new AuditLog(actorId, username, action, entityType, entityId,
                workflowState, reason));
        log.info("AUDIT action={} entityType={} entityId={} actor={} state={}",
                action, entityType, entityId, username, workflowState);
    }
}
