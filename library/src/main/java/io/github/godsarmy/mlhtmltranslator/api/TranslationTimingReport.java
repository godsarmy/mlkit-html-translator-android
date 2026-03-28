package io.github.godsarmy.mlhtmltranslator.api;

public final class TranslationTimingReport {

    private final long startedAtEpochMs;
    private final long completedAtEpochMs;
    private final int chunkCount;

    public TranslationTimingReport(long startedAtEpochMs, long completedAtEpochMs, int chunkCount) {
        this.startedAtEpochMs = startedAtEpochMs;
        this.completedAtEpochMs = completedAtEpochMs;
        this.chunkCount = chunkCount;
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
}
