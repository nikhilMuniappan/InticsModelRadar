# Model Radar — backend

The single Spring Boot application: the Hugging Face sync + scheduler, PostgreSQL
persistence, the REST API, **and** the embedded Vaadin dashboard UI. See the
[top-level README](../README.md) for the full runbook; this file is
component-specific detail.

## Stack

- Java 21, Spring Boot 3.5, Maven (via the `./mvnw` wrapper)
- Spring Data JPA + PostgreSQL; **Flyway** owns the schema (`ddl-auto=validate`)
- Spring `WebClient` (from `spring-boot-starter-webflux`) as the outbound HF client
  — the app itself is servlet-based (Tomcat)
- **ShedLock** for scheduled-job locking
- **Vaadin 24 (core)** for the UI — see [`../frontend/README.md`](../frontend/README.md)

## Run / build / test

```bash
./mvnw spring-boot:run                 # run (dev); serves API + UI on :8080
./mvnw test                            # unit tests (skips the live integration test)
./mvnw test -Dtest=HuggingFaceClientIntegrationTest   # the live HF integration test
./mvnw -Pproduction package            # production build (optimized Vaadin bundle)
```

- Requires the Postgres from the project-root `docker compose up -d` (on `:5433`).
- The live integration test hits the real HF API; it's tagged `integration`. CI can
  skip it: `./mvnw test -DexcludedGroups=integration`.

## Package layout (`com.intics.modelradar`)

| Package       | What's in it                                                             |
| ------------- | ------------------------------------------------------------------------ |
| `domain`      | JPA entities (`TrackedModel`, `SyncRun`) + enums (`Category`, `MatchStrategy`, `SyncTrigger`, `SyncStatus`) |
| `repository`  | Spring Data repositories (`TrackedModelRepository`, `SyncRunRepository`) |
| `hf`          | Hugging Face client (`HuggingFaceClient`), DTO (`HuggingFaceModel`), and `HuggingFaceMetadata` (license / model-size / README-description extraction) |
| `service`     | `ModelSyncService` (fetch + diff + upsert), `SyncRunService` (audit wrapper), `ModelQueryService` (dashboard reads) |
| `job`         | `ScheduledSyncJob` (`@Scheduled` + `@SchedulerLock`)                      |
| `web`         | REST controllers (`ModelController`, `AdminController`, `HealthController`) + DTOs |
| `ui`          | Vaadin views (`DashboardView`) + `AppShell` (`@Push`)                    |
| `config`      | `ModelRadarProperties`, `TrackingProperties`, `SchedulingConfig`, `WebConfig` (CORS) |

## REST API

| Method & path            | Purpose                                                        |
| ------------------------ | -------------------------------------------------------------- |
| `GET /api/models`        | Paginated list; params `category`, `newSince` (days), `sort` (`newest`/`downloads`/`likes`), `search`, `page`, `size`. Returns `{content,totalElements,page,size}` |
| `GET /api/models/new`    | "New this week" (firstSeen within 7 days)                      |
| `GET /api/categories`    | Categories with per-category counts                           |
| `POST /api/admin/sync`   | Trigger a sync now (recorded in `sync_runs`)                   |
| `GET /api/health`        | 200 UP if last successful sync < 26h ago, else 503 DOWN       |

The dashboard UI calls the Spring services **directly (in-JVM)**, not the REST API;
the API is for external/future consumers and debugging.

## Configuration (`application.yml`, prefix `model-radar`)

- `huggingface.*` — base URL, `page-size`, `new-window-days`, `retry-backoff`,
  `response-timeout`, `readme-fetch-limit` (README fetches per run → `shortDescription`)
- `tracking.pipeline-tags` / `tracking.keywords` — the Section 4 catalog, grouped by
  category (edit these to track more/less; restart to apply)
- `sync.cron` — the scheduled-sync cron; **unset = disabled** (dev default). Set in
  `application-prod.yml` (`0 0 8 * * *`) or via `SYNC_CRON`.

Datasource overridable via `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD`.

## Database schema (Flyway, `src/main/resources/db/migration`)

| Migration | Table            | Notes                                             |
| --------- | ---------------- | ------------------------------------------------- |
| `V1`      | `tracked_models` | one row per tracked model (Section 5 fields + our `first_seen_at`) |
| `V2`      | `shedlock`       | ShedLock's lock table                             |
| `V3`      | `sync_runs`      | audit trail of every sync (start/end, status, counts) |
