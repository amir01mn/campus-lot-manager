package io.github.amir01mn.parking.lot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {

    List<ParkingSpot> findByLotIdOrderByLabelAsc(Long lotId);

    long countByLotId(Long lotId);

    long countByLotIdAndOccupiedTrue(Long lotId);

    long countByOccupiedTrue();

    Optional<ParkingSpot> findBySensorId(String sensorId);

    List<ParkingSpot> findBySensorIdIn(Collection<String> sensorIds);
}
