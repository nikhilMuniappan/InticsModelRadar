# INTICS Model Radar — Test Cases

Comprehensive test catalogue for the Model Radar (Spring Boot + Vaadin, single app).
Use it for manual QA, as an acceptance checklist, and as the backlog for automated
tests.

## Conventions

**Type**

| Code | Meaning |
| ---- | ------- |
| UNIT | Pure unit test (no Spring, no DB, no network) |
| INT  | Integration (Spring context and/or DB and/or live Hugging Face) |
| API  | REST endpoint test (HTTP against the running app) |
| UI   | End-to-end through the Vaadin dashboard in a browser |
| OPS  | Operational / manual (scheduler, deployment, resilience) |

**Priority:** P1 = critical (blocks "done"), P2 = high, P3 = nice-to-have.

**Standing preconditions** (assumed unless a case says otherwise): PostgreSQL is up
(`docker compose up -d`, host `:5433`); the app is running (`:8080`); the database has
been populated by at least one sync.

---

## A. Hugging Face API client (`HuggingFaceClient`)

| ID | Objective | Steps | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-HF-01 | Fetch by pipeline tag returns newest-first results | Call `fetchModelsByPipelineTag("image-text-to-text", 5)` | Non-empty list, ≤5 items, each with a non-blank `id`, ordered by `createdAt` desc | INT | P1 |
| TC-HF-02 | Fetch by keyword search returns results | Call `searchModelsByKeyword("OCR", 5)` | Non-empty list; ids/authors relate to the keyword | INT | P1 |
| TC-HF-03 | Request includes `cardData` + `full` | Inspect the built URL / DEBUG log | URL has `cardData=true&full=true`; responses include `lastModified` and card license | INT | P2 |
| TC-HF-04 | Rate-limit (429) → retry once, then skip | Simulate/observe a 429 from HF | One retry after `retry-backoff`; on continued 429 → empty list + WARN "rate-limited"; sync continues | INT | P2 |
| TC-HF-05 | Network/timeout error is swallowed | Point `base-url` at an unreachable host; run a fetch | Returns empty list, logs "fetch failed", does not throw | INT | P1 |
| TC-HF-06 | README fetch → short description | Call `fetchDescription(id)` for a model with a README | Returns cleaned first-paragraph text; for a model with no README (404) → `null` | INT | P2 |
| TC-HF-07 | Redirects on raw README are followed | Fetch a README URL that 302-redirects | Content is returned (HttpClient `followRedirect`) | INT | P3 |

## B. Metadata extraction (`HuggingFaceMetadata`) — UNIT

| ID | Objective | Input | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-META-01 | License from `cardData.license` (string) | cardData license = `"apache-2.0"` | `"apache-2.0"` | UNIT | P2 |
| TC-META-02 | License from `cardData.license` (list) | license = `["mit","other"]` | `"mit, other"` | UNIT | P3 |
| TC-META-03 | License from `license:*` tag fallback | no cardData; tags include `license:gemma` | `"gemma"` | UNIT | P2 |
| TC-META-04 | License unknown → null | no cardData license, no license tag | `null` | UNIT | P2 |
| TC-META-05 | Model size regex | ids ending `-7B`, `-1.5B`, `-70M`, `-13B`, and one with none | `7B`,`1.5B`,`70M`,`13B`, and `null` | UNIT | P2 |
| TC-META-06 | Description cleaning | README with YAML front-matter + `<a>`/`&nbsp;` badge line + first prose paragraph | Front-matter/HTML/markdown stripped; first real paragraph; ≤300 chars; empty README → `null` | UNIT | P2 |

## C. Categorisation & tracking catalog

| ID | Objective | Steps | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-CAT-01 | Pipeline tag → correct category | For each tracked tag, sync a model carrying it | `matchedCategory` matches the yml grouping (e.g. `optical-character-recognition`→OCR) | INT | P1 |
| TC-CAT-02 | Keyword → correct category | Keyword-only match (tag not tracked) | Filed under the keyword's category, `matchStrategy=KEYWORD_SEARCH` | INT | P1 |
| TC-CAT-03 | Exactly one category chosen | Model matching multiple tags | Single `matchedCategory` per the documented tie-breaks (image-to-text→OCR, image-segmentation→LAYOUT, object-detection→DIAGRAM_ANALYSIS) | INT | P2 |
| TC-CAT-04 | Only Section 4 categories tracked | Query `select distinct matched_category` | Values ⊆ {VLM,LLM,OCR,DIAGRAM_ANALYSIS,DOCUMENT_QA,LAYOUT,EMBEDDING,OTHER} | INT | P1 |

