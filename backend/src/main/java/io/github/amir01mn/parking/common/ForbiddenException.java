package io.github.amir01mn.parking.common;

/** The caller is authenticated but may not act on this resource (HTTP 403). */
public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message);
    }
}
