package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.payment.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * @param plate licence plate; upper-cased and trimmed, then must match {@code ^[A-Z0-9 -]{2,10}$}
 */
public record CreateBookingRequest(
        @NotNull Long spotId,
        @Schema(example = "ABC 123") @NotBlank @Size(max = 20) String plate,
        @Schema(example = "2030-01-15T15:00:00Z") @NotNull Instant start,
        @Schema(example = "2030-01-15T17:00:00Z") @NotNull Instant end,
        @NotNull PaymentMethod paymentMethod) {
}
