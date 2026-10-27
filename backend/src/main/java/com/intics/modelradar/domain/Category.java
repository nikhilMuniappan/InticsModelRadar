package com.intics.modelradar.domain;

/**
 * The INTICS use-case bucket a tracked model maps to (Section 4 of the spec).
 *
 * <p>Each model is filed under exactly one category ({@code TrackedModel.matchedCategory}).
 * Where Hugging Face tags are ambiguous (some map to several buckets — e.g.
 * {@code image-to-text} is both an OCR and a VLM signal), the catalog picks the
 * most specific bucket; see {@code TrackingCatalog}. {@link #OTHER} is the fallback
 * when a matched model can't be filed anywhere more precise.
 */
public enum Category {

    /** Vision-Language Models — reading/understanding images generally. */
    VLM("Vision-Language (VLM)"),

    /** Large Language Models — answering questions from documents/knowledge. */
    LLM("Language (LLM)"),

    /** Optical Character Recognition — extracting text from documents/scans. */
    OCR("OCR"),

    /** Detecting/analysing symbols in diagrams, drawings, P&IDs, charts. */
    DIAGRAM_ANALYSIS("Diagram Analysis"),

    /** Models trained to answer questions about a specific document/form/invoice. */
    DOCUMENT_QA("Document Q&A"),

    /** Document layout / table-structure understanding (structured extraction). */
    LAYOUT("Layout & Table Structure"),

    /** Embeddings for search/matching — CLAW-style and ColPali-style retrieval. */
    EMBEDDING("Embeddings & Retrieval"),

    /** Matched our filters but doesn't fit a more specific bucket. */
    OTHER("Other");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * A label short enough for a narrow table column — the full
     * {@link #getDisplayName()} is what tabs, charts and tooltips use.
     */
    public String getShortName() {
        return switch (this) {
            case VLM -> "VLM";
            case LLM -> "LLM";
            case OCR -> "OCR";
            case DIAGRAM_ANALYSIS -> "Diagram";
            case DOCUMENT_QA -> "Doc Q&A";
            case LAYOUT -> "Layout";
            case EMBEDDING -> "Embedding";
            case OTHER -> "Other";
        };
    }
}
