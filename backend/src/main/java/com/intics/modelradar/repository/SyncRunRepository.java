package com.intics.modelradar.repository;

import com.intics.modelradar.domain.SyncRun;
import com.intics.modelradar.domain.SyncStatus;
import com.intics.modelradar.domain.SyncTrigger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence for the {@link SyncRun} audit trail. */
public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    /** Most recent successful run — powers the {@code /api/health} freshness check. */
    Optional<SyncRun> findFirstByStatusOrderByFinishedAtDesc(SyncStatus status);

    /** Most recent run for a given trigger (e.g. the last SCHEDULED run). */
    Optional<SyncRun> findFirstByTriggerSourceOrderByStartedAtDesc(SyncTrigger triggerSource);

    /**
     * The distinct UTC days on which a sync completed successfully.
     *
     * <p>Used to work out which release days we can actually vouch for: we only know
     * the full day's releases for a day we synced during <em>and</em> the day after.
     */
    @org.springframework.data.jpa.repository.Query(value =
            "select distinct to_char(date_trunc('day', finished_at at time zone 'UTC'), 'YYYY-MM-DD') "
                    + "from sync_runs where status = 'SUCCESS' and finished_at is not null "
                    + "order by 1", nativeQuery = true)
    java.util.List<String> successfulSyncDays();
}
