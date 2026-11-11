package com.vendorfraud.audit;

import com.vendorfraud.audit.dto.AuditLogResponse;
import com.vendorfraud.common.ApiPage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit history, readable only by an Administrator.
 *
 * <p>There is no POST, PUT, PATCH or DELETE here. Audit rows are
 * written by the service layer and are never editable through the API.
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiPage<AuditLogResponse>> list(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiPage.from(
                auditQueryService.search(action, entityType, entityId, page, size)));
    }
}
