package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import io.github.godsarmy.mlhtmltranslator.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.MlKitHtmlTranslator;
import io.github.godsarmy.mlhtmltranslator.api.ExplainHtmlChunk;
import io.github.godsarmy.mlhtmltranslator.api.ExplainHtmlNode;
import io.github.godsarmy.mlhtmltranslator.api.ExplainHtmlResult;
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
    private String originalHtmlBody;
    private View loadingContainer;
    private TextView errorText;
    private TextView optionsValue;
    private TabLayout explainTabs;
    private ViewPager2 explainPager;
    private ExplainPagerAdapter pagerAdapter;
    private TabLayoutMediator tabLayoutMediator;

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

        originalHtmlBody = getIntent().getStringExtra(EXTRA_HTML_BODY);
        if (originalHtmlBody == null) {
            showError(getString(R.string.explain_error_missing_html));
            return;
        }

        loadExplainResult(originalHtmlBody);
    }

    private void bindViews() {
        loadingContainer = findViewById(R.id.explainLoadingContainer);
        errorText = findViewById(R.id.explainErrorText);
        optionsValue = findViewById(R.id.explainOptionsValue);
        explainTabs = findViewById(R.id.explainTabs);
        explainPager = findViewById(R.id.explainPager);
        pagerAdapter = new ExplainPagerAdapter(this);
        explainPager.setAdapter(pagerAdapter);
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

        optionsValue.setText(
                getString(
                        R.string.explain_html_options_value,
                        result.isMaskUrls(),
                        result.isMaskPlaceholders(),
                        result.isMaskPaths(),
                        result.getProtectedTags()));
        bindPages(result);
    }

    private void bindPages(ExplainHtmlResult result) {
        List<ExplainPageItem> pages =
                List.of(
                        new ExplainPageItem(
                                getString(R.string.explain_html_body_label),
                                new ExplainPageItem.ToggleRows(
                                        getString(R.string.explain_html_body_original),
                                        List.of(
                                                new ExplainPageItem.ExplainPageRow(
                                                        getString(
                                                                R.string
                                                                        .explain_html_body_original),
                                                        originalHtmlBody)),
                                        getString(R.string.explain_html_body_normalized),
                                        List.of(
                                                new ExplainPageItem.ExplainPageRow(
                                                        getString(
                                                                R.string
                                                                        .explain_html_body_normalized),
                                                        result.getNormalizedHtmlBody())))),
                        new ExplainPageItem(
                                withCount(
                                        getString(R.string.explain_chunks_label),
                                        result.getChunks().size()),
                                formatChunkRows(result.getChunks())),
                        new ExplainPageItem(
                                withCount(
                                        getString(R.string.explain_nodes_label),
                                        result.getNodes().size()),
                                formatNodeRows(result.getNodes())));
        pagerAdapter.setItems(pages);
        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
        }
        tabLayoutMediator =
                new TabLayoutMediator(
                        explainTabs,
                        explainPager,
                        (tab, position) -> tab.setText(pagerAdapter.getItem(position).getTitle()));
        tabLayoutMediator.attach();
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

    private List<ExplainPageItem.ExplainPageRow> formatChunkRows(List<ExplainHtmlChunk> chunks) {
        List<ExplainPageItem.ExplainPageRow> rows = new java.util.ArrayList<>();
        for (ExplainHtmlChunk chunk : chunks) {
            String value =
                    new StringBuilder()
                            .append("#")
                            .append(chunk.getIndex())
                            .append(" • len=")
                            .append(chunk.getPlainTextLength())
                            .append(" • nodeIndexes=")
                            .append(chunk.getNodeIndexes())
                            .append("\n")
                            .append(chunk.getPayload())
                            .toString();
            rows.add(new ExplainPageItem.ExplainPageRow("Chunk " + chunk.getIndex(), value));
        }
        return rows;
    }

    private List<ExplainPageItem.ExplainPageRow> formatNodeRows(List<ExplainHtmlNode> nodes) {
        List<ExplainPageItem.ExplainPageRow> rows = new java.util.ArrayList<>();
        for (ExplainHtmlNode node : nodes) {
            String value =
                    new StringBuilder()
                            .append("#")
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
                            .append(formatPlaceholders(node.getPlaceholders()))
                            .toString();
            rows.add(new ExplainPageItem.ExplainPageRow("Node " + node.getIndex(), value));
        }
        return rows;
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

    private static String withCount(String title, int count) {
        return title + " (" + count + ")";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
        }
        if (translator != null) {
            translator.close();
        }
    }
}
