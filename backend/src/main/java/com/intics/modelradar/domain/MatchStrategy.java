package com.intics.modelradar.domain;

/**
 * How we discovered a model (Section 4's two tracking strategies).
 *
 * <p>A model could be found by both; when that happens the official tag wins, so
 * {@link #PIPELINE_TAG} is recorded in preference to {@link #KEYWORD_SEARCH}.
 */
public enum MatchStrategy {

    /** Strategy A — matched an official Hugging Face {@code pipeline_tag}. */
    PIPELINE_TAG,

    /** Strategy B — surfaced by a free-text keyword search. */
    KEYWORD_SEARCH
}
