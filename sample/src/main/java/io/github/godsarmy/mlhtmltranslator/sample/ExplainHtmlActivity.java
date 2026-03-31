package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import io.github.godsarmy.mlhtmltranslator.api.ExplainHtmlChunk;
import io.github.godsarmy.mlhtmltranslator.api.ExplainHtmlNode;
import io.github.godsarmy.mlhtmltranslator.api.ExplainHtmlResult;
import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.api.MlKitHtmlTranslator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ExplainHtmlActivity extends AppCompatActivity {
    private static final String EXTRA_HTML_BODY = "extra_html_body";
    private static final String EXTRA_MARKER_START = "extra_marker_start";
    private static final String EXTRA_MARKER_END = "extra_marker_end";
    private static final String EXTRA_MAX_CHUNK_CHARS = "extra_max_chunk_chars";
    private static final String EXTRA_MASK_URLS = "extra_mask_urls";
    private static final String EXTRA_MASK_PLACEHOLDERS = "extra_mask_placeholders";
    private static final String EXTRA_MASK_PATHS = "extra_mask_paths";
    private static final String EXTRA_FAILURE_POLICY = "extra_failure_policy";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private MlKitHtmlTranslator translator;
    private View loadingContainer;
    private TextView errorText;
    private TextView countsValue;
    private TextView optionsValue;
    private TextView normalizedHtmlValue;
    private TextView chunksValue;
    private TextView nodesValue;

    public static Intent createIntent(
            Context context,
            String htmlBody,
            String markerStart,
            String markerEnd,
            int maxChunkChars,
            boolean maskUrls,
            boolean maskPlaceholders,
            boolean maskPaths,
            String failurePolicy) {
        Intent intent = new Intent(context, ExplainHtmlActivity.class);
        intent.putExtra(EXTRA_HTML_BODY, htmlBody);
        intent.putExtra(EXTRA_MARKER_START, markerStart);
        intent.putExtra(EXTRA_MARKER_END, markerEnd);
        intent.putExtra(EXTRA_MAX_CHUNK_CHARS, maxChunkChars);
        intent.putExtra(EXTRA_MASK_URLS, maskUrls);
        intent.putExtra(EXTRA_MASK_PLACEHOLDERS, maskPlaceholders);
        intent.putExtra(EXTRA_MASK_PATHS, maskPaths);
        intent.putExtra(EXTRA_FAILURE_POLICY, failurePolicy);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explain_html);

        bindViews();
        String markerStart = getIntent().getStringExtra(EXTRA_MARKER_START);
        String markerEnd = getIntent().getStringExtra(EXTRA_MARKER_END);
        int maxChunkChars = Math.max(1, getIntent().getIntExtra(EXTRA_MAX_CHUNK_CHARS, 3000));
        boolean maskUrls = getIntent().getBooleanExtra(EXTRA_MASK_URLS, true);
        boolean maskPlaceholders = getIntent().getBooleanExtra(EXTRA_MASK_PLACEHOLDERS, true);
        boolean maskPaths = getIntent().getBooleanExtra(EXTRA_MASK_PATHS, true);
        String failurePolicy = getIntent().getStringExtra(EXTRA_FAILURE_POLICY);
        HtmlTranslationOptions.Builder optionsBuilder = HtmlTranslationOptions.builder();
        optionsBuilder.setMaxChunkChars(maxChunkChars);
        optionsBuilder.setMaskUrls(maskUrls);
        optionsBuilder.setMaskPlaceholders(maskPlaceholders);
        optionsBuilder.setMaskPaths(maskPaths);
        if (HtmlTranslationOptions.FailurePolicy.FAIL_FAST.name().equals(failurePolicy)) {
            optionsBuilder.setFailurePolicy(HtmlTranslationOptions.FailurePolicy.FAIL_FAST);
        } else {
            optionsBuilder.setFailurePolicy(HtmlTranslationOptions.FailurePolicy.BEST_EFFORT);
        }
        if (markerStart != null && !markerStart.trim().isEmpty()) {
            optionsBuilder.setPlaceholderMarkerStart(markerStart);
        }
        if (markerEnd != null && !markerEnd.trim().isEmpty()) {
            optionsBuilder.setPlaceholderMarkerEnd(markerEnd);
        }
        translator = new MlKitHtmlTranslator(optionsBuilder.build());

        String htmlBody = getIntent().getStringExtra(EXTRA_HTML_BODY);
        if (htmlBody == null) {
            showError(getString(R.string.explain_error_missing_html));
            return;
        }

        loadExplainResult(htmlBody);
    }

    private void bindViews() {
        loadingContainer = findViewById(R.id.explainLoadingContainer);
        errorText = findViewById(R.id.explainErrorText);
        countsValue = findViewById(R.id.explainCountsValue);
        optionsValue = findViewById(R.id.explainOptionsValue);
        normalizedHtmlValue = findViewById(R.id.explainNormalizedHtmlValue);
        chunksValue = findViewById(R.id.explainChunksValue);
        nodesValue = findViewById(R.id.explainNodesValue);
    }

    private void loadExplainResult(String htmlBody) {
        showLoading(true);
        executorService.execute(
                () -> {
                    try {
                        ExplainHtmlResult result = translator.explainHtml(htmlBody);
                        runOnUiThread(() -> bindExplainResult(result));
                    } catch (RuntimeException error) {
                        runOnUiThread(() -> showError(error.getMessage()));
                    }
                });
    }

    private void bindExplainResult(ExplainHtmlResult result) {
        showLoading(false);
        errorText.setVisibility(View.GONE);

        countsValue.setText(
                getString(
                        R.string.explain_html_counts_value,
                        result.getTotalNodeCount(),
                        result.getTotalChunkCount()));
        optionsValue.setText(
                getString(
                        R.string.explain_html_options_value,
                        result.isMaskUrls(),
                        result.isMaskPlaceholders(),
                        result.isMaskPaths(),
                        result.getProtectedTags()));
        normalizedHtmlValue.setText(result.getNormalizedHtmlBody());
        chunksValue.setText(formatChunks(result.getChunks()));
        nodesValue.setText(formatNodes(result.getNodes()));
    }

    private void showError(String message) {
        showLoading(false);
        String safeMessage = message == null || message.isBlank() ? "Unknown error" : message;
        errorText.setText(getString(R.string.explain_error_template, safeMessage));
        errorText.setVisibility(View.VISIBLE);
    }

    private void showLoading(boolean loading) {
        loadingContainer.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String formatChunks(List<ExplainHtmlChunk> chunks) {
        if (chunks.isEmpty()) {
            return getString(R.string.explain_none);
        }

        StringBuilder builder = new StringBuilder();
        for (ExplainHtmlChunk chunk : chunks) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("#")
                    .append(chunk.getIndex())
                    .append(" • len=")
                    .append(chunk.getPlainTextLength())
                    .append(" • nodeIndexes=")
                    .append(chunk.getNodeIndexes())
                    .append("\n")
                    .append(chunk.getPayload());
        }
        return builder.toString();
    }

    private String formatNodes(List<ExplainHtmlNode> nodes) {
        if (nodes.isEmpty()) {
            return getString(R.string.explain_none);
        }

        StringBuilder builder = new StringBuilder();
        for (ExplainHtmlNode node : nodes) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("#")
                    .append(node.getIndex())
                    .append(" • leading=")
                    .append(printableWhitespace(node.getLeadingWhitespace()))
                    .append(" • trailing=")
                    .append(printableWhitespace(node.getTrailingWhitespace()))
                    .append("\n")
                    .append("text: ")
                    .append(node.getTranslatableText())
                    .append("\n")
                    .append("masked: ")
                    .append(node.getMaskedText())
                    .append("\n")
                    .append("placeholders: ")
                    .append(formatPlaceholders(node.getPlaceholders()));
        }
        return builder.toString();
    }

    private static String formatPlaceholders(Map<String, String> placeholders) {
        if (placeholders.isEmpty()) {
            return "{}";
        }
        return placeholders.toString();
    }

    private static String printableWhitespace(String value) {
        return value.replace("\n", "\\n").replace("\t", "\\t");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
        if (translator != null) {
            translator.close();
        }
    }
}
