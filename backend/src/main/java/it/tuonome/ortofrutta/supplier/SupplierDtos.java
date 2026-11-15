package it.tuonome.ortofrutta.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 30) String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 500) String notes
    ) {
    }

    public record Response(Long id, String name, String phone, String email, String notes) {
        static Response from(Supplier s) {
            return new Response(s.getId(), s.getName(), s.getPhone(), s.getEmail(), s.getNotes());
        }
    }
}
