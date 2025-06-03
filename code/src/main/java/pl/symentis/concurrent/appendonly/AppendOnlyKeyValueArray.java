// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.appendonly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A highly optimized, thread-safe, append-only key-value store using:
 * - Two-dimensional object arrays for efficient storage
 * - AtomicLong for lock-free indexing
 * - System.arraycopy for fast array growth
 * - Zero locking for reads once additions are complete
 * - Memory-efficient iteration via iterators and streams
 *
 * @param <K> The type of keys
 * @param <V> The type of values
 */
public class AppendOnlyKeyValueArray<K, V> {

    // Default chunk size (number of elements in each array chunk)
    private static final int DEFAULT_CHUNK_SIZE = 1024;

    // Main storage - ConcurrentHashMap for thread-safe key access
    private final ConcurrentHashMap<K, ChunkedArray<V>> map;

    // Total size counter
    private final AtomicLong totalSize = new AtomicLong(0);

    // Chunk size for this store
    private final int chunkSize;

    /**
     * Creates a new store with default chunk size.
     */
    public AppendOnlyKeyValueArray() {
        this(DEFAULT_CHUNK_SIZE);
    }

    /**
     * Creates a new store with the specified chunk size.
     *
     * @param chunkSize the size of each array chunk
     */
    public AppendOnlyKeyValueArray(int chunkSize) {
        this.map = new ConcurrentHashMap<>();
        this.chunkSize = chunkSize;
    }

