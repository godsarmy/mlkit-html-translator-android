package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;

public final class AdvancedParametersActivity extends AppCompatActivity {
    private static final String EXTRA_MARKER_START = "extra_marker_start";
    private static final String EXTRA_MARKER_END = "extra_marker_end";
    private static final String EXTRA_MAX_CHUNK_CHARS = "extra_max_chunk_chars";
    private static final String EXTRA_CHUNK_TIMEOUT_MS = "extra_chunk_timeout_ms";
    private static final String EXTRA_MASK_URLS = "extra_mask_urls";
    private static final String EXTRA_MASK_PLACEHOLDERS = "extra_mask_placeholders";
    private static final String EXTRA_MASK_PATHS = "extra_mask_paths";
    private static final String EXTRA_FAILURE_POLICY = "extra_failure_policy";

    private EditText markerStartInput;
    private EditText markerEndInput;
    private EditText maxChunkCharsInput;
    private EditText chunkTimeoutMsInput;
    private Spinner failurePolicySpinner;
    private SwitchMaterial maskUrlsCheck;
    private SwitchMaterial maskPlaceholdersCheck;
    private SwitchMaterial maskPathsCheck;

    public static Intent createIntent(
            @NonNull Context context,
            @NonNull String markerStart,
            @NonNull String markerEnd,
            int maxChunkChars,
            long chunkTimeoutMs,
            boolean maskUrls,
            boolean maskPlaceholders,
            boolean maskPaths,
            @NonNull String failurePolicy) {
        Intent intent = new Intent(context, AdvancedParametersActivity.class);
        intent.putExtra(EXTRA_MARKER_START, markerStart);
        intent.putExtra(EXTRA_MARKER_END, markerEnd);
        intent.putExtra(EXTRA_MAX_CHUNK_CHARS, maxChunkChars);
        intent.putExtra(EXTRA_CHUNK_TIMEOUT_MS, chunkTimeoutMs);
        intent.putExtra(EXTRA_MASK_URLS, maskUrls);
        intent.putExtra(EXTRA_MASK_PLACEHOLDERS, maskPlaceholders);
        intent.putExtra(EXTRA_MASK_PATHS, maskPaths);
        intent.putExtra(EXTRA_FAILURE_POLICY, failurePolicy);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advanced_parameters);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setupToolbar();

