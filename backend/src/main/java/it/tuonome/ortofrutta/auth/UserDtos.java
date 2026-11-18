package it.tuonome.ortofrutta.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import it.tuonome.ortofrutta.company.CompanyMembership;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateRequest(
            @NotBlank
            @Size(min = 3, max = 50)
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Solo lettere, numeri, punto, trattino e underscore")
            String username,
            @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72, message = "La password deve avere da 8 a 72 caratteri") String password,
            @NotNull Long roleId
    ) {
    }

    public record UpdateRequest(@NotNull Long roleId, @NotNull Boolean enabled) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 72, message = "La password deve avere da 8 a 72 caratteri") String newPassword
    ) {
    }

    public record Response(Long id, String username, String email, Long roleId, String roleName,
                           boolean enabled, boolean mustChangePassword) {
        static Response from(CompanyMembership membership) {
            AppUser u = membership.getUser();
            return new Response(u.getId(), u.getUsername(), u.getEmail(), membership.getRole().getId(),
                    membership.getRole().getName(), membership.isEnabled() && u.isEnabled(), u.isMustChangePassword());
        }
    }
}
