package com.intics.modelradar.service;

import com.intics.modelradar.config.ModelRadarProperties;
import com.intics.modelradar.domain.AdoptionStatus;
import com.intics.modelradar.domain.Category;
import com.intics.modelradar.domain.ModelAdoption;
import com.intics.modelradar.domain.TrackedModel;
import com.intics.modelradar.repository.SyncRunRepository;
import com.intics.modelradar.repository.TrackedModelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read side for the dashboard: category filter, "new since N days" filter, sort,
 * case-insensitive id/author search, and pagination — all against our own stored
 * data, never calling Hugging Face (Section 7).
 */
@Service
public class ModelQueryService {

    /** Largest page the API will serve, to keep responses bounded. */
    private static final int MAX_PAGE_SIZE = 200;

    /** Sort options offered by the dashboard (Section 6). */
    public enum SortOption {
        NEWEST,
        DOWNLOADS,
        LIKES;

        public static SortOption fromParam(String raw) {
            if (raw == null || raw.isBlank()) {
                return NEWEST;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return NEWEST;
            }
        }
    }

    private final TrackedModelRepository repository;
    private final SyncRunRepository syncRunRepository;
    private final ModelRadarProperties props;

    public ModelQueryService(TrackedModelRepository repository, SyncRunRepository syncRunRepository,
                             ModelRadarProperties props) {
        this.repository = repository;
        this.syncRunRepository = syncRunRepository;
        this.props = props;
    }

    /** The "NEW badge" window length in days (config: model-radar.huggingface.new-window-days). */
    public int newWindowDays() {
        return props.getHuggingface().getNewWindowDays();
    }

    /** Instant marking the start of the NEW window (now - newWindowDays); used to compute isNew. */
    public Instant newSince() {
        return Instant.now().minus(newWindowDays(), ChronoUnit.DAYS);
    }

    /**
     * Paginated, filtered query.
     *
     * @param category     null = all categories
     * @param newSinceDays null = no recency filter; else only models first seen within N days
     * @param sort         sort order
     * @param search       optional case-insensitive substring on id/author
     * @param originalsOnly true to hide re-uploads (quantizations, mirrors)
     * @param page         zero-based page index (clamped to >= 0)
     * @param size         page size (clamped to 1..{@value #MAX_PAGE_SIZE})
     */
    public Page<TrackedModel> query(Category category, Integer newSinceDays, SortOption sort,
                                    String search, AdoptionStatus status, boolean originalsOnly,
                                    int page, int size) {
        Instant since = newSinceDays == null ? null
                : Instant.now().minus(newSinceDays, ChronoUnit.DAYS);
        String needle = (search == null || search.isBlank()) ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, toSort(sort));

