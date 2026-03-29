package io.github.godsarmy.mlhtmltranslator.mask;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.regex.Pattern;
import org.junit.Test;

public class TokenPatternRegistryTest {

    @Test
    public void placeholderPattern_escapesClosingBraces() throws Exception {
        Field field = TokenPatternRegistry.class.getDeclaredField("PLACEHOLDER_PATTERN");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);

        String regex = pattern.pattern();
        assertTrue(regex.contains("\\}"));
    }
}
