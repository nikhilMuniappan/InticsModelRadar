package com.intics.modelradar.service;

import com.intics.modelradar.domain.AdoptionEvent;
import com.intics.modelradar.domain.AdoptionStatus;
import com.intics.modelradar.domain.ModelAdoption;
import com.intics.modelradar.repository.AdoptionEventRepository;
import com.intics.modelradar.repository.ModelAdoptionRepository;
import com.intics.modelradar.repository.TrackedModelRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The INTICS side of the tracker: marking a model as being evaluated / built on /
 * deployed, and reading that state back.
 *
 * <p>Every save writes two things — the current state on {@link ModelAdoption} and an
 * append-only {@link AdoptionEvent} for the timeline. Dates are stamped here rather
 * than typed by the user: first-marked, status-changed and updated timestamps all come
 * from the save itself.
 */
@Service
public class AdoptionService {

    private final ModelAdoptionRepository adoptionRepository;
    private final AdoptionEventRepository eventRepository;
    private final TrackedModelRepository modelRepository;

    public AdoptionService(ModelAdoptionRepository adoptionRepository,
                           AdoptionEventRepository eventRepository,
                           TrackedModelRepository modelRepository) {
        this.adoptionRepository = adoptionRepository;
        this.eventRepository = eventRepository;
        this.modelRepository = modelRepository;
    }

    /** What the status form submits. Blank text fields arrive as null. */
    public record StatusUpdate(AdoptionStatus status, String owner, String team,
                               String useCase, String note) {

        public StatusUpdate {
            owner = trimToNull(owner);
            team = trimToNull(team);
            useCase = trimToNull(useCase);
            note = trimToNull(note);
        }
    }

    /**
     * Record a status change (or a detail-only edit) for one model.
     *
     * <p>Setting {@link AdoptionStatus#NOT_REVIEWED} clears the model's INTICS state:
     * the current-state row is deleted, but the timeline keeps the entry so the history
     * of it having been picked up and dropped survives.
     *
     * @throws IllegalArgumentException if the model id is not tracked
     */
    @Transactional
    public Optional<ModelAdoption> updateStatus(String modelId, StatusUpdate update) {
        if (!modelRepository.existsById(modelId)) {
            throw new IllegalArgumentException("Unknown model: " + modelId);
        }
        Instant now = Instant.now();
        ModelAdoption existing = adoptionRepository.findById(modelId).orElse(null);
        AdoptionStatus from = existing == null ? AdoptionStatus.NOT_REVIEWED : existing.getStatus();
        AdoptionStatus to = update.status() == null ? AdoptionStatus.NOT_REVIEWED : update.status();

        eventRepository.save(new AdoptionEvent(modelId, from, to,
                update.owner(), update.team(), update.useCase(), update.note(), now));

        if (to == AdoptionStatus.NOT_REVIEWED) {
            if (existing != null) {
                adoptionRepository.delete(existing);
            }
            return Optional.empty();
        }

        ModelAdoption adoption = existing == null ? new ModelAdoption(modelId) : existing;
        if (existing == null) {
            adoption.setFirstMarkedAt(now);
            adoption.setStatusChangedAt(now);
        } else if (from != to) {
            adoption.setStatusChangedAt(now);
        }
        adoption.setStatus(to);
        adoption.setOwner(update.owner());
        adoption.setTeam(update.team());
        adoption.setUseCase(update.useCase());
        adoption.setNotes(update.note());
        adoption.setUpdatedAt(now);
        return Optional.of(adoptionRepository.save(adoption));
    }

    /** Current INTICS state for one model, if it has been marked. */
    public Optional<ModelAdoption> find(String modelId) {
        return adoptionRepository.findById(modelId);
    }

    /** Effective status for one model — {@code NOT_REVIEWED} when it has no row. */
    public AdoptionStatus statusOf(String modelId) {
        return find(modelId).map(ModelAdoption::getStatus).orElse(AdoptionStatus.NOT_REVIEWED);
    }

    /**
     * Adoption rows for a batch of model ids, keyed by id — one query for a whole grid
     * page instead of one per row.
     */
    public Map<String, ModelAdoption> findAll(Collection<String> modelIds) {
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        Map<String, ModelAdoption> byId = new HashMap<>();
        for (ModelAdoption a : adoptionRepository.findAllById(modelIds)) {
            byId.put(a.getModelId(), a);
        }
        return byId;
    }

    /** One model's timeline, newest first. */
    public List<AdoptionEvent> history(String modelId) {
        return eventRepository.findByModelIdOrderByChangedAtDesc(modelId);
    }

    /** Company-wide activity feed across all models, newest first. */
    public List<AdoptionEvent> recentActivity(int limit) {
        return eventRepository.recent(PageRequest.of(0, Math.max(1, limit)));
    }

    /** The most recently touched models, newest first — the "current work" list. */
    public List<ModelAdoption> recentlyUpdated(int limit) {
        return adoptionRepository.recentlyUpdated(PageRequest.of(0, Math.max(1, limit)));
    }

    /**
     * Model count per stage. Always contains every stage; {@code NOT_REVIEWED} is the
     * remainder (tracked models with no adoption row).
     */
    public Map<AdoptionStatus, Long> countsByStatus() {
        Map<AdoptionStatus, Long> counts = new EnumMap<>(AdoptionStatus.class);
        for (AdoptionStatus s : AdoptionStatus.values()) {
            counts.put(s, 0L);
        }
        long marked = 0;
        for (ModelAdoptionRepository.StatusCount row : adoptionRepository.countGroupedByStatus()) {
            counts.put(row.getStatus(), row.getCount());
            marked += row.getCount();
        }
        counts.put(AdoptionStatus.NOT_REVIEWED, Math.max(0, modelRepository.count() - marked));
        return counts;
    }

    /** Total models that have been marked with any status. */
    public long markedCount() {
        return adoptionRepository.count();
    }

    /** Owners seen so far, for the form's suggestion list. */
    public List<String> knownOwners() {
        return adoptionRepository.distinctOwners();
    }

    /** Teams seen so far, for the form's suggestion list. */
    public List<String> knownTeams() {
        return adoptionRepository.distinctTeams();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
