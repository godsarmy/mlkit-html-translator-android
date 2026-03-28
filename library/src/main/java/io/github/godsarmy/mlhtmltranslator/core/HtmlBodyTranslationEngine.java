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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
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
        Document document = Jsoup.parseBodyFragment(htmlBody);
        List<CollectedTextNode> nodes =
                nodeCollector.collectEligibleNodes(document.body(), options);

        if (nodes.isEmpty()) {
            return document.body().html();
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

        Map<Integer, String> translatedMaskedByNodeIndex =
                translateChunks(
                        chunks,
                        sourceLanguage,
                        targetLanguage,
                        markerCodec,
                        translationAdapter,
                        cancelled);

        for (int i = 0; i < nodes.size(); i++) {
            CollectedTextNode node = nodes.get(i);
            String translatedMaskedText = translatedMaskedByNodeIndex.get(i);
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

        return document.body().html();
    }

    @NonNull
    private Map<Integer, String> translateChunks(
            @NonNull List<ChunkBuilder.Chunk> chunks,
            @NonNull String sourceLanguage,
            @NonNull String targetLanguage,
            @NonNull SegmentMarkerCodec markerCodec,
            @NonNull MlTranslationAdapter translationAdapter,
            @NonNull AtomicBoolean cancelled)
            throws TranslationException {
        int inFlightChunkCount = Math.max(1, Math.min(DEFAULT_MAX_IN_FLIGHT_CHUNKS, chunks.size()));
        ExecutorService executor = Executors.newFixedThreadPool(inFlightChunkCount);
        List<Future<Map<Integer, String>>> futures = new ArrayList<>();

        try {
            for (ChunkBuilder.Chunk chunk : chunks) {
                if (cancelled.get()) {
                    throw new TranslationException(
                            TranslationErrorCode.CANCELLED,
                            "Translation cancelled before chunk submission");
                }

                Callable<Map<Integer, String>> task =
                        () -> {
                            if (cancelled.get()) {
                                throw new TranslationException(
                                        TranslationErrorCode.CANCELLED,
                                        "Translation cancelled during chunk processing");
                            }

                            String translatedPayload =
                                    translationAdapter.translate(
                                            chunk.getPayload(),
                                            sourceLanguage,
                                            targetLanguage,
                                            DEFAULT_CHUNK_TIMEOUT_MS);
                            return chunkResultMapper.mapChunkResult(
                                    chunk, translatedPayload, markerCodec);
                        };
                futures.add(executor.submit(task));
            }

            Map<Integer, String> translatedMaskedByNodeIndex = new ConcurrentHashMap<>();
            for (Future<Map<Integer, String>> future : futures) {
                Map<Integer, String> mapped;
                try {
                    mapped = future.get(DEFAULT_CHUNK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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

                translatedMaskedByNodeIndex.putAll(mapped);
            }

            return translatedMaskedByNodeIndex;
        } finally {
            for (Future<Map<Integer, String>> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
            executor.shutdownNow();
        }
    }
}
