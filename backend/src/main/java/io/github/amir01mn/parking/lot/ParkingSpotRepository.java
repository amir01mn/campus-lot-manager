package io.github.amir01mn.parking.lot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {

    List<ParkingSpot> findByLotIdOrderByLabelAsc(Long lotId);

    long countByLotId(Long lotId);

    long countByLotIdAndOccupiedTrue(Long lotId);

    long countByOccupiedTrue();

    Optional<ParkingSpot> findBySensorId(String sensorId);

    /** Loads the spot with a row lock ({@code SELECT ... FOR UPDATE}); serialises booking writes per spot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSpot s join fetch s.lot where s.id = :id")
    Optional<ParkingSpot> findByIdForUpdate(@Param("id") Long id);

    /** Spot counts for every lot in one aggregate query. "Bookable" here ignores the lot's own enabled flag. */
    @Query("""
            select new io.github.amir01mn.parking.lot.LotSpotCounts(
                s.lot.id,
                count(s),
                sum(case when s.enabled = true and s.maintenanceRequired = false then 1L else 0L end),
                sum(case when s.occupied = true then 1L else 0L end))
            from ParkingSpot s
            group by s.lot.id
            """)
    List<LotSpotCounts> countSpotsByLot();

    /** Spot counts for one lot; empty when the lot has no spots. */
    @Query("""
            select new io.github.amir01mn.parking.lot.LotSpotCounts(
                s.lot.id,
                count(s),
                sum(case when s.enabled = true and s.maintenanceRequired = false then 1L else 0L end),
                sum(case when s.occupied = true then 1L else 0L end))
            from ParkingSpot s
            where s.lot.id = :lotId
            group by s.lot.id
            """)
    Optional<LotSpotCounts> countSpotsForLot(@Param("lotId") Long lotId);

    List<ParkingSpot> findBySensorIdIn(Collection<String> sensorIds);
}
