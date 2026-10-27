package com.intics.modelradar.job;

import com.intics.modelradar.domain.SyncTrigger;
import com.intics.modelradar.service.ModelSyncService;
import com.intics.modelradar.service.ModelSyncService.SyncSummary;
import com.intics.modelradar.service.SyncRunService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Runs {@link ModelSyncService#syncAllModels()} on a schedule.
 *
 * <p>Cron comes from {@code sync.cron}; with none set it defaults to {@code "-"},
 * which Spring treats as "disabled" — so the job stays off during normal local dev
 * and only fires where a cron is configured (e.g. the {@code prod} profile, or a
 * temporary override to test). Production runs daily at 08:00 local time.
 *
 * <p>{@link SchedulerLock} guards against overlapping runs via the {@code shedlock}
 * table: if a run is already in progress (here, or on another instance), the tick is
 * skipped. {@code lockAtMostFor} releases a stuck lock; {@code lockAtLeastFor} keeps
 * it briefly even for a fast run so two near-simultaneous ticks can't both proceed.
 */
@Component
public class ScheduledSyncJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledSyncJob.class);

    private final SyncRunService syncRunService;

    public ScheduledSyncJob(SyncRunService syncRunService) {
        this.syncRunService = syncRunService;
    }

    @Scheduled(cron = "${sync.cron:-}")
    @SchedulerLock(name = "modelSyncJob", lockAtMostFor = "PT20M", lockAtLeastFor = "PT5S")
    public void runScheduledSync() {
        long startNanos = System.nanoTime();
        log.info("Scheduled sync started");
        try {
            SyncSummary summary = syncRunService.runAndRecord(SyncTrigger.SCHEDULED);
            log.info("Scheduled sync finished in {} — {} checked, {} new, {} updated",
                    format(Duration.ofNanos(System.nanoTime() - startNanos)),
                    summary.checked(), summary.created(), summary.updated());
        } catch (RuntimeException e) {
            log.error("Scheduled sync failed after {}",
                    format(Duration.ofNanos(System.nanoTime() - startNanos)), e);
            throw e;
        }
    }

    private static String format(Duration d) {
        long seconds = d.toSeconds();
        long millis = d.toMillisPart();
        return seconds > 0 ? "%d.%03ds".formatted(seconds, millis) : "%dms".formatted(d.toMillis());
    }
}
