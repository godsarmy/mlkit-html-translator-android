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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class HtmlBodyTranslationEngine {

    private static final int DEFAULT_MAX_IN_FLIGHT_CHUNKS = 2;
    private static final long DEFAULT_CHUNK_TIMEOUT_MS = 10_000L;

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
        Document document = Jsoup.parseBodyFragment(htmlBody);
        List<CollectedTextNode> nodes =
                nodeCollector.collectEligibleNodes(document.body(), options);

        if (nodes.isEmpty()) {
            return new PipelineResult(document.body().html(), new Diagnostics(0, 0, 0, 0, 0));
        }

        TokenMasker.MaskingConfig maskingConfig =
                new TokenMasker.MaskingConfig(
                        options.isMaskUrls(), options.isMaskPlaceholders(), options.isMaskPaths());

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

        ChunkAggregate aggregate =
                translateChunks(
                        chunks,
                        sourceLanguage,
                        targetLanguage,
                        markerCodec,
                        translationAdapter,
                        options,
                        cancelled);

        for (int i = 0; i < nodes.size(); i++) {
            CollectedTextNode node = nodes.get(i);
            String translatedMaskedText = aggregate.translatedMaskedByNodeIndex.get(i);
            if (translatedMaskedText == null) {
                throw new TranslationException(
                        TranslationErrorCode.INTERNAL_ERROR,
                        "Missing translated segment for node index " + i);
            }

            String unmasked =
                    tokenMasker.unmask(
                            translatedMaskedText, maskingResults.get(i).getPlaceholderToOriginal());
            String restored = node.getLeadingWhitespace() + unmasked + node.getTrailingWhitespace();
            node.getTextNode().text(restored);
        }

        Diagnostics diagnostics =
                new Diagnostics(
                        nodes.size(),
                        aggregate.translatedNodes,
                        aggregate.failedNodes,
                        aggregate.retryCount,
                        chunks.size());
        return new PipelineResult(document.body().html(), diagnostics);
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

    public static final class Diagnostics {
        private final int totalNodes;
        private final int translatedNodes;
        private final int failedNodes;
        private final int retryCount;
        private final int chunkCount;

        Diagnostics(
                int totalNodes,
                int translatedNodes,
                int failedNodes,
                int retryCount,
                int chunkCount) {
            this.totalNodes = totalNodes;
            this.translatedNodes = translatedNodes;
            this.failedNodes = failedNodes;
            this.retryCount = retryCount;
            this.chunkCount = chunkCount;
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
    }

    private static final class ChunkAggregate {
        private final Map<Integer, String> translatedMaskedByNodeIndex = new LinkedHashMap<>();
        private int translatedNodes;
        private int failedNodes;
        private int retryCount;
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
