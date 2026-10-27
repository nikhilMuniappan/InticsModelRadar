package com.intics.modelradar.ui;

import com.intics.modelradar.domain.AdoptionEvent;
import com.intics.modelradar.domain.AdoptionStatus;
import com.intics.modelradar.domain.ModelAdoption;
import com.intics.modelradar.domain.TrackedModel;
import com.intics.modelradar.service.AdoptionService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Everything Model Radar knows about one model, in one panel: the Hugging Face facts on
 * the left, the INTICS working state and its dated timeline on the right.
 *
 * <p>This is the "no dead ends" surface — from here you can jump to the model on Hugging
 * Face or change its INTICS status without going back to the grid.
 */
public class ModelDetailDialog extends Dialog {

    private final AdoptionService adoptionService;
    private final TrackedModel model;
    private final Instant newSince;
    private final Consumer<AdoptionStatus> onStatusSaved;

    private final Div inticsBody = new Div();
    private final Span headerStatus = new Span();

    public ModelDetailDialog(AdoptionService adoptionService, TrackedModel model, Instant newSince,
                             Consumer<AdoptionStatus> onStatusSaved) {
        this.adoptionService = adoptionService;
        this.model = model;
        this.newSince = newSince;
        this.onStatusSaved = onStatusSaved;

        setWidth("980px");
        setMaxWidth("94vw");
        setHeight("82vh");
        setModal(true);
        setDraggable(true);
        setResizable(true);
        setCloseOnEsc(true);

        getHeader().add(buildHeader());
        add(buildBody());
        getFooter().add(buildFooter());
        refreshIntics();
    }

    // ── Header ──────────────────────────────────────────────────────────────

    private Component buildHeader() {
        Span title = new Span(model.getId());
        title.getStyle().set("font-size", "16px").set("font-weight", "700")
                .set("color", "var(--mr-text)").set("word-break", "break-all");

        HorizontalLayout badges = new HorizontalLayout(headerStatus);
        badges.setSpacing(false);
        badges.getStyle().set("gap", "8px");
        badges.setAlignItems(FlexComponent.Alignment.CENTER);
        if (model.getFirstSeenAt() != null && !model.getFirstSeenAt().isBefore(newSince)) {
            badges.add(UiFormat.newBadge());
        }
        if (model.getMatchedCategory() != null) {
            Span cat = new Span(model.getMatchedCategory().getDisplayName());
            cat.getStyle().set("font-size", "11.5px").set("font-weight", "600")
                    .set("padding", "5px 10px").set("border-radius", "999px")
                    .set("background", "var(--mr-accent-soft)").set("color", "var(--mr-accent-text)");
            badges.add(cat);
        }

        VerticalLayout col = new VerticalLayout(title, badges);
        col.setPadding(false);
        col.setSpacing(false);
        col.setMargin(false);
        col.getStyle().set("gap", "9px").set("padding-right", "12px");
        return col;
    }

    // ── Body ────────────────────────────────────────────────────────────────

    private Component buildBody() {
        Div hf = panel("Hugging Face", VaadinIcon.CLOUD_DOWNLOAD, buildHuggingFaceFacts());

        inticsBody.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "18px");
        Div intics = panel("INTICS", VaadinIcon.BUILDING, inticsBody);

        hf.getStyle().set("flex", "1").set("min-width", "0");
        intics.getStyle().set("flex", "1").set("min-width", "0");

