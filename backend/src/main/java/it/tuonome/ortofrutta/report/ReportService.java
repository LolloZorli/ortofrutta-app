package it.tuonome.ortofrutta.report;

import it.tuonome.ortofrutta.common.CurrentUser;
import it.tuonome.ortofrutta.company.CompanyContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Report calcolati con SQL diretto (JdbcClient): sono aggregazioni, non entità.
 * Le vendite stornate (voided) non contano mai.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Rome");
    private static final int EXPIRY_ALERT_DAYS = 3;

    private record Totals(BigDecimal todayRevenue, BigDecimal revenue30, BigDecimal cost30, BigDecimal waste30) {
    }

    private final JdbcClient jdbc;

    public ReportService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public ReportDtos.Dashboard dashboard() {
        Timestamp startOfDay = Timestamp.from(LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant());
        Timestamp since30 = daysAgo(30);

        // Un'unica scansione dei movimenti per tutti gli importi (invece di una query per ogni cifra)
        Totals t = jdbc.sql("""
                select
                  coalesce(sum(case when type = 'SALE' and created_at >= :today then quantity * unit_price end), 0) as today_revenue,
                  coalesce(sum(case when type = 'SALE' then quantity * unit_price end), 0) as revenue30,
                  coalesce(sum(case when type = 'SALE' then quantity * unit_cost end), 0) as cost30,
                  coalesce(sum(case when type = 'WASTE' then quantity * unit_cost end), 0) as waste30
                from stock_movements
                where company_id = :companyId and not voided and created_at >= :since
                """).param("companyId", CompanyContext.requireId()).param("today", startOfDay).param("since", since30)
                .query((rs, n) -> new Totals(rs.getBigDecimal("today_revenue"), rs.getBigDecimal("revenue30"),
                        rs.getBigDecimal("cost30"), rs.getBigDecimal("waste30")))
                .single();

        BigDecimal stockValue = jdbc.sql(
                "select coalesce(sum(remaining_quantity * unit_cost), 0) from lots where company_id = :companyId")
                .param("companyId", CompanyContext.requireId())
                .query(BigDecimal.class).single();

        long[] counts = jdbc.sql("""
                select
                  (select count(*) from customer_orders where company_id = :companyId and status in ('NEW', 'READY')) as reservations,
                  (select count(*) from supplier_orders where company_id = :companyId and status in ('DRAFT', 'SENT')) as orders
                """).param("companyId", CompanyContext.requireId())
                .query((rs, n) -> new long[]{rs.getLong("reservations"), rs.getLong("orders")}).single();

        return new ReportDtos.Dashboard(t.todayRevenue(), stockValue, t.revenue30(),
                t.revenue30().subtract(t.cost30()), t.waste30(), counts[0], counts[1],
                lowStock(), expiringLots(), topProducts(since30));
    }

    /** Prodotti sotto la scorta minima, con una quantità suggerita per tornare a 2 volte la scorta minima. */
    public List<ReportDtos.LowStockItem> lowStock() {
        return jdbc.sql("""
                select p.id, p.name, u.short_label, p.min_stock,
                       coalesce(sum(l.remaining_quantity), 0) as stock,
                       coalesce((select sum(sm.quantity) / 30.0 from stock_movements sm
                         where sm.company_id = p.company_id and sm.product_id=p.id and sm.type='SALE' and not sm.voided
                         and sm.created_at >= now() - interval '30 days'), 0) as daily_sales,
                       (select l2.unit_cost from lots l2 where l2.company_id = p.company_id and l2.product_id = p.id
                         order by l2.received_at desc, l2.id desc limit 1) as last_cost
                from products p
                join units u on u.id = p.unit_id and u.company_id = p.company_id
                left join lots l on l.product_id = p.id and l.company_id = p.company_id
                where p.company_id = :companyId and p.active and p.min_stock > 0
                group by p.id, p.name, u.short_label, p.min_stock
                having coalesce(sum(l.remaining_quantity), 0) < p.min_stock
                order by p.name
                """).param("companyId", CompanyContext.requireId()).query((rs, n) -> {
            BigDecimal min = rs.getBigDecimal("min_stock");
            BigDecimal stock = rs.getBigDecimal("stock");
            BigDecimal daily = rs.getBigDecimal("daily_sales");
            BigDecimal target = min.multiply(BigDecimal.valueOf(2)).max(daily.multiply(BigDecimal.valueOf(7)));
            BigDecimal suggested = target.subtract(stock)
                    .max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP);
            return new ReportDtos.LowStockItem(rs.getLong("id"), rs.getString("name"),
                    rs.getString("short_label"), stock, min, suggested, rs.getBigDecimal("last_cost"),
                    daily.setScale(3, RoundingMode.HALF_UP), daily.signum() == 0 ? null : stock.divide(daily, 1, RoundingMode.HALF_UP));
        }).list();
    }

    public List<ReportDtos.ExpiringLot> expiringLots() {
        LocalDate limit = LocalDate.now(ZONE).plusDays(EXPIRY_ALERT_DAYS);
        return jdbc.sql("""
                select l.id, p.id as product_id, p.name, u.short_label, l.remaining_quantity, l.expires_at
                from lots l
                join products p on p.id = l.product_id and p.company_id = l.company_id
                join units u on u.id = p.unit_id and u.company_id = p.company_id
                where l.company_id = :companyId and l.remaining_quantity > 0 and l.expires_at is not null and l.expires_at <= :limit
                order by l.expires_at, p.name
                """).param("companyId", CompanyContext.requireId()).param("limit", Date.valueOf(limit)).query((rs, n) -> {
            LocalDate exp = rs.getDate("expires_at").toLocalDate();
            return new ReportDtos.ExpiringLot(rs.getLong("id"), rs.getLong("product_id"),
                    rs.getString("name"), rs.getString("short_label"),
                    rs.getBigDecimal("remaining_quantity"), exp,
                    ChronoUnit.DAYS.between(LocalDate.now(ZONE), exp));
        }).list();
    }

    private List<ReportDtos.TopProduct> topProducts(Timestamp since) {
        return jdbc.sql("""
                select p.id, p.name, u.short_label,
                       sum(m.quantity) as quantity, sum(m.quantity * m.unit_price) as revenue
                from stock_movements m
                join products p on p.id = m.product_id and p.company_id = m.company_id
                join units u on u.id = p.unit_id and u.company_id = p.company_id
                where m.company_id = :companyId and m.type = 'SALE' and not m.voided and m.created_at >= :from
                group by p.id, p.name, u.short_label
                order by revenue desc
                limit 5
                """).param("companyId", CompanyContext.requireId()).param("from", since).query((rs, n) ->
                new ReportDtos.TopProduct(rs.getLong("id"), rs.getString("name"),
                        rs.getString("short_label"), rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("revenue"))).list();
    }

    /** Vendite, margine e scarti per prodotto negli ultimi N giorni. */
    public List<ReportDtos.ProductReport> productReport(int days) {
        int d = Math.max(1, Math.min(days, 365));
        return jdbc.sql("""
                select p.id, p.name, u.short_label,
                  coalesce(sum(case when m.type = 'SALE' then m.quantity end), 0) as sold_qty,
                  coalesce(sum(case when m.type = 'SALE' then m.quantity * m.unit_price end), 0) as revenue,
                  coalesce(sum(case when m.type = 'SALE' then m.quantity * m.unit_cost end), 0) as cost,
                  coalesce(sum(case when m.type = 'WASTE' then m.quantity end), 0) as waste_qty,
                  coalesce(sum(case when m.type = 'WASTE' then m.quantity * m.unit_cost end), 0) as waste_cost
                from products p
                join units u on u.id = p.unit_id and u.company_id = p.company_id
                left join stock_movements m on m.product_id = p.id and m.company_id = p.company_id and not m.voided and m.created_at >= :from
                where p.company_id = :companyId
                group by p.id, p.name, u.short_label
                having coalesce(sum(case when m.type in ('SALE', 'WASTE') then m.quantity end), 0) > 0
                order by revenue desc, p.name
                """).param("companyId", CompanyContext.requireId()).param("from", daysAgo(d)).query((rs, n) -> {
            BigDecimal sold = rs.getBigDecimal("sold_qty");
            BigDecimal revenue = rs.getBigDecimal("revenue");
            BigDecimal cost = rs.getBigDecimal("cost");
            BigDecimal wasteQty = rs.getBigDecimal("waste_qty");
            BigDecimal margin = revenue.subtract(cost);
            return new ReportDtos.ProductReport(rs.getLong("id"), rs.getString("name"),
                    rs.getString("short_label"), sold, revenue, cost, margin,
                    percent(margin, revenue), wasteQty, rs.getBigDecimal("waste_cost"),
                    percent(wasteQty, sold.add(wasteQty)));
        }).list();
    }

    /** Riepilogo giornaliero: incassi per aliquota IVA (con imponibile e imposta scorporati) e per metodo di pagamento. */
    public ReportDtos.Corrispettivi corrispettivi(LocalDate date) {
        Timestamp from = Timestamp.from(date.atStartOfDay(ZONE).toInstant());
        Timestamp to = Timestamp.from(date.plusDays(1).atStartOfDay(ZONE).toInstant());

        List<ReportDtos.VatRow> byVat = jdbc.sql("""
                select coalesce(m.vat_rate, 0) as rate, sum(m.quantity * m.unit_price) as gross
                from stock_movements m
                where m.company_id = :companyId and m.type = 'SALE' and not m.voided and m.created_at >= :from and m.created_at < :to
                group by coalesce(m.vat_rate, 0)
                order by 1
                """).param("companyId", CompanyContext.requireId()).param("from", from).param("to", to).query((rs, n) -> {
            BigDecimal rate = rs.getBigDecimal("rate");
            BigDecimal gross = rs.getBigDecimal("gross").setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = gross.divide(BigDecimal.ONE.add(rate.movePointLeft(2)), 2, RoundingMode.HALF_UP);
            return new ReportDtos.VatRow(rate, gross, net, gross.subtract(net));
        }).list();

        List<ReportDtos.PaymentRow> byPayment = jdbc.sql("""
                select coalesce(pm.name, 'Non specificato') as method,
                       sum(m.quantity * m.unit_price) as gross,
                       count(distinct m.sale_id) as sales
                from stock_movements m
                left join sales s on s.id = m.sale_id and s.company_id = m.company_id
                left join payment_methods pm on pm.id = s.payment_method_id and pm.company_id = s.company_id
                where m.company_id = :companyId and m.type = 'SALE' and not m.voided and m.created_at >= :from and m.created_at < :to
                group by coalesce(pm.name, 'Non specificato')
                order by gross desc
                """).param("companyId", CompanyContext.requireId()).param("from", from).param("to", to).query((rs, n) ->
                new ReportDtos.PaymentRow(rs.getString("method"),
                        rs.getBigDecimal("gross").setScale(2, RoundingMode.HALF_UP), rs.getLong("sales"))).list();

        long[] salesCounts = jdbc.sql("""
                select count(*) filter (where status = 'COMPLETED') as done,
                       count(*) filter (where status = 'VOIDED') as voided
                from sales where company_id = :companyId and created_at >= :from and created_at < :to
                """).param("companyId", CompanyContext.requireId()).param("from", from).param("to", to)
                .query((rs, n) -> new long[]{rs.getLong("done"), rs.getLong("voided")}).single();
        BigDecimal voidedTotal = jdbc.sql("""
                select coalesce(sum(total), 0) from sales
                where company_id = :companyId and status = 'VOIDED' and created_at >= :from and created_at < :to
                """).param("companyId", CompanyContext.requireId()).param("from", from).param("to", to).query(BigDecimal.class).single();

        BigDecimal gross = byVat.stream().map(ReportDtos.VatRow::gross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = byVat.stream().map(ReportDtos.VatRow::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReportDtos.Corrispettivi(date, gross, net, gross.subtract(net),
                salesCounts[0], salesCounts[1], voidedTotal, byVat, byPayment);
    }

    public ReportDtos.CashClosing cashClosing(LocalDate date) {
        return jdbc.sql("select expected_cash, counted_cash, note, closed_by, closed_at from cash_closings where company_id=:companyId and closing_date=:date")
                .param("companyId", CompanyContext.requireId()).param("date", date).query((rs, n) -> closing(rs.getBigDecimal("expected_cash"),
                        rs.getBigDecimal("counted_cash"), rs.getString("note"), rs.getString("closed_by"),
                        rs.getTimestamp("closed_at").toInstant())).optional().orElse(null);
    }

    @Transactional
    public ReportDtos.CashClosing saveCashClosing(LocalDate date, ReportDtos.CashClosingRequest request) {
        BigDecimal expected = jdbc.sql("""
                select coalesce(sum(s.total),0) from sales s join payment_methods pm on pm.id=s.payment_method_id
                where s.company_id = :companyId and pm.company_id = s.company_id and s.status='COMPLETED' and s.created_at >= :from and s.created_at < :to and pm.code='CASH'
                """).param("from", Timestamp.from(date.atStartOfDay(ZONE).toInstant()))
                .param("companyId", CompanyContext.requireId())
                .param("to", Timestamp.from(date.plusDays(1).atStartOfDay(ZONE).toInstant()))
                .query(BigDecimal.class).single();
        jdbc.sql("""
                insert into cash_closings(company_id,closing_date,expected_cash,counted_cash,note,closed_by)
                values(:companyId,:date,:expected,:counted,:note,:user)
                on conflict(company_id,closing_date) do update set expected_cash=excluded.expected_cash,
                counted_cash=excluded.counted_cash,note=excluded.note,closed_by=excluded.closed_by,closed_at=now()
                """).param("companyId", CompanyContext.requireId()).param("date", date).param("expected", expected).param("counted", request.countedCash())
                .param("note", request.note()).param("user", CurrentUser.name()).update();
        return cashClosing(date);
    }

    private static ReportDtos.CashClosing closing(BigDecimal expected, BigDecimal counted, String note, String user, Instant at) {
        return new ReportDtos.CashClosing(expected, counted, counted.subtract(expected), note, user, at);
    }

    // ---------- utilità ----------

    private static Timestamp daysAgo(int days) {
        return Timestamp.from(Instant.now().minus(days, ChronoUnit.DAYS));
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
