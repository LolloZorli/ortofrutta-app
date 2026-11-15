package it.tuonome.ortofrutta.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 30) String phone,
            @Size(max = 500) String notes,
            Long typeId
    ) {
    }

    public record Response(Long id, String name, String phone, String notes, Long typeId, String type) {
    }
}
