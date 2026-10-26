package io.github.amir01mn.parking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stand-in mail transport: writes the message to the log. A real SMTP/API sender can replace this bean. */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[email] to={} subject=\"{}\" body=\"{}\"", to, subject, body);
    }
}
