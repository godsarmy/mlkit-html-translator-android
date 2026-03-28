package io.github.godsarmy.mlhtmltranslator.cache;

import androidx.annotation.NonNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class TranslationCacheKeyFactory {

    private static final String KEY_VERSION = "v1";

    private TranslationCacheKeyFactory() {}

    @NonNull
    public static String create(
            @NonNull String htmlBody,
            @NonNull String sourceLanguage,
            @NonNull String targetLanguage,
            @NonNull String optionsVersion) {
        String raw =
                htmlBody
                        + "\n|src="
                        + sourceLanguage
                        + "|tgt="
                        + targetLanguage
                        + "|opt="
                        + optionsVersion
                        + "|key="
                        + KEY_VERSION;
        return sha256(raw);
    }

    @NonNull
    private static String sha256(@NonNull String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
