package com.intics.modelradar.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tiny server-side SVG chart generator (no charting library / no Vaadin Charts
 * licence). Each method returns a self-contained {@code <svg>} string that the
 * dashboard drops into a card via {@code element.setProperty("innerHTML", …)}.
 *
 * <p>Theme-aware colours (text, gridlines, track, accent) are applied via inline
 * {@code style="fill:var(--mr-…)"} — SVG <em>presentation attributes</em> don't
 * resolve CSS {@code var()}, but inline styles do — so the charts follow the
 * light/dark theme automatically. Fixed series colours (category palette) stay as
 * plain presentation attributes.
 */
final class Charts {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private Charts() {
    }

    /** Donut chart with a value in the centre. {@code colors} matches {@code data} order/size. */
    static String donut(Map<String, Long> data, List<String> colors, String centre, String caption) {
        double cx = 100, cy = 100, r = 74, sw = 24;
        long total = data.values().stream().mapToLong(Long::longValue).sum();
        StringBuilder svg = new StringBuilder(512);
        svg.append("<svg viewBox='0 0 200 200' width='100%' height='100%' style='display:block'>");
        svg.append(String.format(Locale.US,
                "<circle cx='%.0f' cy='%.0f' r='%.0f' fill='none' stroke-width='%.0f' style='stroke:var(--mr-track)'/>",
                cx, cy, r, sw));

        if (total > 0) {
            double cumulative = 0;
            int i = 0;
            for (Map.Entry<String, Long> e : data.entrySet()) {
                double fraction = e.getValue() / (double) total;
                String color = colors.get(Math.min(i, colors.size() - 1));
                if (fraction > 0) {
                    svg.append(arc(cx, cy, r, cumulative * 360, (cumulative + fraction) * 360, color, sw));
                    cumulative += fraction;
                }
                i++;
            }
        }
        svg.append(String.format(Locale.US,
                "<text x='100' y='96' text-anchor='middle' font-size='30' font-weight='700' "
                        + "style='fill:var(--mr-text)'>%s</text>", esc(centre)));
        svg.append(String.format(Locale.US,
                "<text x='100' y='118' text-anchor='middle' font-size='12' font-weight='600' "
                        + "letter-spacing='0.04em' style='fill:var(--mr-text-dim)'>%s</text>",
                esc(caption.toUpperCase(Locale.ROOT))));
        svg.append("</svg>");
        return svg.toString();
    }

    private static String arc(double cx, double cy, double r, double startDeg, double endDeg,
                              String color, double sw) {
        if (endDeg - startDeg >= 359.99) {
            return String.format(Locale.US,
                    "<circle cx='%.2f' cy='%.2f' r='%.2f' fill='none' stroke='%s' stroke-width='%.0f'/>",
                    cx, cy, r, color, sw);
        }
        double[] p1 = polar(cx, cy, r, startDeg);
        double[] p2 = polar(cx, cy, r, endDeg);
        int large = (endDeg - startDeg) > 180 ? 1 : 0;
        return String.format(Locale.US,
                "<path d='M %.2f %.2f A %.2f %.2f 0 %d 1 %.2f %.2f' fill='none' stroke='%s' "
                        + "stroke-width='%.0f' stroke-linecap='butt'/>",
                p1[0], p1[1], r, r, large, p2[0], p2[1], color, sw);
    }

    private static double[] polar(double cx, double cy, double r, double angleDeg) {
        double rad = Math.toRadians(angleDeg - 90);
        return new double[]{cx + r * Math.cos(rad), cy + r * Math.sin(rad)};
    }

    /**
     * Area + line chart of a daily time series.
     *
     * <p>Points are drawn when the series is short enough for them to read as
     * individual days rather than noise, and an optional dashed reference line marks
     * the average — a handful of trustworthy days should still look deliberate.
     */
    static String areaLine(Map<LocalDate, Long> series, String accent) {
        return areaLine(series, accent, 0);
    }

    /**
     * As {@link #areaLine(Map, String)}, plus a dashed average line when
     * {@code average > 0}.
     *
     * <p>A {@code null} value means "we don't know what happened that day" — the line
     * breaks there and the day gets a muted tick on the baseline, instead of being
     * drawn as a zero. A gap in our own sampling should look like a gap, not like a
     * day nobody published anything.
     */
    static String areaLine(Map<LocalDate, Long> series, String accent, long average) {
        int w = 780, h = 230, padL = 46, padR = 18, padT = 16, padB = 34;
        int n = series.size();
        long maxValue = series.values().stream().filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue).max().orElse(0);
        if (n == 0 || maxValue == 0 && average == 0) {
            return emptyChart(w, h, "Not enough fully-synced days yet");
        }
        long max = Math.max(1, Math.max(average, maxValue));
        double plotW = w - padL - padR;
        double plotH = h - padT - padB;

