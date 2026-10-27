package com.intics.modelradar.config;

import com.intics.modelradar.domain.Category;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tracking catalog from Section 4, externalized to {@code application.yml}
 * (prefix {@code model-radar.tracking}) so a non-developer can add/remove tags and
 * keywords without touching Java or recompiling.
 *
 * <p>Both maps are keyed by INTICS {@link Category}, so the sync knows which bucket
 * to file a match under simply from which list the tag/keyword came out of:
 * <ul>
 *   <li>{@link #pipelineTags} — Strategy A: official Hugging Face {@code pipeline_tag}
 *       values grouped by category.</li>
 *   <li>{@link #keywords} — Strategy B: free-text search terms grouped by the
 *       category a hit should be filed under.</li>
 * </ul>
 * Iteration order follows the YAML, which makes category precedence deterministic
 * when the same model surfaces under more than one tag.
 */
@ConfigurationProperties(prefix = "model-radar.tracking")
public class TrackingProperties {

    private Map<Category, List<String>> pipelineTags = new LinkedHashMap<>();
    private Map<Category, List<String>> keywords = new LinkedHashMap<>();

    public Map<Category, List<String>> getPipelineTags() {
        return pipelineTags;
    }

    public void setPipelineTags(Map<Category, List<String>> pipelineTags) {
        this.pipelineTags = pipelineTags;
    }

    public Map<Category, List<String>> getKeywords() {
        return keywords;
    }

    public void setKeywords(Map<Category, List<String>> keywords) {
        this.keywords = keywords;
    }
}
