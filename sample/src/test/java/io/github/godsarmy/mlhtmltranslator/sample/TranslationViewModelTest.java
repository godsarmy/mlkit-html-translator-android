package io.github.godsarmy.mlhtmltranslator.sample;

import static org.junit.Assert.assertEquals;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import io.github.godsarmy.mlhtmltranslator.api.MlKitHtmlTranslator;
import org.junit.Rule;
import org.junit.Test;

public class TranslationViewModelTest {

    @Rule public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Test
    public void missingModelPrecondition_surfacesModelUnavailableErrorCode() {
        MlKitHtmlTranslator translator = new MlKitHtmlTranslator();
        TranslationRepository repository = new TranslationRepository(translator);
        ModelLifecycleManager modelLifecycleManager = new ModelLifecycleManager();
        TranslationViewModel viewModel =
                new TranslationViewModel(repository, modelLifecycleManager);

        // Default manager has only 'en'. Using 'ja' target should fail precondition.
        viewModel.translate("<p>Hello</p>", "en", "ja");

        assertEquals("MODEL_UNAVAILABLE", viewModel.errorCode().getValue());
    }
}
