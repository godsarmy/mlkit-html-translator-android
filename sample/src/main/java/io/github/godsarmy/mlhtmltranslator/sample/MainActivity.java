package io.github.godsarmy.mlhtmltranslator.sample;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import io.github.godsarmy.mlhtmltranslator.api.MlKitHtmlTranslator;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView output = findViewById(R.id.output);

        MlKitHtmlTranslator translator = new MlKitHtmlTranslator();
        TranslationRepository repository = new TranslationRepository(translator);
        ModelLifecycleManager modelLifecycleManager = new ModelLifecycleManager();
        TranslationViewModel viewModel =
                new TranslationViewModel(repository, modelLifecycleManager);

        viewModel.translatedHtml().observe(this, output::setText);
        viewModel
                .errorText()
                .observe(
                        this,
                        error -> {
                            if (error != null) {
                                output.setText(error);
                            }
                        });

        viewModel.translate("<p>Hello world</p>", "en", "es");
    }
}
