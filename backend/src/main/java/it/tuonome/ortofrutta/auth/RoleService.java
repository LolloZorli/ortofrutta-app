package it.tuonome.ortofrutta.auth;

import it.tuonome.ortofrutta.audit.AuditService;
import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.NotFoundException;
import it.tuonome.ortofrutta.company.CompanyContext;
import it.tuonome.ortofrutta.company.CompanyMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Ruoli personalizzabili: l'amministratore sceglie i permessi, tranne quelli riservati a lui. */
@Service
@Transactional(readOnly = true)
public class RoleService {

    /** Chi fa queste cose ha bisogno di leggere il listino. */
    private static final Set<Permission> NEED_PRODUCTS_READ = EnumSet.of(
            Permission.SALES_CREATE, Permission.STOCK_READ, Permission.STOCK_RECEIVE, Permission.WASTE_CREATE,
            Permission.SUPPLIER_ORDER_CREATE, Permission.SUPPLIER_ORDERS_MANAGE,
            Permission.RESERVATIONS_MANAGE, Permission.PRODUCTS_WRITE);
    /** Chi carica merce o fa ordini deve poter scegliere il fornitore. */
    private static final Set<Permission> NEED_SUPPLIERS_READ = EnumSet.of(
            Permission.STOCK_RECEIVE, Permission.SUPPLIER_ORDER_CREATE, Permission.SUPPLIER_ORDERS_MANAGE);
    private static final Set<Permission> NEED_STOCK_READ = EnumSet.of(
            Permission.STOCK_RECEIVE, Permission.WASTE_CREATE);
    private static final Set<Permission> NEED_CUSTOMERS_MANAGE = EnumSet.of(Permission.CUSTOMERS_DELETE);
    private static final Set<Permission> NEED_USERS_READ = EnumSet.of(Permission.USERS_APPROVE);
    private static final Set<Permission> NEED_CUSTOMER_ORDER_READ = EnumSet.of(Permission.CUSTOMER_ORDER_CREATE);
    private static final Set<Permission> NEED_CATALOG_READ = EnumSet.of(
            Permission.CUSTOMER_ORDER_READ, Permission.CUSTOMER_ORDER_CREATE);
    private static final Set<Permission> NEED_SALES_CREATE = EnumSet.of(Permission.SALES_PRICE_OVERRIDE);

    private final RoleRepository roles;
    private final AuditService audit;
    private final CompanyMembershipRepository memberships;

    public RoleService(RoleRepository roles, AuditService audit, CompanyMembershipRepository memberships) {
        this.roles = roles;
        this.audit = audit;
        this.memberships = memberships;
    }

    public List<RoleDtos.Response> list() {
        return roles.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    public List<RoleDtos.PermissionInfo> permissionCatalog() {
        return Arrays.stream(Permission.values()).map(RoleDtos.PermissionInfo::from).toList();
    }

    public List<RoleDtos.Option> options() {
        return roles.findAllByOrderByNameAsc().stream().filter(r -> !r.isCustomerRole())
                .map(r -> new RoleDtos.Option(r.getId(), r.getCode(), r.getName())).toList();
    }

    @Transactional
    public RoleDtos.Response create(RoleDtos.Request r) {
        String name = r.name().trim();
        if (roles.existsByNameIgnoreCase(name)) {
            throw new BusinessException("Esiste già un ruolo con questo nome");
        }
        AppRole role = roles.save(new AppRole(uniqueCode(name), name, r.description(), normalize(r.permissions())));
        audit.record("ROLE_CREATE", "ROLE", role.getId(), name + ": " + role.effectivePermissions().size() + " permessi");
        return toResponse(role);
    }

    @Transactional
    public RoleDtos.Response update(Long id, RoleDtos.Request r) {
        AppRole role = find(id);
        if (role.isAdminRole() || role.isCustomerRole()) {
            throw new BusinessException("Questo ruolo di sistema non è modificabile");
        }
        if (roles.existsByNameIgnoreCaseAndIdNot(r.name().trim(), id)) {
            throw new BusinessException("Esiste già un ruolo con questo nome");
        }
        role.update(r.name(), r.description(), normalize(r.permissions()));
        audit.record("ROLE_UPDATE", "ROLE", id, role.getName() + ": " + role.effectivePermissions().size() + " permessi");
        return toResponse(role);
    }

    @Transactional
    public void delete(Long id) {
        AppRole role = find(id);
        if (role.isSystemRole()) {
            throw new BusinessException("Questo ruolo di sistema non si può eliminare");
        }
        long inUse = memberships.countByCompanyIdAndRoleId(CompanyContext.requireId(), id);
        if (inUse > 0) {
            throw new BusinessException("Il ruolo è assegnato a " + inUse + " utenti: riassegnali prima di eliminarlo");
        }
        roles.delete(role);
        roles.flush();
        audit.record("ROLE_DELETE", "ROLE", id, role.getName());
    }

    // ---------------------------------- interni ----------------------------------

    private Set<Permission> normalize(Set<Permission> requested) {
        for (Permission p : requested) {
            if (p.adminOnly()) {
                throw new BusinessException("Il permesso \"" + p.label() + "\" è riservato all'amministratore");
            }
        }
        Set<Permission> result = requested.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(requested);
        if (result.stream().anyMatch(NEED_PRODUCTS_READ::contains)) {
            result.add(Permission.PRODUCTS_READ);
        }
        if (result.stream().anyMatch(NEED_SUPPLIERS_READ::contains)) {
            result.add(Permission.SUPPLIERS_READ);
        }
        if (result.stream().anyMatch(NEED_STOCK_READ::contains)) {
            result.add(Permission.STOCK_READ);
        }
        if (result.stream().anyMatch(NEED_CUSTOMERS_MANAGE::contains)) {
            result.add(Permission.CUSTOMERS_MANAGE);
        }
        if (result.stream().anyMatch(NEED_USERS_READ::contains)) {
            result.add(Permission.USERS_READ);
        }
        if (result.stream().anyMatch(NEED_CUSTOMER_ORDER_READ::contains)) {
            result.add(Permission.CUSTOMER_ORDER_READ);
        }
        if (result.stream().anyMatch(NEED_CATALOG_READ::contains)) {
            result.add(Permission.CATALOG_READ);
        }
        if (result.stream().anyMatch(NEED_SALES_CREATE::contains)) {
            result.add(Permission.SALES_CREATE);
        }
        return result;
    }

    private RoleDtos.Response toResponse(AppRole r) {
        return new RoleDtos.Response(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isSystemRole(),
                r.effectivePermissions().stream().map(Enum::name).sorted().toList(),
                memberships.countByCompanyIdAndRoleId(CompanyContext.requireId(), r.getId()));
    }

    private AppRole find(Long id) {
        return roles.findById(id).orElseThrow(() -> new NotFoundException("Ruolo", id));
    }

    private String uniqueCode(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "RUOLO";
        }
        if (base.length() > 30) {
            base = base.substring(0, 30);
        }
        String code = base;
        int n = 2;
        while (roles.existsByCode(code)) {
            code = base + "_" + n++;
        }
        return code;
    }
}
