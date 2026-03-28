package io.github.godsarmy.mlhtmltranslator.api;

import androidx.annotation.NonNull;

public interface TranslationCallback {

    void onSuccess(@NonNull String translatedHtml);

    void onFailure(@NonNull TranslationException exception);
}
