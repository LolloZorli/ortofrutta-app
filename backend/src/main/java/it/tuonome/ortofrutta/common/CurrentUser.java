package it.tuonome.ortofrutta.common;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    /** Nome dell'utente che sta eseguendo l'operazione ("system" fuori da una richiesta, es. dati demo). */
    public static String name() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        return a.getName();
    }
}
