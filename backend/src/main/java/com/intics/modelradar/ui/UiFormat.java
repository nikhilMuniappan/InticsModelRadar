package com.intics.modelradar.ui;

import com.intics.modelradar.domain.AdoptionStatus;
import com.vaadin.flow.component.html.Span;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared display formatting for the dashboard — one place so a date or a count looks
 * the same in the grid, the detail panel and the dialogs.
 */
final class UiFormat {

    static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private UiFormat() {
    }

    /** Thousands-separated count, e.g. {@code 3,049}. */
    static String count(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    /** Short date, or an em dash when absent. */
    static String date(Instant instant) {
        return instant == null ? "—" : DATE.format(instant);
    }

    /** Full date + time in UTC, for tooltips and the detail panel. */
    static String dateTime(Instant instant) {
        return instant == null ? "not available" : DATE_TIME.format(instant);
    }

    /** Relative age, e.g. {@code 3 hours ago}. */
    static String ago(Instant then) {
        if (then == null) {
            return "never";
        }
        long secs = Duration.between(then, Instant.now()).getSeconds();
        if (secs < 60) {
            return "just now";
        }
        long mins = secs / 60;
        if (mins < 60) {
            return mins + " min ago";
        }
        long hours = mins / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }

    /**
     * The INTICS status pill. Each stage keeps its colour everywhere it appears, so the
     * grid, the charts and the detail panel all read as one thing.
     */
    static Span statusBadge(AdoptionStatus status) {
        Span pill = new Span(status.getDisplayName());
        pill.getStyle()
                .set("display", "inline-flex").set("align-items", "center")
                .set("font-size", "11.5px").set("font-weight", "600").set("line-height", "1")
                .set("padding", "5px 10px").set("border-radius", "999px")
                .set("white-space", "nowrap")
                .set("color", status.getColor())
                .set("background", "color-mix(in srgb, " + status.getColor() + " 14%, transparent)")
                .set("box-shadow", "inset 0 0 0 1px color-mix(in srgb, "
                        + status.getColor() + " 32%, transparent)");
        if (status == AdoptionStatus.NOT_REVIEWED) {
            pill.getStyle().set("font-weight", "500").set("opacity", "0.75");
        }
        return pill;
    }

    /** The green "NEW" pill used for models first seen inside the new-window. */
    static Span newBadge() {
        Span pill = new Span("NEW");
        pill.getElement().getThemeList().add("badge success primary pill small");
        return pill;
    }
}
