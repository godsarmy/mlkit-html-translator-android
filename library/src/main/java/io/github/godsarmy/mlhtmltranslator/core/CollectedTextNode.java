package io.github.godsarmy.mlhtmltranslator.core;

import androidx.annotation.NonNull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.TextNode;

public final class CollectedTextNode {

    private static final Pattern LEADING_PATTERN = Pattern.compile("^\\s*");
    private static final Pattern TRAILING_PATTERN = Pattern.compile("\\s*$");

    private final TextNode textNode;
    private final String leadingWhitespace;
    private final String translatableText;
    private final String trailingWhitespace;

    private CollectedTextNode(
            TextNode textNode,
            String leadingWhitespace,
            String translatableText,
            String trailingWhitespace) {
        this.textNode = textNode;
        this.leadingWhitespace = leadingWhitespace;
        this.translatableText = translatableText;
        this.trailingWhitespace = trailingWhitespace;
    }

    @NonNull
    static CollectedTextNode from(@NonNull TextNode textNode) {
        String wholeText = textNode.getWholeText();
        String leadingWhitespace = matchLeading(wholeText);
        String trailingWhitespace = matchTrailing(wholeText);

        int start = leadingWhitespace.length();
        int end = wholeText.length() - trailingWhitespace.length();
        String translatableText = start <= end ? wholeText.substring(start, end) : "";

        return new CollectedTextNode(
                textNode, leadingWhitespace, translatableText, trailingWhitespace);
    }

    @NonNull
    private static String matchLeading(@NonNull String value) {
        Matcher matcher = LEADING_PATTERN.matcher(value);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group();
    }

    @NonNull
    private static String matchTrailing(@NonNull String value) {
        Matcher matcher = TRAILING_PATTERN.matcher(value);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group();
    }

    @NonNull
    public TextNode getTextNode() {
        return textNode;
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
}
