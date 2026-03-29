package io.github.godsarmy.mlhtmltranslator.mask;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TokenMasker {

    private static final Pattern PLACEHOLDER_KEY_PATTERN =
            Pattern.compile("^(.+?)PH(\\d+)(?:_(\\d+))?(.+)$");

    public static final class MaskingConfig {
        private final boolean maskUrls;
        private final boolean maskPlaceholders;
        private final boolean maskPaths;
        private final String placeholderMarkerStart;
        private final String placeholderMarkerEnd;

        public MaskingConfig(boolean maskUrls, boolean maskPlaceholders, boolean maskPaths) {
            this(maskUrls, maskPlaceholders, maskPaths, "[{[", "]}]");
        }

        public MaskingConfig(
                boolean maskUrls,
                boolean maskPlaceholders,
                boolean maskPaths,
                @NonNull String placeholderMarkerStart,
                @NonNull String placeholderMarkerEnd) {
            if (placeholderMarkerStart.trim().isEmpty()) {
                throw new IllegalArgumentException("placeholderMarkerStart cannot be blank");
            }
            if (placeholderMarkerEnd.trim().isEmpty()) {
                throw new IllegalArgumentException("placeholderMarkerEnd cannot be blank");
            }
            this.maskUrls = maskUrls;
            this.maskPlaceholders = maskPlaceholders;
            this.maskPaths = maskPaths;
            this.placeholderMarkerStart = placeholderMarkerStart;
            this.placeholderMarkerEnd = placeholderMarkerEnd;
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

        @NonNull
        public String getPlaceholderMarkerStart() {
            return placeholderMarkerStart;
        }

        @NonNull
        public String getPlaceholderMarkerEnd() {
            return placeholderMarkerEnd;
        }
    }

    public static final class MaskingResult {
        private final String maskedText;
        private final Map<String, String> placeholderToOriginal;

        MaskingResult(String maskedText, Map<String, String> placeholderToOriginal) {
            this.maskedText = maskedText;
            this.placeholderToOriginal = placeholderToOriginal;
        }

        @NonNull
        public String getMaskedText() {
            return maskedText;
        }

        @NonNull
        public Map<String, String> getPlaceholderToOriginal() {
            return placeholderToOriginal;
        }
    }

    private static final Comparator<TokenMatch> TOKEN_MATCH_COMPARATOR =
            Comparator.comparingInt(TokenMatch::getStart)
                    .thenComparing(
                            (first, second) -> Integer.compare(second.getEnd(), first.getEnd()))
                    .thenComparingInt(TokenMatch::getPriority);

    @NonNull
    public MaskingResult mask(@NonNull String input, @NonNull MaskingConfig config) {
        List<TokenPatternRegistry.TokenDetector> detectors = TokenPatternRegistry.detectors(config);
        List<TokenMatch> rawMatches = new ArrayList<>();

        for (int i = 0; i < detectors.size(); i++) {
            TokenPatternRegistry.TokenDetector detector = detectors.get(i);
            Matcher matcher = detector.getPattern().matcher(input);
            while (matcher.find()) {
                if (matcher.end() <= matcher.start()) {
                    continue;
                }
                rawMatches.add(new TokenMatch(matcher.start(), matcher.end(), matcher.group(), i));
            }
        }

        if (rawMatches.isEmpty()) {
            return new MaskingResult(input, Collections.emptyMap());
        }

        rawMatches.sort(TOKEN_MATCH_COMPARATOR);
        List<TokenMatch> acceptedMatches = selectNonOverlapping(rawMatches);

        StringBuilder maskedText = new StringBuilder();
        Map<String, String> placeholderToOriginal = new LinkedHashMap<>();
        Set<String> usedPlaceholders = new LinkedHashSet<>();

        int cursor = 0;
        for (int i = 0; i < acceptedMatches.size(); i++) {
            TokenMatch match = acceptedMatches.get(i);

            if (match.getStart() > cursor) {
                maskedText.append(input, cursor, match.getStart());
            }

            String placeholder = nextPlaceholder(input, usedPlaceholders, i, config);
            usedPlaceholders.add(placeholder);
            placeholderToOriginal.put(placeholder, match.getText());
            maskedText.append(placeholder);
            cursor = match.getEnd();
        }

        if (cursor < input.length()) {
            maskedText.append(input.substring(cursor));
        }

        return new MaskingResult(
                maskedText.toString(), Collections.unmodifiableMap(placeholderToOriginal));
    }

    @NonNull
    public String unmask(
            @NonNull String translatedText, @NonNull Map<String, String> placeholderToOriginal) {
        String restored = translatedText;

        // Fast path: exact canonical placeholders.
        for (Map.Entry<String, String> entry : placeholderToOriginal.entrySet()) {
            restored = restored.replace(entry.getKey(), entry.getValue());
        }

        // Tolerant path: case/spacing variants (e.g. [{[Ph0]}]).
        List<PlaceholderDescriptor> descriptors = parseDescriptors(placeholderToOriginal.keySet());
        for (PlaceholderDescriptor descriptor : descriptors) {
            Pattern flexPattern = descriptor.toFlexiblePattern();
            restored =
                    flexPattern
                            .matcher(restored)
                            .replaceAll(
                                    Matcher.quoteReplacement(
                                            placeholderToOriginal.get(descriptor.key)));
        }

        // Handle detached ids only when empty wrappers appear (e.g. PH0[{[ ]}]).
        for (PlaceholderDescriptor descriptor : descriptors) {
            Pattern orphanWrapperPattern = descriptor.toOrphanWrapperPattern();
            if (!orphanWrapperPattern.matcher(restored).find()) {
                continue;
            }
            restored = orphanWrapperPattern.matcher(restored).replaceAll("");
            restored =
                    descriptor
                            .toDetachedIdPattern()
                            .matcher(restored)
                            .replaceAll(Matcher.quoteReplacement(descriptor.key));
            restored = restored.replace(descriptor.key, placeholderToOriginal.get(descriptor.key));
        }

        return restored;
    }

    @NonNull
    private static List<PlaceholderDescriptor> parseDescriptors(
            @NonNull Set<String> placeholderKeys) {
        List<PlaceholderDescriptor> descriptors = new ArrayList<>();
        for (String key : placeholderKeys) {
            Matcher matcher = PLACEHOLDER_KEY_PATTERN.matcher(key);
            if (!matcher.matches()) {
                continue;
            }
            descriptors.add(
                    new PlaceholderDescriptor(
                            key,
                            matcher.group(1),
                            matcher.group(2),
                            matcher.group(3),
                            matcher.group(4)));
        }
        return descriptors;
    }

    @NonNull
    private static List<TokenMatch> selectNonOverlapping(@NonNull List<TokenMatch> sortedMatches) {
        List<TokenMatch> accepted = new ArrayList<>();
        int currentEnd = -1;
        for (TokenMatch match : sortedMatches) {
            if (match.getStart() < currentEnd) {
                continue;
            }
            accepted.add(match);
            currentEnd = match.getEnd();
        }
        return accepted;
    }

    @NonNull
    private static String nextPlaceholder(
            @NonNull String original,
            @NonNull Set<String> usedPlaceholders,
            int index,
            @NonNull MaskingConfig config) {
        int salt = 0;
        while (true) {
            String placeholder =
                    canonicalPlaceholder(
                            String.valueOf(index),
                            salt == 0 ? null : String.valueOf(salt),
                            config.getPlaceholderMarkerStart(),
                            config.getPlaceholderMarkerEnd());

            if (!usedPlaceholders.contains(placeholder) && !original.contains(placeholder)) {
                return placeholder;
            }
            salt++;
        }
    }

    @NonNull
    private static String canonicalPlaceholder(
            @NonNull String index,
            String salt,
            @NonNull String markerStart,
            @NonNull String markerEnd) {
        return salt == null
                ? markerStart + "PH" + index + markerEnd
                : markerStart + "PH" + index + "_" + salt + markerEnd;
    }

    @NonNull
    private static String markerRegex(@NonNull String marker) {
        if (marker.length() > 1 && isRepeatedChar(marker)) {
            return Pattern.quote(marker.substring(0, 1)) + "+";
        }
        return Pattern.quote(marker);
    }

    private static boolean isRepeatedChar(@NonNull String text) {
        char first = text.charAt(0);
        for (int i = 1; i < text.length(); i++) {
            if (text.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private static final class PlaceholderDescriptor {
        private final String key;
        private final String markerStart;
        private final String index;
        private final String salt;
        private final String markerEnd;

        private PlaceholderDescriptor(
                @NonNull String key,
                @NonNull String markerStart,
                @NonNull String index,
                String salt,
                @NonNull String markerEnd) {
            this.key = key;
            this.markerStart = markerStart;
            this.index = index;
            this.salt = salt;
            this.markerEnd = markerEnd;
        }

        @NonNull
        Pattern toFlexiblePattern() {
            String idPattern =
                    salt == null
                            ? "[Pp]\\s*[Hh]?\\s*" + index
                            : "[Pp]\\s*[Hh]?\\s*" + index + "\\s*_\\s*" + salt;
            return Pattern.compile(
                    markerRegex(markerStart)
                            + "\\s*"
                            + idPattern
                            + "\\s*"
                            + markerRegex(markerEnd));
        }

        @NonNull
        Pattern toOrphanWrapperPattern() {
            return Pattern.compile(markerRegex(markerStart) + "\\s*" + markerRegex(markerEnd));
        }

        @NonNull
        Pattern toDetachedIdPattern() {
            String idPattern =
                    salt == null
                            ? "(?:PH|P)\\s*" + index
                            : "(?:PH|P)\\s*" + index + "\\s*_\\s*" + salt;
            return Pattern.compile(
                    "(?<![A-Za-z0-9_])" + idPattern + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
        }
    }

    private static final class TokenMatch {
        private final int start;
        private final int end;
        private final String text;
        private final int priority;

        TokenMatch(int start, int end, String text, int priority) {
            this.start = start;
            this.end = end;
            this.text = text;
            this.priority = priority;
        }

        int getStart() {
            return start;
        }

        int getEnd() {
            return end;
        }

        String getText() {
            return text;
        }

        int getPriority() {
            return priority;
        }
    }
}
