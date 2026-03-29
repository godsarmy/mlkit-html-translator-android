# mlkit-html-translator-android

<p align="center">
  <img src="docs/project-icon.svg" alt="ML HTML Translator icon" width="120" height="120" />
</p>

Android library for translating **HTML body content** with an ML-backed pipeline while preserving structure, attributes, links, and protected tags.

## Quickstart

### 1) Create translator

```java
HtmlTranslationOptions options = HtmlTranslationOptions.builder()
        .setMaxChunkChars(3000)
        .setFailurePolicy(HtmlTranslationOptions.FailurePolicy.BEST_EFFORT)
        .setMaskUrls(true)
        .setMaskPlaceholders(true)
        .setMaskPaths(true)
        .build();

MlKitHtmlTranslator translator = new MlKitHtmlTranslator(options);
```

### 2) Translate HTML body

```java
translator.translateHtml(
        "<p>Hello <a href=\"https://example.com\">world</a></p>",
        "en",
        "es",
        new TranslationCallback() {
            @Override
            public void onSuccess(@NonNull String translatedHtml) {
                // Render translated HTML
            }

            @Override
            public void onFailure(@NonNull TranslationException exception) {
                // Handle TranslationErrorCode
            }
        });
```

### 3) Close when done

```java
translator.close();
```

### Optional: explain preprocessing without translation

```java
ExplainHtmlResult explain = translator.explainHtml(
        "<p>Hello <a href=\"https://example.com\">world</a></p>");

for (ExplainHtmlChunk chunk : explain.getChunks()) {
    // inspect markerized payload, node indexes, and plain-text length
}
```

`explainHtml(...)` runs parse/collection/masking/chunking diagnostics only and does not call ML translation.

## App-owned model lifecycle

This library intentionally **does not** expose model download/list/delete APIs.

App code should manage model lifecycle (download/check/delete) via ML Kit APIs, then call `translateHtml(...)` once required models are available.

## Sample app

The `sample/` app demonstrates:

- source/target language selectors
- input/output HTML preview
- sample assets for manual-like and mixed code/prose content
- app-layer model operations (download/delete/check)
- structured error output (`TranslationErrorCode`)
- optional timing report rendering

## Documentation

- API reference: `docs/api.md`
- Integration guide: `docs/integration.md`
- Migration notes: `docs/migration.md`
- Benchmark summary: `docs/bench-report.md`
- Maintainer parity notes: `docs/maintainers.md`
