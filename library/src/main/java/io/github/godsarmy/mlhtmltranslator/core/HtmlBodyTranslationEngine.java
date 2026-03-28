package io.github.godsarmy.mlhtmltranslator.core;

import androidx.annotation.NonNull;
import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class HtmlBodyTranslationEngine {

    private final NodeCollector nodeCollector = new NodeCollector();

    @NonNull
    public List<CollectedTextNode> collectEligibleTextNodes(
            @NonNull String htmlBody, @NonNull HtmlTranslationOptions options) {
        Document document = Jsoup.parseBodyFragment(htmlBody);
        return nodeCollector.collectEligibleNodes(document.body(), options);
    }
}
