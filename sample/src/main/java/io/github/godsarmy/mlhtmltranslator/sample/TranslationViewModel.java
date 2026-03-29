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
    private final MutableLiveData<String> modelStatus = new MutableLiveData<>();

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

    @NonNull
    public LiveData<String> modelStatus() {
        return modelStatus;
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

    public void downloadModel(String languageCode) {
        boolean changed = modelLifecycleManager.downloadModel(languageCode);
        modelStatus.postValue(
                changed
                        ? "Downloaded model: " + languageCode
                        : "Model already downloaded: " + languageCode);
    }

    public boolean isModelAvailable(String languageCode) {
        return modelLifecycleManager.isModelAvailable(languageCode);
    }

    public void deleteModel(String languageCode) {
        boolean changed = modelLifecycleManager.deleteModel(languageCode);
        modelStatus.postValue(
                changed ? "Deleted model: " + languageCode : "Model not found: " + languageCode);
    }

    public void checkModel(String languageCode) {
        boolean available = modelLifecycleManager.isModelAvailable(languageCode);
        modelStatus.postValue((available ? "Available" : "Missing") + " model: " + languageCode);
    }
}
