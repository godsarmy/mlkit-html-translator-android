package io.github.godsarmy.mlhtmltranslator.api;

import static org.junit.Assert.assertEquals;

import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import io.github.godsarmy.mlhtmltranslator.cache.InMemoryTranslationCache;
import java.util.Arrays;
import java.util.LinkedHashSet;
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

    @Test
    public void translateHtml_cacheReusedWhenProtectedTagsOrderDiffers() {
        AtomicInteger callCount = new AtomicInteger(0);
        MlTranslationAdapter countingAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    callCount.incrementAndGet();
                    return text;
                };
        InMemoryTranslationCache sharedCache = new InMemoryTranslationCache(16);

        MlKitHtmlTranslator translatorA =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder()
                                .setProtectedTags(new LinkedHashSet<>(Arrays.asList("code", "pre")))
                                .build(),
                        countingAdapter,
                        sharedCache);
        MlKitHtmlTranslator translatorB =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder()
                                .setProtectedTags(new LinkedHashSet<>(Arrays.asList("pre", "code")))
                                .build(),
                        countingAdapter,
                        sharedCache);

        translatorA.translateHtml("<p>Hello world</p>", "en", "es", new NoOpCallback());
        translatorB.translateHtml("<p>Hello world</p>", "en", "es", new NoOpCallback());

        assertEquals(1, callCount.get());
    }

    @Test
    public void translateHtml_cacheInvalidatesWhenMaskingFlagsChange() {
        AtomicInteger callCount = new AtomicInteger(0);
        MlTranslationAdapter countingAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    callCount.incrementAndGet();
                    return text;
                };
        InMemoryTranslationCache sharedCache = new InMemoryTranslationCache(16);

        MlKitHtmlTranslator maskUrlsOn =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().setMaskUrls(true).build(),
                        countingAdapter,
                        sharedCache);
        MlKitHtmlTranslator maskUrlsOff =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().setMaskUrls(false).build(),
                        countingAdapter,
                        sharedCache);

        maskUrlsOn.translateHtml("<p>See https://example.com</p>", "en", "es", new NoOpCallback());
        maskUrlsOff.translateHtml("<p>See https://example.com</p>", "en", "es", new NoOpCallback());

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
