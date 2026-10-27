# Project: INTICS Model Radar — Hugging Face Model Release Tracker

> This document is the project specification. It was reconstructed from the
> original project brief and kept as the source of truth in the repo. **Section 8
> (Tech stack) was updated on 2026-07-27**: the frontend is now **Vaadin, embedded
> in the Spring Boot application**, instead of a separate Next.js app.

## 1. Why we need this (read this first)

INTICS builds products that depend on AI models — for reading documents,
understanding diagrams, extracting data from drawings, answering questions, etc.
The AI research community releases new models constantly on a website called
Hugging Face (think of it like an "app store" for AI models — anyone can publish
a model there, and thousands of new ones show up every week).

Right now, nobody at INTICS has time to manually go to huggingface.co every day
and check "did anything new and useful come out?" So we miss good models that
could make our products better, faster, or cheaper to run — until someone happens
to stumble across them.

**Your job:** build a small internal tool that automatically checks Hugging Face
on a schedule, finds models relevant to INTICS's use cases, and shows them to us
on a screen (a dashboard) — so we never have to manually search again.

This is not a research project. This is a practical internal tool. Treat it like
building a small app that has one job and does it reliably.

## 2. Glossary — read this before anything else

| Term | What it means |
| --- | --- |
| **LLM (Large Language Model)** | An AI model that understands and generates text. Example: the model behind ChatGPT. Used at INTICS for things like answering questions about documents. |
| **VLM (Vision-Language Model)** | An AI model that can look at an image and understand/describe it, or answer questions about it. This is what INTICS uses to "read" diagrams, P&IDs, drawings — the model looks at the picture and understands what's in it. |
| **OCR (Optical Character Recognition)** | Technology that reads text out of an image or scanned document (turns a picture of text into actual, editable text). |
| **Model / AI model** | A file (or set of files) containing a trained neural network that can do a specific task (e.g., read an image, answer a question, generate text). |
| **Hugging Face / Hugging Face Hub** | A website (huggingface.co) where AI researchers and companies publish their models publicly. The biggest public library of AI models in the world. |
| **Hugging Face Hub API** | A way for a program (not a human clicking around) to ask Hugging Face's servers questions like "show me all models tagged as OCR, sorted by newest." We use this API — NOT scraping the website. |
| **Pipeline tag / Task tag** | A label Hugging Face puts on each model describing what it does — e.g., image-to-text, visual-question-answering, text-generation, object-detection. This is how we filter for relevant models. |
| **API / Endpoint** | A URL a program sends a request to, to get data back (usually JSON). |
| **JSON** | A simple text format for structured data, e.g. `{"name": "model-x", "downloads": 5000}`. |
| **Cron job / Scheduled job** | A program that runs automatically on a fixed schedule (e.g., "every day at 8 AM") without a human starting it. |
| **Database (DB)** | A place to store data permanently so it isn't lost when the program stops. We use a simple one. |
| **Backend** | The part that does the actual work — fetching, processing, storing. Runs on a server. |
| **Frontend / Dashboard / UI** | The part a human looks at and clicks on — tables, filters, buttons. |
| **Diff / Delta** | The difference between two points in time — e.g., "which models are new today that weren't there yesterday." |
| **Repo card / README** | The description page a model has on Hugging Face, written by whoever published it. |

## 3. What "done" looks like (the goal, in plain words)

Someone at INTICS should be able to open one screen, once a week (or whenever
they want), and see:

- A list of new or recently updated AI models relevant to INTICS's work
- Organized/filterable by category (VLM, LLM, OCR, document/diagram understanding)
- With enough information to decide in 30 seconds whether it's worth investigating
- Without anyone having to manually browse huggingface.co

That's it. That's the whole product.

## 4. Which categories of models we care about (be specific — don't track everything)

