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
import io.github.godsarmy.mlhtmltranslator.api.MlKitHtmlTranslator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ExplainHtmlActivity extends AppCompatActivity {
    private static final String EXTRA_HTML_BODY = "extra_html_body";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private MlKitHtmlTranslator translator;
    private View loadingContainer;
    private TextView errorText;
    private TextView countsValue;
    private TextView optionsValue;
    private TextView normalizedHtmlValue;
    private TextView chunksValue;
    private TextView nodesValue;

    public static Intent createIntent(Context context, String htmlBody) {
        Intent intent = new Intent(context, ExplainHtmlActivity.class);
        intent.putExtra(EXTRA_HTML_BODY, htmlBody);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explain_html);

        bindViews();
        translator = new MlKitHtmlTranslator();

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
