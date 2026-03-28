package io.github.godsarmy.mlhtmltranslator.batch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChunkBuilder {

    @NonNull
    public List<Chunk> buildChunks(
            @NonNull List<String> nodeTexts,
            int maxChunkChars,
            @Nullable Integer maxUnitsPerChunk,
            @NonNull SegmentMarkerCodec markerCodec) {
        if (maxChunkChars < 1) {
            throw new IllegalArgumentException("maxChunkChars must be >= 1");
        }

        int maxUnits = maxUnitsPerChunk == null ? Integer.MAX_VALUE : Math.max(1, maxUnitsPerChunk);
        List<Chunk> result = new ArrayList<>();

        List<Integer> currentNodeIndexes = new ArrayList<>();
        List<String> currentNodeTexts = new ArrayList<>();
        StringBuilder payload = new StringBuilder();

        for (int nodeIndex = 0; nodeIndex < nodeTexts.size(); nodeIndex++) {
            String nodeText = nodeTexts.get(nodeIndex);
            String segment = markerCodec.encodeSegment(currentNodeTexts.size(), nodeText);

            boolean exceedsCharBudget =
                    payload.length() > 0 && payload.length() + segment.length() > maxChunkChars;
            boolean exceedsUnitBudget = currentNodeTexts.size() >= maxUnits;

            if (exceedsCharBudget || exceedsUnitBudget) {
                result.add(new Chunk(payload.toString(), currentNodeIndexes, currentNodeTexts));

                currentNodeIndexes = new ArrayList<>();
                currentNodeTexts = new ArrayList<>();
                payload = new StringBuilder();
                segment = markerCodec.encodeSegment(0, nodeText);
            }

            payload.append(segment);
            currentNodeIndexes.add(nodeIndex);
            currentNodeTexts.add(nodeText);
        }

        if (!currentNodeIndexes.isEmpty()) {
            result.add(new Chunk(payload.toString(), currentNodeIndexes, currentNodeTexts));
        }

        return result;
    }

    public static final class Chunk {
        private final String payload;
        private final List<Integer> nodeIndexes;
        private final List<String> originalNodeTexts;

        public Chunk(String payload, List<Integer> nodeIndexes, List<String> originalNodeTexts) {
            this.payload = payload;
            this.nodeIndexes = Collections.unmodifiableList(new ArrayList<>(nodeIndexes));
            this.originalNodeTexts =
                    Collections.unmodifiableList(new ArrayList<>(originalNodeTexts));
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
        public List<String> getOriginalNodeTexts() {
            return originalNodeTexts;
        }
    }
}
