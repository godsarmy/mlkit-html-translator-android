package io.github.godsarmy.mlhtmltranslator.backend;

import androidx.annotation.NonNull;
import io.github.godsarmy.mlhtmltranslator.api.TranslationException;

public interface MlTranslationAdapter {

    @NonNull
    String translate(
            @NonNull String text,
            @NonNull String sourceLanguage,
            @NonNull String targetLanguage,
            long timeoutMs)
            throws TranslationException;
}
