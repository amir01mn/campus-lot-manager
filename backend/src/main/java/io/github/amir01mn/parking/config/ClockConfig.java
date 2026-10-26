package io.github.amir01mn.parking.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single source of "now" so tests can control time. Campus time zone is America/Toronto. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:America/Toronto}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
