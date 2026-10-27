package com.intics.modelradar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A Hugging Face model we're tracking, with every field from Section 5 of the spec
 * plus our own bookkeeping.
 *
 * <p>The schema is owned by Flyway (see {@code db/migration/V1__create_tracked_models.sql}),
 * not Hibernate auto-DDL — {@code spring.jpa.hibernate.ddl-auto} is {@code validate},
 * so this entity and the migration must stay in agreement.
 *
 * <p>{@link #firstSeenAt} is the field that makes "new since last check" (Section 7)
 * work: it records when <em>we</em> first stored the model, which is distinct from
 * Hugging Face's own {@link #createdAt} and is what the dashboard's "NEW" badge uses.
 */
@Entity
@Table(name = "tracked_models")
public class TrackedModel {

    /** Hugging Face model id, e.g. {@code Qwen/Qwen2.5-VL-7B-Instruct}. Primary key. */
    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    /** Publishing author/organisation (HF {@code author}), e.g. {@code Qwen}, {@code google}. */
    @Column
    private String author;

    /** Official HF {@code pipeline_tag}, e.g. {@code image-text-to-text}. May be null. */
    @Column(name = "pipeline_tag")
    private String pipelineTag;

    /** When Hugging Face says the model repo was created. */
    @Column(name = "created_at")
    private Instant createdAt;

    /** When Hugging Face says the model was last modified. */
    @Column(name = "last_modified")
    private Instant lastModified;

    /** All-time downloads reported by HF. */
    @Column(nullable = false)
    private long downloads;

    /** Likes reported by HF. */
    @Column(nullable = false)
    private long likes;

    /** License, from {@code cardData.license} or a {@code license:*} tag. Null if unclear. */
    @Column
    private String license;

    /** Parameter size if cheaply extractable from the id/name (e.g. {@code 7B}). Null otherwise. */
    @Column(name = "model_size")
    private String modelSize;

    /** Public HF page, constructed from the id. Stored so the dashboard need not build it. */
    @Column(name = "model_url", nullable = false)
    private String modelUrl;

    /** Short description if available from the card; else null ("not available"). */
    @Column(name = "short_description", columnDefinition = "text")
    private String shortDescription;

    /** When WE first recorded this model. Drives the "NEW in last N days" badge. */
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    /** When the most recent fetch last touched this row. */
    @Column(name = "last_fetched_at", nullable = false)
    private Instant lastFetchedAt;

    /** The INTICS bucket this model is filed under (Section 4). */
    @Column(name = "matched_category", nullable = false)
    @Enumerated(EnumType.STRING)
    private Category matchedCategory;

    /** Which strategy surfaced this model (pipeline tag vs. keyword search). */
    @Column(name = "match_strategy", nullable = false)
    @Enumerated(EnumType.STRING)
    private MatchStrategy matchStrategy;

    /**
     * True when this is a re-upload of somebody else's model — a GGUF/AWQ/GPTQ
     * quantization, an MLX conversion, a mirror account. Analytics exclude these so
     * the numbers describe genuinely new, distinct models. Maintained at sync time by
     * {@code DerivativeClassifier} from the {@code model-radar.quality} config.
     */
    @Column(nullable = false)
    private boolean derivative;

    protected TrackedModel() {
        // for JPA
    }

    public TrackedModel(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPipelineTag() {
        return pipelineTag;
    }

    public void setPipelineTag(String pipelineTag) {
        this.pipelineTag = pipelineTag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public long getDownloads() {
        return downloads;
    }

    public void setDownloads(long downloads) {
        this.downloads = downloads;
    }

    public long getLikes() {
        return likes;
    }

    public void setLikes(long likes) {
        this.likes = likes;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getModelSize() {
        return modelSize;
    }

    public void setModelSize(String modelSize) {
        this.modelSize = modelSize;
    }

    public String getModelUrl() {
        return modelUrl;
    }

    public void setModelUrl(String modelUrl) {
        this.modelUrl = modelUrl;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastFetchedAt() {
        return lastFetchedAt;
    }

    public void setLastFetchedAt(Instant lastFetchedAt) {
        this.lastFetchedAt = lastFetchedAt;
    }

    public Category getMatchedCategory() {
        return matchedCategory;
    }

    public void setMatchedCategory(Category matchedCategory) {
        this.matchedCategory = matchedCategory;
    }

    public MatchStrategy getMatchStrategy() {
        return matchStrategy;
    }

    public void setMatchStrategy(MatchStrategy matchStrategy) {
        this.matchStrategy = matchStrategy;
    }

    public boolean isDerivative() {
        return derivative;
    }

    public void setDerivative(boolean derivative) {
        this.derivative = derivative;
    }
}
