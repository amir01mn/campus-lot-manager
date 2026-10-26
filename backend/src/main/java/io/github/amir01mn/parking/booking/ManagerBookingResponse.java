package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.user.User;
import io.github.amir01mn.parking.user.UserType;
import java.util.UUID;

/** A booking in the manager's table: the regular booking view plus who made it. */
public record ManagerBookingResponse(BookingResponse booking, Customer customer) {

    public record Customer(UUID id, String email, String firstName, String lastName, UserType userType) {
    }

    /** Requires the booking's user, spot and lot to be loaded. */
    public static ManagerBookingResponse of(Booking booking, String currency) {
        User user = booking.getUser();
        return new ManagerBookingResponse(BookingResponse.of(booking, currency),
                new Customer(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                        user.getUserType()));
    }
}
