package io.github.godsarmy.mlhtmltranslator.api;

import androidx.annotation.NonNull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExplainHtmlNode {

    private final int index;
    private final String leadingWhitespace;
    private final String translatableText;
    private final String trailingWhitespace;
    private final String maskedText;
    private final Map<String, String> placeholders;

    public ExplainHtmlNode(
            int index,
            @NonNull String leadingWhitespace,
            @NonNull String translatableText,
            @NonNull String trailingWhitespace,
            @NonNull String maskedText,
            @NonNull Map<String, String> placeholders) {
        this.index = index;
        this.leadingWhitespace = leadingWhitespace;
        this.translatableText = translatableText;
        this.trailingWhitespace = trailingWhitespace;
        this.maskedText = maskedText;
        this.placeholders = Collections.unmodifiableMap(new LinkedHashMap<>(placeholders));
    }

    public int getIndex() {
        return index;
    }

    @NonNull
    public String getLeadingWhitespace() {
        return leadingWhitespace;
    }

    @NonNull
    public String getTranslatableText() {
        return translatableText;
    }

    @NonNull
    public String getTrailingWhitespace() {
        return trailingWhitespace;
    }

    @NonNull
    public String getMaskedText() {
        return maskedText;
    }

    @NonNull
    public Map<String, String> getPlaceholders() {
        return placeholders;
    }
}
