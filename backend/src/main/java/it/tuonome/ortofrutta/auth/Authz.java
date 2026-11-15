package it.tuonome.ortofrutta.auth;

import org.springframework.security.core.Authentication;

public final class Authz {

    private Authz() {
    }

    /** L'utente corrente ha questo permesso? (i permessi sono caricati dal database a ogni richiesta) */
    public static boolean has(Authentication authentication, Permission permission) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> permission.name().equals(a.getAuthority()));
    }
}
