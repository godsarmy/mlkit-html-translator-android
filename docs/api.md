# API Reference — mlkit-html-translator-android

## Main entry point

- `MlKitHtmlTranslator()`
- `MlKitHtmlTranslator(HtmlTranslationOptions options)`
- `translateHtml(String htmlBody, String sourceLanguage, String targetLanguage, TranslationCallback callback)`
- `explainHtml(String htmlBody)`
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

## Explain/diagnostics API

`explainHtml(...)` runs local preprocessing diagnostics and does not call the translation backend.

`ExplainHtmlResult` provides:

- `normalizedHtmlBody`
- `nodes` (`ExplainHtmlNode`)
- `chunks` (`ExplainHtmlChunk`)
- `protectedTags`
- mask flags (`maskUrls`, `maskPlaceholders`, `maskPaths`)
- derived totals (`totalNodeCount`, `totalChunkCount`)

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

## Translation behavior

- The library executes the translation pipeline for each request.
- It does not cache translated results internally.
- If caching is needed, implement it at the app/repository layer.

## Lifecycle boundary

This library does **not** expose ML Kit model lifecycle APIs.
Model download/delete/availability checks are app-owned responsibilities.
