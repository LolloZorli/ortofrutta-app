package it.tuonome.ortofrutta.lookup;

import it.tuonome.ortofrutta.audit.AuditService;
import it.tuonome.ortofrutta.company.CompanyContext;
import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.NotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accesso alle tipologiche, con una cache in memoria: sono poche, cambiano raramente e vengono
 * lette a ogni richiesta (nomi, unità di misura, aliquote), quindi non vale la pena interrogare il database ogni volta.
 * <ul>
 *   <li>La cache si rinnova da sola ogni 60 secondi e subito dopo ogni modifica fatta da questo processo.</li>
 *   <li>Se un id non è in cache (creato da un'altra replica), si rilegge dal database prima di dichiararlo inesistente:
 *       con più repliche in Kubernetes un nuovo valore è quindi usabile subito ovunque.</li>
 * </ul>
 */
@Service
public class LookupService {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration MIN_RELOAD_GAP = Duration.ofSeconds(2);

    private record Snapshot(Instant loadedAt, Map<LookupType, Map<Long, LookupItem>> data) {
    }

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Map<Long, Snapshot> snapshots = new ConcurrentHashMap<>();

    public LookupService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // ---------------------------------- letture ----------------------------------

    public List<LookupItem> list(LookupType type, boolean includeInactive) {
        return current().data().get(type).values().stream()
                .filter(i -> includeInactive || i.active())
                .toList();
    }

    /** Elenco aggiornato dal database (per la schermata di amministrazione). */
    public List<LookupItem> listFresh(LookupType type) {
        return reload().data().get(type).values().stream().toList();
    }

    public LookupItem find(LookupType type, Long id) {
        if (id == null) {
            return null;
        }
        Snapshot s = current();
        LookupItem item = s.data().get(type).get(id);
        if (item == null && Duration.between(s.loadedAt(), Instant.now()).compareTo(MIN_RELOAD_GAP) > 0) {
            item = reload().data().get(type).get(id);
        }
        return item;
    }

    /** Il valore deve esistere ed essere attivo: un valore disattivato resta sui dati vecchi ma non si assegna più. */
    public LookupItem requireActive(LookupType type, Long id, String what) {
        LookupItem item = find(type, id);
        if (item == null) {
            throw new BusinessException(what + " non valido");
        }
        if (!item.active()) {
            throw new BusinessException(what + " \"" + item.name() + "\" non è più attivo");
        }
        return item;
    }

    public String name(LookupType type, Long id) {
        LookupItem i = find(type, id);
        return i == null ? null : i.name();
    }

    public String unitLabel(Long unitId) {
        LookupItem u = find(LookupType.UNIT, unitId);
        return u == null || u.shortLabel() == null ? "" : u.shortLabel();
    }

    public BigDecimal vatRate(Long vatRateId) {
        LookupItem v = find(LookupType.VAT_RATE, vatRateId);
        return v == null ? null : v.rate();
    }

    // -------------------------- scritture (solo amministratore) --------------------------

    @Transactional
    public LookupItem create(LookupType type, LookupDtos.Request r) {
        String name = r.name().trim();
        validateAttributes(type, r.shortLabel(), r.rate());
        if (nameExists(type, name, null)) {
            throw new BusinessException("Esiste già un valore con questo nome");
        }

        Map<String, Object> p = new HashMap<>();
        p.put("code", uniqueCode(type, name));
        p.put("name", name);
        p.put("sort", r.sortOrder() != null ? r.sortOrder() : nextSort(type));
        p.put("active", r.active() == null || r.active());
        p.put("companyId", CompanyContext.requireId());
        StringBuilder cols = new StringBuilder("company_id, code, name, sort_order, active");
        StringBuilder vals = new StringBuilder(":companyId, :code, :name, :sort, :active");
        if (type.hasUnitAttributes()) {
            cols.append(", short_label, allow_decimals");
            vals.append(", :short, :dec");
            p.put("short", r.shortLabel().trim());
            p.put("dec", Boolean.TRUE.equals(r.allowDecimals()));
        }
        if (type.hasRate()) {
            cols.append(", rate");
            vals.append(", :rate");
            p.put("rate", r.rate());
        }
        Long id = jdbc.sql("insert into " + type.table() + " (" + cols + ") values (" + vals + ") returning id")
                .params(p).query(Long.class).single();

        reload();
        audit.record("LOOKUP_CREATE", type.name(), id, describe(type, name, r.rate()));
        return find(type, id);
    }

    @Transactional
    public LookupItem update(LookupType type, Long id, LookupDtos.Request r) {
        LookupItem cur = reload().data().get(type).get(id);
        if (cur == null) {
            throw new NotFoundException("Valore", id);
        }
        String name = r.name().trim();
        if (nameExists(type, name, id)) {
            throw new BusinessException("Esiste già un valore con questo nome");
        }

        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        p.put("companyId", CompanyContext.requireId());
        p.put("name", name);
        p.put("sort", r.sortOrder() != null ? r.sortOrder() : cur.sortOrder());
        p.put("active", r.active() != null ? r.active() : cur.active());
        StringBuilder set = new StringBuilder("name = :name, sort_order = :sort, active = :active");
        String shortLabel = r.shortLabel() != null ? r.shortLabel() : cur.shortLabel();
        BigDecimal rate = r.rate() != null ? r.rate() : cur.rate();
        validateAttributes(type, shortLabel, rate);
        if (type.hasUnitAttributes()) {
            set.append(", short_label = :short, allow_decimals = :dec");
            p.put("short", shortLabel.trim());
            p.put("dec", r.allowDecimals() != null ? r.allowDecimals() : Boolean.TRUE.equals(cur.allowDecimals()));
        }
        if (type.hasRate()) {
            set.append(", rate = :rate");
            p.put("rate", rate);
        }
        jdbc.sql("update " + type.table() + " set " + set + " where id = :id and company_id = :companyId").params(p).update();

        reload();
        audit.record("LOOKUP_UPDATE", type.name(), id,
                describe(type, name, rate) + ((Boolean) p.get("active") ? "" : " (disattivato)"));
        return find(type, id);
    }

    // ---------------------------------- interni ----------------------------------

    private Snapshot current() {
        Long companyId = CompanyContext.requireId();
        Snapshot s = snapshots.get(companyId);
        if (s == null || Duration.between(s.loadedAt(), Instant.now()).compareTo(TTL) > 0) {
            return reload();
        }
        return s;
    }

    private synchronized Snapshot reload() {
        long companyId = CompanyContext.requireId();
        Map<LookupType, Map<Long, LookupItem>> data = new EnumMap<>(LookupType.class);
        for (LookupType t : LookupType.values()) {
            Map<Long, LookupItem> m = new LinkedHashMap<>();
            jdbc.sql("select " + columns(t) + " from " + t.table()
                            + " where company_id = :companyId order by sort_order, name")
                    .param("companyId", companyId)
                    .query((rs, n) -> map(t, rs))
                    .list()
                    .forEach(i -> m.put(i.id(), i));
            data.put(t, m);
        }
        Snapshot s = new Snapshot(Instant.now(), data);
        snapshots.put(companyId, s);
        return s;
    }

    private static String columns(LookupType t) {
        StringBuilder sb = new StringBuilder("id, code, name, sort_order, active");
        if (t.hasUnitAttributes()) {
            sb.append(", short_label, allow_decimals");
        }
        if (t.hasRate()) {
            sb.append(", rate");
        }
        return sb.toString();
    }

    private static LookupItem map(LookupType t, ResultSet rs) throws SQLException {
        return new LookupItem(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getInt("sort_order"), rs.getBoolean("active"),
                t.hasUnitAttributes() ? rs.getString("short_label") : null,
                t.hasUnitAttributes() ? rs.getBoolean("allow_decimals") : null,
                t.hasRate() ? rs.getBigDecimal("rate") : null);
    }

    private static void validateAttributes(LookupType type, String shortLabel, BigDecimal rate) {
        if (type.hasUnitAttributes() && (shortLabel == null || shortLabel.isBlank())) {
            throw new BusinessException("Indica l'etichetta breve dell'unità di misura (es. kg)");
        }
        if (type.hasRate() && rate == null) {
            throw new BusinessException("Indica la percentuale dell'aliquota");
        }
    }

    private boolean nameExists(LookupType type, String name, Long excludeId) {
        String sql = "select count(*) from " + type.table() + " where company_id = :companyId and lower(name) = lower(:name)"
                + (excludeId != null ? " and id <> :id" : "");
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("companyId", CompanyContext.requireId()).param("name", name);
        if (excludeId != null) {
            spec = spec.param("id", excludeId);
        }
        return spec.query(Long.class).single() > 0;
    }

    private String uniqueCode(LookupType type, String name) {
        String base = slug(name);
        String code = base;
        int n = 2;
        while (jdbc.sql("select count(*) from " + type.table() + " where company_id = :companyId and code = :code")
                .param("companyId", CompanyContext.requireId()).param("code", code).query(Long.class).single() > 0) {
            code = base + "_" + n++;
        }
        return code;
    }

    private int nextSort(LookupType type) {
        Integer max = jdbc.sql("select coalesce(max(sort_order), 0) from " + type.table() + " where company_id = :companyId")
                .param("companyId", CompanyContext.requireId())
                .query(Integer.class).single();
        return max + 1;
    }

    static String slug(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.isEmpty()) {
            s = "VALORE";
        }
        return s.length() > 50 ? s.substring(0, 50) : s;
    }

    private static String describe(LookupType type, String name, BigDecimal rate) {
        return type.label() + ": " + name + (rate != null && type.hasRate() ? " (" + rate + "%)" : "");
    }
}
