package it.tuonome.ortofrutta.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 300) String description,
            @NotNull Set<Permission> permissions
    ) {
    }

    public record Response(Long id, String code, String name, String description, boolean system,
                           List<String> permissions, long userCount) {
    }

    public record PermissionInfo(String code, String label, String group, boolean adminOnly) {
        static PermissionInfo from(Permission p) {
            return new PermissionInfo(p.name(), p.label(), p.group(), p.adminOnly());
        }
    }

    /** Elemento per la tendina "ruolo" nel modulo utenti. */
    public record Option(Long id, String name) {
    }
}
