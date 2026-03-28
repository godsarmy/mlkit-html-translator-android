package io.github.godsarmy.mlhtmltranslator.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HtmlTranslationOptionsTest {

    @Test
    public void defaults_areInitialized() {
        HtmlTranslationOptions options = HtmlTranslationOptions.builder().build();

        assertEquals(3000, options.getMaxChunkChars());
        assertEquals(HtmlTranslationOptions.FailurePolicy.BEST_EFFORT, options.getFailurePolicy());
        assertTrue(options.getProtectedTags().contains("code"));
        assertTrue(options.getProtectedTags().contains("pre"));
        assertTrue(options.getProtectedTags().contains("script"));
        assertTrue(options.getProtectedTags().contains("style"));
    }
}
