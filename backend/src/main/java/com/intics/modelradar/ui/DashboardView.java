package com.intics.modelradar.ui;

import com.intics.modelradar.domain.AdoptionEvent;
import com.intics.modelradar.domain.AdoptionStatus;
import com.intics.modelradar.domain.Category;
import com.intics.modelradar.domain.ModelAdoption;
import com.intics.modelradar.domain.SyncRun;
import com.intics.modelradar.domain.SyncTrigger;
import com.intics.modelradar.domain.TrackedModel;
import com.intics.modelradar.service.AdoptionService;
import com.intics.modelradar.service.ModelQueryService;
import com.intics.modelradar.service.ModelQueryService.SortOption;
import com.intics.modelradar.service.ModelSyncService;
import com.intics.modelradar.service.SyncRunService;
import com.intics.modelradar.service.TranslationService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The Model Radar dashboard (Section 6).
 *
 * <p>Layout follows what people actually come here to do: find models and act on them.
 * The header, category tabs and filter bar stay put; the analytics panel is collapsed by
 * default (and remembers its state per browser) so the model list is the first thing on
 * screen rather than the last; the grid takes all remaining height and scrolls on its own.
 *
 * <p>Each row carries the model's INTICS status alongside the Hugging Face facts, and can
 * be opened ({@link ModelDetailDialog}) or marked ({@link StatusDialog}) from the row
 * itself. All reads go through {@link ModelQueryService} and {@link AdoptionService}
 * against our own database.
 */
@Route("")
@PageTitle("Model Radar")
public class DashboardView extends VerticalLayout implements BeforeEnterObserver {

    private static final int PAGE_SIZE = 50;
    private static final int ACTIVITY_LIMIT = 8;
    /** Below this many measurable days, the momentum chart draws bars instead of a trend line. */
    private static final int MIN_DAYS_FOR_TREND = 10;

    /**
     * Theme palette (as CSS custom properties) + finishing touches, injected once.
     * Everything keys off the Lumo {@code theme="dark"} attribute on &lt;html&gt;, so the
     * light/dark toggle just flips that one attribute and the whole UI (Lumo components
     * + our custom cards + the SVG charts, which read the same vars) follows. The vars
     * live on {@code :root} so overlays — dialogs, tooltips — pick them up too.
     */
    private static final String GLOBAL_CSS = """
            :root {
              --mr-bg:#eef0f5; --mr-surface:#ffffff; --mr-surface-2:#f8f9fc; --mr-border:#ecedf3;
              --mr-text:#101828; --mr-text-muted:#667085; --mr-text-dim:#98a2b3;
              --mr-track:#eceef4; --mr-pos:#059669;
              --mr-accent:#4f46e5; --mr-accent-text:#4338ca; --mr-accent-soft:#eef0fe;
              --mr-shadow: 0 1px 2px rgba(16,24,40,.04), 0 6px 16px -6px rgba(16,24,40,.08);
              --mr-row-hover:#f6f7ff;
              --lumo-primary-color:#4f46e5; --lumo-primary-text-color:#4338ca;
              --lumo-primary-color-50pct:rgba(79,70,229,.5); --lumo-primary-color-10pct:rgba(79,70,229,.12);
              --lumo-border-radius-m:10px; --lumo-border-radius-l:16px;
              --lumo-font-family: Inter, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            }
            html[theme~='dark'] {
              --mr-bg:#0a0d13; --mr-surface:#141922; --mr-surface-2:#1a2029; --mr-border:#262d3a;
              --mr-text:#e8ebf2; --mr-text-muted:#96a1b4; --mr-text-dim:#6b7688;
              --mr-track:#252c3a; --mr-pos:#34d399;
              --mr-accent:#8b83ff; --mr-accent-text:#aca6ff; --mr-accent-soft:#20233a;
              --mr-shadow: 0 1px 2px rgba(0,0,0,.4), 0 12px 28px -12px rgba(0,0,0,.65);
              --mr-row-hover:rgba(139,131,255,.07);
              --lumo-primary-color:#8b83ff; --lumo-primary-text-color:#aca6ff;
              --lumo-primary-color-50pct:rgba(139,131,255,.5); --lumo-primary-color-10pct:rgba(139,131,255,.16);
            }
            html, body { background: var(--mr-bg); }
            * { -webkit-font-smoothing: antialiased; -moz-osx-font-smoothing: grayscale; }
            ::-webkit-scrollbar { width: 11px; height: 11px; }
            ::-webkit-scrollbar-thumb { background: var(--mr-border); border-radius: 8px; border: 2px solid var(--mr-bg); }
            ::-webkit-scrollbar-thumb:hover { background: var(--mr-text-dim); }
            /* Truncate long cell values; full text (and translation) lives in the tooltip. */
            vaadin-grid-cell-content { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-size: 13.5px; }
            vaadin-grid, vaadin-grid::part(row) { background: transparent; }
            vaadin-grid::part(header-cell) { background: var(--mr-surface-2); border-bottom: 1px solid var(--mr-border); }
            vaadin-grid::part(row):hover > [part~='cell'] { background: var(--mr-row-hover); }
            vaadin-tooltip-overlay::part(overlay) { max-width: 480px; line-height: 1.45; }
            vaadin-dialog-overlay::part(overlay) { background: var(--mr-surface); border-radius: 18px; }
            vaadin-dialog-overlay::part(header), vaadin-dialog-overlay::part(footer) {
              background: var(--mr-surface); border-color: var(--mr-border);
            }
            /* Row action buttons: quiet until the row is hovered, so the data stays the focus. */
            .mr-row-actions vaadin-button { opacity: .45; transition: opacity .12s ease; }
            vaadin-grid::part(row):hover .mr-row-actions vaadin-button { opacity: 1; }
            """;

    private final ModelQueryService queryService;
    private final SyncRunService syncRunService;
    private final TranslationService translationService;
    private final AdoptionService adoptionService;

    // Header
    private final Span lastSynced = new Span();
    private final ProgressBar progressBar = new ProgressBar();
    private final Button refreshButton = new Button("Refresh now", VaadinIcon.REFRESH.create());

    // Tabs (one per category + "All")
    private final Tabs tabs = new Tabs();
    private final Map<Tab, TabInfo> tabInfo = new LinkedHashMap<>();

    // Analytics — collapsible panel
    private final VerticalLayout analyticsBody = new VerticalLayout();
    private final Button analyticsToggle = new Button("Show");
    private final Span analyticsSummary = new Span();

    // Analytics — KPI tiles
    private final Span kpiTotal = new Span();
    private final Span kpiWeek = new Span();
    private final Span kpiDay = new Span();
    private final Span kpiInProgress = new Span();
    private final Span kpiDeployed = new Span();
    private final Anchor kpiTopLink = new Anchor();
    private final Span kpiTopSub = new Span();

