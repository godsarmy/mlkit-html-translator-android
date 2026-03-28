# Integration Guide — app-owned model lifecycle

This library translates HTML body content, but **does not** own ML Kit model lifecycle.

## Lifecycle boundary

- App layer is responsible for:
  - downloading language models
  - checking model availability
  - deleting language models
- Library is responsible for:
  - HTML traversal, masking, chunking, orchestration, and output reconstruction

## Precondition

Before calling `MlKitHtmlTranslator.translateHtml(...)`, ensure source/target language models are available in app code.

If required models are missing/unavailable, app code should surface typed error mapping with:

- `TranslationErrorCode.MODEL_UNAVAILABLE`

## Error mapping contract (recommended)

- Missing model / download required / remote model unavailable:
  - map to `MODEL_UNAVAILABLE`
- Runtime translation failures:
  - map to `TRANSLATION_FAILED`
- User cancellation / interrupted operations:
  - map to `CANCELLED`

## Recommended app architecture

Use this layering:

1. **Activity/Fragment**
   - binds UI events and displays output/errors
2. **ViewModel**
   - owns screen state and coordinates use-cases
3. **Repository**
   - wraps `MlKitHtmlTranslator` calls
4. **Model manager utility (app layer)**
   - wraps ML Kit `RemoteModelManager`, `TranslateRemoteModel`, `DownloadConditions`

## Example flow

1. UI requests translation.
2. ViewModel asks model manager to ensure model availability.
3. Repository runs `MlKitHtmlTranslator.translateHtml(...)`.
4. ViewModel emits translated HTML or typed `TranslationException`.

## Important

- Keep model lifecycle APIs out of the library public interface.
- Keep model management in app code so each product can define its own UX and caching policy.
