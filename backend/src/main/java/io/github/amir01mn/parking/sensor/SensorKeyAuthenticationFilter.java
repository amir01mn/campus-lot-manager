package io.github.amir01mn.parking.sensor;

import io.github.amir01mn.parking.config.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates {@code /api/sensors/**} requests by the {@code X-Sensor-Key} header. The key is compared in
 * constant time (both sides are hashed to a fixed length first, so not even the length leaks). A missing or wrong
 * key gets a 401 ProblemDetail; a correct one authenticates the request with {@value #SENSOR_ROLE}.
 *
 * <p>Not a Spring bean on purpose: it is added to the security filter chain only, never as a global servlet filter.
 */
public class SensorKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Sensor-Key";
    public static final String SENSOR_ROLE = "ROLE_SENSOR";
    static final String PATH_PREFIX = "/api/sensors/";

    private static final Logger log = LoggerFactory.getLogger(SensorKeyAuthenticationFilter.class);

    private final byte[] expectedDigest;
    private final ProblemDetailWriter problemWriter;

    public SensorKeyAuthenticationFilter(String apiKey, ProblemDetailWriter problemWriter) {
        this.expectedDigest = sha256(Objects.requireNonNull(apiKey, "apiKey"));
        this.problemWriter = problemWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null || presented.isEmpty()) {
            reject(request, response, "The " + HEADER + " header is required.");
            return;
        }
        if (!MessageDigest.isEqual(expectedDigest, sha256(presented))) {
            log.warn("Rejected sensor request with a wrong {} from {}", HEADER, request.getRemoteAddr());
            reject(request, response, "The " + HEADER + " header is invalid.");
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("sensor-gateway", null,
                List.of(new SimpleGrantedAuthority(SENSOR_ROLE))));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String detail) throws IOException {
        problemWriter.write(request, response, HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", detail);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
