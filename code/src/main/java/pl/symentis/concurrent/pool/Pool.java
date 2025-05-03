// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.pool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * A thread-safe object pool implementation with minimum and maximum capacity.
 * Objects are managed using a BlockingQueue and Semaphore to control concurrent access.
 * The pool auto-creates objects up to maximum capacity and maintains minimum number
 * of valid objects. Invalid objects are replaced to maintain minimum capacity.
 *
 * @param <T> the type of objects stored in the pool
 */
public class Pool<T extends AutoCloseable> {

    private final BlockingQueue<T> resources;
    private final Semaphore semaphore;
    private final ObjectFactory<T> factory;
    private final ObjectValidator<T> validator;
    private final int minCapacity;
    private final int maxCapacity;
    private volatile boolean closed;

    /**
     * Creates a new object pool with the specified maxCapacity and factory method.
     *
     * @param minCapacity the minimum number of objects in the pool
     * @param maxCapacity the maximum number of objects in the pool
     * @param factory     the supplier function to create new objects
     * @param validator   the validator function to check if an object is valid
     */
    public Pool(int minCapacity, int maxCapacity, ObjectFactory<T> factory, ObjectValidator<T> validator) {
        if (maxCapacity < minCapacity)
            throw new IllegalArgumentException("Max capacity %d must be greater than or equal to min capacity %d"
                    .formatted(maxCapacity, minCapacity));
        this.minCapacity = minCapacity;
        this.maxCapacity = maxCapacity;
        this.resources = new LinkedBlockingQueue<>(maxCapacity);
        this.semaphore = new Semaphore(maxCapacity, false); // fair semaphore
        this.factory = factory;
        this.validator = validator;

        // Pre-populate the pool with objects
        for (int i = 0; i < minCapacity; i++) {
            resources.offer(factory.create());
        }
    }

    /**
     * Borrows an object from the pool. If no objects are available, this method
     * will block until one becomes available.
     *
     * @return an object from the pool
     * @throws InterruptedException  if the thread is interrupted while waiting
     * @throws IllegalStateException if the pool is closed
     */
    public T borrow() throws InterruptedException {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }
        semaphore.acquire();
        T resource = resources.poll();

        // If the queue is empty but semaphore allowed access, create a new resource
        if (resource == null) {
            resource = factory.create();
        }

        return resource;
    }

    /**
     * Returns an object to the pool.
     *
     * @param resource the object to return to the pool
     * @return true if the object was returned to the pool, false otherwise
     */
    public boolean release(T resource) {
        try {
            if (validator.isValid(resource)) {
                resources.offer(resource);
                return true;
            } else {
                if (availableCount() < minCapacity) {
                    resources.offer(factory.create());
                }
                return false;
            }
        } finally {
            semaphore.release();
        }
    }

    /**
     * Returns the number of available objects in the pool.
     *
     * @return the number of available objects
     */
    public int availableCount() {
        return resources.size();
    }

    public int minCapacity() {
        return minCapacity;
    }

    public int maxCapacity() {
        return maxCapacity;
    }

    public void close() {
        closed = true;
        // Release all resources
        resources.forEach(r -> {
            try {
                r.close();
            } catch (Exception e) {
                // ignore
            }
        });
    }
}
