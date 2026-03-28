package io.github.godsarmy.mlhtmltranslator.mask;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

final class TokenPatternRegistry {

    private static final Pattern URL_PATTERN =
            Pattern.compile("\\b(?:https?://|www\\.)[^\\s<>\"']+");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("(?:%\\d*\\$?[a-zA-Z]|\\{[A-Za-z0-9_.-]+}|\\$\\{[A-Za-z0-9_.-]+})");
    private static final Pattern SHELL_FLAG_PATTERN =
            Pattern.compile("(?<!\\w)(?:--[A-Za-z0-9][A-Za-z0-9-]*|-{1}[A-Za-z])(?!\\w)");
    private static final Pattern PATH_PATTERN =
            Pattern.compile("(?:~|\\.{1,2}|/)[A-Za-z0-9._~/-]*[A-Za-z0-9_/-]");

    private TokenPatternRegistry() {}

    @NonNull
    static List<TokenDetector> detectors(TokenMasker.MaskingConfig config) {
        List<TokenDetector> detectors = new ArrayList<>();
        if (config.isMaskUrls()) {
            detectors.add(new TokenDetector(TokenType.URL, URL_PATTERN));
        }
        detectors.add(new TokenDetector(TokenType.EMAIL, EMAIL_PATTERN));
        if (config.isMaskPlaceholders()) {
            detectors.add(new TokenDetector(TokenType.PLACEHOLDER, PLACEHOLDER_PATTERN));
        }
        detectors.add(new TokenDetector(TokenType.SHELL_FLAG, SHELL_FLAG_PATTERN));
        if (config.isMaskPaths()) {
            detectors.add(new TokenDetector(TokenType.PATH, PATH_PATTERN));
        }
        return Collections.unmodifiableList(detectors);
    }

    enum TokenType {
        URL,
        EMAIL,
        PLACEHOLDER,
        SHELL_FLAG,
        PATH
    }

    static final class TokenDetector {
        private final TokenType tokenType;
        private final Pattern pattern;

        TokenDetector(TokenType tokenType, Pattern pattern) {
            this.tokenType = tokenType;
            this.pattern = pattern;
        }

        TokenType getTokenType() {
            return tokenType;
        }

        Pattern getPattern() {
            return pattern;
        }
    }
}
