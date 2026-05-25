// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Thread-safe LRU cache backed by a {@link ConcurrentHashMap} and a doubly-linked list.
 *
 * <p>Reads use an optimistic lock-free fast path: the map is queried without a lock, then
 * re-validated under {@code listLock} to prevent a race with concurrent eviction. Writes and
 * evictions hold {@code listLock} for their entire duration, keeping the map and the list
 * consistent with each other at all times.
 */
public class Cache<K, V> {

    private final int capacity;
    private final Function<K, V> computeFunction;
    private final Map<K, Node<K, V>> map;
    private final AtomicInteger size = new AtomicInteger(0);

    private final Node<K, V> head;
    private final Node<K, V> tail;

    private final ReentrantLock listLock = new ReentrantLock();

    public Cache(int capacity, Function<K, V> computeFunction) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.computeFunction = computeFunction;
        this.map = new ConcurrentHashMap<>(capacity);

        head = new Node<>(null, null);
        tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    /**
     * Returns the value for {@code key}, computing and caching it on a miss.
     *
     * <p>Uses an optimistic lock-free read: the map lookup races no other operation.
     * If the node is found, we re-check under {@code listLock} — eviction removes from the
     * map while holding the lock, so a positive {@code containsKey} under the lock guarantees
     * the node is still in the list.
     */
    public V get(K key) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }

        // Optimistic lock-free read
        Node<K, V> node = map.get(key);
        if (node != null) {
            listLock.lock();
            try {
                // Re-validate: evictLRU removes from map while holding listLock, so if the
                // key is still present here the node is also still in the list.
                if (map.containsKey(key)) {
                    moveToHead(node);
                    return node.value;
                }
            } finally {
                listLock.unlock();
            }
        }

        // Cache miss: compute outside the lock (may be expensive)
        V value = computeFunction.apply(key);
        if (value == null) {
            return null;
        }

        listLock.lock();
        try {
            // Another thread may have cached this key while we computed
            Node<K, V> existing = map.get(key);
            if (existing != null) {
                moveToHead(existing);
                return existing.value;
            }
            addNewEntry(key, value);
        } finally {
            listLock.unlock();
        }

        return value;
    }

    /**
     * Inserts or updates {@code key → value}, evicting the LRU entry if over capacity.
     *
     * <p>The entire operation runs under {@code listLock} so the map and the list
     * are never observed in an inconsistent state.
     */
    public V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("Key and value cannot be null");
        }

        listLock.lock();
        try {
            Node<K, V> oldNode = map.get(key);
            if (oldNode != null) {
                V oldValue = oldNode.value;
                oldNode.value = value;
                moveToHead(oldNode);
                return oldValue;
            }
            addNewEntry(key, value);
        } finally {
            listLock.unlock();
        }
        return null;
    }

    public int size() {
        return size.get();
    }

    public void clear() {
        listLock.lock();
        try {
            map.clear();
            head.next = tail;
            tail.prev = head;
            size.set(0);
        } finally {
            listLock.unlock();
        }
    }

    // ── helpers (all called under listLock) ───────────────────────────────────

    private void addNewEntry(K key, V value) {
        Node<K, V> node = new Node<>(key, value);
        map.put(key, node);
        addToHead(node);
        if (size.incrementAndGet() > capacity) {
            evictLRU();
        }
    }

    private void moveToHead(Node<K, V> node) {
        if (head.next == node) {
            return;
        }
        removeFromList(node);
        addToHead(node);
    }

    private void addToHead(Node<K, V> node) {
        Node<K, V> first = head.next;
        node.next = first;
        node.prev = head;
        first.prev = node;
        head.next = node;
    }

    private void removeFromList(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void evictLRU() {
        Node<K, V> lru = tail.prev;
        if (lru == head) {
            return;
        }
        removeFromList(lru);
        map.remove(lru.key);
        size.decrementAndGet();
    }

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
