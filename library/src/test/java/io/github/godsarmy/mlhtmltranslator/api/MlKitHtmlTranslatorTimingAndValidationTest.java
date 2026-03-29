package io.github.godsarmy.mlhtmltranslator.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class MlKitHtmlTranslatorTimingAndValidationTest {

    @Test
    public void translateHtml_blankLanguageCodesReturnInvalidInput() {
        MlKitHtmlTranslator translator = new MlKitHtmlTranslator();
        CapturingCallback callback = new CapturingCallback();

        translator.translateHtml("<p>Hello</p>", "", "es", callback);

        assertNotNull(callback.exception);
        assertEquals(TranslationErrorCode.INVALID_INPUT, callback.exception.getErrorCode());
        assertNull(callback.translated);
    }

    @Test
    public void translateHtml_invokesTimingListenerForFreshTranslation() {
        AtomicInteger adapterCalls = new AtomicInteger(0);
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    adapterCalls.incrementAndGet();
                    return text.replace("Hello", "Hola");
                };

        List<TranslationTimingReport> reports = new ArrayList<>();
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder().setTimingListener(reports::add).build();
        MlKitHtmlTranslator translator = new MlKitHtmlTranslator(options, adapter);

        CapturingCallback callback = new CapturingCallback();
        translator.translateHtml("<p>Hello world</p>", "en", "es", callback);

        assertNull(callback.exception);
        assertTrue(callback.translated.contains("Hola"));
        assertEquals(1, adapterCalls.get());
        assertEquals(1, reports.size());
        assertTrue(reports.get(0).getChunkCount() > 0);
    }

    @Test
    public void translateHtml_repeatedRequestsStillRunTranslationPipeline() {
        AtomicInteger adapterCalls = new AtomicInteger(0);
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    adapterCalls.incrementAndGet();
                    return text.replace("Hello", "Hola");
                };

        List<TranslationTimingReport> reports = new ArrayList<>();
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder().setTimingListener(reports::add).build();
        MlKitHtmlTranslator translator = new MlKitHtmlTranslator(options, adapter);

        translator.translateHtml("<p>Hello world</p>", "en", "es", new CapturingCallback());
        translator.translateHtml("<p>Hello world</p>", "en", "es", new CapturingCallback());

        assertEquals(2, adapterCalls.get());
        assertEquals(2, reports.size());
        assertTrue(reports.get(0).getChunkCount() > 0);
        assertTrue(reports.get(1).getChunkCount() > 0);
    }

    @Test
    public void explainHtml_runsPreprocessWithoutCallingTranslationAdapter() {
        AtomicInteger adapterCalls = new AtomicInteger(0);
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    adapterCalls.incrementAndGet();
                    return text;
                };

        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder().setMaxChunkChars(20).build();
        MlKitHtmlTranslator translator = new MlKitHtmlTranslator(options, adapter);

        ExplainHtmlResult result =
                translator.explainHtml(
                        "<p> Hello https://example.com </p><code>skip</code><p>Second</p>");

        assertEquals(0, adapterCalls.get());
        assertEquals(2, result.getTotalNodeCount());
        assertEquals(2, result.getTotalChunkCount());
        assertTrue(result.getNodes().get(0).getMaskedText().contains("@@P"));
        assertTrue(result.getChunks().get(0).getPayload().contains("⟦M"));
    }

    @Test
    public void explainHtml_blankInputReturnsEmptyDiagnostics() {
        MlKitHtmlTranslator translator = new MlKitHtmlTranslator();

        ExplainHtmlResult result = translator.explainHtml("   ");

        assertEquals(0, result.getTotalNodeCount());
        assertEquals(0, result.getTotalChunkCount());
        assertEquals("", result.getNormalizedHtmlBody());
    }

    private static final class CapturingCallback implements TranslationCallback {
        private String translated;
        private TranslationException exception;

        @Override
        public void onSuccess(String translatedHtml) {
            this.translated = translatedHtml;
        }

        @Override
        public void onFailure(TranslationException exception) {
            this.exception = exception;
        }
    }
}
