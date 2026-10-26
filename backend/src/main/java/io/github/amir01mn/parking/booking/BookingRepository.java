package io.github.amir01mn.parking.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

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

    /** Booking with its user, spot and lot fetched in one query (for detail views and authorization). */
    @EntityGraph(attributePaths = {"user", "spot", "spot.lot"})
    Optional<Booking> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"spot", "spot.lot"})
    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"spot", "spot.lot"})
    Page<Booking> findByUserIdAndStatus(UUID userId, BookingStatus status, Pageable pageable);

    long countByUserId(UUID userId);

    /** CONFIRMED bookings whose end has passed, for the auto-checkout job. */
    List<Booking> findByStatusAndEndTimeLessThanEqual(BookingStatus status, Instant cutoff);

    /** Manager search: filtered page with user, spot and lot fetched (all to-one, so paging stays in SQL). */
    @Override
    @EntityGraph(attributePaths = {"user", "spot", "spot.lot"})
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    /** Ids of the given spots that have a CONFIRMED booking covering {@code at} ({@code start <= at < end}). */
    @Query("""
            select distinct b.spot.id from Booking b
            where b.spot.id in :spotIds
              and b.status = io.github.amir01mn.parking.booking.BookingStatus.CONFIRMED
              and b.startTime <= :at
              and b.endTime > :at
            """)
    List<Long> findSpotIdsCoveredAt(@Param("spotIds") Collection<Long> spotIds, @Param("at") Instant at);

    /** Ids of every spot that has a CONFIRMED booking covering {@code at}. */
    @Query("""
            select distinct b.spot.id from Booking b
            where b.status = io.github.amir01mn.parking.booking.BookingStatus.CONFIRMED
              and b.startTime <= :at
              and b.endTime > :at
            """)
    List<Long> findAllSpotIdsCoveredAt(@Param("at") Instant at);

    /** CONFIRMED bookings on the spot that have not ended yet (current or future). */
    @Query("""
            select count(b) from Booking b
            where b.spot.id = :spotId
              and b.status = io.github.amir01mn.parking.booking.BookingStatus.CONFIRMED
              and b.endTime > :now
            """)
    long countUpcomingConfirmedForSpot(@Param("spotId") Long spotId, @Param("now") Instant now);

    /** CONFIRMED bookings in the lot that have not ended yet (current or future). */
    @Query("""
            select count(b) from Booking b
            where b.spot.lot.id = :lotId
              and b.status = io.github.amir01mn.parking.booking.BookingStatus.CONFIRMED
              and b.endTime > :now
            """)
    long countUpcomingConfirmedForLot(@Param("lotId") Long lotId, @Param("now") Instant now);

    /**
     * Overstay candidates: non-cancelled bookings, not yet alerted, that ended in {@code (since, cutoff]}, whose
     * spot is still occupied by an occupancy that began before the booking ended, with no CONFIRMED booking
     * covering {@code now} on the spot and no later non-cancelled booking that has already started there.
     */
    @Query("""
            select b from Booking b join fetch b.spot s join fetch s.lot
            where b.status <> io.github.amir01mn.parking.booking.BookingStatus.CANCELLED
              and b.overstayAlertedAt is null
              and b.endTime <= :cutoff
              and b.endTime > :since
              and s.occupied = true
              and s.occupiedSince < b.endTime
              and not exists (
                  select 1 from Booking o
                  where o.spot = s
                    and o.status = io.github.amir01mn.parking.booking.BookingStatus.CONFIRMED
                    and o.startTime <= :now
                    and o.endTime > :now)
              and not exists (
                  select 1 from Booking n
                  where n.spot = s
                    and n.id <> b.id
                    and n.status <> io.github.amir01mn.parking.booking.BookingStatus.CANCELLED
                    and n.startTime >= b.endTime
                    and n.startTime <= :now)
            order by b.endTime
            """)
    List<Booking> findOverstayCandidates(@Param("since") Instant since, @Param("cutoff") Instant cutoff,
                                         @Param("now") Instant now);
}