## D. Sync & diff (`ModelSyncService`)

| ID | Objective | Steps | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-SYNC-01 | First sight inserts, stamps firstSeenAt | Empty DB → run sync | New rows inserted; `first_seen_at ≈ now`; `matchStrategy` set | INT | P1 |
| TC-SYNC-02 | Re-sync updates mutable fields only, preserves firstSeenAt | Run sync twice | Existing rows: `downloads/likes/lastModified/lastFetchedAt` refreshed; `first_seen_at` UNCHANGED | INT | P1 |
| TC-SYNC-03 | Keyword pass skips ids caught by tag pass | Run a full sync | No duplicate rows; each id processed once (`checked` = distinct ids) | INT | P1 |
| TC-SYNC-04 | matchStrategy provenance | Inspect rows | Tag-caught → `PIPELINE_TAG`; keyword-only → `KEYWORD_SEARCH` | INT | P2 |
| TC-SYNC-05 | Description fetch + backfill, capped | Null-out descriptions; run sync | Up to `readme-fetch-limit` descriptions fetched per run; new rows get one; log shows "(N descriptions fetched)" | INT | P2 |
| TC-SYNC-06 | Summary counts correct | Run sync | `checked = new + updated`; returned summary matches DB deltas | INT | P2 |
| TC-SYNC-07 | Run recorded in audit trail | Run sync | A `sync_runs` row (RUNNING→SUCCESS) with counts | INT | P1 |

## E. Persistence & schema

| ID | Objective | Steps | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-DB-01 | Flyway migrations apply cleanly | Start app on a fresh DB | V1/V2/V3 applied; `flyway_schema_history` all success; Hibernate `validate` passes | INT | P1 |
| TC-DB-02 | All Section 5 fields stored | `\d tracked_models` + sample rows | Every field present; nullable ones null-with-reason (pipeline_tag/license/model_size/short_description) | INT | P1 |
| TC-DB-03 | Data survives restart | Restart app | Rows persist (Docker volume); counts unchanged; NEW badges age from stored timestamps | OPS | P1 |

## F. "NEW" badge / new-since logic

| ID | Objective | Steps | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-NEW-01 | Recent model is NEW | Set a row `first_seen_at = now` | Appears in `/api/models/new`; `isNew=true`; UI shows NEW pill | API | P1 |
| TC-NEW-02 | Old model is not NEW | Set a row `first_seen_at = now-8d` | Excluded from `/api/models/new`; `isNew=false`; no pill | API | P1 |
| TC-NEW-03 | Badge ages out at +7d | Model first seen exactly 7 days + 1s ago | No longer NEW | API | P2 |
| TC-NEW-04 | Refresh does not reset firstSeenAt | Note a row's firstSeenAt → Refresh Now → re-check | `first_seen_at` unchanged; badge persists its remaining window | API | P1 |

## G. REST API

