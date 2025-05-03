// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.pool;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class PoolBenchmark {

    private final Pool<TestResource> pool = new Pool<>(1, 2, new TestResourceFactory(), new TestResourceValidator());

    @Benchmark
    public boolean borrowAndRelease() throws InterruptedException {
        var resource = pool.borrow();
        try {
            resource.work();
        } finally {
            return pool.release(resource);
        }
    }
}
