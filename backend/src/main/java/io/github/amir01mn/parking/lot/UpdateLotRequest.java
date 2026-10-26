package io.github.amir01mn.parking.lot;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial lot update: absent (null) fields are left unchanged. */
public record UpdateLotRequest(
        Boolean enabled,
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String location) {
}
