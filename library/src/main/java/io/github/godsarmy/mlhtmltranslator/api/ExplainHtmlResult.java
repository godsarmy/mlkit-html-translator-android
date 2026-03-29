package io.github.godsarmy.mlhtmltranslator.api;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ExplainHtmlResult {

    private final String normalizedHtmlBody;
    private final List<ExplainHtmlNode> nodes;
    private final List<ExplainHtmlChunk> chunks;
    private final Set<String> protectedTags;
    private final boolean maskUrls;
    private final boolean maskPlaceholders;
    private final boolean maskPaths;

    public ExplainHtmlResult(
            @NonNull String normalizedHtmlBody,
            @NonNull List<ExplainHtmlNode> nodes,
            @NonNull List<ExplainHtmlChunk> chunks,
            @NonNull Set<String> protectedTags,
            boolean maskUrls,
            boolean maskPlaceholders,
            boolean maskPaths) {
        this.normalizedHtmlBody = normalizedHtmlBody;
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.chunks = Collections.unmodifiableList(new ArrayList<>(chunks));
        this.protectedTags = Collections.unmodifiableSet(new LinkedHashSet<>(protectedTags));
        this.maskUrls = maskUrls;
        this.maskPlaceholders = maskPlaceholders;
        this.maskPaths = maskPaths;
    }

    @NonNull
    public String getNormalizedHtmlBody() {
        return normalizedHtmlBody;
    }

    @NonNull
    public List<ExplainHtmlNode> getNodes() {
        return nodes;
    }

    @NonNull
    public List<ExplainHtmlChunk> getChunks() {
        return chunks;
    }

    @NonNull
    public Set<String> getProtectedTags() {
        return protectedTags;
    }

    public boolean isMaskUrls() {
        return maskUrls;
    }

    public boolean isMaskPlaceholders() {
        return maskPlaceholders;
    }

    public boolean isMaskPaths() {
        return maskPaths;
    }

    public int getTotalNodeCount() {
        return nodes.size();
    }

    public int getTotalChunkCount() {
        return chunks.size();
    }
}
