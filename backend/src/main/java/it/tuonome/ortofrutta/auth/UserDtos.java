package it.tuonome.ortofrutta.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateRequest(
            @NotBlank
            @Size(min = 3, max = 50)
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Solo lettere, numeri, punto, trattino e underscore")
            String username,
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

    public record Response(Long id, String username, Long roleId, String roleName,
                           boolean enabled, boolean mustChangePassword) {
        static Response from(AppUser u) {
            return new Response(u.getId(), u.getUsername(), u.getRole().getId(), u.getRole().getName(),
                    u.isEnabled(), u.isMustChangePassword());
        }
    }
}
