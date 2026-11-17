package it.tuonome.ortofrutta.stock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class StockDtos {

    private StockDtos() {
    }

    public record ReceiptRequest(
            @NotNull Long productId,
            Long supplierId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitCost,
            LocalDate receivedAt,
            LocalDate expiresAt
    ) {
    }

    /** Il motivo dello scarto si sceglie dalla tipologica "Motivi di scarto". */
    public record WasteRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @NotNull Long reasonId
    ) {
    }

    public record CountRequest(@NotNull Long productId, @NotNull @DecimalMin("0.00") BigDecimal countedQuantity) { }

    /** "unit" è l'etichetta breve dell'unità di misura (kg, pz...). */
    public record LotResponse(
            Long id, Long productId, String productName, String unit,
            Long supplierId, String supplierName,
            LocalDate receivedAt, LocalDate expiresAt,
            BigDecimal initialQuantity, BigDecimal remainingQuantity, BigDecimal unitCost,
            Long daysToExpiry
    ) {
        static LotResponse from(Lot l, String unit) {
            Long days = l.getExpiresAt() == null ? null
                    : ChronoUnit.DAYS.between(LocalDate.now(), l.getExpiresAt());
            return new LotResponse(l.getId(), l.getProduct().getId(), l.getProduct().getName(), unit,
                    l.getSupplier() == null ? null : l.getSupplier().getId(),
                    l.getSupplier() == null ? null : l.getSupplier().getName(),
                    l.getReceivedAt(), l.getExpiresAt(), l.getInitialQuantity(),
                    l.getRemainingQuantity(), l.getUnitCost(), days);
        }

        /** Chi non ha COSTS_VIEW non vede costo di acquisto e fornitore. */
        public LotResponse withoutCost() {
            return new LotResponse(id, productId, productName, unit, null, null, receivedAt, expiresAt,
                    initialQuantity, remainingQuantity, null, daysToExpiry);
        }
    }

    public record MovementResponse(
            Long id, Long productId, String productName, String unit, MovementType type,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal unitCost,
            String reason, String createdBy, boolean voided, Instant createdAt
    ) {
        static MovementResponse from(StockMovement m, String unit) {
            return new MovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getName(), unit,
                    m.getType(), m.getQuantity(), m.getUnitPrice(), m.getUnitCost(), m.getReason(),
                    m.getCreatedBy(), m.isVoided(), m.getCreatedAt());
        }

        public MovementResponse withoutCost() {
            return new MovementResponse(id, productId, productName, unit, type, quantity, unitPrice,
                    null, reason, createdBy, voided, createdAt);
        }
    }
}
