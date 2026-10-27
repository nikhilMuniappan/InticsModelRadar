package com.intics.modelradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Which tracked models are <em>re-uploads</em> rather than original releases —
 * externalized to {@code application.yml} (prefix {@code model-radar.quality}) so the
 * lists can be tuned without recompiling, the same way the tracking catalog is.
 *
 * <p>Hugging Face is full of accounts that mirror other people's work in a different
 * format — GGUF/AWQ/GPTQ quantizations, MLX conversions, low-bit repacks. They are
 * legitimate artefacts, but counting them as new releases makes the numbers lie: a
 * single upstream model can appear a dozen times under a dozen mirror accounts, and
 * a prolific quantizer outranks the labs actually shipping models.
 *
 * <p>A model is treated as a re-upload if <strong>either</strong> its author is a
 * known mirror account, <strong>or</strong> its id matches one of the format
 * patterns. Both are deliberately conservative — the goal is to remove obvious
 * mirrors, not to guess at what counts as "original" research.
 */
@ConfigurationProperties(prefix = "model-radar.quality")
public class QualityProperties {

    /** Accounts whose output is overwhelmingly re-uploads of other people's models. */
    private List<String> mirrorAuthors = new ArrayList<>();

    /**
     * Case-insensitive regular expressions matched against the model id. Anything
     * naming a quantization or conversion format belongs here.
     */
    private List<String> derivativePatterns = new ArrayList<>();

    public List<String> getMirrorAuthors() {
        return mirrorAuthors;
    }

    public void setMirrorAuthors(List<String> mirrorAuthors) {
        this.mirrorAuthors = mirrorAuthors;
    }

    public List<String> getDerivativePatterns() {
        return derivativePatterns;
    }

    public void setDerivativePatterns(List<String> derivativePatterns) {
        this.derivativePatterns = derivativePatterns;
    }
}
