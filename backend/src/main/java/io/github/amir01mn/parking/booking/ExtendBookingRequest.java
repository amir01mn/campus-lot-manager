package io.github.amir01mn.parking.booking;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ExtendBookingRequest(@NotNull Instant newEnd) {
}
