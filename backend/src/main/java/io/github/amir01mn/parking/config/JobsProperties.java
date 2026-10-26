package io.github.amir01mn.parking.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Scheduled jobs ({@code app.jobs.*}). {@code enabled=false} (the {@code test} profile) turns scheduling off; the
 * job beans still exist and tests call them directly.
 */
@ConfigurationProperties(prefix = "app.jobs")
public record JobsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue AutoCheckout autoCheckout,
        @DefaultValue Overstay overstay) {

    /** Completes CONFIRMED bookings whose end has passed. */
    public record AutoCheckout(@DefaultValue("PT1M") Duration interval) {
    }

    /**
     * Raises overstay alerts.
     *
     * @param grace    how long after a booking's end the car may still be there before it is an overstay
     * @param lookback bookings that ended longer ago than this are not checked
     */
    public record Overstay(
            @DefaultValue("PT1M") Duration interval,
            @DefaultValue("PT10M") Duration grace,
            @DefaultValue("PT24H") Duration lookback) {
    }
}
