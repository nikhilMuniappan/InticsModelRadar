package com.intics.modelradar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One audit-trail record per sync run (Section 10 #1): when it started/finished,
 * what triggered it, whether it succeeded, and the counts it reported. Written by
 * {@code SyncRunService} around every sync, and read by the {@code /api/health} check.
 */
@Entity
@Table(name = "sync_runs")
public class SyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code trigger} is a SQL reserved word, so the column is {@code trigger_source}. */
    @Column(name = "trigger_source", nullable = false)
    @Enumerated(EnumType.STRING)
    private SyncTrigger triggerSource;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SyncStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "models_checked")
    private Integer modelsChecked;

    @Column(name = "new_count")
    private Integer newCount;

    @Column(name = "updated_count")
    private Integer updatedCount;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected SyncRun() {
        // for JPA
    }

    /** Begin a run in RUNNING state. */
    public static SyncRun started(SyncTrigger trigger, Instant at) {
        SyncRun run = new SyncRun();
        run.triggerSource = trigger;
        run.status = SyncStatus.RUNNING;
        run.startedAt = at;
        return run;
    }

    public void markSuccess(Instant at, int modelsChecked, int newCount, int updatedCount) {
        this.finishedAt = at;
        this.status = SyncStatus.SUCCESS;
        this.modelsChecked = modelsChecked;
        this.newCount = newCount;
        this.updatedCount = updatedCount;
    }

    public void markFailure(Instant at, String message) {
        this.finishedAt = at;
        this.status = SyncStatus.FAILURE;
        this.errorMessage = message == null ? null
                : message.substring(0, Math.min(message.length(), 4000));
    }

    public Long getId() {
        return id;
    }

    public SyncTrigger getTriggerSource() {
        return triggerSource;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Integer getModelsChecked() {
        return modelsChecked;
    }

    public Integer getNewCount() {
        return newCount;
    }

    public Integer getUpdatedCount() {
        return updatedCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
