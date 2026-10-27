package com.intics.modelradar.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Schedules a single, one-off sync run at a configured wall-clock time
 * ({@code sync.kickoff}, an ISO-8601 timestamp with offset, e.g.
 * {@code 2026-07-28T14:10:00-04:00}) — separate from the recurring daily cron.
 *
 * <p>Runs the exact same {@link ScheduledSyncJob#runScheduledSync()} (so it's
 * ShedLock-guarded and recorded as a SCHEDULED run). If {@code sync.kickoff} is
 * unset or already in the past, nothing is scheduled. Requires the app to be running
 * at that time (in-JVM scheduling).
 */
@Component
public class SyncKickoff {

    private static final Logger log = LoggerFactory.getLogger(SyncKickoff.class);

    private final TaskScheduler taskScheduler;
    private final ScheduledSyncJob scheduledSyncJob;
    private final String kickoff;

    public SyncKickoff(TaskScheduler taskScheduler,
                       ScheduledSyncJob scheduledSyncJob,
                       @Value("${sync.kickoff:}") String kickoff) {
        this.taskScheduler = taskScheduler;
        this.scheduledSyncJob = scheduledSyncJob;
        this.kickoff = kickoff;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleKickoff() {
        if (kickoff == null || kickoff.isBlank()) {
            return;
        }
        Instant at;
        try {
            at = OffsetDateTime.parse(kickoff.trim()).toInstant();
        } catch (Exception e) {
            log.warn("Invalid sync.kickoff '{}' (expected ISO-8601 with offset like "
                    + "2026-07-28T14:10:00-04:00): {}", kickoff, e.toString());
            return;
        }
        if (at.isBefore(Instant.now())) {
            log.warn("sync.kickoff {} is already in the past — not scheduling a one-time run", kickoff);
            return;
        }
        taskScheduler.schedule(() -> {
            log.info("One-time kickoff sync firing now (configured for {})", kickoff);
            try {
                scheduledSyncJob.runScheduledSync();
            } catch (RuntimeException ex) {
                log.error("Kickoff sync failed", ex);
            }
        }, at);
        log.info("Scheduled one-time kickoff sync for {} (resolved to {})", kickoff, at);
    }
}
