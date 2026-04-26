package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SideBySideCompareActivity extends AppCompatActivity {
    private static final String EXTRA_SOURCE_HTML_PATH = "extra_source_html_path";
    private static final String EXTRA_TRANSLATED_HTML_PATH = "extra_translated_html_path";
    private static final long TOGGLE_AUTO_HIDE_DELAY_MS = 2400L;
    private static final long TOGGLE_FADE_DURATION_MS = 180L;

    private TextView sourceText;
    private TextView translatedText;
    private WebView sourceRenderedHtml;
    private WebView translatedRenderedHtml;
    private ImageButton normalizeToggleButton;
    private ImageButton lineNumbersToggleButton;
    private ImageButton renderToggleButton;
    private ImageButton closeButton;
    private LineNumberGutterView sourceLineNumbers;
    private LineNumberGutterView translatedLineNumbers;
    private View sourceLineDivider;
    private View translatedLineDivider;
    private boolean syncingScroll;
    private boolean renderModeEnabled;
    private boolean normalizeModeEnabled;
    private boolean lineNumbersEnabled;
    private boolean rawHtmlLoaded;
    private boolean normalizeInProgress;
    private boolean isRenderToggleVisible;
    private String rawSourceHtml = "";
    private String rawTranslatedHtml = "";
    private String normalizedSourceHtml;
    private String normalizedTranslatedHtml;
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();
    private final Runnable hideRenderToggleRunnable = this::hideRenderToggle;

    public static Intent createIntent(
            Context context, String sourceHtmlPath, String translatedHtmlPath) {
        Intent intent = new Intent(context, SideBySideCompareActivity.class);
        intent.putExtra(EXTRA_SOURCE_HTML_PATH, sourceHtmlPath);
        intent.putExtra(EXTRA_TRANSLATED_HTML_PATH, translatedHtmlPath);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_side_by_side_compare);
        setTitle(R.string.compare_screen_title);

        hideStatusBar();

        View compareRoot = findViewById(R.id.compareRoot);
        sourceText = findViewById(R.id.compareSourceText);
        translatedText = findViewById(R.id.compareTranslatedText);
        sourceRenderedHtml = findViewById(R.id.compareSourceRenderedHtml);
        translatedRenderedHtml = findViewById(R.id.compareTranslatedRenderedHtml);
        normalizeToggleButton = findViewById(R.id.compareNormalizeToggleButton);
        lineNumbersToggleButton = findViewById(R.id.compareLineNumbersToggleButton);
        renderToggleButton = findViewById(R.id.compareRenderToggleButton);
        closeButton = findViewById(R.id.compareCloseButton);
        sourceLineNumbers = findViewById(R.id.compareSourceLineNumbers);
        translatedLineNumbers = findViewById(R.id.compareTranslatedLineNumbers);
        sourceLineDivider = findViewById(R.id.compareSourceLineDivider);
        translatedLineDivider = findViewById(R.id.compareTranslatedLineDivider);

        setupWebView(sourceRenderedHtml);
        setupWebView(translatedRenderedHtml);
        setupRawCompareText(sourceText);
        setupRawCompareText(translatedText);
        matchLineNumberStyle(sourceText, sourceLineNumbers);
        matchLineNumberStyle(translatedText, translatedLineNumbers);
        applySafeInsets(compareRoot);

        rawSourceHtml = "";
        rawTranslatedHtml = "";
        rawHtmlLoaded = false;
        updateRawTextMode();
        sourceLineNumbers.bindTo(sourceText);
        translatedLineNumbers.bindTo(translatedText);
        sourceText.post(this::invalidateLineNumberGutters);

        Intent intent = getIntent();
        String sourceHtmlPath = intent.getStringExtra(EXTRA_SOURCE_HTML_PATH);
        String translatedHtmlPath = intent.getStringExtra(EXTRA_TRANSLATED_HTML_PATH);
        compareRoot.post(() -> loadHtmlFromCacheAsync(sourceHtmlPath, translatedHtmlPath));

        sourceText.setOnScrollChangeListener(
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    syncScroll(sourceText, translatedText, scrollX, scrollY);
                    invalidateLineNumberGutters();
                });
        translatedText.setOnScrollChangeListener(
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    syncScroll(translatedText, sourceText, scrollX, scrollY);
                    invalidateLineNumberGutters();
                });
        sourceRenderedHtml.setOnScrollChangeListener(
                (v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                        syncScroll(sourceRenderedHtml, translatedRenderedHtml, scrollX, scrollY));
        translatedRenderedHtml.setOnScrollChangeListener(
                (v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                        syncScroll(translatedRenderedHtml, sourceRenderedHtml, scrollX, scrollY));

        normalizeToggleButton.setOnClickListener(v -> toggleNormalizeMode());
        lineNumbersToggleButton.setOnClickListener(v -> toggleLineNumbers());
        renderToggleButton.setOnClickListener(v -> toggleRenderMode());
        closeButton.setOnClickListener(v -> finish());
        applyRenderMode();
        showRenderToggleTemporarily();
    }

    private void setupWebView(WebView webView) {
        webView.setBackgroundColor(0x00000000);
        webView.setVerticalScrollBarEnabled(true);
        webView.setHorizontalScrollBarEnabled(true);
        webView.setScrollbarFadingEnabled(true);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
    }

    private static void setupRawCompareText(TextView textView) {
        textView.setHorizontallyScrolling(true);
        textView.setMovementMethod(new ScrollingMovementMethod());
        textView.setVerticalScrollBarEnabled(true);
        textView.setHorizontalScrollBarEnabled(true);
        textView.setScrollbarFadingEnabled(false);
    }

    private static void matchLineNumberStyle(
            TextView contentView, LineNumberGutterView lineNumbersView) {
        if (contentView == null || lineNumbersView == null) {
            return;
        }
        lineNumbersView.applyTextMetricsFrom(contentView);
    }

    private void toggleRenderMode() {
        renderModeEnabled = !renderModeEnabled;
        applyRenderMode();
        showRenderToggleTemporarily();
    }

    private void toggleNormalizeMode() {
        if (renderModeEnabled || !rawHtmlLoaded || normalizeInProgress) {
            return;
        }
        normalizeModeEnabled = !normalizeModeEnabled;
        if (normalizeModeEnabled) {
            maybeStartNormalizeAsync();
        }
        updateRawTextMode();
        updateNormalizeToggleIcon();
        showRenderToggleTemporarily();
    }

    private void toggleLineNumbers() {
        if (renderModeEnabled || !rawHtmlLoaded) {
            return;
        }
        lineNumbersEnabled = !lineNumbersEnabled;
        updateLineNumbersVisibility();
        updateLineNumbersToggleIcon();
        showRenderToggleTemporarily();
    }

    private void applyRenderMode() {
        if (renderModeEnabled) {
            sourceText.setVisibility(View.GONE);
            translatedText.setVisibility(View.GONE);
            sourceRenderedHtml.setVisibility(View.VISIBLE);
            translatedRenderedHtml.setVisibility(View.VISIBLE);
            lineNumbersToggleButton.setEnabled(false);
            lineNumbersToggleButton.setClickable(false);
            sourceLineNumbers.setVisibility(View.GONE);
            translatedLineNumbers.setVisibility(View.GONE);
            sourceLineDivider.setVisibility(View.GONE);
            translatedLineDivider.setVisibility(View.GONE);
            renderHtmlToWebView(sourceRenderedHtml, sourceText.getText().toString());
            renderHtmlToWebView(translatedRenderedHtml, translatedText.getText().toString());
        } else {
            updateRawTextMode();
            sourceText.setVisibility(View.VISIBLE);
            translatedText.setVisibility(View.VISIBLE);
            sourceRenderedHtml.setVisibility(View.GONE);
            translatedRenderedHtml.setVisibility(View.GONE);
            lineNumbersToggleButton.setEnabled(rawHtmlLoaded);
            lineNumbersToggleButton.setClickable(rawHtmlLoaded);
            updateLineNumbersVisibility();
        }
        updateNormalizeToggleIcon();
        updateLineNumbersToggleIcon();
        updateRenderToggleIcon();
    }

    private void updateRawTextMode() {
        String sourceDisplay;
        String translatedDisplay;
        if (normalizeModeEnabled) {
            if (normalizedSourceHtml != null && normalizedTranslatedHtml != null) {
                sourceDisplay = normalizedSourceHtml;
                translatedDisplay = normalizedTranslatedHtml;
            } else {
                sourceDisplay = rawSourceHtml;
                translatedDisplay = rawTranslatedHtml;
                maybeStartNormalizeAsync();
            }
        } else {
            sourceDisplay = rawSourceHtml;
            translatedDisplay = rawTranslatedHtml;
        }
        sourceText.setText(sourceDisplay);
        translatedText.setText(translatedDisplay);
        invalidateLineNumberGutters();
    }

    private void maybeStartNormalizeAsync() {
        if (!normalizeModeEnabled
                || normalizeInProgress
                || normalizedSourceHtml != null
                || normalizedTranslatedHtml != null) {
            return;
        }
        normalizeInProgress = true;
        updateNormalizeToggleIcon();
        loadExecutor.execute(
                () -> {
                    try {
                        String sourceNormalized = HtmlCompareFormatter.normalize(rawSourceHtml);
                        String translatedNormalized =
                                HtmlCompareFormatter.normalize(rawTranslatedHtml);
                        runOnUiThread(
                                () ->
                                        applyNormalizedHtml(
                                                sourceNormalized, translatedNormalized, false));
                    } catch (RuntimeException exception) {
                        runOnUiThread(() -> applyNormalizedHtml(null, null, true));
                    }
                });
    }

    private void applyNormalizedHtml(
            String sourceNormalized, String translatedNormalized, boolean failed) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        normalizeInProgress = false;
        if (failed) {
            normalizedSourceHtml = null;
            normalizedTranslatedHtml = null;
            normalizeModeEnabled = false;
            Toast.makeText(this, R.string.compare_normalize_failed, Toast.LENGTH_SHORT).show();
        } else {
            normalizedSourceHtml = sourceNormalized;
            normalizedTranslatedHtml = translatedNormalized;
        }
        updateRawTextMode();
        updateNormalizeToggleIcon();
    }

    private void updateNormalizeToggleIcon() {
        boolean enabled = !renderModeEnabled && rawHtmlLoaded && !normalizeInProgress;
        normalizeToggleButton.setEnabled(enabled);
        normalizeToggleButton.setClickable(enabled);
        normalizeToggleButton.setAlpha(enabled ? 1f : 0.45f);
        int tintColor;
        if (!enabled) {
            tintColor =
                    normalizeModeEnabled
                            ? getColor(R.color.mlkit_primary_variant)
                            : getColor(R.color.mlkit_on_surface_variant);
        } else {
            tintColor =
                    normalizeModeEnabled
                            ? getColor(R.color.mlkit_primary)
                            : getColor(R.color.mlkit_on_surface_variant);
        }
        normalizeToggleButton.setImageTintList(ColorStateList.valueOf(tintColor));
        normalizeToggleButton.setContentDescription(
                getString(
                        normalizeModeEnabled
                                ? R.string.compare_normalize_disable
                                : R.string.compare_normalize_enable));
    }

    private void updateRenderToggleIcon() {
        renderToggleButton.setImageResource(
                renderModeEnabled
                        ? R.drawable.ic_render_preview_on
                        : R.drawable.ic_render_preview_off);
        int tintColor =
                renderModeEnabled
                        ? getColor(R.color.mlkit_primary)
                        : getColor(R.color.mlkit_on_surface_variant);
        renderToggleButton.setImageTintList(ColorStateList.valueOf(tintColor));
        renderToggleButton.setContentDescription(
                getString(
                        renderModeEnabled
                                ? R.string.compare_render_html_disable
                                : R.string.compare_render_html_enable));
    }

    private void updateLineNumbersToggleIcon() {
        if (lineNumbersToggleButton == null) {
            return;
        }
        boolean enabled = !renderModeEnabled && rawHtmlLoaded;
        lineNumbersToggleButton.setEnabled(enabled);
        lineNumbersToggleButton.setClickable(enabled);
        lineNumbersToggleButton.setImageResource(R.drawable.ic_line_numbers);
        int tintColor =
                lineNumbersEnabled
                        ? getColor(R.color.mlkit_primary)
                        : getColor(R.color.mlkit_on_surface_variant);
        lineNumbersToggleButton.setImageTintList(ColorStateList.valueOf(tintColor));
        lineNumbersToggleButton.setAlpha(enabled ? 1f : 0.45f);
        lineNumbersToggleButton.setContentDescription(
                getString(
                        lineNumbersEnabled
                                ? R.string.compare_line_numbers_disable
                                : R.string.compare_line_numbers_enable));
    }

    private void updateLineNumbersVisibility() {
        int visibility = rawHtmlLoaded && lineNumbersEnabled ? View.VISIBLE : View.GONE;
        sourceLineNumbers.setVisibility(visibility);
        translatedLineNumbers.setVisibility(visibility);
        sourceLineDivider.setVisibility(visibility);
        translatedLineDivider.setVisibility(visibility);
        invalidateLineNumberGutters();
    }

    private void invalidateLineNumberGutters() {
        if (sourceLineNumbers != null && sourceLineNumbers.getVisibility() == View.VISIBLE) {
            sourceLineNumbers.invalidate();
        }
        if (translatedLineNumbers != null
                && translatedLineNumbers.getVisibility() == View.VISIBLE) {
            translatedLineNumbers.invalidate();
        }
    }

    private void showRenderToggleTemporarily() {
        if (normalizeToggleButton == null
                || lineNumbersToggleButton == null
                || renderToggleButton == null
                || closeButton == null) {
            return;
        }
        renderToggleButton.removeCallbacks(hideRenderToggleRunnable);
        if (!isRenderToggleVisible) {
            isRenderToggleVisible = true;
            normalizeToggleButton.setVisibility(View.VISIBLE);
            normalizeToggleButton.setClickable(normalizeToggleButton.isEnabled());
            lineNumbersToggleButton.setVisibility(View.VISIBLE);
            lineNumbersToggleButton.setClickable(lineNumbersToggleButton.isEnabled());
            renderToggleButton.setVisibility(View.VISIBLE);
            renderToggleButton.setClickable(true);
            closeButton.setVisibility(View.VISIBLE);
            closeButton.setClickable(true);
            normalizeToggleButton.animate().cancel();
            lineNumbersToggleButton.animate().cancel();
            renderToggleButton.animate().cancel();
            closeButton.animate().cancel();
            normalizeToggleButton.setAlpha(0f);
            lineNumbersToggleButton.setAlpha(0f);
            renderToggleButton.setAlpha(0f);
            closeButton.setAlpha(0f);
            normalizeToggleButton
                    .animate()
                    .alpha(normalizeToggleButton.isEnabled() ? 1f : 0.45f)
                    .setDuration(TOGGLE_FADE_DURATION_MS)
                    .start();
            lineNumbersToggleButton
                    .animate()
                    .alpha(lineNumbersToggleButton.isEnabled() ? 1f : 0.45f)
                    .setDuration(TOGGLE_FADE_DURATION_MS)
                    .start();
            renderToggleButton.animate().alpha(1f).setDuration(TOGGLE_FADE_DURATION_MS).start();
            closeButton.animate().alpha(1f).setDuration(TOGGLE_FADE_DURATION_MS).start();
        }
        renderToggleButton.postDelayed(hideRenderToggleRunnable, TOGGLE_AUTO_HIDE_DELAY_MS);
    }

    private void hideRenderToggle() {
        if (normalizeToggleButton == null
                || lineNumbersToggleButton == null
                || renderToggleButton == null
                || closeButton == null
                || !isRenderToggleVisible) {
            return;
        }
        renderToggleButton.removeCallbacks(hideRenderToggleRunnable);
        normalizeToggleButton.animate().cancel();
        lineNumbersToggleButton.animate().cancel();
        renderToggleButton.animate().cancel();
        closeButton.animate().cancel();
        normalizeToggleButton
                .animate()
                .alpha(0f)
                .setDuration(TOGGLE_FADE_DURATION_MS)
                .withEndAction(
                        () -> {
                            normalizeToggleButton.setVisibility(View.INVISIBLE);
                            normalizeToggleButton.setClickable(false);
                        })
                .start();
        lineNumbersToggleButton
                .animate()
                .alpha(0f)
                .setDuration(TOGGLE_FADE_DURATION_MS)
                .withEndAction(
                        () -> {
                            lineNumbersToggleButton.setVisibility(View.INVISIBLE);
                            lineNumbersToggleButton.setClickable(false);
                        })
                .start();
        renderToggleButton
                .animate()
                .alpha(0f)
                .setDuration(TOGGLE_FADE_DURATION_MS)
                .withEndAction(
                        () -> {
                            renderToggleButton.setVisibility(View.INVISIBLE);
                            renderToggleButton.setClickable(false);
                            isRenderToggleVisible = false;
                        })
                .start();
        closeButton
                .animate()
                .alpha(0f)
                .setDuration(TOGGLE_FADE_DURATION_MS)
                .withEndAction(
                        () -> {
                            closeButton.setVisibility(View.INVISIBLE);
                            closeButton.setClickable(false);
                        })
                .start();
    }

    private void renderHtmlToWebView(WebView webView, String htmlBody) {
        String safeBody = htmlBody == null ? "" : htmlBody;
        webView.loadDataWithBaseURL(null, wrapHtmlDocument(safeBody), "text/html", "utf-8", null);
    }

    private void loadHtmlFromCacheAsync(String sourcePath, String translatedPath) {
        loadExecutor.execute(
                () -> {
                    try {
                        String sourceHtml = readUtf8File(sourcePath);
                        String translatedHtml = readUtf8File(translatedPath);
                        runOnUiThread(() -> applyLoadedHtml(sourceHtml, translatedHtml));
                    } catch (IOException exception) {
                        runOnUiThread(this::handleHtmlLoadFailure);
                    }
                });
    }

    private void applyLoadedHtml(String sourceHtml, String translatedHtml) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        rawSourceHtml = sourceHtml;
        rawTranslatedHtml = translatedHtml;
        rawHtmlLoaded = true;
        normalizedSourceHtml = null;
        normalizedTranslatedHtml = null;
        normalizeInProgress = false;
        updateRawTextMode();
        updateNormalizeToggleIcon();
        updateLineNumbersToggleIcon();
        updateLineNumbersVisibility();
        if (renderModeEnabled) {
            renderHtmlToWebView(sourceRenderedHtml, rawSourceHtml);
            renderHtmlToWebView(translatedRenderedHtml, rawTranslatedHtml);
        }
    }

    private void handleHtmlLoadFailure() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Toast.makeText(this, R.string.compare_load_failed, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static String readUtf8File(String filePath) throws IOException {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[8192];
        int readCount;
        try (Reader reader =
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8)) {
            while ((readCount = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, readCount);
            }
        }
        return builder.toString();
    }

    private String wrapHtmlDocument(String body) {
        String textColor = toCssColor(getColor(R.color.mlkit_on_background));
        String linkColor = toCssColor(getColor(R.color.mlkit_primary));
        String codeBackground = toCssColor(getColor(R.color.mlkit_code_block_bg));
        String codeText = toCssColor(getColor(R.color.mlkit_on_surface_variant));
        String tableBorder = toCssColor(getColor(R.color.mlkit_outline));
        String tableHeaderBackground = toCssColor(getColor(R.color.mlkit_surface));
        return "<html><head><meta charset='utf-8' /><meta name='color-scheme' content='light dark' /><style>"
                + "body{color:"
                + textColor
                + ";font-family:sans-serif;padding:0;margin:0;background:transparent;overflow-x:auto;white-space:nowrap;}"
                + "p,li,blockquote,td,th,h1,h2,h3,h4,h5,h6,a,span,strong,em,div{white-space:nowrap;}"
                + "a{color:"
                + linkColor
                + ";}"
                + "pre,code{white-space:pre;background:"
                + codeBackground
                + ";color:"
                + codeText
                + ";border-radius:8px;}"
                + "code{padding:0.15em 0.35em;}"
                + "pre{padding:8px;}"
                + "table{border-collapse:collapse;width:100%;margin:8px 0;display:block;overflow-x:auto;}"
                + "th,td{border:1px solid "
                + tableBorder
                + ";padding:6px 8px;text-align:left;}"
                + "th{background:"
                + tableHeaderBackground
                + ";}"
                + "</style></head><body>"
                + body
                + "</body></html>";
    }

    private static String toCssColor(int colorInt) {
        return String.format(Locale.US, "#%06X", 0xFFFFFF & colorInt);
    }

    private void hideStatusBar() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller == null) {
            return;
        }
        controller.hide(WindowInsetsCompat.Type.statusBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private static void applySafeInsets(View root) {
        int basePaddingLeft = root.getPaddingLeft();
        int basePaddingTop = root.getPaddingTop();
        int basePaddingRight = root.getPaddingRight();
        int basePaddingBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, insets) -> {
                    Insets safeInsets =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.navigationBars()
                                            | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(
                            basePaddingLeft + safeInsets.left,
                            basePaddingTop + safeInsets.top,
                            basePaddingRight + safeInsets.right,
                            basePaddingBottom + safeInsets.bottom);
                    return insets;
                });
        ViewCompat.requestApplyInsets(root);
    }

    private void syncScroll(View source, View target, int sourceScrollX, int sourceScrollY) {
        if (syncingScroll) {
            return;
        }
        int targetMaxScrollX = calculateMaxHorizontalScroll(target);
        int targetMaxScrollY = calculateMaxVerticalScroll(target);
        int clampedTargetX = Math.max(0, Math.min(sourceScrollX, targetMaxScrollX));
        int clampedTargetY = Math.max(0, Math.min(sourceScrollY, targetMaxScrollY));
        syncingScroll = true;
        target.scrollTo(clampedTargetX, clampedTargetY);
        syncingScroll = false;
    }

    private static int calculateMaxHorizontalScroll(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (textView.getLayout() == null) {
                return 0;
            }
            int lineCount = textView.getLineCount();
            float widestLine = 0f;
            for (int i = 0; i < lineCount; i++) {
                widestLine = Math.max(widestLine, textView.getLayout().getLineWidth(i));
            }
            int contentWidth = (int) Math.ceil(widestLine);
            int visibleWidth =
                    textView.getWidth()
                            - textView.getCompoundPaddingLeft()
                            - textView.getCompoundPaddingRight();
            return Math.max(0, contentWidth - visibleWidth);
        }
        if (view instanceof WebView) {
            return Integer.MAX_VALUE;
        }
        return 0;
    }

    private static int calculateMaxVerticalScroll(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (textView.getLayout() == null) {
                return 0;
            }
            int contentHeight = textView.getLayout().getHeight();
            int visibleHeight =
                    textView.getHeight()
                            - textView.getCompoundPaddingTop()
                            - textView.getCompoundPaddingBottom();
            return Math.max(0, contentHeight - visibleHeight);
        }
        if (view instanceof WebView) {
            WebView webView = (WebView) view;
            int contentHeight = (int) Math.floor(webView.getContentHeight() * webView.getScale());
            int visibleHeight = view.getHeight() - view.getPaddingTop() - view.getPaddingBottom();
            return Math.max(0, contentHeight - visibleHeight);
        }
        return 0;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideStatusBar();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            showRenderToggleTemporarily();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onDestroy() {
        if (renderToggleButton != null) {
            renderToggleButton.removeCallbacks(hideRenderToggleRunnable);
        }
        if (normalizeToggleButton != null) {
            normalizeToggleButton.removeCallbacks(hideRenderToggleRunnable);
        }
        if (lineNumbersToggleButton != null) {
            lineNumbersToggleButton.removeCallbacks(hideRenderToggleRunnable);
        }
        loadExecutor.shutdownNow();
        super.onDestroy();
    }
}
