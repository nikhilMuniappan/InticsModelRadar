package com.intics.modelradar.domain;

/** Lifecycle state of a sync run in the {@code sync_runs} audit trail. */
public enum SyncStatus {

    /** Started, not yet finished. */
    RUNNING,

    /** Completed successfully. */
    SUCCESS,

    /** Threw an error before completing. */
    FAILURE
}
