package io.github.godsarmy.mlhtmltranslator.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class MlKitHtmlTranslator implements AutoCloseable {

    private final HtmlTranslationOptions options;

    public MlKitHtmlTranslator() {
        this(HtmlTranslationOptions.builder().build());
    }

    public MlKitHtmlTranslator(@Nullable HtmlTranslationOptions options) {
        this.options = options == null ? HtmlTranslationOptions.builder().build() : options;
    }

    public void translateHtml(
            @NonNull String htmlBody,
            @NonNull String sourceLanguage,
            @NonNull String targetLanguage,
            @NonNull TranslationCallback callback) {
        if (htmlBody.trim().isEmpty()) {
            callback.onFailure(
                    new TranslationException(
                            TranslationErrorCode.INVALID_INPUT, "htmlBody must not be blank"));
            return;
        }

        if (sourceLanguage.trim().isEmpty() || targetLanguage.trim().isEmpty()) {
            callback.onFailure(
                    new TranslationException(
                            TranslationErrorCode.INVALID_INPUT,
                            "sourceLanguage and targetLanguage must not be blank"));
            return;
        }

        long startedAt = System.currentTimeMillis();
        callback.onSuccess(htmlBody);

        if (options.getTimingListener() != null) {
            options.getTimingListener()
                    .onTimingReady(
                            new TranslationTimingReport(startedAt, System.currentTimeMillis(), 0));
        }
    }

    @Override
    public void close() {
        // Placeholder. Real implementation will release ML resources.
    }
}
