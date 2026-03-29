package io.github.godsarmy.mlhtmltranslator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

public class HtmlTranslationCorrectnessTest {

    private final HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
    private final HtmlTranslationOptions options = HtmlTranslationOptions.builder().build();

    @Test
    public void protectedTagsRemainUnchanged() throws Exception {
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> text.toUpperCase();

        String html =
                "<p>hello</p><code>do not touch</code><pre>echo 1</pre><script>var x=1</script>"
                        + "<style>.a{color:red;}</style>";

        String translated =
                engine.translateHtmlBody(
                        html, "en", "es", options, adapter, new AtomicBoolean(false));

        assertTrue(translated.contains("HELLO"));
        assertTrue(translated.contains("<code>do not touch</code>"));
        assertTrue(translated.contains("<pre>echo 1</pre>"));
        assertTrue(translated.contains("<script>var x=1</script>"));
        assertTrue(translated.contains("<style>.a{color:red;}</style>"));
    }

    @Test
    public void anchorTextTranslatedHrefUnchanged() throws Exception {
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) ->
                        text.replace("Click here", "Pulse aqui");

        String translated =
                engine.translateHtmlBody(
                        "<a href=\"https://example.com/docs\">Click here</a>",
                        "en",
                        "es",
                        options,
                        adapter,
                        new AtomicBoolean(false));

        Document document = Jsoup.parseBodyFragment(translated);
        assertEquals("https://example.com/docs", document.selectFirst("a").attr("href"));
        assertEquals("Pulse aqui", document.selectFirst("a").text());
    }

    @Test
    public void nestedTagsPreserveStructure() throws Exception {
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> text.replace("hello", "hola");

        String translated =
                engine.translateHtmlBody(
                        "<div><p><span>hello</span> <em>world</em></p></div>",
                        "en",
                        "es",
                        options,
                        adapter,
                        new AtomicBoolean(false));

        Document document = Jsoup.parseBodyFragment(translated);
        assertEquals(1, document.select("div > p > span").size());
        assertEquals(1, document.select("div > p > em").size());
        assertEquals("hola", document.selectFirst("span").text());
    }

    @Test
    public void whitespaceAroundInlineElementsIsPreserved() throws Exception {
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) ->
                        text.replace("A", "X").replace("B", "Y");

        String translated =
                engine.translateHtmlBody(
                        "<p> A <strong>B</strong> C </p>",
                        "en",
                        "es",
                        options,
                        adapter,
                        new AtomicBoolean(false));

        int strongIndex = translated.indexOf("<strong>Y</strong>");
        assertTrue(strongIndex > 0);
        String beforeStrong = translated.substring(0, strongIndex);
        String afterStrong = translated.substring(strongIndex + "<strong>Y</strong>".length());
        assertTrue(beforeStrong.contains("X"));
        assertTrue(translated.contains("<strong>Y</strong>"));
        assertTrue(afterStrong.contains("C"));
    }

    @Test
    public void fixtureLongManualLikeArticleMaintainsHeadingsListsAndTables() throws Exception {
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) ->
                        text.replace("Deploy", "Desplegar");

        String html =
                "<h1>Deploy Manual</h1><ul><li>Deploy service</li></ul>"
                        + "<table><tr><th>Step</th><td>Deploy now</td></tr></table>";

        String translated =
                engine.translateHtmlBody(
                        html, "en", "es", options, adapter, new AtomicBoolean(false));

        Document document = Jsoup.parseBodyFragment(translated);
        assertEquals(1, document.select("h1").size());
        assertEquals(1, document.select("ul > li").size());
        assertEquals(1, document.select("table tr th").size());
        assertTrue(translated.contains("Desplegar"));
    }

    @Test
    public void fixtureMixedProseAndCodeBlocksLeavesCodeUntouched() throws Exception {
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> text.replace("Guide", "Guia");

        String html = "<p>Guide text</p><pre><code>curl --retry 3</code></pre>";

        String translated =
                engine.translateHtmlBody(
                        html, "en", "es", options, adapter, new AtomicBoolean(false));

        assertTrue(translated.contains("Guia text"));
        assertTrue(translated.contains("<pre><code>curl --retry 3</code></pre>"));
    }

    @Test
    public void fixtureHeavyLinksAndInlineCodeKeepsAttributesAndInlineCode() throws Exception {
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> text.replace("Open", "Abrir");

        String html = "<p><a href=\"https://a.com\">Open doc</a> and <code>${TOKEN}</code></p>";

        String translated =
                engine.translateHtmlBody(
                        html, "en", "es", options, adapter, new AtomicBoolean(false));

        Document doc = Jsoup.parseBodyFragment(translated);
        assertEquals("https://a.com", doc.selectFirst("a").attr("href"));
        assertEquals("${TOKEN}", doc.selectFirst("code").text());
    }

    @Test
    public void fixtureMultilingualInputsRemainValid() throws Exception {
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> text.replace("Hello", "Hola");

        String html = "<p>Hello こんにちは مرحبا</p>";

        String translated =
                engine.translateHtmlBody(
                        html, "en", "es", options, adapter, new AtomicBoolean(false));

        assertTrue(translated.contains("Hola"));
        assertTrue(translated.contains("こんにちは"));
        assertTrue(translated.contains("مرحبا"));
        assertFalse(translated.isEmpty());
    }

    @Test
    public void malformedHtml_isRecoveredAndTranslatedWithoutBreakingStructure() throws Exception {
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> text.replace("Hello", "Hola");

        String html = "<div><p>Hello <b>world</div>";

        String translated =
                engine.translateHtmlBody(
                        html, "en", "es", options, adapter, new AtomicBoolean(false));

        Document doc = Jsoup.parseBodyFragment(translated);
        assertEquals("Hola", doc.selectFirst("p").ownText().trim());
        assertEquals("world", doc.selectFirst("b").text());
    }

    @Test
    public void htmlCommentsAndEntities_arePreservedWhileTextIsTranslated() throws Exception {
        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) ->
                        text.replace("Welcome", "Bienvenido");

        String html = "<!-- keep comment --><p>Welcome &amp; enjoy 😊</p>";

        String translated =
                engine.translateHtmlBody(
                        html, "en", "es", options, adapter, new AtomicBoolean(false));

        assertTrue(translated.contains("<!-- keep comment -->"));
        assertTrue(translated.contains("Bienvenido &amp; enjoy 😊"));
    }
}