        return repository.findAll(buildSpec(category, since, needle, status, originalsOnly), pageable);
    }

    /** Combine only the filters that are actually set — no null parameters reach SQL. */
    private static Specification<TrackedModel> buildSpec(Category category, Instant since,
                                                         String needle, AdoptionStatus status,
                                                         boolean originalsOnly) {
        Specification<TrackedModel> spec = (root, query, cb) -> cb.conjunction();
        if (category != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("matchedCategory"), category));
        }
        if (since != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("firstSeenAt"), since));
        }
        if (needle != null) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("id")), needle),
                    cb.like(cb.lower(root.get("author")), needle)));
        }
        if (status != null) {
            spec = spec.and(statusSpec(status));
        }
        if (originalsOnly) {
            spec = spec.and((root, query, cb) -> cb.isFalse(root.get("derivative")));
        }
        return spec;
    }

    /**
     * INTICS-status filter. Adoption lives in its own table keyed by model id, so this
     * is an {@code id IN (subquery)} rather than a join — which also makes
     * "not reviewed" expressible as the negation (no adoption row at all).
     */
    private static Specification<TrackedModel> statusSpec(AdoptionStatus status) {
        return (root, query, cb) -> {
            var sub = query.subquery(String.class);
            var adoption = sub.from(ModelAdoption.class);
            sub.select(adoption.get("modelId"));
            if (status == AdoptionStatus.NOT_REVIEWED) {
                return cb.not(root.get("id").in(sub));
            }
            sub.where(cb.equal(adoption.get("status"), status));
            return root.get("id").in(sub);
        };
    }

    /**
     * A window of models for the dashboard's lazy grid — filtered, sorted, and sliced
     * by absolute {@code offset}/{@code limit} (see {@link OffsetPageable}). Filtering
     * and pagination happen in SQL, not in memory.
     */
    public List<TrackedModel> queryPage(Category category, Integer newSinceDays, SortOption sort,
                                        String search, AdoptionStatus status, boolean originalsOnly,
                                        int offset, int limit) {
        Specification<TrackedModel> spec =
                buildSpec(category, sinceFrom(newSinceDays), needle(search), status, originalsOnly);
        Pageable pageable = new OffsetPageable(offset, limit, toSort(sort));
        return repository.findAll(spec, pageable).getContent();
    }

    /** Total rows matching the given filters (for the grid's item-count callback). */
    public int countModels(Category category, Integer newSinceDays, String search,
                           AdoptionStatus status, boolean originalsOnly) {
        Specification<TrackedModel> spec =
                buildSpec(category, sinceFrom(newSinceDays), needle(search), status, originalsOnly);
        return (int) repository.count(spec);
    }

    /** One model by its Hugging Face id, for the detail panel. */
    public java.util.Optional<TrackedModel> findById(String id) {
        return repository.findById(id);
    }

    private Instant sinceFrom(Integer newSinceDays) {
        return newSinceDays == null ? null : Instant.now().minus(newSinceDays, ChronoUnit.DAYS);
    }

    private static String needle(String search) {
        return (search == null || search.isBlank()) ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    /** Count of models in each category (missing categories default to 0 at the caller). */
    public Map<Category, Long> countsByCategory() {
        return toMap(repository.countByCategory());
    }

    /** Count of models first seen within the NEW window, per category (for tab "NEW" badges). */
    public Map<Category, Long> newCountsByCategory() {
        return toMap(repository.countByCategorySince(newSince()));
    }

    private static Map<Category, Long> toMap(List<TrackedModelRepository.CategoryCount> rows) {
        Map<Category, Long> counts = new EnumMap<>(Category.class);
        for (TrackedModelRepository.CategoryCount cc : rows) {
            counts.put(cc.getCategory(), cc.getCount());
        }
        return counts;
    }

    // ── Analytics ─────────────────────────────────────────────────────────

    /**
     * Release momentum: original models published per day, restricted to days whose
     * count we can actually stand behind.
     *
     * @param days how far back to look (the answer may be shorter — see below)
     */
    public record Momentum(
            /**
             * Every day in the window (today excluded), in order. The value is the day's
             * count, or {@code null} where coverage is incomplete — the chart draws those
             * as gaps rather than as zeros, because we don't know what happened, and
             * plotting a guess is what made the old chart lie.
             */
            LinkedHashMap<LocalDate, Long> window,
            /** Days we can vouch for. */
            int coveredDays,
            /** Days dropped as not fully covered. */
            int excludedDays,
            /** Total days requested. */
            int requestedDays,
            /** Original models created so far today — partial, hence outside the window. */
            long todaySoFar,
            /** Average per fully-covered day, or 0 when nothing qualifies. */
            long dailyAverage) {

        public boolean isEmpty() {
            return coveredDays == 0;
        }
    }

    /**
     * Why days get dropped: our count for a given day is only complete if a sync ran
     * during that day <em>and</em> on the following day. A day we never synced (before
     * tracking started, or while the machine was asleep) is under-counted — Hugging
     * Face is queried newest-first with a page limit, so a sync catching up on a
     * backlog silently misses the older end of it. Charting those days next to
     * fully-synced ones invents a spike where there is only a gap in our own sampling.
     *
     * <p>Today is always excluded too: the day is still in progress.
     */
    public Momentum releaseMomentum(int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate windowStart = today.minusDays(days - 1L);

        Set<LocalDate> syncedDays = new HashSet<>();
        for (String d : syncRunRepository.successfulSyncDays()) {
            syncedDays.add(LocalDate.parse(d));
        }

        Map<LocalDate, Long> counts = new HashMap<>();
        Instant since = windowStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        for (Object[] row : repository.countByCreatedDay(since)) {
            counts.put(LocalDate.parse((String) row[0]), ((Number) row[1]).longValue());
        }

        LinkedHashMap<LocalDate, Long> window = new LinkedHashMap<>();
        int covered = 0;
        int excluded = 0;
        for (LocalDate d = windowStart; d.isBefore(today); d = d.plusDays(1)) {
            if (syncedDays.contains(d) && syncedDays.contains(d.plusDays(1))) {
                window.put(d, counts.getOrDefault(d, 0L));
                covered++;
            } else {
                window.put(d, null); // a gap in our sampling, not a quiet day
                excluded++;
            }
        }

        long todaySoFar = repository.countOriginalsCreatedBetween(
                today.atStartOfDay(ZoneOffset.UTC).toInstant(),
                today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        long average = covered == 0 ? 0
                : Math.round(window.values().stream().filter(java.util.Objects::nonNull)
                        .mapToLong(Long::longValue).average().orElse(0));

        return new Momentum(window, covered, excluded, days, todaySoFar, average);
    }

    /** How many tracked models are re-uploads (quantizations, mirrors) rather than originals. */
    public long derivativeCount() {
        return repository.countDerivatives();
    }

    /** License breakdown: the top {@code topN} licenses, the rest folded into "Other". */
    public LinkedHashMap<String, Long> licenseBreakdown(int topN) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        long other = 0;
        int i = 0;
        for (Object[] row : repository.countByLicense()) {
            String license = (String) row[0];
            long count = ((Number) row[1]).longValue();
            if (i < topN) {
                result.put(license, count);
            } else {
                other += count;
            }
            i++;
        }
        if (other > 0) {
            result.put("Other", other);
        }
        return result;
    }

    /** Top publishing organisations by model count. */
    public LinkedHashMap<String, Long> topOrganizations(int limit) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : repository.topAuthors(limit)) {
            result.put((String) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private static Sort toSort(SortOption sort) {
        // Note: no explicit NULLS LAST — the Criteria path (Specification queries)
        // rejects null precedence, and createdAt is effectively never null anyway.
        return switch (sort) {
            case DOWNLOADS -> Sort.by(Sort.Order.desc("downloads"));
            case LIKES -> Sort.by(Sort.Order.desc("likes"));
            case NEWEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("firstSeenAt"));
        };
    }
}