        markerStartInput = findViewById(R.id.markerStartInput);
        markerEndInput = findViewById(R.id.markerEndInput);
        maxChunkCharsInput = findViewById(R.id.maxChunkCharsInput);
        chunkTimeoutMsInput = findViewById(R.id.chunkTimeoutMsInput);
        failurePolicySpinner = findViewById(R.id.failurePolicySpinner);
        maskUrlsCheck = findViewById(R.id.maskUrlsCheck);
        maskPlaceholdersCheck = findViewById(R.id.maskPlaceholdersCheck);
        maskPathsCheck = findViewById(R.id.maskPathsCheck);

        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(
                        this, R.array.failure_policy_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        failurePolicySpinner.setAdapter(adapter);

        markerStartInput.setText(getIntent().getStringExtra(EXTRA_MARKER_START));
        markerEndInput.setText(getIntent().getStringExtra(EXTRA_MARKER_END));
        maxChunkCharsInput.setText(
                String.valueOf(getIntent().getIntExtra(EXTRA_MAX_CHUNK_CHARS, 3000)));
        chunkTimeoutMsInput.setText(
                String.valueOf(getIntent().getLongExtra(EXTRA_CHUNK_TIMEOUT_MS, 20_000L)));
        maskUrlsCheck.setChecked(getIntent().getBooleanExtra(EXTRA_MASK_URLS, true));
        maskPlaceholdersCheck.setChecked(
                getIntent().getBooleanExtra(EXTRA_MASK_PLACEHOLDERS, true));
        maskPathsCheck.setChecked(getIntent().getBooleanExtra(EXTRA_MASK_PATHS, true));

        String failurePolicy = getIntent().getStringExtra(EXTRA_FAILURE_POLICY);
        boolean isFailFast =
                HtmlTranslationOptions.FailurePolicy.FAIL_FAST.name().equals(failurePolicy);
        failurePolicySpinner.setSelection(isFailFast ? 1 : 0);

        findViewById(R.id.cancelAdvancedParametersButton)
                .setOnClickListener(
                        v -> {
                            setResult(RESULT_CANCELED);
                            finish();
                        });
        findViewById(R.id.saveAdvancedParametersButton).setOnClickListener(v -> saveAndFinish());
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.advancedParametersToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void saveAndFinish() {
        String markerStart = safeMarker(markerStartInput.getText().toString(), "[{[");
        String markerEnd = safeMarker(markerEndInput.getText().toString(), "]}]");
        int maxChunkChars = parseChunkChars(maxChunkCharsInput.getText().toString());
        long chunkTimeoutMs = parseChunkTimeoutMs(chunkTimeoutMsInput.getText().toString());
        String failurePolicy =
                failurePolicySpinner.getSelectedItemPosition() == 1
                        ? HtmlTranslationOptions.FailurePolicy.FAIL_FAST.name()
                        : HtmlTranslationOptions.FailurePolicy.BEST_EFFORT.name();

        Intent result = new Intent();
        result.putExtra(EXTRA_MARKER_START, markerStart);
        result.putExtra(EXTRA_MARKER_END, markerEnd);
        result.putExtra(EXTRA_MAX_CHUNK_CHARS, maxChunkChars);
        result.putExtra(EXTRA_CHUNK_TIMEOUT_MS, chunkTimeoutMs);
        result.putExtra(EXTRA_MASK_URLS, maskUrlsCheck.isChecked());
        result.putExtra(EXTRA_MASK_PLACEHOLDERS, maskPlaceholdersCheck.isChecked());
        result.putExtra(EXTRA_MASK_PATHS, maskPathsCheck.isChecked());
        result.putExtra(EXTRA_FAILURE_POLICY, failurePolicy);
        setResult(RESULT_OK, result);
        finish();
    }

    private static String safeMarker(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static int parseChunkChars(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 3000;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 3000;
        }
    }

    private static long parseChunkTimeoutMs(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 20_000L;
        }
        try {
            return Math.max(1L, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return 20_000L;
        }
    }

    public static String markerStartFromResult(@Nullable Intent data, @NonNull String fallback) {
        if (data == null) {
            return fallback;
        }
        String value = data.getStringExtra(EXTRA_MARKER_START);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public static String markerEndFromResult(@Nullable Intent data, @NonNull String fallback) {
        if (data == null) {
            return fallback;
        }
        String value = data.getStringExtra(EXTRA_MARKER_END);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public static int maxChunkCharsFromResult(@Nullable Intent data, int fallback) {
        return data == null
                ? fallback
                : Math.max(1, data.getIntExtra(EXTRA_MAX_CHUNK_CHARS, fallback));
    }

    public static long chunkTimeoutMsFromResult(@Nullable Intent data, long fallback) {
        return data == null
                ? fallback
                : Math.max(1L, data.getLongExtra(EXTRA_CHUNK_TIMEOUT_MS, fallback));
    }

    public static boolean maskUrlsFromResult(@Nullable Intent data, boolean fallback) {
        return data == null ? fallback : data.getBooleanExtra(EXTRA_MASK_URLS, fallback);
    }

    public static boolean maskPlaceholdersFromResult(@Nullable Intent data, boolean fallback) {
        return data == null ? fallback : data.getBooleanExtra(EXTRA_MASK_PLACEHOLDERS, fallback);
    }

    public static boolean maskPathsFromResult(@Nullable Intent data, boolean fallback) {
        return data == null ? fallback : data.getBooleanExtra(EXTRA_MASK_PATHS, fallback);
    }

    @NonNull
    public static String failurePolicyFromResult(@Nullable Intent data, @NonNull String fallback) {
        if (data == null) {
            return fallback;
        }
        String value = data.getStringExtra(EXTRA_FAILURE_POLICY);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
