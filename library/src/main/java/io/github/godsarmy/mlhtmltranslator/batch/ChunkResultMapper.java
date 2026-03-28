package io.github.godsarmy.mlhtmltranslator.batch;

import androidx.annotation.NonNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChunkResultMapper {

    @NonNull
    public Map<Integer, String> mapChunkResult(
            @NonNull ChunkBuilder.Chunk chunk,
            @NonNull String translatedChunkPayload,
            @NonNull SegmentMarkerCodec markerCodec) {
        List<String> splitSegments =
                markerCodec.splitSegments(translatedChunkPayload, chunk.getNodeIndexes().size());

        Map<Integer, String> mapped = new LinkedHashMap<>();
        for (int i = 0; i < splitSegments.size(); i++) {
            mapped.put(chunk.getNodeIndexes().get(i), splitSegments.get(i));
        }
        return mapped;
    }
}
