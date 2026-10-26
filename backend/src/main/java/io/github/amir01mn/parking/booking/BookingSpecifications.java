package io.github.amir01mn.parking.booking;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** JPA criteria for {@link BookingSearchCriteria}. */
final class BookingSpecifications {

    private static final char ESCAPE = '\\';

    private BookingSpecifications() {
    }

    static Specification<Booking> matching(BookingSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.lotId() != null) {
                predicates.add(cb.equal(root.get("spot").get("lot").get("id"), criteria.lotId()));
            }
            if (criteria.from() != null) {
                predicates.add(cb.greaterThan(root.get("endTime"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(cb.lessThan(root.get("startTime"), criteria.to()));
            }
            if (criteria.q() != null && !criteria.q().isBlank()) {
                String pattern = "%" + escapeLike(criteria.q().trim().toUpperCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.upper(root.get("referenceCode")), pattern, ESCAPE),
                        cb.like(cb.upper(root.get("licencePlate")), pattern, ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Escapes LIKE wildcards so user input matches literally. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
