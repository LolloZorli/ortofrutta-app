package it.tuonome.ortofrutta.company;

import it.tuonome.ortofrutta.auth.AppRole;
import it.tuonome.ortofrutta.auth.AppUser;
import it.tuonome.ortofrutta.auth.RoleRepository;
import it.tuonome.ortofrutta.auth.UserRepository;
import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.lookup.LookupType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CompanyService {

    private static final long TEMPLATE_COMPANY_ID = CompanyContext.LEGACY_COMPANY_ID;

    private final CompanyRepository companies;
    private final UserRepository users;
    private final RoleRepository roles;
    private final CompanyMembershipRepository memberships;
    private final PasswordEncoder encoder;
    private final JdbcClient jdbc;

    public CompanyService(CompanyRepository companies, UserRepository users, RoleRepository roles,
                          CompanyMembershipRepository memberships, PasswordEncoder encoder, JdbcClient jdbc) {
        this.companies = companies;
        this.users = users;
        this.roles = roles;
        this.memberships = memberships;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    public List<CompanyDtos.PublicShop> shops() {
        return companies.findAllByEnabledTrueOrderByNameAsc().stream().map(CompanyDtos.PublicShop::from).toList();
    }

    @Transactional
    public CompanyDtos.RegisteredShop register(CompanyDtos.RegisterRequest request) {
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (companies.existsBySlugIgnoreCase(slug)) {
            throw new BusinessException("Questo indirizzo negozio è già utilizzato");
        }

        AppUser owner = users.findByEmailIgnoreCase(email).orElseGet(() -> users.findByUsernameIgnoreCase(email).orElse(null));
        if (owner != null && owner.isCustomerAccount()) {
            throw new BusinessException("Un account cliente non può essere usato come account aziendale");
        }
        if (owner != null && !encoder.matches(request.password(), owner.getPasswordHash())) {
            throw new BusinessException("Account esistente: password non valida per creare una nuova azienda");
        }

        Company company = companies.saveAndFlush(new Company(request.companyName(), slug));
        copyRolesAndLookups(company.getId());
        CompanyContext.set(company.getId());
        jdbc.sql("select set_config('app.company_id', :companyId, true)")
                .param("companyId", company.getId().toString()).query(String.class).single();

        AppRole adminRole = roles.findByCode(AppRole.ADMIN_CODE)
                .orElseThrow(() -> new IllegalStateException("Ruolo ADMIN non copiato per l'azienda"));
        if (owner == null) {
            owner = users.save(new AppUser(email, email, null, encoder.encode(request.password()), adminRole, false));
        } else if (memberships.existsByCompanyIdAndUserId(company.getId(), owner.getId())) {
            throw new BusinessException("Questo account è già associato all'azienda");
        }
        if (owner.getDisplayName() == null) owner.setDisplayName(request.ownerName());
        memberships.save(new CompanyMembership(company.getId(), owner, adminRole, null));
        return CompanyDtos.RegisteredShop.from(company, email);
    }

    private void copyRolesAndLookups(Long companyId) {
        jdbc.sql("""
                insert into roles(company_id, code, name, description, system_role)
                select :companyId, code, name, description, system_role
                from roles where company_id = :templateCompanyId
                """).param("companyId", companyId).param("templateCompanyId", TEMPLATE_COMPANY_ID).update();
        jdbc.sql("""
                insert into role_permissions(role_id, permission)
                select new_role.id, rp.permission
                from role_permissions rp
                join roles old_role on old_role.id = rp.role_id
                join roles new_role on new_role.company_id = :companyId and new_role.code = old_role.code
                where old_role.company_id = :templateCompanyId
                """).param("companyId", companyId).param("templateCompanyId", TEMPLATE_COMPANY_ID).update();

        for (LookupType type : LookupType.values()) {
            String columns = "company_id, code, name, sort_order, active";
            String select = ":companyId, code, name, sort_order, active";
            if (type.hasUnitAttributes()) {
                columns += ", short_label, allow_decimals";
                select += ", short_label, allow_decimals";
            }
            if (type.hasRate()) {
                columns += ", rate";
                select += ", rate";
            }
            jdbc.sql("insert into " + type.table() + " (" + columns + ") select " + select + " from "
                            + type.table() + " where company_id = :templateCompanyId")
                    .param("companyId", companyId).param("templateCompanyId", TEMPLATE_COMPANY_ID).update();
        }
    }
}
