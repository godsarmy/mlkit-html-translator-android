# HTML ML Translation Parser — Actionable Implementation Plan (Markdown-Lib Aligned)

Use this checklist to build a reusable Android library that translates HTML **body content** with ML while preserving structure, attributes, links, and protected tags — with a public interface aligned to `mlkit-markdown-translator-android`.

---

## Objective

- Build a production-ready HTML-body translation engine for Android.
- Keep the library interface and developer ergonomics similar to `mlkit-markdown-translator-android`.
- Translate user-visible prose only.
- Preserve HTML semantics and rendering integrity.

### Non-goals

- translating JavaScript/CSS
- changing DOM structure intentionally
- rewriting URLs/attributes
- adding app-specific UI logic inside the library
- managing ML Kit language pack lifecycle inside the library

---

## Success Criteria

- [x] Output HTML renders with same structure as input.
- [x] Protected zones (`code`, `pre`, `script`, `style`, etc.) remain unchanged.
- [x] Link attributes (`href`, `src`) remain unchanged.
- [ ] Translation quality is acceptable on long manual pages.
- [x] Batching reduces calls vs per-node translation.
- [x] Library API is app-agnostic, documented, and test-covered.
- [x] API shape is comparable to markdown lib (`translator + options + callback + error codes + close`).
- [x] Example app demonstrates translation usage and **app-owned** language model management.

---

## Target API parity with `mlkit-markdown-translator-android`

Mirror this style (names adapted for HTML):

- [x] Main class: `MlKitHtmlTranslator`
  - [x] default constructor
  - [x] constructor with `HtmlTranslationOptions`
  - [x] `translateHtml(String htmlBody, String sourceLanguage, String targetLanguage, TranslationCallback callback)`
  - [x] `close()`
- [x] Options model: immutable `HtmlTranslationOptions` + `Builder`
- [x] Callback contract: `TranslationCallback` with success/failure
- [x] Typed error model: `TranslationException` + `TranslationErrorCode`
- [x] Optional timing hooks compatible with markdown style:
  - [x] `TranslationTimingListener`
  - [x] `TranslationTimingReport`

Explicit exclusion:

- [x] No `ensureLanguageModelDownloaded`, `getDownloadedLanguagePacks`, or `deleteLanguagePack` in library API.

---

## Phase 0 — Project bootstrap

- [x] Create/confirm module layout similar to markdown project:
  - [x] `library/` (core translator library)
  - [x] `sample/` (example app)
  - [x] `docs/` (API + integration docs)
- [x] Add dependencies:
  - [x] HTML parser (Jsoup)
  - [x] ML translation backend dependency (or adapter interface)
  - [x] Unit test framework
- [x] Configure CI tasks:
  - [x] lint
  - [x] unit tests
  - [x] formatting

Deliverable:
- [x] Buildable skeleton with aligned API placeholders

---

## Phase 1 — Public API design (aligned)

- [x] Define `MlKitHtmlTranslator` API matching markdown lib interaction model.
- [x] Define `HtmlTranslationOptions` with builder-only construction.
- [x] Define callback + error contracts:
  - [x] success returns translated HTML string
  - [x] failure returns `TranslationException` with `TranslationErrorCode`
- [x] Define options fields (keep concise and practical):
  - [x] protected tags set
  - [x] max chunk chars
  - [x] failure policy (`FAIL_FAST` / `BEST_EFFORT`)
  - [x] token masking toggles (URLs/placeholders/paths)
  - [x] optional timing listener
- [x] Document thread/callback behavior clearly (and keep consistent).

Deliverable:
- [x] API + options + callback/error contracts finalized in `docs/api.md`

---

## Phase 2 — DOM traversal and eligibility engine

- [x] Parse input as body fragment.
- [x] Build text-node collector in DOM order.
- [x] Implement `isTranslatableNode` rules:
  - [x] non-blank text node
  - [x] not under protected ancestor tags
  - [ ] not under ignored attributes/flags (if configured)
