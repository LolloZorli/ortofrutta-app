package it.tuonome.ortofrutta.auth;

import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.customer.Customer;
import it.tuonome.ortofrutta.customer.CustomerRepository;
import it.tuonome.ortofrutta.config.AppProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties props;
    private final CustomerRepository customers;
    private final RoleRepository roles;

    public AuthService(UserRepository users, PasswordEncoder encoder,
                       JwtEncoder jwtEncoder, AppProperties props, CustomerRepository customers, RoleRepository roles) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.customers = customers;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        AppUser user = (username.contains("@") ? users.findWithRoleByEmailIgnoreCase(username) : users.findWithRoleByUsername(username))
                .filter(AppUser::isEnabled)
                .filter(u -> encoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Credenziali non valide"));

        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.jwt().expiration());
        // Il token identifica solo l'utente: ruolo e permessi si rileggono dal database a ogni richiesta,
        // così un utente disattivato o con permessi cambiati li perde subito.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ortofrutta")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AuthDtos.LoginResponse(token, expiresAt, AuthDtos.Profile.from(user));
    }

    @Transactional
    public void registerCustomer(AuthDtos.CustomerRegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email) || users.existsByUsername(email)) {
            throw new BusinessException("Esiste già un account con questa email");
        }
        AppRole customerRole = roles.findByCode(AppRole.CUSTOMER_CODE)
                .orElseThrow(() -> new IllegalStateException("Ruolo Cliente mancante: migrazione V6 non applicata"));
        Customer customer = customers.save(new Customer(request.name(), request.phone(), null, null));
        users.save(new AppUser(email, email, customer.getId(), encoder.encode(request.password()), customerRole, false));
    }

    @Transactional(readOnly = true)
    public AuthDtos.Profile profile(String username) {
        return AuthDtos.Profile.from(users.findWithRoleByUsername(username)
                .orElseThrow(() -> new BusinessException("Utente non trovato")));
    }

    @Transactional
    public void changePassword(String username, AuthDtos.ChangePasswordRequest request) {
        AppUser u = users.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Utente non trovato"));
        if (!encoder.matches(request.currentPassword(), u.getPasswordHash())) {
            throw new BusinessException("La password attuale non è corretta");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("La nuova password deve essere diversa da quella attuale");
        }
        u.changePassword(encoder.encode(request.newPassword()), false);
    }
}
