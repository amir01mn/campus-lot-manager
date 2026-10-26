package io.github.amir01mn.parking.payment;

public enum PaymentType {
    /** One hour at the user's rate, charged when the booking is created. */
    DEPOSIT,
    /** Total minus deposit, charged at checkout. */
    FINAL,
    /** Deposit returned on an early cancellation. */
    REFUND
}