- [x] Add protected tag defaults:
  - [x] `code`
  - [x] `pre`
  - [x] `script`
  - [x] `style`
  - [x] `kbd`
  - [x] `samp`
  - [x] `var`
- [x] Preserve whitespace boundaries per node.

Deliverable:
- [x] Deterministic list of eligible text nodes

---

## Phase 3 — Token masking subsystem

- [x] Implement token detectors:
  - [x] URLs
  - [x] emails
  - [x] placeholders (`%s`, `{name}`, `${x}`)
  - [x] shell flags (`--flag`, `-a`)
  - [x] filesystem-like paths
- [x] Replace each token with stable placeholder (`@@P0@@`, `@@P1@@`, ...).
- [x] Store reversible mapping per node/chunk.
- [x] Implement unmask restoration after translation.
- [x] Add collision-safe placeholder generation.

Deliverable:
- [x] Reversible masking/unmasking with unit tests

---

## Phase 4 — Batching/chunking engine

- [x] Define chunk constraints:
  - [x] `maxChunkChars` (default e.g. 3000)
  - [x] max units per chunk (optional)
- [x] Join node texts with robust segment markers:
  - [x] marker format includes random/session prefix
  - [x] marker parser tolerant to whitespace
- [x] Build chunk builder that preserves node order.
- [x] Implement split/restore parser with marker count validation.

Deliverable:
- [x] Working chunk assembly + deterministic re-mapping

---

## Phase 5 — Translation orchestration

- [x] Implement end-to-end pipeline:
  - [x] collect eligible nodes
  - [x] mask tokens
  - [x] batch nodes
  - [x] translate chunks
  - [x] split chunk output
  - [x] unmask + whitespace restore
  - [x] write back text nodes
  - [x] serialize body HTML
- [x] Add concurrency controls:
  - [x] max in-flight chunks (default 2)
  - [x] cancellation support
  - [x] thread-safe aggregation
- [x] Add timeout handling per chunk.

Deliverable:
- [x] End-to-end translation pipeline returns translated HTML

---

## Phase 6 — Fallback and resilience policy

- [x] If marker parse fails:
  - [x] retry with smaller chunks
  - [x] fallback to per-node translation for failing chunk
- [x] If translation fails:
  - [x] `BEST_EFFORT`: keep original for failed parts
  - [x] `FAIL_FAST`: abort and return error
- [x] Emit diagnostics metadata:
  - [x] total nodes
  - [x] translated nodes
  - [x] failed nodes
  - [x] retry count

Deliverable:
- [x] Robust error handling with predictable behavior

---

## Phase 7 — App-owned ML Kit model lifecycle contract (no library lifecycle APIs)

- [x] Document that language model lifecycle is managed by app code via ML Kit APIs (`RemoteModelManager`, `TranslateRemoteModel`, `DownloadConditions`).
- [x] Document library precondition: required models should be available before translation call.
- [x] Standardize error mapping for missing/unavailable model (typed `TranslationErrorCode`).
- [x] Add integration recipe matching markdown project architecture:
  - [x] Activity/Fragment
  - [x] ViewModel
  - [x] Repository wrapping `MlKitHtmlTranslator`
  - [x] Model manager utility in app layer

Deliverable:
- [x] Clear lifecycle boundary and integration contract published

---

## Phase 8 — Caching strategy

- [x] Add in-memory cache key:
  - [x] hash(`htmlBody + sourceLang + targetLang + optionsVersion`)
- [x] Optional persistent cache interface (pluggable).
- [x] Add cache size and eviction policy.
- [x] Ensure cache invalidates on options/version change.

Deliverable:
- [x] Fast repeat translations via cache hits

---

## Phase 9 — Example app (required)

Build a `sample/` app similar to markdown sample and focused on real usage.

- [x] Provide source/target language selectors.
- [x] Provide input HTML and translated output preview.
- [x] Include sample HTML assets (manual-like docs, mixed code/prose).
- [x] Demonstrate model operations in app layer (not library):
  - [x] download model
  - [x] delete model
  - [x] check model availability
