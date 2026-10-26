package io.github.amir01mn.parking.notification;

/** Outbound e-mail. The only implementation logs instead of sending (see {@link LoggingEmailSender}). */
public interface EmailSender {

    void send(String to, String subject, String body);
}
