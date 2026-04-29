package io.github.godsarmy.mlhtmltranslator.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HtmlTranslationOptionsTest {

    @Test
    public void defaults_areInitialized() {
        HtmlTranslationOptions options = HtmlTranslationOptions.builder().build();

        assertEquals(3000, options.getMaxChunkChars());
        assertEquals(20_000L, options.getChunkTimeoutMs());
        assertEquals(HtmlTranslationOptions.FailurePolicy.BEST_EFFORT, options.getFailurePolicy());
        assertTrue(options.getProtectedTags().contains("code"));
        assertTrue(options.getProtectedTags().contains("pre"));
        assertTrue(options.getProtectedTags().contains("script"));
        assertTrue(options.getProtectedTags().contains("style"));
        assertEquals("[{[", options.getPlaceholderMarkerStart());
        assertEquals("]}]", options.getPlaceholderMarkerEnd());
    }

    @Test
    public void placeholderMarkers_areConfigurable() {
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder()
                        .setPlaceholderMarkerStart("@[@")
                        .setPlaceholderMarkerEnd("@]@")
                        .setChunkTimeoutMs(45_000L)
                        .build();

        assertEquals("@[@", options.getPlaceholderMarkerStart());
        assertEquals("@]@", options.getPlaceholderMarkerEnd());
        assertEquals(45_000L, options.getChunkTimeoutMs());
    }
}
