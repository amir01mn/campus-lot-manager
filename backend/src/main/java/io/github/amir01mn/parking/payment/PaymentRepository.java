package io.github.amir01mn.parking.payment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
