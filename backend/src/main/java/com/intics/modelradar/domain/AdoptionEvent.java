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
 * One entry in a model's INTICS timeline — appended every time somebody saves the
 * status form, whether or not the stage itself moved (a note or an owner change is
 * worth recording too).
 *
 * <p>Rows are never updated or deleted, so the timeline is a faithful audit trail of
 * who did what and when.
 */
@Entity
@Table(name = "adoption_events")
public class AdoptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The model this entry belongs to. */
    @Column(name = "model_id", nullable = false, updatable = false)
    private String modelId;

    /** Stage before this change; null for the very first entry. */
    @Column(name = "from_status")
    @Enumerated(EnumType.STRING)
    private AdoptionStatus fromStatus;

    /** Stage after this change. */
    @Column(name = "to_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AdoptionStatus toStatus;

    /** Owner recorded at the time of the change. */
    @Column
    private String owner;

    /** Team recorded at the time of the change. */
    @Column
    private String team;

    /** Use case recorded at the time of the change. */
    @Column(name = "use_case", columnDefinition = "text")
    private String useCase;

    /** The note typed with this particular change. */
    @Column(columnDefinition = "text")
    private String note;

    /** When the change was saved. */
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected AdoptionEvent() {
        // for JPA
    }

    public AdoptionEvent(String modelId, AdoptionStatus fromStatus, AdoptionStatus toStatus,
                         String owner, String team, String useCase, String note, Instant changedAt) {
        this.modelId = modelId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.owner = owner;
        this.team = team;
        this.useCase = useCase;
        this.note = note;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public String getModelId() {
        return modelId;
    }

    public AdoptionStatus getFromStatus() {
        return fromStatus;
    }

    public AdoptionStatus getToStatus() {
        return toStatus;
    }

    public String getOwner() {
        return owner;
    }

    public String getTeam() {
        return team;
    }

    public String getUseCase() {
        return useCase;
    }

    public String getNote() {
        return note;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    /** True when this entry actually moved the model to a different stage. */
    public boolean isStatusChange() {
        return fromStatus != toStatus;
    }
}
