package io.github.godsarmy.mlhtmltranslator.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InMemoryTranslationCache implements TranslationCache {

    private final int maxEntries;
    private final LinkedHashMap<String, String> lru;

    public InMemoryTranslationCache(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.lru =
                new LinkedHashMap<String, String>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                        return size() > InMemoryTranslationCache.this.maxEntries;
                    }
                };
    }

    @Nullable
    @Override
    public synchronized String get(@NonNull String key) {
        return lru.get(key);
    }

    @Override
    public synchronized void put(@NonNull String key, @NonNull String translatedHtml) {
        lru.put(key, translatedHtml);
    }

    @Override
    public synchronized void clear() {
        lru.clear();
    }
}
