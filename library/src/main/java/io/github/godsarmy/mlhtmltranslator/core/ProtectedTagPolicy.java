package io.github.godsarmy.mlhtmltranslator.core;

import androidx.annotation.NonNull;
import java.util.Locale;
import java.util.Set;
import org.jsoup.nodes.Node;

final class ProtectedTagPolicy {

    private ProtectedTagPolicy() {}

    static boolean isUnderProtectedAncestor(
            @NonNull Node node, @NonNull Set<String> protectedTags) {
        Node current = node.parent();
        while (current != null) {
            String nodeName = current.nodeName().toLowerCase(Locale.ROOT);
            if (protectedTags.contains(nodeName)) {
                return true;
            }
            current = current.parent();
        }
        return false;
    }
}
