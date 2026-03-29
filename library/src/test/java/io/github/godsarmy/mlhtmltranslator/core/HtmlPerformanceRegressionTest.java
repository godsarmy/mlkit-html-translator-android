package io.github.godsarmy.mlhtmltranslator.core;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class HtmlPerformanceRegressionTest {

    private static final int WARMUP_ITERATIONS =
            Integer.getInteger("mlhtml.perf.warmupIterations", 1);
    private static final int MEASURED_ITERATIONS =
            Integer.getInteger("mlhtml.perf.measuredIterations", 3);
    private static final int HUGE_REPEAT = Integer.getInteger("mlhtml.perf.hugeRepeat", 100);
    private static final int CHUNK_LENGTH = Integer.getInteger("mlhtml.perf.chunkLength", 3000);
    private static final double THRESHOLD_MULTIPLIER =
            doubleProperty("mlhtml.perf.thresholdMultiplier", 1.0d);
    private static final boolean VERBOSE = Boolean.getBoolean("mlhtml.perf.verbose");

    @Test
    public void largeAndComplexFixtures_completeWithinRegressionThresholds() throws Exception {
        Map<String, Long> maxElapsedMillisByFixture = new LinkedHashMap<>();
        maxElapsedMillisByFixture.put("fixtures/perf/large-prose.html", 3000L);
        maxElapsedMillisByFixture.put("fixtures/perf/complex-structure.html", 3500L);
        maxElapsedMillisByFixture.put("fixtures/perf/mixed-worst-case.html", 4000L);

        HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder().setMaxChunkChars(CHUNK_LENGTH).build();
        MlTranslationAdapter adapter = new DeterministicNoOpAdapter();

        for (Map.Entry<String, Long> entry : maxElapsedMillisByFixture.entrySet()) {
            String fixturePath = entry.getKey();
            long expectedMaxMs = entry.getValue();
            String html = readFixture(fixturePath);

            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                executePipeline(engine, options, adapter, html);
            }

            long maxObservedNanos = 0L;
            int observedNodes = 0;
            int observedChunks = 0;

            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                PipelineProbe probe = executePipeline(engine, options, adapter, html);
                maxObservedNanos = Math.max(maxObservedNanos, probe.elapsedNanos);
                observedNodes = probe.totalNodes;
                observedChunks = probe.chunkCount;
            }

            long observedMaxMs = nanosToMillis(maxObservedNanos);
            long allowedMs = Math.round(expectedMaxMs * THRESHOLD_MULTIPLIER);

            assertTrue(
                    fixturePath
                            + " expected max <="
                            + allowedMs
                            + "ms but was "
                            + observedMaxMs
                            + "ms",
                    observedMaxMs <= allowedMs);
            assertTrue(fixturePath + " should produce nodes", observedNodes > 0);
            assertTrue(fixturePath + " should produce chunks", observedChunks > 0);

            log(
                    "fixture=%s observedMaxMs=%d allowedMs=%d nodes=%d chunks=%d",
                    fixturePath, observedMaxMs, allowedMs, observedNodes, observedChunks);
        }
    }

    @Test
    public void hugeFixtureSeed_expandedInput_hasStableMemoryAndNoFailure() throws Exception {
        String hugeSeed = readFixture("fixtures/perf/huge-document.html");
        String hugeHtml = repeatSeed(hugeSeed, HUGE_REPEAT);

        HtmlBodyTranslationEngine engine = new HtmlBodyTranslationEngine();
        HtmlTranslationOptions options =
                HtmlTranslationOptions.builder().setMaxChunkChars(CHUNK_LENGTH).build();
        MlTranslationAdapter adapter = new DeterministicNoOpAdapter();

        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = usedMemory(runtime);
        PipelineProbe firstRun = executePipeline(engine, options, adapter, hugeHtml);
        long afterFirstRun = usedMemory(runtime);
        PipelineProbe secondRun = executePipeline(engine, options, adapter, hugeHtml);
        long afterSecondRun = usedMemory(runtime);

        assertTrue("huge input should produce many nodes", firstRun.totalNodes > 100);
        assertTrue("huge input should produce many chunks", firstRun.chunkCount > 10);
        assertNotNull("translated output should not be null", firstRun.translatedHtml);
        assertTrue("translated output should not be empty", !firstRun.translatedHtml.isEmpty());

        long growthFirstRun = Math.max(0L, afterFirstRun - memoryBefore);
        long growthSecondRun = Math.max(0L, afterSecondRun - afterFirstRun);
        long maxAllowedGrowthBytes = 256L * 1024L * 1024L;

        assertTrue(
                "first run memory growth too high: " + growthFirstRun,
                growthFirstRun < maxAllowedGrowthBytes);
        assertTrue(
                "second run memory growth too high: " + growthSecondRun,
                growthSecondRun < maxAllowedGrowthBytes);
        assertTrue("second run should complete", secondRun.elapsedNanos > 0L);

        log(
                "huge repeat=%d elapsed1Ms=%d elapsed2Ms=%d nodes=%d chunks=%d growth1Bytes=%d growth2Bytes=%d",
                HUGE_REPEAT,
                nanosToMillis(firstRun.elapsedNanos),
                nanosToMillis(secondRun.elapsedNanos),
                firstRun.totalNodes,
                firstRun.chunkCount,
                growthFirstRun,
                growthSecondRun);
    }

    private static PipelineProbe executePipeline(
            HtmlBodyTranslationEngine engine,
            HtmlTranslationOptions options,
            MlTranslationAdapter adapter,
            String html)
            throws Exception {
        long start = System.nanoTime();
        HtmlBodyTranslationEngine.PipelineResult result =
                engine.translateHtmlBodyWithReport(
                        html, "en", "es", options, adapter, new AtomicBoolean(false));
        long elapsedNanos = System.nanoTime() - start;

        return new PipelineProbe(
                elapsedNanos,
                result.getDiagnostics().getTotalNodes(),
                result.getDiagnostics().getChunkCount(),
                result.getTranslatedHtml());
    }

    private static String readFixture(String path) {
        try (InputStream stream =
                HtmlPerformanceRegressionTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing fixture: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read fixture: " + path, exception);
        }
    }

    private static String repeatSeed(String seed, int times) {
        StringBuilder builder = new StringBuilder(seed.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(seed).append('\n');
        }
        return builder.toString();
    }

    private static long usedMemory(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private static double doubleProperty(String key, double defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static void log(String format, Object... args) {
        if (!VERBOSE) {
            return;
        }
        System.out.println("[PERF] " + String.format(format, args));
    }

    private static final class PipelineProbe {
        private final long elapsedNanos;
        private final int totalNodes;
        private final int chunkCount;
        private final String translatedHtml;

        private PipelineProbe(
                long elapsedNanos, int totalNodes, int chunkCount, String translatedHtml) {
            this.elapsedNanos = elapsedNanos;
            this.totalNodes = totalNodes;
            this.chunkCount = chunkCount;
            this.translatedHtml = translatedHtml;
        }
    }

    private static final class DeterministicNoOpAdapter implements MlTranslationAdapter {
        @Override
        public String translate(
                String text, String sourceLanguage, String targetLanguage, long timeoutMs) {
            return text;
        }
    }
}