| ID | Objective | Request | Expected result | Type | Pri |
|----|-----------|---------|-----------------|------|-----|
| TC-API-01 | Paginated list envelope | `GET /api/models` | `{content,totalElements,page,size}`; page 0, size 50, sort newest | API | P1 |
| TC-API-02 | Category filter | `?category=OCR` | Only OCR rows | API | P1 |
| TC-API-03 | New-since filter | `?newSince=3` | Only models first seen within 3 days | API | P2 |
| TC-API-04 | Sort options | `?sort=downloads` / `likes` / `newest` | Ordered accordingly (e.g. downloads desc) | API | P1 |
| TC-API-05 | Case-insensitive search | `?search=QWEN` and `?search=qwen` | Same non-empty result on id/author substring | API | P1 |
| TC-API-06 | Pagination & size cap | `?page=1&size=5`; `?size=9999` | Correct page; size clamped to ≤200 | API | P2 |
| TC-API-07 | New-this-week endpoint | `GET /api/models/new` | Only firstSeen within 7 days | API | P1 |
| TC-API-08 | Categories with counts | `GET /api/categories` | All 8 categories with `{name,label,count}` | API | P2 |
| TC-API-09 | Manual sync endpoint | `POST /api/admin/sync` | 200 with `{checked,created,updated}`; a MANUAL `sync_runs` row | API | P1 |
| TC-API-10 | Health endpoint | `GET /api/health` | 200 `UP` if last success <26h; else 503 `DOWN`; body has `lastSuccessfulSyncAt` | API | P1 |
| TC-API-11 | JSON shape | Inspect a model object | camelCase keys; `isNew` present; **no** `lastFetchedAt` leaked | API | P2 |
| TC-API-12 | CORS | Preflight `OPTIONS /api/models` with `Origin: http://localhost:3000` | `Access-Control-Allow-Origin: http://localhost:3000`, methods GET,POST,OPTIONS | API | P3 |
| TC-API-13 | Unknown category param | `?category=BOGUS` | Treated as "all" (no 500) | API | P3 |

## H. Dashboard UI (end-to-end)

| ID | Objective | Steps | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-UI-01 | Dashboard renders | Open `http://localhost:8080/` | Header, tabs, stat cards, filter bar, and grid all render | UI | P1 |
| TC-UI-02 | Tab per category with NEW counts | Inspect tab bar | "All" + one tab per category; each shows total + green "N new" badge | UI | P1 |
| TC-UI-03 | Tab selection filters everything | Click "OCR" | Grid shows only OCR; stat cards update; URL → `?category=OCR` | UI | P1 |
| TC-UI-04 | Stat cards accuracy | Per selected tab | Models-in-view, New-this-week, Most-downloaded match the DB for that category | UI | P2 |
| TC-UI-05 | Sort control | Choose "Most downloads" | Grid reorders by downloads desc | UI | P2 |
| TC-UI-06 | Search (debounced) | Type "qwen" | Grid filters after ~300 ms; URL → `?search=qwen` | UI | P1 |
| TC-UI-07 | "New only" checkbox | Tick it | Only NEW models shown; URL → `?new=true` | UI | P2 |
| TC-UI-08 | NEW pill correctness | Compare recent vs backdated rows | Pill only on rows first-seen within 7 days | UI | P1 |
| TC-UI-09 | Model ID links out | Click a Model ID | Opens `https://huggingface.co/{id}` in a new tab | UI | P2 |
| TC-UI-10 | Tooltips on truncated text | Hover each truncated cell (ID, author, category, pipeline tag, description, counts, dates) | Full value shown in a tooltip | UI | P1 |
| TC-UI-11 | Refresh Now | Click Refresh now | Progress shows; success notification; grid, tab badges, and stat cards update | UI | P1 |
| TC-UI-12 | Shareable deep link | Open `/?category=VLM&sort=downloads&search=x` | Filters applied on load; correct tab selected | UI | P2 |
| TC-UI-13 | Empty state | Filter/search with no matches (or empty DB) | Graceful empty grid / "No models yet" message | UI | P3 |
| TC-UI-14 | Reads from DB only | Load dashboard while HF is blocked | Dashboard works fully (no live HF call on page load) | UI | P1 |

## I. Scheduler

| ID | Objective | Steps | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-SCH-01 | Disabled when no cron | Start with `sync.cron` unset | Cron resolves to `-` (disabled); no scheduled runs | OPS | P1 |
| TC-SCH-02 | Daily 8 AM cron | `sync.cron="0 0 8 * * *"` (default now) | Fires once daily at 08:00 local while app runs | OPS | P1 |
| TC-SCH-03 | Prod profile enables it | `SPRING_PROFILES_ACTIVE=prod` | 8 AM cron active | OPS | P2 |
| TC-SCH-04 | Env override | `SYNC_CRON="*/30 * * * * *"` | Fires every 30 s (verification only) | OPS | P2 |
| TC-SCH-05 | One-time kickoff | `SYNC_KICKOFF="<future ISO>"` | Runs once at that instant (recorded SCHEDULED); past time → skipped + WARN; invalid → WARN | OPS | P2 |
| TC-SCH-06 | No overlapping runs (ShedLock) | Trigger concurrent/near-simultaneous runs | Only one executes; a `shedlock` row is held; the other tick is skipped | INT | P1 |
| TC-SCH-07 | Scheduled run is audited | After a scheduled fire | `sync_runs` row with `trigger_source=SCHEDULED` | INT | P2 |
| TC-SCH-08 | In-JVM requirement | Stop the app before the fire time | The scheduled run does NOT fire (documented: app must be running) | OPS | P2 |