    // Analytics — chart hosts (SVG via innerHTML) + bar containers
    private final Div momentumHost = new Div();
    private final Div donutHost = new Div();
    private final Div donutLegend = new Div();
    private final Div adoptionBars = new Div();
    private final Div newByCatBars = new Div();
    private final Div licenseBars = new Div();
    private final Div orgBars = new Div();
    private final Div activityList = new Div();
    private final Span momentumCaption = new Span();
    private final Span orgCaption = new Span();

    // Filters
    private final ComboBox<SortOption> sortField = new ComboBox<>();
    private final ComboBox<AdoptionStatus> statusFilter = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final Checkbox newOnlyFilter = new Checkbox("New only (7 days)");
    private final Checkbox originalsOnlyFilter = new Checkbox("Originals only");
    private final Button clearFilters = new Button("Clear filters", VaadinIcon.CLOSE_SMALL.create());
    private final Span resultCount = new Span();

    private final Grid<TrackedModel> grid = new Grid<>(TrackedModel.class, false);
    private final DataProvider<TrackedModel, Void> dataProvider;

    /**
     * INTICS state for the rows currently on screen. Filled once per fetched page so the
     * status/owner cells don't each hit the database.
     */
    private final Map<String, ModelAdoption> adoptionCache = new HashMap<>();

    private boolean applyingUrlState = false;

    public DashboardView(ModelQueryService queryService, SyncRunService syncRunService,
                         TranslationService translationService, AdoptionService adoptionService) {
        this.queryService = queryService;
        this.syncRunService = syncRunService;
        this.translationService = translationService;
        this.adoptionService = adoptionService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("padding", "22px 28px").set("gap", "14px")
                .set("background", "var(--mr-bg)").set("overflow-y", "auto");

        this.dataProvider = DataProvider.fromCallbacks(
                q -> {
                    List<TrackedModel> page = queryService.queryPage(selectedCategory(), newSinceDays(),
                            sortValue(), searchField.getValue(), statusFilter.getValue(),
                            originalsOnly(), (int) q.getOffset(), q.getLimit());
                    cacheAdoptions(page);
                    return page.stream();
                },
                q -> queryService.countModels(selectedCategory(), newSinceDays(),
                        searchField.getValue(), statusFilter.getValue(), originalsOnly()));

        add(buildHeader(), buildTabs(), buildAnalyticsSection(), buildFilterBar(), buildGridCard());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        // Inject the global stylesheet once, then apply the saved theme (defaults to dark).
        getElement().executeJs(
                "if(!document.getElementById('mr-styles')){var s=document.createElement('style');"
                        + "s.id='mr-styles';s.textContent=$0;document.head.appendChild(s);}"
                        + "var t=localStorage.getItem('mr-theme')||'dark';"
                        + "document.documentElement.setAttribute('theme', t==='light'?'':'dark');", GLOBAL_CSS);
        // Restore whether the analytics panel was left open on this browser.
        getElement().executeJs("return localStorage.getItem('mr-analytics');")
                .then(String.class, saved -> setAnalyticsExpanded("open".equals(saved)));
    }

    // ── Header ──────────────────────────────────────────────────────────────

    private Component buildHeader() {
        Span logo = new Span("📡");
        logo.getStyle().set("font-size", "20px").set("line-height", "1")
                .set("display", "inline-flex").set("align-items", "center").set("justify-content", "center")
                .set("width", "42px").set("height", "42px").set("border-radius", "12px")
                .set("background", "var(--mr-accent-soft)")
                .set("box-shadow", "inset 0 0 0 1px var(--mr-border)");

        Span title = new Span("Model Radar");
        title.getStyle().set("font-size", "21px").set("font-weight", "700")
                .set("letter-spacing", "-0.02em").set("color", "var(--mr-text)");
        Span subtitle = new Span("Hugging Face releases, tracked for INTICS");
        subtitle.getStyle().set("font-size", "12.5px").set("color", "var(--mr-text-muted)");
        VerticalLayout titleCol = tightColumn(title, subtitle);

        HorizontalLayout brand = new HorizontalLayout(logo, titleCol);
        brand.setAlignItems(FlexComponent.Alignment.CENTER);
        brand.setSpacing(true);

        lastSynced.getStyle().set("font-size", "12.5px").set("color", "var(--mr-text-muted)")
                .set("white-space", "nowrap");

        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setWidth("120px");

        Button themeToggle = new Button(VaadinIcon.ADJUST.create());
        themeToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        themeToggle.getElement().setAttribute("title", "Toggle light / dark");
        themeToggle.addClickListener(e -> themeToggle.getElement().executeJs(
                "var h=document.documentElement; var dark=!(h.getAttribute('theme')||'').includes('dark');"
                        + "h.setAttribute('theme', dark?'dark':''); "
                        + "localStorage.setItem('mr-theme', dark?'dark':'light');"));

        refreshButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        refreshButton.getElement().setAttribute("title", "Fetch the latest models from Hugging Face now");
        refreshButton.addClickListener(e -> runSync());

        HorizontalLayout right = new HorizontalLayout(lastSynced, progressBar, themeToggle, refreshButton);
        right.setAlignItems(FlexComponent.Alignment.CENTER);
        right.setSpacing(true);

        HorizontalLayout bar = new HorizontalLayout(brand, right);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Div card = card(bar);
        card.getStyle().set("padding", "14px 20px").set("flex", "0 0 auto");
        card.setWidthFull();
        return card;
    }

    // ── Tabs (per category) ─────────────────────────────────────────────────

    private Component buildTabs() {
        addTab("All", null);
        for (Category c : Category.values()) {
            addTab(c.getDisplayName(), c);
        }
        tabs.addSelectedChangeListener(e -> onTabChanged());
        tabs.setWidthFull();

        Div card = card(tabs);
        card.getStyle().set("padding", "0 12px")
                .set("display", "flex").set("align-items", "center")
                .set("min-height", "52px").set("flex", "0 0 auto")
                .set("overflow-x", "auto");
        card.setWidthFull();
        return card;
    }

    private void addTab(String label, Category category) {
        Span name = new Span(label);
        name.getStyle().set("font-weight", "600").set("color", "var(--mr-text)");

        Span total = new Span("0");
        total.getStyle().set("color", "var(--mr-text-dim)").set("font-variant-numeric", "tabular-nums")
                .set("font-size", "13px");

        Span newBadge = new Span();
        newBadge.getElement().getThemeList().add("badge success small pill");
        newBadge.setVisible(false);

        HorizontalLayout content = new HorizontalLayout(name, total, newBadge);
        content.setAlignItems(FlexComponent.Alignment.CENTER);
        content.setSpacing(false);
        content.getStyle().set("gap", "8px");

        Tab tab = new Tab(content);
        tabs.add(tab);
        tabInfo.put(tab, new TabInfo(category, total, newBadge));
    }

