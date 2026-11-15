package it.tuonome.ortofrutta.auth;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private AppRole role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean mustChangePassword;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, AppRole role, boolean mustChangePassword) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.mustChangePassword = mustChangePassword;
    }

    public void changePassword(String newHash, boolean mustChangeAgain) {
        this.passwordHash = newHash;
        this.mustChangePassword = mustChangeAgain;
    }

    public void setRole(AppRole role) { this.role = role; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public AppRole getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public boolean isMustChangePassword() { return mustChangePassword; }
}
