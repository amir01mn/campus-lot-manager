package io.github.amir01mn.parking.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** HS256 signing settings for access tokens ({@code app.jwt.*}). */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
        @NotNull Duration expiry,
        @NotBlank String issuer) {

    @Override
    public String toString() {
        return "JwtProperties[secret=***, expiry=" + expiry + ", issuer=" + issuer + "]";
    }
}
