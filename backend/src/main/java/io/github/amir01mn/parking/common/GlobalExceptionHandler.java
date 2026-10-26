package io.github.amir01mn.parking.common;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 7807 {@link ProblemDetail} responses. Spring MVC's own exceptions (malformed JSON,
 * unsupported media type, ...) are handled by {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Postgres SQLSTATE for an exclusion-constraint violation (overlapping booking). */
    static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";
    /** Postgres SQLSTATE for a unique-constraint violation. */
    static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** A single invalid field or parameter in a 400 response. */
    public record FieldViolation(String field, String message) {
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldViolation(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        ProblemDetail body = validationProblem(violations);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .sorted(Comparator.comparing(FieldViolation::field,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        ProblemDetail body = validationProblem(violations);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        return validationProblem(violations);
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "conflict", "Conflict", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.debug("Optimistic lock failure", ex);
        return problem(HttpStatus.CONFLICT, "concurrent-modification", "Concurrent modification",
                "The resource was modified by another request; reload and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        String sqlState = findSqlState(ex);
        if (SQLSTATE_EXCLUSION_VIOLATION.equals(sqlState)) {
            log.debug("Booking overlap rejected by exclusion constraint", ex);
            return problem(HttpStatus.CONFLICT, "booking-overlap", "Booking conflict",
                    "The requested time overlaps an existing confirmed booking.");
        }
        if (SQLSTATE_UNIQUE_VIOLATION.equals(sqlState)) {
            log.debug("Unique constraint violation", ex);
            return problem(HttpStatus.CONFLICT, "duplicate", "Duplicate resource",
                    "A resource with the same unique value already exists.");
        }
        log.warn("Data integrity violation (SQLSTATE {})", sqlState, ex);
        return problem(HttpStatus.CONFLICT, "data-integrity", "Data integrity violation",
                "The request conflicts with existing data.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        String detail = ex instanceof BadCredentialsException ? "Invalid email or password."
                : "Authentication is required to access this resource.";
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", detail);
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ProblemDetail handleForbidden(RuntimeException ex) {
        String detail = ex instanceof ForbiddenException ? ex.getMessage() : "You are not allowed to do this.";
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", detail);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, "business-rule", "Business rule violated",
                ex.getMessage());
        pd.setProperty("code", ex.getCode());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Internal server error",
                "An unexpected error occurred.");
    }

    private static ProblemDetail validationProblem(List<FieldViolation> violations) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "The request contains invalid fields.");
        pd.setProperty("errors", violations);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        return Problems.of(status, type, title, detail);
    }

    @Nullable
    static String findSqlState(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }
}
