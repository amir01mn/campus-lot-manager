package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.auth.CurrentUser;
import io.github.amir01mn.parking.common.PageResponse;
import io.github.amir01mn.parking.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings", description = "Create and manage parking bookings")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class BookingController {

    private final BookingService bookingService;
    private final CurrentUser currentUser;

    public BookingController(BookingService bookingService, CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "Book a spot", description = "Charges a deposit of one hour at your rate.")
    @ApiResponse(responseCode = "201", description = "Booking confirmed")
    @ApiResponse(responseCode = "409", description = "The spot, or you, already have an overlapping booking")
    @ApiResponse(responseCode = "422", description = "A booking rule was violated (see the problem's code)")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        BookingResponse booking = bookingService.create(currentUser.id(), request);
        return ResponseEntity.created(URI.create("/api/bookings/" + booking.id())).body(booking);
    }

    @GetMapping("/me")
    @Operation(summary = "List my bookings", description = "Newest start time first; optionally filtered by status.")
    public PageResponse<BookingResponse> mine(
            @RequestParam Optional<BookingStatus> status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return bookingService.listMine(currentUser.id(), status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking", description = "Visible to the owner and to managers.")
    public BookingResponse get(@PathVariable UUID id) {
        return bookingService.get(currentUser.id(), id);
    }

    @PostMapping("/{id}/extend")
    @Operation(summary = "Extend a booking", description = "Owner only. The total is recalculated.")
    public BookingResponse extend(@PathVariable UUID id, @Valid @RequestBody ExtendBookingRequest request) {
        return bookingService.extend(currentUser.id(), id, request.newEnd());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a booking",
            description = "Owner or manager. The deposit is refunded if cancelled at least 1 hour before start.")
    public BookingResponse cancel(@PathVariable UUID id) {
        return bookingService.cancel(currentUser.id(), id);
    }

    @PostMapping("/{id}/checkout")
    @Operation(summary = "Check out", description = "Owner only, after the start. Charges total minus deposit.")
    public BookingResponse checkout(@PathVariable UUID id) {
        return bookingService.checkout(currentUser.id(), id);
    }

    @GetMapping("/{id}/receipt")
    @Operation(summary = "Get a receipt", description = "Booking summary with every payment. Owner or manager.")
    public ReceiptResponse receipt(@PathVariable UUID id) {
        return bookingService.receipt(currentUser.id(), id);
    }
}