        LocalDate[] days = series.keySet().toArray(LocalDate[]::new);
        Long[] vals = series.values().toArray(Long[]::new);

        // Build one line/area segment per unbroken run of known days.
        StringBuilder line = new StringBuilder();
        StringBuilder area = new StringBuilder();
        StringBuilder gaps = new StringBuilder();
        boolean inRun = false;
        double runStartX = 0;
        double prevX = 0;
        for (int i = 0; i < n; i++) {
            double x = n == 1 ? padL + plotW / 2 : padL + plotW * i / (n - 1);
            if (vals[i] == null) {
                if (inRun) {
                    area.append(String.format(Locale.US, " L %.1f %.1f L %.1f %.1f Z",
                            prevX, padT + plotH, runStartX, padT + plotH));
                    inRun = false;
                }
                gaps.append(String.format(Locale.US,
                        "<line x1='%.1f' y1='%.1f' x2='%.1f' y2='%.1f' stroke-width='1.5' "
                                + "style='stroke:var(--mr-track)'/>",
                        x, padT + plotH, x, padT + plotH - 5));
                continue;
            }
            double y = padT + plotH * (1 - vals[i] / (double) max);
            if (!inRun) {
                line.append(String.format(Locale.US, "M %.1f %.1f", x, y));
                area.append(String.format(Locale.US, "M %.1f %.1f L %.1f %.1f",
                        x, padT + plotH, x, y));
                runStartX = x;
                inRun = true;
            } else {
                line.append(String.format(Locale.US, " L %.1f %.1f", x, y));
                area.append(String.format(Locale.US, " L %.1f %.1f", x, y));
            }
            prevX = x;
        }
        if (inRun) {
            area.append(String.format(Locale.US, " L %.1f %.1f L %.1f %.1f Z",
                    prevX, padT + plotH, runStartX, padT + plotH));
        }

        StringBuilder svg = new StringBuilder(1024);
        svg.append(String.format(Locale.US,
                "<svg viewBox='0 0 %d %d' width='100%%' height='100%%' style='display:block'>", w, h));
        svg.append("<defs><linearGradient id='areaFill' x1='0' y1='0' x2='0' y2='1'>")
                .append("<stop offset='0%' style='stop-color:").append(accent).append(";stop-opacity:0.22'/>")
                .append("<stop offset='100%' style='stop-color:").append(accent).append(";stop-opacity:0.01'/>")
                .append("</linearGradient></defs>");
        for (int g = 0; g <= 2; g++) {
            double frac = g / 2.0;
            double y = padT + plotH * (1 - frac);
            long label = Math.round(max * frac);
            svg.append(String.format(Locale.US,
                    "<line x1='%.0f' y1='%.1f' x2='%d' y2='%.1f' stroke-width='1' style='stroke:var(--mr-track)'/>",
                    (double) padL, y, w - padR, y));
            svg.append(String.format(Locale.US,
                    "<text x='%d' y='%.1f' text-anchor='end' font-size='11' style='fill:var(--mr-text-dim)'>%d</text>",
                    padL - 8, y + 3, label));
        }
        svg.append(gaps);
        svg.append("<path d='").append(area).append("' fill='url(#areaFill)'/>");
        svg.append("<path d='").append(line).append("' fill='none' stroke-width='2.5' "
                + "stroke-linejoin='round' stroke-linecap='round' style='stroke:").append(accent).append("'/>");

        // Average reference line — gives a sparse series something to be read against.
        if (average > 0) {
            double avgY = padT + plotH * (1 - average / (double) max);
            svg.append(String.format(Locale.US,
                    "<line x1='%d' y1='%.1f' x2='%d' y2='%.1f' stroke-width='1.5' "
                            + "stroke-dasharray='5 4' style='stroke:%s;opacity:.55'/>",
                    padL, avgY, w - padR, avgY, accent));
            svg.append(String.format(Locale.US,
                    "<text x='%d' y='%.1f' text-anchor='end' font-size='10.5' font-weight='600' "
                            + "style='fill:var(--mr-text-muted)'>avg %d</text>",
                    w - padR, avgY - 6, average));
        }

        // Individual day markers, but only while they still read as days.
        if (n <= 31) {
            for (int i = 0; i < n; i++) {
                if (vals[i] == null) {
                    continue;
                }
                double x = n == 1 ? padL + plotW / 2 : padL + plotW * i / (n - 1);
                double y = padT + plotH * (1 - vals[i] / (double) max);
                svg.append(String.format(Locale.US,
                        "<circle cx='%.1f' cy='%.1f' r='%s' fill='%s'/>",
                        x, y, n <= 14 ? "3.6" : "2.4", accent));
            }
        }

