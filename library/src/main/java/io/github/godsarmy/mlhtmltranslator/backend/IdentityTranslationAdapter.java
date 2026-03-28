package io.github.godsarmy.mlhtmltranslator.backend;

import androidx.annotation.NonNull;

public final class IdentityTranslationAdapter implements MlTranslationAdapter {

    @NonNull
    @Override
    public String translate(
            @NonNull String text,
            @NonNull String sourceLanguage,
            @NonNull String targetLanguage,
            long timeoutMs) {
        return text;
    }
}
