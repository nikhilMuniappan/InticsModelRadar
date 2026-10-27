package com.intics.modelradar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * INTICS-side working state for one tracked model: what stage it is at internally,
 * who owns it, which team, and what it is being used for.
 *
 * <p>One row per model, keyed by the Hugging Face model id — a model only gets a row
 * once somebody marks it, so "no row" reads as {@link AdoptionStatus#NOT_REVIEWED}.
 * The row always holds the <em>current</em> state; the full change history lives in
 * {@link AdoptionEvent}.
 *
 * <p>Dates are recorded for you rather than typed: {@link #firstMarkedAt} is when the
 * model was first picked up by anyone, {@link #statusChangedAt} when it last moved
 * stage, and {@link #updatedAt} when any detail last changed.
 */
@Entity
@Table(name = "model_adoptions")
public class ModelAdoption {

    /** Hugging Face model id — same value as {@link TrackedModel#getId()}. */
    @Id
    @Column(name = "model_id", nullable = false, updatable = false)
    private String modelId;

    /** Current internal stage. Never {@code NOT_REVIEWED} on a stored row. */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AdoptionStatus status;

    /** Person deploying / working with the model, e.g. {@code Nikhil Muniappan}. */
    @Column
    private String owner;

    /** Team inside INTICS that owns the work, e.g. {@code ADI Platform}. */
    @Column
    private String team;

    /** What it is being used for — the INTICS product, project or use case. */
    @Column(name = "use_case", columnDefinition = "text")
    private String useCase;

    /** Free-text notes carried forward from the most recent update. */
    @Column(columnDefinition = "text")
    private String notes;

    /** When this model was first marked by anyone. */
    @Column(name = "first_marked_at", nullable = false, updatable = false)
    private Instant firstMarkedAt;

    /** When {@link #status} last actually changed value. */
    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    /** When any field on this row last changed. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ModelAdoption() {
        // for JPA
    }

    public ModelAdoption(String modelId) {
        this.modelId = modelId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public AdoptionStatus getStatus() {
        return status;
    }

    public void setStatus(AdoptionStatus status) {
        this.status = status;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getUseCase() {
        return useCase;
    }

    public void setUseCase(String useCase) {
        this.useCase = useCase;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getFirstMarkedAt() {
        return firstMarkedAt;
    }

    public void setFirstMarkedAt(Instant firstMarkedAt) {
        this.firstMarkedAt = firstMarkedAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public void setStatusChangedAt(Instant statusChangedAt) {
        this.statusChangedAt = statusChangedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
