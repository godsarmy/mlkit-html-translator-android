package io.github.godsarmy.mlhtmltranslator.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ChunkBuilderTest {

    @Test
    public void buildChunks_preservesNodeOrder_andRespectsConstraints() {
        ChunkBuilder builder = new ChunkBuilder();
        SegmentMarkerCodec codec = new SegmentMarkerCodec("ABCD1234");

        List<ChunkBuilder.Chunk> chunks =
                builder.buildChunks(Arrays.asList("a", "bb", "ccc", "dddd"), 1000, 2, codec);

        assertEquals(2, chunks.size());
        assertEquals(Arrays.asList(0, 1), chunks.get(0).getNodeIndexes());
        assertEquals(Arrays.asList(2, 3), chunks.get(1).getNodeIndexes());
    }

    @Test
    public void chunkResultMapper_mapsBackToOriginalNodeIndexes() {
        ChunkBuilder builder = new ChunkBuilder();
        SegmentMarkerCodec codec = new SegmentMarkerCodec("ABCD1234");
        ChunkResultMapper mapper = new ChunkResultMapper();

        List<ChunkBuilder.Chunk> chunks =
                builder.buildChunks(Arrays.asList("alpha", "beta", "gamma"), 100, null, codec);

        ChunkBuilder.Chunk chunk = chunks.get(0);
        String translatedPayload =
                "@@MLHTABCD1234:0@@uno@@MLHTABCD1234:1@@dos@@MLHTABCD1234:2@@tres";

        assertEquals("uno", mapper.mapChunkResult(chunk, translatedPayload, codec).get(0));
        assertEquals("dos", mapper.mapChunkResult(chunk, translatedPayload, codec).get(1));
        assertEquals("tres", mapper.mapChunkResult(chunk, translatedPayload, codec).get(2));
        assertTrue(mapper.mapChunkResult(chunk, translatedPayload, codec).containsKey(2));
    }
}
