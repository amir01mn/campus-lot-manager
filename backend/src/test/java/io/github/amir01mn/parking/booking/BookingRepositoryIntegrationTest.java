package io.github.amir01mn.parking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.amir01mn.parking.lot.ParkingSpot;
import io.github.amir01mn.parking.lot.ParkingSpotRepository;
import io.github.amir01mn.parking.payment.PaymentMethod;
import io.github.amir01mn.parking.support.AbstractIntegrationTest;
import io.github.amir01mn.parking.user.Role;
import io.github.amir01mn.parking.user.User;
import io.github.amir01mn.parking.user.UserRepository;
import io.github.amir01mn.parking.user.UserType;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class BookingRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final Instant T10 = Instant.parse("2030-01-15T15:00:00Z"); // 10:00 Toronto
    private static final Instant T11 = T10.plusSeconds(3600);
    private static final Instant T12 = T11.plusSeconds(3600);
    private static final Instant T13 = T12.plusSeconds(3600);
    private static final AtomicInteger REF_SEQ = new AtomicInteger();

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ParkingSpotRepository spotRepository;

    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;
    private ParkingSpot spotA1;
    private ParkingSpot spotA2;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(new User("alice@example.com", "{noop}x", "Alice", "A",
                UserType.STUDENT, Role.USER, T10.minusSeconds(86_400)));
        bob = userRepository.save(new User("bob@example.com", "{noop}x", "Bob", "B",
                UserType.VISITOR, Role.USER, T10.minusSeconds(86_400)));
        spotA1 = spotRepository.findBySensorId("SNS-A-001").orElseThrow();
        spotA2 = spotRepository.findBySensorId("SNS-A-002").orElseThrow();
    }

    @Test
    void exclusionConstraintRejectsOverlappingConfirmedBookingOnSameSpot() {
        bookingRepository.saveAndFlush(booking(alice, spotA1, T10, T12));

        assertThatThrownBy(() -> bookingRepository.saveAndFlush(booking(bob, spotA1, T11, T13)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(sqlState(ex)).isEqualTo("23P01"));
    }

    @Test
    void exclusionConstraintAllowsBackToBackBookings() {
        bookingRepository.saveAndFlush(booking(alice, spotA1, T10, T11));

        assertThatCode(() -> bookingRepository.saveAndFlush(booking(bob, spotA1, T11, T12)))
                .doesNotThrowAnyException();
    }

    @Test
    void exclusionConstraintIgnoresCancelledBookings() {
        Booking cancelled = booking(alice, spotA1, T10, T12);
        cancelled.cancel(T10.minusSeconds(7200));
        bookingRepository.saveAndFlush(cancelled);

        assertThatCode(() -> bookingRepository.saveAndFlush(booking(bob, spotA1, T11, T13)))
                .doesNotThrowAnyException();
    }

    @Test
    void cancellingFreesTheSlotForANewConfirmedBooking() {
        Booking first = bookingRepository.saveAndFlush(booking(alice, spotA1, T10, T12));
        first.cancel(T10.minusSeconds(7200));
        bookingRepository.saveAndFlush(first);

        assertThatCode(() -> bookingRepository.saveAndFlush(booking(bob, spotA1, T10, T12)))
                .doesNotThrowAnyException();
    }

    @Test
    void exclusionConstraintRejectsOverlappingConfirmedBookingsForSameUser() {
        bookingRepository.saveAndFlush(booking(alice, spotA1, T10, T12));

        assertThatThrownBy(() -> bookingRepository.saveAndFlush(booking(alice, spotA2, T11, T13)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(sqlState(ex)).isEqualTo("23P01"));
    }

    @Test
    void spotOverlapQueryUsesHalfOpenIntervalsAndOnlyConfirmed() {
        Booking existing = bookingRepository.saveAndFlush(booking(alice, spotA1, T11, T12));
        Booking cancelled = booking(bob, spotA1, T12, T13);
        cancelled.cancel(T10);
        bookingRepository.saveAndFlush(cancelled);

        assertThat(bookingRepository.findConfirmedOverlappingForSpot(spotA1.getId(), T10, T11)).isEmpty();
        assertThat(bookingRepository.findConfirmedOverlappingForSpot(spotA1.getId(), T12, T13)).isEmpty();
        assertThat(bookingRepository.findConfirmedOverlappingForSpot(spotA1.getId(), T10, T11.plusSeconds(60)))
                .containsExactly(existing);
        assertThat(bookingRepository.findConfirmedOverlappingForSpot(spotA1.getId(), T10, T13))
                .containsExactly(existing);
        assertThat(bookingRepository.findConfirmedOverlappingForSpot(spotA2.getId(), T10, T13)).isEmpty();
    }

    @Test
    void userOverlapQueryFindsBookingsOnAnySpot() {
        Booking onA1 = bookingRepository.saveAndFlush(booking(alice, spotA1, T10, T11));
        Booking onA2 = bookingRepository.saveAndFlush(booking(alice, spotA2, T12, T13));

        assertThat(bookingRepository.findConfirmedOverlappingForUser(alice.getId(), T10, T13))
                .containsExactly(onA1, onA2);
        assertThat(bookingRepository.findConfirmedOverlappingForUser(alice.getId(), T11, T12)).isEmpty();
        assertThat(bookingRepository.findConfirmedOverlappingForUser(bob.getId(), T10, T13)).isEmpty();
    }

    @Test
    void bookedSpotIdsInLotReflectConfirmedOverlaps() {
        bookingRepository.saveAndFlush(booking(alice, spotA1, T10, T11));
        bookingRepository.saveAndFlush(booking(bob, spotA2, T12, T13));

        Long lotId = spotA1.getLot().getId();
        assertThat(bookingRepository.findBookedSpotIdsInLot(lotId, T10, T12)).containsExactly(spotA1.getId());
        assertThat(bookingRepository.findBookedSpotIdsInLot(lotId, T10, T13))
                .containsExactlyInAnyOrder(spotA1.getId(), spotA2.getId());
    }

    private static Booking booking(User user, ParkingSpot spot, Instant start, Instant end) {
        String ref = "CP-T%05d".formatted(REF_SEQ.incrementAndGet());
        return new Booking(ref, user, spot, "ABC 123", start, end, PaymentMethod.CREDIT_CARD,
                new BigDecimal("5.00"), new BigDecimal("5.00"), new BigDecimal("10.00"), T10.minusSeconds(86_400));
    }

    private static String sqlState(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
