package com.intics.modelradar.web;

import com.intics.modelradar.domain.AdoptionEvent;

import java.time.Instant;

/** One entry of a model's INTICS timeline, display-ready. */
public record AdoptionEventResponse(
        Long id,
        String modelId,
        String fromStatus,
        String fromStatusLabel,
        String toStatus,
        String toStatusLabel,
        String owner,
        String team,
        String useCase,
        String note,
        boolean statusChange,
        Instant changedAt) {

    public static AdoptionEventResponse from(AdoptionEvent e) {
        return new AdoptionEventResponse(
                e.getId(),
                e.getModelId(),
                e.getFromStatus() == null ? null : e.getFromStatus().name(),
                e.getFromStatus() == null ? null : e.getFromStatus().getDisplayName(),
                e.getToStatus().name(),
                e.getToStatus().getDisplayName(),
                e.getOwner(),
                e.getTeam(),
                e.getUseCase(),
                e.getNote(),
                e.isStatusChange(),
                e.getChangedAt());
    }
}
