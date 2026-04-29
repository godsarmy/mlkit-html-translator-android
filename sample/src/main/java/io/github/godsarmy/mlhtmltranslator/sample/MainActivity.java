package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private enum SourceEntryType {
        SAMPLE,
        LOAD_FILE,
        LOAD_URL
    }

    private TranslationViewModel viewModel;
    private ImageButton leftMenuButton;
    private DrawerLayout mainDrawerLayout;
    private NavigationView leftNavigationView;
    private Button translateButton;
    private Button explainButton;
    private Spinner sourceSpinner;
    private Spinner targetSpinner;
    private AppCompatAutoCompleteTextView sampleAssetInput;
    private EditText inputHtmlText;
    private TextView outputHtmlText;
    private WebView inputRenderedHtml;
    private WebView outputRenderedHtml;
    private SwitchMaterial renderModeToggle;
    private ImageButton compareModeButton;
    private ImageButton saveTranslatedButton;
    private ImageButton shareTranslatedButton;
    private View exampleSourceContainer;
    private View translationProgressContainer;
    private TextView translationProgressText;
    private TextView translationResultText;
    private boolean isTranslating;
    private int currentRequestCharCount;
    private TranslationTimingReport latestTimingReport;
    private TranslationTimingListener timingListener;
    private TranslationRepository translationRepository;
    private SharedPreferences markerPreferences;
    private boolean isSourceLoading;
    private final List<SourceSelectorEntry> sourceEntries = new ArrayList<>();
    private final List<String> downloadedLanguageOptions = new ArrayList<>();
    private int selectedSourcePosition;
    private KeyListener sampleAssetInputKeyListener;
    private ActivityResultLauncher<String[]> htmlFilePickerLauncher;
    private String selectedFileName;

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

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            refreshDownloadedModels();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        sourceSpinner = findViewById(R.id.sourceLanguageSpinner);
        targetSpinner = findViewById(R.id.targetLanguageSpinner);
        leftMenuButton = findViewById(R.id.leftMenuButton);
        mainDrawerLayout = findViewById(R.id.mainDrawerLayout);
        leftNavigationView = findViewById(R.id.leftNavigationView);
        applyDrawerHeaderInsets();
        sampleAssetInput = findViewById(R.id.sampleAssetInput);
        exampleSourceContainer = findViewById(R.id.exampleSourceContainer);
        inputHtmlText = findViewById(R.id.inputHtml);
        outputHtmlText = findViewById(R.id.outputHtml);
        inputRenderedHtml = findViewById(R.id.inputRenderedHtml);
        outputRenderedHtml = findViewById(R.id.outputRenderedHtml);
        renderModeToggle = findViewById(R.id.renderModeToggle);
        compareModeButton = findViewById(R.id.compareModeButton);
        saveTranslatedButton = findViewById(R.id.saveTranslatedButton);
        shareTranslatedButton = findViewById(R.id.shareTranslatedButton);
        translationProgressContainer = findViewById(R.id.translationProgressContainer);
        translationProgressText = findViewById(R.id.translationProgressText);
        translationResultText = findViewById(R.id.translationResultText);
        translateButton = findViewById(R.id.translateButton);
        explainButton = findViewById(R.id.explainButton);
        markerPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        registerHtmlFilePickerLauncher();

        setupWebView(inputRenderedHtml);
        setupWebView(outputRenderedHtml);
        setupRawOutputScrolling();

        setupLanguageSpinners();

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
                            updateShareButtonState();
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
        compareModeButton.setOnClickListener(v -> openSideBySideCompare());
        saveTranslatedButton.setOnClickListener(v -> saveTranslatedHtml());
        shareTranslatedButton.setOnClickListener(v -> shareTranslatedHtml());

        leftMenuButton.setOnClickListener(v -> mainDrawerLayout.openDrawer(GravityCompat.START));
        leftNavigationView.setNavigationItemSelectedListener(this::onDrawerItemSelected);
        updateVersionMenuItemTitle();
        setupSourceSelector();

        sampleAssetInput.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (isLoadUrlSelected()) {
                            sampleAssetInput.dismissDropDown();
                        }
                        updateSourceInputState();
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        sampleAssetInput.setOnTouchListener(
                (v, event) -> {
                    if (event.getAction() != MotionEvent.ACTION_UP) {
                        return false;
                    }
                    if (isTapOnEndDrawable(sampleAssetInput, event)) {
                        sampleAssetInput.showDropDown();
                        return true;
                    }
                    if (isLoadUrlSelected() && isTapOnStartDrawable(sampleAssetInput, event)) {
                        loadHtmlFromUrlInput();
                        return true;
                    }
                    return false;
                });

        sampleAssetInput.setOnClickListener(
                v -> {
                    if (isLoadFileSelected()) {
                        launchHtmlFilePicker();
                        return;
                    }
                    if (!isLoadUrlSelected()) {
                        sampleAssetInput.showDropDown();
                    }
                });

        sampleAssetInput.setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (!isLoadUrlSelected()) {
                        return false;
                    }
                    if (actionId == EditorInfo.IME_ACTION_GO
                            || actionId == EditorInfo.IME_ACTION_DONE
                            || actionId == EditorInfo.IME_ACTION_SEND
                            || actionId == EditorInfo.IME_NULL) {
                        loadHtmlFromUrlInput();
                        return true;
                    }
                    return false;
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
                        updateTranslateButtonState();
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

        sampleAssetInputKeyListener = sampleAssetInput.getKeyListener();
        loadSelectedSample(0);
        updateSourceInputState();
        translationResultText.setText("");
        translationResultText.setVisibility(View.INVISIBLE);
        translationProgressContainer.setVisibility(View.INVISIBLE);
        applyRenderMode(renderModeToggle.isChecked());
        updateShareButtonState();
        refreshDownloadedModels();
        updateExplainButtonState();
    }

    private void updateShareButtonState() {
        if (shareTranslatedButton == null
                || saveTranslatedButton == null
                || outputHtmlText == null) {
            return;
        }
        boolean hasTranslatedHtml = !outputHtmlText.getText().toString().trim().isEmpty();
        shareTranslatedButton.setEnabled(hasTranslatedHtml);
        saveTranslatedButton.setEnabled(hasTranslatedHtml);
        int tintColor =
                hasTranslatedHtml
                        ? getColor(R.color.mlkit_on_surface_variant)
                        : getColor(R.color.mlkit_outline);
        saveTranslatedButton.setImageTintList(ColorStateList.valueOf(tintColor));
        shareTranslatedButton.setImageTintList(ColorStateList.valueOf(tintColor));
    }

    private void shareTranslatedHtml() {
        String translatedHtml = outputHtmlText.getText().toString();
        if (translatedHtml.trim().isEmpty()) {
            Toast.makeText(this, R.string.share_translated_html_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, translatedHtml);
        startActivity(
                Intent.createChooser(
                        shareIntent, getString(R.string.share_translated_html_chooser_title)));
    }

    private void openSideBySideCompare() {
        try {
            startActivity(
                    SideBySideCompareActivity.createIntent(
                            this,
                            inputHtmlText.getText().toString(),
                            outputHtmlText.getText().toString()));
        } catch (RuntimeException exception) {
            Toast.makeText(this, R.string.compare_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveTranslatedHtml() {
        String translatedHtml = outputHtmlText.getText().toString();
        if (translatedHtml.trim().isEmpty()) {
            Toast.makeText(this, R.string.save_translated_html_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, R.string.save_translated_html_failure, Toast.LENGTH_SHORT).show();
            return;
        }

        String baseName = buildOutputBaseName();
        String nextFilename = nextAvailableDownloadFilename(baseName);
        if (nextFilename == null) {
            Toast.makeText(this, R.string.save_translated_html_failure, Toast.LENGTH_SHORT).show();
            return;
        }

        Uri downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, nextFilename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/html");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/");

        Uri itemUri = null;
        try {
            itemUri = getContentResolver().insert(downloadsUri, values);
            if (itemUri == null) {
                throw new IOException("Insert failed");
            }
            try (OutputStream outputStream = getContentResolver().openOutputStream(itemUri, "w")) {
                if (outputStream == null) {
                    throw new IOException("Open output stream failed");
                }
                outputStream.write(translatedHtml.getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(
                            this,
                            getString(R.string.save_translated_html_success, nextFilename),
                            Toast.LENGTH_SHORT)
                    .show();
        } catch (Exception e) {
            if (itemUri != null) {
                getContentResolver().delete(itemUri, null, null);
            }
            Toast.makeText(this, R.string.save_translated_html_failure, Toast.LENGTH_SHORT).show();
        }
    }

    private String buildOutputBaseName() {
        String derived = deriveSourceName();
        String normalizedTarget = normalizeLanguageCode(targetLanguage());
        String targetCode =
                normalizedTarget == null || normalizedTarget.isBlank() ? "xx" : normalizedTarget;
        return sanitizeFilename(derived) + "-" + sanitizeFilename(targetCode);
    }

    private String deriveSourceName() {
        if (isLoadUrlSelected()) {
            String urlValue = sampleAssetInput.getText().toString().trim();
            try {
                Uri uri = Uri.parse(urlValue);
                String segment = uri.getLastPathSegment();
                if (segment != null && !segment.isBlank()) {
                    return stripExtension(segment);
                }
            } catch (Exception ignored) {
            }
            return "translated";
        }

        SourceSelectorEntry entry = sourceEntryAt(selectedSourcePosition);
        if (entry.type == SourceEntryType.SAMPLE && entry.label != null && !entry.label.isBlank()) {
            return stripExtension(entry.label.trim());
        }
        return "translated";
    }

    private static String stripExtension(String value) {
        int extensionIndex = value.lastIndexOf('.');
        if (extensionIndex > 0) {
            return value.substring(0, extensionIndex);
        }
        return value;
    }

    private static String sanitizeFilename(String value) {
        String sanitized = value == null ? "" : value.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        sanitized = sanitized.replaceAll("\\s+", "_");
        if (sanitized.isBlank()) {
            return "translated";
        }
        return sanitized;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private String nextAvailableDownloadFilename(String baseName) {
        String safeBase = sanitizeFilename(baseName);
        for (int suffix = 0; suffix < 10000; suffix++) {
            String candidate = suffix == 0 ? safeBase + ".html" : safeBase + "." + suffix + ".html";
            if (!downloadNameExists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private boolean downloadNameExists(String displayName) {
        String[] projection = {MediaStore.MediaColumns._ID};
        String selection =
                MediaStore.MediaColumns.DISPLAY_NAME
                        + "=? AND "
                        + MediaStore.MediaColumns.RELATIVE_PATH
                        + "=?";
        String[] args = {displayName, "Download/"};
        try (Cursor cursor =
                getContentResolver()
                        .query(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                projection,
                                selection,
                                args,
                                null)) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    private String targetLanguage() {
        Object selected = targetSpinner.getSelectedItem();
        return selected == null ? "" : String.valueOf(selected);
    }

    private void applyDrawerHeaderInsets() {
        if (leftNavigationView.getHeaderCount() == 0) {
            return;
        }
        View header = leftNavigationView.getHeaderView(0);
        int initialTopPadding = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(
                header,
                (view, insets) -> {
                    int statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    view.setPadding(
                            view.getPaddingLeft(),
                            initialTopPadding + statusBarInset,
                            view.getPaddingRight(),
                            view.getPaddingBottom());
                    return insets;
                });
        ViewCompat.requestApplyInsets(header);
    }

    private void startTranslation(String sourceLanguage) {
        String htmlBody = inputHtmlText.getText().toString();
        String targetLanguage = targetSpinner.getSelectedItem().toString();
        translationRepository.setTranslator(buildTranslator());

        currentRequestCharCount = htmlBody.length();
        latestTimingReport = null;
        isTranslating = true;
        showStatusProgress(R.string.status_translating);
        translateButton.setEnabled(false);
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
        hideStatusProgress();
        translationResultText.setVisibility(View.VISIBLE);
        translationResultText.setTextColor(
                getColor(failed ? R.color.mlkit_error : R.color.mlkit_on_surface_variant));
        updateTranslateButtonState();
        updateExplainButtonState();
    }

    private void updateTranslateButtonState() {
        if (translateButton == null
                || targetSpinner == null
                || sourceSpinner == null
                || viewModel == null) {
            return;
        }

        if (isTranslating) {
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

    private boolean onDrawerItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_advanced_parameters) {
            mainDrawerLayout.closeDrawer(GravityCompat.START);
            openAdvancedParametersScreen();
            return true;
        }
        if (itemId == R.id.menu_manage_models) {
            mainDrawerLayout.closeDrawer(GravityCompat.START);
            startActivity(ModelManagementActivity.createIntent(this));
            return true;
        }
        if (itemId == R.id.menu_help_feedback) {
            mainDrawerLayout.closeDrawer(GravityCompat.START);
            startActivity(HelpActivity.createIntent(this));
            return true;
        }
        if (itemId == R.id.menu_version) {
            mainDrawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }

    private void updateVersionMenuItemTitle() {
        MenuItem versionItem = leftNavigationView.getMenu().findItem(R.id.menu_version);
        if (versionItem == null) {
            return;
        }
        versionItem.setTitle(getString(R.string.version_format, resolveVersionName()));
    }

    @NonNull
    private String resolveVersionName() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (packageInfo.versionName != null && !packageInfo.versionName.trim().isEmpty()) {
                return packageInfo.versionName;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // no-op
        }
        return "unknown";
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

    private void setupSourceSelector() {
        sourceEntries.clear();
        String[] sampleOptions = getResources().getStringArray(R.array.sample_assets);
        for (int i = 0; i < sampleOptions.length; i++) {
            sourceEntries.add(
                    new SourceSelectorEntry(
                            sampleOptions[i],
                            R.drawable.ic_source_sample,
                            SourceEntryType.SAMPLE,
                            i));
        }
        sourceEntries.add(
                new SourceSelectorEntry(
                        getString(R.string.source_selector_load_file),
                        R.drawable.ic_import,
                        SourceEntryType.LOAD_FILE,
                        -1));
        sourceEntries.add(
                new SourceSelectorEntry(
                        getString(R.string.source_selector_load_url),
                        R.drawable.ic_source_url,
                        SourceEntryType.LOAD_URL,
                        -1));

        SourceSelectorAdapter adapter = new SourceSelectorAdapter();
        sampleAssetInput.setAdapter(adapter);
        sampleAssetInput.setOnItemClickListener(
                (parent, view, position, id) -> {
                    selectedSourcePosition = position;
                    SourceSelectorEntry entry = sourceEntryAt(position);
                    if (entry.type == SourceEntryType.SAMPLE && entry.sampleIndex >= 0) {
                        selectedFileName = null;
                        loadSelectedSample(entry.sampleIndex);
                    } else if (entry.type == SourceEntryType.LOAD_FILE) {
                        launchHtmlFilePicker();
                    } else if (entry.type == SourceEntryType.LOAD_URL) {
                        selectedFileName = null;
                        sampleAssetInput.setText("", false);
                    }
                    updateSourceInputState();
                });
    }

    @NonNull
    private SourceSelectorEntry sourceEntryAt(int position) {
        if (position < 0 || position >= sourceEntries.size()) {
            return sourceEntries.get(0);
        }
        return sourceEntries.get(position);
    }

    private boolean isLoadUrlSelected() {
        return sourceEntryAt(selectedSourcePosition).type == SourceEntryType.LOAD_URL;
    }

    private boolean isLoadFileSelected() {
        return sourceEntryAt(selectedSourcePosition).type == SourceEntryType.LOAD_FILE;
    }

    private void loadSelectedSample(int sampleIndex) {
        if (sampleIndex < 0 || sampleIndex >= sourceEntries.size()) {
            return;
        }
        SourceSelectorEntry entry = sourceEntries.get(sampleIndex);
        selectedSourcePosition = sampleIndex;
        sampleAssetInput.setText(entry.label, false);
        inputHtmlText.setText(loadAssetHtml(entry.label));
        refreshRenderedPreviewIfNeeded();
        updateExplainButtonState();
    }

    private void updateSourceInputState() {
        boolean enabled = !isSourceLoading;
        boolean loadUrlSelected = isLoadUrlSelected();
        boolean loadFileSelected = isLoadFileSelected();
        sampleAssetInput.setEnabled(enabled);
        sampleAssetInput.setFocusable(loadUrlSelected && enabled);
        sampleAssetInput.setFocusableInTouchMode(loadUrlSelected && enabled);
        sampleAssetInput.setCursorVisible(loadUrlSelected && enabled);
        sampleAssetInput.setKeyListener(loadUrlSelected ? sampleAssetInputKeyListener : null);
        sampleAssetInput.setThreshold(loadUrlSelected ? Integer.MAX_VALUE : 0);
        sampleAssetInput.setInputType(
                loadUrlSelected
                        ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                        : InputType.TYPE_NULL);

        if (loadUrlSelected) {
            sampleAssetInput.setHint(R.string.source_url_hint);
            sampleAssetInput.setImeOptions(EditorInfo.IME_ACTION_GO);
            sampleAssetInput.setImeActionLabel(
                    getString(R.string.source_action_load), EditorInfo.IME_ACTION_GO);
        } else {
            sampleAssetInput.dismissDropDown();
            sampleAssetInput.setHint(null);
            sampleAssetInput.setImeActionLabel(null, EditorInfo.IME_ACTION_NONE);
            sampleAssetInput.setImeOptions(EditorInfo.IME_ACTION_NONE);
            String label = sourceSelectorDisplayLabel(loadFileSelected);
            if (!label.contentEquals(sampleAssetInput.getText())) {
                sampleAssetInput.setText(label, false);
            }
        }

        updateSourceSelectorDrawables(loadUrlSelected);
        exampleSourceContainer.setVisibility(View.VISIBLE);
    }

    private String sourceSelectorDisplayLabel(boolean loadFileSelected) {
        if (loadFileSelected && selectedFileName != null && !selectedFileName.trim().isEmpty()) {
            return selectedFileName;
        }
        return sourceEntryAt(selectedSourcePosition).label;
    }

    private void updateSourceSelectorDrawables(boolean loadUrlSelected) {
        SourceSelectorEntry entry = sourceEntryAt(selectedSourcePosition);
        int leftIcon = loadUrlSelected ? R.drawable.ic_source_url : entry.iconRes;
        sampleAssetInput.setCompoundDrawablesRelativeWithIntrinsicBounds(
                leftIcon, 0, R.drawable.ic_source_dropdown, 0);
        sampleAssetInput.setCompoundDrawableTintList(
                ColorStateList.valueOf(getColor(R.color.mlkit_on_surface_variant)));
    }

    private static boolean isTapOnStartDrawable(TextView textView, MotionEvent event) {
        android.graphics.drawable.Drawable[] drawables = textView.getCompoundDrawablesRelative();
        android.graphics.drawable.Drawable startDrawable = drawables[0];
        if (startDrawable == null) {
            return false;
        }
        int drawableWidth = startDrawable.getBounds().width();
        int drawableEnd = textView.getPaddingStart() + drawableWidth;
        return event.getX() <= drawableEnd;
    }

    private static boolean isTapOnEndDrawable(TextView textView, MotionEvent event) {
        android.graphics.drawable.Drawable[] drawables = textView.getCompoundDrawablesRelative();
        android.graphics.drawable.Drawable endDrawable = drawables[2];
        if (endDrawable == null) {
            return false;
        }
        int drawableWidth = endDrawable.getBounds().width();
        int drawableStart = textView.getWidth() - textView.getPaddingEnd() - drawableWidth;
        return event.getX() >= drawableStart;
    }

    private void setSourceLoading(boolean sourceLoading) {
        isSourceLoading = sourceLoading;
        updateSourceInputState();
    }

    private void registerHtmlFilePickerLauncher() {
        htmlFilePickerLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.OpenDocument(),
                        uri -> {
                            if (uri == null) {
                                return;
                            }
                            loadHtmlFromPickedFile(uri);
                        });
    }

    private void launchHtmlFilePicker() {
        if (isSourceLoading) {
            return;
        }
        htmlFilePickerLauncher.launch(new String[] {"text/html", "application/xhtml+xml"});
    }

    private void loadHtmlFromPickedFile(@NonNull Uri fileUri) {
        String displayName = resolveDisplayName(fileUri);
        if (!isHtmlFile(displayName)) {
            Toast.makeText(this, R.string.source_mode_file_load_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            getContentResolver()
                    .takePersistableUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // no-op
        }

        setSourceLoading(true);
        new Thread(
                        () -> {
                            String content;
                            try (InputStream inputStream =
                                    getContentResolver().openInputStream(fileUri)) {
                                if (inputStream == null) {
                                    throw new IOException("Input stream unavailable");
                                }
                                content = readText(inputStream);
                            } catch (IOException e) {
                                runOnUiThread(
                                        () -> {
                                            setSourceLoading(false);
                                            Toast.makeText(
                                                            MainActivity.this,
                                                            R.string.source_mode_file_load_failed,
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                        });
                                return;
                            }

                            final String loadedContent = content;
                            runOnUiThread(
                                    () -> {
                                        setSourceLoading(false);
                                        selectedFileName =
                                                displayName == null || displayName.trim().isEmpty()
                                                        ? getString(R.string.source_mode_file)
                                                        : displayName;
                                        updateSourceInputState();
                                        inputHtmlText.setText(loadedContent);
                                        refreshRenderedPreviewIfNeeded();
                                        updateExplainButtonState();
                                    });
                        })
                .start();
    }

    private String resolveDisplayName(@NonNull Uri fileUri) {
        try (Cursor cursor =
                getContentResolver()
                        .query(
                                fileUri,
                                new String[] {OpenableColumns.DISPLAY_NAME},
                                null,
                                null,
                                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // no-op
        }
        return null;
    }

    private static boolean isHtmlFile(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return false;
        }
        String lower = displayName.toLowerCase(Locale.US);
        return lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xhtml");
    }

    private static String readText(@NonNull InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private void loadHtmlFromUrlInput() {
        String urlText = sampleAssetInput.getText().toString().trim();
        if (!isValidHttpUrl(urlText)) {
            Toast.makeText(this, R.string.source_mode_invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        setSourceLoading(true);
        showStatusProgress(R.string.status_loading_url);
        new Thread(
                        () -> {
                            String content;
                            try {
                                content = readUrlContent(urlText);
                            } catch (IOException e) {
                                runOnUiThread(
                                        () -> {
                                            setSourceLoading(false);
                                            restoreProgressStateAfterSourceLoad();
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
                                        setSourceLoading(false);
                                        restoreProgressStateAfterSourceLoad();
                                        inputHtmlText.setText(loadedContent);
                                        refreshRenderedPreviewIfNeeded();
                                        updateExplainButtonState();
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

    private void showStatusProgress(@StringRes int messageRes) {
        translationResultText.setText("");
        translationResultText.setVisibility(View.INVISIBLE);
        if (translationProgressText != null) {
            translationProgressText.setText(messageRes);
        }
        translationProgressContainer.setVisibility(View.VISIBLE);
    }

    private void hideStatusProgress() {
        translationProgressContainer.setVisibility(View.INVISIBLE);
    }

    private void restoreProgressStateAfterSourceLoad() {
        if (isTranslating) {
            showStatusProgress(R.string.status_translating);
            return;
        }
        hideStatusProgress();
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

    private void refreshDownloadedModels() {
        viewModel.refreshDownloadedModels(
                new ModelLifecycleManager.RefreshCallback() {
                    @Override
                    public void onComplete() {
                        updateDownloadedLanguageOptions();
                        updateTranslateButtonState();
                    }

                    @Override
                    public void onError(@NonNull String reason) {
                        updateDownloadedLanguageOptions();
                        updateTranslateButtonState();
                    }
                });
    }

    private void setupLanguageSpinners() {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this, android.R.layout.simple_spinner_item, downloadedLanguageOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(adapter);
        targetSpinner.setAdapter(adapter);
    }

    private void updateDownloadedLanguageOptions() {
        String previousSource =
                sourceSpinner.getSelectedItem() == null
                        ? ""
                        : sourceSpinner.getSelectedItem().toString();
        String previousTarget =
                targetSpinner.getSelectedItem() == null
                        ? ""
                        : targetSpinner.getSelectedItem().toString();

        downloadedLanguageOptions.clear();
        Set<String> downloadedModels = viewModel.downloadedModels();
        String[] supportedLanguages = getResources().getStringArray(R.array.language_codes);
        for (String language : supportedLanguages) {
            String normalized = normalizeLanguageCode(language);
            if (normalized != null && downloadedModels.contains(normalized)) {
                downloadedLanguageOptions.add(language);
            }
        }
        Collections.sort(downloadedLanguageOptions);

        @SuppressWarnings("unchecked")
        ArrayAdapter<String> sourceAdapter = (ArrayAdapter<String>) sourceSpinner.getAdapter();
        @SuppressWarnings("unchecked")
        ArrayAdapter<String> targetAdapter = (ArrayAdapter<String>) targetSpinner.getAdapter();
        if (sourceAdapter != null) {
            sourceAdapter.notifyDataSetChanged();
        }
        if (targetAdapter != null) {
            targetAdapter.notifyDataSetChanged();
        }

        restoreSpinnerSelection(sourceSpinner, previousSource);
        restoreSpinnerSelection(targetSpinner, previousTarget);
    }

    private static void restoreSpinnerSelection(Spinner spinner, String preferredLanguage) {
        ArrayAdapter<?> adapter = (ArrayAdapter<?>) spinner.getAdapter();
        if (adapter == null || adapter.getCount() == 0) {
            return;
        }

        int preferredIndex = -1;
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (preferredLanguage.equals(item)) {
                preferredIndex = i;
                break;
            }
        }
        spinner.setSelection(preferredIndex >= 0 ? preferredIndex : 0);
    }

    private static String normalizeLanguageCode(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        String normalizedInput = languageCode.trim();
        String translatedLanguage = TranslateLanguage.fromLanguageTag(normalizedInput);
        if (translatedLanguage != null) {
            return translatedLanguage;
        }

        int separatorIndex = normalizedInput.indexOf('-');
        if (separatorIndex < 0) {
            separatorIndex = normalizedInput.indexOf('_');
        }
        if (separatorIndex > 0) {
            return TranslateLanguage.fromLanguageTag(normalizedInput.substring(0, separatorIndex));
        }

        return null;
    }

    private final class SourceSelectorAdapter extends ArrayAdapter<SourceSelectorEntry>
            implements ListAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
        private final Filter allEntriesFilter =
                new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = sourceEntries;
                        results.count = sourceEntries.size();
                        return results;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };

        private SourceSelectorAdapter() {
            super(MainActivity.this, R.layout.item_source_selector_selected, new ArrayList<>());
        }

        @Override
        public int getCount() {
            return sourceEntries.size();
        }

        @Override
        public SourceSelectorEntry getItem(int position) {
            return sourceEntryAt(position);
        }

        @Override
        public @NonNull Filter getFilter() {
            return allEntriesFilter;
        }

        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view =
                    convertView != null
                            ? convertView
                            : inflater.inflate(
                                    R.layout.item_source_selector_dropdown, parent, false);
            SourceSelectorEntry entry = getItem(position);
            if (entry == null) {
                entry = sourceEntryAt(position);
            }
            bindSourceSelectorView(view, entry);
            View divider = view.findViewById(R.id.sourceSelectorDivider);
            if (divider != null) {
                divider.setVisibility(
                        entry.type == SourceEntryType.LOAD_FILE ? View.VISIBLE : View.GONE);
            }
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
            View view =
                    convertView != null
                            ? convertView
                            : inflater.inflate(
                                    R.layout.item_source_selector_dropdown, parent, false);
            SourceSelectorEntry entry = getItem(position);
            if (entry == null) {
                entry = sourceEntryAt(position);
            }
            bindSourceSelectorView(view, entry);
            View divider = view.findViewById(R.id.sourceSelectorDivider);
            if (divider != null) {
                divider.setVisibility(
                        entry.type == SourceEntryType.LOAD_FILE ? View.VISIBLE : View.GONE);
            }
            return view;
        }

        private void bindSourceSelectorView(
                @NonNull View view, @NonNull SourceSelectorEntry entry) {
            ImageView iconView = view.findViewById(R.id.sourceSelectorIcon);
            TextView textView = view.findViewById(R.id.sourceSelectorText);
            iconView.setImageResource(entry.iconRes);
            textView.setText(entry.label);
        }
    }

    private static final class SourceSelectorEntry {
        private final String label;
        private final int iconRes;
        private final SourceEntryType type;
        private final int sampleIndex;

        private SourceSelectorEntry(
                String label, int iconRes, SourceEntryType type, int sampleIndex) {
            this.label = label;
            this.iconRes = iconRes;
            this.type = type;
            this.sampleIndex = sampleIndex;
        }
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
        if (inputRenderedHtml != null) {
            inputRenderedHtml.destroy();
        }
        if (outputRenderedHtml != null) {
            outputRenderedHtml.destroy();
        }
        super.onDestroy();
    }
}
