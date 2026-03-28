package io.github.godsarmy.mlhtmltranslator.api;

public final class TranslationTimingReport {

    private final long startedAtEpochMs;
    private final long completedAtEpochMs;
    private final int chunkCount;
    private final int totalNodes;
    private final int translatedNodes;
    private final int failedNodes;
    private final int retryCount;

    public TranslationTimingReport(long startedAtEpochMs, long completedAtEpochMs, int chunkCount) {
        this(startedAtEpochMs, completedAtEpochMs, chunkCount, 0, 0, 0, 0);
    }

    public TranslationTimingReport(
            long startedAtEpochMs,
            long completedAtEpochMs,
            int chunkCount,
            int totalNodes,
            int translatedNodes,
            int failedNodes,
            int retryCount) {
        this.startedAtEpochMs = startedAtEpochMs;
        this.completedAtEpochMs = completedAtEpochMs;
        this.chunkCount = chunkCount;
        this.totalNodes = totalNodes;
        this.translatedNodes = translatedNodes;
        this.failedNodes = failedNodes;
        this.retryCount = retryCount;
    }

    public long getStartedAtEpochMs() {
        return startedAtEpochMs;
    }

    public long getCompletedAtEpochMs() {
        return completedAtEpochMs;
    }

    public long getDurationMs() {
        return Math.max(0L, completedAtEpochMs - startedAtEpochMs);
    }

    public int getChunkCount() {
        return chunkCount;
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
}
