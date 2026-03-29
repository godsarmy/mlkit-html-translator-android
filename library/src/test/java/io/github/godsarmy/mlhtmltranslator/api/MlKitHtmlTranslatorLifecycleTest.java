package io.github.godsarmy.mlhtmltranslator.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class MlKitHtmlTranslatorLifecycleTest {

    @Test
    public void close_thenTranslate_returnsCancelledError() {
        MlKitHtmlTranslator translator = new MlKitHtmlTranslator();
        translator.close();

        CapturingCallback callback = new CapturingCallback();
        translator.translateHtml("<p>Hello</p>", "en", "es", callback);

        assertNotNull(callback.exception);
        assertEquals(TranslationErrorCode.CANCELLED, callback.exception.getErrorCode());
    }

    @Test
    public void close_thenNewTranslatorStillTranslates() {
        AtomicInteger callCount = new AtomicInteger(0);
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    callCount.incrementAndGet();
                    return text;
                };

        MlKitHtmlTranslator first =
                new MlKitHtmlTranslator(HtmlTranslationOptions.builder().build(), adapter);
        first.translateHtml("<p>Hello</p>", "en", "es", new CapturingCallback());
        first.close();

        MlKitHtmlTranslator second =
                new MlKitHtmlTranslator(HtmlTranslationOptions.builder().build(), adapter);
        second.translateHtml("<p>Hello</p>", "en", "es", new CapturingCallback());

        assertEquals(2, callCount.get());
    }

    @Test
    public void differentMaskOptionsDoNotShareTranslatorState() {
        AtomicInteger callCount = new AtomicInteger(0);
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    callCount.incrementAndGet();
                    return text;
                };

        MlKitHtmlTranslator maskPathsOn =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().setMaskPaths(true).build(), adapter);
        MlKitHtmlTranslator maskPathsOff =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().setMaskPaths(false).build(), adapter);

        String html = "<p>Path: /tmp/data.txt</p>";
        maskPathsOn.translateHtml(html, "en", "es", new CapturingCallback());
        maskPathsOff.translateHtml(html, "en", "es", new CapturingCallback());

        assertEquals(2, callCount.get());
    }

    private static final class CapturingCallback implements TranslationCallback {
        private TranslationException exception;

        @Override
        public void onSuccess(String translatedHtml) {}

        @Override
        public void onFailure(TranslationException exception) {
            this.exception = exception;
        }
    }
}
