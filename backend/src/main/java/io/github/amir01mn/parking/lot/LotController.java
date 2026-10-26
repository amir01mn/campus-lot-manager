package io.github.amir01mn.parking.lot;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lots")
@Tag(name = "Lots", description = "Public lot and spot information")
public class LotController {

    private final LotService lotService;

    public LotController(LotService lotService) {
        this.lotService = lotService;
    }

    @GetMapping
    @Operation(summary = "List lots", description = "Every lot with total, bookable and occupied spot counts.")
    public List<LotResponse> list() {
        return lotService.listLots();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a lot")
    public LotResponse get(@PathVariable Long id) {
        return lotService.getLot(id);
    }

    @GetMapping("/{id}/spots")
    @Operation(summary = "List a lot's spots", description = "Live state: manager flags, occupancy, bookability.")
    public List<SpotResponse> spots(@PathVariable Long id) {
        return lotService.listSpots(id);
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Find free spots for a time window",
            description = "Bookable spots with no confirmed booking overlapping [start, end).")
    public AvailabilityResponse availability(
            @PathVariable Long id,
            @Parameter(description = "ISO-8601 instant, e.g. 2030-01-15T15:00:00Z") @RequestParam Instant start,
            @Parameter(description = "ISO-8601 instant, exclusive") @RequestParam Instant end) {
        return lotService.availability(id, start, end);
    }
}
