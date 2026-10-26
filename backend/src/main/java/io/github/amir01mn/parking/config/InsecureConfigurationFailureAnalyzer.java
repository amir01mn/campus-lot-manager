package io.github.amir01mn.parking.config;

import io.github.amir01mn.parking.config.ProductionSafetyCheck.InsecureConfigurationException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Turns {@link InsecureConfigurationException} into a readable "APPLICATION FAILED TO START" report. */
public class InsecureConfigurationFailureAnalyzer extends AbstractFailureAnalyzer<InsecureConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InsecureConfigurationException cause) {
        StringBuilder description = new StringBuilder("The 'prod' profile is active but the configuration is not "
                + "safe for production:\n");
        cause.getProblems().forEach(problem -> description.append("    - ").append(problem).append('\n'));
        return new FailureAnalysis(description.toString(),
                "Set strong, unique values through the environment (APP_JWT_SECRET with at least 32 random bytes, "
                        + "e.g. `openssl rand -base64 48`; APP_SENSORS_API_KEY; APP_SUPER_MANAGER_PASSWORD), and do "
                        + "not combine 'prod' with 'demo'. See .env.example.",
                cause);
    }
}
