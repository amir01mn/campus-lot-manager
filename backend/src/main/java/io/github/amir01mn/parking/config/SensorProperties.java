package io.github.amir01mn.parking.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Sensor ingestion settings ({@code app.sensors.*}).
 *
 * @param apiKey       shared secret expected in the {@code X-Sensor-Key} header
 * @param maxClockSkew readings timestamped further than this into the future are not applied
 */
@Validated
@ConfigurationProperties(prefix = "app.sensors")
public record SensorProperties(
        @NotBlank String apiKey,
        @DefaultValue("PT5M") Duration maxClockSkew,
        @DefaultValue @Valid Simulator simulator) {

    @Override
    public String toString() {
        return "SensorProperties[apiKey=***, maxClockSkew=" + maxClockSkew + ", simulator=" + simulator + "]";
    }

    /**
     * Demo-only reading generator (active only with the {@code demo} profile and {@code enabled=true}).
     *
     * @param interval             delay between ticks
     * @param sampleFraction       share of all spots that report on each tick
     * @param coveredOccupancy     probability that a spot with a booking covering now reports occupied
     * @param uncoveredOccupancy   probability that any other spot reports occupied (each such transition raises an
     *                             unauthorized-parking alert, so keep it small)
     */
    public record Simulator(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("PT30S") Duration interval,
            @DefaultValue("0.25") @DecimalMin("0.0") @DecimalMax("1.0") double sampleFraction,
            @DefaultValue("0.85") @DecimalMin("0.0") @DecimalMax("1.0") double coveredOccupancy,
            @DefaultValue("0.003") @DecimalMin("0.0") @DecimalMax("1.0") double uncoveredOccupancy) {
    }
}
