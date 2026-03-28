# API Reference — mlkit-html-translator-android

## Main entry point

- `MlKitHtmlTranslator()`
- `MlKitHtmlTranslator(HtmlTranslationOptions options)`
- `translateHtml(String htmlBody, String sourceLanguage, String targetLanguage, TranslationCallback callback)`
- `close()`

## Options

`HtmlTranslationOptions` is immutable and built via `HtmlTranslationOptions.Builder`.

Current options:

- protected tags set
- max chunk chars
- failure policy (`FAIL_FAST`, `BEST_EFFORT`)
- token masking flags (URLs/placeholders/paths)
- optional timing listener

## Callback contract

- `onSuccess(String translatedHtml)`
- `onFailure(TranslationException exception)`

## Threading and callback behavior

- Current implementation invokes callbacks on the caller thread.
- Avoid heavy/blocking work directly in callback methods.

## Error contract

`TranslationException` exposes typed `TranslationErrorCode`:

- `INVALID_INPUT`
- `TRANSLATOR_UNAVAILABLE`
- `MODEL_UNAVAILABLE`
- `TRANSLATION_FAILED`
- `CANCELLED`
- `INTERNAL_ERROR`

## Timing report

When timing is enabled via `TranslationTimingListener`, `TranslationTimingReport` provides:

- `startedAtEpochMs`
- `completedAtEpochMs`
- `durationMs`
- `chunkCount`
- `totalNodes`
- `translatedNodes`
- `failedNodes`
- `retryCount`

## Cache behavior

- In-memory LRU cache is enabled by default.
- Key is hash of: `htmlBody + sourceLanguage + targetLanguage + optionsVersion`.
- Cache invalidates automatically when options version changes.

## Lifecycle boundary

This library does **not** expose ML Kit model lifecycle APIs.
Model download/delete/availability checks are app-owned responsibilities.