Hugging Face has hundreds of thousands of models. We do NOT want all of them. We
only care about models relevant to INTICS's actual use cases: VLM, LLM, OCR,
diagram/analysis on top of diagrams, and extraction from diagrams.

Because Hugging Face's `pipeline_tag` filter alone misses many relevant models
(labs don't tag cleanly), this tracker needs **two tracking strategies working
together**.

### Strategy A — filter by official pipeline tag

| INTICS use case | Hugging Face pipeline tag(s) to track |
| --- | --- |
| Reading/understanding diagrams, drawings, P&IDs (VLM) | image-text-to-text, visual-question-answering, image-to-text |
| Extracting text from documents/scans (OCR) | image-to-text (most OCR models live here), optical-character-recognition |
| Answering questions from documents/knowledge (LLM) | text-generation, text2text-generation, question-answering |
| Detecting objects/symbols in diagrams | object-detection, zero-shot-object-detection, image-segmentation, keypoint-detection |
| Document-specific Q&A | document-question-answering |
| Document layout / table structure understanding | image-segmentation (layout models often use this), table-question-answering |
| Embeddings (search/matching, ColPali-style doc-image retrieval) | feature-extraction, sentence-similarity, visual-document-retrieval |

### Strategy B — keyword search on model name/description/tags

Because tagging is inconsistent, also run a text search (`search=` query param)
for these keywords, independent of pipeline tag, and pull in anything matching
that isn't already caught by Strategy A:

`OCR, VLM, vision-language, document, layout, table extraction, table structure,
diagram, P&ID, chart, handwriting, receipt, invoice, form understanding,
document AI, docQA, grounding, bounding box`

### Known model families (context — do NOT hardcode; used to sanity-check)

- **VLM / document-diagram:** Qwen2-VL / Qwen2.5-VL, InternVL, LLaVA, Florence-2, GLM-4V, DeepSeek-VL/VL2, MiniCPM-V, Idefics, SmolVLM, Pixtral
- **OCR-specific:** TrOCR, GOT-OCR2.0, Nougat, PaddleOCR-based, dots.ocr, DeepSeek-OCR, olmOCR
- **Document structure / layout / forms:** LayoutLM/v2/v3, Donut, Pix2Struct, Table Transformer
- **Document-image retrieval/embeddings:** ColPali, ColQwen

Ask before adding new categories — flag to the team lead first; don't silently
expand scope.

## 5. What data to capture for each model

| Field | Where it comes from | Why |
| --- | --- | --- |
| Model name / ID | HF API `id` | To identify it |
| Author/organization | HF API `author` | Trust signal |
| Pipeline/task tag | HF API `pipeline_tag` | Which category it belongs to |
| Created date | HF API `createdAt` | Is it actually new |
| Last modified date | HF API `lastModified` | Significant updates without being "new" |
| Downloads | HF API `downloads` | Popularity/adoption |
| Likes | HF API `likes` | Community interest |
| License | HF API `cardData.license` or tags | We can only use usable/open licenses — flag if unclear |
| Model size / parameter count | Model name (e.g. "7B") or README — extract if easy | What we're evaluating |
| Link to model page | `https://huggingface.co/{model_id}` | Click through |
| Short description | First few lines of the README/card, if available | Quick context |

## 6. The screen (dashboard) — what it must show and do

- A table or card-grid view — one row/card per model
- Filter controls: by category (VLM/LLM/OCR/etc.), and "new this week" vs "all tracked"
- Sort: by newest, by downloads, by likes
- A "NEW" badge/highlight on any model first seen in the last 7 days — **the single most important feature**
- A search box (by model name/author)
- Clicking a model opens its Hugging Face page in a new tab (link out; don't rebuild HF's page)
- Runs on a schedule (Section 7) — always reasonably fresh; a manual "refresh now" is fine

Keep the UI simple. Internal tool — prioritize the data being correct and useful
over visual polish.

## 7. How it should run (behind the scenes)

A scheduled job runs automatically (once a day) that:

1. Calls the Hugging Face Hub API for models matching our tracked pipeline tags,
   sorted by creation date (newest first)
2. Compares results against what we already have stored (the diff/delta) to figure
   out what's genuinely new since the last run
3. Saves all of it (new and existing) into a simple database

The dashboard reads from that database — it does **not** call Hugging Face
directly every time someone opens the page. This keeps the dashboard fast and
available even if Hugging Face is slow or down.

## 8. Tech stack (use our standard stack — matches other INTICS projects)

**Backend: Java + Spring Boot**

- Use Spring's `RestTemplate` or `WebClient` to call the Hugging Face Hub API
  (`https://huggingface.co/api/models`) — a plain REST API returning JSON. There
  is no official Java SDK for Hugging Face, so call the REST endpoints directly.
- Use Spring's `@Scheduled` annotation to run the daily fetch job automatically,
  inside the Spring Boot application (no external cron needed).
- Expose a small set of REST endpoints (e.g. `GET /api/models?category=VLM&sort=newest`,
  `GET /api/models/new`) — standard `@RestController` pattern.

**Database: PostgreSQL**

- Tables hold the tracked models (all fields from Section 5), plus a "first seen
  by us" timestamp so we can correctly compute "new since last check."
- Use Spring Data JPA (repository pattern); avoid hand-written SQL unless a query
  genuinely needs it.

**Frontend: Vaadin (runs inside the Spring Boot application)**

- Build the dashboard with [Vaadin](https://vaadin.com), whose views run **inside
  the same Spring Boot application** as the backend — there is no separate
  frontend server and no separate JavaScript/Node build.
- **This collapses the original two-app architecture (Spring Boot backend +
  standalone Next.js frontend) into a single Java application** — one process, one
  deployment, one language. It replaces the earlier Next.js plan.
- Vaadin views get their data by calling the Spring `@Service` beans **directly,
  in-JVM** (type-safe, no HTTP round-trip to our own app). The REST endpoints
  above are still exposed and maintained — for external/future consumers,
  automation, and debugging — but the UI does not need them to render.
- Keep components simple: a filter bar, a sortable table/card grid, a "NEW" badge,
  a search input. No heavy state-management libraries for a tool this size.

**Do not over-engineer this.** No Kubernetes, no fancy auth (internal tool behind
existing network access), no mobile app, no microservices — a **single Spring Boot
application** (backend + scheduled job + embedded Vaadin UI) plus one PostgreSQL
database is enough.

## 9. Step-by-step build plan (do these in order)

1. Explore the Hugging Face Hub API manually first (read the docs; call it by hand once).
2. Write a small script that fetches models for ONE tag (e.g., image-text-to-text) and prints them.
3. Expand to all tracked tags (Section 4), still printing.
4. Design the database table(s) (Section 5 fields); create the table.
5. Write "save to database" logic — insert new, update changed.
6. Write "what's new since last run" logic — mark anything not previously seen as new.
7. Set up the scheduled job (once a day).
8. Build the dashboard — start with a plain table of everything, no filters.
9. Add filters, sorting, search, and the "NEW" badge (Section 6).
10. Test for a full week — confirm "new" is identified correctly and doesn't reappear as new daily.
11. Demo to the team before calling it "done."

## 10. Definition of Done (acceptance criteria)

- [ ] Automatically checks Hugging Face at least once per day, unattended.
- [ ] Only tracks the categories in Section 4 (not everything on Hugging Face).
- [ ] Every model shown has all Section 5 fields (or a clear "not available").
- [ ] The dashboard clearly marks which models are new in the last 7 days.
- [ ] Filtering by category and sorting by newest/downloads/likes both work.
- [ ] The dashboard loads without calling Hugging Face live (reads stored data).
- [ ] Has run correctly, unattended, for at least 7 consecutive days.
- [ ] A short README explains how to run/restart it.
