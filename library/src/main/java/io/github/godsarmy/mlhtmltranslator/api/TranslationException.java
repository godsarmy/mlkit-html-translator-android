package io.github.godsarmy.mlhtmltranslator.api;

import androidx.annotation.NonNull;

public final class TranslationException extends Exception {

    private final TranslationErrorCode errorCode;

    public TranslationException(@NonNull TranslationErrorCode errorCode, @NonNull String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TranslationException(
            @NonNull TranslationErrorCode errorCode,
            @NonNull String message,
            @NonNull Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    @NonNull
    public TranslationErrorCode getErrorCode() {
        return errorCode;
    }
}
