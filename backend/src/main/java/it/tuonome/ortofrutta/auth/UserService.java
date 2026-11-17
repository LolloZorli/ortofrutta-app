package it.tuonome.ortofrutta.auth;

import it.tuonome.ortofrutta.audit.AuditService;
import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.NotFoundException;
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

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.audit = audit;
    }

    public List<UserDtos.Response> list() {
        return users.findAllWithRole().stream().map(UserDtos.Response::from).toList();
    }

    /** La password iniziale è temporanea: al primo accesso l'utente deve sceglierne una sua. */
    @Transactional
    public UserDtos.Response create(UserDtos.CreateRequest r) {
        String username = r.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw new BusinessException("Esiste già un utente con questo nome");
        }
        String email = r.email() == null || r.email().isBlank() ? null : r.email().trim().toLowerCase(Locale.ROOT);
        if (email != null && users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Questa email è già associata a un utente");
        }
        AppRole role = findRole(r.roleId());
        if (role.isCustomerRole()) throw new BusinessException("Gli account cliente vengono creati dalla registrazione clienti");
        AppUser u = users.save(new AppUser(username, email, null, encoder.encode(r.password()), role, true));
        audit.record("USER_CREATE", "USER", u.getId(), username + " (" + role.getName() + ")");
        return UserDtos.Response.from(u);
    }

    /** Cambia ruolo o attiva/disattiva. Non si può modificare sé stessi: evita di restare senza amministratori. */
    @Transactional
    public UserDtos.Response update(Long id, UserDtos.UpdateRequest r, String currentUsername) {
        AppUser u = find(id);
        if (u.getUsername().equals(currentUsername)) {
            throw new BusinessException("Non puoi modificare il tuo stesso ruolo o stato");
        }
        AppRole role = findRole(r.roleId());
        if (role.isCustomerRole()) throw new BusinessException("Il ruolo Cliente si assegna solo tramite la registrazione clienti");
        u.setRole(role);
        u.setEnabled(r.enabled());
        audit.record("USER_UPDATE", "USER", id, u.getUsername() + ": " + role.getName()
                + (r.enabled() ? "" : " (disattivato)"));
        return UserDtos.Response.from(u);
    }

    @Transactional
    public UserDtos.Response resetPassword(Long id, UserDtos.ResetPasswordRequest r) {
        AppUser u = find(id);
        u.changePassword(encoder.encode(r.newPassword()), true);
        audit.record("USER_PASSWORD_RESET", "USER", id, u.getUsername());
        return UserDtos.Response.from(u);
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Utente", id));
    }

    private AppRole findRole(Long id) {
        return roles.findById(id).orElseThrow(() -> new NotFoundException("Ruolo", id));
    }
}
