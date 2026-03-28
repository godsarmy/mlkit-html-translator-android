package io.github.godsarmy.mlhtmltranslator.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.godsarmy.mlhtmltranslator.backend.IdentityTranslationAdapter;
import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import io.github.godsarmy.mlhtmltranslator.cache.InMemoryTranslationCache;
import io.github.godsarmy.mlhtmltranslator.cache.TranslationCache;
import io.github.godsarmy.mlhtmltranslator.cache.TranslationCacheKeyFactory;
import io.github.godsarmy.mlhtmltranslator.core.HtmlBodyTranslationEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MlKitHtmlTranslator implements AutoCloseable {

    private static final int DEFAULT_CACHE_SIZE = 256;

    private final HtmlTranslationOptions options;
    private final HtmlBodyTranslationEngine translationEngine;
    private final MlTranslationAdapter translationAdapter;
    private final TranslationCache translationCache;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public MlKitHtmlTranslator() {
        this(
                HtmlTranslationOptions.builder().build(),
                new IdentityTranslationAdapter(),
                new InMemoryTranslationCache(DEFAULT_CACHE_SIZE));
    }

    public MlKitHtmlTranslator(@Nullable HtmlTranslationOptions options) {
        this(
                options,
                new IdentityTranslationAdapter(),
                new InMemoryTranslationCache(DEFAULT_CACHE_SIZE));
    }

    MlKitHtmlTranslator(
            @Nullable HtmlTranslationOptions options,
            @NonNull MlTranslationAdapter translationAdapter) {
        this(options, translationAdapter, new InMemoryTranslationCache(DEFAULT_CACHE_SIZE));
    }

    MlKitHtmlTranslator(
            @Nullable HtmlTranslationOptions options,
            @NonNull MlTranslationAdapter translationAdapter,
            @NonNull TranslationCache translationCache) {
        this.options = options == null ? HtmlTranslationOptions.builder().build() : options;
        this.translationEngine = new HtmlBodyTranslationEngine();
        this.translationAdapter = translationAdapter;
        this.translationCache = translationCache;
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
        String cacheKey =
                TranslationCacheKeyFactory.create(
                        htmlBody, sourceLanguage, targetLanguage, optionsVersion());
        String cachedResult = translationCache.get(cacheKey);
        if (cachedResult != null) {
            callback.onSuccess(cachedResult);
            if (options.getTimingListener() != null) {
                options.getTimingListener()
                        .onTimingReady(
                                new TranslationTimingReport(
                                        startedAt, System.currentTimeMillis(), 0));
            }
            return;
        }

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
            translationCache.put(cacheKey, translatedHtml);
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
        }
    }

    @Override
    public void close() {
        cancelled.set(true);
        translationCache.clear();
    }

    @NonNull
    private String optionsVersion() {
        List<String> protectedTags = new ArrayList<>(options.getProtectedTags());
        Collections.sort(protectedTags);
        return "schema=v1"
                + "|tags="
                + String.join(",", protectedTags)
                + "|maxChunkChars="
                + options.getMaxChunkChars()
                + "|failurePolicy="
                + options.getFailurePolicy().name().toLowerCase(Locale.ROOT)
                + "|maskUrls="
                + options.isMaskUrls()
                + "|maskPlaceholders="
                + options.isMaskPlaceholders()
                + "|maskPaths="
                + options.isMaskPaths();
    }
}
