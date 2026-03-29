package io.github.godsarmy.mlhtmltranslator.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import io.github.godsarmy.mlhtmltranslator.cache.InMemoryTranslationCache;
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
    public void close_clearsCacheSoNextTranslatorDoesNotReuseOldEntries() {
        AtomicInteger callCount = new AtomicInteger(0);
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    callCount.incrementAndGet();
                    return text;
                };
        InMemoryTranslationCache sharedCache = new InMemoryTranslationCache(16);

        MlKitHtmlTranslator first =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().build(), adapter, sharedCache);
        first.translateHtml("<p>Hello</p>", "en", "es", new CapturingCallback());
        first.close();

        MlKitHtmlTranslator second =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().build(), adapter, sharedCache);
        second.translateHtml("<p>Hello</p>", "en", "es", new CapturingCallback());

        assertEquals(2, callCount.get());
    }

    @Test
    public void sharedCache_differentMaskOptionsUseDifferentCacheKeys() {
        AtomicInteger callCount = new AtomicInteger(0);
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    callCount.incrementAndGet();
                    return text;
                };
        InMemoryTranslationCache sharedCache = new InMemoryTranslationCache(16);

        MlKitHtmlTranslator maskPathsOn =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().setMaskPaths(true).build(),
                        adapter,
                        sharedCache);
        MlKitHtmlTranslator maskPathsOff =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().setMaskPaths(false).build(),
                        adapter,
                        sharedCache);

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
