package io.github.amir01mn.parking.common;

/** The request conflicts with the current state, e.g. a duplicate e-mail or a booking overlap (HTTP 409). */
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
