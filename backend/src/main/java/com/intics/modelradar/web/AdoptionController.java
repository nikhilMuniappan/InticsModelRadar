package com.intics.modelradar.web;

import com.intics.modelradar.domain.AdoptionStatus;
import com.intics.modelradar.service.AdoptionService;
import com.intics.modelradar.service.AdoptionService.StatusUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Write + read API for INTICS adoption state — the same operations the dashboard's
 * "Set status" dialog performs, exposed for scripts and future integrations.
 *
 * <p>Model ids contain slashes, so they are captured with wildcard path variables
 * ({@code /api/adoption/status/Qwen/Qwen2.5-VL-7B-Instruct}).
 */
@RestController
@RequestMapping("/api/adoption")
public class AdoptionController {

    private final AdoptionService adoptionService;

    public AdoptionController(AdoptionService adoptionService) {
        this.adoptionService = adoptionService;
    }

    /** Body of a status change. All fields optional except {@code status}. */
    public record StatusRequest(String status, String owner, String team, String useCase, String note) {
    }

    /** Current INTICS state of one model, after the change. */
    public record AdoptionResponse(String modelId, String status, String statusLabel, String owner,
                                   String team, String useCase, String notes, Instant firstMarkedAt,
                                   Instant statusChangedAt, Instant updatedAt) {
    }

    /** Set or change a model's INTICS status. {@code NOT_REVIEWED} clears it. */
    @PutMapping("/status/{*id}")
    public ResponseEntity<AdoptionResponse> setStatus(@PathVariable String id,
                                                      @RequestBody StatusRequest body) {
        String modelId = trimId(id);
        AdoptionStatus status = AdoptionStatus.fromParam(body.status());
        if (status == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return adoptionService
                    .updateStatus(modelId, new StatusUpdate(status, body.owner(), body.team(),
                            body.useCase(), body.note()))
                    .map(a -> ResponseEntity.ok(new AdoptionResponse(a.getModelId(), a.getStatus().name(),
                            a.getStatus().getDisplayName(), a.getOwner(), a.getTeam(), a.getUseCase(),
                            a.getNotes(), a.getFirstMarkedAt(), a.getStatusChangedAt(), a.getUpdatedAt())))
                    // Cleared back to "not reviewed" — no current-state row remains.
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** One model's timeline, newest first. */
    @GetMapping("/history/{*id}")
    public List<AdoptionEventResponse> history(@PathVariable String id) {
        return adoptionService.history(trimId(id)).stream().map(AdoptionEventResponse::from).toList();
    }

    /** How many models sit at each INTICS stage (every stage listed, 0 when empty). */
    @GetMapping("/summary")
    public List<StatusCount> summary() {
        Map<AdoptionStatus, Long> counts = adoptionService.countsByStatus();
        return Arrays.stream(AdoptionStatus.values())
                .map(s -> new StatusCount(s.name(), s.getDisplayName(), counts.getOrDefault(s, 0L)))
                .toList();
    }

    /** Company-wide activity feed: the most recent status changes across all models. */
    @GetMapping("/activity")
    public List<AdoptionEventResponse> activity(@RequestParam(defaultValue = "20") int limit) {
        return adoptionService.recentActivity(limit).stream().map(AdoptionEventResponse::from).toList();
    }

    /** A stage plus how many models are at it. */
    public record StatusCount(String name, String label, long count) {
    }

    private static String trimId(String pathId) {
        return pathId.startsWith("/") ? pathId.substring(1) : pathId;
    }
}
