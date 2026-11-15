package it.tuonome.ortofrutta.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    /** "unit" è sempre l'etichetta breve dell'unità di misura (kg, pz...). */
    public record LowStockItem(
            Long productId, String productName, String unit,
            BigDecimal stock, BigDecimal minStock, BigDecimal suggestedQuantity, BigDecimal lastUnitCost
    ) {
    }

    public record ExpiringLot(
            Long lotId, Long productId, String productName, String unit,
            BigDecimal remainingQuantity, LocalDate expiresAt, long daysToExpiry
    ) {
    }

    public record TopProduct(Long productId, String productName, String unit, BigDecimal quantity, BigDecimal revenue) {
    }

    /** Gli importi sono null per chi non ha il permesso di vederli (vedi restrict). */
    public record Dashboard(
            BigDecimal todayRevenue,
            BigDecimal stockValue,
            BigDecimal revenue30,
            BigDecimal margin30,
            BigDecimal waste30Cost,
            long openReservations,
            long openSupplierOrders,
            List<LowStockItem> lowStock,
            List<ExpiringLot> expiring,
            List<TopProduct> topProducts
    ) {
        /**
         * Incassi e prodotti più venduti richiedono REPORTS_VIEW; valore di magazzino, margini,
         * costo degli scarti e costi di riordino richiedono COSTS_VIEW.
         */
        public Dashboard restrict(boolean canSeeRevenue, boolean canSeeCosts) {
            List<LowStockItem> low = canSeeCosts ? lowStock : lowStock.stream()
                    .map(i -> new LowStockItem(i.productId(), i.productName(), i.unit(), i.stock(),
                            i.minStock(), i.suggestedQuantity(), null))
                    .toList();
            return new Dashboard(
                    canSeeRevenue ? todayRevenue : null,
                    canSeeCosts ? stockValue : null,
                    canSeeRevenue ? revenue30 : null,
                    canSeeCosts ? margin30 : null,
                    canSeeCosts ? waste30Cost : null,
                    openReservations, openSupplierOrders, low, expiring,
                    canSeeRevenue ? topProducts : List.of());
        }
    }

    public record ProductReport(
            Long productId, String productName, String unit,
            BigDecimal soldQuantity, BigDecimal revenue, BigDecimal cost, BigDecimal margin,
            BigDecimal marginPercent,
            BigDecimal wasteQuantity, BigDecimal wasteCost, BigDecimal wastePercent
    ) {
    }

    /** Riga del riepilogo per aliquota IVA: i prezzi di vendita sono IVA inclusa, l'imponibile si scorpora. */
    public record VatRow(BigDecimal rate, BigDecimal gross, BigDecimal net, BigDecimal vat) {
    }

    public record PaymentRow(String method, BigDecimal gross, long sales) {
    }

    /**
     * Riepilogo giornaliero degli incassi (corrispettivi) per aliquota IVA e per metodo di pagamento.
     * È un prospetto interno: non sostituisce la memorizzazione e trasmissione fiscale dei corrispettivi.
     */
    public record Corrispettivi(
            LocalDate date,
            BigDecimal totalGross, BigDecimal totalNet, BigDecimal totalVat,
            long salesCount, long voidedCount, BigDecimal voidedTotal,
            List<VatRow> byVat, List<PaymentRow> byPayment
    ) {
    }
}
