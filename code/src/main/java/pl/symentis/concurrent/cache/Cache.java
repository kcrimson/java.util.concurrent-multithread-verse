// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.cache;

import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * A thread-safe LRU cache implementation using a single NavigableMap to track
 * both values and access counts.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of values maintained by this cache
 */
public class Cache<K, V> {

    private final int capacity;
    private final Function<K, V> computeFunction;
    private final Map<K, V> valueMap;
    private final NavigableMap<Entry<K>, K> accessMap;
    private final AtomicLong accessCounter = new AtomicLong(0);

    /**
     * Creates a new cache with the specified capacity and compute function.
     *
     * @param capacity the maximum number of entries in the cache
     * @param computeFunction the function to compute a value if it's not present in the cache
     */
    public Cache(int capacity, Function<K, V> computeFunction) {
        this.capacity = capacity;
        this.computeFunction = computeFunction;
        this.valueMap = new ConcurrentHashMap<>(capacity);
        this.accessMap = new ConcurrentSkipListMap<>();
    }

    /**
     * Gets a value from the cache, computing it if necessary.
     *
     * @param key the key whose associated value is to be returned
     * @return the value associated with the key
     */
    public V get(K key) {
        // Try to get the value from the cache
        V value = valueMap.get(key);

        if (value == null) {
            // Value not in cache, compute it
            value = computeFunction.apply(key);

            // Put the value in the cache
            V existingValue = valueMap.putIfAbsent(key, value);
            if (existingValue != null) {
                // Another thread computed the value first
                value = existingValue;
            } else {
                // We added a new entry, check if we need to evict
                evictIfNecessary();
            }
        }

        // Update access information
        updateAccess(key);

        return value;
    }

    /**
     * Updates the access information for a key.
     *
     * @param key the key whose access information is to be updated
     */
    private void updateAccess(K key) {
        // Remove old entry if it exists
        accessMap.values().remove(key);

        // Create new entry with current access count
        Entry<K> entry = new Entry<>(key, accessCounter.incrementAndGet());

        // Add new entry
        accessMap.put(entry, key);
    }

    /**
     * Evicts the least recently used entry if the cache is at capacity.
     */
    private void evictIfNecessary() {
        while (valueMap.size() > capacity) {
            // Get the entry with the lowest access count (least recently used)
            Map.Entry<Entry<K>, K> lruEntry = accessMap.firstEntry();
            if (lruEntry != null) {
                K keyToRemove = lruEntry.getValue();

                // Remove from both maps
                valueMap.remove(keyToRemove);
                accessMap.remove(lruEntry.getKey());
            } else {
                // No entries to remove (should not happen)
                break;
            }
        }
    }

    /**
     * Returns the number of entries in the cache.
     *
     * @return the number of entries
     */
    public int size() {
        return valueMap.size();
    }

    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        valueMap.clear();
        accessMap.clear();
    }

    /**
     * Entry class that combines a key with its access counter.
     * Entries are comparable by access count to determine LRU order.
     *
     * @param <K> the type of the key
     */
    private static class Entry<K> implements Comparable<Entry<K>> {
        private final K key;
        private final long accessCount;

        Entry(K key, long accessCount) {
            this.key = key;
            this.accessCount = accessCount;
        }

        @Override
        public int compareTo(Entry<K> other) {
            // Compare by access count (for LRU ordering)
            return Long.compare(this.accessCount, other.accessCount);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            Entry<?> entry = (Entry<?>) obj;

            if (accessCount != entry.accessCount) return false;
            return key != null ? key.equals(entry.key) : entry.key == null;
        }

        @Override
        public int hashCode() {
            int result = key != null ? key.hashCode() : 0;
            result = 31 * result + (int) (accessCount ^ (accessCount >>> 32));
            return result;
        }
    }
}
