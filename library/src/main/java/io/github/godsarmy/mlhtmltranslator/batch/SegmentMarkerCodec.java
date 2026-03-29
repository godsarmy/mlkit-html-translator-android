package io.github.godsarmy.mlhtmltranslator.batch;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SegmentMarkerCodec {

    private static final String ASCII_MARKER_PREFIX = "@@MLHT";

    private final String sessionPrefix;
    private final Pattern markerPattern;

    public SegmentMarkerCodec() {
        this(randomSessionPrefix());
    }

    public SegmentMarkerCodec(@NonNull String sessionPrefix) {
        this.sessionPrefix = sessionPrefix;
        String escapedPrefix = Pattern.quote(sessionPrefix);
        this.markerPattern =
                Pattern.compile(
                        "(?:⟦\\s*M\\s*"
                                + escapedPrefix
                                + "\\s*:\\s*(\\d+)\\s*⟧|@@\\s*MLHT\\s*"
                                + escapedPrefix
                                + "\\s*:\\s*(\\d+)\\s*@@)");
    }

    @NonNull
    public String markerFor(int segmentIndex) {
        if (segmentIndex < 0) {
            throw new IllegalArgumentException("segmentIndex must be >= 0");
        }
        return ASCII_MARKER_PREFIX + sessionPrefix + ":" + segmentIndex + "@@";
    }

    @NonNull
    public String encodeSegment(int segmentIndex, @NonNull String text) {
        return markerFor(segmentIndex) + text;
    }

    @NonNull
    public List<String> splitSegments(@NonNull String chunkText, int expectedSegmentCount) {
        if (expectedSegmentCount < 0) {
            throw new IllegalArgumentException("expectedSegmentCount must be >= 0");
        }
        if (expectedSegmentCount == 0) {
            return new ArrayList<>();
        }

        Matcher matcher = markerPattern.matcher(chunkText);
        List<MarkerMatch> markers = new ArrayList<>();
        while (matcher.find()) {
            String segmentGroup = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            int segmentIndex = Integer.parseInt(segmentGroup);
            markers.add(new MarkerMatch(segmentIndex, matcher.start(), matcher.end()));
        }

        if (markers.size() != expectedSegmentCount) {
            throw new IllegalArgumentException(
                    "marker count mismatch. expected="
                            + expectedSegmentCount
                            + ", actual="
                            + markers.size());
        }

        List<String> segments = new ArrayList<>(expectedSegmentCount);
        for (int i = 0; i < expectedSegmentCount; i++) {
            segments.add(null);
        }

        for (int i = 0; i < markers.size(); i++) {
            MarkerMatch current = markers.get(i);
            if (current.segmentIndex < 0 || current.segmentIndex >= expectedSegmentCount) {
                throw new IllegalArgumentException(
                        "marker index out of range: " + current.segmentIndex);
            }
            if (segments.get(current.segmentIndex) != null) {
                throw new IllegalArgumentException(
                        "duplicate marker index: " + current.segmentIndex);
            }

            int contentStart = current.end;
            int contentEnd =
                    (i + 1 < markers.size()) ? markers.get(i + 1).start : chunkText.length();
            segments.set(current.segmentIndex, chunkText.substring(contentStart, contentEnd));
        }

        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i) == null) {
                throw new IllegalArgumentException("missing marker index: " + i);
            }
        }

        return segments;
    }

    @NonNull
    private static String randomSessionPrefix() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }

    private static final class MarkerMatch {
        private final int segmentIndex;
        private final int start;
        private final int end;

        MarkerMatch(int segmentIndex, int start, int end) {
            this.segmentIndex = segmentIndex;
            this.start = start;
            this.end = end;
        }
    }
}
