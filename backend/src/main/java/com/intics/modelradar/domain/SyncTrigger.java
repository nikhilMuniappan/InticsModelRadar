package com.intics.modelradar.domain;

/** What initiated a sync run — recorded in the {@code sync_runs} audit trail. */
public enum SyncTrigger {

    /** The daily {@code @Scheduled} job. */
    SCHEDULED,

    /** A manual trigger — the "Refresh Now" button or {@code POST /api/admin/sync}. */
    MANUAL
}
