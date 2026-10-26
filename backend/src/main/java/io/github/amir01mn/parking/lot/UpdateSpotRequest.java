package io.github.amir01mn.parking.lot;

/** Partial spot update of the manager-controlled flags: absent (null) fields are left unchanged. */
public record UpdateSpotRequest(Boolean enabled, Boolean maintenanceRequired) {
}
