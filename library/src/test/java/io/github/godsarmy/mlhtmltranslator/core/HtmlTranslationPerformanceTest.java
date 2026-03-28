package io.github.godsarmy.mlhtmltranslator.core;

import static org.junit.Assert.assertTrue;

import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class HtmlTranslationPerformanceTest {

    @Test
    public void chunkedStrategyCutsTranslationCallsByAtLeastFortyPercent() throws Exception {
        int nodeCount = 50;
        String html = buildLargeHtml(nodeCount);
        AtomicInteger chunkedCalls = new AtomicInteger(0);

        MlTranslationAdapter adapter =
                (text, sourceLanguage, targetLanguage, timeoutMs) -> {
                    chunkedCalls.incrementAndGet();
                    return text;
                };

        HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder().setMaxChunkChars(3000).build();
        engine.translateHtmlBodyWithReport(
                html, "en", "es", options, adapter, new AtomicBoolean(false));

        int baselineCalls = nodeCount;
        int optimizedCalls = chunkedCalls.get();
        // 40% fewer calls means optimized <= 60% of baseline.
        assertTrue(optimizedCalls <= Math.floor(baselineCalls * 0.6));
    }

    @Test
    public void largeManualLikeHtmlRunsWithoutOomAndReportsDurations() throws Exception {
        String html = buildLargeHtml(800);
        HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
        HtmlTranslationOptions options = HtmlTranslationOptions.builder().build();
        MlTranslationAdapter adapter = (text, sourceLanguage, targetLanguage, timeoutMs) -> text;

        HtmlBodyTranslationEngine.PipelineResult result =
                engine.translateHtmlBodyWithReport(
                        html, "en", "es", options, adapter, new AtomicBoolean(false));

        assertTrue(result.getDiagnostics().getTotalNodes() >= 800);
        assertTrue(result.getDiagnostics().getParseWalkDurationMs() >= 0);
        assertTrue(result.getDiagnostics().getMaskChunkDurationMs() >= 0);
        assertTrue(result.getDiagnostics().getTranslationDurationMs() >= 0);
    }

    private static String buildLargeHtml(int paragraphs) {
        StringBuilder builder = new StringBuilder();
        builder.append("<h1>Operations Manual</h1><ul>");
        for (int i = 0; i < paragraphs; i++) {
            builder.append("<li><p>Step ")
                    .append(i)
                    .append(" deploy service and verify dashboard metrics.</p></li>");
        }
        builder.append("</ul><table><tr><th>Key</th><th>Value</th></tr>");
        for (int i = 0; i < 10; i++) {
            builder.append("<tr><td>k")
                    .append(i)
                    .append("</td><td>v")
                    .append(i)
                    .append("</td></tr>");
        }
        builder.append("</table>");
        return builder.toString();
    }
}
