package io.github.godsarmy.mlhtmltranslator.batch;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public class SegmentMarkerCodecTest {

    @Test
    public void splitSegments_isTolerantToMarkerWhitespace() {
        SegmentMarkerCodec codec = new SegmentMarkerCodec("ABCD1234");
        String translated = "⟦ M ABCD1234 : 0 ⟧hola⟦MABCD1234:1⟧mundo";

        List<String> split = codec.splitSegments(translated, 2);

        assertEquals(2, split.size());
        assertEquals("hola", split.get(0));
        assertEquals("mundo", split.get(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void splitSegments_throwsWhenMarkerCountMismatches() {
        SegmentMarkerCodec codec = new SegmentMarkerCodec("ABCD1234");
        String translated = "⟦MABCD1234:0⟧one";
        codec.splitSegments(translated, 2);
    }
}
