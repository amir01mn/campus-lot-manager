package io.github.amir01mn.parking.lot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

/**
 * A single parking space. Manager state ({@code enabled}, {@code maintenanceRequired}) and sensor state
 * ({@code occupied}, {@code lastSensorUpdate}) are independent: sensor readings never change manager flags.
 */
@Entity
@Table(name = "parking_spots")
public class ParkingSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ParkingLot lot;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(name = "sensor_id", nullable = false, unique = true, length = 50)
    private String sensorId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "maintenance_required", nullable = false)
    private boolean maintenanceRequired;

    @Column(nullable = false)
    private boolean occupied;

    @Column(name = "last_sensor_update")
    private Instant lastSensorUpdate;

    /** Start of the current continuous occupancy; {@code null} while the spot is free. */
    @Column(name = "occupied_since")
    private Instant occupiedSince;

    @Version
    private long version;

    protected ParkingSpot() {
        // for JPA
    }

    public ParkingSpot(ParkingLot lot, String label, String sensorId) {
        this.lot = Objects.requireNonNull(lot, "lot");
        this.label = Objects.requireNonNull(label, "label");
        this.sensorId = Objects.requireNonNull(sensorId, "sensorId");
        this.enabled = true;
    }

    /** A spot can be booked only when its lot is enabled, it is enabled, and it needs no maintenance. */
    public boolean isBookable() {
        return lot.isEnabled() && enabled && !maintenanceRequired;
    }

    /**
     * Applies a sensor reading. Readings older than the last applied one are ignored. An unoccupied -> occupied
     * transition starts a new occupancy ({@link #getOccupiedSince()}); becoming free clears it.
     *
     * @return {@code true} if the reading was applied
     */
    public boolean applySensorReading(boolean occupiedNow, Instant readingTime) {
        Objects.requireNonNull(readingTime, "readingTime");
        if (lastSensorUpdate != null && readingTime.isBefore(lastSensorUpdate)) {
            return false;
        }
        if (occupiedNow && !occupied) {
            this.occupiedSince = readingTime;
        } else if (!occupiedNow) {
            this.occupiedSince = null;
        }
        this.occupied = occupiedNow;
        this.lastSensorUpdate = readingTime;
        return true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMaintenanceRequired(boolean maintenanceRequired) {
        this.maintenanceRequired = maintenanceRequired;
    }

    public Long getId() {
        return id;
    }

    public ParkingLot getLot() {
        return lot;
    }

    public String getLabel() {
        return label;
    }

    public String getSensorId() {
        return sensorId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMaintenanceRequired() {
        return maintenanceRequired;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public Instant getLastSensorUpdate() {
        return lastSensorUpdate;
    }

    public Instant getOccupiedSince() {
        return occupiedSince;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ParkingSpot other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return ParkingSpot.class.hashCode();
    }

    @Override
    public String toString() {
        return "ParkingSpot[id=" + id + ", label=" + label + "]";
    }
}
