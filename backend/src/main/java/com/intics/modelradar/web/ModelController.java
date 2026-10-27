package com.intics.modelradar.web;

import com.intics.modelradar.domain.AdoptionStatus;
import com.intics.modelradar.domain.Category;
import com.intics.modelradar.domain.ModelAdoption;
import com.intics.modelradar.service.AdoptionService;
import com.intics.modelradar.service.ModelQueryService;
import com.intics.modelradar.service.ModelQueryService.SortOption;
import org.springframework.data.domain.Page;
import com.intics.modelradar.domain.TrackedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read API for the dashboard. All reads hit our own database (via
 * {@link ModelQueryService}); Hugging Face is only ever contacted by the sync
 * service (see {@code AdminController} / the scheduler), per Section 7.
 */
@RestController
@RequestMapping("/api")
public class ModelController {

    private final ModelQueryService queryService;
    private final AdoptionService adoptionService;

    public ModelController(ModelQueryService queryService, AdoptionService adoptionService) {
        this.queryService = queryService;
        this.adoptionService = adoptionService;
    }

    /**
     * Paginated list of tracked models.
     *
     * @param category optional INTICS category (enum name, e.g. {@code VLM})
     * @param newSince optional recency filter — only models first seen within the last N days
     * @param sort     {@code newest} (default), {@code downloads}, or {@code likes}
     * @param search   optional case-insensitive substring on model id or author
     * @param status   optional INTICS status (e.g. {@code IN_PROGRESS}, {@code NOT_REVIEWED})
     * @param originals {@code true} to exclude re-uploads (quantizations, mirrors)
     * @param page     zero-based page index (default 0)
     * @param size     page size (default 50, capped server-side)
     */
    @GetMapping("/models")
    public PagedResponse<ModelResponse> models(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer newSince,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean originals,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Category cat = parseCategory(category);
        SortOption sortOption = SortOption.fromParam(sort);
        Instant newThreshold = queryService.newSince();

        Page<TrackedModel> result = queryService.query(
                cat, newSince, sortOption, search, AdoptionStatus.fromParam(status), originals, page, size);
        // One extra query for the whole page rather than one per row.
        Map<String, ModelAdoption> adoptions =
                adoptionService.findAll(result.getContent().stream().map(TrackedModel::getId).toList());
        return PagedResponse.of(result,
                m -> ModelResponse.from(m, newThreshold, adoptions.get(m.getId())));
    }

    /** "NEW this week" view (Section 6): models first seen within the NEW window (7 days). */
    @GetMapping("/models/new")
    public PagedResponse<ModelResponse> newModels(
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean originals,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return models(null, queryService.newWindowDays(), sort, search, status, originals, page, size);
    }

    /**
     * Everything we hold about one model, including its INTICS state and timeline.
     *
     * <p>Model ids contain slashes ({@code Qwen/Qwen2.5-VL-7B-Instruct}), so the id is
     * captured with a wildcard path variable and the leading slash trimmed.
     */
    @GetMapping("/models/detail/{*id}")
    public ResponseEntity<ModelDetailResponse> detail(@PathVariable String id) {
        String modelId = id.startsWith("/") ? id.substring(1) : id;
        return queryService.findById(modelId)
                .map(m -> ResponseEntity.ok(new ModelDetailResponse(
                        ModelResponse.from(m, queryService.newSince(),
                                adoptionService.find(modelId).orElse(null)),
                        adoptionService.history(modelId).stream()
                                .map(AdoptionEventResponse::from)
                                .toList())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** INTICS categories with a count of models in each (all categories listed, 0 if none). */
    @GetMapping("/categories")
    public List<CategoryCount> categories() {
        Map<Category, Long> counts = queryService.countsByCategory();
        return Arrays.stream(Category.values())
                .map(c -> new CategoryCount(c.name(), c.getDisplayName(), counts.getOrDefault(c, 0L)))
                .toList();
    }

    /** A category plus how many tracked models fall under it. */
    public record CategoryCount(String name, String label, long count) {
    }

    /** A model plus its INTICS timeline. */
    public record ModelDetailResponse(ModelResponse model, List<AdoptionEventResponse> history) {
    }

    private static Category parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Category.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null; // unknown category → treat as "all"
        }
    }
}
