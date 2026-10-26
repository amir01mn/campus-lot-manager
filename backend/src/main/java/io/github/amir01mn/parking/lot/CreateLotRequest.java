package io.github.amir01mn.parking.lot;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** New lot; {@code capacity} (1-500) defaults to {@value #DEFAULT_CAPACITY} spots. */
public record CreateLotRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 200) String location,
        @Min(1) @Max(500) Integer capacity) {

    public static final int DEFAULT_CAPACITY = 100;

    public int capacityOrDefault() {
        return capacity == null ? DEFAULT_CAPACITY : capacity;
    }
}
