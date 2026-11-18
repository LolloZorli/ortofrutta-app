package it.tuonome.ortofrutta.auth;

import it.tuonome.ortofrutta.audit.AuditService;
import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.NotFoundException;
import it.tuonome.ortofrutta.company.CompanyContext;
import it.tuonome.ortofrutta.company.CompanyMembership;
import it.tuonome.ortofrutta.company.CompanyMembershipRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Gestione utenti: permesso USERS_MANAGE (solo amministratore). */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final CompanyMembershipRepository memberships;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder encoder, AuditService audit,
                       CompanyMembershipRepository memberships) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.audit = audit;
        this.memberships = memberships;
    }

    public List<UserDtos.Response> list() {
        return memberships.findAllByCompanyId(CompanyContext.requireId()).stream()
                .filter(m -> !m.getRole().isCustomerRole()).map(UserDtos.Response::from).toList();
    }

    @Transactional
    public UserDtos.Response approve(Long id) {
        CompanyMembership membership = findMembership(id);
        if (membership.getRole().isCustomerRole()) throw new BusinessException("L'account cliente non richiede approvazione");
        if (membership.isApproved()) throw new BusinessException("La richiesta è già stata approvata");
        membership.approve();
        audit.record("USER_APPROVE", "USER", id, membership.getUser().getUsername());
        return UserDtos.Response.from(membership);
    }

    /** La password iniziale è temporanea: al primo accesso l'utente deve sceglierne una sua. */
    @Transactional
    public UserDtos.Response create(UserDtos.CreateRequest r) {
        String username = r.username().trim().toLowerCase(Locale.ROOT);
        String email = r.email() == null || r.email().isBlank() ? null : r.email().trim().toLowerCase(Locale.ROOT);
        AppRole role = findRole(r.roleId());
        if (role.isCustomerRole()) throw new BusinessException("Gli account cliente vengono creati dalla registrazione clienti");
        AppUser byName = users.findByUsernameIgnoreCase(username).orElse(null);
        AppUser byEmail = email == null ? null : users.findByEmailIgnoreCase(email).orElse(null);
        if (byName != null && byEmail != null && !byName.getId().equals(byEmail.getId())) {
            throw new BusinessException("Nome utente ed email appartengono a account diversi");
        }
        AppUser u = byName != null ? byName : byEmail;
        if (u != null) {
            if (!encoder.matches(r.password(), u.getPasswordHash())) {
                throw new BusinessException("Account esistente: password non valida per associarlo a questa azienda");
            }
            if (memberships.existsByCompanyIdAndUserId(CompanyContext.requireId(), u.getId())) {
                throw new BusinessException("Questo account è già presente in questa azienda");
            }
        } else {
            if (users.existsByUsername(username) || (email != null && users.existsByEmailIgnoreCase(email))) {
                throw new BusinessException("Utente o email già utilizzati");
            }
            u = users.save(new AppUser(username, email, null, encoder.encode(r.password()), role, true));
        }
        CompanyMembership membership = memberships.save(new CompanyMembership(CompanyContext.requireId(), u, role, null));
        audit.record("USER_CREATE", "USER", u.getId(), username + " (" + role.getName() + ")");
        return UserDtos.Response.from(membership);
    }

    /** Cambia ruolo o attiva/disattiva. Non si può modificare sé stessi: evita di restare senza amministratori. */
    @Transactional
    public UserDtos.Response update(Long id, UserDtos.UpdateRequest r, String currentUsername) {
        CompanyMembership membership = findMembership(id);
        AppUser u = membership.getUser();
        if (u.getUsername().equals(currentUsername)) {
            throw new BusinessException("Non puoi modificare il tuo stesso ruolo o stato");
        }
        AppRole role = findRole(r.roleId());
        if (role.isCustomerRole()) throw new BusinessException("Il ruolo Cliente si assegna solo tramite la registrazione clienti");
        membership.setRole(role);
        membership.setEnabled(r.enabled());
        audit.record("USER_UPDATE", "USER", id, u.getUsername() + ": " + role.getName()
                + (r.enabled() ? "" : " (disattivato)"));
        return UserDtos.Response.from(membership);
    }

    @Transactional
    public UserDtos.Response resetPassword(Long id, UserDtos.ResetPasswordRequest r) {
        CompanyMembership membership = findMembership(id);
        AppUser u = membership.getUser();
        if (memberships.countByUserId(id) != 1) {
            throw new BusinessException("Questo account è condiviso: la password va cambiata dall'utente");
        }
        u.changePassword(encoder.encode(r.newPassword()), true);
        audit.record("USER_PASSWORD_RESET", "USER", id, u.getUsername());
        return UserDtos.Response.from(membership);
    }

    private CompanyMembership findMembership(Long userId) {
        return memberships.findByCompanyAndUserId(CompanyContext.requireId(), userId)
                .orElseThrow(() -> new NotFoundException("Utente", userId));
    }

    private AppRole findRole(Long id) {
        return roles.findById(id).orElseThrow(() -> new NotFoundException("Ruolo", id));
    }
}
