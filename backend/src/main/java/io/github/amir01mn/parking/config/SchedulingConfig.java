package io.github.amir01mn.parking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs unless {@code app.jobs.enabled=false} (set in the {@code test} profile so tests
 * control when jobs run). Jobs are single-instance: running several API replicas would need a lock such as ShedLock.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
