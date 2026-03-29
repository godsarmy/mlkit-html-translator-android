package io.github.godsarmy.mlhtmltranslator.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class HtmlTranslationOptions {

    public enum FailurePolicy {
        FAIL_FAST,
        BEST_EFFORT
    }

    private static final Set<String> DEFAULT_PROTECTED_TAGS =
            Collections.unmodifiableSet(
                    new LinkedHashSet<>(
                            Arrays.asList("code", "pre", "script", "style", "kbd", "samp", "var")));

    private final Set<String> protectedTags;
    private final int maxChunkChars;
    private final FailurePolicy failurePolicy;
    private final boolean maskUrls;
    private final boolean maskPlaceholders;
    private final boolean maskPaths;
    private final String placeholderMarkerStart;
    private final String placeholderMarkerEnd;
    private final TranslationTimingListener timingListener;

    private HtmlTranslationOptions(Builder builder) {
        this.protectedTags =
                Collections.unmodifiableSet(new LinkedHashSet<>(builder.protectedTags));
        this.maxChunkChars = builder.maxChunkChars;
        this.failurePolicy = builder.failurePolicy;
        this.maskUrls = builder.maskUrls;
        this.maskPlaceholders = builder.maskPlaceholders;
        this.maskPaths = builder.maskPaths;
        this.placeholderMarkerStart = builder.placeholderMarkerStart;
        this.placeholderMarkerEnd = builder.placeholderMarkerEnd;
        this.timingListener = builder.timingListener;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public Set<String> getProtectedTags() {
        return protectedTags;
    }

    public int getMaxChunkChars() {
        return maxChunkChars;
    }

    @NonNull
    public FailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    public boolean isMaskUrls() {
        return maskUrls;
    }

    public boolean isMaskPlaceholders() {
        return maskPlaceholders;
    }

    public boolean isMaskPaths() {
        return maskPaths;
    }

    @NonNull
    public String getPlaceholderMarkerStart() {
        return placeholderMarkerStart;
    }

    @NonNull
    public String getPlaceholderMarkerEnd() {
        return placeholderMarkerEnd;
    }

    @Nullable
    public TranslationTimingListener getTimingListener() {
        return timingListener;
    }

    public static final class Builder {
        private Set<String> protectedTags = new LinkedHashSet<>(DEFAULT_PROTECTED_TAGS);
        private int maxChunkChars = 3000;
        private FailurePolicy failurePolicy = FailurePolicy.BEST_EFFORT;
        private boolean maskUrls = true;
        private boolean maskPlaceholders = true;
        private boolean maskPaths = true;
        private String placeholderMarkerStart = "[{[";
        private String placeholderMarkerEnd = "]}]";
        private TranslationTimingListener timingListener;

        private Builder() {}

        @NonNull
        public Builder setProtectedTags(@NonNull Set<String> protectedTags) {
            this.protectedTags = new LinkedHashSet<>(protectedTags);
            return this;
        }

        @NonNull
        public Builder setMaxChunkChars(int maxChunkChars) {
            this.maxChunkChars = Math.max(1, maxChunkChars);
            return this;
        }

        @NonNull
        public Builder setFailurePolicy(@NonNull FailurePolicy failurePolicy) {
            this.failurePolicy = failurePolicy;
            return this;
        }

        @NonNull
        public Builder setMaskUrls(boolean maskUrls) {
            this.maskUrls = maskUrls;
            return this;
        }

        @NonNull
        public Builder setMaskPlaceholders(boolean maskPlaceholders) {
            this.maskPlaceholders = maskPlaceholders;
            return this;
        }

        @NonNull
        public Builder setMaskPaths(boolean maskPaths) {
            this.maskPaths = maskPaths;
            return this;
        }

        @NonNull
        public Builder setPlaceholderMarkerStart(@NonNull String placeholderMarkerStart) {
            if (placeholderMarkerStart.trim().isEmpty()) {
                throw new IllegalArgumentException("placeholderMarkerStart cannot be blank");
            }
            this.placeholderMarkerStart = placeholderMarkerStart;
            return this;
        }

        @NonNull
        public Builder setPlaceholderMarkerEnd(@NonNull String placeholderMarkerEnd) {
            if (placeholderMarkerEnd.trim().isEmpty()) {
                throw new IllegalArgumentException("placeholderMarkerEnd cannot be blank");
            }
            this.placeholderMarkerEnd = placeholderMarkerEnd;
            return this;
        }

        @NonNull
        public Builder setTimingListener(@Nullable TranslationTimingListener timingListener) {
            this.timingListener = timingListener;
            return this;
        }

        @NonNull
        public HtmlTranslationOptions build() {
            return new HtmlTranslationOptions(this);
        }
    }
}
