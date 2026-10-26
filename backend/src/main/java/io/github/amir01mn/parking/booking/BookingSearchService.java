package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.common.BusinessRuleException;
import io.github.amir01mn.parking.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only booking search across all users, for managers. */
@Service
public class BookingSearchService {

    private static final Sort NEWEST_START_FIRST = Sort.by(Sort.Order.desc("startTime"), Sort.Order.desc("id"));

    private final BookingRepository bookingRepository;
    private final PricingService pricingService;

    public BookingSearchService(BookingRepository bookingRepository, PricingService pricingService) {
        this.bookingRepository = bookingRepository;
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ManagerBookingResponse> search(BookingSearchCriteria criteria, int page, int size) {
        if (criteria.from() != null && criteria.to() != null && !criteria.to().isAfter(criteria.from())) {
            throw new BusinessRuleException("INVALID_TIME_RANGE", "'to' must be after 'from'.");
        }
        String currency = pricingService.currency();
        return PageResponse.of(bookingRepository.findAll(BookingSpecifications.matching(criteria),
                PageRequest.of(page, size, NEWEST_START_FIRST)), b -> ManagerBookingResponse.of(b, currency));
    }
}
