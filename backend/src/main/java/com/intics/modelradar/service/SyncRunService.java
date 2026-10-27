package com.intics.modelradar.service;

import com.intics.modelradar.domain.SyncRun;
import com.intics.modelradar.domain.SyncStatus;
import com.intics.modelradar.domain.SyncTrigger;
import com.intics.modelradar.repository.SyncRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Runs {@link ModelSyncService#syncAllModels()} and records the outcome in the
 * {@code sync_runs} audit trail (Section 10 #1).
 *
 * <p>This is a separate bean on purpose: it is NOT {@code @Transactional}, so each
 * {@code SyncRun} save runs in its own transaction (a failed sync still leaves a
 * FAILURE audit row), while the call into {@link ModelSyncService} still goes through
 * that bean's proxy so the sync itself remains atomic.
 */
@Service
public class SyncRunService {

    private static final Logger log = LoggerFactory.getLogger(SyncRunService.class);

    private final ModelSyncService syncService;
    private final SyncRunRepository repository;

    public SyncRunService(ModelSyncService syncService, SyncRunRepository repository) {
        this.syncService = syncService;
        this.repository = repository;
    }

    /** Run a sync and record it. Rethrows on failure (after recording it). */
    public ModelSyncService.SyncSummary runAndRecord(SyncTrigger trigger) {
        SyncRun run = repository.save(SyncRun.started(trigger, Instant.now()));
        try {
            ModelSyncService.SyncSummary summary = syncService.syncAllModels();
            run.markSuccess(Instant.now(), summary.checked(), summary.created(), summary.updated());
            repository.save(run);
            return summary;
        } catch (RuntimeException e) {
            run.markFailure(Instant.now(), e.toString());
            repository.save(run);
            log.error("Sync ({}) failed and was recorded as FAILURE", trigger, e);
            throw e;
        }
    }

    /** The most recent successful run (for the health check). */
    public Optional<SyncRun> lastSuccessfulRun() {
        return repository.findFirstByStatusOrderByFinishedAtDesc(SyncStatus.SUCCESS);
    }

    /** The most recent run for a given trigger (e.g. last SCHEDULED run). */
    public Optional<SyncRun> lastRun(SyncTrigger trigger) {
        return repository.findFirstByTriggerSourceOrderByStartedAtDesc(trigger);
    }
}
