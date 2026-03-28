package io.github.godsarmy.mlhtmltranslator.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface TranslationCache {

    @Nullable
    String get(@NonNull String key);

    void put(@NonNull String key, @NonNull String translatedHtml);

    void clear();
}
