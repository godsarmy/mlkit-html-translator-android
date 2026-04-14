package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
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
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.mlkit.nl.translate.TranslateLanguage;
import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.api.MlKitHtmlTranslator;
import io.github.godsarmy.mlhtmltranslator.api.TranslationTimingListener;
import io.github.godsarmy.mlhtmltranslator.api.TranslationTimingReport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private enum SourceMode {
        EXAMPLE,
        FILE,
        URL
    }

    private TranslationViewModel viewModel;
    private MaterialButton modelActionButton;
    private MaterialButton sourceModeButton;
    private MaterialButton sourceModeActionButton;
    private MaterialButton advancedParameterButton;
    private Button translateButton;
    private Button explainButton;
    private Spinner sourceSpinner;
    private Spinner targetSpinner;
    private EditText inputHtmlText;
    private TextView outputHtmlText;
    private WebView inputRenderedHtml;
    private WebView outputRenderedHtml;
    private SwitchMaterial renderModeToggle;
    private View exampleSourceContainer;
    private View fileSourceContainer;
    private View urlSourceContainer;
    private TextView sourceModeLabel;
    private TextView localFileNameText;
    private EditText urlInputText;
    private View translationProgressContainer;
    private TextView translationResultText;
    private boolean isTranslating;
    private int currentRequestCharCount;
    private TranslationTimingReport latestTimingReport;
    private TranslationTimingListener timingListener;
    private TranslationRepository translationRepository;
    private SharedPreferences markerPreferences;
    private SourceMode sourceMode = SourceMode.EXAMPLE;
    private boolean isSourceLoading;

    private static final String PREFS_NAME = "marker_config";
    private static final String KEY_MARKER_START = "marker_start";
    private static final String KEY_MARKER_END = "marker_end";
    private static final String KEY_MAX_CHUNK_CHARS = "max_chunk_chars";
    private static final String KEY_MASK_URLS = "mask_urls";
    private static final String KEY_MASK_PLACEHOLDERS = "mask_placeholders";
    private static final String KEY_MASK_PATHS = "mask_paths";
    private static final String KEY_FAILURE_POLICY = "failure_policy";

    private static final int DEFAULT_MAX_CHUNK_CHARS = 3000;
    private static final boolean DEFAULT_MASK_URLS = true;
    private static final boolean DEFAULT_MASK_PLACEHOLDERS = true;
    private static final boolean DEFAULT_MASK_PATHS = true;
    private static final String DEFAULT_FAILURE_POLICY =
            HtmlTranslationOptions.FailurePolicy.BEST_EFFORT.name();

    private final ActivityResultLauncher<Intent> advancedParametersLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK) {
                            return;
                        }
                        saveAdvancedPreferences(result.getData());
                    });

    private final ActivityResultLauncher<String[]> localFilePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        isSourceLoading = false;
                        if (uri == null) {
                            updateSourceModeUi();
                            return;
                        }
                        loadHtmlFromUri(uri);
                        updateSourceModeUi();
                    });

    private int activeDownloadRequestId;
    private AlertDialog downloadProgressDialog;
    private boolean isDownloadingModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        sourceSpinner = findViewById(R.id.sourceLanguageSpinner);
        targetSpinner = findViewById(R.id.targetLanguageSpinner);
        sourceModeButton = findViewById(R.id.sourceModeButton);
        sourceModeActionButton = findViewById(R.id.sourceModeActionButton);
        sourceModeLabel = findViewById(R.id.sourceModeLabel);
        advancedParameterButton = findViewById(R.id.advancedParameterButton);
        Spinner sampleSpinner = findViewById(R.id.sampleAssetSpinner);
        exampleSourceContainer = findViewById(R.id.exampleSourceContainer);
        fileSourceContainer = findViewById(R.id.fileSourceContainer);
        urlSourceContainer = findViewById(R.id.urlSourceContainer);
        localFileNameText = findViewById(R.id.localFileNameText);
        urlInputText = findViewById(R.id.urlInputText);
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
        markerPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        setupWebView(inputRenderedHtml);
        setupWebView(outputRenderedHtml);
        setupRawOutputScrolling();

        setupSpinner(sourceSpinner, R.array.language_codes);
        setupSpinner(targetSpinner, R.array.language_codes);
        setupSpinner(sampleSpinner, R.array.sample_assets);
        sourceSpinner.setSelection(findSpinnerIndex(sourceSpinner, "en"));
        targetSpinner.setSelection(findSpinnerIndex(targetSpinner, "es"));

        timingListener = report -> latestTimingReport = report;

        MlKitHtmlTranslator translator = buildTranslator();
        translationRepository = new TranslationRepository(translator);
        ModelLifecycleManager modelLifecycleManager = new ModelLifecycleManager();
        viewModel = new TranslationViewModel(translationRepository, modelLifecycleManager);

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
        advancedParameterButton.setOnClickListener(v -> openAdvancedParametersScreen());
        sourceModeButton.setOnClickListener(v -> showSourceModeMenu());
        sourceModeActionButton.setOnClickListener(v -> onSourceActionClicked(sampleSpinner));

        urlInputText.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        // no-op
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (sourceMode == SourceMode.URL) {
                            updateSourceModeUi();
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        // no-op
                    }
                });

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
        updateSourceModeUi();
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
        translationRepository.setTranslator(buildTranslator());

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
        updateModelActionCaption();
        updateExplainButtonState();
    }

    private void updateTranslateButtonState() {
        if (translateButton == null
                || targetSpinner == null
                || sourceSpinner == null
                || viewModel == null) {
            return;
        }

        if (isTranslating || isDownloadingModel) {
            translateButton.setEnabled(false);
            return;
        }

        String sourceLanguage =
                sourceSpinner.getSelectedItem() == null
                        ? ""
                        : sourceSpinner.getSelectedItem().toString();
        String targetLanguage =
                targetSpinner.getSelectedItem() == null
                        ? ""
                        : targetSpinner.getSelectedItem().toString();
        boolean isSourceModelAvailable = viewModel.isModelAvailable(sourceLanguage);
        boolean isTargetModelAvailable = viewModel.isModelAvailable(targetLanguage);
        translateButton.setEnabled(isSourceModelAvailable && isTargetModelAvailable);
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
        startActivity(
                ExplainHtmlActivity.createIntent(
                        this,
                        htmlBody,
                        readMarkerStart(),
                        readMarkerEnd(),
                        readMaxChunkChars(),
                        readMaskUrls(),
                        readMaskPlaceholders(),
                        readMaskPaths(),
                        readFailurePolicy()));
    }

    @NonNull
    private MlKitHtmlTranslator buildTranslator() {
        String markerStart = readMarkerStart();
        String markerEnd = readMarkerEnd();
        String failurePolicyName = readFailurePolicy();
        HtmlTranslationOptions.FailurePolicy failurePolicy =
                HtmlTranslationOptions.FailurePolicy.BEST_EFFORT;
        if (HtmlTranslationOptions.FailurePolicy.FAIL_FAST.name().equals(failurePolicyName)) {
            failurePolicy = HtmlTranslationOptions.FailurePolicy.FAIL_FAST;
        }

        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder()
                        .setTimingListener(timingListener)
                        .setMaxChunkChars(readMaxChunkChars())
                        .setMaskUrls(readMaskUrls())
                        .setMaskPlaceholders(readMaskPlaceholders())
                        .setMaskPaths(readMaskPaths())
                        .setFailurePolicy(failurePolicy)
                        .setPlaceholderMarkerStart(markerStart)
                        .setPlaceholderMarkerEnd(markerEnd)
                        .build();
        return new MlKitHtmlTranslator(getApplicationContext(), options);
    }

    @NonNull
    private String readMarkerStart() {
        String marker =
                markerPreferences.getString(
                        KEY_MARKER_START, getString(R.string.default_placeholder_marker_start));
        if (marker == null) {
            marker = getString(R.string.default_placeholder_marker_start);
        }
        marker = marker.trim();
        return marker.isEmpty() ? getString(R.string.default_placeholder_marker_start) : marker;
    }

    @NonNull
    private String readMarkerEnd() {
        String marker =
                markerPreferences.getString(
                        KEY_MARKER_END, getString(R.string.default_placeholder_marker_end));
        if (marker == null) {
            marker = getString(R.string.default_placeholder_marker_end);
        }
        marker = marker.trim();
        return marker.isEmpty() ? getString(R.string.default_placeholder_marker_end) : marker;
    }

    private int readMaxChunkChars() {
        return Math.max(1, markerPreferences.getInt(KEY_MAX_CHUNK_CHARS, DEFAULT_MAX_CHUNK_CHARS));
    }

    private boolean readMaskUrls() {
        return markerPreferences.getBoolean(KEY_MASK_URLS, DEFAULT_MASK_URLS);
    }

    private boolean readMaskPlaceholders() {
        return markerPreferences.getBoolean(KEY_MASK_PLACEHOLDERS, DEFAULT_MASK_PLACEHOLDERS);
    }

    private boolean readMaskPaths() {
        return markerPreferences.getBoolean(KEY_MASK_PATHS, DEFAULT_MASK_PATHS);
    }

    @NonNull
    private String readFailurePolicy() {
        String value = markerPreferences.getString(KEY_FAILURE_POLICY, DEFAULT_FAILURE_POLICY);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_FAILURE_POLICY;
        }
        return value;
    }

    private void openAdvancedParametersScreen() {
        advancedParametersLauncher.launch(
                AdvancedParametersActivity.createIntent(
                        this,
                        readMarkerStart(),
                        readMarkerEnd(),
                        readMaxChunkChars(),
                        readMaskUrls(),
                        readMaskPlaceholders(),
                        readMaskPaths(),
                        readFailurePolicy()));
    }

    private void saveAdvancedPreferences(Intent data) {
        markerPreferences
                .edit()
                .putString(
                        KEY_MARKER_START,
                        AdvancedParametersActivity.markerStartFromResult(
                                data, getString(R.string.default_placeholder_marker_start)))
                .putString(
                        KEY_MARKER_END,
                        AdvancedParametersActivity.markerEndFromResult(
                                data, getString(R.string.default_placeholder_marker_end)))
                .putInt(
                        KEY_MAX_CHUNK_CHARS,
                        AdvancedParametersActivity.maxChunkCharsFromResult(
                                data, DEFAULT_MAX_CHUNK_CHARS))
                .putBoolean(
                        KEY_MASK_URLS,
                        AdvancedParametersActivity.maskUrlsFromResult(data, DEFAULT_MASK_URLS))
                .putBoolean(
                        KEY_MASK_PLACEHOLDERS,
                        AdvancedParametersActivity.maskPlaceholdersFromResult(
                                data, DEFAULT_MASK_PLACEHOLDERS))
                .putBoolean(
                        KEY_MASK_PATHS,
                        AdvancedParametersActivity.maskPathsFromResult(data, DEFAULT_MASK_PATHS))
                .putString(
                        KEY_FAILURE_POLICY,
                        AdvancedParametersActivity.failurePolicyFromResult(
                                data, DEFAULT_FAILURE_POLICY))
                .apply();
    }

    private void showSourceModeMenu() {
        PopupMenu popupMenu = new PopupMenu(this, sourceModeButton);
        popupMenu.getMenu().add(0, SourceMode.EXAMPLE.ordinal(), 0, R.string.source_mode_example);
        popupMenu.getMenu().add(0, SourceMode.FILE.ordinal(), 1, R.string.source_mode_file);
        popupMenu.getMenu().add(0, SourceMode.URL.ordinal(), 2, R.string.source_mode_url);
        popupMenu.setOnMenuItemClickListener(
                item -> {
                    int id = item.getItemId();
                    if (id == SourceMode.EXAMPLE.ordinal()) {
                        sourceMode = SourceMode.EXAMPLE;
                    } else if (id == SourceMode.FILE.ordinal()) {
                        sourceMode = SourceMode.FILE;
                    } else {
                        sourceMode = SourceMode.URL;
                    }
                    updateSourceModeUi();
                    return true;
                });
        popupMenu.show();
    }

    private void onSourceActionClicked(Spinner sampleSpinner) {
        if (sourceMode == SourceMode.EXAMPLE) {
            sampleSpinner.performClick();
            return;
        }
        if (sourceMode == SourceMode.FILE) {
            isSourceLoading = true;
            updateSourceModeUi();
            localFilePickerLauncher.launch(
                    new String[] {"text/html", "application/xhtml+xml", "text/plain", "*/*"});
            return;
        }
        loadHtmlFromUrlInput();
    }

    private void updateSourceModeUi() {
        if (sourceModeLabel == null) {
            return;
        }
        int modeTextRes;
        int actionTextRes;
        boolean actionEnabled = true;
        boolean showActionButton = true;
        switch (sourceMode) {
            case FILE:
                modeTextRes = R.string.source_mode_file;
                actionTextRes = R.string.source_action_load;
                break;
            case URL:
                modeTextRes = R.string.source_mode_url;
                actionTextRes = R.string.source_action_load;
                actionEnabled = isValidHttpUrl(urlInputText.getText().toString());
                break;
            case EXAMPLE:
            default:
                modeTextRes = R.string.source_mode_example;
                actionTextRes = R.string.source_action_pick;
                showActionButton = false;
                break;
        }

        actionEnabled = actionEnabled && !isSourceLoading;

        sourceModeLabel.setText(modeTextRes);
        sourceModeActionButton.setText(actionTextRes);
        sourceModeActionButton.setEnabled(actionEnabled);
        sourceModeActionButton.setVisibility(showActionButton ? View.VISIBLE : View.GONE);

        exampleSourceContainer.setVisibility(
                sourceMode == SourceMode.EXAMPLE ? View.VISIBLE : View.GONE);
        fileSourceContainer.setVisibility(sourceMode == SourceMode.FILE ? View.VISIBLE : View.GONE);
        urlSourceContainer.setVisibility(sourceMode == SourceMode.URL ? View.VISIBLE : View.GONE);
    }

    private void loadHtmlFromUri(@NonNull Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Input stream is null");
            }
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
            }
            inputHtmlText.setText(builder.toString().trim());
            localFileNameText.setText(resolveDisplayName(uri));
            refreshRenderedPreviewIfNeeded();
            updateExplainButtonState();
        } catch (IOException e) {
            Toast.makeText(this, R.string.source_mode_file_load_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadHtmlFromUrlInput() {
        String urlText = urlInputText.getText().toString().trim();
        if (!isValidHttpUrl(urlText)) {
            Toast.makeText(this, R.string.source_mode_invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        isSourceLoading = true;
        updateSourceModeUi();
        new Thread(
                        () -> {
                            String content;
                            try {
                                content = readUrlContent(urlText);
                            } catch (IOException e) {
                                runOnUiThread(
                                        () -> {
                                            isSourceLoading = false;
                                            updateSourceModeUi();
                                            Toast.makeText(
                                                            MainActivity.this,
                                                            R.string.source_mode_url_load_failed,
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                        });
                                return;
                            }

                            final String loadedContent = content;
                            runOnUiThread(
                                    () -> {
                                        isSourceLoading = false;
                                        inputHtmlText.setText(loadedContent);
                                        refreshRenderedPreviewIfNeeded();
                                        updateExplainButtonState();
                                        updateSourceModeUi();
                                    });
                        })
                .start();
    }

    @NonNull
    private String readUrlContent(@NonNull String urlText) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("Unexpected status code: " + statusCode);
            }

            try (InputStream inputStream = connection.getInputStream();
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                return builder.toString().trim();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isValidHttpUrl(@NonNull String value) {
        if (value.trim().isEmpty()) {
            return false;
        }
        try {
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (Exception ignored) {
            return false;
        }
    }

    @NonNull
    private String resolveDisplayName(@NonNull Uri uri) {
        String lastSegment = uri.getLastPathSegment();
        if (lastSegment == null || lastSegment.trim().isEmpty()) {
            return getString(R.string.source_mode_file);
        }
        int slashIndex = lastSegment.lastIndexOf('/');
        return slashIndex >= 0 ? lastSegment.substring(slashIndex + 1) : lastSegment;
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
            showDeleteModelConfirmationDialog(targetLanguage);
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

    private void showDeleteModelConfirmationDialog(@NonNull String targetLanguage) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.model_delete_confirmation_title)
                .setMessage(getString(R.string.model_delete_confirmation_message, targetLanguage))
                .setNegativeButton(R.string.cancel_download, null)
                .setPositiveButton(
                        R.string.action_delete_model,
                        (dialog, which) -> deleteModel(targetLanguage))
                .show();
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
        updateTranslateButtonState();

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

    private int findSpinnerIndex(Spinner spinner, String value) {
        if (spinner == null || value == null) {
            return 0;
        }
        ArrayAdapter<?> adapter = (ArrayAdapter<?>) spinner.getAdapter();
        if (adapter == null) {
            return 0;
        }
        for (int index = 0; index < adapter.getCount(); index++) {
            Object item = adapter.getItem(index);
            if (item != null && value.equalsIgnoreCase(item.toString())) {
                return index;
            }
        }
        return 0;
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
