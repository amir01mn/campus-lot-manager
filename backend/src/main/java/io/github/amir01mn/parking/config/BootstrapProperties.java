package io.github.amir01mn.parking.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Accounts created at startup ({@code app.bootstrap.*}). A blank e-mail or password disables the super-manager
 * bootstrap: startup logs a warning instead of failing.
 */
@Validated
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(@DefaultValue @Valid SuperManager superManager) {

    public record SuperManager(
            @Email String email,
            String password,
            @DefaultValue("Super") String firstName,
            @DefaultValue("Manager") String lastName) {

        public boolean isConfigured() {
            return StringUtils.hasText(email) && StringUtils.hasText(password);
        }

        @Override
        public String toString() {
            return "SuperManager[email=" + email + ", password=***]";
        }
    }
}
