package it.tuonome.ortofrutta.auth;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String username;

    @Column(length = 254)
    private String email;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(length = 30)
    private String phone;

    private Long customerId;

    @Column(name = "customer_account", nullable = false)
    private boolean customerAccount;

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
        this(username, null, null, passwordHash, role, mustChangePassword);
    }

    public AppUser(String username, String email, Long customerId, String passwordHash, AppRole role, boolean mustChangePassword) {
        this.username = username;
        this.email = email;
        this.customerId = customerId;
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
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public void setDisplayName(String displayName) {
        this.displayName = displayName == null || displayName.isBlank() ? null : displayName.trim();
    }
    public void setPhone(String phone) {
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
    }
    public void setCustomerAccount(boolean customerAccount) { this.customerAccount = customerAccount; }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPhone() { return phone; }
    public Long getCustomerId() { return customerId; }
    public boolean isCustomerAccount() { return customerAccount; }
    public String getPasswordHash() { return passwordHash; }
    public AppRole getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public boolean isMustChangePassword() { return mustChangePassword; }
}
