package com.intics.modelradar.hf;

import com.intics.modelradar.config.ModelRadarProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that hits the <em>real</em> Hugging Face Hub API — no mocks.
 *
 * <p>Tagged {@code integration} so it can be excluded where a live network call is
 * undesirable (e.g. CI): run everything except this with
 * {@code ./mvnw test -DexcludedGroups=integration}.
 *
 * <p>Built without a Spring context on purpose: the client only needs a
 * {@link WebClient.Builder} and {@link ModelRadarProperties}, so there's no need to
 * start the datasource/JPA just to exercise the HTTP client.
 */
@Tag("integration")
class HuggingFaceClientIntegrationTest {

    private final HuggingFaceClient client =
            new HuggingFaceClient(new ModelRadarProperties(), WebClient.builder());

    @Test
    void fetchModelsByPipelineTagReturnsResults() {
        List<HuggingFaceModel> models = client.fetchModelsByPipelineTag("image-text-to-text", 5);

        assertThat(models)
                .as("Hugging Face should return at least one image-text-to-text model")
                .isNotEmpty();
        assertThat(models)
                .allSatisfy(m -> assertThat(m.id).isNotBlank());
    }
}
