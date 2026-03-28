# API Draft — mlkit-html-translator-android

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

- Current bootstrap implementation invokes callbacks synchronously on the calling thread.
- Consumers should avoid heavy/blocking work directly in callback methods.
- Any future internal async execution will keep callback contract explicit in this document.

## Error contract

`TranslationException` exposes typed `TranslationErrorCode`:

- `INVALID_INPUT`
- `TRANSLATOR_UNAVAILABLE`
- `MODEL_UNAVAILABLE`
- `TRANSLATION_FAILED`
- `CANCELLED`
- `INTERNAL_ERROR`

Model lifecycle is app-owned. For missing/unavailable model scenarios, integrations should map errors to:

- `MODEL_UNAVAILABLE`

## Lifecycle boundary

This library does **not** expose ML Kit model lifecycle APIs.
Model download/delete/availability checks are owned by app code.
