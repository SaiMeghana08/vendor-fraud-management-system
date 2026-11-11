package com.vendorfraud.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cooling-off window configuration. The demonstration default is 24
 * hours; every environment may shorten or lengthen it.
 */
@ConfigurationProperties(prefix = "app.cooling-off")
public record CoolingOffProperties(Duration duration, Scheduler scheduler) {

    public CoolingOffProperties {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            duration = Duration.ofHours(24);
        }
        if (scheduler == null) {
            scheduler = new Scheduler(null, 0);
        }
    }

    public record Scheduler(String cron, int batchSize) {
        public Scheduler {
            if (cron == null || cron.isBlank()) {
                cron = "0 */2 * * * *";
            }
            if (batchSize <= 0) {
                batchSize = 50;
            }
        }
    }
}
