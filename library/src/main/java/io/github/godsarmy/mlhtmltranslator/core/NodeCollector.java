package io.github.godsarmy.mlhtmltranslator.core;

import androidx.annotation.NonNull;
import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

final class NodeCollector {

    @NonNull
    List<CollectedTextNode> collectEligibleNodes(
            @NonNull Node root, @NonNull HtmlTranslationOptions options) {
        List<CollectedTextNode> nodes = new ArrayList<>();
        NodeTraversor.traverse(
                new NodeVisitor() {
                    @Override
                    public void head(Node node, int depth) {
                        if (!(node instanceof TextNode)) {
                            return;
                        }

                        TextNode textNode = (TextNode) node;
                        String wholeText = textNode.getWholeText();

                        if (wholeText.trim().isEmpty()) {
                            return;
                        }

                        if (ProtectedTagPolicy.isUnderProtectedAncestor(
                                textNode, options.getProtectedTags())) {
                            return;
                        }

                        nodes.add(CollectedTextNode.from(textNode));
                    }

                    @Override
                    public void tail(Node node, int depth) {
                        // no-op
                    }
                },
                root);
        return nodes;
    }
}
