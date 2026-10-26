package io.github.amir01mn.parking.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests: full Spring context against a real PostgreSQL 16.
 *
 * <p>The container is a JVM-wide singleton started once and shared by every subclass (and therefore by the
 * cached Spring context); Testcontainers' Ryuk reaps it when the JVM exits. All subclasses use the same
 * annotations so Spring reuses one application context. The application clock is a {@link MutableClock}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
