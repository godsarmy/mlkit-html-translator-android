# Migration notes from app-local HTML translators

If your app already has local HTML translation logic, migrate in this order.

## 1) Replace direct translation calls

- Move translation entrypoint to `MlKitHtmlTranslator.translateHtml(...)`.
- Keep existing UI flow unchanged first.

## 2) Keep model lifecycle in app layer

- Keep (or add) app-owned model manager utility.
- Ensure model availability before each translation request.
- Map missing-model cases to `TranslationErrorCode.MODEL_UNAVAILABLE`.

## 3) Remove regex-only HTML rewriting

- Let library pipeline handle:
  - DOM traversal
  - protected tag skipping
  - token masking/unmasking
  - chunking and re-mapping

## 4) Migrate error handling

- Replace generic exception handling with typed `TranslationErrorCode` handling.
- Distinguish `BEST_EFFORT` and `FAIL_FAST` behavior in UI.

## 5) Validate behavior with fixtures

- long manual-like content
- mixed prose and code blocks
- link-heavy pages and inline code
- multilingual samples

## 6) Clean up old code

- remove duplicated app-local parser/chunker/token masker code after rollout stability
- retain only app-owned model lifecycle utilities and orchestration UI/state code
