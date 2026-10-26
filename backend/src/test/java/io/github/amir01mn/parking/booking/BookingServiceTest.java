package io.github.amir01mn.parking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.amir01mn.parking.booking.event.BookingCancelled;
import io.github.amir01mn.parking.booking.event.BookingCompleted;
import io.github.amir01mn.parking.booking.event.BookingConfirmed;
import io.github.amir01mn.parking.booking.event.BookingExtended;
import io.github.amir01mn.parking.common.BusinessRuleException;
import io.github.amir01mn.parking.common.ConflictException;
import io.github.amir01mn.parking.common.ForbiddenException;
import io.github.amir01mn.parking.common.NotFoundException;
import io.github.amir01mn.parking.config.PricingProperties;
import io.github.amir01mn.parking.lot.ParkingLot;
import io.github.amir01mn.parking.lot.ParkingSpot;
import io.github.amir01mn.parking.lot.ParkingSpotRepository;
import io.github.amir01mn.parking.payment.PaymentMethod;
import io.github.amir01mn.parking.payment.PaymentService;
import io.github.amir01mn.parking.payment.PaymentType;
import io.github.amir01mn.parking.user.Role;
import io.github.amir01mn.parking.user.User;
import io.github.amir01mn.parking.user.UserService;
import io.github.amir01mn.parking.user.UserType;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2030-03-01T14:00:00Z");

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final ParkingSpotRepository spots = mock(ParkingSpotRepository.class);
    private final UserService userService = mock(UserService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("America/Toronto"));
    private final PricingService pricing = new PricingService(new PricingProperties("CAD",
            new PricingProperties.Rates(new BigDecimal("5.00"), new BigDecimal("8.00"), new BigDecimal("10.00"),
                    new BigDecimal("15.00"))));
    private final BookingCompletionService completion =
            new BookingCompletionService(bookings, pricing, payments, events, clock);
    private final BookingService service = new BookingService(bookings, spots, userService, pricing, payments,
            completion, new ReferenceCodeGenerator(), events, clock);

    private ParkingLot lot;
    private ParkingSpot spot;
    private User student;
    private User stranger;
    private User manager;

    @BeforeEach
    void setUp() {
        lot = new ParkingLot("Lot A", "Somewhere", NOW);
        ReflectionTestUtils.setField(lot, "id", 1L);
        spot = new ParkingSpot(lot, "A-001", "SNS-A-001");
        ReflectionTestUtils.setField(spot, "id", 10L);
        student = user("student@my.yorku.ca", UserType.STUDENT, Role.USER);
        stranger = user("stranger@example.com", UserType.VISITOR, Role.USER);
        manager = user("manager@yorku.ca", UserType.STAFF, Role.MANAGER);
        when(spots.findByIdForUpdate(10L)).thenReturn(Optional.of(spot));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
            return booking;
        });
    }

    private User user(String email, UserType type, Role role) {
        User user = new User(email, "hash", "First", "Last", type, role, NOW);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(userService.require(user.getId())).thenReturn(user);
        when(userService.requireForUpdate(user.getId())).thenReturn(user);
        return user;
    }

    private CreateBookingRequest request(String plate, Instant start, Instant end) {
        return new CreateBookingRequest(10L, plate, start, end, PaymentMethod.PAYPAL);
    }

    private Booking existing(User owner, Instant start, Instant end) {
        Booking booking = new Booking("CP-ABCDEF", owner, spot, "ABC 123", start, end, PaymentMethod.CREDIT_CARD,
                new BigDecimal("5.00"), new BigDecimal("5.00"),
                pricing.total(new BigDecimal("5.00"), start, end), NOW.minus(Duration.ofDays(1)));
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        when(bookings.findDetailedById(booking.getId())).thenReturn(Optional.of(booking));
        return booking;
    }

    private static String code(Throwable ex) {
        return ((BusinessRuleException) ex).getCode();
    }

    @Nested
    class Create {

        @Test
        void snapshotsRateChargesDepositAndPublishesEvent() {
            Instant start = NOW.plus(Duration.ofHours(1));
            BookingResponse response = service.create(student.getId(),
                    request(" ab-12 ", start, start.plus(Duration.ofMinutes(150))));

            assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(response.referenceCode()).matches("^CP-[A-Z0-9]{6}$");
            assertThat(response.licencePlate()).isEqualTo("AB-12");
            assertThat(response.hourlyRate()).isEqualByComparingTo("5.00");
            assertThat(response.depositAmount()).isEqualByComparingTo("5.00");
            assertThat(response.totalAmount()).isEqualByComparingTo("15.00");
            assertThat(response.createdAt()).isEqualTo(NOW);
            verify(payments).record(any(Booking.class), eq(PaymentType.DEPOSIT), eq(new BigDecimal("5.00")));
            ArgumentCaptor<BookingConfirmed> event = ArgumentCaptor.forClass(BookingConfirmed.class);
            verify(events).publishEvent(event.capture());
            assertThat(event.getValue().bookingId()).isEqualTo(response.id());
            assertThat(event.getValue().userId()).isEqualTo(student.getId());
            assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        }

        @Test
        void allowsAFiveMinuteGraceForTheStart() {
            Instant start = NOW.minus(Duration.ofMinutes(5));
            service.create(student.getId(), request("ABC", start, start.plus(Duration.ofHours(1))));

            Instant tooEarly = NOW.minus(Duration.ofMinutes(6));
            assertThatThrownBy(() -> service.create(student.getId(),
                    request("ABC", tooEarly, tooEarly.plus(Duration.ofHours(1)))))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("START_IN_PAST"));
        }

        @Test
        void enforcesDurationBounds() {
            Instant start = NOW.plus(Duration.ofHours(1));
            assertThatThrownBy(() -> service.create(student.getId(), request("ABC", start, start)))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("INVALID_TIME_RANGE"));
            assertThatThrownBy(() -> service.create(student.getId(),
                    request("ABC", start, start.minus(Duration.ofHours(1)))))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("INVALID_TIME_RANGE"));
            assertThatThrownBy(() -> service.create(student.getId(),
                    request("ABC", start, start.plus(Duration.ofMinutes(29)))))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("BOOKING_TOO_SHORT"));
            assertThatThrownBy(() -> service.create(student.getId(),
                    request("ABC", start, start.plus(Duration.ofHours(24)).plusSeconds(60))))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("BOOKING_TOO_LONG"));

            service.create(student.getId(), request("ABC", start, start.plus(Duration.ofMinutes(30))));
            service.create(student.getId(), request("ABC", start, start.plus(Duration.ofHours(24))));
        }

        @Test
        void validatesLicencePlates() {
            assertThat(BookingService.normalizePlate("  ab 12-x ")).isEqualTo("AB 12-X");
            assertThat(BookingService.normalizePlate("A1")).isEqualTo("A1");
            assertThat(BookingService.normalizePlate("ABCDEFGHIJ")).isEqualTo("ABCDEFGHIJ");
            for (String bad : new String[] {"A", "ABCDEFGHIJK", "AB_12", "ÄBC", "", "   "}) {
                assertThatThrownBy(() -> BookingService.normalizePlate(bad))
                        .as(bad)
                        .satisfies(ex -> assertThat(code(ex)).isEqualTo("INVALID_LICENCE_PLATE"));
            }
        }

        @Test
        void rejectsUnbookableSpots() {
            Instant start = NOW.plus(Duration.ofHours(1));
            CreateBookingRequest request = request("ABC", start, start.plus(Duration.ofHours(1)));

            spot.setMaintenanceRequired(true);
            assertThatThrownBy(() -> service.create(student.getId(), request))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("SPOT_NOT_BOOKABLE"));
            spot.setMaintenanceRequired(false);
            spot.setEnabled(false);
            assertThatThrownBy(() -> service.create(student.getId(), request))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("SPOT_NOT_BOOKABLE"));
            spot.setEnabled(true);
            lot.setEnabled(false);
            assertThatThrownBy(() -> service.create(student.getId(), request))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("SPOT_NOT_BOOKABLE"));

            verify(bookings, never()).saveAndFlush(any());
            verifyNoInteractions(payments);
        }

        @Test
        void unknownSpotIs404() {
            Instant start = NOW.plus(Duration.ofHours(1));
            assertThatThrownBy(() -> service.create(student.getId(),
                    new CreateBookingRequest(99L, "ABC", start, start.plus(Duration.ofHours(1)), PaymentMethod.PAYPAL)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void rejectsOverlapOnTheSpot() {
            Instant start = NOW.plus(Duration.ofHours(1));
            Booking other = existing(stranger, start, start.plus(Duration.ofHours(2)));
            when(bookings.findConfirmedOverlappingForSpot(anyLong(), any(), any())).thenReturn(List.of(other));

            assertThatThrownBy(() -> service.create(student.getId(),
                    request("ABC", start.plus(Duration.ofHours(1)), start.plus(Duration.ofHours(3)))))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Spot A-001 is already booked");
            verify(bookings, never()).saveAndFlush(any());
        }

        @Test
        void rejectsOverlapForTheUser() {
            Instant start = NOW.plus(Duration.ofHours(1));
            Booking mine = existing(student, start, start.plus(Duration.ofHours(2)));
            when(bookings.findConfirmedOverlappingForUser(eq(student.getId()), any(), any()))
                    .thenReturn(List.of(mine));

            assertThatThrownBy(() -> service.create(student.getId(),
                    request("ABC", start, start.plus(Duration.ofHours(1)))))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("You already have a confirmed booking (CP-ABCDEF) that overlaps this time.");
        }

        @Test
        void translatesExclusionConstraintRaceToConflict() {
            when(bookings.saveAndFlush(any(Booking.class))).thenThrow(new DataIntegrityViolationException("insert",
                    new SQLException("conflicting key value violates exclusion constraint "
                            + "\"ex_bookings_spot_no_overlap\"", "23P01")));
            Instant start = NOW.plus(Duration.ofHours(1));

            assertThatThrownBy(() -> service.create(student.getId(),
                    request("ABC", start, start.plus(Duration.ofHours(1)))))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already booked");
            verifyNoInteractions(payments);
        }
    }

    @Nested
    class Cancel {

        @Test
        void refundsDepositWhenCancelledExactlyOneHourBeforeStart() {
            Booking booking = existing(student, NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(3)));

            BookingResponse response = service.cancel(student.getId(), booking.getId());

            assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(response.cancelledAt()).isEqualTo(NOW);
            verify(payments).record(booking, PaymentType.REFUND, new BigDecimal("5.00"));
            ArgumentCaptor<BookingCancelled> event = ArgumentCaptor.forClass(BookingCancelled.class);
            verify(events).publishEvent(event.capture());
            assertThat(event.getValue().refundAmount()).isEqualByComparingTo("5.00");
            assertThat(event.getValue().cancelledByOwner()).isTrue();
        }

        @Test
        void keepsDepositWhenCancelledLessThanOneHourBeforeStart() {
            Booking booking = existing(student, NOW.plus(Duration.ofMinutes(59)), NOW.plus(Duration.ofHours(3)));

            service.cancel(student.getId(), booking.getId());

            verify(payments, never()).record(any(), any(), any());
            ArgumentCaptor<BookingCancelled> event = ArgumentCaptor.forClass(BookingCancelled.class);
            verify(events).publishEvent(event.capture());
            assertThat(event.getValue().refundAmount()).isZero();
        }

        @Test
        void allowsCancellingAnOngoingBookingWithoutRefund() {
            Booking booking = existing(student, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(1)));

            assertThat(service.cancel(student.getId(), booking.getId()).status())
                    .isEqualTo(BookingStatus.CANCELLED);
            verify(payments, never()).record(any(), any(), any());
        }

        @Test
        void rejectsEndedOrInactiveBookings() {
            Booking ended = existing(student, NOW.minus(Duration.ofHours(2)), NOW);
            assertThatThrownBy(() -> service.cancel(student.getId(), ended.getId()))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("BOOKING_ENDED"));

            Booking cancelled = existing(student, NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(3)));
            cancelled.cancel(NOW);
            assertThatThrownBy(() -> service.cancel(student.getId(), cancelled.getId()))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("BOOKING_NOT_ACTIVE"));
        }

        @Test
        void hidesOtherUsersBookingsButLetsManagersCancel() {
            Booking booking = existing(student, NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(3)));

            assertThatThrownBy(() -> service.cancel(stranger.getId(), booking.getId()))
                    .isInstanceOf(NotFoundException.class);

            service.cancel(manager.getId(), booking.getId());
            ArgumentCaptor<BookingCancelled> event = ArgumentCaptor.forClass(BookingCancelled.class);
            verify(events).publishEvent(event.capture());
            assertThat(event.getValue().cancelledByOwner()).isFalse();
            assertThat(event.getValue().cancelledBy()).isEqualTo(manager.getId());
        }
    }

    @Nested
    class Extend {

        @Test
        void recalculatesTotalAtTheSnapshotRate() {
            Booking booking = existing(student, NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(3)));
            // the booking itself is returned by the overlap query: it must be ignored
            when(bookings.findConfirmedOverlappingForSpot(anyLong(), any(), any())).thenReturn(List.of(booking));
            when(bookings.findConfirmedOverlappingForUser(any(), any(), any())).thenReturn(List.of(booking));
            Instant newEnd = NOW.plus(Duration.ofMinutes(270));

            BookingResponse response = service.extend(student.getId(), booking.getId(), newEnd);

            assertThat(response.endTime()).isEqualTo(newEnd);
            assertThat(response.totalAmount()).isEqualByComparingTo("20.00"); // 3.5h -> 4h at 5.00
            assertThat(response.depositAmount()).isEqualByComparingTo("5.00");
            verify(bookings).flush();
            ArgumentCaptor<BookingExtended> event = ArgumentCaptor.forClass(BookingExtended.class);
            verify(events).publishEvent(event.capture());
            assertThat(event.getValue().previousEndTime()).isEqualTo(NOW.plus(Duration.ofHours(3)));
        }

        @Test
        void validatesTheNewEnd() {
            Booking booking = existing(student, NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(3)));

            assertThatThrownBy(() -> service.extend(student.getId(), booking.getId(), NOW.plus(Duration.ofHours(3))))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("INVALID_NEW_END"));
            assertThatThrownBy(() -> service.extend(student.getId(), booking.getId(),
                    NOW.plus(Duration.ofHours(25)).plusSeconds(60)))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("BOOKING_TOO_LONG"));
        }

        @Test
        void rejectsConflictsWithOtherBookings() {
            Booking booking = existing(student, NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(3)));
            Booking other = existing(stranger, NOW.plus(Duration.ofHours(3)), NOW.plus(Duration.ofHours(4)));
            when(bookings.findConfirmedOverlappingForSpot(anyLong(), any(), any())).thenReturn(List.of(other));

            assertThatThrownBy(() -> service.extend(student.getId(), booking.getId(), NOW.plus(Duration.ofHours(4))))
                    .isInstanceOf(ConflictException.class);
            assertThat(booking.getEndTime()).isEqualTo(NOW.plus(Duration.ofHours(3)));
        }

        @Test
        void rejectsEndedBookingsAndNonOwners() {
            Booking ended = existing(student, NOW.minus(Duration.ofHours(3)), NOW.minus(Duration.ofHours(1)));
            assertThatThrownBy(() -> service.extend(student.getId(), ended.getId(), NOW.plus(Duration.ofHours(1))))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("BOOKING_ENDED"));

            Booking booking = existing(student, NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(2)));
            assertThatThrownBy(() -> service.extend(manager.getId(), booking.getId(), NOW.plus(Duration.ofHours(3))))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> service.extend(stranger.getId(), booking.getId(), NOW.plus(Duration.ofHours(3))))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Checkout {

        @Test
        void isRejectedBeforeTheStart() {
            Booking booking = existing(student, NOW.plus(Duration.ofMinutes(1)), NOW.plus(Duration.ofHours(2)));

            assertThatThrownBy(() -> service.checkout(student.getId(), booking.getId()))
                    .satisfies(ex -> assertThat(code(ex)).isEqualTo("CHECKOUT_TOO_EARLY"));
            verifyNoInteractions(payments);
        }

        @Test
        void chargesTotalMinusDepositAndCompletes() {
            Booking booking = existing(student, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(2)));

            BookingResponse response = service.checkout(student.getId(), booking.getId());

            assertThat(response.status()).isEqualTo(BookingStatus.COMPLETED);
            assertThat(response.completedAt()).isEqualTo(NOW);
            verify(payments).record(booking, PaymentType.FINAL, new BigDecimal("10.00"));
            ArgumentCaptor<BookingCompleted> event = ArgumentCaptor.forClass(BookingCompleted.class);
            verify(events).publishEvent(event.capture());
            assertThat(event.getValue().finalCharge()).isEqualByComparingTo("10.00");
        }

        @Test
        void recordsNoFinalPaymentWhenTheDepositCoversTheTotal() {
            Booking booking = existing(student, NOW.minus(Duration.ofMinutes(30)), NOW.plus(Duration.ofMinutes(30)));

            service.checkout(student.getId(), booking.getId());

            verify(payments, never()).record(any(), any(), any());
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        }

        @Test
        void isOwnerOnly() {
            Booking booking = existing(student, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(1)));

            assertThatThrownBy(() -> service.checkout(manager.getId(), booking.getId()))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> service.checkout(stranger.getId(), booking.getId()))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void completionForAutoCheckoutSkipsBookingsNoLongerConfirmed() {
        Booking booking = existing(student, NOW.minus(Duration.ofHours(3)), NOW.minus(Duration.ofHours(1)));
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThat(completion.completeIfConfirmed(booking.getId())).isTrue();
        assertThat(completion.completeIfConfirmed(booking.getId())).isFalse();
        verify(payments).record(booking, PaymentType.FINAL, new BigDecimal("5.00"));
    }

    @Test
    void quoteValidatesTheWindowAndUsesTheCallersTier() {
        PriceQuote quote = service.quote(stranger.getId(), NOW, NOW.plus(Duration.ofMinutes(61)));
        assertThat(quote.hourlyRate()).isEqualByComparingTo("15.00");
        assertThat(quote.billableHours()).isEqualTo(2);
        assertThat(quote.total()).isEqualByComparingTo("30.00");

        assertThatThrownBy(() -> service.quote(stranger.getId(), NOW, NOW))
                .isInstanceOf(BusinessRuleException.class);
    }
}
