package it.tuonome.ortofrutta.auth;

import jakarta.persistence.*;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class AppRole {

    public static final String ADMIN_CODE = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new HashSet<>();

    protected AppRole() {
    }

    public AppRole(String code, String name, String description, Set<Permission> permissions) {
        this.code = code;
        update(name, description, permissions);
    }

    public void update(String name, String description, Set<Permission> permissions) {
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.permissions = new HashSet<>(permissions);
    }

    public boolean isAdminRole() {
        return systemRole && ADMIN_CODE.equals(code);
    }

    /** L'amministratore ha sempre tutti i permessi, anche quelli aggiunti in versioni future. */
    public Set<Permission> effectivePermissions() {
        if (isAdminRole()) {
            return EnumSet.allOf(Permission.class);
        }
        return permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isSystemRole() { return systemRole; }
}
