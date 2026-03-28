package io.github.godsarmy.mlhtmltranslator.api;

import androidx.annotation.NonNull;

public interface TranslationTimingListener {

    void onTimingReady(@NonNull TranslationTimingReport report);
}
