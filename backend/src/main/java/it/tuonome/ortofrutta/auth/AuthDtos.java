package it.tuonome.ortofrutta.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record Profile(String username, String roleCode, String roleName,
                          List<String> permissions, boolean mustChangePassword) {
        static Profile from(AppUser u) {
            return new Profile(u.getUsername(), u.getRole().getCode(), u.getRole().getName(),
                    u.getRole().effectivePermissions().stream().map(Enum::name).sorted().toList(),
                    u.isMustChangePassword());
        }
    }

    public record LoginResponse(String token, Instant expiresAt, Profile profile) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "La password deve avere da 8 a 72 caratteri") String newPassword
    ) {
    }
}
