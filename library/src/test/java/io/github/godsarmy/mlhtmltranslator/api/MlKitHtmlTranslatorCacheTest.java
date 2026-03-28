package io.github.godsarmy.mlhtmltranslator.api;

import static org.junit.Assert.assertEquals;

import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import io.github.godsarmy.mlhtmltranslator.cache.InMemoryTranslationCache;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class MlKitHtmlTranslatorCacheTest {

    @Test
    public void translateHtml_usesCacheForRepeatedRequests() {
        AtomicInteger callCount = new AtomicInteger(0);
        MlTranslationAdapter countingAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    callCount.incrementAndGet();
                    return text;
                };

        MlKitHtmlTranslator translator =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().build(),
                        countingAdapter,
                        new InMemoryTranslationCache(16));

        translator.translateHtml("<p>Hello world</p>", "en", "es", new NoOpCallback());
        translator.translateHtml("<p>Hello world</p>", "en", "es", new NoOpCallback());

        assertEquals(1, callCount.get());
    }

    @Test
    public void translateHtml_cacheInvalidatesWhenOptionsChange() {
        AtomicInteger callCount = new AtomicInteger(0);
        MlTranslationAdapter countingAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    callCount.incrementAndGet();
                    return text;
                };

        MlKitHtmlTranslator translatorA =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().setMaxChunkChars(3000).build(),
                        countingAdapter,
                        new InMemoryTranslationCache(16));
        MlKitHtmlTranslator translatorB =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().setMaxChunkChars(1000).build(),
                        countingAdapter,
                        new InMemoryTranslationCache(16));

        translatorA.translateHtml("<p>Hello world</p>", "en", "es", new NoOpCallback());
        translatorB.translateHtml("<p>Hello world</p>", "en", "es", new NoOpCallback());

        assertEquals(2, callCount.get());
    }

    private static final class NoOpCallback implements TranslationCallback {
        @Override
        public void onSuccess(String translatedHtml) {}

        @Override
        public void onFailure(TranslationException exception) {
            throw new AssertionError("Unexpected failure: " + exception.getMessage());
        }
    }
}
