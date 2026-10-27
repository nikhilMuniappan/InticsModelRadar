package com.intics.modelradar.repository;

import com.intics.modelradar.domain.Category;
import com.intics.modelradar.domain.TrackedModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Persistence for {@link TrackedModel}. Standard Spring Data JPA.
 *
 * <p>The dashboard's list query has several optional filters (category / "new
 * since" / search). Rather than a JPQL query full of {@code :param is null or …}
 * predicates — which trips PostgreSQL's "could not determine data type of
 * parameter" on untyped NULL binds — filtering is built with {@link
 * JpaSpecificationExecutor}: a predicate is added only when its filter is present,
 * so no null parameters ever reach the database. Sorting/pagination come from the
 * {@code Pageable} passed to {@code findAll(Specification, Pageable)}.
 */
public interface TrackedModelRepository
        extends JpaRepository<TrackedModel, String>, JpaSpecificationExecutor<TrackedModel> {

    /** Count of models per INTICS category (categories with zero rows are simply absent). */
    @Query("select m.matchedCategory as category, count(m) as count"
            + " from TrackedModel m group by m.matchedCategory")
    List<CategoryCount> countByCategory();

    /** Count of models per category first seen on/after {@code since} (for the "NEW" tab badges). */
    @Query("select m.matchedCategory as category, count(m) as count"
            + " from TrackedModel m where m.firstSeenAt >= :since group by m.matchedCategory")
    List<CategoryCount> countByCategorySince(@org.springframework.data.repository.query.Param("since") java.time.Instant since);

    // ── Analytics (native aggregate queries) ──────────────────────────────

    /**
     * Original (non re-upload) models created per day on/after {@code since}, by HF
     * {@code createdAt} in UTC — the raw input to the release-momentum chart.
     *
     * <p>Quantizations and mirrors are excluded: one upstream release can spawn a dozen
     * GGUF/MLX copies, which would show up as a release surge that never happened.
     */
    @Query(value = "select to_char(date_trunc('day', created_at at time zone 'UTC'), 'YYYY-MM-DD') as d, "
            + "count(*) as c from tracked_models "
            + "where created_at >= :since and derivative = false group by 1 order by 1", nativeQuery = true)
    List<Object[]> countByCreatedDay(@org.springframework.data.repository.query.Param("since") java.time.Instant since);

    /** Model count per license, most common first ({@code null} → 'Unknown'). */
    @Query(value = "select coalesce(license, 'Unknown') as l, count(*) as c "
            + "from tracked_models group by 1 order by c desc", nativeQuery = true)
    List<Object[]> countByLicense();

    /**
     * Top publishing organisations by count of <em>original</em> models.
     *
     * <p>Re-uploads are excluded so the ranking reflects who is actually publishing
     * models rather than who is most prolific at repackaging them. Authors are grouped
     * case-insensitively — Hugging Face treats {@code KerasFormers} and
     * {@code kerasformers} as separate accounts, which split one org across two rows —
     * and the most frequently used spelling is shown.
     */
    @Query(value = "select (array_agg(author order by n desc))[1] as display, sum(n) as c from ("
            + "  select author, count(*) as n from tracked_models "
            + "  where author is not null and author <> '' and derivative = false "
            + "  group by author"
            + ") s group by lower(author) order by c desc limit :limit", nativeQuery = true)
    List<Object[]> topAuthors(@org.springframework.data.repository.query.Param("limit") int limit);

    /** How many tracked models are re-uploads rather than original releases. */
    @Query(value = "select count(*) from tracked_models where derivative = true", nativeQuery = true)
    long countDerivatives();

    /** Original models created on the given UTC day — used for the "so far today" caption. */
    @Query(value = "select count(*) from tracked_models where derivative = false "
            + "and created_at >= :dayStart and created_at < :dayEnd", nativeQuery = true)
    long countOriginalsCreatedBetween(
            @org.springframework.data.repository.query.Param("dayStart") java.time.Instant dayStart,
            @org.springframework.data.repository.query.Param("dayEnd") java.time.Instant dayEnd);

    /** Projection for {@link #countByCategory()}. */
    interface CategoryCount {
        Category getCategory();

        long getCount();
    }
}
