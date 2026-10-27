package com.intics.modelradar.hf;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small stateless helpers for pulling two fields out of a raw {@link HuggingFaceModel}
 * that aren't handed to us cleanly by the API.
 */
public final class HuggingFaceMetadata {

    private static final String LICENSE_TAG_PREFIX = "license:";

    /**
     * Parameter size embedded in a model name: "7B", "13B", "1.5B", "70M".
     * Requires a b/B/m/M suffix and word boundaries so we don't match arbitrary numbers.
     */
    private static final Pattern MODEL_SIZE =
            Pattern.compile("(?<![\\w.])(\\d+(?:\\.\\d+)?)\\s?([bBmM])(?![\\w])");

    private HuggingFaceMetadata() {
    }

    /**
     * License for a model. Prefers {@code cardData.license} (which may be a string
     * or a list); falls back to a {@code license:*} entry in the {@code tags} array.
     *
     * @return the license string, or {@code null} if none can be determined
     */
    public static String extractLicense(HuggingFaceModel model) {
        if (model == null) {
            return null;
        }
        if (model.cardData != null && model.cardData.license != null) {
            String fromCard = licenseFromCardData(model.cardData.license);
            if (fromCard != null) {
                return fromCard;
            }
        }
        if (model.tags != null) {
            for (String tag : model.tags) {
                if (tag != null && tag.startsWith(LICENSE_TAG_PREFIX)) {
                    return tag.substring(LICENSE_TAG_PREFIX.length());
                }
            }
        }
        return null;
    }

    private static String licenseFromCardData(Object license) {
        if (license instanceof String s) {
            return s.isBlank() ? null : s;
        }
        if (license instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder();
            for (Object o : it) {
                if (o != null) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(o);
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        return null;
    }

    private static final int MAX_DESCRIPTION_LENGTH = 300;

    /**
     * A short description pulled from a model's raw README/card. Strips the YAML
     * front-matter and any leading headings/badges/HTML, then takes the first
     * substantive paragraph and lightly de-markdowns it.
     *
     * @return a trimmed one-liner (≤ ~300 chars), or {@code null} if the README is
     *         empty / has no usable prose
     */
    public static String shortDescriptionFromReadme(String readme) {
        if (readme == null || readme.isBlank()) {
            return null;
        }
        String body = stripFrontMatter(readme);

        StringBuilder sb = new StringBuilder();
        for (String rawLine : body.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                if (sb.length() > 0) {
                    break; // end of the first paragraph
                }
                continue;
            }
            if (isSkippable(line)) {
                continue; // headings, badges, images, HTML, comments, tables, rules
            }
            String cleaned = clean(line);
            if (cleaned.isEmpty() || !hasWord(cleaned)) {
                continue; // separator/nav lines with no real words
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(cleaned);
            if (sb.length() >= MAX_DESCRIPTION_LENGTH) {
                break;
            }
        }

        String desc = sb.toString().strip();
        if (desc.isEmpty()) {
            return null;
        }
        if (desc.length() > MAX_DESCRIPTION_LENGTH) {
            desc = desc.substring(0, MAX_DESCRIPTION_LENGTH - 1).strip() + "…";
        }
        return desc;
    }

    /** Does the line contain at least one real word character (letter/digit)? */
    private static boolean hasWord(String s) {
        return s.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private static boolean isSkippable(String line) {
        return line.startsWith("#")        // heading
                || line.startsWith("<")     // HTML / comment
                || line.startsWith("![")    // image
                || line.startsWith("[![")   // linked badge
                || line.startsWith("|")     // table row
                || line.startsWith("---")   // horizontal rule
                || line.startsWith("===");
    }

    /** Drop a leading {@code --- ... ---} YAML front-matter block, if present. */
    private static String stripFrontMatter(String readme) {
        String s = readme.stripLeading();
        if (s.startsWith("---")) {
            int close = s.indexOf("\n---", 3);
            if (close >= 0) {
                int nextLine = s.indexOf('\n', close + 1);
                return nextLine >= 0 ? s.substring(nextLine + 1) : "";
            }
        }
        return readme;
    }

    /** Strip HTML tags/entities and markdown markers, collapse whitespace. */
    private static String clean(String line) {
        return line
                .replaceAll("<!--.*?-->", " ")               // HTML comments
                .replaceAll("<[^>]+>", " ")                  // HTML tags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1") // [text](url) -> text
                .replaceAll("[*_`]", "")                      // * _ ` markers
                .replaceAll("\\s+", " ")
                .strip();
    }

    /**
     * Parameter size parsed from a model id/name, e.g. {@code "Qwen/Qwen2.5-VL-7B"} → {@code "7B"}.
     * Case-normalised to an uppercase suffix.
     *
     * @return e.g. {@code "7B"} / {@code "1.5B"} / {@code "70M"}, or {@code null} if not found
     */
    public static String extractModelSize(String modelId) {
        if (modelId == null) {
            return null;
        }
        String name = modelId.substring(modelId.indexOf('/') + 1);
        Matcher matcher = MODEL_SIZE.matcher(name);
        if (matcher.find()) {
            return matcher.group(1) + matcher.group(2).toUpperCase();
        }
        return null;
    }
}