    private void updateTabCounts() {
        Map<Category, Long> totals = queryService.countsByCategory();
        Map<Category, Long> news = queryService.newCountsByCategory();
        long totalAll = totals.values().stream().mapToLong(Long::longValue).sum();
        long newAll = news.values().stream().mapToLong(Long::longValue).sum();

        for (TabInfo ti : tabInfo.values()) {
            long total = ti.category == null ? totalAll : totals.getOrDefault(ti.category, 0L);
            long fresh = ti.category == null ? newAll : news.getOrDefault(ti.category, 0L);
            ti.total.setText(UiFormat.count(total));
            if (fresh > 0) {
                ti.newBadge.setText(UiFormat.count(fresh) + " new");
                ti.newBadge.setVisible(true);
            } else {
                ti.newBadge.setVisible(false);
            }
        }
    }

    // ── Analytics (collapsible) ─────────────────────────────────────────────

    /**
     * The analytics panel plus the bar that opens it. Collapsed by default: the charts
     * are context, the model list is the job, so the list stays above the fold.
     */
    private Component buildAnalyticsSection() {
        Icon icon = VaadinIcon.CHART.create();
        icon.setSize("15px");
        icon.getStyle().set("color", "var(--mr-accent)");
        Div chip = new Div(icon);
        chip.getStyle().set("width", "28px").set("height", "28px").set("border-radius", "8px")
                .set("display", "inline-flex").set("align-items", "center").set("justify-content", "center")
                .set("background", "var(--mr-accent-soft)").set("flex", "0 0 auto");

        Span title = new Span("Analytics & insights");
        title.getStyle().set("font-size", "13.5px").set("font-weight", "700").set("color", "var(--mr-text)");
        analyticsSummary.getStyle().set("font-size", "12.5px").set("color", "var(--mr-text-muted)")
                .set("white-space", "nowrap").set("overflow", "hidden").set("text-overflow", "ellipsis");

        analyticsToggle.setIcon(VaadinIcon.CHEVRON_DOWN.create());
        analyticsToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        // No listener of its own: the click bubbles to the card below, which owns the
        // toggle. Two listeners would fire for one click and cancel each other out.

        HorizontalLayout left = new HorizontalLayout(chip, title, analyticsSummary);
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        left.setSpacing(false);
        left.getStyle().set("gap", "11px").set("min-width", "0").set("flex", "1");

        HorizontalLayout bar = new HorizontalLayout(left, analyticsToggle);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Div toggleCard = card(bar);
        toggleCard.getStyle().set("padding", "10px 16px").set("cursor", "pointer").set("flex", "0 0 auto");
        toggleCard.setWidthFull();
        toggleCard.addClickListener(e -> toggleAnalytics());

        analyticsBody.setPadding(false);
        analyticsBody.setSpacing(false);
        analyticsBody.setWidthFull();
        analyticsBody.getStyle().set("gap", "16px").set("flex", "0 0 auto");
        analyticsBody.add(buildKpiStrip(), buildCharts(), buildActivityCard());
        analyticsBody.setVisible(false);

        VerticalLayout section = new VerticalLayout(toggleCard, analyticsBody);
        section.setPadding(false);
        section.setSpacing(false);
        section.setWidthFull();
        section.getStyle().set("gap", "14px").set("flex", "0 0 auto");
        return section;
    }

    private void toggleAnalytics() {
        setAnalyticsExpanded(!analyticsBody.isVisible());
        getElement().executeJs("localStorage.setItem('mr-analytics', $0);",
                analyticsBody.isVisible() ? "open" : "closed");
    }

    private void setAnalyticsExpanded(boolean expanded) {
        analyticsBody.setVisible(expanded);
        analyticsToggle.setText(expanded ? "Hide" : "Show");
        analyticsToggle.setIcon(expanded ? VaadinIcon.CHEVRON_UP.create() : VaadinIcon.CHEVRON_DOWN.create());
        if (expanded) {
            updateAnalytics();
        }
    }

    private Component buildKpiStrip() {
        styleKpiNumber(kpiTotal, "var(--mr-text)");
        styleKpiNumber(kpiWeek, "var(--mr-pos)");
        styleKpiNumber(kpiDay, "var(--mr-accent-text)");
        styleKpiNumber(kpiInProgress, AdoptionStatus.IN_PROGRESS.getColor());
        styleKpiNumber(kpiDeployed, AdoptionStatus.DEPLOYED.getColor());
        kpiTopLink.setTarget("_blank");
        kpiTopLink.getStyle().set("font-weight", "700").set("font-size", "16px").set("color", "var(--mr-accent-text)")
                .set("text-decoration", "none").set("white-space", "nowrap").set("overflow", "hidden")
                .set("text-overflow", "ellipsis").set("display", "block").set("max-width", "230px");
        kpiTopSub.getStyle().set("font-size", "12.5px").set("color", "var(--mr-text-muted)");

        Div inProgressTile = kpiTile("In progress", VaadinIcon.COG, kpiInProgress);
        Div deployedTile = kpiTile("Deployed", VaadinIcon.CHECK_CIRCLE, kpiDeployed);
        // The INTICS tiles double as shortcuts into the matching filter.
        makeFilterShortcut(inProgressTile, AdoptionStatus.IN_PROGRESS);
        makeFilterShortcut(deployedTile, AdoptionStatus.DEPLOYED);

        HorizontalLayout row = new HorizontalLayout(
                kpiTile("Total tracked", VaadinIcon.DATABASE, kpiTotal),
                kpiTile("New this week", VaadinIcon.STAR, kpiWeek),
                kpiTile("New (24 h)", VaadinIcon.CLOCK, kpiDay),
                inProgressTile,
                deployedTile,
                kpiTile("Most downloaded", VaadinIcon.DOWNLOAD, kpiTopLink, kpiTopSub));
        row.setWidthFull();
        row.getStyle().set("gap", "16px").set("flex-wrap", "wrap");
        return row;
    }

    private void makeFilterShortcut(Div tile, AdoptionStatus status) {
        tile.getStyle().set("cursor", "pointer");
        tile.getElement().setAttribute("title", "Show only models that are " + status.getDisplayName());
        tile.addClickListener(e -> {
            statusFilter.setValue(status);
            selectTabFor(null);
        });
    }

    private static void styleKpiNumber(Span s, String color) {
        s.getStyle().set("font-size", "26px").set("font-weight", "700").set("color", color);
    }

    private Div kpiTile(String label, VaadinIcon iconName, Component... value) {
        Icon icon = iconName.create();
        icon.setSize("15px");
        icon.getStyle().set("color", "var(--mr-accent)");
        Div chip = new Div(icon);
        chip.getStyle().set("width", "28px").set("height", "28px").set("border-radius", "8px")
                .set("display", "inline-flex").set("align-items", "center").set("justify-content", "center")
                .set("background", "var(--mr-accent-soft)").set("flex", "0 0 auto");
        Span lbl = new Span(label);
        lbl.getStyle().set("font-size", "11px").set("font-weight", "600")
                .set("letter-spacing", "0.05em").set("text-transform", "uppercase").set("color", "var(--mr-text-muted)");
        HorizontalLayout head = new HorizontalLayout(chip, lbl);
        head.setAlignItems(FlexComponent.Alignment.CENTER);
        head.setSpacing(false);
        head.getStyle().set("gap", "9px");

        VerticalLayout col = new VerticalLayout();
        col.setPadding(false);
        col.setSpacing(false);
        col.setMargin(false);
        col.getStyle().set("gap", "10px");
        col.add(head);
        for (Component v : value) {
            col.add(v);
        }
        Div c = card(col);
        c.getStyle().set("padding", "15px 18px").set("flex", "1").set("min-width", "182px");
        return c;
    }

