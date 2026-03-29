# Architecture — mlkit-html-translator-android

## Overview

This library translates **HTML body content** while preserving structure and protected regions.

High-level flow:

1. Validate request (`MlKitHtmlTranslator`)
2. Parse/traverse DOM and collect eligible text nodes (`NodeCollector`)
3. Mask sensitive tokens (`TokenMasker`)
4. Build markerized chunks (`ChunkBuilder`, `SegmentMarkerCodec`)
5. Translate chunks via backend adapter (`MlTranslationAdapter`)
6. Map results back, unmask, and restore DOM (`ChunkResultMapper`)
7. Emit translated HTML + diagnostics/timing

---

## Package map

- `api/`
  - Public surface: `MlKitHtmlTranslator`, options, callback, error/timing models
- `core/`
  - DOM traversal, eligibility, orchestration, retries/fallbacks
- `batch/`
  - Segment markers, chunk creation, result mapping
- `mask/`
  - URL/placeholder/path token masking and restoration
- `backend/`
  - Translation backend contract (`MlTranslationAdapter`)

---

## Pipeline internals

### 1) API boundary

`MlKitHtmlTranslator.translateHtml(...)`:

- validates non-blank input/language params
- delegates to `HtmlBodyTranslationEngine`
- maps failures into `TranslationException` callback paths

### 2) DOM collection

`HtmlBodyTranslationEngine` parses with Jsoup and `NodeCollector` collects translatable text nodes.

Protected tags (default): `code`, `pre`, `script`, `style`, `kbd`, `samp`, `var`.

### 3) Token safety

`TokenMasker` shields tokens before translation:

- URLs
- placeholders (`%s`, `${name}`, `{name}`)
- paths
- plus additional detectors (email/shell-flag support in registry)

After translation, placeholders are restored exactly.

### 4) Chunking and markers

`ChunkBuilder` groups node text into bounded chunks (`maxChunkChars`) and wraps each segment with deterministic markers via `SegmentMarkerCodec`.

`ChunkResultMapper` decodes translated payload and maps segment outputs back to original node indexes.

### 5) Translation orchestration

`HtmlBodyTranslationEngine.translateChunks(...)` runs chunk tasks with bounded parallelism and timeout.

- default max in-flight chunks: `2`
- default per-chunk timeout: `10_000ms`
- cancellation checked before/after key stages

Failure behavior depends on `FailurePolicy`:

- `FAIL_FAST`: bubble first translation failure
- `BEST_EFFORT`: fallback to original text for failed nodes

### 6) Reconstruction + diagnostics

Translated text is unmasked and reinserted into DOM while preserving whitespace boundaries.

Diagnostics include: total/translated/failed nodes, retries, chunk count, and stage durations.

---

## Architecture boundaries

This library intentionally does **not** manage ML Kit model lifecycle.

App layer owns:

- model download/delete
- model availability checks
- UX around download progress/error states

Library owns:

- HTML-safe translation pipeline
- fallback/cancellation behavior
- diagnostics/timing

---

## Extension points

- Swap backend by providing custom `MlTranslationAdapter`
- Tune behavior via `HtmlTranslationOptions` (masking, chunking, failure policy, protected tags)

---

## Parity with `mlkit-markdown-translator-android`

This project intentionally aligns with markdown-translator ergonomics and boundaries.

### API shape parity

- main class + default/options constructors
- callback-based `translate*` entrypoint
- typed exception + error code enum
- explicit `close()` lifecycle hook
- optional timing listener/report

### Architecture parity

- traversal/eligibility stage
- token protection stage
- chunking + marker mapping stage
- translation orchestration + fallback policy stage

### Boundary and operational parity

- library remains app-agnostic
- model lifecycle remains outside library API
- sample app demonstrates app-side model ownership
- Spotless formatting + unit tests + CI workflow + benchmark docs
