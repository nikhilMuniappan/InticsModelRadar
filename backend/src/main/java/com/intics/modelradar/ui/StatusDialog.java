package com.intics.modelradar.ui;

import com.intics.modelradar.domain.AdoptionStatus;
import com.intics.modelradar.domain.ModelAdoption;
import com.intics.modelradar.domain.TrackedModel;
import com.intics.modelradar.service.AdoptionService;
import com.intics.modelradar.service.AdoptionService.StatusUpdate;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The "Set INTICS status" form — where a model gets picked up by someone at INTICS.
 *
 * <p>Captures the stage plus who is working with it, which team, what it is for, and a
 * note about this particular change. Dates are not asked for: the service stamps
 * first-marked / status-changed / updated itself, and the note becomes a dated entry on
 * the model's timeline.
 *
 * <p>Owner and team are free text with suggestions drawn from what colleagues have
 * already typed, so the vocabulary converges without anyone maintaining a list.
 */
public class StatusDialog extends Dialog {

    private final AdoptionService adoptionService;
    private final TrackedModel model;
    private final ModelAdoption existing;
    private final Consumer<AdoptionStatus> onSaved;

    private final ComboBox<AdoptionStatus> statusField = new ComboBox<>("Status");
    private final ComboBox<String> ownerField = new ComboBox<>("Owner — who is working with it");
    private final ComboBox<String> teamField = new ComboBox<>("Team");
    private final TextField useCaseField = new TextField("Use case / INTICS product");
    private final TextArea noteField = new TextArea("Note for the timeline");

    public StatusDialog(AdoptionService adoptionService, TrackedModel model,
                        Consumer<AdoptionStatus> onSaved) {
        this.adoptionService = adoptionService;
        this.model = model;
        this.existing = adoptionService.find(model.getId()).orElse(null);
        this.onSaved = onSaved;

        setHeaderTitle(existing == null ? "Mark model for INTICS" : "Update INTICS status");
        setWidth("560px");
        setModal(true);
        setDraggable(true);
        setCloseOnEsc(true);

        add(buildBody());
        getFooter().add(buildFooter());
    }

    private VerticalLayout buildBody() {
        Span modelName = new Span(model.getId());
        modelName.getStyle().set("font-weight", "600").set("color", "var(--mr-text)")
                .set("font-size", "14px").set("word-break", "break-all");
        Span sub = new Span(model.getMatchedCategory() == null ? "Hugging Face model"
                : model.getMatchedCategory().getDisplayName()
                        + (model.getAuthor() == null ? "" : " · " + model.getAuthor()));
        sub.getStyle().set("font-size", "12.5px").set("color", "var(--mr-text-muted)");
        Div head = new Div(modelName, sub);
        head.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "3px")
                .set("padding", "12px 14px").set("border-radius", "12px")
                .set("background", "var(--mr-surface-2)").set("border", "1px solid var(--mr-border)");

        statusField.setItems(AdoptionStatus.values());
        statusField.setItemLabelGenerator(AdoptionStatus::getDisplayName);
        statusField.setRenderer(new ComponentRenderer<>(StatusDialog::statusOption));
        statusField.setValue(existing == null ? AdoptionStatus.EVALUATING : existing.getStatus());
        statusField.setRequiredIndicatorVisible(true);
        statusField.setWidthFull();
        statusField.setHelperText("\"Not reviewed\" clears the model's INTICS state (the timeline is kept).");

        configureSuggestField(ownerField, adoptionService.knownOwners(),
                existing == null ? null : existing.getOwner(), "e.g. Nikhil Muniappan");
        ownerField.setRequiredIndicatorVisible(true);
        configureSuggestField(teamField, adoptionService.knownTeams(),
                existing == null ? null : existing.getTeam(), "e.g. ADI Platform");

        useCaseField.setWidthFull();
        useCaseField.setPlaceholder("e.g. Invoice table extraction in ADI");
        useCaseField.setValue(existing == null || existing.getUseCase() == null ? "" : existing.getUseCase());

        noteField.setWidthFull();
        noteField.setHeight("92px");
        noteField.setPlaceholder("What changed and why — recorded with today's date on the timeline");

        HorizontalLayout row = new HorizontalLayout(ownerField, teamField);
        row.setWidthFull();
        row.getStyle().set("gap", "14px");

        VerticalLayout body = new VerticalLayout(head, statusField, row, useCaseField, noteField);
        body.setPadding(false);
        body.setSpacing(false);
        body.getStyle().set("gap", "16px").set("padding-top", "6px");
        body.setWidthFull();
        return body;
    }

    /** A free-text combo box that suggests values colleagues have already used. */
    private static void configureSuggestField(ComboBox<String> field, List<String> known,
                                              String current, String placeholder) {
        List<String> items = new ArrayList<>(known);
        if (current != null && !items.contains(current)) {
            items.add(current);
        }
        field.setItems(items);
        field.setAllowCustomValue(true);
        field.setClearButtonVisible(true);
        field.setPlaceholder(placeholder);
        field.setWidthFull();
        field.addCustomValueSetListener(e -> {
            String typed = e.getDetail() == null ? "" : e.getDetail().trim();
            if (typed.isEmpty()) {
                return;
            }
            if (!items.contains(typed)) {
                items.add(typed);
                field.setItems(items);
            }
            field.setValue(typed);
        });
        if (current != null) {
            field.setValue(current);
        }
    }

    private static Span statusOption(AdoptionStatus status) {
        Span dot = new Span("●");
        dot.getStyle().set("color", status.getColor()).set("margin-right", "8px");
        Span label = new Span(status.getDisplayName());
        Span wrapper = new Span(dot, label);
        wrapper.getStyle().set("display", "inline-flex").set("align-items", "center");
        return wrapper;
    }

    private HorizontalLayout buildFooter() {
        Button cancel = new Button("Cancel", e -> close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button save = new Button("Save status", e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Span lastUpdated = new Span();
        if (existing != null) {
            lastUpdated.setText("Last updated " + UiFormat.ago(existing.getUpdatedAt())
                    + (existing.getOwner() == null ? "" : " by " + existing.getOwner()));
            lastUpdated.getStyle().set("font-size", "12px").set("color", "var(--mr-text-muted)")
                    .set("margin-right", "auto");
        }

        HorizontalLayout footer = new HorizontalLayout(lastUpdated, cancel, save);
        footer.setWidthFull();
        footer.setAlignItems(FlexComponent.Alignment.CENTER);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        return footer;
    }

    private void save() {
        AdoptionStatus status = statusField.getValue();
        if (status == null) {
            statusField.setInvalid(true);
            statusField.setErrorMessage("Pick a status");
            return;
        }
        String owner = ownerField.getValue();
        if (status != AdoptionStatus.NOT_REVIEWED && (owner == null || owner.isBlank())) {
            ownerField.setInvalid(true);
            ownerField.setErrorMessage("Who is working with this model?");
            return;
        }
        ownerField.setInvalid(false);

        adoptionService.updateStatus(model.getId(), new StatusUpdate(
                status, owner, teamField.getValue(), useCaseField.getValue(), noteField.getValue()));

        Notification n = Notification.show(
                status == AdoptionStatus.NOT_REVIEWED
                        ? "INTICS status cleared for " + model.getId()
                        : model.getId() + " marked as " + status.getDisplayName(),
                3500, Notification.Position.TOP_END);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        close();
        if (onSaved != null) {
            onSaved.accept(status);
        }
    }
}
