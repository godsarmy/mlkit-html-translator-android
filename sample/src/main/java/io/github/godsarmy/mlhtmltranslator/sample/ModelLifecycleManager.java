package io.github.godsarmy.mlhtmltranslator.sample;

import androidx.annotation.NonNull;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import java.util.HashSet;
import java.util.Set;

public final class ModelLifecycleManager {

    public interface RefreshCallback {
        void onComplete();

        void onError(@NonNull String reason);
    }

    public interface ActionCallback {
        void onSuccess();

        void onFailure(@NonNull String reason);
    }

    private final RemoteModelManager remoteModelManager = RemoteModelManager.getInstance();
    private final DownloadConditions downloadConditions = new DownloadConditions.Builder().build();
    private final Set<String> downloadedModels = new HashSet<>();

    public ModelLifecycleManager() {
        downloadedModels.add(TranslateLanguage.ENGLISH);
    }

    public boolean isModelAvailable(String languageCode) {
        String normalized = normalizeLanguageCode(languageCode);
        if (normalized == null) {
            return false;
        }
        return isBuiltInLanguage(normalized) || downloadedModels.contains(normalized);
    }

    public void refreshDownloadedModels(@NonNull RefreshCallback callback) {
        remoteModelManager
                .getDownloadedModels(TranslateRemoteModel.class)
                .addOnSuccessListener(
                        models -> {
                            synchronized (downloadedModels) {
                                downloadedModels.clear();
                                downloadedModels.add(TranslateLanguage.ENGLISH);
                                for (TranslateRemoteModel model : models) {
                                    downloadedModels.add(model.getLanguage());
                                }
                            }
                            callback.onComplete();
                        })
                .addOnFailureListener(
                        error ->
                                callback.onError(messageOrDefault(error, "Failed to load models")));
    }

    public void downloadModel(@NonNull String languageCode, @NonNull ActionCallback callback) {
        String normalized = normalizeLanguageCode(languageCode);
        if (normalized == null) {
            callback.onFailure("Unsupported language code");
            return;
        }
        if (isBuiltInLanguage(normalized) || isModelAvailable(normalized)) {
            callback.onSuccess();
            return;
        }

        TranslateRemoteModel model = new TranslateRemoteModel.Builder(normalized).build();
        remoteModelManager
                .download(model, downloadConditions)
                .addOnSuccessListener(
                        unused -> {
                            synchronized (downloadedModels) {
                                downloadedModels.add(normalized);
                            }
                            callback.onSuccess();
                        })
                .addOnFailureListener(
                        error ->
                                callback.onFailure(
                                        messageOrDefault(
                                                error, "Failed to download language model")));
    }

    public void deleteModel(@NonNull String languageCode, @NonNull ActionCallback callback) {
        String normalized = normalizeLanguageCode(languageCode);
        if (normalized == null) {
            callback.onFailure("Unsupported language code");
            return;
        }
        if (isBuiltInLanguage(normalized)) {
            callback.onFailure("Built-in model cannot be deleted");
            return;
        }

        TranslateRemoteModel model = new TranslateRemoteModel.Builder(normalized).build();
        remoteModelManager
                .deleteDownloadedModel(model)
                .addOnSuccessListener(
                        unused -> {
                            synchronized (downloadedModels) {
                                downloadedModels.remove(normalized);
                            }
                            callback.onSuccess();
                        })
                .addOnFailureListener(
                        error ->
                                callback.onFailure(
                                        messageOrDefault(
                                                error, "Failed to delete language model")));
    }

    private static boolean isBuiltInLanguage(@NonNull String languageCode) {
        return TranslateLanguage.ENGLISH.equals(languageCode);
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

    private static String messageOrDefault(@NonNull Exception error, @NonNull String fallback) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message;
    }
}
