package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.common.PageResponse;
import io.github.amir01mn.parking.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manager/bookings")
@Tag(name = "Manager: bookings", description = "All bookings across users (MANAGER)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ManagerBookingController {

    private final BookingSearchService searchService;

    public ManagerBookingController(BookingSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(summary = "Search bookings",
            description = "Newest start first. `from`/`to` select bookings overlapping [from, to); `q` matches the "
                    + "reference code or licence plate (case-insensitive substring).")
    public PageResponse<ManagerBookingResponse> search(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Long lotId,
            @Parameter(description = "ISO-8601 instant") @RequestParam(required = false) Instant from,
            @Parameter(description = "ISO-8601 instant, exclusive") @RequestParam(required = false) Instant to,
            @RequestParam(required = false) @Size(max = 20) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return searchService.search(new BookingSearchCriteria(status, lotId, from, to, q), page, size);
    }
}
