package it.tuonome.ortofrutta.common;

/** Violazione di una regola di business: viene tradotta in HTTP 409. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