- [x] Demonstrate translation call through `MlKitHtmlTranslator`.
- [x] Display structured errors by `TranslationErrorCode`.
- [x] Optionally display timing report when enabled.

Deliverable:
- [x] Runnable demo app proving integration pattern and lifecycle separation

---

## Phase 10 — Test plan (must-have)

### Unit tests
- [x] Protected tags unchanged (`code/pre/script/style`).
- [x] Anchor text translated; `href` unchanged.
- [x] Nested tags preserve structure.
- [x] Token masking roundtrip correctness.
- [x] Chunk marker split/rejoin correctness.
- [x] Whitespace preservation around inline elements.
- [x] Error code mapping correctness.

### Fixture tests
- [x] Long manual-like article with headings/lists/tables.
- [x] Mixed prose + code blocks.
- [x] Heavy links and inline code.
- [x] Multilingual sample inputs.

### Failure tests
- [x] Chunk translation failure with `BEST_EFFORT`.
- [x] Marker mismatch fallback path.
- [x] Cancellation mid-flight.
- [x] Missing model precondition error surface.

Deliverable:
- [x] Automated suite with high confidence on correctness

---

## Phase 11 — Performance validation

- [x] Measure parse/walk time.
- [x] Measure masking/chunking time.
- [x] Measure translation time by chunk count/size.
- [x] Compare:
  - [x] per-node baseline
  - [x] chunked strategy
- [x] Tune defaults:
  - [x] `maxChunkChars`
  - [x] in-flight chunk count

Acceptance targets (adjust per product needs):
- [x] at least 40% fewer translation calls vs per-node baseline
- [x] no OOM on large manual pages in test corpus
- [x] stable runtime for repeated translations with cache

Deliverable:
- [x] Bench report + tuned defaults

---

## Phase 12 — Documentation output

- [x] `README.md` quickstart for library + sample.
- [x] `docs/api.md` with aligned API contracts.
- [x] `docs/integration.md` with app-owned model lifecycle steps.
- [x] Migration notes from app-local HTML translators.
- [x] “How this mirrors markdown translator” section for maintainers.

Deliverable:
- [x] Copy-paste-ready integration docs with lifecycle boundary clearly stated

---

## Suggested package structure

- [x] `library/src/main/java/.../api/`
  - [x] `MlKitHtmlTranslator`
  - [x] `HtmlTranslationOptions`
  - [x] `TranslationCallback`
  - [x] `TranslationException`
  - [x] `TranslationErrorCode`
  - [x] `TranslationTimingListener`
  - [x] `TranslationTimingReport`
- [x] `library/src/main/java/.../core/`
  - [x] `HtmlBodyTranslationEngine`
  - [x] `NodeCollector`
  - [x] `ProtectedTagPolicy`
- [x] `library/src/main/java/.../batch/`
  - [x] `ChunkBuilder`
  - [x] `SegmentMarkerCodec`
  - [x] `ChunkResultMapper`
- [x] `library/src/main/java/.../mask/`
  - [x] `TokenMasker`
  - [x] `TokenPatternRegistry`
- [x] `library/src/main/java/.../backend/`
  - [x] `MlTranslationAdapter`
- [x] `library/src/main/java/.../cache/`
  - [x] `TranslationCache`
- [x] `sample/` (demo app with app-side model manager usage)
- [x] `docs/` (API + integration)

---

## Recommended rollout strategy

- [ ] Ship library as internal alpha.
- [ ] Integrate behind feature flag in one screen first.
- [ ] Collect crash/perf telemetry.
- [ ] Expand to all HTML translation surfaces.
- [ ] Remove duplicated app-local logic after stable rollout.

---

## Final completion checklist

- [x] API finalized and documented (aligned to markdown translator style)
- [x] Protected-tag skipping verified
- [x] Links/attributes/structure preservation verified
- [x] Chunk batching implemented and benchmarked
- [x] Fallback behavior verified
- [x] Library does **not** manage model lifecycle
- [x] Sample app demonstrates app-managed model lifecycle + library translation usage
- [x] Test suite green in CI
- [x] Integration docs published
