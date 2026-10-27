# Known model families (reference)

Reference material from Section 4 of the project spec — examples of the kinds of
models this tracker should surface. **This list is not used by the code** (the
tracker matches on pipeline tags + keywords, see `application.yml`); it exists as a
human sanity-check: if the dashboard is catching new releases from these families
(and their competitors/successors), it's doing its job.

> ⚠️ **This list goes stale fast.** New families appear constantly. Don't hardcode
> it anywhere — use it only to eyeball whether the tracker is surfacing the right
> _kind_ of model.

## VLM / document–diagram understanding

Qwen2-VL / Qwen2.5-VL family · InternVL family · LLaVA family · Florence-2 ·
GLM-4V · DeepSeek-VL / DeepSeek-VL2 · MiniCPM-V · Idefics · SmolVLM · Pixtral

## OCR-specific

TrOCR · GOT-OCR2.0 · Nougat (dense/technical documents) · PaddleOCR-based models ·
dots.ocr · DeepSeek-OCR · olmOCR

## Document structure / layout / forms

LayoutLM / LayoutLMv2 / LayoutLMv3 · Donut (OCR-free document understanding) ·
Pix2Struct · Table Transformer (table-structure recognition — directly relevant to
BOM/table extraction from drawings)

## Document-image retrieval / embeddings

ColPali · ColQwen (retrieval over scanned/document images — relevant to
Copilot-style search)

---

If a new release maps to one of these families — a new version, a competitor, or a
genuinely new approach — that's a strong signal it belongs on the dashboard even if
its Hugging Face tags are messy. If you notice the tracker consistently missing such
models, that's a cue to add a tag/keyword (see the README's "Add or remove tracked
tags/keywords" section) — flag it to the team lead first.
