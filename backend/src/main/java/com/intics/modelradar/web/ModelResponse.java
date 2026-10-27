package com.intics.modelradar.web;

import com.intics.modelradar.domain.AdoptionStatus;
import com.intics.modelradar.domain.ModelAdoption;
import com.intics.modelradar.domain.TrackedModel;

import java.time.Instant;

/**
 * What the dashboard receives for one model. A flat, display-ready shape — the
 * frontend does no further computation ({@link #isNew} is resolved here, and
 * {@link #modelUrl} is stored on the row).
 *
 * <p>The {@code intics*} fields carry our own working state. A model nobody has marked
 * still reports {@code inticsStatus = NOT_REVIEWED} rather than null, so callers never
 * have to special-case the absent row.
 */
public record ModelResponse(
        String id,
        String author,
        String pipelineTag,
        String matchedCategory,
        String matchedCategoryLabel,
        String matchStrategy,
        Instant createdAt,
        Instant lastModified,
        long downloads,
        long likes,
        String license,
        String modelSize,
        String modelUrl,
        String shortDescription,
        Instant firstSeenAt,
        boolean isNew,
        String inticsStatus,
        String inticsStatusLabel,
        String inticsOwner,
        String inticsTeam,
        String inticsUseCase,
        String inticsNotes,
        Instant inticsFirstMarkedAt,
        Instant inticsStatusChangedAt,
        Instant inticsUpdatedAt) {

    public static ModelResponse from(TrackedModel m, Instant newSince) {
        return from(m, newSince, null);
    }

    public static ModelResponse from(TrackedModel m, Instant newSince, ModelAdoption adoption) {
        boolean isNew = m.getFirstSeenAt() != null && !m.getFirstSeenAt().isBefore(newSince);
        AdoptionStatus status = adoption == null ? AdoptionStatus.NOT_REVIEWED : adoption.getStatus();
        return new ModelResponse(
                m.getId(),
                m.getAuthor(),
                m.getPipelineTag(),
                m.getMatchedCategory() == null ? null : m.getMatchedCategory().name(),
                m.getMatchedCategory() == null ? null : m.getMatchedCategory().getDisplayName(),
                m.getMatchStrategy() == null ? null : m.getMatchStrategy().name(),
                m.getCreatedAt(),
                m.getLastModified(),
                m.getDownloads(),
                m.getLikes(),
                m.getLicense(),
                m.getModelSize(),
                m.getModelUrl(),
                m.getShortDescription(),
                m.getFirstSeenAt(),
                isNew,
                status.name(),
                status.getDisplayName(),
                adoption == null ? null : adoption.getOwner(),
                adoption == null ? null : adoption.getTeam(),
                adoption == null ? null : adoption.getUseCase(),
                adoption == null ? null : adoption.getNotes(),
                adoption == null ? null : adoption.getFirstMarkedAt(),
                adoption == null ? null : adoption.getStatusChangedAt(),
                adoption == null ? null : adoption.getUpdatedAt());
    }
}
