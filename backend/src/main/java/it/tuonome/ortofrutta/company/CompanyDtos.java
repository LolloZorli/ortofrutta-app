package it.tuonome.ortofrutta.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class CompanyDtos {

    private CompanyDtos() { }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String companyName,
            @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "[a-zA-Z0-9]+(?:-[a-zA-Z0-9]+)*") String slug,
            @NotBlank @Size(max = 120) String ownerName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 30) String phone) { }

    public record PublicShop(Long id, String name, String slug) {
        static PublicShop from(Company company) {
            return new PublicShop(company.getId(), company.getName(), company.getSlug());
        }
    }

    public record RegisteredShop(Long id, String name, String slug, String ownerEmail, Instant createdAt) {
        static RegisteredShop from(Company company, String email) {
            return new RegisteredShop(company.getId(), company.getName(), company.getSlug(), email, company.getCreatedAt());
        }
    }
}
