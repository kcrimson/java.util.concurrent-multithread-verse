// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.pubsub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

/**
 * An in-memory publish-subscribe system similar to Kafka.
 * Uses StampedLock to efficiently handle concurrent read/write operations.
 */
public class PubSub {

    private final Map<String, Topic> topics = new ConcurrentHashMap<>();

    /**
     * Gets a topic, creating it if it doesn't exist.
     */
    public Topic getTopic(String name) {
        return topics.computeIfAbsent(name, Topic::new);
    }

    /**
     * Gets a producer for the specified topic.
     */
    public Producer getProducer(String topic) {
        return new ProducerImpl(getTopic(topic));
    }

    /**
     * Gets a consumer for the specified topic and consumer group.
     */
    public Consumer getConsumer(String topic, String consumerGroup) {
        return new ConsumerImpl(getTopic(topic), consumerGroup);
    }

    /**
     * Represents a topic that holds messages.
     */
    public class Topic {
        private final String name;
        private final List<Message> messages = new ArrayList<>();
        private final Map<String, ConsumerGroup> consumerGroups = new HashMap<>();
        private final StampedLock lock = new StampedLock();

        Topic(String name) {
            this.name = name;
        }

        /**
         * Publishes a message to this topic.
         * Uses a write lock to ensure exclusive access while adding the message.
         */
        void publish(Message message) {
            long stamp = lock.writeLock();
            try {
                messages.add(message);
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        /**
         * Gets messages for a consumer starting from its current offset.
         * Uses an optimistic read which is faster but may need to retry.
         */
        List<Message> getMessages(String consumerGroup, int maxMessages) {
            ConsumerGroup group = getOrCreateConsumerGroup(consumerGroup);
            // Try optimistic read first
            long stamp = lock.tryOptimisticRead();
            int currentOffset = group.getCurrentOffset();
            int messageCount = messages.size();

            // Create result list based on optimistic read values
            List<Message> result = new ArrayList<>();
            if (currentOffset < messageCount) {
                int endOffset = Math.min(currentOffset + maxMessages, messageCount);
                for (int i = currentOffset; i < endOffset; i++) {
                    result.add(messages.get(i));
                }
            }

            // Validate the read
            if (!lock.validate(stamp)) {
                // Optimistic read failed, fall back to read lock
                stamp = lock.readLock();
                try {
                    // Re-read with the lock
                    currentOffset = group.getCurrentOffset();
                    messageCount = messages.size();

                    // Rebuild the result list
                    result.clear();
                    if (currentOffset < messageCount) {
                        int endOffset = Math.min(currentOffset + maxMessages, messageCount);
                        for (int i = currentOffset; i < endOffset; i++) {
                            result.add(messages.get(i));
                        }
                    }
                } finally {
                    lock.unlockRead(stamp);
                }
            }

            return result;
        }

        /**
         * Commits the offset for a consumer group.
         * Uses lock conversion: read lock -> write lock for efficiency.
         */
        void commitOffset(String consumerGroup, int newOffset) {
            // Acquire read lock first
            long stamp = lock.readLock();
            try {
                ConsumerGroup group = getOrCreateConsumerGroup(consumerGroup);
                int currentOffset = group.getCurrentOffset();

                // Only proceed if we're updating to a larger offset
                if (newOffset > currentOffset) {
                    // Try to convert to write lock
                    long writeStamp = lock.tryConvertToWriteLock(stamp);
                    if (writeStamp != 0L) {
                        // Successfully converted to write lock
                        stamp = writeStamp;
                        group.setCurrentOffset(newOffset);
                    } else {
                        // Could not convert, release read lock and acquire write lock
                        lock.unlockRead(stamp);
                        stamp = lock.writeLock();
                        // Re-check condition since another thread may have updated
                        if (newOffset > group.getCurrentOffset()) {
                            group.setCurrentOffset(newOffset);
                        }
                    }
                }
            } finally {
                lock.unlock(stamp);
            }
        }

        /**
         * Gets or creates a consumer group for this topic.
         */
        private ConsumerGroup getOrCreateConsumerGroup(String groupName) {
            // Using a write lock here because we might modify the consumerGroups map
            long stamp = lock.writeLock();
            try {
                return consumerGroups.computeIfAbsent(groupName, name -> new ConsumerGroup(name));
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        /**
         * Gets the current size of the topic (number of messages).
         */
        public int size() {
            long stamp = lock.tryOptimisticRead();
            int size = messages.size();
            if (!lock.validate(stamp)) {
                // Optimistic read failed, fall back to read lock
                stamp = lock.readLock();
                try {
                    size = messages.size();
                } finally {
                    lock.unlockRead(stamp);
                }
            }
            return size;
        }
    }

    /**
     * Tracks the current offset for a group of consumers.
     */
    private static class ConsumerGroup {
        private final String name;
        private int currentOffset = 0;

        ConsumerGroup(String name) {
            this.name = name;
        }

        int getCurrentOffset() {
            return currentOffset;
        }

        void setCurrentOffset(int offset) {
            this.currentOffset = offset;
        }
    }

    /**
     * Represents a message in the pubsub system.
     */
    public static class Message {
        private final String key;
        private final String value;
        private final long timestamp;

        public Message(String key, String value) {
            this.key = key;
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "Message{key='" + key + "', value='" + value + "', timestamp=" + timestamp + '}';
        }
    }

    /**
     * Interface for publishing messages to a topic.
     */
    public interface Producer {
        void send(String key, String value);
    }

    /**
     * Implementation of the Producer interface.
     */
    private class ProducerImpl implements Producer {
        private final Topic topic;

        ProducerImpl(Topic topic) {
            this.topic = topic;
        }

        @Override
        public void send(String key, String value) {
            Message message = new Message(key, value);
            topic.publish(message);
        }
    }

    /**
     * Interface for consuming messages from a topic.
     */
    public interface Consumer {
        List<Message> poll(int maxMessages);

        void commitOffset(int offset);
    }

    /**
     * Implementation of the Consumer interface.
     */
    private class ConsumerImpl implements Consumer {
        private final Topic topic;
        private final String consumerGroup;

        ConsumerImpl(Topic topic, String consumerGroup) {
            this.topic = topic;
            this.consumerGroup = consumerGroup;
        }

        @Override
        public List<Message> poll(int maxMessages) {
            return topic.getMessages(consumerGroup, maxMessages);
        }

        @Override
        public void commitOffset(int offset) {
            topic.commitOffset(consumerGroup, offset);
        }
    }
}
