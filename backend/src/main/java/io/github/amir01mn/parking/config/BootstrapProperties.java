package io.github.amir01mn.parking.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Accounts created at startup ({@code app.bootstrap.*}). */
@Validated
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(@NotNull @Valid SuperManager superManager) {

    public record SuperManager(
            @NotBlank @Email String email,
            @NotBlank String password,
            @DefaultValue("Super") String firstName,
            @DefaultValue("Manager") String lastName) {

        @Override
        public String toString() {
            return "SuperManager[email=" + email + ", password=***]";
        }
    }
}
