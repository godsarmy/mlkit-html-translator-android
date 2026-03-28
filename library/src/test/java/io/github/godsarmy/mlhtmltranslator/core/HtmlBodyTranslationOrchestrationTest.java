package io.github.godsarmy.mlhtmltranslator.core;

import static org.junit.Assert.assertTrue;

import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.api.TranslationErrorCode;
import io.github.godsarmy.mlhtmltranslator.api.TranslationException;
import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class HtmlBodyTranslationOrchestrationTest {

    @Test
    public void translateHtmlBody_runsPipeline_andPreservesProtectedTags() throws Exception {
        HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
        HtmlTranslationOptions options = HtmlTranslationOptions.builder().build();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        MlTranslationAdapter fakeAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) ->
                        text.replace("Hello", "Hola").replace("world", "mundo");

        String result =
                engine.translateHtmlBody(
                        "<p>Hello <b>world</b></p><code>Hello world</code>",
                        "en",
                        "es",
                        options,
                        fakeAdapter,
                        cancelled);

        assertTrue(result.contains("Hola"));
        assertTrue(result.contains("mundo"));
        assertTrue(result.contains("<code>Hello world</code>"));
    }

    @Test
    public void translateHtmlBody_returnsCancelledWhenCancelledBeforeStart() {
        HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
        HtmlTranslationOptions options = HtmlTranslationOptions.builder().build();
        AtomicBoolean cancelled = new AtomicBoolean(true);

        MlTranslationAdapter fakeAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> text;

        try {
            engine.translateHtmlBody(
                    "<p>Hello world</p>", "en", "es", options, fakeAdapter, cancelled);
        } catch (TranslationException e) {
            assertTrue(e.getErrorCode() == TranslationErrorCode.CANCELLED);
            return;
        }

        throw new AssertionError("Expected TranslationException with CANCELLED code");
    }
}
