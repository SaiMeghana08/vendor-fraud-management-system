package com.vendorfraud.audit;

import com.vendorfraud.audit.dto.AuditLogResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the audit module.
 *
 * <p>Audit reasons are written by services and can mention a synthetic
 * account reference. They are masked on the way out, so even an
 * Administrator reading the trail does not see a full banking
 * reference.
 */
@Service
public class AuditQueryService {

    /** Matches the synthetic account-reference shape used in this system. */
    private static final Pattern ACCOUNT_REFERENCE =
            Pattern.compile("\\b[A-Z]{2,5}-[A-Z]{2}-\\d{4,}\\b");

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(AuditAction action, String entityType, Long entityId,
                                         int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<AuditLog> logs;
        if (entityType != null && entityId != null) {
            logs = repository.findByEntityTypeAndEntityIdOrderByIdDesc(entityType, entityId, pageable);
        } else if (action != null) {
            logs = repository.findByActionOrderByIdDesc(action, pageable);
        } else {
            logs = repository.findAllByOrderByIdDesc(pageable);
        }
        return logs.map(log -> AuditLogResponse.from(log, mask(log.getReason())));
    }

    private String mask(String reason) {
        if (reason == null) {
            return null;
        }
        Matcher matcher = ACCOUNT_REFERENCE.matcher(reason);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    com.vendorfraud.common.MaskingUtils.maskAccountReference(matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
