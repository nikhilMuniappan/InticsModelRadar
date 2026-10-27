package com.intics.modelradar.service;

import com.intics.modelradar.config.QualityProperties;
import com.intics.modelradar.hf.HuggingFaceModel;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Decides whether a model is a <em>re-upload</em> — a quantization, conversion or
 * mirror of somebody else's model — rather than an original release.
 *
 * <p>Used at sync time to stamp {@code tracked_models.derivative}, which the analytics
 * then use to answer "how many genuinely new, distinct models appeared?" without a
 * prolific quantizer drowning out the labs actually publishing work.
 *
 * <p>Patterns come from {@link QualityProperties}; an invalid regex is skipped with a
 * warning rather than breaking the sync.
 */
@Component
public class DerivativeClassifier {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DerivativeClassifier.class);

    private final Set<String> mirrorAuthors;
    private final List<Pattern> patterns;

    public DerivativeClassifier(QualityProperties props) {
        this.mirrorAuthors = new HashSet<>();
        for (String a : props.getMirrorAuthors()) {
            if (a != null && !a.isBlank()) {
                mirrorAuthors.add(a.trim().toLowerCase(Locale.ROOT));
            }
        }
        this.patterns = props.getDerivativePatterns().stream()
                .map(DerivativeClassifier::compile)
                .filter(java.util.Objects::nonNull)
                .toList();
        log.info("Re-upload classifier: {} mirror accounts, {} id patterns",
                mirrorAuthors.size(), patterns.size());
    }

    private static Pattern compile(String regex) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            log.warn("Ignoring invalid model-radar.quality.derivative-patterns entry '{}': {}",
                    regex, e.getMessage());
            return null;
        }
    }

    /** True when this model looks like a re-upload rather than an original release. */
    public boolean isDerivative(String modelId, String author) {
        if (author != null && mirrorAuthors.contains(author.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (modelId == null) {
            return false;
        }
        // Match on the repo name only: an author called "mlx-community" is already
        // covered above, and we don't want an unlucky org name to condemn every model.
        String name = modelId.contains("/") ? modelId.substring(modelId.indexOf('/') + 1) : modelId;
        for (Pattern p : patterns) {
            if (p.matcher(name).find()) {
                return true;
            }
        }
        return false;
    }

    /** Convenience overload for a freshly fetched Hugging Face model. */
    public boolean isDerivative(HuggingFaceModel model, String resolvedAuthor) {
        return isDerivative(model.id, resolvedAuthor);
    }
}