    /**
     * Adds a value to the list associated with the specified key.
     * Thread-safe with no explicit locking.
     *
     * @param key the key
     * @param value the value to add
     */
    public void add(K key, V value) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }

        // Get or create the chunked array for this key
        ChunkedArray<V> array = map.computeIfAbsent(key, k -> new ChunkedArray<>(chunkSize));

        // Add value (thread-safe)
        array.add(value);

        // Increment total size
        totalSize.incrementAndGet();
    }

    /**
     * Adds multiple values to the list associated with the specified key.
     * More efficient than adding values individually.
     *
     * @param key the key
     * @param values the values to add
     */
    public void addAll(K key, List<V> values) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }
        if (values == null || values.isEmpty()) {
            return;
        }

        // Get or create the chunked array for this key
        ChunkedArray<V> array = map.computeIfAbsent(key, k -> new ChunkedArray<>(chunkSize));

        // Add all values (thread-safe)
        int count = array.addAll(values);

        // Update total size
        totalSize.addAndGet(count);
    }

    /**
     * Gets all values associated with the specified key as a list.
     * This operation is completely lock-free but creates a new list.
     *
     * @param key the key
     * @return an unmodifiable list of values, or empty list if key not found
     */
    public List<V> get(K key) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }

        ChunkedArray<V> array = map.get(key);
        return array != null ? array.toList() : Collections.emptyList();
    }

    /**
     * Returns an iterator over the values associated with the specified key.
     * This is memory-efficient as it doesn't create a list.
     *
     * @param key the key
     * @return an iterator over the values
     */
    public Iterator<V> iterator(K key) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }

        ChunkedArray<V> array = map.get(key);
        return array != null ? array.iterator() : Collections.emptyIterator();
    }

    /**
     * Returns a stream of values associated with the specified key.
     * This is memory-efficient as it doesn't create a list upfront.
     *
     * @param key the key
     * @return a stream of values
     */
    public Stream<V> stream(K key) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }

        ChunkedArray<V> array = map.get(key);
        return array != null ? array.stream() : Stream.empty();
    }

    /**
     * Returns all keys in this store.
     *
     * @return a set of all keys
     */
    public Set<K> keySet() {
        return map.keySet();
    }

    /**
     * Applies the given function to each key-values pair.
     *
     * @param action the function to apply
     */
    public void forEach(BiConsumer<K, Stream<V>> action) {
        map.forEach((key, array) -> action.accept(key, array.stream()));
    }

    /**
     * Returns the total number of values in this store.
     *
     * @return the total number of values
     */
    public int size() {
        return (int) totalSize.get();
    }

    /**
     * Returns the number of keys in this store.
     *
     * @return the number of keys
     */
    public int keyCount() {
        return map.size();
    }

    /**
     * A thread-safe, append-only array implementation using chunks
     * for efficient storage and growth.
     *
     * @param <T> the type of elements
     */
    private static class ChunkedArray<T> {
        // 2D array: chunks[chunkIndex][elementIndex]
        private volatile Object[][] chunks;

        // Single atomic for storing the next position
        private final AtomicLong nextIndex = new AtomicLong(0);

        // Chunk size (number of elements per chunk)
        private final int chunkSize;

        // Number of chunks
        private volatile int chunkCount = 1;

        /**
         * Creates a new chunked array with the specified chunk size.
         *
         * @param chunkSize the size of each chunk
         */
        ChunkedArray(int chunkSize) {
            this.chunkSize = chunkSize;
            // Start with one chunk
            this.chunks = new Object[1][chunkSize];
        }

        /**
         * Adds an element to this array.
         * Thread-safe without explicit locking.
         *
         * @param value the value to add
         */
        public void add(T value) {
            // Get next available index atomically
            long currentIndex = nextIndex.getAndIncrement();

            // Calculate chunk and position
            int chunkIndex = (int) (currentIndex / chunkSize);
            int position = (int) (currentIndex % chunkSize);

            // Ensure we have enough chunks
            ensureCapacity(chunkIndex);

            // Store the value
            chunks[chunkIndex][position] = value;
        }

        /**
         * Adds multiple elements to this array.
         * More efficient than adding elements individually.
         *
         * @param values the values to add
         * @return the number of values added
         */
        public int addAll(List<T> values) {
            if (values.isEmpty()) {
                return 0;
            }

            int size = values.size();

            // Reserve space for all values at once by advancing the index
            long startIndex = nextIndex.getAndAdd(size);

            // Calculate the starting chunk and position
            int startChunk = (int) (startIndex / chunkSize);
            int startPos = (int) (startIndex % chunkSize);

            // Calculate the ending chunk
            int endChunk = (int) ((startIndex + size - 1) / chunkSize);

            // Ensure we have enough chunks
            ensureCapacity(endChunk);

            // Add values to the appropriate chunks
            int valuesAdded = 0;
            int valueIndex = 0;

            for (int chunk = startChunk; chunk <= endChunk && valueIndex < size; chunk++) {
                // Calculate start position in this chunk
                int position = (chunk == startChunk) ? startPos : 0;

                // Calculate how many values to add to this chunk
                int valuesToAdd = Math.min(chunkSize - position, size - valueIndex);

                // Add values to this chunk
                for (int i = 0; i < valuesToAdd; i++) {
                    chunks[chunk][position + i] = values.get(valueIndex++);
                    valuesAdded++;
                }
            }

            return valuesAdded;
        }

        /**
         * Ensures that the chunks array has capacity for the specified chunk index.
         * Thread-safe using synchronization.
         *
         * @param requiredChunkIndex the required chunk index
         */
        private void ensureCapacity(int requiredChunkIndex) {
            // Fast check without locking
            if (requiredChunkIndex < chunkCount) {
                return;
            }

            // Need to grow the chunks array
            synchronized (this) {
                // Double-check after acquiring lock
                if (requiredChunkIndex < chunkCount) {
                    return;
                }

                // Calculate new capacity (at least requiredChunkIndex + 1)
                int newCapacity = Math.max(chunkCount * 2, requiredChunkIndex + 1);

                // Create new chunks array
                Object[][] newChunks = new Object[newCapacity][];

                // Copy existing chunks
                System.arraycopy(chunks, 0, newChunks, 0, chunkCount);

                // Initialize new chunks
                for (int i = chunkCount; i < newCapacity; i++) {
                    newChunks[i] = new Object[chunkSize];
                }

                // Update chunks and chunkCount
                chunks = newChunks;
                chunkCount = newCapacity;
            }
        }

        /**
         * Returns an iterator over the elements in this array.
         * Memory-efficient as it doesn't create a list.
         *
         * @return an iterator over the elements
         */
        @SuppressWarnings("unchecked")
        public Iterator<T> iterator() {
            return new Iterator<>() {
                // Current index in the array
                private long currentIndex = 0;

                // Total elements in the array
                private final long totalElements = nextIndex.get();

                @Override
                public boolean hasNext() {
                    return currentIndex < totalElements;
                }

                @Override
                public T next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }

                    // Calculate chunk and position
                    int chunkIndex = (int) (currentIndex / chunkSize);
                    int position = (int) (currentIndex % chunkSize);

                    // Increment index for next call
                    currentIndex++;

                    // Return the element
                    return (T) chunks[chunkIndex][position];
                }
            };
        }

        /**
         * Returns a stream of the elements in this array.
         * Memory-efficient as it's based on the iterator.
         *
         * @return a stream of elements
         */
        public Stream<T> stream() {
            return StreamSupport.stream(
                    Spliterators.spliterator(
                            iterator(), size(), Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL),
                    false // not parallel
                    );
        }

        /**
         * Converts this chunked array to a list.
         * This operation creates a new list with all elements.
         * Consider using iterator() or stream() for memory efficiency.
         *
         * @return an unmodifiable list of values
         */
        @SuppressWarnings("unchecked")
        public List<T> toList() {
            // Get current size
            long size = nextIndex.get();
            if (size == 0) {
                return Collections.emptyList();
            }

            // Create result list
            List<T> result = new ArrayList<>((int) size);

            // Use the iterator to collect elements
            iterator().forEachRemaining(result::add);

            return Collections.unmodifiableList(result);
        }

        /**
         * Returns the number of elements in this array.
         *
         * @return the number of elements
         */
        public int size() {
            return (int) nextIndex.get();
        }
    }

    /**
     * Demo showing the performance of this implementation.
     */
    public static void main(String[] args) throws Exception {
        // Create a store with 1024 elements per chunk
        AppendOnlyKeyValueArray<String, Integer> store = new AppendOnlyKeyValueArray<>(1024);

        // Number of operations
        final int numOps = 1_000_000;

        System.out.println("Testing ArrayBasedAppendOnlyStore with " + numOps + " operations");

        // Add values
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            String key = "key" + i;
            for (int j = 0; j < numOps / 5; j++) {
                store.add(key, j);
            }
        }
        long end = System.nanoTime();

        System.out.println("Single-threaded add time: " + ((end - start) / 1_000_000) + " ms");
        System.out.println("Throughput: " + (numOps * 1_000_000_000L / (end - start)) + " ops/s");

        // Compare list vs stream performance
        System.out.println("\nComparing memory usage and performance:");

        // Test get() performance (creates a list)
        start = System.nanoTime();
        int sum = 0;
        for (Integer value : store.get("key0")) {
            sum += value;
        }
        end = System.nanoTime();
        System.out.println("List-based traversal time: " + ((end - start) / 1_000_000) + " ms");
        System.out.println("Sum: " + sum); // Prevent optimization

        // Test stream() performance (no list creation)
        start = System.nanoTime();
        sum = store.stream("key0").mapToInt(Integer::intValue).sum();
        end = System.nanoTime();
        System.out.println("Stream-based traversal time: " + ((end - start) / 1_000_000) + " ms");
        System.out.println("Sum: " + sum); // Prevent optimization

        // Test iterator() performance (no list creation)
        start = System.nanoTime();
        sum = 0;
        Iterator<Integer> it = store.iterator("key0");
        while (it.hasNext()) {
            sum += it.next();
        }
        end = System.nanoTime();
        System.out.println("Iterator-based traversal time: " + ((end - start) / 1_000_000) + " ms");
        System.out.println("Sum: " + sum); // Prevent optimization

        // Test concurrent writes
        final int numThreads = 10;
        Thread[] threads = new Thread[numThreads];

        start = System.nanoTime();
        for (int t = 0; t < numThreads; t++) {
            final int threadNum = t;
            threads[t] = new Thread(() -> {
                String key = "thread-" + threadNum;
                for (int i = 0; i < numOps / numThreads; i++) {
                    store.add(key, i);
                }
            });
            threads[t].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        end = System.nanoTime();

        System.out.println("\nMulti-threaded performance:");
        System.out.println("Add time: " + ((end - start) / 1_000_000) + " ms");
        System.out.println("Throughput: " + (numOps * 1_000_000_000L / (end - start)) + " ops/s");
        System.out.println("Total size: " + store.size());
        System.out.println("Key count: " + store.keyCount());
    }
}
