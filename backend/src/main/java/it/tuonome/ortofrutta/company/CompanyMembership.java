package it.tuonome.ortofrutta.company;

import it.tuonome.ortofrutta.auth.AppRole;
import it.tuonome.ortofrutta.auth.AppUser;
import jakarta.persistence.*;

@Entity
@Table(name = "company_memberships")
public class CompanyMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private AppRole role;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = false)
    private boolean enabled = true;

    protected CompanyMembership() { }

    public CompanyMembership(Long companyId, AppUser user, AppRole role, Long customerId) {
        this.companyId = companyId;
        this.user = user;
        this.role = role;
        this.customerId = customerId;
    }

    public void setRole(AppRole role) { this.role = role; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public AppUser getUser() { return user; }
    public AppRole getRole() { return role; }
    public Long getCustomerId() { return customerId; }
    public boolean isEnabled() { return enabled; }
}
