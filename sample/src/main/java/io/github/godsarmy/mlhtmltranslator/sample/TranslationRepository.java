package io.github.godsarmy.mlhtmltranslator.sample;

import io.github.godsarmy.mlhtmltranslator.api.MlKitHtmlTranslator;
import io.github.godsarmy.mlhtmltranslator.api.TranslationCallback;

public final class TranslationRepository {

    private final MlKitHtmlTranslator translator;

    public TranslationRepository(MlKitHtmlTranslator translator) {
        this.translator = translator;
    }

    public void translate(
            String htmlBody,
            String sourceLanguage,
            String targetLanguage,
            TranslationCallback callback) {
        translator.translateHtml(htmlBody, sourceLanguage, targetLanguage, callback);
    }
}
