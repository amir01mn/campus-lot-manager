package io.github.amir01mn.parking.config;

import io.github.amir01mn.parking.user.UserType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Hourly rates per pricing tier ({@code app.pricing.*}). */
@Validated
@ConfigurationProperties(prefix = "app.pricing")
public record PricingProperties(@NotBlank String currency, @NotNull @Valid Rates rates) {

    public record Rates(
            @NotNull @Positive BigDecimal student,
            @NotNull @Positive BigDecimal faculty,
            @NotNull @Positive BigDecimal staff,
            @NotNull @Positive BigDecimal visitor) {

        public BigDecimal forType(UserType type) {
            return switch (type) {
                case STUDENT -> student;
                case FACULTY -> faculty;
                case STAFF -> staff;
                case VISITOR -> visitor;
            };
        }
    }
}
