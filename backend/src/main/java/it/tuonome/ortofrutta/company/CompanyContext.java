package it.tuonome.ortofrutta.company;

/** Current company selected for this HTTP request. */
public final class CompanyContext {

    public static final long LEGACY_COMPANY_ID = 1L;
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private CompanyContext() { }

    public static Long currentId() { return CURRENT.get(); }

    /** The original single-company API defaults to its migrated company for compatibility. */
    public static long requireId() {
        Long id = CURRENT.get();
        return id == null ? LEGACY_COMPANY_ID : id;
    }

    public static void set(Long companyId) { CURRENT.set(companyId); }

    public static void clear() { CURRENT.remove(); }
}
