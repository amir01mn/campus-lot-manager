package io.github.amir01mn.parking.lot;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingLotRepository extends JpaRepository<ParkingLot, Long> {

    Optional<ParkingLot> findByName(String name);

    boolean existsByName(String name);
}
