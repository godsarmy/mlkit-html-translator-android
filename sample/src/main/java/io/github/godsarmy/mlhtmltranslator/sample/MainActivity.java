package io.github.godsarmy.mlhtmltranslator.sample;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import io.github.godsarmy.mlhtmltranslator.api.MlKitHtmlTranslator;
import io.github.godsarmy.mlhtmltranslator.api.TranslationCallback;
import io.github.godsarmy.mlhtmltranslator.api.TranslationException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView output = findViewById(R.id.output);
        MlKitHtmlTranslator translator = new MlKitHtmlTranslator();
        translator.translateHtml(
                "<p>Hello world</p>",
                "en",
                "es",
                new TranslationCallback() {
                    @Override
                    public void onSuccess(String translatedHtml) {
                        output.setText(translatedHtml);
                    }

                    @Override
                    public void onFailure(TranslationException exception) {
                        output.setText(exception.getMessage());
                    }
                });
    }
}
