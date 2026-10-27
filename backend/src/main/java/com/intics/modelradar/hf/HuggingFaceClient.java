package com.intics.modelradar.hf;

import com.intics.modelradar.config.ModelRadarProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Client for the Hugging Face Hub API's {@code /api/models} endpoint, built on
 * {@link WebClient}. There is no official Java SDK for Hugging Face, so this calls
 * the plain REST endpoints directly and lets Jackson map the JSON into
 * {@link HuggingFaceModel}.
 *
 * <p>Both listing strategies from Section 4 are exposed:
 * <ul>
 *   <li>{@link #fetchModelsByPipelineTag} — Strategy A (official tag)</li>
 *   <li>{@link #searchModelsByKeyword} — Strategy B (free-text search)</li>
 * </ul>
 * Both sort by {@code createdAt} descending (newest first) and request
 * {@code cardData=true} so the license is available.
 *
 * <p>Resilience: a {@code 429 Too Many Requests} is retried once after a short
 * backoff; if it still fails (or any other error occurs) the call is logged and an
 * empty list is returned, so one bad tag/keyword never aborts a whole fetch run.
 */
@Component
public class HuggingFaceClient {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceClient.class);

    private static final String MODELS_PATH = "/api/models";
    // Guard against WebClient's default 256 KB in-memory limit — a page of 100
    // models with tags + cardData easily exceeds it.
    private static final int MAX_IN_MEMORY_BYTES = 16 * 1024 * 1024;

    private final WebClient webClient;
    private final Duration retryBackoff;

    public HuggingFaceClient(ModelRadarProperties props, WebClient.Builder builder) {
        ModelRadarProperties.Huggingface hf = props.getHuggingface();
        this.retryBackoff = hf.getRetryBackoff();

        HttpClient httpClient = HttpClient.create()
                .followRedirect(true) // raw README URLs may redirect
                .responseTimeout(hf.getResponseTimeout());

        this.webClient = builder
                .baseUrl(hf.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .filter(logRequestUrl())
                .build();
    }

    /** Strategy A — newest models carrying the given official pipeline tag. */
    public List<HuggingFaceModel> fetchModelsByPipelineTag(String tag, int limit) {
        return getModels(uri -> uri
                .path(MODELS_PATH)
                .queryParam("pipeline_tag", tag)
                .queryParam("sort", "createdAt")
                .queryParam("direction", -1)
                .queryParam("limit", limit)
                .queryParam("cardData", true)
                // full=true is required for the API to include lastModified (Section 5).
                .queryParam("full", true)
                .build(), "pipeline_tag=" + tag);
    }

    /** Strategy B — newest models matching a free-text keyword search. */
    public List<HuggingFaceModel> searchModelsByKeyword(String keyword, int limit) {
        return getModels(uri -> uri
                .path(MODELS_PATH)
                .queryParam("search", keyword)
                .queryParam("sort", "createdAt")
                .queryParam("direction", -1)
                .queryParam("limit", limit)
                .queryParam("cardData", true)
                // full=true is required for the API to include lastModified (Section 5).
                .queryParam("full", true)
                .build(), "search=" + keyword);
    }

    /**
     * Fetch a model's raw README and distill it into a short description (Section 5).
     * Best-effort: returns {@code null} if the model has no README (404) or on any error.
     */
    public String fetchDescription(String modelId) {
        try {
            String readme = webClient.get()
                    // literal path so the "/" in "org/name" stays a path separator, not %2F
                    .uri(uri -> uri.path("/" + modelId + "/raw/main/README.md").build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return HuggingFaceMetadata.shortDescriptionFromReadme(readme);
        } catch (Exception e) {
            log.debug("No usable README for {}: {}", modelId, e.toString());
            return null;
        }
    }

    private List<HuggingFaceModel> getModels(Function<UriBuilder, URI> uriSpec, String label) {
        try {
            List<HuggingFaceModel> body = webClient.get()
                    .uri(uriSpec)
                    .retrieve()
                    .bodyToFlux(HuggingFaceModel.class)
                    .collectList()
                    // 429 → back off and retry once; then rethrow the original error.
                    .retryWhen(Retry.fixedDelay(1, retryBackoff)
                            .filter(HuggingFaceClient::isRateLimited)
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();
            return body == null ? List.of() : body;
        } catch (Exception e) {
            if (isRateLimited(e)) {
                log.warn("Hugging Face rate-limited (429) for {} even after retry — skipping", label);
            } else {
                log.warn("Hugging Face fetch failed for {}: {}", label, e.toString());
            }
            return List.of();
        }
    }

    private static boolean isRateLimited(Throwable t) {
        return t instanceof WebClientResponseException w && w.getStatusCode().value() == 429;
    }

    /** Logs the fully-resolved request URL at DEBUG so calls can be traced later. */
    private static ExchangeFilterFunction logRequestUrl() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            if (log.isDebugEnabled()) {
                log.debug("Hugging Face request: {} {}", request.method(), request.url());
            }
            return Mono.just(request);
        });
    }
}
