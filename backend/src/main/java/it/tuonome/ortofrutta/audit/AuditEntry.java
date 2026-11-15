package it.tuonome.ortofrutta.audit;

import java.time.Instant;

public record AuditEntry(Long id, Instant createdAt, String username, String action,
                         String entityType, String entityId, String detail) {
}