        int[] ticks = n <= 1 ? new int[]{0} : new int[]{0, n / 2, n - 1};
        for (int t : ticks) {
            double x = n == 1 ? padL + plotW / 2 : padL + plotW * t / (n - 1);
            svg.append(String.format(Locale.US,
                    "<text x='%.1f' y='%d' text-anchor='middle' font-size='11' style='fill:var(--mr-text-dim)'>%s</text>",
                    x, h - 12, DAY.format(days[t])));
        }
        svg.append("</svg>");
        return svg.toString();
    }

    /**
     * Vertical bars, one per day, with the value above and the date below.
     *
     * <p>Used when only a handful of days are measurable: a few points stretched across
     * a 30-day axis read as a spike surrounded by nothing, which is exactly the false
     * impression the momentum chart is meant to stop giving. Bars say "here are the
     * days we can vouch for" without implying anything about the rest.
     */
    static String dayBars(Map<LocalDate, Long> series, String accent, long average) {
        int w = 780, h = 230, padL = 46, padR = 18, padT = 26, padB = 40;
        int n = series.size();
        if (n == 0) {
            return emptyChart(w, h, "Not enough fully-synced days yet");
        }
        long max = Math.max(1, Math.max(average,
                series.values().stream().mapToLong(Long::longValue).max().orElse(1)));
        double plotW = w - padL - padR;
        double plotH = h - padT - padB;
        double slot = plotW / n;
        double barW = Math.min(84, slot * 0.55);

        StringBuilder svg = new StringBuilder(1024);
        svg.append(String.format(Locale.US,
                "<svg viewBox='0 0 %d %d' width='100%%' height='100%%' style='display:block'>", w, h));

        for (int g = 0; g <= 2; g++) {
            double frac = g / 2.0;
            double y = padT + plotH * (1 - frac);
            svg.append(String.format(Locale.US,
                    "<line x1='%d' y1='%.1f' x2='%d' y2='%.1f' stroke-width='1' style='stroke:var(--mr-track)'/>",
                    padL, y, w - padR, y));
            svg.append(String.format(Locale.US,
                    "<text x='%d' y='%.1f' text-anchor='end' font-size='11' style='fill:var(--mr-text-dim)'>%d</text>",
                    padL - 8, y + 3, Math.round(max * frac)));
        }

        int i = 0;
        for (Map.Entry<LocalDate, Long> e : series.entrySet()) {
            double cx = padL + slot * (i + 0.5);
            double barH = plotH * (e.getValue() / (double) max);
            double y = padT + plotH - barH;
            svg.append(String.format(Locale.US,
                    "<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' rx='4' fill='%s' opacity='0.85'/>",
                    cx - barW / 2, y, barW, Math.max(barH, 2), accent));
            svg.append(String.format(Locale.US,
                    "<text x='%.1f' y='%.1f' text-anchor='middle' font-size='12' font-weight='700' "
                            + "style='fill:var(--mr-text)'>%d</text>", cx, y - 7, e.getValue()));
            svg.append(String.format(Locale.US,
                    "<text x='%.1f' y='%d' text-anchor='middle' font-size='11' "
                            + "style='fill:var(--mr-text-dim)'>%s</text>",
                    cx, h - 14, DAY.format(e.getKey())));
            i++;
        }

        if (average > 0 && n > 1) {
            double avgY = padT + plotH * (1 - average / (double) max);
            svg.append(String.format(Locale.US,
                    "<line x1='%d' y1='%.1f' x2='%d' y2='%.1f' stroke-width='1.5' "
                            + "stroke-dasharray='5 4' style='stroke:%s;opacity:.55'/>",
                    padL, avgY, w - padR, avgY, accent));
            svg.append(String.format(Locale.US,
                    "<text x='%d' y='%.1f' text-anchor='end' font-size='10.5' font-weight='600' "
                            + "style='fill:var(--mr-text-muted)'>avg %d</text>",
                    w - padR, avgY - 6, average));
        }
        svg.append("</svg>");
        return svg.toString();
    }

    /** Placeholder for a chart with nothing trustworthy to show yet. */
    private static String emptyChart(int w, int h, String message) {
        return String.format(Locale.US,
                "<svg viewBox='0 0 %d %d' width='100%%' height='100%%' style='display:block'>"
                        + "<text x='%d' y='%d' text-anchor='middle' font-size='13' "
                        + "style='fill:var(--mr-text-dim)'>%s</text></svg>",
                w, h, w / 2, h / 2, esc(message));
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
