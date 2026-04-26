package io.github.godsarmy.mlhtmltranslator.sample;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

final class HtmlCompareFormatter {
    private static final String INDENT = "  ";
    private static final Set<String> VOID_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
                            "meta", "param", "source", "track", "wbr"));

    private HtmlCompareFormatter() {}

    @NonNull
    static String normalize(@NonNull String html) {
        Document document = Jsoup.parseBodyFragment(html);
        List<String> lines = new ArrayList<>();
        for (Node node : document.body().childNodes()) {
            appendNode(lines, node, 0);
        }
        return String.join("\n", lines);
    }

    private static void appendNode(List<String> lines, Node node, int depth) {
        if (node instanceof Element) {
            Element element = (Element) node;
            String tagName = element.normalName();
            lines.add(indent(depth) + openTag(element));
            for (Node child : element.childNodes()) {
                appendNode(lines, child, depth + 1);
            }
            if (!VOID_TAGS.contains(tagName)) {
                lines.add(indent(depth) + "</" + tagName + ">");
            }
            return;
        }

        if (node instanceof TextNode) {
            String normalizedText = normalizeText(((TextNode) node).getWholeText());
            if (!normalizedText.isEmpty()) {
                lines.add(indent(depth) + normalizedText);
            }
            return;
        }

        if (node instanceof Comment) {
            String commentText = ((Comment) node).getData().trim();
            if (!commentText.isEmpty()) {
                lines.add(indent(depth) + "<!-- " + commentText + " -->");
            }
            return;
        }

        if (node instanceof DataNode) {
            String dataText = normalizeText(((DataNode) node).getWholeData());
            if (!dataText.isEmpty()) {
                lines.add(indent(depth) + dataText);
            }
        }
    }

    @NonNull
    private static String openTag(Element element) {
        StringBuilder builder = new StringBuilder();
        builder.append('<').append(element.normalName());
        for (Attribute attribute : element.attributes()) {
            builder.append(' ')
                    .append(attribute.getKey())
                    .append("=\"")
                    .append(escapeAttributeValue(attribute.getValue()))
                    .append('"');
        }
        builder.append('>');
        return builder.toString();
    }

    @NonNull
    private static String normalizeText(String input) {
        return input.replaceAll("\\s+", " ").trim();
    }

    @NonNull
    private static String escapeAttributeValue(String value) {
        return value.replace("\"", "&quot;");
    }

    @NonNull
    private static String indent(int depth) {
        if (depth <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(depth * INDENT.length());
        for (int i = 0; i < depth; i++) {
            builder.append(INDENT);
        }
        return builder.toString();
    }
}
