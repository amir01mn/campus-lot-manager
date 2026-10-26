package io.github.amir01mn.parking.lot;

import io.github.amir01mn.parking.booking.BookingRepository;
import io.github.amir01mn.parking.common.BusinessRuleException;
import io.github.amir01mn.parking.common.NotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public, read-only views of lots and spots. */
@Service
@Transactional(readOnly = true)
public class LotService {

    private final ParkingLotRepository lotRepository;
    private final ParkingSpotRepository spotRepository;
    private final BookingRepository bookingRepository;

    public LotService(ParkingLotRepository lotRepository, ParkingSpotRepository spotRepository,
                      BookingRepository bookingRepository) {
        this.lotRepository = lotRepository;
        this.spotRepository = spotRepository;
        this.bookingRepository = bookingRepository;
    }

    /** All lots by name, with counts from a single aggregate query. */
    public List<LotResponse> listLots() {
        Map<Long, LotSpotCounts> counts = spotRepository.countSpotsByLot().stream()
                .collect(Collectors.toMap(LotSpotCounts::lotId, Function.identity()));
        return lotRepository.findAll().stream()
                .sorted(Comparator.comparing(ParkingLot::getName))
                .map(lot -> LotResponse.of(lot, counts.getOrDefault(lot.getId(), LotSpotCounts.empty(lot.getId()))))
                .toList();
    }

    public LotResponse getLot(Long lotId) {
        ParkingLot lot = requireLot(lotId);
        return LotResponse.of(lot, spotRepository.countSpotsForLot(lotId).orElse(LotSpotCounts.empty(lotId)));
    }

    public List<SpotResponse> listSpots(Long lotId) {
        requireLot(lotId); // loads the lot once; spots' lot references resolve to it
        return spotRepository.findByLotIdOrderByLabelAsc(lotId).stream().map(SpotResponse::of).toList();
    }

    /** Bookable spots with no CONFIRMED booking overlapping {@code [start, end)}. */
    public AvailabilityResponse availability(Long lotId, Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("INVALID_TIME_RANGE", "The end time must be after the start time.");
        }
        requireLot(lotId);
        Set<Long> booked = new HashSet<>(bookingRepository.findBookedSpotIdsInLot(lotId, start, end));
        List<SpotResponse> free = spotRepository.findByLotIdOrderByLabelAsc(lotId).stream()
                .filter(ParkingSpot::isBookable)
                .filter(spot -> !booked.contains(spot.getId()))
                .map(SpotResponse::of)
                .toList();
        return new AvailabilityResponse(lotId, start, end, free.size(), free);
    }

    private ParkingLot requireLot(Long lotId) {
        return lotRepository.findById(lotId).orElseThrow(() -> NotFoundException.of("Lot", lotId));
    }
}
