package io.github.godsarmy.mlhtmltranslator.backend;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import io.github.godsarmy.mlhtmltranslator.api.TranslationErrorCode;
import io.github.godsarmy.mlhtmltranslator.api.TranslationException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MlKitTranslationAdapter implements MlTranslationAdapter, AutoCloseable {

    private final Map<String, Translator> translatorsByPair = new ConcurrentHashMap<>();

    public MlKitTranslationAdapter(@NonNull Context context) {}

    @NonNull
    @Override
    public String translate(
            @NonNull String text,
            @NonNull String sourceLanguage,
            @NonNull String targetLanguage,
            long timeoutMs)
            throws TranslationException {
        String source = normalizeLanguageCode(sourceLanguage);
        String target = normalizeLanguageCode(targetLanguage);

        if (source == null || target == null) {
            throw new TranslationException(
                    TranslationErrorCode.INVALID_INPUT,
                    "Unsupported sourceLanguage or targetLanguage for ML Kit");
        }

        if (source.equals(target)) {
            return text;
        }

        Translator translator = getTranslator(source, target);
        try {
            if (timeoutMs == 0L) {
                return Tasks.await(translator.translate(text));
            }
            return Tasks.await(translator.translate(text), timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new TranslationException(
                    TranslationErrorCode.CANCELLED,
                    "Translation interrupted",
                    interruptedException);
        } catch (TimeoutException timeoutException) {
            throw new TranslationException(
                    TranslationErrorCode.TRANSLATION_FAILED,
                    "Translation timed out",
                    timeoutException);
        } catch (ExecutionException executionException) {
            throw mapToTranslationException(executionException);
        }
    }

    @Override
    public void close() {
        for (Translator translator : translatorsByPair.values()) {
            translator.close();
        }
        translatorsByPair.clear();
    }

    @NonNull
    private Translator getTranslator(@NonNull String source, @NonNull String target) {
        String key = source + "->" + target;
        return translatorsByPair.computeIfAbsent(
                key,
                unused -> {
                    TranslatorOptions options =
                            new TranslatorOptions.Builder()
                                    .setSourceLanguage(source)
                                    .setTargetLanguage(target)
                                    .build();
                    return Translation.getClient(options);
                });
    }

    @NonNull
    private TranslationException mapToTranslationException(
            @NonNull ExecutionException executionException) {
        Throwable cause = executionException.getCause();
        if (cause instanceof MlKitException) {
            MlKitException mlKitException = (MlKitException) cause;
            TranslationErrorCode errorCode =
                    mlKitException.getErrorCode() == MlKitException.CANCELLED
                            ? TranslationErrorCode.CANCELLED
                            : TranslationErrorCode.TRANSLATION_FAILED;
            String message = mlKitException.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "ML Kit translation failed";
            }
            if (message.toLowerCase(Locale.ROOT).contains("model")) {
                errorCode = TranslationErrorCode.MODEL_UNAVAILABLE;
            }
            return new TranslationException(errorCode, message, mlKitException);
        }

        String message = cause != null ? cause.getMessage() : executionException.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "Translation execution failed";
        }
        return new TranslationException(
                TranslationErrorCode.TRANSLATION_FAILED, message, executionException);
    }

    private static String normalizeLanguageCode(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        String normalizedInput = languageCode.trim();
        String translatedLanguage = TranslateLanguage.fromLanguageTag(normalizedInput);
        if (translatedLanguage != null) {
            return translatedLanguage;
        }

        int separatorIndex = normalizedInput.indexOf('-');
        if (separatorIndex < 0) {
            separatorIndex = normalizedInput.indexOf('_');
        }
        if (separatorIndex > 0) {
            return TranslateLanguage.fromLanguageTag(normalizedInput.substring(0, separatorIndex));
        }

        return null;
    }
}
