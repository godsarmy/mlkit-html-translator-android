package io.github.godsarmy.mlhtmltranslator.sample;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.api.MlKitHtmlTranslator;
import io.github.godsarmy.mlhtmltranslator.api.TranslationTimingListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private TranslationViewModel viewModel;
    private TextView timingText;
    private CheckBox timingCheckbox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Spinner sourceSpinner = findViewById(R.id.sourceLanguageSpinner);
        Spinner targetSpinner = findViewById(R.id.targetLanguageSpinner);
        Spinner sampleSpinner = findViewById(R.id.sampleAssetSpinner);
        EditText inputHtml = findViewById(R.id.inputHtml);
        TextView outputHtml = findViewById(R.id.outputHtml);
        TextView errorCode = findViewById(R.id.errorCode);
        TextView modelStatus = findViewById(R.id.modelStatus);
        timingText = findViewById(R.id.timingReport);
        timingCheckbox = findViewById(R.id.enableTimingCheckbox);

        setupSpinner(sourceSpinner, R.array.language_codes);
        setupSpinner(targetSpinner, R.array.language_codes);
        setupSpinner(sampleSpinner, R.array.sample_assets);
        sourceSpinner.setSelection(0);
        targetSpinner.setSelection(1);

        TranslationTimingListener timingListener =
                report ->
                        runOnUiThread(
                                () -> {
                                    if (!timingCheckbox.isChecked()) {
                                        timingText.setText(
                                                getString(R.string.timing_disabled_message));
                                        return;
                                    }
                                    String timingSummary =
                                            "durationMs="
                                                    + report.getDurationMs()
                                                    + ", chunks="
                                                    + report.getChunkCount()
                                                    + ", totalNodes="
                                                    + report.getTotalNodes()
                                                    + ", translatedNodes="
                                                    + report.getTranslatedNodes()
                                                    + ", failedNodes="
                                                    + report.getFailedNodes()
                                                    + ", retries="
                                                    + report.getRetryCount();
                                    timingText.setText(timingSummary);
                                });

        MlKitHtmlTranslator translator =
                new MlKitHtmlTranslator(
                        HtmlTranslationOptions.builder().setTimingListener(timingListener).build());
        TranslationRepository repository = new TranslationRepository(translator);
        ModelLifecycleManager modelLifecycleManager = new ModelLifecycleManager();
        viewModel = new TranslationViewModel(repository, modelLifecycleManager);

        viewModel.translatedHtml().observe(this, outputHtml::setText);
        viewModel
                .errorCode()
                .observe(
                        this,
                        code -> {
                            if (code == null) {
                                errorCode.setText(getString(R.string.error_none));
                            } else {
                                errorCode.setText(code);
                            }
                        });
        viewModel.modelStatus().observe(this, modelStatus::setText);

        Button translateButton = findViewById(R.id.translateButton);
        translateButton.setOnClickListener(
                v ->
                        viewModel.translate(
                                inputHtml.getText().toString(),
                                sourceSpinner.getSelectedItem().toString(),
                                targetSpinner.getSelectedItem().toString()));

        Button downloadButton = findViewById(R.id.downloadModelButton);
        downloadButton.setOnClickListener(
                v -> viewModel.downloadModel(targetSpinner.getSelectedItem().toString()));

        Button deleteButton = findViewById(R.id.deleteModelButton);
        deleteButton.setOnClickListener(
                v -> viewModel.deleteModel(targetSpinner.getSelectedItem().toString()));

        Button checkButton = findViewById(R.id.checkModelButton);
        checkButton.setOnClickListener(
                v -> viewModel.checkModel(targetSpinner.getSelectedItem().toString()));

        Button loadSampleButton = findViewById(R.id.loadSampleButton);
        loadSampleButton.setOnClickListener(
                v -> inputHtml.setText(loadAssetHtml(sampleSpinner.getSelectedItem().toString())));

        inputHtml.setText(loadAssetHtml("manual-like.html"));
        timingText.setText(getString(R.string.timing_disabled_message));
    }

    private void setupSpinner(Spinner spinner, int arrayRes) {
        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(
                        this, arrayRes, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private String loadAssetHtml(String fileName) {
        try (InputStream inputStream = getAssets().open("html/" + fileName);
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString().trim();
        } catch (IOException e) {
            return "<p>Failed to load sample asset: " + fileName + "</p>";
        }
    }
}
