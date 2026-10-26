package io.github.amir01mn.parking.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.amir01mn.parking.CampusParkingApplication;
import io.github.amir01mn.parking.config.ProductionSafetyCheck.InsecureConfigurationException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyCheckTest {

    private static final String STRONG_SECRET = "q8Vx2mZ7rT4kLp9WcN3hJ6dF1sYbGu5e";   // 32 bytes

    private final ProductionSafetyCheck check = new ProductionSafetyCheck();
    private final SpringApplication application = new SpringApplication();

    /** The real application.yml (base document), resolved without any environment overrides. */
    private static MockEnvironment withApplicationDefaults(String... profiles) throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addLast(documents.get(0));
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private static MockEnvironment prodWith(String jwtSecret, String sensorKey, String superPassword) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.jwt.secret", jwtSecret)
                .withProperty("app.sensors.api-key", sensorKey)
                .withProperty("app.bootstrap.super-manager.password", superPassword);
        environment.setActiveProfiles("prod");
        return environment;
    }

    @Test
    void prodWithTheApplicationYmlDefaultsReportsEverySecret() throws IOException {
        MockEnvironment environment = withApplicationDefaults("prod");

        assertThatThrownBy(() -> check.postProcessEnvironment(environment, application))
                .isInstanceOfSatisfying(InsecureConfigurationException.class, e -> assertThat(e.getProblems())
                        .hasSize(3)
                        .anySatisfy(p -> assertThat(p).contains("APP_JWT_SECRET").contains("development default"))
                        .anySatisfy(p -> assertThat(p).contains("APP_SENSORS_API_KEY"))
                        .anySatisfy(p -> assertThat(p).contains("APP_SUPER_MANAGER_PASSWORD")));
    }

    @Test
    void theDevDefaultsStillWorkOutsideProd() throws IOException {
        assertThatCode(() -> check.postProcessEnvironment(withApplicationDefaults(), application))
                .doesNotThrowAnyException();
        assertThatCode(() -> check.postProcessEnvironment(withApplicationDefaults("demo"), application))
                .doesNotThrowAnyException();
    }

    @Test
    void prodWithStrongValuesStarts() {
        assertThatCode(() -> check.postProcessEnvironment(
                prodWith(STRONG_SECRET, "a-real-sensor-key", "An0ther!Secret"), application))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAJwtSecretShorterThan32Bytes() {
        assertThat(ProductionSafetyCheck.check(prodWith(STRONG_SECRET.substring(1), "key", "Pw!12345678")))
                .containsExactly("app.jwt.secret (APP_JWT_SECRET) must be at least 32 bytes for HS256.");
        assertThat(ProductionSafetyCheck.check(prodWith("", "key", "Pw!12345678"))).hasSize(1);
    }

    @Test
    void rejectsTheDemoProfileAlongsideProd() {
        MockEnvironment environment = prodWith(STRONG_SECRET, "a-real-sensor-key", "An0ther!Secret");
        environment.setActiveProfiles("prod", "demo");

        assertThat(ProductionSafetyCheck.check(environment)).singleElement().asString().contains("'demo'");
    }

    @Test
    void theApplicationRefusesToStartBeforeTouchingTheDatabase() {
        // no datasource is reachable here: the guard must stop startup first
        SpringApplicationBuilder builder = new SpringApplicationBuilder(CampusParkingApplication.class)
                .profiles("prod")
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off");

        assertThatThrownBy(builder::run).isInstanceOf(InsecureConfigurationException.class)
                .hasMessageStartingWith("Refusing to start with the 'prod' profile");
    }

    @Test
    void failureAnalysisListsTheProblemsAndTheFix() {
        InsecureConfigurationException failure = new InsecureConfigurationException(List.of("problem one"));

        FailureAnalysis analysis = new InsecureConfigurationFailureAnalyzer().analyze(failure);

        assertThat(analysis.getDescription()).contains("- problem one");
        assertThat(analysis.getAction()).contains("APP_JWT_SECRET").contains(".env.example");
    }
}
