package io.github.godsarmy.mlhtmltranslator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

public class HtmlTranslationMaskingOptionsTest {

    private final HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();

    @Test
    public void maskingEnabled_preservesUrlsPlaceholdersAndPaths() throws Exception {
        MlTranslationAdapter uppercaseAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> text.toUpperCase(Locale.ROOT);

        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder()
                        .setMaskUrls(true)
                        .setMaskPlaceholders(true)
                        .setMaskPaths(true)
                        .build();

        String translated =
                engine.translateHtmlBody(
                        "<p>open https://example.com/docs with %s from /tmp/file</p>",
                        "en", "es", options, uppercaseAdapter, new AtomicBoolean(false));

        assertTrue(translated.contains("OPEN"));
        assertTrue(translated.contains("https://example.com/docs"));
        assertTrue(translated.contains("%s"));
        assertTrue(translated.contains("/tmp/file"));
    }

    @Test
    public void maskingDisabled_allowsTokensToBeChangedByAdapter() throws Exception {
        MlTranslationAdapter uppercaseAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> text.toUpperCase(Locale.ROOT);

        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder()
                        .setMaskUrls(false)
                        .setMaskPlaceholders(false)
                        .setMaskPaths(false)
                        .build();

        String translated =
                engine.translateHtmlBody(
                        "<p>open https://example.com/docs with %s from /tmp/file</p>",
                        "en", "es", options, uppercaseAdapter, new AtomicBoolean(false));

        assertTrue(translated.contains("HTTPS://EXAMPLE.COM/DOCS"));
        assertTrue(translated.contains("%S"));
        assertTrue(translated.contains("/TMP/FILE"));
    }

    @Test
    public void customProtectedTags_skipTranslationForCustomTag() throws Exception {
        MlTranslationAdapter uppercaseAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> text.toUpperCase(Locale.ROOT);

        Set<String> protectedTags = new LinkedHashSet<>();
        protectedTags.add("blockquote");

        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder().setProtectedTags(protectedTags).build();

        String translated =
                engine.translateHtmlBody(
                        "<blockquote>keep me</blockquote><p>translate me</p>",
                        "en",
                        "es",
                        options,
                        uppercaseAdapter,
                        new AtomicBoolean(false));

        Document doc = Jsoup.parseBodyFragment(translated);
        assertEquals("keep me", doc.selectFirst("blockquote").text());
        assertEquals("TRANSLATE ME", doc.selectFirst("p").text());
    }

    @Test
    public void noEligibleNodes_returnsOriginalBodyAndZeroDiagnostics() throws Exception {
        MlTranslationAdapter passThroughAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> text;

        HtmlTranslationOptions options = HtmlTranslationOptions.builder().build();

        HtmlBodyTranslationEngine.PipelineResult result =
                engine.translateHtmlBodyWithReport(
                        "<script>const x = 1;</script><style>.a{color:red;}</style>",
                        "en",
                        "es",
                        options,
                        passThroughAdapter,
                        new AtomicBoolean(false));

        assertTrue(result.getTranslatedHtml().contains("<script>const x = 1;</script>"));
        assertTrue(result.getTranslatedHtml().contains("<style>.a{color:red;}</style>"));
        assertEquals(0, result.getDiagnostics().getTotalNodes());
        assertEquals(0, result.getDiagnostics().getChunkCount());
    }
}
