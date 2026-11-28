package it.tuonome.ortofrutta.auth;

import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.config.AppProperties;
import it.tuonome.ortofrutta.company.CompanyContext;
import it.tuonome.ortofrutta.company.CompanyMembership;
import it.tuonome.ortofrutta.company.CompanyMembershipRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties props;
    private final RoleRepository roles;
    private final CompanyMembershipRepository memberships;
    private final PasswordResetTokenRepository resetTokens;
    private final JavaMailSender mailSender;

    public AuthService(UserRepository users, PasswordEncoder encoder,
                       JwtEncoder jwtEncoder, AppProperties props, RoleRepository roles,
                       CompanyMembershipRepository memberships, PasswordResetTokenRepository resetTokens,
                       JavaMailSender mailSender) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.roles = roles;
        this.memberships = memberships;
        this.resetTokens = resetTokens;
        this.mailSender = mailSender;
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        AppUser user = (username.contains("@") ? users.findByEmailIgnoreCase(username) : users.findByUsernameIgnoreCase(username))
                .filter(AppUser::isEnabled)
                .filter(u -> encoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Credenziali non valide"));
        CompanyMembership membership = null;
        if (!user.isCustomerAccount()) {
            membership = memberships.findByCompanyAndUserId(CompanyContext.requireId(), user.getId())
                    .orElseThrow(() -> new BadCredentialsException("Account non associato a questa azienda"));
            if (!membership.isApproved()) {
                throw new BadCredentialsException("La richiesta di accesso è in attesa di approvazione da parte dell'azienda");
            }
            if (!membership.isEnabled()) throw new BadCredentialsException("Account disattivato in questa azienda");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.jwt().expiration());
        // Il token identifica solo l'utente; i permessi aziendali vengono ricaricati a ogni richiesta.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ortofrutta")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        AuthDtos.Profile profile = user.isCustomerAccount()
                ? AuthDtos.Profile.customer(user)
                : AuthDtos.Profile.from(user, membership);
        return new AuthDtos.LoginResponse(token, expiresAt, profile);
    }

    @Transactional
    public void registerCustomer(AuthDtos.CustomerRegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        AppRole customerRole = roles.findByCode(AppRole.CUSTOMER_CODE)
                .orElseThrow(() -> new IllegalStateException("Ruolo Cliente mancante: migrazione V6 non applicata"));
        if (users.findByEmailIgnoreCase(email).isPresent() || users.findByUsernameIgnoreCase(email).isPresent()) {
            throw new BusinessException("Esiste già un account con questa email: accedi con il tuo account cliente");
        }
        AppUser user = new AppUser(email, email, null, encoder.encode(request.password()), customerRole, false);
        user.setDisplayName(request.name());
        user.setPhone(request.phone());
        user.setCustomerAccount(true);
        users.save(user);
    }

    @Transactional
    public AuthDtos.EmployeeRegistrationResponse registerEmployee(AuthDtos.EmployeeRegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent() || users.findByUsernameIgnoreCase(email).isPresent()) {
            throw new BusinessException("Esiste già un account con questa email: accedi o usa un altro indirizzo");
        }
        AppRole staffRole = roles.findByCode("STAFF")
                .orElseThrow(() -> new IllegalStateException("Ruolo Commesso mancante per questa azienda"));
        AppUser user = users.save(new AppUser(email, email, null, encoder.encode(request.password()), staffRole, false));
        user.setDisplayName(request.name());
        memberships.save(new CompanyMembership(CompanyContext.requireId(), user, staffRole, null, false, false));
        return new AuthDtos.EmployeeRegistrationResponse(
                "Richiesta inviata. Potrai accedere dopo l'approvazione dell'azienda.", "PENDING");
    }

    @Transactional(readOnly = true)
    public AuthDtos.Profile profile(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Utente non trovato"));
        if (user.isCustomerAccount()) return AuthDtos.Profile.customer(user);
        CompanyMembership membership = memberships.findActiveByCompanyAndUsername(CompanyContext.requireId(), username)
                .orElseThrow(() -> new BusinessException("Utente non trovato in questa azienda"));
        return AuthDtos.Profile.from(user, membership);
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

    @Transactional
    public void requestPasswordReset(AuthDtos.ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(email).ifPresent(user -> {
            Instant now = Instant.now();
            resetTokens.invalidateActiveForUser(user.getId(), now);
            byte[] random = new byte[32];
            RANDOM.nextBytes(random);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            Instant expiresAt = now.plus(props.passwordReset().tokenTtl());
            resetTokens.save(new PasswordResetToken(user, hash(token), expiresAt));

            String baseUrl = props.passwordReset().baseUrl();
            String resetLink = baseUrl == null || baseUrl.isBlank()
                    ? "?resetToken=" + token
                    : baseUrl.replaceAll("/+$", "") + "/?resetToken=" + token;
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(user.getEmail());
                message.setSubject("Reset password Ortofrutta");
                message.setText("Usa questo link entro 30 minuti per scegliere una nuova password:\n" + resetLink);
                mailSender.send(message);
            } catch (RuntimeException mailError) {
                LOGGER.warn("Password reset token created for {}, but email delivery is unavailable", email);
            }
        });
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        String hash = hash(request.token().trim());
        PasswordResetToken reset = resetTokens.findByTokenHash(hash)
                .filter(t -> t.isUsable(Instant.now()))
                .orElseThrow(() -> new BusinessException("Il link di reset non è valido o è scaduto"));

        AppUser user = reset.getUser();
        user.changePassword(encoder.encode(request.newPassword()), false);
        reset.markUsed();
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile proteggere il token reset", e);
        }
    }
}
