package io.github.godsarmy.mlhtmltranslator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.api.TranslationErrorCode;
import io.github.godsarmy.mlhtmltranslator.api.TranslationException;
import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class HtmlBodyTranslationResilienceTest {

    @Test
    public void translationFailure_bestEffortKeepsOriginalFailedParts() throws Exception {
        HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder()
                        .setFailurePolicy(HtmlTranslationOptions.FailurePolicy.BEST_EFFORT)
                        .build();

        MlTranslationAdapter alwaysFailing =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    throw new TranslationException(
                            TranslationErrorCode.TRANSLATION_FAILED, "simulated failure");
                };

        HtmlBodyTranslationEngine.PipelineResult result =
                engine.translateHtmlBodyWithReport(
                        "<p>Hello</p><p>world</p>",
                        "en",
                        "es",
                        options,
                        alwaysFailing,
                        new AtomicBoolean(false));

        assertTrue(result.getTranslatedHtml().contains("Hello"));
        assertTrue(result.getTranslatedHtml().contains("world"));
        assertEquals(2, result.getDiagnostics().getFailedNodes());
        assertEquals(0, result.getDiagnostics().getTranslatedNodes());
    }

    @Test
    public void translationFailure_failFastAborts() {
        HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder()
                        .setFailurePolicy(HtmlTranslationOptions.FailurePolicy.FAIL_FAST)
                        .build();

        MlTranslationAdapter alwaysFailing =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    throw new TranslationException(
                            TranslationErrorCode.TRANSLATION_FAILED, "simulated failure");
                };

        try {
            engine.translateHtmlBodyWithReport(
                    "<p>Hello</p>", "en", "es", options, alwaysFailing, new AtomicBoolean(false));
        } catch (TranslationException e) {
            assertEquals(TranslationErrorCode.TRANSLATION_FAILED, e.getErrorCode());
            return;
        }

        throw new AssertionError("Expected fail-fast exception");
    }

    @Test
    public void markerMismatch_retriesWithSmallerChunks_thenPerNodeFallback() throws Exception {
        HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder()
                        .setFailurePolicy(HtmlTranslationOptions.FailurePolicy.BEST_EFFORT)
                        .build();

        MlTranslationAdapter markerBreakingAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    if (text.contains("[[[SEG|")) {
                        return text.replace("[[[SEG|", "BROKEN|");
                    }
                    return text.toUpperCase();
                };

        HtmlBodyTranslationEngine.PipelineResult result =
                engine.translateHtmlBodyWithReport(
                        "<p>first</p><p>second</p>",
                        "en",
                        "es",
                        options,
                        markerBreakingAdapter,
                        new AtomicBoolean(false));

        // Marker parsing fails on chunked payload, so flow retries and falls back per-node.
        assertTrue(
                result.getTranslatedHtml().contains("FIRST")
                        || result.getTranslatedHtml().contains("first"));
        assertTrue(result.getDiagnostics().getRetryCount() > 0);
    }
}
