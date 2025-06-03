// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.appendonly;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmarks for measuring the performance of AppendOnlyKeyValueArray
 * under concurrent addition operations from multiple threads.
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class ConcurrentAddBenchmark {

    // The store to benchmark
    private AppendOnlyKeyValueArray<String, Integer> store;

    // Number of elements to add in total
    @Param({"1000000"})
    private int totalElements;

    // Number of threads to use
    @Param({"1", "2", "4", "8", "16"})
    private int threadCount;

    // Chunk size for the store
    @Param({"1024", "4096"})
    private int chunkSize;

    // Whether threads use the same key (contention) or different keys
    @Param({"true", "false"})
    private boolean useSharedKey;

    // Shared key for high-contention scenario
    private final String SHARED_KEY = "sharedKey";

    // Thread-local key prefix for low-contention scenario
    private final String KEY_PREFIX = "thread-";

    // Thread ID generator
    private final AtomicInteger threadIdGenerator = new AtomicInteger(0);

    // Threads to use in the benchmark
    private Thread[] threads;

    // CountDownLatch for coordinating thread start
    private CountDownLatch startLatch;

    // CountDownLatch for waiting for all threads to finish
    private CountDownLatch endLatch;

    // Elements to add per thread
    private int elementsPerThread;

    @Setup
    public void setup() {
        // Create store with specified chunk size
        store = new AppendOnlyKeyValueArray<>(chunkSize);

        // Calculate elements per thread
        elementsPerThread = totalElements / threadCount;

        // Create threads
        threads = new Thread[threadCount];
        startLatch = new CountDownLatch(1);
        endLatch = new CountDownLatch(threadCount);

        // Initialize threads
        for (int i = 0; i < threadCount; i++) {
            final int threadId = threadIdGenerator.getAndIncrement();
            threads[i] = new Thread(() -> {
                try {
                    // Wait for signal to start
                    startLatch.await();

                    // Use shared key or thread-specific key based on contention parameter
                    String key = useSharedKey ? SHARED_KEY : KEY_PREFIX + threadId;

                    // Add elements
                    for (int j = 0; j < elementsPerThread; j++) {
                        store.add(key, j);
                    }

                    // Signal completion
                    endLatch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }
    }

    @TearDown
    public void tearDown() throws InterruptedException {
        // Ensure all threads are done
        for (Thread thread : threads) {
            thread.join(1000);
        }
    }

    /**
     * Benchmark for concurrent add operations.
     */
    @Benchmark
    public void concurrentAdd(Blackhole blackhole) throws InterruptedException {
        // Signal threads to start
        startLatch.countDown();

        // Wait for all threads to finish
        endLatch.await();

        // Consume the store to prevent dead code elimination
        blackhole.consume(store);
    }

    /**
     * Benchmark comparing batch add vs. individual adds
     */
    @State(Scope.Benchmark)
    public static class BatchState {
        // The store to benchmark
        private AppendOnlyKeyValueArray<String, Integer> store;

        // Number of elements to add
        @Param({"10000", "100000", "1000000"})
        private int elementCount;

        // Chunk size for the store
        @Param({"1024", "4096"})
        private int chunkSize;

        // Test key
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
    }

    /**
     * Benchmark for individual add operations.
     */
    @Benchmark
    public void individualAdd(BatchState state, Blackhole blackhole) {
        AppendOnlyKeyValueArray<String, Integer> localStore = new AppendOnlyKeyValueArray<>(state.chunkSize);

        for (int i = 0; i < state.elementCount; i++) {
            localStore.add(state.TEST_KEY, i);
        }

        blackhole.consume(localStore);
    }

    /**
     * Benchmark for batch add operation.
     */
    @Benchmark
    public void batchAdd(BatchState state, Blackhole blackhole) {
        AppendOnlyKeyValueArray<String, Integer> localStore = new AppendOnlyKeyValueArray<>(state.chunkSize);

        localStore.addAll(state.TEST_KEY, state.valuesToAdd);

        blackhole.consume(localStore);
    }

    /**
     * Run the benchmarks.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ConcurrentAddBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
