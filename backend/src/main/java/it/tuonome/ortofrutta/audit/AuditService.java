package it.tuonome.ortofrutta.audit;

import it.tuonome.ortofrutta.common.CurrentUser;
import it.tuonome.ortofrutta.common.PageResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;

/**
 * Registro delle attività rilevanti (modifiche a prezzi, utenti, ruoli, tipologiche, storni...).
 * Scrive nella stessa transazione dell'operazione: se questa fallisce, non resta nessuna riga.
 */
@Service
public class AuditService {

    private final JdbcClient jdbc;

    public AuditService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String action, String entityType, Object entityId, String detail) {
        jdbc.sql("insert into audit_log (username, action, entity_type, entity_id, detail) "
                        + "values (:user, :action, :type, :entityId, :detail)")
                .param("user", CurrentUser.name())
                .param("action", action)
                .param("type", entityType)
                .param("entityId", entityId == null ? null : String.valueOf(entityId), Types.VARCHAR)
                .param("detail", truncate(detail), Types.VARCHAR)
                .update();
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> list(int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);
        long total = jdbc.sql("select count(*) from audit_log").query(Long.class).single();
        var rows = jdbc.sql("""
                select id, created_at, username, action, entity_type, entity_id, detail
                from audit_log order by created_at desc, id desc limit :size offset :offset
                """)
                .param("size", s).param("offset", (long) p * s)
                .query((rs, n) -> new AuditEntry(rs.getLong("id"), rs.getTimestamp("created_at").toInstant(),
                        rs.getString("username"), rs.getString("action"), rs.getString("entity_type"),
                        rs.getString("entity_id"), rs.getString("detail")))
                .list();
        return PageResponse.of(rows, p, s, total);
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 497) + "...";
    }
}
