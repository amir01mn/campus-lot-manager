package io.github.amir01mn.parking.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /**
     * CONFIRMED bookings on {@code spotId} overlapping the half-open interval {@code [start, end)}.
     * Back-to-back bookings (one ends exactly when the other starts) do not overlap.
     * When extending, callers filter out the booking being extended.
     */
    @Query("""
            select b from Booking b
            where b.spot.id = :spotId
              and b.status = io.github.amir01mn.parking.booking.BookingStatus.CONFIRMED
              and b.startTime < :end
              and b.endTime > :start
            order by b.startTime
            """)
    List<Booking> findConfirmedOverlappingForSpot(@Param("spotId") Long spotId,
                                                  @Param("start") Instant start,
                                                  @Param("end") Instant end);

    /** CONFIRMED bookings held by {@code userId} overlapping the half-open interval {@code [start, end)}. */
    @Query("""
            select b from Booking b
            where b.user.id = :userId
              and b.status = io.github.amir01mn.parking.booking.BookingStatus.CONFIRMED
              and b.startTime < :end
              and b.endTime > :start
            order by b.startTime
            """)
    List<Booking> findConfirmedOverlappingForUser(@Param("userId") UUID userId,
                                                  @Param("start") Instant start,
                                                  @Param("end") Instant end);

    /** Ids of spots in {@code lotId} that have a CONFIRMED booking overlapping {@code [start, end)}. */
    @Query("""
            select distinct b.spot.id from Booking b
            where b.spot.lot.id = :lotId
              and b.status = io.github.amir01mn.parking.booking.BookingStatus.CONFIRMED
              and b.startTime < :end
              and b.endTime > :start
            """)
    List<Long> findBookedSpotIdsInLot(@Param("lotId") Long lotId,
                                      @Param("start") Instant start,
                                      @Param("end") Instant end);

    Optional<Booking> findByReferenceCode(String referenceCode);

    boolean existsByReferenceCode(String referenceCode);

    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    Page<Booking> findByUserIdAndStatus(UUID userId, BookingStatus status, Pageable pageable);

    /** CONFIRMED bookings whose end has passed, for the auto-checkout job. */
    List<Booking> findByStatusAndEndTimeLessThanEqual(BookingStatus status, Instant cutoff);
}
