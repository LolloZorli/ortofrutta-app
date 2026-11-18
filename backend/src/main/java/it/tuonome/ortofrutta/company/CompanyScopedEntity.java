package it.tuonome.ortofrutta.company;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

@MappedSuperclass
public abstract class CompanyScopedEntity {

    public static final String RESTRICTION = "company_id = COALESCE(NULLIF(current_setting('app.company_id', true), '')::bigint, 1)";

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @PrePersist
    protected void assignCompany() {
        if (companyId == null) companyId = CompanyContext.requireId();
    }

    public Long getCompanyId() { return companyId; }
}
