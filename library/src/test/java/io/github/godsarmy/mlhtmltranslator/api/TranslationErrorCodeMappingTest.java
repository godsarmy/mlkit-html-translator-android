package io.github.godsarmy.mlhtmltranslator.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import org.junit.Test;

public class TranslationErrorCodeMappingTest {

    @Test
    public void invalidInputMapsToInvalidInputCode() {
        MlKitHtmlTranslator translator = new MlKitHtmlTranslator();
        CapturingCallback callback = new CapturingCallback();

        translator.translateHtml("   ", "en", "es", callback);

        assertNotNull(callback.exception);
        assertEquals(TranslationErrorCode.INVALID_INPUT, callback.exception.getErrorCode());
    }

    @Test
    public void backendFailureMapsToTranslationFailedCode() {
        MlTranslationAdapter failingAdapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    throw new TranslationException(
                            TranslationErrorCode.TRANSLATION_FAILED, "backend failure");
                };

        MlKitHtmlTranslator translator =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder()
                                .setFailurePolicy(HtmlTranslationOptions.FailurePolicy.FAIL_FAST)
                                .build(),
                        failingAdapter);
        CapturingCallback callback = new CapturingCallback();

        translator.translateHtml("<p>Hello</p>", "en", "es", callback);

        assertNotNull(callback.exception);
        assertEquals(TranslationErrorCode.TRANSLATION_FAILED, callback.exception.getErrorCode());
    }

    private static final class CapturingCallback implements TranslationCallback {
        private String translated;
        private TranslationException exception;

        @Override
        public void onSuccess(String translatedHtml) {
            this.translated = translatedHtml;
        }

        @Override
        public void onFailure(TranslationException exception) {
            this.exception = exception;
        }
    }
}
