package io.github.godsarmy.mlhtmltranslator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import io.github.godsarmy.mlhtmltranslator.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

public class HtmlDirectionApplierTest {

    private final HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
    private final MlTranslationAdapter identityAdapter =
            (text, sourceLanguage, targetLanguage, timeoutMs) -> text;

    @Test
    public void preserveMode_doesNotAddDirectionAttributes() throws Exception {
        String translated =
                translate("<p>Hello</p>", "ar", HtmlTranslationOptions.builder().build());

        Document document = Jsoup.parseBodyFragment(translated);
        assertFalse(document.selectFirst("p").hasAttr("dir"));
        assertFalse(document.selectFirst("p").hasAttr("lang"));
    }

    @Test
    public void autoMode_addsRtlDirectionForArabicSingleRoot() throws Exception {
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder()
                        .setOutputDirectionMode(
                                HtmlTranslationOptions.OutputDirectionMode
                                        .AUTO_FROM_TARGET_LANGUAGE)
                        .build();

        String translated = translate("<p>Hello</p>", "ar-SA", options);

        Document document = Jsoup.parseBodyFragment(translated);
        assertEquals("rtl", document.selectFirst("p").attr("dir"));
        assertEquals("ar", document.selectFirst("p").attr("lang"));
    }

    @Test
    public void autoMode_wrapsMultipleRootsForUrdu() throws Exception {
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder()
                        .setOutputDirectionMode(
                                HtmlTranslationOptions.OutputDirectionMode
                                        .AUTO_FROM_TARGET_LANGUAGE)
                        .build();

        String translated = translate("<h1>Hello</h1><p>World</p>", "ur_PK", options);

        Document document = Jsoup.parseBodyFragment(translated);
        assertEquals("rtl", document.body().child(0).attr("dir"));
        assertEquals("ur", document.body().child(0).attr("lang"));
        assertEquals(1, document.select("body > div > h1").size());
        assertEquals(1, document.select("body > div > p").size());
    }

    @Test
    public void autoMode_addsLtrDirectionForEnglish() throws Exception {
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder()
                        .setOutputDirectionMode(
                                HtmlTranslationOptions.OutputDirectionMode
                                        .AUTO_FROM_TARGET_LANGUAGE)
                        .build();

        String translated = translate("<p>Hola</p>", "en", options);

        Document document = Jsoup.parseBodyFragment(translated);
        assertEquals("ltr", document.selectFirst("p").attr("dir"));
        assertEquals("en", document.selectFirst("p").attr("lang"));
    }

    @Test
    public void forceRtl_usesTargetLanguageForLangAttribute() throws Exception {
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder()
                        .setOutputDirectionMode(
                                HtmlTranslationOptions.OutputDirectionMode.FORCE_RTL)
                        .build();

        String translated = translate("<p>Hello</p>", "en-US", options);

        Document document = Jsoup.parseBodyFragment(translated);
        assertEquals("rtl", document.selectFirst("p").attr("dir"));
        assertEquals("en", document.selectFirst("p").attr("lang"));
    }

    private String translate(String htmlBody, String targetLanguage, HtmlTranslationOptions options)
            throws Exception {
        return engine.translateHtmlBody(
                htmlBody, "en", targetLanguage, options, identityAdapter, new AtomicBoolean(false));
    }
}
