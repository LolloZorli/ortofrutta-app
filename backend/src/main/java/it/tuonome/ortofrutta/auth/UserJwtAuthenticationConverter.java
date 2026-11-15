package it.tuonome.ortofrutta.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Trasforma un token valido in un'autenticazione con i permessi ATTUALI dell'utente, letti dal database
 * (una query sola: utente + ruolo + permessi).
 * - utente inesistente o disattivato: token rifiutato (401);
 * - utente che deve cambiare password: nessun permesso, può usare solo /api/auth/me e /api/auth/change-password;
 * - altrimenti: l'autorità "ACCESS" (utente attivo) più un'autorità per ogni permesso del suo ruolo.
 */
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ACCESS = "ACCESS";

    private final UserRepository users;

    public UserJwtAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser user = users.findWithRoleByUsername(jwt.getSubject())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Utente non valido o disattivato"));

        List<GrantedAuthority> authorities = new ArrayList<>();
        if (!user.isMustChangePassword()) {
            authorities.add(new SimpleGrantedAuthority(ACCESS));
            for (Permission p : user.getRole().effectivePermissions()) {
                authorities.add(new SimpleGrantedAuthority(p.name()));
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, user.getUsername());
    }
}
