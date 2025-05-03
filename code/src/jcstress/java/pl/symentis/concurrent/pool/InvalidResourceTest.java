// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.pool;

import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/**
 * This test verifies that concurrent borrow and release operations
 * maintain the correct pool state.
 */
@JCStressTest
@Outcome(
        id = "2, 1",
        expect = Expect.ACCEPTABLE,
        desc = "Resource was borrowed and released (some were invalidated), pool has min size")
@Outcome(
        id = "2, 2",
        expect = Expect.ACCEPTABLE,
        desc = "Resource was borrowed and released (some were invalidated), pool has max size")
@State
public class InvalidResourceTest {

    // The pool instance to test
    private final Pool<TestResource> pool = new Pool<>(1, 2, new TestResourceFactory(), new TestResourceValidator());

    // Counter to track successful operations
    private final AtomicInteger successCounter = new AtomicInteger(0);

    @Actor
    public void actor1() {
        try {
            var resource = pool.borrow();
            try {
                resource.work();
            } finally {
                if (pool.release(resource)) {
                    successCounter.incrementAndGet();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Actor
    public void actor2() {
        try {
            var resource = pool.borrow();
            try {
                resource.work();
            } finally {
                if (pool.release(resource)) {
                    successCounter.incrementAndGet();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Actor
    public void actor3() {
        try {
            var resource = pool.borrow();
            try {
                resource.work();
                resource.invalidate();
            } finally {
                if (pool.release(resource)) {
                    successCounter.incrementAndGet();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Actor
    public void actor4() {
        try {
            var resource = pool.borrow();
            try {
                resource.work();
                resource.invalidate();
            } finally {
                if (pool.release(resource)) {
                    successCounter.incrementAndGet();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Arbiter
    public void arbiter(II_Result result) {
        result.r1 = successCounter.get();
        result.r2 = pool.availableCount();
    }
}
