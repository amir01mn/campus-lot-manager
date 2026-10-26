package io.github.amir01mn.parking.lot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ParkingSpotTest {

    private static final Instant NOW = Instant.parse("2030-01-15T15:00:00Z");

    private final ParkingLot lot = new ParkingLot("Lot", "Somewhere", NOW);
    private final ParkingSpot spot = new ParkingSpot(lot, "A-001", "SNS-A-001");

    @Test
    void bookableOnlyWhenLotAndSpotEnabledAndNoMaintenance() {
        assertThat(spot.isBookable()).isTrue();

        spot.setMaintenanceRequired(true);
        assertThat(spot.isBookable()).isFalse();
        spot.setMaintenanceRequired(false);

        spot.setEnabled(false);
        assertThat(spot.isBookable()).isFalse();
        spot.setEnabled(true);

        lot.setEnabled(false);
        assertThat(spot.isBookable()).isFalse();
        assertThat(spot.isEnabled()).as("disabling a lot must not rewrite spot flags").isTrue();
    }

    @Test
    void sensorReadingNeverTouchesManagerState() {
        spot.setEnabled(false);
        spot.setMaintenanceRequired(true);

        assertThat(spot.applySensorReading(true, NOW)).isTrue();

        assertThat(spot.isOccupied()).isTrue();
        assertThat(spot.isEnabled()).isFalse();
        assertThat(spot.isMaintenanceRequired()).isTrue();
    }

    @Test
    void staleSensorReadingsAreIgnored() {
        spot.applySensorReading(true, NOW);

        assertThat(spot.applySensorReading(false, NOW.minusSeconds(1))).isFalse();
        assertThat(spot.isOccupied()).isTrue();
        assertThat(spot.getLastSensorUpdate()).isEqualTo(NOW);

        assertThat(spot.applySensorReading(false, NOW.plusSeconds(1))).isTrue();
        assertThat(spot.isOccupied()).isFalse();
    }
}
