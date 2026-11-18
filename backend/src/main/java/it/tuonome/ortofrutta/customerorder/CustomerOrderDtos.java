package it.tuonome.ortofrutta.customerorder;

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

public final class CustomerOrderDtos {

    private CustomerOrderDtos() {
    }

    public record LineRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @DecimalMin("0.00") BigDecimal unitPrice
    ) {
    }

    public record Request(
            @NotNull Long customerId,
            @NotNull LocalDate pickupDate,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<LineRequest> lines,
            FulfillmentType fulfillmentType,
            @Size(max = 250) String deliveryAddress,
            @Size(max = 120) String deliveryCity,
            @Size(max = 20) String deliveryPostalCode,
            @Size(max = 30) String deliveryPhone,
            @Size(max = 500) String deliveryNotes
    ) {
        public Request(Long customerId, LocalDate pickupDate, String notes, List<LineRequest> lines) {
            this(customerId, pickupDate, notes, lines, FulfillmentType.PICKUP, null, null, null, null, null);
        }
    }

    public record CustomerRequest(@NotNull LocalDate pickupDate, @Size(max = 500) String notes,
                                  @NotEmpty @Valid List<CustomerLineRequest> lines,
                                  FulfillmentType fulfillmentType,
                                  @Size(max = 250) String deliveryAddress,
                                  @Size(max = 120) String deliveryCity,
                                  @Size(max = 20) String deliveryPostalCode,
                                  @Size(max = 30) String deliveryPhone,
                                  @Size(max = 500) String deliveryNotes) { }

    public record CustomerLineRequest(@NotNull Long productId,
                                      @NotNull @DecimalMin("0.001") BigDecimal quantity) { }

    /** Al ritiro la prenotazione diventa una vendita: serve il metodo di pagamento. */
    public record PickupRequest(@NotNull Long paymentMethodId) {
    }

    public record LineResponse(
            Long id, Long productId, String productName, String unit,
            BigDecimal quantity, BigDecimal unitPrice
    ) {
    }

    public record Response(
            Long id, Long customerId, String customerName, String customerPhone,
            CustomerOrderStatus status, LocalDate pickupDate, String notes, Instant createdAt,
            List<LineResponse> lines, BigDecimal total, FulfillmentType fulfillmentType,
            String deliveryAddress, String deliveryCity, String deliveryPostalCode,
            String deliveryPhone, String deliveryNotes
    ) {
        static Response from(CustomerOrder o, LookupService lookups) {
            List<LineResponse> lines = o.getLines().stream()
                    .map(l -> new LineResponse(l.getId(), l.getProduct().getId(), l.getProduct().getName(),
                            lookups.unitLabel(l.getProduct().getUnitId()), l.getQuantity(), l.getUnitPrice()))
                    .toList();
            BigDecimal total = lines.stream()
                    .map(l -> l.quantity().multiply(l.unitPrice()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new Response(o.getId(), o.getCustomer().getId(), o.getCustomer().getName(),
                    o.getCustomer().getPhone(), o.getStatus(), o.getPickupDate(), o.getNotes(),
                    o.getCreatedAt(), lines, total, o.getFulfillmentType(), o.getDeliveryAddress(),
                    o.getDeliveryCity(), o.getDeliveryPostalCode(), o.getDeliveryPhone(), o.getDeliveryNotes());
        }
    }
}