    private Component buildCharts() {
        momentumHost.setWidthFull();
        momentumHost.getStyle().set("height", "210px");
        donutHost.getStyle().set("width", "180px").set("height", "180px").set("flex", "0 0 auto");
        donutLegend.getStyle().set("flex", "1").set("min-width", "0");
        HorizontalLayout donutRow = new HorizontalLayout(donutHost, donutLegend);
        donutRow.setWidthFull();
        donutRow.setAlignItems(FlexComponent.Alignment.CENTER);
        donutRow.getStyle().set("gap", "18px");

        for (Div box : List.of(adoptionBars, newByCatBars, licenseBars, orgBars)) {
            styleBarBox(box);
        }

        momentumCaption.getStyle().set("font-size", "11.5px").set("color", "var(--mr-text-dim)")
                .set("line-height", "1.5").set("display", "block");
        VerticalLayout momentumBox = new VerticalLayout(momentumHost, momentumCaption);
        momentumBox.setPadding(false);
        momentumBox.setSpacing(false);
        momentumBox.setWidthFull();
        momentumBox.getStyle().set("gap", "10px");
        Div momentumCard = chartCard("Release momentum",
                "original models per day · fully-synced days only", momentumBox);
        Div landscapeCard = chartCard("Model landscape", "by INTICS category", donutRow);
        momentumCard.getStyle().set("flex", "1.7").set("min-width", "320px");
        landscapeCard.getStyle().set("flex", "1").set("min-width", "300px");
        HorizontalLayout rowA = new HorizontalLayout(momentumCard, landscapeCard);
        rowA.setWidthFull();
        rowA.getStyle().set("gap", "16px").set("flex-wrap", "wrap");

        Div adoptionCard = chartCard("INTICS adoption", "models by internal status", adoptionBars);
        Div newCatCard = chartCard("New this week", "by category", newByCatBars);
        Div licenseCard = chartCard("Licenses", "usable vs restricted", licenseBars);
        orgCaption.getStyle().set("font-size", "11.5px").set("color", "var(--mr-text-dim)")
                .set("line-height", "1.5").set("display", "block");
        VerticalLayout orgBox = new VerticalLayout(orgBars, orgCaption);
        orgBox.setPadding(false);
        orgBox.setSpacing(false);
        orgBox.setWidthFull();
        orgBox.getStyle().set("gap", "10px");
        Div orgCard = chartCard("Top organisations", "by original releases", orgBox);
        HorizontalLayout rowB = new HorizontalLayout(adoptionCard, newCatCard, licenseCard, orgCard);
        rowB.setWidthFull();
        rowB.getStyle().set("gap", "16px").set("flex-wrap", "wrap");
        for (Div d : List.of(adoptionCard, newCatCard, licenseCard, orgCard)) {
            d.getStyle().set("flex", "1").set("min-width", "240px");
        }

        VerticalLayout wrap = new VerticalLayout(rowA, rowB);
        wrap.setPadding(false);
        wrap.setSpacing(false);
        wrap.setWidthFull();
        wrap.getStyle().set("gap", "16px");
        return wrap;
    }

