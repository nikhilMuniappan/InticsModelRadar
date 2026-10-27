package com.intics.modelradar.service;

import com.intics.modelradar.config.ModelRadarProperties;
import com.intics.modelradar.config.TrackingProperties;
import com.intics.modelradar.domain.Category;
import com.intics.modelradar.domain.MatchStrategy;
import com.intics.modelradar.domain.TrackedModel;
import com.intics.modelradar.hf.HuggingFaceClient;
import com.intics.modelradar.hf.HuggingFaceMetadata;
import com.intics.modelradar.hf.HuggingFaceModel;
import com.intics.modelradar.repository.TrackedModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Syncs Hugging Face into our database using the externalized {@link TrackingProperties}
 * catalog. Two passes (Section 4):
 * <ol>
 *   <li><b>Strategy A</b> — every configured pipeline tag; each model is filed under
 *       the category the tag belongs to, with {@link MatchStrategy#PIPELINE_TAG}.</li>
 *   <li><b>Strategy B</b> — every configured keyword; models already seen in the
 *       pipeline pass are skipped, so a keyword-only match is filed under the
 *       keyword's category with {@link MatchStrategy#KEYWORD_SEARCH}.</li>
 * </ol>
 *
 * <p>Persistence rule: a model already in the DB has only its mutable fields refreshed
 * (downloads, likes, lastModified, lastFetchedAt) — {@code firstSeenAt} (and the
 * original category/strategy) are never overwritten, so the "NEW" badge stays correct.
 */
@Service
public class ModelSyncService {

    private static final Logger log = LoggerFactory.getLogger(ModelSyncService.class);

    private static final String HF_MODEL_URL_PREFIX = "https://huggingface.co/";

    private final HuggingFaceClient client;
    private final TrackedModelRepository repository;
    private final TrackingProperties tracking;
    private final DerivativeClassifier derivativeClassifier;
    private final int pageSize;
    private final int readmeFetchLimit;

    public ModelSyncService(HuggingFaceClient client,
                            TrackedModelRepository repository,
                            TrackingProperties tracking,
                            DerivativeClassifier derivativeClassifier,
                            ModelRadarProperties props) {
        this.client = client;
        this.repository = repository;
        this.tracking = tracking;
        this.derivativeClassifier = derivativeClassifier;
        this.pageSize = props.getHuggingface().getPageSize();
        this.readmeFetchLimit = props.getHuggingface().getReadmeFetchLimit();
    }

    /**
     * Run a full sync of every configured tag and keyword.
     *
     * @return a summary of how many models were checked / inserted / updated
     */
    @Transactional
    public SyncSummary syncAllModels() {
        Instant now = Instant.now();
        // Tracks every id we've already persisted this run, so the keyword pass
        // skips anything the pipeline pass already caught (and we never double-count).
        Set<String> seenIds = new HashSet<>();
        int created = 0;
        int updated = 0;
        // Remaining README fetches allowed this run (populates shortDescription).
        int[] readmeBudget = { readmeFetchLimit };

        // Strategy A — pipeline tags, grouped by category.
        for (Map.Entry<Category, List<String>> entry : tracking.getPipelineTags().entrySet()) {
            Category category = entry.getKey();
            for (String tag : entry.getValue()) {
                for (HuggingFaceModel model : client.fetchModelsByPipelineTag(tag, pageSize)) {
                    if (model.id == null || !seenIds.add(model.id)) {
                        continue;
                    }
                    if (persist(model, category, MatchStrategy.PIPELINE_TAG, now, readmeBudget)) {
                        created++;
                    } else {
                        updated++;
                    }
                }
            }
        }

        // Strategy B — keywords, grouped by category; skip ids already caught above.
        for (Map.Entry<Category, List<String>> entry : tracking.getKeywords().entrySet()) {
            Category category = entry.getKey();
            for (String keyword : entry.getValue()) {
                for (HuggingFaceModel model : client.searchModelsByKeyword(keyword, pageSize)) {
                    if (model.id == null || !seenIds.add(model.id)) {
                        continue;
                    }
                    if (persist(model, category, MatchStrategy.KEYWORD_SEARCH, now, readmeBudget)) {
                        created++;
                    } else {
                        updated++;
                    }
                }
            }
        }

        int checked = seenIds.size();
        int descriptionsFetched = readmeFetchLimit - readmeBudget[0];
        log.info("Sync complete: {} models checked, {} new, {} updated ({} descriptions fetched)",
                checked, created, updated, descriptionsFetched);
        return new SyncSummary(checked, created, updated);
    }

    /**
     * Insert a new model, or refresh the mutable fields of an existing one.
     *
     * @return {@code true} if a new row was inserted, {@code false} if an existing
     *         row was updated
     */
    private boolean persist(HuggingFaceModel model, Category category, MatchStrategy strategy,
                            Instant now, int[] readmeBudget) {
        TrackedModel existing = repository.findById(model.id).orElse(null);
        if (existing == null) {
            TrackedModel entity = new TrackedModel(model.id);
            entity.setFirstSeenAt(now); // stamped once — never overwritten (drives NEW badge)
            entity.setAuthor(resolveAuthor(model));
            entity.setPipelineTag(model.pipelineTag);
            entity.setCreatedAt(model.createdAt);
            entity.setLastModified(model.lastModified);
            entity.setDownloads(model.downloads);
            entity.setLikes(model.likes);
            entity.setLicense(HuggingFaceMetadata.extractLicense(model));
            entity.setModelSize(HuggingFaceMetadata.extractModelSize(model.id));
            entity.setModelUrl(HF_MODEL_URL_PREFIX + model.id);
            entity.setShortDescription(fetchDescription(model.id, readmeBudget)); // first lines of the README
            entity.setMatchedCategory(category);
            entity.setMatchStrategy(strategy);
            entity.setDerivative(derivativeClassifier.isDerivative(model, entity.getAuthor()));
            entity.setLastFetchedAt(now);
            repository.save(entity);
            return true;
        }

        // Existing row: refresh only the mutable fields; leave firstSeenAt/category/strategy intact.
        existing.setDownloads(model.downloads);
        existing.setLikes(model.likes);
        existing.setLastModified(model.lastModified);
        // Backfill a missing description (older rows predate README fetching), budget permitting.
        if (existing.getShortDescription() == null) {
            String desc = fetchDescription(model.id, readmeBudget);
            if (desc != null) {
                existing.setShortDescription(desc);
            }
        }
        // Re-run the classifier: the mirror/pattern config can change between syncs.
        existing.setDerivative(derivativeClassifier.isDerivative(model, existing.getAuthor()));
        existing.setLastFetchedAt(now);
        repository.save(existing);
        return false;
    }

    /** Fetch a README-derived description if we still have budget this run; else null. */
    private String fetchDescription(String modelId, int[] readmeBudget) {
        if (readmeBudget[0] <= 0) {
            return null;
        }
        readmeBudget[0]--;
        return client.fetchDescription(modelId);
    }

    private static String resolveAuthor(HuggingFaceModel m) {
        if (m.author != null && !m.author.isBlank()) {
            return m.author;
        }
        int slash = m.id == null ? -1 : m.id.indexOf('/');
        return slash > 0 ? m.id.substring(0, slash) : null;
    }

    /** Summary of one sync run, returned to the admin endpoint and logged. */
    public record SyncSummary(int checked, int created, int updated) {
    }
}
