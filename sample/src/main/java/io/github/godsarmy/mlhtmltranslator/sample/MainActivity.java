package io.github.godsarmy.mlhtmltranslator.sample;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
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

    private static final long MODEL_DOWNLOAD_DELAY_MS = 2200L;

    private TranslationViewModel viewModel;
    private TextView timingText;
    private Button modelActionButton;
    private Spinner targetSpinner;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingDownloadRunnable;
    private AlertDialog downloadProgressDialog;
    private boolean isDownloadingModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Spinner sourceSpinner = findViewById(R.id.sourceLanguageSpinner);
        targetSpinner = findViewById(R.id.targetLanguageSpinner);
        Spinner sampleSpinner = findViewById(R.id.sampleAssetSpinner);
        EditText inputHtml = findViewById(R.id.inputHtml);
        TextView outputHtml = findViewById(R.id.outputHtml);
        TextView errorCode = findViewById(R.id.errorCode);
        timingText = findViewById(R.id.timingReport);
        modelActionButton = findViewById(R.id.downloadModelButton);

        setupSpinner(sourceSpinner, R.array.language_codes);
        setupSpinner(targetSpinner, R.array.language_codes);
        setupSpinner(sampleSpinner, R.array.sample_assets);
        sourceSpinner.setSelection(0);
        targetSpinner.setSelection(1);

        TranslationTimingListener timingListener =
                report ->
                        runOnUiThread(
                                () -> {
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
        ModelLifecycleManager modelLifecycleManager =
                new ModelLifecycleManager(getApplicationContext());
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

        Button translateButton = findViewById(R.id.translateButton);
        translateButton.setOnClickListener(
                v ->
                        viewModel.translate(
                                inputHtml.getText().toString(),
                                sourceSpinner.getSelectedItem().toString(),
                                targetSpinner.getSelectedItem().toString()));

        modelActionButton.setOnClickListener(v -> onModelActionClicked());

        targetSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, android.view.View view, int position, long id) {
                        updateModelActionCaption();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        updateModelActionCaption();
                    }
                });

        sampleSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, android.view.View view, int position, long id) {
                        inputHtml.setText(
                                loadAssetHtml(sampleSpinner.getSelectedItem().toString()));
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // no-op
                    }
                });

        inputHtml.setText(loadAssetHtml(sampleSpinner.getSelectedItem().toString()));
        timingText.setText("");
        updateModelActionCaption();
    }

    private void onModelActionClicked() {
        if (isDownloadingModel) {
            return;
        }

        String targetLanguage = targetSpinner.getSelectedItem().toString();
        if (viewModel.isModelAvailable(targetLanguage)) {
            viewModel.deleteModel(targetLanguage);
            updateModelActionCaption();
            return;
        }

        startModelDownload(targetLanguage);
    }

    private void startModelDownload(String targetLanguage) {
        isDownloadingModel = true;
        modelActionButton.setEnabled(false);
        showDownloadProgressDialog(targetLanguage);

        pendingDownloadRunnable =
                () -> {
                    pendingDownloadRunnable = null;
                    dismissDownloadProgressDialog();
                    viewModel.downloadModel(targetLanguage);
                    isDownloadingModel = false;
                    updateModelActionCaption();
                };
        handler.postDelayed(pendingDownloadRunnable, MODEL_DOWNLOAD_DELAY_MS);
    }

    private void showDownloadProgressDialog(String targetLanguage) {
        dismissDownloadProgressDialog();
        downloadProgressDialog =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.model_download_progress_title)
                        .setMessage(
                                getString(R.string.model_download_progress_message, targetLanguage))
                        .setCancelable(true)
                        .setNegativeButton(
                                R.string.cancel_download,
                                (dialog, which) -> cancelModelDownload(targetLanguage))
                        .setOnCancelListener(dialog -> cancelModelDownload(targetLanguage))
                        .create();
        downloadProgressDialog.show();
    }

    private void cancelModelDownload(String targetLanguage) {
        if (!isDownloadingModel) {
            return;
        }
        if (pendingDownloadRunnable != null) {
            handler.removeCallbacks(pendingDownloadRunnable);
            pendingDownloadRunnable = null;
        }
        isDownloadingModel = false;
        dismissDownloadProgressDialog();
        updateModelActionCaption();
    }

    private void dismissDownloadProgressDialog() {
        if (downloadProgressDialog != null && downloadProgressDialog.isShowing()) {
            downloadProgressDialog.dismiss();
        }
        downloadProgressDialog = null;
    }

    private void updateModelActionCaption() {
        if (isDownloadingModel) {
            modelActionButton.setEnabled(false);
            return;
        }

        String targetLanguage =
                targetSpinner.getSelectedItem() == null
                        ? ""
                        : targetSpinner.getSelectedItem().toString();
        boolean available = viewModel.isModelAvailable(targetLanguage);
        modelActionButton.setText(
                available ? R.string.action_delete_model : R.string.action_download_model);
        modelActionButton.setEnabled(true);
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

    @Override
    protected void onDestroy() {
        if (pendingDownloadRunnable != null) {
            handler.removeCallbacks(pendingDownloadRunnable);
            pendingDownloadRunnable = null;
        }
        dismissDownloadProgressDialog();
        super.onDestroy();
    }
}
