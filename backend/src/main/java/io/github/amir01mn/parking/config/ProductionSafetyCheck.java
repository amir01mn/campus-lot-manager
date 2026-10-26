package io.github.amir01mn.parking.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Fail-fast guard for the {@code prod} profile. It runs while the environment is prepared, before any bean or
 * database connection exists, and stops startup when a secret still has its development default from
 * {@code application.yml}, when the JWT secret is shorter than 32 bytes, or when {@code demo} (which seeds accounts
 * with a published password) is active alongside {@code prod}. {@link InsecureConfigurationFailureAnalyzer} prints
 * the problems as a readable startup failure. Registered in {@code META-INF/spring.factories}.
 */
public class ProductionSafetyCheck implements EnvironmentPostProcessor, Ordered {

    public static final String PROD_PROFILE = "prod";

    /** The development defaults in {@code application.yml}; a test keeps these in sync with the file. */
    static final String DEV_JWT_SECRET = "dev-only-change-me-dev-only-change-me-0123456789";
    static final String DEV_SENSOR_API_KEY = "dev-sensor-key";
    static final String DEV_SUPER_MANAGER_PASSWORD = "ChangeMe!2024";

    static final int MIN_JWT_SECRET_BYTES = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            List<String> problems = check(environment);
            if (!problems.isEmpty()) {
                throw new InsecureConfigurationException(problems);
            }
        }
    }

    /** Everything wrong with the configuration for production use (empty when it is safe). */
    static List<String> check(Environment environment) {
        List<String> problems = new ArrayList<>();
        String jwtSecret = environment.getProperty("app.jwt.secret", "");
        if (Objects.equals(jwtSecret, DEV_JWT_SECRET)) {
            problems.add("app.jwt.secret (APP_JWT_SECRET) still has its development default.");
        } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            problems.add("app.jwt.secret (APP_JWT_SECRET) must be at least " + MIN_JWT_SECRET_BYTES
                    + " bytes for HS256.");
        }
        if (Objects.equals(environment.getProperty("app.sensors.api-key"), DEV_SENSOR_API_KEY)) {
            problems.add("app.sensors.api-key (APP_SENSORS_API_KEY) still has its development default.");
        }
        if (Objects.equals(environment.getProperty("app.bootstrap.super-manager.password"),
                DEV_SUPER_MANAGER_PASSWORD)) {
            problems.add("app.bootstrap.super-manager.password (APP_SUPER_MANAGER_PASSWORD) still has its "
                    + "development default.");
        }
        if (environment.acceptsProfiles(Profiles.of("demo"))) {
            problems.add("The 'demo' profile (demo accounts with a published password) must not run with 'prod'.");
        }
        return problems;
    }

    @Override
    public int getOrder() {
        // after ConfigDataEnvironmentPostProcessor has loaded application.yml and activated the profiles
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** Thrown when the {@code prod} profile is started with unsafe settings. */
    public static class InsecureConfigurationException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        private final transient List<String> problems;

        public InsecureConfigurationException(List<String> problems) {
            super("Refusing to start with the 'prod' profile: " + String.join(" ", problems));
            this.problems = List.copyOf(problems);
        }

        public List<String> getProblems() {
            return problems;
        }
    }
}
