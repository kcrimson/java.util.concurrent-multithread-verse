// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PoolTest {

    private static class TestResource implements AutoCloseable {
        private final int id;
        private boolean closed = false;

        TestResource(int id) {
            this.id = id;
        }

        int getId() {
            return id;
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void createPoolWithSpecifiedSize() {
        // given
        var minPoolSize = 1;
        var maxPoolSize = 5;
        var counter = new AtomicInteger();

        // when
        var pool = new Pool<>(minPoolSize, maxPoolSize, () -> new TestResource(counter.incrementAndGet()), obj -> true);

        // then
        assertThat(pool.availableCount()).isEqualTo(minPoolSize);
        assertThat(counter).hasValue(minPoolSize);
    }

    @Test
    void acquireAndReleaseResource() throws Exception {
        // given
        var pool = new Pool<>(1, 1, () -> new TestResource(1), obj -> true);

        // when
        var resource = pool.borrow();

        // then
        assertThat(resource).isNotNull();
        assertThat(resource.getId()).isEqualTo(1);

        // when
        pool.release(resource);

        // then
        var resourceAgain = pool.borrow();
        assertThat(resourceAgain).isSameAs(resource);
    }

    @Test
    void blockWhenPoolIsExhausted() throws Exception {
        // given
        var poolSize = 1;
        var pool = new Pool<>(1, poolSize, () -> new TestResource(1), obj -> true);
        var resource = pool.borrow();

        // when
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> pool.borrow());

            // then
            // Verify that the future doesn't complete (blocks) for a short time
            assertThat(future.isDone()).isFalse();

            // Release the resource to unblock the second thread
            pool.release(resource);

            // Now the future should complete within a reasonable time
            var borrowedResource = future.get(1, TimeUnit.SECONDS);
            assertThat(borrowedResource).isNotNull();
        }
    }

    @Test
    void multipleThreadsAcquiringResources() throws Exception {
        // given
        var poolSize = 5;
        var threadCount = 10;
        var pool = new Pool<>(poolSize, poolSize, () -> new TestResource(1), obj -> true);

        // when
        try (var executor = Executors.newFixedThreadPool(threadCount)) {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        var resource = pool.borrow();
                        // Simulate some work
                        Thread.sleep(100);
                        pool.release(resource);
                        return resource.getId();
                    }
                }));
            }

            // then
            for (var future : futures) {
                assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo(1);
            }
        }
    }

    @Test
    void closeAllResourcesWhenPoolIsClosed() throws Exception {
        // given
        var minPoolSize = 5;
        var maxPoolSize = 5;
        List<TestResource> createdResources = new ArrayList<>();

        Pool<TestResource> pool = new Pool<>(
                minPoolSize,
                maxPoolSize,
                () -> {
                    TestResource resource = new TestResource(createdResources.size() + 1);
                    createdResources.add(resource);
                    return resource;
                },
                resource -> true);

        // when
        pool.close();

        // then
        assertThat(createdResources).hasSize(maxPoolSize);
        for (TestResource resource : createdResources) {
            assertThat(resource.isClosed()).isTrue();
        }
    }

    @Test
    void throwExceptionWhenAcquiringFromClosedPool() throws Exception {
        // given
        Pool<TestResource> pool = new Pool<>(1, 1, () -> new TestResource(1), obj -> true);
        pool.close();

        // when/then
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(pool::borrow)
                .withMessageContaining("closed");
    }
}