        HorizontalLayout row = new HorizontalLayout(hf, intics);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.STRETCH);
        row.getStyle().set("gap", "18px").set("flex-wrap", "wrap");

        Div scroller = new Div(row);
        scroller.getStyle().set("overflow-y", "auto").set("height", "100%").set("padding", "4px 2px");
        return scroller;
    }

    private Component buildHuggingFaceFacts() {
        Div grid = new Div();
        grid.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "1px");
        grid.add(
                field("Author", text(model.getAuthor())),
                field("Category", text(model.getMatchedCategory() == null ? null
                        : model.getMatchedCategory().getDisplayName())),
                field("Pipeline tag", text(model.getPipelineTag())),
                field("License", text(model.getLicense())),
                field("Model size", text(model.getModelSize())),
                field("Downloads", text(UiFormat.count(model.getDownloads()))),
                field("Likes", text(UiFormat.count(model.getLikes()))),
                field("Created on HF", text(UiFormat.dateTime(model.getCreatedAt()))),
                field("Last modified", text(UiFormat.dateTime(model.getLastModified()))),
                field("First seen by us", text(UiFormat.dateTime(model.getFirstSeenAt()))),
                field("Last synced", text(UiFormat.dateTime(model.getLastFetchedAt()))),
                field("Matched via", text(model.getMatchStrategy() == null ? null
                        : model.getMatchStrategy().name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT))),
                field("Model page", hfLink()));

        Span descLabel = new Span("Description");
        styleLabel(descLabel);
        Span desc = new Span(model.getShortDescription() == null
                ? "No description available from the model card." : model.getShortDescription());
        desc.getStyle().set("font-size", "13px").set("line-height", "1.55")
                .set("color", model.getShortDescription() == null
                        ? "var(--mr-text-dim)" : "var(--mr-text)");
        Div descBox = new Div(descLabel, desc);
        descBox.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "6px")
                .set("margin-top", "14px").set("padding-top", "14px")
                .set("border-top", "1px solid var(--mr-border)");

        Div wrap = new Div(grid, descBox);
        wrap.getStyle().set("display", "flex").set("flex-direction", "column");
        return wrap;
    }

    private Component hfLink() {
        Anchor a = new Anchor(model.getModelUrl(), model.getModelUrl().replace("https://", ""));
        a.setTarget("_blank");
        a.getStyle().set("color", "var(--mr-accent-text)").set("font-size", "13px")
                .set("word-break", "break-all");
        return a;
    }

    /** Rebuilds the INTICS column — called on open and after every status save. */
    private void refreshIntics() {
        ModelAdoption adoption = adoptionService.find(model.getId()).orElse(null);
        AdoptionStatus status = adoption == null ? AdoptionStatus.NOT_REVIEWED : adoption.getStatus();

        headerStatus.removeAll();
        headerStatus.add(UiFormat.statusBadge(status));

        inticsBody.removeAll();
        if (adoption == null) {
            Span none = new Span("No one at INTICS has picked this model up yet.");
            none.getStyle().set("font-size", "13px").set("color", "var(--mr-text-muted)")
                    .set("line-height", "1.5");
            Button mark = new Button("Mark this model", VaadinIcon.BOOKMARK.create(), e -> openStatusDialog());
            mark.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            Div box = new Div(none, mark);
            box.getStyle().set("display", "flex").set("flex-direction", "column")
                    .set("align-items", "flex-start").set("gap", "12px");
            inticsBody.add(box);
        } else {
            Div grid = new Div();
            grid.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "1px");
            grid.add(
                    field("Status", UiFormat.statusBadge(status)),
                    field("Owner", text(adoption.getOwner())),
                    field("Team", text(adoption.getTeam())),
                    field("Use case", text(adoption.getUseCase())),
                    field("First marked", text(UiFormat.dateTime(adoption.getFirstMarkedAt()))),
                    field("Status changed", text(UiFormat.dateTime(adoption.getStatusChangedAt())
                            + " (" + UiFormat.ago(adoption.getStatusChangedAt()) + ")")),
                    field("Last updated", text(UiFormat.dateTime(adoption.getUpdatedAt()))));
            inticsBody.add(grid);

            if (adoption.getNotes() != null) {
                Span noteLabel = new Span("Latest note");
                styleLabel(noteLabel);
                Span note = new Span(adoption.getNotes());
                note.getStyle().set("font-size", "13px").set("line-height", "1.55")
                        .set("color", "var(--mr-text)");
                Div noteBox = new Div(noteLabel, note);
                noteBox.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "6px")
                        .set("padding", "12px 14px").set("border-radius", "10px")
                        .set("background", "var(--mr-surface-2)")
                        .set("border", "1px solid var(--mr-border)");
                inticsBody.add(noteBox);
            }
        }

        inticsBody.add(buildTimeline());
    }

    private Component buildTimeline() {
        List<AdoptionEvent> history = adoptionService.history(model.getId());
        Span label = new Span("Timeline");
        styleLabel(label);

        Div list = new Div();
        list.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "0");
        if (history.isEmpty()) {
            Span none = new Span("Nothing recorded yet.");
            none.getStyle().set("font-size", "12.5px").set("color", "var(--mr-text-dim)");
            list.add(none);
        } else {
            for (AdoptionEvent e : history) {
                list.add(timelineRow(e));
            }
        }

        Div wrap = new Div(label, list);
        wrap.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "10px")
                .set("padding-top", "14px").set("border-top", "1px solid var(--mr-border)");
        return wrap;
    }

    private Component timelineRow(AdoptionEvent e) {
        Div dot = new Div();
        dot.getStyle().set("width", "9px").set("height", "9px").set("border-radius", "50%")
                .set("background", e.getToStatus().getColor()).set("flex", "0 0 auto")
                .set("margin-top", "5px");

        String headline = e.isStatusChange()
                ? (e.getFromStatus() == null || e.getFromStatus() == AdoptionStatus.NOT_REVIEWED
                        ? "Marked as " + e.getToStatus().getDisplayName()
                        : e.getFromStatus().getDisplayName() + " → " + e.getToStatus().getDisplayName())
                : "Details updated (" + e.getToStatus().getDisplayName() + ")";
        Span head = new Span(headline);
        head.getStyle().set("font-size", "13px").set("font-weight", "600").set("color", "var(--mr-text)");

        StringBuilder meta = new StringBuilder(UiFormat.dateTime(e.getChangedAt()));
        if (e.getOwner() != null) {
            meta.append(" · ").append(e.getOwner());
        }
        if (e.getTeam() != null) {
            meta.append(" · ").append(e.getTeam());
        }
        Span metaSpan = new Span(meta.toString());
        metaSpan.getStyle().set("font-size", "11.5px").set("color", "var(--mr-text-dim)");

        Div col = new Div(head, metaSpan);
        col.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "2px")
                .set("min-width", "0");
        if (e.getNote() != null) {
            Span note = new Span(e.getNote());
            note.getStyle().set("font-size", "12.5px").set("color", "var(--mr-text-muted)")
                    .set("line-height", "1.5").set("margin-top", "3px");
            col.add(note);
        }

        Div row = new Div(dot, col);
        row.getStyle().set("display", "flex").set("gap", "11px")
                .set("padding", "9px 0").set("border-bottom", "1px solid var(--mr-border)");
        return row;
    }

    // ── Footer ──────────────────────────────────────────────────────────────

    private Component buildFooter() {
        Anchor open = new Anchor(model.getModelUrl(), "");
        open.setTarget("_blank");
        Button openButton = new Button("Open on Hugging Face", VaadinIcon.EXTERNAL_LINK.create());
        openButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        open.add(openButton);
        open.getStyle().set("text-decoration", "none").set("margin-right", "auto");

        Button setStatus = new Button("Set INTICS status", VaadinIcon.FLAG.create(), e -> openStatusDialog());
        setStatus.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button close = new Button("Close", e -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout footer = new HorizontalLayout(open, close, setStatus);
        footer.setWidthFull();
        footer.setAlignItems(FlexComponent.Alignment.CENTER);
        return footer;
    }

    private void openStatusDialog() {
        new StatusDialog(adoptionService, model, status -> {
            refreshIntics();
            if (onStatusSaved != null) {
                onStatusSaved.accept(status);
            }
        }).open();
    }

    // ── Small building blocks ───────────────────────────────────────────────

    private static Component field(String label, Component value) {
        Span l = new Span(label);
        l.getStyle().set("font-size", "12px").set("color", "var(--mr-text-muted)")
                .set("flex", "0 0 138px").set("width", "138px");
        Div v = new Div(value);
        v.getStyle().set("flex", "1").set("min-width", "0").set("font-size", "13px")
                .set("color", "var(--mr-text)");
        Div row = new Div(l, v);
        row.getStyle().set("display", "flex").set("gap", "10px").set("align-items", "baseline")
                .set("padding", "7px 0").set("border-bottom", "1px solid var(--mr-border)");
        return row;
    }

    private static Span text(String value) {
        Span s = new Span(value == null || value.isBlank() ? "—" : value);
        if (value == null || value.isBlank()) {
            s.getStyle().set("color", "var(--mr-text-dim)");
        }
        s.getStyle().set("word-break", "break-word");
        return s;
    }

    private static void styleLabel(Span s) {
        s.getStyle().set("font-size", "11.5px").set("font-weight", "600")
                .set("letter-spacing", "0.05em").set("text-transform", "uppercase")
                .set("color", "var(--mr-text-muted)");
    }

    private Div panel(String title, VaadinIcon iconName, Component content) {
        com.vaadin.flow.component.icon.Icon icon = iconName.create();
        icon.setSize("14px");
        icon.getStyle().set("color", "var(--mr-accent)");
        Span t = new Span(title);
        t.getStyle().set("font-size", "12px").set("font-weight", "700")
                .set("letter-spacing", "0.06em").set("text-transform", "uppercase")
                .set("color", "var(--mr-text-muted)");
        HorizontalLayout head = new HorizontalLayout(icon, t);
        head.setAlignItems(FlexComponent.Alignment.CENTER);
        head.setSpacing(false);
        head.getStyle().set("gap", "8px").set("margin-bottom", "14px");

        Div box = new Div(head, content);
        box.getStyle().set("background", "var(--mr-surface)")
                .set("border", "1px solid var(--mr-border)")
                .set("border-radius", "16px").set("padding", "18px 20px")
                .set("display", "flex").set("flex-direction", "column");
        return box;
    }
}
