// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.pool;

import java.util.concurrent.atomic.AtomicInteger;

final class TestResourceFactory implements ObjectFactory<TestResource> {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public TestResource create() {
        return new TestResource(counter.incrementAndGet());
    }
}
