# Model Radar — frontend

**There is no separate frontend application.** The dashboard is built with
[Vaadin](https://vaadin.com) and runs **inside the Spring Boot backend** — same JVM,
same process, same port (`8080`). The project was originally planned with a standalone
Next.js frontend; it was consolidated into a single Java application (one process, one
deployment). This folder is kept only as a signpost.

## Where the UI lives

`backend/src/main/java/com/intics/modelradar/ui/`

| Class | Role |
| --- | --- |
| `DashboardView` | The dashboard (`@Route("")`, served at `/`): header, category tabs, collapsible analytics, filter bar (search / status / sort / new-only / originals-only), and the lazily-loaded model grid with the `NEW` badge and status pills. Filter state is mirrored into the URL |
| `ModelDetailDialog` | Per-model panel — every stored Hugging Face field plus INTICS state and timeline (ℹ button or row double-click) |
| `StatusDialog` | The INTICS status form (⚑ button); owner and team are allow-custom combo boxes seeded from existing values |
| `Charts` | Donut, area/line and bar charts built as **hand-written SVG** — no charting dependency, and nothing commercial |
| `UiFormat` | Shared number/date/text formatting |
| `AppShell` | Enables Vaadin `@Push`, so a background sync can update the UI live |

Dependency: `com.vaadin:vaadin-core` (Apache-licensed components only) via
`vaadin-spring-boot-starter`, declared in `backend/pom.xml`. The full `vaadin`
platform artifact is deliberately excluded — its commercial components trigger an
online licence check.

## How to run it

Run the backend and open the dashboard; there is nothing else to start.

```bash
cd ../backend
./mvnw spring-boot:run
# then open http://localhost:8080/
```

- **Local development** uses Vaadin's prebuilt dev bundle — **no Node/npm build**
  required to start.
- **Production** bundles the frontend through the Vaadin Maven plugin under the
  `production` profile, which needs **Node 20+**:

  ```bash
  cd ../backend
  ./mvnw -Pproduction package
  ```

## Theming

Dark is the default theme, with a light toggle in the header that persists per browser
(`localStorage['mr-theme']`). Colours are defined as `--mr-*` CSS custom properties for
both themes; the SVG charts use inline `style="fill:var(--mr-…)"` because presentation
attributes do not resolve `var()`.

## How data reaches the UI

Vaadin views inject the Spring `@Service` beans (`ModelQueryService`,
`AdoptionService`, `SyncRunService`) and call them **directly, in-JVM** — no HTTP
round-trip. The REST API (`/api/**`) exists for external consumers, not for this UI.

See [`../backend/README.md`](../backend/README.md) and the
[top-level README](../README.md) for the full picture.
