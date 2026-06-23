# ML Kit HTML Translator

[![JitPack](https://jitpack.io/v/godsarmy/mlkit-html-translator-android.svg)](https://jitpack.io/#godsarmy/mlkit-html-translator-android)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/android-minSdk%2024-brightgreen.svg)](library/build.gradle)

<img src="docs/project-icon.svg" alt="ML Kit HTML Translator icon" width="96" />

Translate HTML body content in Android apps with Google ML Kit while preserving document structure.

This library prepares HTML body fragments for translation, sends only translatable text through ML Kit, and rebuilds the body with tags, attributes, links, protected elements, spacing, and masked tokens preserved as much as possible. It is designed for apps that need local, ML Kit-powered translation without flattening HTML into plain text.

## Why use it?

- **HTML-aware translation** — translates human-readable text while preserving tags and attributes.
- **Built on Google ML Kit Translate** — uses on-device translation models managed by your app.
- **Small Java API** — simple callback-based integration for Android projects.
- **DOM-based pipeline** — uses jsoup parsing instead of regex-only HTML handling.
- **Diagnostics included** — inspect node collection, masking, and chunking with `explainHtml(...)`.
- **Sample app included** — see a practical integration with model management, rendered preview, and side-by-side comparison.

Requirements: Android `minSdk 24`, Java 17, and an app-level ML Kit model download flow.

Current release: **0.8.1**

## Example app

<img src="screenshot.jpg" alt="Screenshot of the example app" width="360" />

The `sample/` app shows how to build a complete HTML translation experience around the library. It demonstrates:

- HTML input and translated output
- source and target language selection
- ML Kit model download, delete, and availability checks
- raw HTML and rendered WebView preview modes
- side-by-side original/translated comparison
- advanced chunking, masking, and failure-policy controls
- structured error output and optional timing report rendering

Build it from the repository root:

```bash
./gradlew :sample:assembleDebug
```

Generated APK:

```text
sample/build/outputs/apk/debug/sample-debug.apk
```

Install to a connected device:

```bash
adb install -r sample/build/outputs/apk/debug/sample-debug.apk
```

## Installation

### JitPack

JitPack page: https://jitpack.io/#godsarmy/mlkit-html-translator-android

Groovy:

```gradle
repositories {
    google()
    mavenCentral()
    maven { url "https://jitpack.io" }
}

dependencies {
    implementation "com.github.godsarmy.mlkit-html-translator-android:library:0.8.1"
}
```

Kotlin DSL:

```kotlin
repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.godsarmy.mlkit-html-translator-android:library:0.8.1")
}
```

### Local module

If you are working with this repository as part of a multi-module build:

```gradle
dependencies {
    implementation project(":library")
}
```

## Quick start

```java
import io.github.godsarmy.mlhtmltranslator.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.MlKitHtmlTranslator;
import io.github.godsarmy.mlhtmltranslator.api.TranslationCallback;
import io.github.godsarmy.mlhtmltranslator.api.TranslationException;

HtmlTranslationOptions options = HtmlTranslationOptions.builder()
        .setMaxChunkChars(3000)
        .setFailurePolicy(HtmlTranslationOptions.FailurePolicy.BEST_EFFORT)
        .setMaskUrls(true)
        .setMaskPlaceholders(true)
        .setMaskPaths(true)
        .setOutputDirectionMode(HtmlTranslationOptions.OutputDirectionMode.AUTO_FROM_TARGET_LANGUAGE)
        .build();

MlKitHtmlTranslator translator = new MlKitHtmlTranslator(context, options);

translator.translateHtml(
        "<p>Hello <a href=\"https://example.com\">world</a></p>",
        "en",
        "es",
        new TranslationCallback() {
            @Override
            public void onSuccess(String translatedHtml) {
                // Render or store the translated HTML.
            }

            @Override
            public void onFailure(TranslationException error) {
                // Show an error state or request a missing language model.
            }
        });
```

Create one translator per screen/controller scope and call `close()` when that scope is destroyed.

## Handling ML Kit models

`translateHtml(...)` does not automatically download missing language models. Manage model lifecycle in your app with ML Kit APIs such as:

- `RemoteModelManager`
- `TranslateRemoteModel`
- `DownloadConditions`

Handle missing models explicitly:

```java
import io.github.godsarmy.mlhtmltranslator.api.TranslationErrorCode;
import io.github.godsarmy.mlhtmltranslator.api.TranslationException;

if (error instanceof TranslationException
        && ((TranslationException) error).getCode() == TranslationErrorCode.MODEL_UNAVAILABLE) {
    // Ask the user to download the required ML Kit language model.
}
```

If your app downloads models, include internet permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Inspect HTML processing

Use `explainHtml(...)` to understand how the library normalizes, masks, and chunks HTML before translation. This is useful when testing your own HTML fixtures or debugging edge cases.

```java
import android.util.Log;
import io.github.godsarmy.mlhtmltranslator.MlKitHtmlTranslator;
import io.github.godsarmy.mlhtmltranslator.api.ExplainHtmlChunk;
import io.github.godsarmy.mlhtmltranslator.api.ExplainHtmlResult;

MlKitHtmlTranslator translator = new MlKitHtmlTranslator(options);
ExplainHtmlResult explain = translator.explainHtml(htmlBody);

for (ExplainHtmlChunk chunk : explain.getChunks()) {
    Log.d("MLHTML", "chunk #" + chunk.getIndex() + " payload=" + chunk.getPayload());
}

Log.d("MLHTML", "nodes=" + explain.getTotalNodeCount()
        + " chunks=" + explain.getTotalChunkCount());
```

`explainHtml(...)` runs local preparation diagnostics only; it does not call ML Kit translation.

## Version notes

The library currently defaults to:

- `com.google.mlkit:translate:17.0.3`
- `org.jsoup:jsoup:1.17.2`

When integrating through JitPack, you can pin a newer ML Kit or jsoup version in your app if needed:

```gradle
dependencies {
    implementation "com.github.godsarmy.mlkit-html-translator-android:library:0.8.1"
    implementation "com.google.mlkit:translate:17.0.4"
    implementation "org.jsoup:jsoup:1.17.2"
}
```

For local-module development, override versions in root `gradle.properties`:

```properties
mlkitTranslateVersion=17.0.4
jsoupVersion=1.17.2
```

## Documentation

- Public API reference: [`docs/api.md`](docs/api.md)
- Integration guide: [`docs/integration.md`](docs/integration.md)
- Architecture and pipeline notes: [`docs/architecture.md`](docs/architecture.md)
- Benchmark summary: [`docs/bench-report.md`](docs/bench-report.md)

Repository layout:

- `library/` — reusable Android library module
- `sample/` — example Android app
- `docs/` — API, architecture, integration, and benchmark notes

## HTML compatibility notes

The library is designed to preserve HTML structure during translation, but translation engines can still change punctuation, spacing, or marker-like text in plain-text regions. Validate your own HTML corpus if your app depends on strict round-tripping.

Known practical limits:

- input is treated as HTML body content, not a full browser document shell
- protected tags should be configured for app-specific elements that must not be translated
- RTL layout metadata is opt-in with `OutputDirectionMode.AUTO_FROM_TARGET_LANGUAGE`, `FORCE_RTL`, or `FORCE_LTR`; the default `PRESERVE` mode does not add `dir` or `lang`
- very large or deeply nested documents may need app-specific chunk-size validation
- translation can mutate structural marker text, so single-node chunks use a marker-free path for reliability
- URL, placeholder, and path masking reduce accidental translation of machine-readable tokens but cannot cover every app-specific token format

Golden-test fixtures live under `library/src/test/resources/fixtures`.

## License

Apache License 2.0. See [`LICENSE`](LICENSE).
