package io.github.godsarmy.mlhtmltranslator.sample;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.mlkit.nl.translate.TranslateLanguage;
import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.api.MlKitHtmlTranslator;
import io.github.godsarmy.mlhtmltranslator.api.TranslationTimingListener;
import io.github.godsarmy.mlhtmltranslator.api.TranslationTimingReport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private TranslationViewModel viewModel;
    private MaterialButton modelActionButton;
    private Button translateButton;
    private Button explainButton;
    private Spinner targetSpinner;
    private EditText inputHtmlText;
    private TextView outputHtmlText;
    private WebView inputRenderedHtml;
    private WebView outputRenderedHtml;
    private CheckBox renderModeToggle;
    private View translationProgressContainer;
    private TextView translationResultText;
    private boolean isTranslating;
    private int currentRequestCharCount;
    private TranslationTimingReport latestTimingReport;

    private int activeDownloadRequestId;
    private AlertDialog downloadProgressDialog;
    private boolean isDownloadingModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Spinner sourceSpinner = findViewById(R.id.sourceLanguageSpinner);
        targetSpinner = findViewById(R.id.targetLanguageSpinner);
        Spinner sampleSpinner = findViewById(R.id.sampleAssetSpinner);
        inputHtmlText = findViewById(R.id.inputHtml);
        outputHtmlText = findViewById(R.id.outputHtml);
        inputRenderedHtml = findViewById(R.id.inputRenderedHtml);
        outputRenderedHtml = findViewById(R.id.outputRenderedHtml);
        renderModeToggle = findViewById(R.id.renderModeToggle);
        translationProgressContainer = findViewById(R.id.translationProgressContainer);
        translationResultText = findViewById(R.id.translationResultText);
        modelActionButton = findViewById(R.id.downloadModelButton);
        translateButton = findViewById(R.id.translateButton);
        explainButton = findViewById(R.id.explainButton);

        setupWebView(inputRenderedHtml);
        setupWebView(outputRenderedHtml);
        setupRawOutputScrolling();

        setupSpinner(sourceSpinner, R.array.language_codes);
        setupSpinner(targetSpinner, R.array.language_codes);
        setupSpinner(sampleSpinner, R.array.sample_assets);
        sourceSpinner.setSelection(0);
        targetSpinner.setSelection(1);

        TranslationTimingListener timingListener = report -> latestTimingReport = report;

        MlKitHtmlTranslator translator =
                new MlKitHtmlTranslator(
                        getApplicationContext(),
                        HtmlTranslationOptions.builder().setTimingListener(timingListener).build());
        TranslationRepository repository = new TranslationRepository(translator);
        ModelLifecycleManager modelLifecycleManager = new ModelLifecycleManager();
        viewModel = new TranslationViewModel(repository, modelLifecycleManager);

        viewModel
                .translatedHtml()
                .observe(
                        this,
                        translatedHtml -> {
                            if (!isTranslating) {
                                return;
                            }
                            outputHtmlText.setText(translatedHtml);
                            refreshRenderedPreviewIfNeeded();
                            showSuccessStatus();
                        });
        viewModel
                .errorReason()
                .observe(
                        this,
                        reason -> {
                            if (!isTranslating || reason == null) {
                                return;
                            }
                            showFailureStatus(reason);
                        });

        translateButton.setOnClickListener(
                v -> startTranslation(sourceSpinner.getSelectedItem().toString()));
        explainButton.setOnClickListener(v -> openExplainScreen());

        modelActionButton.setOnClickListener(v -> onModelActionClicked());

        targetSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, android.view.View view, int position, long id) {
                        refreshDownloadedModels();
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
                        inputHtmlText.setText(
                                loadAssetHtml(sampleSpinner.getSelectedItem().toString()));
                        refreshRenderedPreviewIfNeeded();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // no-op
                    }
                });

        renderModeToggle.setOnCheckedChangeListener(
                (buttonView, isChecked) -> applyRenderMode(isChecked));

        inputHtmlText.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        // no-op
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        updateExplainButtonState();
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        // no-op
                    }
                });

        inputHtmlText.setText(loadAssetHtml(sampleSpinner.getSelectedItem().toString()));
        translationResultText.setText("");
        translationResultText.setVisibility(View.GONE);
        translationProgressContainer.setVisibility(View.GONE);
        applyRenderMode(renderModeToggle.isChecked());
        refreshDownloadedModels();
        updateExplainButtonState();
    }

    private void startTranslation(String sourceLanguage) {
        String htmlBody = inputHtmlText.getText().toString();
        String targetLanguage = targetSpinner.getSelectedItem().toString();

        currentRequestCharCount = htmlBody.length();
        latestTimingReport = null;
        isTranslating = true;
        translationResultText.setText("");
        translationResultText.setVisibility(View.GONE);
        translationProgressContainer.setVisibility(View.VISIBLE);
        translateButton.setEnabled(false);
        modelActionButton.setEnabled(false);
        updateExplainButtonState();

        viewModel.translate(htmlBody, sourceLanguage, targetLanguage);
    }

    private void showSuccessStatus() {
        TranslationTimingReport report = latestTimingReport;
        long durationMs = report != null ? report.getDurationMs() : 0L;
        int chunkCount = report != null ? report.getChunkCount() : 0;
        int translatedNodes = report != null ? report.getTranslatedNodes() : 0;
        int totalNodes = report != null ? report.getTotalNodes() : 0;
        int failedNodes = report != null ? report.getFailedNodes() : 0;
        int retries = report != null ? report.getRetryCount() : 0;

        translationResultText.setText(
                getString(
                        R.string.translation_result_success,
                        currentRequestCharCount,
                        durationMs,
                        chunkCount,
                        translatedNodes,
                        totalNodes,
                        failedNodes,
                        retries));
        finishTranslationState(false);
    }

    private void showFailureStatus(String reason) {
        String failureReason =
                "MODEL_UNAVAILABLE".equals(reason)
                        ? getString(R.string.translation_model_unavailable)
                        : reason;
        translationResultText.setText(
                getString(R.string.translation_result_failure, failureReason));
        finishTranslationState(true);
    }

    private void finishTranslationState(boolean failed) {
        isTranslating = false;
        translationProgressContainer.setVisibility(View.GONE);
        translationResultText.setVisibility(View.VISIBLE);
        translationResultText.setTextColor(
                getColor(failed ? R.color.mlkit_error : R.color.mlkit_on_surface_variant));
        translateButton.setEnabled(true);
        updateModelActionCaption();
        updateExplainButtonState();
    }

    private void updateExplainButtonState() {
        if (explainButton == null || inputHtmlText == null) {
            return;
        }
        String htmlBody = inputHtmlText.getText().toString();
        explainButton.setEnabled(!isTranslating && !htmlBody.trim().isEmpty());
    }

    private void openExplainScreen() {
        String htmlBody = inputHtmlText.getText().toString();
        if (htmlBody.trim().isEmpty()) {
            return;
        }
        startActivity(ExplainHtmlActivity.createIntent(this, htmlBody));
    }

    private void applyRenderMode(boolean renderModeEnabled) {
        if (renderModeEnabled) {
            inputHtmlText.setVisibility(android.view.View.GONE);
            outputHtmlText.setVisibility(android.view.View.GONE);
            inputRenderedHtml.setVisibility(android.view.View.VISIBLE);
            outputRenderedHtml.setVisibility(android.view.View.VISIBLE);
            refreshRenderedPreview();
            return;
        }

        inputRenderedHtml.setVisibility(android.view.View.GONE);
        outputRenderedHtml.setVisibility(android.view.View.GONE);
        inputHtmlText.setVisibility(android.view.View.VISIBLE);
        outputHtmlText.setVisibility(android.view.View.VISIBLE);
    }

    private void refreshRenderedPreviewIfNeeded() {
        if (renderModeToggle != null && renderModeToggle.isChecked()) {
            refreshRenderedPreview();
        }
    }

    private void refreshRenderedPreview() {
        renderHtml(inputRenderedHtml, inputHtmlText.getText().toString());
        renderHtml(outputRenderedHtml, outputHtmlText.getText().toString());
    }

    private void renderHtml(WebView webView, String htmlBody) {
        String safeBody = htmlBody == null ? "" : htmlBody;
        String textColor = toCssColor(getColor(R.color.mlkit_on_background));
        String linkColor = toCssColor(getColor(R.color.mlkit_primary));
        String wrappedHtml =
                "<!doctype html><html><head><meta charset=\"utf-8\" />"
                        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />"
                        + "<style>"
                        + "html,body{margin:0;padding:0;background:transparent !important;color:"
                        + textColor
                        + " !important;}"
                        + "a{color:"
                        + linkColor
                        + ";}"
                        + "img,table{max-width:100%;height:auto;}"
                        + "</style></head><body>"
                        + safeBody
                        + "</body></html>";
        webView.loadDataWithBaseURL("about:blank", wrappedHtml, "text/html", "UTF-8", null);
    }

    private String toCssColor(int color) {
        return String.format(
                "rgba(%d,%d,%d,%.3f)",
                android.graphics.Color.red(color),
                android.graphics.Color.green(color),
                android.graphics.Color.blue(color),
                android.graphics.Color.alpha(color) / 255f);
    }

    private void setupWebView(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setBackgroundColor(Color.TRANSPARENT);
    }

    private void setupRawOutputScrolling() {
        outputHtmlText.setMovementMethod(new ScrollingMovementMethod());
        outputHtmlText.setOnTouchListener(
                (view, event) -> {
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    if (event.getAction() == MotionEvent.ACTION_UP
                            || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return false;
                });
    }

    private void onModelActionClicked() {
        if (isDownloadingModel) {
            return;
        }

        String targetLanguage = targetSpinner.getSelectedItem().toString();
        if (viewModel.isModelAvailable(targetLanguage)) {
            deleteModel(targetLanguage);
            return;
        }

        startModelDownload(targetLanguage);
    }

    private void startModelDownload(String targetLanguage) {
        int requestId = ++activeDownloadRequestId;
        isDownloadingModel = true;
        updateModelActionCaption();
        showDownloadProgressDialog(targetLanguage, requestId);

        viewModel.downloadModel(
                targetLanguage,
                new ModelLifecycleManager.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        if (requestId != activeDownloadRequestId) {
                            return;
                        }
                        activeDownloadRequestId = 0;
                        isDownloadingModel = false;
                        dismissDownloadProgressDialog();
                        refreshDownloadedModels();
                    }

                    @Override
                    public void onFailure(@NonNull String reason) {
                        if (requestId != activeDownloadRequestId) {
                            return;
                        }
                        activeDownloadRequestId = 0;
                        isDownloadingModel = false;
                        dismissDownloadProgressDialog();
                        showFailureStatus(reason);
                    }
                });
    }

    private void deleteModel(String targetLanguage) {
        isDownloadingModel = true;
        updateModelActionCaption();
        viewModel.deleteModel(
                targetLanguage,
                new ModelLifecycleManager.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        isDownloadingModel = false;
                        refreshDownloadedModels();
                    }

                    @Override
                    public void onFailure(@NonNull String reason) {
                        isDownloadingModel = false;
                        showFailureStatus(reason);
                    }
                });
    }

    private void showDownloadProgressDialog(String targetLanguage, int requestId) {
        dismissDownloadProgressDialog();
        downloadProgressDialog =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.model_download_progress_title)
                        .setMessage(
                                getString(R.string.model_download_progress_message, targetLanguage))
                        .setCancelable(true)
                        .setNegativeButton(
                                R.string.cancel_download,
                                (dialog, which) -> cancelModelDownload(requestId))
                        .setOnCancelListener(dialog -> cancelModelDownload(requestId))
                        .create();
        downloadProgressDialog.show();
    }

    private void cancelModelDownload(int requestId) {
        if (!isDownloadingModel || requestId != activeDownloadRequestId) {
            return;
        }
        activeDownloadRequestId = 0;
        isDownloadingModel = false;
        dismissDownloadProgressDialog();
        updateModelActionCaption();
    }

    private void refreshDownloadedModels() {
        viewModel.refreshDownloadedModels(
                new ModelLifecycleManager.RefreshCallback() {
                    @Override
                    public void onComplete() {
                        updateModelActionCaption();
                    }

                    @Override
                    public void onError(@NonNull String reason) {
                        updateModelActionCaption();
                    }
                });
    }

    private void dismissDownloadProgressDialog() {
        if (downloadProgressDialog != null && downloadProgressDialog.isShowing()) {
            downloadProgressDialog.dismiss();
        }
        downloadProgressDialog = null;
    }

    private void updateModelActionCaption() {
        if (isDownloadingModel || isTranslating) {
            modelActionButton.setEnabled(false);
            return;
        }

        String targetLanguage =
                targetSpinner.getSelectedItem() == null
                        ? ""
                        : targetSpinner.getSelectedItem().toString();
        boolean available = viewModel.isModelAvailable(targetLanguage);
        String normalizedTarget = TranslateLanguage.fromLanguageTag(targetLanguage);
        boolean builtIn = TranslateLanguage.ENGLISH.equals(normalizedTarget);
        modelActionButton.setIconResource(
                available ? R.drawable.ic_model_delete : R.drawable.ic_model_download);
        modelActionButton.setContentDescription(
                getString(
                        available
                                ? R.string.delete_model_icon_content_description
                                : R.string.download_model_icon_content_description));
        modelActionButton.setEnabled(!builtIn);
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
        dismissDownloadProgressDialog();
        if (inputRenderedHtml != null) {
            inputRenderedHtml.destroy();
        }
        if (outputRenderedHtml != null) {
            outputRenderedHtml.destroy();
        }
        super.onDestroy();
    }
}
