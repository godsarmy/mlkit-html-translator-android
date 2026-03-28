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
    private final MutableLiveData<String> errorText = new MutableLiveData<>();

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
    public LiveData<String> errorText() {
        return errorText;
    }

    public void translate(String htmlBody, String sourceLanguage, String targetLanguage) {
        if (!modelLifecycleManager.isModelAvailable(sourceLanguage)
                || !modelLifecycleManager.isModelAvailable(targetLanguage)) {
            errorText.setValue(TranslationErrorCode.MODEL_UNAVAILABLE.name());
            return;
        }

        repository.translate(
                htmlBody,
                sourceLanguage,
                targetLanguage,
                new TranslationCallback() {
                    @Override
                    public void onSuccess(@NonNull String translatedHtmlValue) {
                        translatedHtml.setValue(translatedHtmlValue);
                        errorText.setValue(null);
                    }

                    @Override
                    public void onFailure(@NonNull TranslationException exception) {
                        errorText.setValue(exception.getErrorCode().name());
                    }
                });
    }
}
