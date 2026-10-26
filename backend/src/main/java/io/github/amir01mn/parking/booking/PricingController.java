package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.auth.CurrentUser;
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
@RequestMapping("/api/pricing")
@Tag(name = "Pricing", description = "Price previews")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PricingController {

    private final BookingService bookingService;
    private final CurrentUser currentUser;

    public PricingController(BookingService bookingService, CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
    }

    @GetMapping("/quote")
    @Operation(summary = "Quote a booking window",
            description = "Price for the signed-in user's tier: billable hours rounded up, deposit and final charge.")
    public PriceQuote quote(
            @Parameter(description = "ISO-8601 instant") @RequestParam Instant start,
            @Parameter(description = "ISO-8601 instant, exclusive") @RequestParam Instant end) {
        return bookingService.quote(currentUser.id(), start, end);
    }
}
