// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * A thread-safe LRU cache implementation using a doubly linked list and hash map.
 * Uses a non-blocking approach with fine-grained locking for optimal performance
 * under high contention.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of values maintained by this cache
 */
public class Cache<K, V> {

    private final int capacity;
    private final Function<K, V> computeFunction;
    private final Map<K, Node<K, V>> map;
    private final AtomicInteger size = new AtomicInteger(0);

    // Head and tail of the doubly linked list
    private final Node<K, V> head;
    private final Node<K, V> tail;

    // Lock for list structure modifications
    private final ReentrantLock listLock = new ReentrantLock();

    /**
     * Creates a new cache with the specified capacity and compute function.
     *
     * @param capacity the maximum number of entries in the cache
     * @param computeFunction the function to compute a value if it's not present in the cache
     */
    public Cache(int capacity, Function<K, V> computeFunction) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.computeFunction = computeFunction;
        this.map = new ConcurrentHashMap<>(capacity);

        // Initialize with dummy nodes
        Node<K, V> dummyHead = new Node<>(null, null);
        Node<K, V> dummyTail = new Node<>(null, null);
        dummyHead.next = dummyTail;
        dummyTail.prev = dummyHead;
        head = dummyHead;
        tail = dummyTail;
    }

    /**
     * Gets a value from the cache, computing it if necessary.
     *
     * @param key the key whose associated value is to be returned
     * @return the value associated with the key
     */
    public V get(K key) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }

        // Try to get node from the map
        Node<K, V> node = map.get(key);

        if (node != null) {
            // Key exists, move to front to mark as recently used
            moveToHead(node);
            return node.value;
        }

        // Key not in cache, compute it
        V value = computeFunction.apply(key);
        if (value == null) {
            return null; // Don't cache null values
        }

        // Try to add the computed value to the cache
        putValue(key, value);

        return value;
    }

    /**
     * Puts a key-value pair in the cache.
     *
     * @param key the key to add
     * @param value the value to add
     * @return the previous value associated with the key, or null if there was no mapping
     */
    public V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("Key and value cannot be null");
        }

        Node<K, V> oldNode = map.get(key);
        if (oldNode != null) {
            // Update existing entry
            V oldValue = oldNode.value;
            oldNode.value = value;
            moveToHead(oldNode);
            return oldValue;
        }

        // Add new entry
        putValue(key, value);
        return null;
    }

    /**
     * Helper method to add a new entry to the cache.
     */
    private void putValue(K key, V value) {
        // Create new node
        Node<K, V> newNode = new Node<>(key, value);

        // Try to add to map first
        Node<K, V> existingNode = map.putIfAbsent(key, newNode);
        if (existingNode != null) {
            // Another thread beat us to it, just move to head
            moveToHead(existingNode);
            return;
        }

        // Successfully added to map, now add to list
        addToHead(newNode);

        // Increment size and evict if necessary
        if (size.incrementAndGet() > capacity) {
            evictLRU();
        }
    }

    /**
     * Adds a node to the head of the list.
     */
    private void addToHead(Node<K, V> node) {
        listLock.lock();
        try {
            Node<K, V> first = head.next;
            node.next = first;
            node.prev = head;
            first.prev = node;
            head.next = node;
        } finally {
            listLock.unlock();
        }
    }

    /**
     * Moves a node to the head of the list.
     */
    private void moveToHead(Node<K, V> node) {
        // Skip if it's already at head
        if (head.next == node) {
            return;
        }

        listLock.lock();
        try {
            // Remove from current position
            removeFromList(node);

            // Add to head
            addToHead(node);
        } finally {
            listLock.unlock();
        }
    }

    /**
     * Removes a node from the list.
     */
    private void removeFromList(Node<K, V> node) {
        Node<K, V> prevNode = node.prev;
        Node<K, V> nextNode = node.next;

        if (prevNode != null) {
            prevNode.next = nextNode;
        }

        if (nextNode != null) {
            nextNode.prev = prevNode;
        }
    }

    /**
     * Evicts the least recently used entry.
     */
    private void evictLRU() {
        listLock.lock();
        try {
            // Get the LRU node (the one before tail)
            Node<K, V> lastNode = tail.prev;

            // Skip if it's the dummy head
            if (lastNode == head) {
                return;
            }

            // Remove from list
            removeFromList(lastNode);

            // Remove from map
            if (lastNode.key != null) {
                map.remove(lastNode.key);
                size.decrementAndGet();
            }
        } finally {
            listLock.unlock();
        }
    }

    /**
     * Returns the number of entries in the cache.
     *
     * @return the number of entries
     */
    public int size() {
        return size.get();
    }

    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        listLock.lock();
        try {
            map.clear();

            // Reset the list to just dummy nodes
            Node<K, V> dummyHead = head;
            Node<K, V> dummyTail = tail;
            dummyHead.next = dummyTail;
            dummyTail.prev = dummyHead;

            size.set(0);
        } finally {
            listLock.unlock();
        }
    }

    /**
     * Node for the doubly linked list.
     */
    private static class Node<K, V> {
        final K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}
