package com.intics.modelradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalised configuration under the {@code model-radar} prefix (see application.yml).
 */
@ConfigurationProperties(prefix = "model-radar")
public class ModelRadarProperties {

    private final Huggingface huggingface = new Huggingface();

    public Huggingface getHuggingface() {
        return huggingface;
    }

    public static class Huggingface {
        /** Base URL of the Hub API. */
        private String baseUrl = "https://huggingface.co";
        /** How many newest models to pull per tag / per keyword each run. */
        private int pageSize = 100;
        /** "New in the last N days" window for the NEW badge. */
        private int newWindowDays = 7;
        /** Delay before the single retry after a 429 rate-limit response. */
        private Duration retryBackoff = Duration.ofSeconds(2);
        /** Per-request response timeout for calls to the Hub API. */
        private Duration responseTimeout = Duration.ofSeconds(20);
        /**
         * Max README fetches per sync run (one extra HTTP call each) used to populate
         * {@code shortDescription}. Bounds cost/rate-limit; models are processed
         * newest-first so recent models get descriptions first and older null rows
         * backfill over subsequent runs. Set 0 to disable README fetching.
         */
        private int readmeFetchLimit = 300;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getNewWindowDays() {
            return newWindowDays;
        }

        public void setNewWindowDays(int newWindowDays) {
            this.newWindowDays = newWindowDays;
        }

        public Duration getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
        }

        public Duration getResponseTimeout() {
            return responseTimeout;
        }

        public void setResponseTimeout(Duration responseTimeout) {
            this.responseTimeout = responseTimeout;
        }

        public int getReadmeFetchLimit() {
            return readmeFetchLimit;
        }

        public void setReadmeFetchLimit(int readmeFetchLimit) {
            this.readmeFetchLimit = readmeFetchLimit;
        }
    }
}
