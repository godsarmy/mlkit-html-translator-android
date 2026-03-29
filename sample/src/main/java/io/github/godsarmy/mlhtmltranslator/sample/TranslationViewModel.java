package io.github.godsarmy.mlhtmltranslator.sample;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import io.github.godsarmy.mlhtmltranslator.api.TranslationCallback;
import io.github.godsarmy.mlhtmltranslator.api.TranslationErrorCode;
import io.github.godsarmy.mlhtmltranslator.api.TranslationException;

public final class TranslationViewModel extends ViewModel {

    private final TranslationRepository repository;
    private final ModelLifecycleManager modelLifecycleManager;
    private final MutableLiveData<String> translatedHtml = new MutableLiveData<>();
    private final MutableLiveData<String> errorReason = new MutableLiveData<>();

    public TranslationViewModel(
            @NonNull TranslationRepository repository,
            @NonNull ModelLifecycleManager modelLifecycleManager) {
        this.repository = repository;
        this.modelLifecycleManager = modelLifecycleManager;
    }

    @NonNull
    public LiveData<String> translatedHtml() {
        return translatedHtml;
    }

    @NonNull
    public LiveData<String> errorReason() {
        return errorReason;
    }

    public void translate(String htmlBody, String sourceLanguage, String targetLanguage) {
        if (!modelLifecycleManager.isModelAvailable(sourceLanguage)
                || !modelLifecycleManager.isModelAvailable(targetLanguage)) {
            errorReason.postValue(TranslationErrorCode.MODEL_UNAVAILABLE.name());
            return;
        }

        repository.translate(
                htmlBody,
                sourceLanguage,
                targetLanguage,
                new TranslationCallback() {
                    @Override
                    public void onSuccess(@NonNull String translatedHtmlValue) {
                        translatedHtml.postValue(translatedHtmlValue);
                        errorReason.postValue(null);
                    }

                    @Override
                    public void onFailure(@NonNull TranslationException exception) {
                        String reason = exception.getMessage();
                        if (reason == null || reason.trim().isEmpty()) {
                            reason = exception.getErrorCode().name();
                        }
                        errorReason.postValue(reason);
                    }
                });
    }

    public void refreshDownloadedModels(@NonNull ModelLifecycleManager.RefreshCallback callback) {
        modelLifecycleManager.refreshDownloadedModels(callback);
    }

    public void downloadModel(
            @NonNull String languageCode, @NonNull ModelLifecycleManager.ActionCallback callback) {
        modelLifecycleManager.downloadModel(languageCode, callback);
    }

    public boolean isModelAvailable(String languageCode) {
        return modelLifecycleManager.isModelAvailable(languageCode);
    }

    public void deleteModel(
            @NonNull String languageCode, @NonNull ModelLifecycleManager.ActionCallback callback) {
        modelLifecycleManager.deleteModel(languageCode, callback);
    }
}
