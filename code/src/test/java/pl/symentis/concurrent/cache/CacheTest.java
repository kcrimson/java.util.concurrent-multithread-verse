// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CacheTest {

    @Test
    void invalidateLeastRecentlyUsedEntryWhenCacheIsFull() {
        // given
        var cache = new Cache<String, String>(1, key -> null);
        // when
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        // then
        assertThat(cache.get("key1")).isNull();
        assertThat(cache.get("key2")).isEqualTo("value2");
    }

    @Test
    void updateCacheValueUsageWhenKeyIsAlreadyPresent() {
        // given
        var cache = new Cache<String, String>(2, key -> null);
        // when
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        // key2 is become least recently used
        cache.get("key1");
        // key3 should replace key2
        cache.put("key3", "value3");
        // then
        assertThat(cache.get("key1")).isEqualTo("value1");
        assertThat(cache.get("key2")).isNull();
        assertThat(cache.get("key3")).isEqualTo("value3");
    }
}
