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

public final class TokenMasker {

    public static final class MaskingConfig {
        private final boolean maskUrls;
        private final boolean maskPlaceholders;
        private final boolean maskPaths;

        public MaskingConfig(boolean maskUrls, boolean maskPlaceholders, boolean maskPaths) {
            this.maskUrls = maskUrls;
            this.maskPlaceholders = maskPlaceholders;
            this.maskPaths = maskPaths;
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

            String placeholder = nextPlaceholder(input, usedPlaceholders, i);
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
        for (Map.Entry<String, String> entry : placeholderToOriginal.entrySet()) {
            restored = restored.replace(entry.getKey(), entry.getValue());
        }
        return restored;
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
            @NonNull String original, @NonNull Set<String> usedPlaceholders, int index) {
        int salt = 0;
        while (true) {
            String placeholder =
                    salt == 0 ? "@@P" + index + "@@" : "@@P" + index + "_" + salt + "@@";

            if (!usedPlaceholders.contains(placeholder) && !original.contains(placeholder)) {
                return placeholder;
            }
            salt++;
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