## J. Health & audit trail

| ID | Objective | Steps | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-AUD-01 | Every run audited | Trigger a sync | `sync_runs` row transitions RUNNING → SUCCESS with counts | INT | P1 |
| TC-AUD-02 | Failures are audited | Force a sync failure | Row recorded as FAILURE with `error_message`; sync itself not silently lost | INT | P2 |
| TC-AUD-03 | Health freshness window | Last success 25 h vs 27 h ago | 200 UP vs 503 DOWN respectively | API | P1 |
| TC-AUD-04 | No success yet | Fresh DB, no sync | Health = 503 DOWN | API | P2 |

## K. Resilience / error handling

| ID | Objective | Steps | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-RES-01 | Dashboard independent of HF | Point HF `base-url` at unreachable host; reload | `/`, `/api/models`, `/api/health` all 200 from Postgres; a sync yields `checked=0` + fetch-failed warnings | OPS | P1 |
| TC-RES-02 | Rate-limit recovery | Sustained 429s | Retry once then skip; next run recovers those tags | INT | P2 |
| TC-RES-03 | DB unavailable | Stop Postgres, hit API | Errors surfaced (5xx); on restart, app reconnects and serves | OPS | P2 |
| TC-RES-04 | Unexpected HF fields | HF adds/removes JSON fields | `@JsonIgnoreProperties(ignoreUnknown=true)` → no deserialization crash | INT | P2 |

## L. Configuration

| ID | Objective | Steps | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-CFG-01 | Add/remove tracked tag or keyword | Edit `model-radar.tracking` in `application.yml`; restart | The new tag/keyword is queried on the next sync; a removed one is not | OPS | P1 |
| TC-CFG-02 | Datasource override | Set `SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD` | App connects to the overridden DB | OPS | P2 |
| TC-CFG-03 | README fetch cap | Set `readme-fetch-limit=0` | No README calls; descriptions stay null | OPS | P3 |
| TC-CFG-04 | Port collision documented | Native Postgres on 5432 present | Compose maps 5433; app connects; README explains | OPS | P3 |

## M. Non-functional

| ID | Objective | Steps | Expected result | Type | Pri |
|----|-----------|-------|-----------------|------|-----|
| TC-NFR-01 | Dashboard latency | Load dashboard | Renders quickly; no synchronous HF calls in the read path | OPS | P2 |
| TC-NFR-02 | Large dataset paging | 3,000+ rows | Grid stays responsive via lazy 50-row pages (server-side) | UI | P2 |
| TC-NFR-03 | Access control | Review security posture | No auth by design (internal tool, network-scoped); documented, not a defect | OPS | P3 |
| TC-NFR-04 | Sync duration | Time a full sync | Completes in a bounded time (~30 s incl. up to 300 README fetches) | OPS | P3 |

---

## Acceptance mapping (Section 10 of the spec)

| Spec criterion | Covered by |
|----------------|-----------|
| Automated ≥ once/day | TC-SCH-02, TC-SCH-07, TC-SYNC-07 |
| Only Section 4 categories | TC-CAT-04, TC-CAT-01/02 |
| All Section 5 fields | TC-DB-02, TC-META-* , TC-SYNC-05 |
| NEW badge (last 7 days) | TC-NEW-01..04, TC-UI-08 |
| Filter + sort work | TC-API-02/04/05, TC-UI-03/05/06 |
| Dashboard reads stored data (not HF) | TC-RES-01, TC-UI-14 |
| Runs unattended 7 days | TC-SCH-02/08, TC-DB-03, TC-AUD-03 (monitor via `/api/health`) |
| README to run/restart | Top-level `README.md` runbook |
