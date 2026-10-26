package io.github.amir01mn.parking.common;

import java.util.Objects;

/**
 * A well-formed request violates a domain rule, e.g. booking shorter than 30 minutes or a disabled spot
 * (HTTP 422). {@code code} is a stable machine-readable identifier exposed to clients.
 */
public class BusinessRuleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public String getCode() {
        return code;
    }
}
