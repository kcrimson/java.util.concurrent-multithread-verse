// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.pubsub;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.ZZ_Result;
import pl.symentis.concurrent.pubsub.PubSub.Consumer;
import pl.symentis.concurrent.pubsub.PubSub.Message;
import pl.symentis.concurrent.pubsub.PubSub.Producer;

/**
 * JCStress test for PubSub system that verifies both consumer groups receive
 * multiple messages when a single producer sends multiple messages to a topic.
 */
@JCStressTest
@Outcome(id = "true:true", expect = Expect.ACCEPTABLE, desc = "Both consumer groups received all messages")
@Outcome(
        id = "false:true",
        expect = Expect.ACCEPTABLE_INTERESTING,
        desc = "Only consumer group 2 received all messages")
@Outcome(
        id = "true:false",
        expect = Expect.ACCEPTABLE_INTERESTING,
        desc = "Only consumer group 1 received all messages")
@Outcome(
        id = "false:false",
        expect = Expect.ACCEPTABLE_INTERESTING,
        desc = "Neither consumer group received all messages")
@State
public class MultiMessageConsumerGroupsTest {

    // Number of messages to send
    private static final int MESSAGE_COUNT = 5;

    // Topic name for the test
    private static final String TOPIC_NAME = "multi-message-topic";

    // Consumer group names
    private static final String CONSUMER_GROUP_1 = "multi-group-1";
    private static final String CONSUMER_GROUP_2 = "multi-group-2";

    // Test message key and value prefixes
    private static final String MESSAGE_KEY_PREFIX = "key-";
    private static final String MESSAGE_VALUE_PREFIX = "value-";

    // The PubSub system to test
    private final PubSub pubSub = new PubSub();

    // Producer for the test topic
    private final Producer producer = pubSub.getProducer(TOPIC_NAME);

    // Consumers for the test topic, one from each consumer group
    private final Consumer consumer1 = pubSub.getConsumer(TOPIC_NAME, CONSUMER_GROUP_1);
    private final Consumer consumer2 = pubSub.getConsumer(TOPIC_NAME, CONSUMER_GROUP_2);

    // Track whether each consumer group received all expected messages
    private final AtomicBoolean group1ReceivedAll = new AtomicBoolean(false);
    private final AtomicBoolean group2ReceivedAll = new AtomicBoolean(false);

    // Latch to ensure consumers have time to poll after all messages are produced
    private final CountDownLatch producerLatch = new CountDownLatch(1);

    // Time to wait for message processing
    private static final long WAIT_TIME_MS = 200;

    /**
     * Producer thread that sends multiple messages to the topic.
     */
    @Actor
    public void producer() {
        // Send multiple messages to the topic
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            producer.send(MESSAGE_KEY_PREFIX + i, MESSAGE_VALUE_PREFIX + i);
        }

        // Signal that all messages have been produced
        producerLatch.countDown();
    }

    /**
     * Consumer thread for group 1 that polls for messages.
     */
    @Actor
    public void consumer1() {
        try {
            // Wait for producer to send all messages
            producerLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS);

            // Track received messages
            boolean[] received = new boolean[MESSAGE_COUNT];
            int totalReceived = 0;

            // Poll until we receive all messages or timeout
            long startTime = System.currentTimeMillis();
            while (totalReceived < MESSAGE_COUNT && System.currentTimeMillis() - startTime < WAIT_TIME_MS) {

                // Poll for messages
                List<Message> messages = consumer1.poll(MESSAGE_COUNT);

                // Process received messages
                for (Message message : messages) {
                    String key = message.getKey();
                    if (key.startsWith(MESSAGE_KEY_PREFIX)) {
                        try {
                            int index = Integer.parseInt(key.substring(MESSAGE_KEY_PREFIX.length()));
                            if (index >= 0 && index < MESSAGE_COUNT && !received[index]) {
                                received[index] = true;
                                totalReceived++;
                            }
                        } catch (NumberFormatException e) {
                            // Ignore malformed keys
                        }
                    }
                }

                // Commit the offset if we processed messages
                if (!messages.isEmpty()) {
                    consumer1.commitOffset(messages.size());
                }

                // Short delay between polls
                Thread.sleep(10);
            }

            // Check if we received all expected messages
            group1ReceivedAll.set(totalReceived == MESSAGE_COUNT);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Consumer thread for group 2 that polls for messages.
     */
    @Actor
    public void consumer2() {
        try {
            // Wait for producer to send all messages
            producerLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS);

            // Track received messages
            boolean[] received = new boolean[MESSAGE_COUNT];
            int totalReceived = 0;

            // Poll until we receive all messages or timeout
            long startTime = System.currentTimeMillis();
            while (totalReceived < MESSAGE_COUNT && System.currentTimeMillis() - startTime < WAIT_TIME_MS) {

                // Poll for messages
                List<Message> messages = consumer2.poll(MESSAGE_COUNT);

                // Process received messages
                for (Message message : messages) {
                    String key = message.getKey();
                    if (key.startsWith(MESSAGE_KEY_PREFIX)) {
                        try {
                            int index = Integer.parseInt(key.substring(MESSAGE_KEY_PREFIX.length()));
                            if (index >= 0 && index < MESSAGE_COUNT && !received[index]) {
                                received[index] = true;
                                totalReceived++;
                            }
                        } catch (NumberFormatException e) {
                            // Ignore malformed keys
                        }
                    }
                }

                // Commit the offset if we processed messages
                if (!messages.isEmpty()) {
                    consumer2.commitOffset(messages.size());
                }

                // Short delay between polls
                Thread.sleep(10);
            }

            // Check if we received all expected messages
            group2ReceivedAll.set(totalReceived == MESSAGE_COUNT);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Arbiter that records the results after all actors have run.
     */
    @Arbiter
    public void arbiter(ZZ_Result result) {
        // Record whether each consumer group received all expected messages
        result.r1 = group1ReceivedAll.get();
        result.r2 = group2ReceivedAll.get();
    }
}
