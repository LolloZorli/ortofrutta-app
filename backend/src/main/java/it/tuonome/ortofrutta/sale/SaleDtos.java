package it.tuonome.ortofrutta.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SaleDtos {

    private SaleDtos() {
    }

    /** unitPrice è facoltativo e conta solo per chi ha il permesso SALES_PRICE_OVERRIDE. */
    public record ItemRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @DecimalMin("0.00") BigDecimal unitPrice
    ) {
    }

    public record CheckoutRequest(
            @NotNull Long paymentMethodId,
            @NotEmpty @Valid List<ItemRequest> items
    ) {
    }

    public record VoidRequest(@NotBlank @Size(max = 200) String reason) {
    }

    public record Line(String productName, String unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {
    }

    /** In elenco "lines" è vuoto; nel dettaglio e nella risposta di cassa contiene le righe. */
    public record Response(Long id, Instant createdAt, String createdBy, String paymentMethod,
                           BigDecimal total, SaleStatus status, Instant voidedAt, String voidedBy,
                           String voidReason, List<Line> lines) {
    }
}
