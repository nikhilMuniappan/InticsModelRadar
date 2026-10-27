-- V1 — create the tracked_models table (Section 5 fields + our own bookkeeping).
--
-- Schema is owned by Flyway; Hibernate runs with ddl-auto=validate, so the column
-- names/types here must stay in agreement with the TrackedModel entity.
--
-- No secondary indexes yet (only the primary key). Add them later if the
-- dashboard's category/first_seen/sort queries get slow — not before.

CREATE TABLE tracked_models (
    -- Identity ------------------------------------------------------------
    id                 VARCHAR(255) PRIMARY KEY,          -- Hugging Face model id
    author             VARCHAR(255),
    pipeline_tag       VARCHAR(255),

    -- Hugging Face timestamps / stats -------------------------------------
    created_at         TIMESTAMP(6) WITH TIME ZONE,       -- HF createdAt
    last_modified      TIMESTAMP(6) WITH TIME ZONE,       -- HF lastModified
    downloads          BIGINT       NOT NULL,
    likes              BIGINT       NOT NULL,
    license            VARCHAR(255),

    -- Derived / descriptive ----------------------------------------------
    model_size         VARCHAR(255),                      -- e.g. "7B" (nullable)
    model_url          VARCHAR(255) NOT NULL,             -- https://huggingface.co/{id}
    short_description  TEXT,                               -- nullable

    -- Our bookkeeping -----------------------------------------------------
    first_seen_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,  -- when WE first saw it (NEW badge)
    last_fetched_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,  -- when we last refreshed the row

    -- Classification ------------------------------------------------------
    matched_category   VARCHAR(255) NOT NULL,             -- INTICS bucket (Category enum name)
    match_strategy     VARCHAR(255) NOT NULL              -- PIPELINE_TAG | KEYWORD_SEARCH
);
