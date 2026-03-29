package io.github.godsarmy.mlhtmltranslator.api;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.godsarmy.mlhtmltranslator.backend.IdentityTranslationAdapter;
import io.github.godsarmy.mlhtmltranslator.backend.MlKitTranslationAdapter;
import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import io.github.godsarmy.mlhtmltranslator.core.HtmlBodyTranslationEngine;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MlKitHtmlTranslator implements AutoCloseable {

    private final HtmlTranslationOptions options;
    private final HtmlBodyTranslationEngine translationEngine;
    private final MlTranslationAdapter translationAdapter;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public MlKitHtmlTranslator() {
        this(HtmlTranslationOptions.builder().build(), new IdentityTranslationAdapter());
    }

    public MlKitHtmlTranslator(@Nullable HtmlTranslationOptions options) {
        this(options, new IdentityTranslationAdapter());
    }

    public MlKitHtmlTranslator(@NonNull Context context, @Nullable HtmlTranslationOptions options) {
        this(options, new MlKitTranslationAdapter(context));
    }

    MlKitHtmlTranslator(
            @Nullable HtmlTranslationOptions options,
            @NonNull MlTranslationAdapter translationAdapter) {
        this.options = options == null ? HtmlTranslationOptions.builder().build() : options;
        this.translationEngine = new HtmlBodyTranslationEngine();
        this.translationAdapter = translationAdapter;
    }

    /**
     * Translates HTML body content from source language to target language.
     *
     * <p>Current bootstrap behavior is synchronous and invokes callback methods on the same thread
     * that calls this method. Future asynchronous internals must preserve a consistent documented
     * callback contract.
     */
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

        try {
            HtmlBodyTranslationEngine.PipelineResult pipelineResult =
                    translationEngine.translateHtmlBodyWithReport(
                            htmlBody,
                            sourceLanguage,
                            targetLanguage,
                            options,
                            translationAdapter,
                            cancelled);
            String translatedHtml = pipelineResult.getTranslatedHtml();
            callback.onSuccess(translatedHtml);

            if (options.getTimingListener() != null) {
                HtmlBodyTranslationEngine.Diagnostics diagnostics = pipelineResult.getDiagnostics();
                options.getTimingListener()
                        .onTimingReady(
                                new TranslationTimingReport(
                                        startedAt,
                                        System.currentTimeMillis(),
                                        diagnostics.getChunkCount(),
                                        diagnostics.getTotalNodes(),
                                        diagnostics.getTranslatedNodes(),
                                        diagnostics.getFailedNodes(),
                                        diagnostics.getRetryCount()));
            }
        } catch (TranslationException translationException) {
            callback.onFailure(translationException);
        } catch (RuntimeException runtimeException) {
            callback.onFailure(
                    new TranslationException(
                            TranslationErrorCode.INTERNAL_ERROR,
                            "Unexpected runtime failure during translation",
                            runtimeException));
        }
    }

    @Override
    public void close() {
        cancelled.set(true);
        if (translationAdapter instanceof AutoCloseable) {
            try {
                ((AutoCloseable) translationAdapter).close();
            } catch (Exception ignored) {
                // no-op
            }
        }
    }
}
