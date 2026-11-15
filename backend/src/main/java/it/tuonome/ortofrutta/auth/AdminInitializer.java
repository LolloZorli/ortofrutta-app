package it.tuonome.ortofrutta.auth;

import it.tuonome.ortofrutta.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Crea l'amministratore al primo avvio, se non esiste nessun utente. */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AdminInitializer(UserRepository users, RoleRepository roles, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        AppRole admin = roles.findByCode(AppRole.ADMIN_CODE)
                .orElseThrow(() -> new IllegalStateException("Ruolo ADMIN mancante: migrazione V3 non applicata?"));
        String username = props.admin().username().trim().toLowerCase(Locale.ROOT);
        boolean mustChange = props.admin().mustChangePassword();
        users.save(new AppUser(username, encoder.encode(props.admin().password()), admin, mustChange));
        log.warn("Creato amministratore '{}'{}.", username,
                mustChange ? ": dovrà cambiare la password al primo accesso" : " (cambio password NON richiesto)");
    }
}
