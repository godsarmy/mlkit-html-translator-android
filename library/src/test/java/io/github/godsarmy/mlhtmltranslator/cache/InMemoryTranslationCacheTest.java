package io.github.godsarmy.mlhtmltranslator.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class InMemoryTranslationCacheTest {

    @Test
    public void cacheEvictsLeastRecentlyUsedWhenCapacityExceeded() {
        InMemoryTranslationCache cache = new InMemoryTranslationCache(2);

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        assertEquals("v1", cache.get("k1")); // make k1 most-recent
        cache.put("k3", "v3");

        assertNull(cache.get("k2"));
        assertEquals("v1", cache.get("k1"));
        assertEquals("v3", cache.get("k3"));
    }
}
