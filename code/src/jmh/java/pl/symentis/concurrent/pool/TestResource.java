// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.pool;

final class TestResource implements AutoCloseable {
    final int id;
    volatile boolean valid = true;

    public TestResource(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void invalidate() {
        valid = false;
    }

    public void work() throws InterruptedException {
        Thread.sleep(1);
    }

    @Override
    public void close() {
        // No-op for test
    }
}