    /** The company-wide feed of who marked what, so activity is visible without hunting. */
    private Component buildActivityCard() {
        activityList.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "0");
        Div c = chartCard("Recent INTICS activity", "latest status changes across all models", activityList);
        c.setWidthFull();
        return c;
    }

    private void updateActivity() {
        activityList.removeAll();
        List<AdoptionEvent> events = adoptionService.recentActivity(ACTIVITY_LIMIT);
        if (events.isEmpty()) {
            Span none = new Span("No models have been marked yet — use \"Set status\" on any row to start.");
            none.getStyle().set("font-size", "13px").set("color", "var(--mr-text-dim)");
            activityList.add(none);
            return;
        }
        for (AdoptionEvent e : events) {
            activityList.add(activityRow(e));
        }
    }

    private Component activityRow(AdoptionEvent e) {
        Div dot = new Div();
        dot.getStyle().set("width", "8px").set("height", "8px").set("border-radius", "50%")
                .set("background", e.getToStatus().getColor()).set("flex", "0 0 auto");

        Span modelId = new Span(e.getModelId());
        modelId.getElement().setAttribute("title", "Open " + e.getModelId());
        modelId.getStyle().set("font-size", "13px").set("font-weight", "600")
                .set("color", "var(--mr-accent-text)").set("cursor", "pointer")
                .set("white-space", "nowrap").set("overflow", "hidden").set("text-overflow", "ellipsis")
                .set("flex", "1").set("min-width", "0");
        modelId.getElement().addEventListener("click",
                ev -> queryService.findById(e.getModelId()).ifPresent(this::openDetail));

        Span change = new Span(e.isStatusChange()
                ? (e.getFromStatus() == null || e.getFromStatus() == AdoptionStatus.NOT_REVIEWED
                        ? "marked " + e.getToStatus().getDisplayName()
                        : e.getFromStatus().getDisplayName() + " → " + e.getToStatus().getDisplayName())
                : "details updated");
        change.getStyle().set("font-size", "12.5px").set("color", "var(--mr-text-muted)")
                .set("white-space", "nowrap");

        Span who = new Span(e.getOwner() == null ? UiFormat.ago(e.getChangedAt())
                : e.getOwner() + " · " + UiFormat.ago(e.getChangedAt()));
        who.getStyle().set("font-size", "12px").set("color", "var(--mr-text-dim)")
                .set("white-space", "nowrap").set("flex", "0 0 auto");

        HorizontalLayout row = new HorizontalLayout(dot, modelId, change, who);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setWidthFull();
        row.setSpacing(false);
        row.getStyle().set("gap", "10px").set("padding", "8px 0")
                .set("border-bottom", "1px solid var(--mr-border)");
        return row;
    }

    private Div chartCard(String title, String subtitle, Component content) {
        Span t = new Span(title);
        t.getStyle().set("font-size", "14px").set("font-weight", "700").set("color", "var(--mr-text)");
        Span s = new Span(subtitle);
        s.getStyle().set("font-size", "12px").set("color", "var(--mr-text-dim)");
        VerticalLayout head = new VerticalLayout(t, s);
        head.setPadding(false);
        head.setSpacing(false);
        head.setMargin(false);
        head.getStyle().set("gap", "1px");
        VerticalLayout col = new VerticalLayout(head, content);
        col.setPadding(false);
        col.setSpacing(false);
        col.setMargin(false);
        col.setWidthFull();
        col.getStyle().set("gap", "15px");
        Div c = card(col);
        c.getStyle().set("padding", "18px 20px");
        c.setWidthFull();
        return c;
    }

    private static void styleBarBox(Div box) {
        box.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "11px").set("width", "100%");
    }

    /**
     * Refreshes every number on the dashboard. The cheap counts (KPI tiles + the summary
     * line on the collapsed bar) always run; the charts only when the panel is open.
     */
    private void updateAnalytics() {
        long total = queryService.countModels(null, null, null, null, false);
        long week = queryService.countModels(null, queryService.newWindowDays(), null, null, false);
        Map<AdoptionStatus, Long> statusCounts = adoptionService.countsByStatus();
        long inProgress = statusCounts.getOrDefault(AdoptionStatus.IN_PROGRESS, 0L);
        long deployed = statusCounts.getOrDefault(AdoptionStatus.DEPLOYED, 0L);

        kpiTotal.setText(UiFormat.count(total));
        kpiWeek.setText(UiFormat.count(week));
        kpiDay.setText(UiFormat.count(queryService.countModels(null, 1, null, null, false)));
        kpiInProgress.setText(UiFormat.count(inProgress));
        kpiDeployed.setText(UiFormat.count(deployed));

        long marked = adoptionService.markedCount();
        analyticsSummary.setText("%s tracked · %s new this week · %s in progress · %s deployed · %s marked"
                .formatted(UiFormat.count(total), UiFormat.count(week), UiFormat.count(inProgress),
                        UiFormat.count(deployed), UiFormat.count(marked)));

        if (!analyticsBody.isVisible()) {
            return;
        }

        List<TrackedModel> top = queryService.queryPage(null, null, SortOption.DOWNLOADS, null, null, false, 0, 1);
        if (top.isEmpty()) {
            kpiTopLink.setText("—");
            kpiTopLink.setHref("");
            kpiTopSub.setText("no models");
        } else {
            TrackedModel m = top.get(0);
            kpiTopLink.setText(m.getId());
            kpiTopLink.setHref(m.getModelUrl());
            kpiTopLink.getElement().setAttribute("title", m.getId());
            kpiTopSub.setText(UiFormat.count(m.getDownloads()) + " downloads");
        }

        renderMomentum();

        Map<Category, Long> cats = queryService.countsByCategory();
        LinkedHashMap<String, Long> donutData = new LinkedHashMap<>();
        List<String> donutColors = new ArrayList<>();
        donutLegend.removeAll();
        for (Category c : Category.values()) {
            long v = cats.getOrDefault(c, 0L);
            if (v <= 0) {
                continue;
            }
            donutData.put(c.getDisplayName(), v);
            donutColors.add(categoryColor(c));
            donutLegend.add(legendRow(categoryColor(c), c.getDisplayName(), v));
        }
        setSvg(donutHost, Charts.donut(donutData, donutColors, UiFormat.count(total), "models"));

        // Adoption: every stage except the "not reviewed" remainder, which would dwarf the rest.
        LinkedHashMap<String, Long> adoptionData = new LinkedHashMap<>();
        Map<String, String> adoptionColors = new HashMap<>();
        for (AdoptionStatus s : AdoptionStatus.values()) {
            if (s == AdoptionStatus.NOT_REVIEWED) {
                continue;
            }
            adoptionData.put(s.getDisplayName(), statusCounts.getOrDefault(s, 0L));
            adoptionColors.put(s.getDisplayName(), s.getColor());
        }
        rebuildBars(adoptionBars, adoptionData, k -> adoptionColors.getOrDefault(k, "#94a3b8"));

        Map<Category, Long> news = queryService.newCountsByCategory();
        LinkedHashMap<String, Long> newData = new LinkedHashMap<>();
        Map<String, String> newColors = new HashMap<>();
        for (Category c : Category.values()) {
            long v = news.getOrDefault(c, 0L);
            if (v > 0) {
                newData.put(c.getDisplayName(), v);
                newColors.put(c.getDisplayName(), categoryColor(c));
            }
        }
        rebuildBars(newByCatBars, sortDesc(newData), k -> newColors.getOrDefault(k, "#4f46e5"));
        rebuildBars(licenseBars, queryService.licenseBreakdown(6), k -> "#0ea5e9");
        rebuildBars(orgBars, queryService.topOrganizations(8), k -> "#8b5cf6");
        long reuploads = queryService.derivativeCount();
        orgCaption.setText(reuploads == 0 ? "Counting original releases only."
                : UiFormat.count(reuploads) + " re-uploads (quantizations, mirrors) excluded from this ranking.");
        updateActivity();
    }

    /**
     * Draws the momentum chart and says plainly what it does and doesn't cover.
     *
     * <p>The old version charted every one of the last 30 days, which made our own
     * sampling look like Hugging Face's release rate: days we synced twice showed
     * ~450 models, days we caught up on late showed ~60, and the current day — always
     * incomplete — fell off a cliff to zero. Now only days we can vouch for are
     * plotted, and the caption accounts for everything left out.
     */
    private void renderMomentum() {
        ModelQueryService.Momentum m = queryService.releaseMomentum(30);
        // Too few measurable days for a trend line — bars read honestly at that size,
        // and this switches itself back to a line once daily syncing keeps up.
        if (m.coveredDays() > 0 && m.coveredDays() < MIN_DAYS_FOR_TREND) {
            LinkedHashMap<java.time.LocalDate, Long> covered = new LinkedHashMap<>();
            m.window().forEach((day, count) -> {
                if (count != null) {
                    covered.put(day, count);
                }
            });
            setSvg(momentumHost, Charts.dayBars(covered, "var(--mr-accent)", m.dailyAverage()));
        } else {
            setSvg(momentumHost, Charts.areaLine(m.window(), "var(--mr-accent)", m.dailyAverage()));
        }

        StringBuilder caption = new StringBuilder();
        if (m.isEmpty()) {
            caption.append("No day in the last ").append(m.requestedDays())
                    .append(" has complete coverage yet — a day counts once a sync has run "
                            + "during it and again the day after.");
        } else {
            caption.append(m.coveredDays()).append(" of the last ").append(m.requestedDays())
                    .append(" days fully synced · averaging ").append(UiFormat.count(m.dailyAverage()))
                    .append(" new models a day.");
            if (m.excludedDays() > 0) {
                caption.append(" ").append(m.excludedDays())
                        .append(" days left out — no sync ran during or right after them, "
                                + "so their counts would understate reality.");
            }
        }
        caption.append(" Today excluded (").append(UiFormat.count(m.todaySoFar()))
                .append(" so far, still in progress). Quantizations and mirrors excluded throughout.");
        momentumCaption.setText(caption.toString());
    }

    private static void setSvg(Div host, String svg) {
        host.getElement().setProperty("innerHTML", svg);
    }

    private Component legendRow(String color, String label, long value) {
        Div dot = new Div();
        dot.getStyle().set("width", "10px").set("height", "10px").set("border-radius", "3px")
                .set("background", color).set("flex", "0 0 auto");
        Span name = new Span(label);
        name.getElement().setAttribute("title", label);
        name.getStyle().set("font-size", "13px").set("color", "var(--mr-text-muted)").set("flex", "1")
                .set("white-space", "nowrap").set("overflow", "hidden").set("text-overflow", "ellipsis");
        Span val = new Span(UiFormat.count(value));
        val.getStyle().set("font-size", "13px").set("font-weight", "700").set("color", "var(--mr-text)")
                .set("flex", "0 0 auto");
        HorizontalLayout row = new HorizontalLayout(dot, name, val);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setWidthFull();
        row.getStyle().set("gap", "9px").set("padding", "3px 0");
        return row;
    }

    private void rebuildBars(Div container, Map<String, Long> data, Function<String, String> colorFn) {
        container.removeAll();
        if (data.isEmpty()) {
            Span none = new Span("No data");
            none.getStyle().set("color", "var(--mr-text-dim)").set("font-size", "13px");
            container.add(none);
            return;
        }
        long max = data.values().stream().mapToLong(Long::longValue).max().orElse(1);
        for (Map.Entry<String, Long> e : data.entrySet()) {
            container.add(barRow(e.getKey(), e.getValue(), max, colorFn.apply(e.getKey())));
        }
    }

    private Component barRow(String label, long value, long max, String color) {
        Span name = new Span(label);
        name.getElement().setAttribute("title", label);
        name.getStyle().set("font-size", "12.5px").set("color", "var(--mr-text-muted)").set("flex", "0 0 112px")
                .set("width", "112px").set("white-space", "nowrap").set("overflow", "hidden")
                .set("text-overflow", "ellipsis");
        Div track = new Div();
        track.getStyle().set("flex", "1").set("height", "9px").set("background", "var(--mr-track)")
                .set("border-radius", "6px").set("overflow", "hidden");
        double pct = max <= 0 ? 0 : value * 100.0 / max;
        if (value > 0 && pct < 3) {
            pct = 3;
        }
        Div fill = new Div();
        fill.getStyle().set("width", String.format(Locale.US, "%.1f%%", pct)).set("height", "100%")
                .set("background", color).set("border-radius", "6px");
        track.add(fill);
        Span val = new Span(UiFormat.count(value));
        val.getStyle().set("font-size", "12.5px").set("font-weight", "600").set("color", "var(--mr-text)")
                .set("flex", "0 0 46px").set("width", "46px").set("text-align", "right");
        HorizontalLayout row = new HorizontalLayout(name, track, val);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setWidthFull();
        row.getStyle().set("gap", "10px");
        return row;
    }

    private static LinkedHashMap<String, Long> sortDesc(Map<String, Long> in) {
        LinkedHashMap<String, Long> out = new LinkedHashMap<>();
        in.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    private static final Map<Category, String> CATEGORY_COLOR = Map.of(
            Category.VLM, "#4f46e5", Category.LLM, "#0ea5e9", Category.OCR, "#10b981",
            Category.DIAGRAM_ANALYSIS, "#f59e0b", Category.DOCUMENT_QA, "#ec4899",
            Category.LAYOUT, "#8b5cf6", Category.EMBEDDING, "#14b8a6", Category.OTHER, "#94a3b8");

    private static String categoryColor(Category c) {
        return CATEGORY_COLOR.getOrDefault(c, "#94a3b8");
    }

    // ── Filter bar ──────────────────────────────────────────────────────────

    private Component buildFilterBar() {
        searchField.setPlaceholder("Search model ID or author…");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.setValueChangeTimeout(300);
        searchField.setWidth("340px");
        searchField.addValueChangeListener(e -> onFilterChanged());

        statusFilter.setItems(AdoptionStatus.values());
        statusFilter.setItemLabelGenerator(AdoptionStatus::getDisplayName);
        statusFilter.setPlaceholder("All statuses");
        statusFilter.setClearButtonVisible(true);
        statusFilter.setPrefixComponent(VaadinIcon.FLAG.create());
        statusFilter.setWidth("205px");
        statusFilter.getElement().setAttribute("title", "Filter by INTICS status");
        statusFilter.addValueChangeListener(e -> onFilterChanged());

        sortField.setItems(SortOption.NEWEST, SortOption.DOWNLOADS, SortOption.LIKES);
        sortField.setItemLabelGenerator(DashboardView::sortLabel);
        sortField.setValue(SortOption.NEWEST);
        sortField.setPrefixComponent(VaadinIcon.SORT.create());
        sortField.setWidth("185px");
        sortField.addValueChangeListener(e -> {
            if (e.getValue() == null) {
                sortField.setValue(SortOption.NEWEST);
            } else {
                onFilterChanged();
            }
        });

        newOnlyFilter.addValueChangeListener(e -> onFilterChanged());

        originalsOnlyFilter.getElement().setAttribute("title",
                "Hide re-uploads: GGUF/AWQ/GPTQ quantizations, MLX conversions and known mirror "
                        + "accounts. Analytics always exclude these.");
        originalsOnlyFilter.addValueChangeListener(e -> onFilterChanged());

        clearFilters.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        clearFilters.setVisible(false);
        clearFilters.addClickListener(e -> resetFilters());

        resultCount.getStyle().set("font-size", "12.5px").set("color", "var(--mr-text-muted)")
                .set("white-space", "nowrap").set("margin-left", "auto");

        HorizontalLayout bar = new HorizontalLayout(searchField, statusFilter, sortField,
                newOnlyFilter, originalsOnlyFilter, clearFilters, resultCount);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.setSpacing(false);
        bar.getStyle().set("flex-wrap", "wrap").set("gap", "12px");

        Div c = card(bar);
        c.getStyle().set("padding", "12px 16px").set("flex", "0 0 auto");
        c.setWidthFull();
        return c;
    }

    private void resetFilters() {
        applyingUrlState = true;
        try {
            searchField.clear();
            statusFilter.clear();
            newOnlyFilter.setValue(false);
            originalsOnlyFilter.setValue(false);
            sortField.setValue(SortOption.NEWEST);
            selectTabFor(null);
        } finally {
            applyingUrlState = false;
        }
        onFilterChanged();
    }

    private boolean anyFilterActive() {
        return selectedCategory() != null
                || statusFilter.getValue() != null
                || Boolean.TRUE.equals(newOnlyFilter.getValue())
                || originalsOnly()
                || (searchField.getValue() != null && !searchField.getValue().isBlank());
    }

    private void updateResultCount() {
        int count = queryService.countModels(selectedCategory(), newSinceDays(),
                searchField.getValue(), statusFilter.getValue(), originalsOnly());
        resultCount.setText(count == 0 ? "No models match these filters"
                : UiFormat.count(count) + (count == 1 ? " model" : " models"));
        clearFilters.setVisible(anyFilterActive());
    }

    // ── Grid ────────────────────────────────────────────────────────────────

    private Component buildGridCard() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);

        // NEW badge
        grid.addColumn(new ComponentRenderer<>(m -> isNew(m) ? UiFormat.newBadge() : new Span()))
                .setHeader("").setWidth("96px").setFlexGrow(0)
                .setTooltipGenerator(m -> "First seen " + UiFormat.dateTime(m.getFirstSeenAt()));

        // Model ID (clickable, opens the model on Hugging Face)
        grid.addColumn(new ComponentRenderer<>(m -> {
            Anchor a = new Anchor(m.getModelUrl(), m.getId());
            a.setTarget("_blank");
            a.getStyle().set("color", "var(--mr-accent-text)").set("font-weight", "500");
            return a;
        })).setHeader("Model ID").setFlexGrow(5).setResizable(true)
                .setTooltipGenerator(m -> tip(m.getId())
                        + "\nClick to open on Hugging Face · double-click the row for full details");

        // INTICS status
        grid.addColumn(new ComponentRenderer<>(m -> UiFormat.statusBadge(statusOf(m))))
                .setHeader("INTICS status").setWidth("148px").setFlexGrow(0)
                .setTooltipGenerator(this::statusTooltip);

        grid.addColumn(m -> {
            ModelAdoption a = adoptionCache.get(m.getId());
            return a == null || a.getOwner() == null ? "—" : a.getOwner();
        }).setHeader("Owner").setWidth("145px").setFlexGrow(0)
                .setTooltipGenerator(this::statusTooltip);

        grid.addColumn(TrackedModel::getAuthor).setHeader("Author").setFlexGrow(1).setResizable(true)
                .setTooltipGenerator(m -> tip(m.getAuthor()));
        grid.addColumn(m -> m.getMatchedCategory() == null ? "" : m.getMatchedCategory().getShortName())
                .setHeader("Category").setWidth("115px").setFlexGrow(0)
                .setTooltipGenerator(m -> m.getMatchedCategory() == null
                        ? null : m.getMatchedCategory().getDisplayName());
        grid.addColumn(TrackedModel::getPipelineTag).setHeader("Pipeline Tag").setFlexGrow(1).setResizable(true)
                .setTooltipGenerator(m -> tip(m.getPipelineTag()));
        // License, size, last-modified and the rest live in the detail panel (ℹ on each
        // row) — the grid stays readable rather than showing fifteen clipped columns.
        grid.addColumn(m -> m.getShortDescription() == null ? "—" : m.getShortDescription())
                .setHeader("Description").setFlexGrow(2).setResizable(true)
                .setTooltipGenerator(m -> tip(m.getShortDescription()));
        grid.addColumn(m -> UiFormat.count(m.getDownloads())).setHeader("Downloads").setFlexGrow(0).setWidth("112px")
                .setTooltipGenerator(m -> String.format("%,d downloads · %s · %s",
                        m.getDownloads(),
                        m.getLicense() == null ? "license not stated" : m.getLicense(),
                        m.getModelSize() == null ? "size not stated" : m.getModelSize()));
        grid.addColumn(m -> UiFormat.count(m.getLikes())).setHeader("Likes").setFlexGrow(0).setWidth("82px")
                .setTooltipGenerator(m -> String.format("%,d likes", m.getLikes()));
        grid.addColumn(m -> UiFormat.date(m.getCreatedAt())).setHeader("Created").setFlexGrow(0).setWidth("118px")
                .setTooltipGenerator(m -> "Created on Hugging Face " + UiFormat.dateTime(m.getCreatedAt())
                        + "\nLast modified " + UiFormat.dateTime(m.getLastModified()));
        grid.addColumn(m -> UiFormat.date(m.getFirstSeenAt())).setHeader("First Seen").setFlexGrow(0).setWidth("118px")
                .setTooltipGenerator(m -> "First seen by Model Radar " + UiFormat.dateTime(m.getFirstSeenAt())
                        + "\nLast synced " + UiFormat.dateTime(m.getLastFetchedAt()));

        // Row actions, pinned to the right edge so they're always reachable.
        grid.addColumn(new ComponentRenderer<>(this::rowActions))
                .setHeader("").setWidth("104px").setFlexGrow(0).setFrozenToEnd(true);

        grid.setColumnReorderingAllowed(true);
        grid.addItemDoubleClickListener(e -> openDetail(e.getItem()));
        grid.setPageSize(PAGE_SIZE);
        grid.setSizeFull();
        grid.setItems(dataProvider);

        Div card = card(grid);
        card.getStyle().set("padding", "8px").set("flex", "1 1 auto").set("min-height", "420px")
                .set("display", "flex").set("flex-direction", "column");
        card.setWidthFull();
        setFlexGrow(1, card);
        return card;
    }

    private Component rowActions(TrackedModel model) {
        Button details = new Button(VaadinIcon.INFO_CIRCLE_O.create(), e -> openDetail(model));
        details.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_SMALL);
        details.getElement().setAttribute("title", "Full details & timeline");

        Button setStatus = new Button(VaadinIcon.FLAG.create(), e -> openStatusDialog(model));
        setStatus.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_SMALL);
        setStatus.getElement().setAttribute("title", "Set INTICS status");

        HorizontalLayout row = new HorizontalLayout(details, setStatus);
        row.setSpacing(false);
        row.setPadding(false);
        row.getStyle().set("gap", "2px");
        row.addClassName("mr-row-actions");
        return row;
    }

    private void openDetail(TrackedModel model) {
        new ModelDetailDialog(adoptionService, model, queryService.newSince(),
                status -> afterStatusSaved(model)).open();
    }

    private void openStatusDialog(TrackedModel model) {
        new StatusDialog(adoptionService, model, status -> afterStatusSaved(model)).open();
    }

    /** After a status change: the row, the counts and the analytics all need to catch up. */
    private void afterStatusSaved(TrackedModel model) {
        adoptionCache.remove(model.getId());
        adoptionCache.putAll(adoptionService.findAll(List.of(model.getId())));
        dataProvider.refreshItem(model);
        updateResultCount();
        updateAnalytics();
    }

    private void cacheAdoptions(List<TrackedModel> page) {
        if (page.isEmpty()) {
            return;
        }
        List<String> ids = page.stream().map(TrackedModel::getId).toList();
        Map<String, ModelAdoption> fresh = adoptionService.findAll(ids);
        ids.forEach(adoptionCache::remove);
        adoptionCache.putAll(fresh);
    }

    private AdoptionStatus statusOf(TrackedModel m) {
        ModelAdoption a = adoptionCache.get(m.getId());
        return a == null ? AdoptionStatus.NOT_REVIEWED : a.getStatus();
    }

    private String statusTooltip(TrackedModel m) {
        ModelAdoption a = adoptionCache.get(m.getId());
        if (a == null) {
            return "Not reviewed — nobody at INTICS has picked this model up yet."
                    + "\nUse the flag button on this row to mark it.";
        }
        StringBuilder sb = new StringBuilder(a.getStatus().getDisplayName());
        if (a.getOwner() != null) {
            sb.append("\nOwner: ").append(a.getOwner());
        }
        if (a.getTeam() != null) {
            sb.append("\nTeam: ").append(a.getTeam());
        }
        if (a.getUseCase() != null) {
            sb.append("\nUse case: ").append(a.getUseCase());
        }
        sb.append("\nSince ").append(UiFormat.dateTime(a.getStatusChangedAt()));
        sb.append("\nUpdated ").append(UiFormat.ago(a.getUpdatedAt()));
        if (a.getNotes() != null) {
            sb.append("\nNote: ").append(a.getNotes());
        }
        return sb.toString();
    }

    // ── Filtering / URL state ───────────────────────────────────────────────

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Map<String, List<String>> params = event.getLocation().getQueryParameters().getParameters();
        applyingUrlState = true;
        try {
            Category cat = params.containsKey("category") ? parseCategory(params.get("category").get(0)) : null;
            selectTabFor(cat);
            sortField.setValue(SortOption.fromParam(first(params.get("sort"))));
            statusFilter.setValue(AdoptionStatus.fromParam(first(params.get("status"))));
            newOnlyFilter.setValue(params.containsKey("new") && "true".equalsIgnoreCase(params.get("new").get(0)));
            originalsOnlyFilter.setValue(params.containsKey("originals")
                    && "true".equalsIgnoreCase(params.get("originals").get(0)));
            searchField.setValue(params.containsKey("search") ? params.get("search").get(0) : "");
        } finally {
            applyingUrlState = false;
        }
        updateTabCounts();
        updateAnalytics();
        updateLastSynced();
        updateResultCount();
        refreshGrid();
    }

    private void onTabChanged() {
        if (applyingUrlState) {
            return;
        }
        refreshGrid();
        updateResultCount();
        updateUrl();
    }

    private void onFilterChanged() {
        if (applyingUrlState) {
            return;
        }
        refreshGrid();
        updateResultCount();
        updateUrl();
    }

    private void refreshGrid() {
        adoptionCache.clear();
        dataProvider.refreshAll();
    }

    private void updateUrl() {
        Map<String, String> params = new LinkedHashMap<>();
        Category cat = selectedCategory();
        if (cat != null) {
            params.put("category", cat.name());
        }
        if (statusFilter.getValue() != null) {
            params.put("status", statusFilter.getValue().name().toLowerCase(Locale.ROOT));
        }
        if (Boolean.TRUE.equals(newOnlyFilter.getValue())) {
            params.put("new", "true");
        }
        if (originalsOnly()) {
            params.put("originals", "true");
        }
        if (sortValue() != SortOption.NEWEST) {
            params.put("sort", sortValue().name().toLowerCase(Locale.ROOT));
        }
        String search = searchField.getValue();
        if (search != null && !search.isBlank()) {
            params.put("search", search.trim());
        }
        getUI().ifPresent(ui -> ui.getPage().getHistory()
                .replaceState(null, new Location("", QueryParameters.simple(params))));
    }

    // ── Refresh Now (manual sync) ───────────────────────────────────────────

    private void runSync() {
        refreshButton.setEnabled(false);
        progressBar.setVisible(true);
        UI ui = UI.getCurrent();
        Thread worker = new Thread(() -> {
            try {
                ModelSyncService.SyncSummary summary = syncRunService.runAndRecord(SyncTrigger.MANUAL);
                ui.access(() -> {
                    refreshGrid();
                    updateTabCounts();
                    updateAnalytics();
                    updateLastSynced();
                    updateResultCount();
                    finishSync();
                    Notification n = Notification.show(
                            "Sync complete: %d new, %d updated".formatted(summary.created(), summary.updated()),
                            4000, Notification.Position.TOP_END);
                    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                });
            } catch (RuntimeException ex) {
                ui.access(() -> {
                    finishSync();
                    Notification n = Notification.show("Sync failed: " + ex.getMessage(),
                            5000, Notification.Position.TOP_END);
                    n.addThemeVariants(NotificationVariant.LUMO_ERROR);
                });
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void finishSync() {
        progressBar.setVisible(false);
        refreshButton.setEnabled(true);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void updateLastSynced() {
        lastSynced.setText(syncRunService.lastSuccessfulRun()
                .map(SyncRun::getFinishedAt)
                .map(t -> "Last synced " + UiFormat.ago(t))
                .orElse("Never synced — click Refresh now"));
    }

    private Category selectedCategory() {
        TabInfo ti = tabInfo.get(tabs.getSelectedTab());
        return ti == null ? null : ti.category;
    }

    private void selectTabFor(Category category) {
        for (Map.Entry<Tab, TabInfo> e : tabInfo.entrySet()) {
            if (java.util.Objects.equals(e.getValue().category, category)) {
                tabs.setSelectedTab(e.getKey());
                return;
            }
        }
    }

    private Integer newSinceDays() {
        return Boolean.TRUE.equals(newOnlyFilter.getValue()) ? queryService.newWindowDays() : null;
    }

    private boolean originalsOnly() {
        return Boolean.TRUE.equals(originalsOnlyFilter.getValue());
    }

    private SortOption sortValue() {
        return sortField.getValue() == null ? SortOption.NEWEST : sortField.getValue();
    }

    private boolean isNew(TrackedModel m) {
        return m.getFirstSeenAt() != null && !m.getFirstSeenAt().isBefore(queryService.newSince());
    }

    /**
     * Tooltip text: the full value, plus an English translation appended when the
     * value is non-English (foreign-language descriptions etc.). English/Latin text
     * is returned unchanged; translation failures fall back to the original.
     */
    private String tip(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (!translationService.isNonEnglish(text)) {
            return text;
        }
        String english = translationService.toEnglish(text);
        return english == null ? text : text + "   🌐 EN: " + english;
    }

    /** A card surface: rounded, subtle shadow + hairline border. */
    private Div card(Component... children) {
        Div d = new Div(children);
        d.getStyle()
                .set("background", "var(--mr-surface)")
                .set("border", "1px solid var(--mr-border)")
                .set("border-radius", "16px")
                .set("box-shadow", "var(--mr-shadow)");
        return d;
    }

    private VerticalLayout tightColumn(Component... children) {
        VerticalLayout col = new VerticalLayout(children);
        col.setPadding(false);
        col.setSpacing(false);
        col.setMargin(false);
        col.getStyle().set("gap", "2px");
        return col;
    }

    private static String sortLabel(SortOption option) {
        return switch (option) {
            case NEWEST -> "Newest";
            case DOWNLOADS -> "Most downloads";
            case LIKES -> "Most likes";
        };
    }

    private static Category parseCategory(String raw) {
        try {
            return Category.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /** Per-tab references so we can refresh the total + NEW badge after a sync. */
    private record TabInfo(Category category, Span total, Span newBadge) {
    }
}
