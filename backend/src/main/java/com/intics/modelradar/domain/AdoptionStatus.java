package com.intics.modelradar.domain;

import java.util.Locale;

/**
 * Where a Hugging Face model stands <em>inside INTICS</em> — the internal lifecycle,
 * as opposed to anything Hugging Face tells us.
 *
 * <p>A model with no {@link ModelAdoption} row at all is treated as
 * {@link #NOT_REVIEWED}: nobody has looked at it yet. Every other value means someone
 * deliberately marked it, so a row exists and carries the owner / team / use case.
 */
public enum AdoptionStatus {

    /** Nobody has picked this up yet. The implicit state of every synced model. */
    NOT_REVIEWED("Not reviewed", "contrast", "#94a3b8"),

    /** Being tested or benchmarked to decide whether it is worth using. */
    EVALUATING("Evaluating", "primary", "#0ea5e9"),

    /** Actively being integrated into an INTICS product. */
    IN_PROGRESS("In progress", "warning", "#f59e0b"),

    /** Live in an INTICS product. */
    DEPLOYED("Deployed", "success", "#10b981"),

    /** Work paused; may resume later. */
    ON_HOLD("On hold", "contrast", "#8b5cf6"),

    /** Evaluated and deliberately not proceeding. */
    REJECTED("Rejected", "error", "#ef4444");

    private final String displayName;
    private final String badgeTheme;
    private final String color;

    AdoptionStatus(String displayName, String badgeTheme, String color) {
        this.displayName = displayName;
        this.badgeTheme = badgeTheme;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Lumo badge theme variant used by the dashboard pill. */
    public String getBadgeTheme() {
        return badgeTheme;
    }

    /** Hex colour used by the charts and the detail panel. */
    public String getColor() {
        return color;
    }

    /** True for the states that mean real work is happening or has happened. */
    public boolean isActive() {
        return this == EVALUATING || this == IN_PROGRESS || this == DEPLOYED;
    }

    /** Lenient parse for URL/query parameters; null when unrecognised or blank. */
    public static AdoptionStatus fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
