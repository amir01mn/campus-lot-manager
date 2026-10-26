package io.github.amir01mn.parking.lot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "parking_lots")
public class ParkingLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected ParkingLot() {
        // for JPA
    }

    public ParkingLot(String name, String location, Instant createdAt) {
        this.name = Objects.requireNonNull(name, "name");
        this.location = Objects.requireNonNull(location, "location");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.enabled = true;
    }

    public void rename(String newName) {
        this.name = Objects.requireNonNull(newName, "newName");
    }

    public void relocate(String newLocation) {
        this.location = Objects.requireNonNull(newLocation, "newLocation");
    }

    /** Manager control. Deliberately does not touch the spots' own flags. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ParkingLot other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return ParkingLot.class.hashCode();
    }

    @Override
    public String toString() {
        return "ParkingLot[id=" + id + ", name=" + name + "]";
    }
}
