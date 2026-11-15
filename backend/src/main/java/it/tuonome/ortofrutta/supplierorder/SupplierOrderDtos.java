package it.tuonome.ortofrutta.supplierorder;

import it.tuonome.ortofrutta.lookup.LookupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SupplierOrderDtos {

    private SupplierOrderDtos() {
    }

    public record LineRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @DecimalMin("0.00") BigDecimal unitCost
    ) {
    }

    public record Request(
            @NotNull Long supplierId,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<LineRequest> lines
    ) {
    }

    /** Riga effettivamente ricevuta: quantità e costo possono differire dall'ordine. */
    public record LineReceipt(
            @NotNull Long lineId,
            @DecimalMin("0") BigDecimal quantity,
            @DecimalMin("0.00") BigDecimal unitCost,
            LocalDate expiresAt
    ) {
    }

    public record ReceiveRequest(@Valid List<LineReceipt> lines) {
    }

    public record LineResponse(
            Long id, Long productId, String productName, String unit,
            BigDecimal quantity, BigDecimal unitCost
    ) {
    }

    public record Response(
            Long id, Long supplierId, String supplierName, String supplierPhone,
            SupplierOrderStatus status, Instant createdAt, Instant sentAt, Instant receivedAt,
            String notes, List<LineResponse> lines, BigDecimal total
    ) {
        static Response from(SupplierOrder o, LookupService lookups) {
            List<LineResponse> lines = o.getLines().stream()
                    .map(l -> new LineResponse(l.getId(), l.getProduct().getId(), l.getProduct().getName(),
                            lookups.unitLabel(l.getProduct().getUnitId()), l.getQuantity(), l.getUnitCost()))
                    .toList();
            BigDecimal total = lines.stream()
                    .map(l -> l.quantity().multiply(l.unitCost()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new Response(o.getId(), o.getSupplier().getId(), o.getSupplier().getName(),
                    o.getSupplier().getPhone(), o.getStatus(), o.getCreatedAt(), o.getSentAt(),
                    o.getReceivedAt(), o.getNotes(), lines, total);
        }

        /** Chi non ha COSTS_VIEW non vede i costi dell'ordine. */
        public Response withoutCosts() {
            List<LineResponse> noCost = lines.stream()
                    .map(l -> new LineResponse(l.id(), l.productId(), l.productName(), l.unit(), l.quantity(), null))
                    .toList();
            return new Response(id, supplierId, supplierName, supplierPhone, status, createdAt, sentAt,
                    receivedAt, notes, noCost, null);
        }
    }
}
