package io.github.godsarmy.mlhtmltranslator.api;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExplainHtmlChunk {

    private final int index;
    private final String payload;
    private final List<Integer> nodeIndexes;
    private final List<String> nodeTexts;

    public ExplainHtmlChunk(
            int index,
            @NonNull String payload,
            @NonNull List<Integer> nodeIndexes,
            @NonNull List<String> nodeTexts) {
        this.index = index;
        this.payload = payload;
        this.nodeIndexes = Collections.unmodifiableList(new ArrayList<>(nodeIndexes));
        this.nodeTexts = Collections.unmodifiableList(new ArrayList<>(nodeTexts));
    }

    public int getIndex() {
        return index;
    }

    @NonNull
    public String getPayload() {
        return payload;
    }

    @NonNull
    public List<Integer> getNodeIndexes() {
        return nodeIndexes;
    }

    @NonNull
    public List<String> getNodeTexts() {
        return nodeTexts;
    }

    public int getPlainTextLength() {
        int totalLength = 0;
        for (String nodeText : nodeTexts) {
            totalLength += nodeText.length();
        }
        return totalLength;
    }
}
