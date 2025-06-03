// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.appendonly;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH benchmarks for the AppendOnlyKeyValueArray implementation.
 * These benchmarks measure:
 * 1. Single-threaded add performance
 * 2. Different traversal methods (list vs. stream vs. iterator)
 * 3. Multi-threaded add performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class ArrayStoreBenchmark {

    // The store to benchmark
    private AppendOnlyKeyValueArray<String, Integer> store;

    // Number of elements to add per key
    @Param({"10000", "100000", "1000000"})
    private int elementCount;

    // Chunk size for the store
    @Param({"1024", "4096"})
    private int chunkSize;

    // Test key for single-threaded operations
    private final String TEST_KEY = "testKey";

    // Precomputed list of values for batch add
    private List<Integer> valuesToAdd;

    @Setup
    public void setup() {
        // Create store with specified chunk size
        store = new AppendOnlyKeyValueArray<>(chunkSize);

        // Prepare values list
        valuesToAdd = new ArrayList<>(elementCount);
        for (int i = 0; i < elementCount; i++) {
            valuesToAdd.add(i);
        }
    }

    /**
     * Benchmark for single-threaded add operation.
     */
    @Benchmark
    public void singleThreadedAdd(Blackhole blackhole) {
        AppendOnlyKeyValueArray<String, Integer> localStore = new AppendOnlyKeyValueArray<>(chunkSize);

        for (int i = 0; i < elementCount; i++) {
            localStore.add(TEST_KEY, i);
        }

        blackhole.consume(localStore);
    }

    /**
     * Benchmark for addAll operation.
     */
    @Benchmark
    public void batchAdd(Blackhole blackhole) {
        AppendOnlyKeyValueArray<String, Integer> localStore = new AppendOnlyKeyValueArray<>(chunkSize);

        localStore.addAll(TEST_KEY, valuesToAdd);

        blackhole.consume(localStore);
    }

    /**
     * Setup for traversal benchmarks.
     */
    @State(Scope.Group)
    public static class TraversalState {
        // The store to benchmark
        private AppendOnlyKeyValueArray<String, Integer> store;

        // Number of elements to add
        @Param({"1000000"})
        private int elementCount;

        // Chunk size for the store
        @Param({"1024"})
        private int chunkSize;

        // Test key
        private final String TEST_KEY = "traversalKey";

        @Setup(Level.Trial)
        public void setup() {
            // Create store with specified chunk size
            store = new AppendOnlyKeyValueArray<>(chunkSize);

            // Add elements
            for (int i = 0; i < elementCount; i++) {
                store.add(TEST_KEY, i);
            }
        }
    }

    /**
     * Benchmark for list-based traversal.
     */
    @Benchmark
    @Group("traversal")
    public void listTraversal(TraversalState state, Blackhole blackhole) {
        int sum = 0;
        for (Integer value : state.store.get(state.TEST_KEY)) {
            sum += value;
        }
        blackhole.consume(sum);
    }

    /**
     * Benchmark for stream-based traversal.
     */
    @Benchmark
    @Group("traversal")
    public void streamTraversal(TraversalState state, Blackhole blackhole) {
        int sum = state.store.stream(state.TEST_KEY).mapToInt(Integer::intValue).sum();
        blackhole.consume(sum);
    }

    /**
     * Benchmark for iterator-based traversal.
     */
    @Benchmark
    @Group("traversal")
    public void iteratorTraversal(TraversalState state, Blackhole blackhole) {
        int sum = 0;
        Iterator<Integer> it = state.store.iterator(state.TEST_KEY);
        while (it.hasNext()) {
            sum += it.next();
        }
        blackhole.consume(sum);
    }

    /**
     * State for multi-threaded benchmarks.
     */
    @State(Scope.Group)
    public static class ThreadedState {
        // The store to benchmark
        private AppendOnlyKeyValueArray<String, Integer> store;

        // Thread-local key to avoid contention between threads
        private final ThreadLocal<String> threadKey =
                ThreadLocal.withInitial(() -> "thread-" + Thread.currentThread().getId());

        // Number of elements to add per thread
        @Param({"100000"})
        private int elementsPerThread;

        // Chunk size for the store
        @Param({"1024", "4096"})
        private int chunkSize;

        @Setup(Level.Trial)
        public void setup() {
            // Create store with specified chunk size
            store = new AppendOnlyKeyValueArray<>(chunkSize);
        }
    }

    /**
     * Benchmark for multi-threaded add operation.
     */
    @Benchmark
    @Group("multithreaded")
    @GroupThreads(8) // Run with 8 threads
    public void multithreadedAdd(ThreadedState state, Blackhole blackhole) {
        String key = state.threadKey.get();

        for (int i = 0; i < state.elementsPerThread; i++) {
            state.store.add(key, i);
        }

        blackhole.consume(state.store);
    }

    /**
     * Run the benchmarks.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ArrayStoreBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
