package io.github.amir01mn.parking.analytics;

import io.github.amir01mn.parking.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manager/stats")
@Tag(name = "Manager: analytics", description = "Dashboard statistics (MANAGER)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    @Operation(summary = "Dashboard statistics",
            description = "Live occupancy plus bookings, revenue (deposits + final charges - refunds), bookings per "
                    + "day, revenue by user type and per-lot utilisation for [from, to). Defaults to the last 30 days.")
    public StatsResponse stats(
            @Parameter(description = "ISO-8601 instant; default: 30 days before `to`")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "ISO-8601 instant, exclusive; default: now")
            @RequestParam(required = false) Instant to) {
        return statsService.stats(from, to);
    }
}
