package com.intics.modelradar;

import com.intics.modelradar.config.ModelRadarProperties;
import com.intics.modelradar.config.QualityProperties;
import com.intics.modelradar.config.TrackingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * INTICS Model Radar — entry point.
 *
 * <p>{@link EnableScheduling} turns on Spring's scheduler. The scheduled sync
 * ({@code ScheduledSyncJob}) only actually fires when a cron is configured via
 * {@code sync.cron}; with no cron set it is disabled (the annotation defaults to
 * {@code "-"}), so it stays quiet during normal local development.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ModelRadarProperties.class, TrackingProperties.class,
        QualityProperties.class})
public class ModelRadarApplication {

	public static void main(String[] args) {
		SpringApplication.run(ModelRadarApplication.class, args);
	}

}
