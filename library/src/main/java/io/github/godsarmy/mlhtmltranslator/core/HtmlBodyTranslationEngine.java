package io.github.godsarmy.mlhtmltranslator.core;

import androidx.annotation.NonNull;
import io.github.godsarmy.mlhtmltranslator.api.HtmlTranslationOptions;
import io.github.godsarmy.mlhtmltranslator.api.TranslationErrorCode;
import io.github.godsarmy.mlhtmltranslator.api.TranslationException;
import io.github.godsarmy.mlhtmltranslator.backend.MlTranslationAdapter;
import io.github.godsarmy.mlhtmltranslator.batch.ChunkBuilder;
import io.github.godsarmy.mlhtmltranslator.batch.ChunkResultMapper;
import io.github.godsarmy.mlhtmltranslator.batch.SegmentMarkerCodec;
import io.github.godsarmy.mlhtmltranslator.mask.TokenMasker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class HtmlBodyTranslationEngine {

    private static final int DEFAULT_MAX_IN_FLIGHT_CHUNKS = 2;
    private static final long DEFAULT_CHUNK_TIMEOUT_MS = 10_000L;
    private static final Pattern LEGACY_MARKER_ARTIFACT_PATTERN =
            Pattern.compile("\\[\\[\\[(?:\\s*SEG\\b[^\\]]*)?\\]?\\]?\\]?");
    private static final Pattern COMPACT_MARKER_ARTIFACT_PATTERN =
            Pattern.compile("⟦\\s*M\\b[^⟧]*⟧?");
    private static final Pattern ASCII_MARKER_ARTIFACT_PATTERN =
            Pattern.compile("@@\\s*MLHT\\b[^@]*(?:@@)?");

    private final NodeCollector nodeCollector = new NodeCollector();
    private final TokenMasker tokenMasker = new TokenMasker();
    private final ChunkBuilder chunkBuilder = new ChunkBuilder();
    private final ChunkResultMapper chunkResultMapper = new ChunkResultMapper();

    @NonNull
    public List<CollectedTextNode> collectEligibleTextNodes(
            @NonNull String htmlBody, @NonNull HtmlTranslationOptions options) {
        Document document = Jsoup.parseBodyFragment(htmlBody);
        return nodeCollector.collectEligibleNodes(document.body(), options);
    }

    @NonNull
    public PreprocessResult explainHtmlPreprocess(
            @NonNull String htmlBody, @NonNull HtmlTranslationOptions options) {
        Preparation preparation = preparePipeline(htmlBody, options);
        List<PreprocessedNode> preprocessedNodes = new ArrayList<>(preparation.nodes.size());
        for (int i = 0; i < preparation.nodes.size(); i++) {
            CollectedTextNode node = preparation.nodes.get(i);
            TokenMasker.MaskingResult maskingResult = preparation.maskingResults.get(i);
            preprocessedNodes.add(
                    new PreprocessedNode(
                            i,
                            node.getLeadingWhitespace(),
                            node.getTranslatableText(),
                            node.getTrailingWhitespace(),
                            maskingResult.getMaskedText(),
                            maskingResult.getPlaceholderToOriginal()));
        }

        return new PreprocessResult(
                preparation.document.body().html(), preprocessedNodes, preparation.chunks);
    }

    @NonNull
    public String translateHtmlBody(
            @NonNull String htmlBody,
            @NonNull String sourceLanguage,
            @NonNull String targetLanguage,
            @NonNull HtmlTranslationOptions options,
            @NonNull MlTranslationAdapter translationAdapter,
            @NonNull AtomicBoolean cancelled)
            throws TranslationException {
        return translateHtmlBodyWithReport(
                        htmlBody,
                        sourceLanguage,
                        targetLanguage,
                        options,
                        translationAdapter,
                        cancelled)
                .getTranslatedHtml();
    }

    @NonNull
    public PipelineResult translateHtmlBodyWithReport(
            @NonNull String htmlBody,
            @NonNull String sourceLanguage,
            @NonNull String targetLanguage,
            @NonNull HtmlTranslationOptions options,
            @NonNull MlTranslationAdapter translationAdapter,
            @NonNull AtomicBoolean cancelled)
            throws TranslationException {
        Preparation preparation = preparePipeline(htmlBody, options);

        if (preparation.nodes.isEmpty()) {
            return new PipelineResult(
                    preparation.document.body().html(),
                    new Diagnostics(
                            0,
                            0,
                            0,
                            0,
                            0,
                            preparation.parseWalkDurationMs,
                            preparation.maskChunkDurationMs,
                            0));
        }

        long translationStartNs = System.nanoTime();
        ChunkAggregate aggregate =
                translateChunks(
                        preparation.chunks,
                        sourceLanguage,
                        targetLanguage,
                        preparation.markerCodec,
                        translationAdapter,
                        options,
                        cancelled);
        long translationDurationMs =
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - translationStartNs);

        for (int i = 0; i < preparation.nodes.size(); i++) {
            CollectedTextNode node = preparation.nodes.get(i);
            String translatedMaskedText = aggregate.translatedMaskedByNodeIndex.get(i);
            if (translatedMaskedText == null) {
                throw new TranslationException(
                        TranslationErrorCode.INTERNAL_ERROR,
                        "Missing translated segment for node index " + i);
            }

            String sanitizedMaskedText = stripMarkerArtifacts(translatedMaskedText);

            String unmasked =
                    tokenMasker.unmask(
                            sanitizedMaskedText,
                            preparation.maskingResults.get(i).getPlaceholderToOriginal());
            String restored = node.getLeadingWhitespace() + unmasked + node.getTrailingWhitespace();
            node.getTextNode().text(restored);
        }

        Diagnostics diagnostics =
                new Diagnostics(
                        preparation.nodes.size(),
                        aggregate.translatedNodes,
                        aggregate.failedNodes,
                        aggregate.retryCount,
                        preparation.chunks.size(),
                        preparation.parseWalkDurationMs,
                        preparation.maskChunkDurationMs,
                        translationDurationMs);
        return new PipelineResult(preparation.document.body().html(), diagnostics);
    }

    @NonNull
    private Preparation preparePipeline(
            @NonNull String htmlBody, @NonNull HtmlTranslationOptions options) {
        long parseWalkStartNs = System.nanoTime();
        Document document = Jsoup.parseBodyFragment(htmlBody);
        List<CollectedTextNode> nodes =
                nodeCollector.collectEligibleNodes(document.body(), options);
        long parseWalkDurationMs =
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - parseWalkStartNs);

        long maskChunkStartNs = System.nanoTime();
        TokenMasker.MaskingConfig maskingConfig =
                new TokenMasker.MaskingConfig(
                        options.isMaskUrls(),
                        options.isMaskPlaceholders(),
                        options.isMaskPaths(),
                        options.getPlaceholderMarkerStart(),
                        options.getPlaceholderMarkerEnd());

        List<TokenMasker.MaskingResult> maskingResults = new ArrayList<>(nodes.size());
        List<String> maskedTexts = new ArrayList<>(nodes.size());
        for (CollectedTextNode node : nodes) {
            TokenMasker.MaskingResult masked =
                    tokenMasker.mask(node.getTranslatableText(), maskingConfig);
            maskingResults.add(masked);
            maskedTexts.add(masked.getMaskedText());
        }

        SegmentMarkerCodec markerCodec = new SegmentMarkerCodec();
        List<ChunkBuilder.Chunk> chunks =
                chunkBuilder.buildChunks(
                        maskedTexts, options.getMaxChunkChars(), null, markerCodec);
        long maskChunkDurationMs =
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - maskChunkStartNs);

        return new Preparation(
                document,
                nodes,
                maskingResults,
                chunks,
                markerCodec,
                parseWalkDurationMs,
                maskChunkDurationMs);
    }

    @NonNull
    private ChunkAggregate translateChunks(
            @NonNull List<ChunkBuilder.Chunk> chunks,
            @NonNull String sourceLanguage,
            @NonNull String targetLanguage,
            @NonNull SegmentMarkerCodec markerCodec,
            @NonNull MlTranslationAdapter translationAdapter,
            @NonNull HtmlTranslationOptions options,
            @NonNull AtomicBoolean cancelled)
            throws TranslationException {
        int inFlightChunkCount = Math.max(1, Math.min(DEFAULT_MAX_IN_FLIGHT_CHUNKS, chunks.size()));
        ExecutorService executor = Executors.newFixedThreadPool(inFlightChunkCount);
        List<Future<ChunkTranslationResult>> futures = new ArrayList<>();

        try {
            for (ChunkBuilder.Chunk chunk : chunks) {
                if (cancelled.get()) {
                    throw new TranslationException(
                            TranslationErrorCode.CANCELLED,
                            "Translation cancelled before chunk submission");
                }

                Callable<ChunkTranslationResult> task =
                        () ->
                                translateChunkWithFallback(
                                        chunk,
                                        sourceLanguage,
                                        targetLanguage,
                                        markerCodec,
                                        translationAdapter,
                                        options,
                                        cancelled,
                                        0);
                futures.add(executor.submit(task));
            }

            ChunkAggregate aggregate = new ChunkAggregate();
            for (Future<ChunkTranslationResult> future : futures) {
                ChunkTranslationResult result;
                try {
                    result = future.get(DEFAULT_CHUNK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException timeoutException) {
                    throw new TranslationException(
                            TranslationErrorCode.TRANSLATION_FAILED,
                            "Chunk translation timed out",
                            timeoutException);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new TranslationException(
                            TranslationErrorCode.CANCELLED,
                            "Chunk translation interrupted",
                            interruptedException);
                } catch (ExecutionException executionException) {
                    Throwable cause = executionException.getCause();
                    if (cause instanceof TranslationException) {
                        throw (TranslationException) cause;
                    }
                    throw new TranslationException(
                            TranslationErrorCode.TRANSLATION_FAILED,
                            "Chunk translation failed",
                            executionException);
                }

                aggregate.translatedMaskedByNodeIndex.putAll(result.translatedMaskedByNodeIndex);
                aggregate.translatedNodes += result.translatedNodes;
                aggregate.failedNodes += result.failedNodes;
                aggregate.retryCount += result.retryCount;
            }

            return aggregate;
        } finally {
            for (Future<ChunkTranslationResult> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
            executor.shutdownNow();
        }
    }

    @NonNull
    private ChunkTranslationResult translateChunkWithFallback(
            @NonNull ChunkBuilder.Chunk chunk,
            @NonNull String sourceLanguage,
            @NonNull String targetLanguage,
            @NonNull SegmentMarkerCodec markerCodec,
            @NonNull MlTranslationAdapter translationAdapter,
            @NonNull HtmlTranslationOptions options,
            @NonNull AtomicBoolean cancelled,
            int retryDepth)
            throws TranslationException {
        if (cancelled.get()) {
            throw new TranslationException(
                    TranslationErrorCode.CANCELLED,
                    "Translation cancelled during chunk processing");
        }

        if (chunk.getNodeIndexes().size() == 1) {
            int nodeIndex = chunk.getNodeIndexes().get(0);
            String originalText = chunk.getOriginalNodeTexts().get(0);
            try {
                String translated =
                        translationAdapter.translate(
                                originalText,
                                sourceLanguage,
                                targetLanguage,
                                DEFAULT_CHUNK_TIMEOUT_MS);

                if (cancelled.get()) {
                    throw new TranslationException(
                            TranslationErrorCode.CANCELLED,
                            "Translation cancelled during chunk processing");
                }

                Map<Integer, String> mapped = new LinkedHashMap<>();
                mapped.put(nodeIndex, translated);
                return ChunkTranslationResult.success(mapped, 1, retryDepth);
            } catch (TranslationException translationException) {
                if (translationException.getErrorCode() == TranslationErrorCode.CANCELLED) {
                    throw translationException;
                }

                if (options.getFailurePolicy() == HtmlTranslationOptions.FailurePolicy.FAIL_FAST) {
                    throw translationException;
                }

                return ChunkTranslationResult.bestEffortOriginal(chunk, retryDepth);
            }
        }

        try {
            String translatedPayload =
                    translationAdapter.translate(
                            chunk.getPayload(),
                            sourceLanguage,
                            targetLanguage,
                            DEFAULT_CHUNK_TIMEOUT_MS);

            if (cancelled.get()) {
                throw new TranslationException(
                        TranslationErrorCode.CANCELLED,
                        "Translation cancelled during chunk processing");
            }

            Map<Integer, String> mapped =
                    chunkResultMapper.mapChunkResult(chunk, translatedPayload, markerCodec);
            return ChunkTranslationResult.success(
                    mapped, chunk.getNodeIndexes().size(), retryDepth);
        } catch (IllegalArgumentException markerParseException) {
            return retryOrFallbackPerNode(
                    chunk,
                    sourceLanguage,
                    targetLanguage,
                    markerCodec,
                    translationAdapter,
                    options,
                    cancelled,
                    retryDepth,
                    markerParseException);
        } catch (TranslationException translationException) {
            if (translationException.getErrorCode() == TranslationErrorCode.CANCELLED) {
                throw translationException;
            }

            if (options.getFailurePolicy() == HtmlTranslationOptions.FailurePolicy.FAIL_FAST) {
                throw translationException;
            }

            return ChunkTranslationResult.bestEffortOriginal(chunk, retryDepth);
        }
    }

    @NonNull
    private ChunkTranslationResult retryOrFallbackPerNode(
            @NonNull ChunkBuilder.Chunk chunk,
            @NonNull String sourceLanguage,
            @NonNull String targetLanguage,
            @NonNull SegmentMarkerCodec markerCodec,
            @NonNull MlTranslationAdapter translationAdapter,
            @NonNull HtmlTranslationOptions options,
            @NonNull AtomicBoolean cancelled,
            int retryDepth,
            @NonNull Exception parseException)
            throws TranslationException {
        if (chunk.getNodeIndexes().size() > 1) {
            List<ChunkBuilder.Chunk> smallerChunks =
                    buildSmallerChunks(chunk, markerCodec, options);
            ChunkTranslationResult aggregate = ChunkTranslationResult.empty();
            for (ChunkBuilder.Chunk smallerChunk : smallerChunks) {
                ChunkTranslationResult child =
                        translateChunkWithFallback(
                                smallerChunk,
                                sourceLanguage,
                                targetLanguage,
                                markerCodec,
                                translationAdapter,
                                options,
                                cancelled,
                                retryDepth + 1);
                aggregate.merge(child);
            }
            aggregate.retryCount += 1;
            return aggregate;
        }

        // Per-node fallback for failing chunk.
        int nodeIndex = chunk.getNodeIndexes().get(0);
        String originalText = chunk.getOriginalNodeTexts().get(0);
        try {
            String translated =
                    translationAdapter.translate(
                            originalText, sourceLanguage, targetLanguage, DEFAULT_CHUNK_TIMEOUT_MS);
            Map<Integer, String> mapped = new LinkedHashMap<>();
            mapped.put(nodeIndex, translated);
            return ChunkTranslationResult.success(mapped, 1, retryDepth + 1);
        } catch (TranslationException fallbackException) {
            if (fallbackException.getErrorCode() == TranslationErrorCode.CANCELLED) {
                throw fallbackException;
            }

            if (options.getFailurePolicy() == HtmlTranslationOptions.FailurePolicy.FAIL_FAST) {
                throw new TranslationException(
                        TranslationErrorCode.TRANSLATION_FAILED,
                        "Per-node fallback failed after marker parse failure",
                        parseException);
            }

            return ChunkTranslationResult.bestEffortOriginal(chunk, retryDepth + 1);
        }
    }

    @NonNull
    private static String stripMarkerArtifacts(@NonNull String translatedMaskedText) {
        String sanitized =
                LEGACY_MARKER_ARTIFACT_PATTERN.matcher(translatedMaskedText).replaceAll("");
        sanitized = COMPACT_MARKER_ARTIFACT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = ASCII_MARKER_ARTIFACT_PATTERN.matcher(sanitized).replaceAll("");
        return sanitized;
    }

    @NonNull
    private List<ChunkBuilder.Chunk> buildSmallerChunks(
            @NonNull ChunkBuilder.Chunk parentChunk,
            @NonNull SegmentMarkerCodec markerCodec,
            @NonNull HtmlTranslationOptions options) {
        int originalTextLen =
                parentChunk.getOriginalNodeTexts().stream().mapToInt(String::length).sum();
        int smallerMaxChars =
                Math.max(1, Math.min(options.getMaxChunkChars() / 2, Math.max(1, originalTextLen)));
        int smallerMaxUnits = Math.max(1, parentChunk.getOriginalNodeTexts().size() / 2);

        List<ChunkBuilder.Chunk> localChunks =
                chunkBuilder.buildChunks(
                        parentChunk.getOriginalNodeTexts(),
                        smallerMaxChars,
                        smallerMaxUnits,
                        markerCodec);

        List<ChunkBuilder.Chunk> remapped = new ArrayList<>(localChunks.size());
        for (ChunkBuilder.Chunk localChunk : localChunks) {
            List<Integer> globalIndexes = new ArrayList<>(localChunk.getNodeIndexes().size());
            for (Integer localIndex : localChunk.getNodeIndexes()) {
                globalIndexes.add(parentChunk.getNodeIndexes().get(localIndex));
            }
            remapped.add(
                    new ChunkBuilder.Chunk(
                            localChunk.getPayload(),
                            globalIndexes,
                            localChunk.getOriginalNodeTexts()));
        }
        return remapped;
    }

    public static final class PipelineResult {
        private final String translatedHtml;
        private final Diagnostics diagnostics;

        PipelineResult(@NonNull String translatedHtml, @NonNull Diagnostics diagnostics) {
            this.translatedHtml = translatedHtml;
            this.diagnostics = diagnostics;
        }

        @NonNull
        public String getTranslatedHtml() {
            return translatedHtml;
        }

        @NonNull
        public Diagnostics getDiagnostics() {
            return diagnostics;
        }
    }

    public static final class PreprocessResult {
        private final String normalizedHtmlBody;
        private final List<PreprocessedNode> nodes;
        private final List<ChunkBuilder.Chunk> chunks;

        PreprocessResult(
                @NonNull String normalizedHtmlBody,
                @NonNull List<PreprocessedNode> nodes,
                @NonNull List<ChunkBuilder.Chunk> chunks) {
            this.normalizedHtmlBody = normalizedHtmlBody;
            this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
            this.chunks = Collections.unmodifiableList(new ArrayList<>(chunks));
        }

        @NonNull
        public String getNormalizedHtmlBody() {
            return normalizedHtmlBody;
        }

        @NonNull
        public List<PreprocessedNode> getNodes() {
            return nodes;
        }

        @NonNull
        public List<ChunkBuilder.Chunk> getChunks() {
            return chunks;
        }
    }

    public static final class PreprocessedNode {
        private final int index;
        private final String leadingWhitespace;
        private final String translatableText;
        private final String trailingWhitespace;
        private final String maskedText;
        private final Map<String, String> placeholders;

        PreprocessedNode(
                int index,
                @NonNull String leadingWhitespace,
                @NonNull String translatableText,
                @NonNull String trailingWhitespace,
                @NonNull String maskedText,
                @NonNull Map<String, String> placeholders) {
            this.index = index;
            this.leadingWhitespace = leadingWhitespace;
            this.translatableText = translatableText;
            this.trailingWhitespace = trailingWhitespace;
            this.maskedText = maskedText;
            this.placeholders = Collections.unmodifiableMap(new LinkedHashMap<>(placeholders));
        }

        public int getIndex() {
            return index;
        }

        @NonNull
        public String getLeadingWhitespace() {
            return leadingWhitespace;
        }

        @NonNull
        public String getTranslatableText() {
            return translatableText;
        }

        @NonNull
        public String getTrailingWhitespace() {
            return trailingWhitespace;
        }

        @NonNull
        public String getMaskedText() {
            return maskedText;
        }

        @NonNull
        public Map<String, String> getPlaceholders() {
            return placeholders;
        }
    }

    public static final class Diagnostics {
        private final int totalNodes;
        private final int translatedNodes;
        private final int failedNodes;
        private final int retryCount;
        private final int chunkCount;
        private final long parseWalkDurationMs;
        private final long maskChunkDurationMs;
        private final long translationDurationMs;

        Diagnostics(
                int totalNodes,
                int translatedNodes,
                int failedNodes,
                int retryCount,
                int chunkCount,
                long parseWalkDurationMs,
                long maskChunkDurationMs,
                long translationDurationMs) {
            this.totalNodes = totalNodes;
            this.translatedNodes = translatedNodes;
            this.failedNodes = failedNodes;
            this.retryCount = retryCount;
            this.chunkCount = chunkCount;
            this.parseWalkDurationMs = parseWalkDurationMs;
            this.maskChunkDurationMs = maskChunkDurationMs;
            this.translationDurationMs = translationDurationMs;
        }

        public int getTotalNodes() {
            return totalNodes;
        }

        public int getTranslatedNodes() {
            return translatedNodes;
        }

        public int getFailedNodes() {
            return failedNodes;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public int getChunkCount() {
            return chunkCount;
        }

        public long getParseWalkDurationMs() {
            return parseWalkDurationMs;
        }

        public long getMaskChunkDurationMs() {
            return maskChunkDurationMs;
        }

        public long getTranslationDurationMs() {
            return translationDurationMs;
        }
    }

    private static final class ChunkAggregate {
        private final Map<Integer, String> translatedMaskedByNodeIndex = new LinkedHashMap<>();
        private int translatedNodes;
        private int failedNodes;
        private int retryCount;
    }

    private static final class Preparation {
        private final Document document;
        private final List<CollectedTextNode> nodes;
        private final List<TokenMasker.MaskingResult> maskingResults;
        private final List<ChunkBuilder.Chunk> chunks;
        private final SegmentMarkerCodec markerCodec;
        private final long parseWalkDurationMs;
        private final long maskChunkDurationMs;

        Preparation(
                @NonNull Document document,
                @NonNull List<CollectedTextNode> nodes,
                @NonNull List<TokenMasker.MaskingResult> maskingResults,
                @NonNull List<ChunkBuilder.Chunk> chunks,
                @NonNull SegmentMarkerCodec markerCodec,
                long parseWalkDurationMs,
                long maskChunkDurationMs) {
            this.document = document;
            this.nodes = nodes;
            this.maskingResults = maskingResults;
            this.chunks = chunks;
            this.markerCodec = markerCodec;
            this.parseWalkDurationMs = parseWalkDurationMs;
            this.maskChunkDurationMs = maskChunkDurationMs;
        }
    }

    private static final class ChunkTranslationResult {
        private final Map<Integer, String> translatedMaskedByNodeIndex;
        private int translatedNodes;
        private int failedNodes;
        private int retryCount;

        ChunkTranslationResult(
                @NonNull Map<Integer, String> translatedMaskedByNodeIndex,
                int translatedNodes,
                int failedNodes,
                int retryCount) {
            this.translatedMaskedByNodeIndex = translatedMaskedByNodeIndex;
            this.translatedNodes = translatedNodes;
            this.failedNodes = failedNodes;
            this.retryCount = retryCount;
        }

        @NonNull
        static ChunkTranslationResult empty() {
            return new ChunkTranslationResult(new LinkedHashMap<>(), 0, 0, 0);
        }

        @NonNull
        static ChunkTranslationResult success(
                @NonNull Map<Integer, String> mapped, int translatedNodes, int retryCount) {
            return new ChunkTranslationResult(
                    new LinkedHashMap<>(mapped), translatedNodes, 0, retryCount);
        }

        @NonNull
        static ChunkTranslationResult bestEffortOriginal(
                @NonNull ChunkBuilder.Chunk chunk, int retryCount) {
            Map<Integer, String> mapped = new LinkedHashMap<>();
            for (int i = 0; i < chunk.getNodeIndexes().size(); i++) {
                mapped.put(chunk.getNodeIndexes().get(i), chunk.getOriginalNodeTexts().get(i));
            }
            return new ChunkTranslationResult(mapped, 0, chunk.getNodeIndexes().size(), retryCount);
        }

        void merge(@NonNull ChunkTranslationResult other) {
            this.translatedMaskedByNodeIndex.putAll(other.translatedMaskedByNodeIndex);
            this.translatedNodes += other.translatedNodes;
            this.failedNodes += other.failedNodes;
            this.retryCount += other.retryCount;
        }
    }
}
