package it.tuonome.ortofrutta.common;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String what, Long id) {
        super(what + " non trovato: " + id);
    }
}
