package io.github.godsarmy.mlhtmltranslator.sample;

import io.github.godsarmy.mlhtmltranslator.api.MlKitHtmlTranslator;
import io.github.godsarmy.mlhtmltranslator.api.TranslationCallback;

public final class TranslationRepository implements AutoCloseable {

    private MlKitHtmlTranslator translator;

    public TranslationRepository(MlKitHtmlTranslator translator) {
        this.translator = translator;
    }

    public synchronized void setTranslator(MlKitHtmlTranslator translator) {
        if (this.translator != null) {
            this.translator.close();
        }
        this.translator = translator;
    }

    public synchronized void translate(
            String htmlBody,
            String sourceLanguage,
            String targetLanguage,
            TranslationCallback callback) {
        translator.translateHtml(htmlBody, sourceLanguage, targetLanguage, callback);
    }

    @Override
    public synchronized void close() {
        if (translator != null) {
            translator.close();
            translator = null;
        }
    }
}
