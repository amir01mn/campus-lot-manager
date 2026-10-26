package io.github.amir01mn.parking.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Sensor ingestion settings ({@code app.sensors.*}). */
@Validated
@ConfigurationProperties(prefix = "app.sensors")
public record SensorProperties(@NotBlank String apiKey, @DefaultValue Simulator simulator) {

    @Override
    public String toString() {
        return "SensorProperties[apiKey=***, simulator=" + simulator + "]";
    }

    /** Demo-only reading generator. */
    public record Simulator(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("PT10S") Duration interval) {
    }
}
