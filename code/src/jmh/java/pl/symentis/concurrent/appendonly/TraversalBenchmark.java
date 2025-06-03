// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.appendonly;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Focused benchmark for comparing different traversal methods
 * in AppendOnlyKeyValueArray.
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class TraversalBenchmark {

    // The store to benchmark
    private AppendOnlyKeyValueArray<String, Integer> store;

    // Number of elements to add
    @Param({"10000", "100000", "1000000"})
    private int elementCount;

    // Chunk size for the store
    @Param({"1024", "4096"})
    private int chunkSize;

    // Test key
    private final String TEST_KEY = "traversalKey";

    @Setup
    public void setup() {
        // Create store with specified chunk size
        store = new AppendOnlyKeyValueArray<>(chunkSize);

        // Add elements
        for (int i = 0; i < elementCount; i++) {
            store.add(TEST_KEY, i);
        }

        // Force GC to minimize interference
        System.gc();
    }

    /**
     * Benchmark for list-based traversal.
     * This creates a full list in memory before traversing.
     */
    @Benchmark
    public long listTraversal() {
        long sum = 0;
        for (Integer value : store.get(TEST_KEY)) {
            sum += value;
        }
        return sum;
    }

    /**
     * Benchmark for stream-based traversal.
     * This avoids creating a full list in memory.
     */
    @Benchmark
    public long streamTraversal() {
        return store.stream(TEST_KEY).mapToLong(Integer::longValue).sum();
    }

    /**
     * Benchmark for parallel stream-based traversal.
     * This tests whether parallelization helps for this workload.
     */
    @Benchmark
    public long parallelStreamTraversal() {
        return store.stream(TEST_KEY).parallel().mapToLong(Integer::longValue).sum();
    }

    /**
     * Benchmark for iterator-based traversal.
     * This is the most memory-efficient approach.
     */
    @Benchmark
    public long iteratorTraversal() {
        long sum = 0;
        Iterator<Integer> it = store.iterator(TEST_KEY);
        while (it.hasNext()) {
            sum += it.next();
        }
        return sum;
    }

    /**
     * Run the benchmarks.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TraversalBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
