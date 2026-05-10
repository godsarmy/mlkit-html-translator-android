package io.github.godsarmy.mlhtmltranslator.api;

import androidx.annotation.NonNull;

public interface TranslationMetricsListener {

    void onMetricsReady(@NonNull TranslationMetricsReport report);
}
