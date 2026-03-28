package io.github.godsarmy.mlhtmltranslator.core;

import static org.junit.Assert.assertEquals;

import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import java.util.List;
import org.junit.Test;

public class HtmlBodyTranslationEngineTest {

    @Test
    public void collectEligibleTextNodes_skipsProtectedTags_andKeepsDomOrder() {
        HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
        HtmlTranslationOptions options = HtmlTranslationOptions.builder().build();

        String html =
                "<div>Hello <b>world</b>! <code>dont touch</code><p>Next <i>line</i></p>"
                        + "<pre>skip me</pre></div>";

        List<CollectedTextNode> nodes = engine.collectEligibleTextNodes(html, options);

        assertEquals(5, nodes.size());
        assertEquals("Hello", nodes.get(0).getTranslatableText());
        assertEquals("world", nodes.get(1).getTranslatableText());
        assertEquals("!", nodes.get(2).getTranslatableText());
        assertEquals("Next", nodes.get(3).getTranslatableText());
        assertEquals("line", nodes.get(4).getTranslatableText());
    }

    @Test
    public void collectEligibleTextNodes_preservesWhitespaceBoundaries() {
        HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
        HtmlTranslationOptions options = HtmlTranslationOptions.builder().build();

        List<CollectedTextNode> nodes =
                engine.collectEligibleTextNodes("<p>  hello world  </p>", options);

        assertEquals(1, nodes.size());
        assertEquals("  ", nodes.get(0).getLeadingWhitespace());
        assertEquals("hello world", nodes.get(0).getTranslatableText());
        assertEquals("  ", nodes.get(0).getTrailingWhitespace());
    }
}
