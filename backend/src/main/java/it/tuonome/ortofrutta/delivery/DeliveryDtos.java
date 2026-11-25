package it.tuonome.ortofrutta.delivery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class DeliveryDtos {
    private DeliveryDtos() {}

    public record SlotRequest(Long id, @NotBlank @Size(max = 80) String name,
                              @NotNull @Min(1) Integer maxOrders, boolean active) {}

    public record ZoneRequest(@NotBlank @Size(max = 120) String city,
                              @NotNull @DecimalMin("0.00") BigDecimal deliveryFee,
                              @NotNull @DecimalMin("0.00") BigDecimal minimumOrderSubtotal,
                              boolean active, @NotEmpty @Valid List<SlotRequest> slots) {}

    public record SlotResponse(Long id, String name, int maxOrders, boolean active) {}
    public record ZoneResponse(Long id, String city, BigDecimal deliveryFee,
                               BigDecimal minimumOrderSubtotal, boolean active, List<SlotResponse> slots) {}

    public record AvailableSlot(Long id, String name, int remainingOrders) {}
    public record OptionsResponse(boolean zoneAvailable, String city, BigDecimal deliveryFee,
                                  BigDecimal minimumOrderSubtotal, List<AvailableSlot> slots) {}

    public record Terms(Long zoneId, Long slotId, String slotName, BigDecimal fee) {}
}
