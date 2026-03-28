package io.github.godsarmy.mlhtmltranslator.mask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TokenMaskerTest {

    @Test
    public void maskAndUnmask_roundTripForAllConfiguredDetectors() {
        TokenMasker masker = new TokenMasker();
        TokenMasker.MaskingConfig config = new TokenMasker.MaskingConfig(true, true, true);

        String input =
                "Visit https://example.com/docs at admin@example.com with %s and ${name} "
                        + "using --force in ./scripts/run.sh";

        TokenMasker.MaskingResult masked = masker.mask(input, config);
        String restored = masker.unmask(masked.getMaskedText(), masked.getPlaceholderToOriginal());

        assertEquals(input, restored);
        assertFalse(masked.getMaskedText().contains("https://example.com/docs"));
        assertFalse(masked.getMaskedText().contains("admin@example.com"));
        assertFalse(masked.getMaskedText().contains("%s"));
        assertFalse(masked.getMaskedText().contains("${name}"));
        assertFalse(masked.getMaskedText().contains("--force"));
        assertFalse(masked.getMaskedText().contains("./scripts/run.sh"));
    }

    @Test
    public void mask_respectsConfigToggles() {
        TokenMasker masker = new TokenMasker();
        TokenMasker.MaskingConfig config = new TokenMasker.MaskingConfig(false, false, false);

        String input = "Keep URL https://example.com and placeholder %s and path /tmp/file";
        TokenMasker.MaskingResult masked = masker.mask(input, config);

        assertTrue(masked.getMaskedText().contains("https://example.com"));
        assertTrue(masked.getMaskedText().contains("%s"));
        assertTrue(masked.getMaskedText().contains("/tmp/file"));
    }

    @Test
    public void mask_isCollisionSafeWhenPlaceholderAlreadyExistsInInput() {
        TokenMasker masker = new TokenMasker();
        TokenMasker.MaskingConfig config = new TokenMasker.MaskingConfig(true, true, true);

        String input = "Literal @@P0@@ and token https://example.com";
        TokenMasker.MaskingResult masked = masker.mask(input, config);

        assertTrue(masked.getMaskedText().contains("@@P0@@"));
        assertTrue(
                masked.getPlaceholderToOriginal().keySet().stream()
                        .anyMatch(key -> !"@@P0@@".equals(key)));
        assertEquals(
                input, masker.unmask(masked.getMaskedText(), masked.getPlaceholderToOriginal()));
    }
}
