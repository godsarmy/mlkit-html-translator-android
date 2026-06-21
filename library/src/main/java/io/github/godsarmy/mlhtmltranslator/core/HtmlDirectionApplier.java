package io.github.godsarmy.mlhtmltranslator.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.godsarmy.mlhtmltranslator.HtmlTranslationOptions;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

final class HtmlDirectionApplier {

    private static final Set<String> RTL_LANGUAGE_CODES =
            new HashSet<>(
                    Arrays.asList("ar", "dv", "fa", "he", "iw", "ps", "sd", "ug", "ur", "yi"));

    void apply(
            @NonNull Document document,
            @NonNull String targetLanguage,
            @NonNull HtmlTranslationOptions.OutputDirectionMode mode) {
        Direction direction = resolveDirection(targetLanguage, mode);
        if (direction == null) {
            return;
        }

        String languageCode = normalizePrimaryLanguage(targetLanguage);
        Element targetElement = findSingleTopLevelElement(document);
        if (targetElement == null) {
            targetElement = wrapBodyContents(document);
        }

        targetElement.attr("dir", direction.attributeValue);
        if (languageCode != null) {
            targetElement.attr("lang", languageCode);
        }
    }

    @Nullable
    private static Direction resolveDirection(
            @NonNull String targetLanguage,
            @NonNull HtmlTranslationOptions.OutputDirectionMode mode) {
        switch (mode) {
            case FORCE_LTR:
                return Direction.LTR;
            case FORCE_RTL:
                return Direction.RTL;
            case AUTO_FROM_TARGET_LANGUAGE:
                String languageCode = normalizePrimaryLanguage(targetLanguage);
                if (languageCode == null) {
                    return null;
                }
                return RTL_LANGUAGE_CODES.contains(languageCode) ? Direction.RTL : Direction.LTR;
            case PRESERVE:
            default:
                return null;
        }
    }

    @Nullable
    private static String normalizePrimaryLanguage(@NonNull String languageCode) {
        String normalized = languageCode.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        int separatorIndex = normalized.indexOf('-');
        if (separatorIndex < 0) {
            separatorIndex = normalized.indexOf('_');
        }
        if (separatorIndex > 0) {
            normalized = normalized.substring(0, separatorIndex);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    @Nullable
    private static Element findSingleTopLevelElement(@NonNull Document document) {
        Element onlyElement = null;
        for (Node node : document.body().childNodes()) {
            if (isBlankTextNode(node)) {
                continue;
            }
            if (!(node instanceof Element)) {
                return null;
            }
            if (onlyElement != null) {
                return null;
            }
            onlyElement = (Element) node;
        }
        return onlyElement;
    }

    @NonNull
    private static Element wrapBodyContents(@NonNull Document document) {
        Element wrapper = document.createElement("div");
        while (!document.body().childNodes().isEmpty()) {
            wrapper.appendChild(document.body().childNode(0));
        }
        document.body().appendChild(wrapper);
        return wrapper;
    }

    private static boolean isBlankTextNode(@NonNull Node node) {
        return node instanceof TextNode && ((TextNode) node).getWholeText().trim().isEmpty();
    }

    private enum Direction {
        LTR("ltr"),
        RTL("rtl");

        private final String attributeValue;

        Direction(@NonNull String attributeValue) {
            this.attributeValue = attributeValue;
        }
    }
}
