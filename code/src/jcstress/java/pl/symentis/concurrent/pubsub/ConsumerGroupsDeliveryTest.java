// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.pubsub;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;
import pl.symentis.concurrent.pubsub.PubSub.Consumer;
import pl.symentis.concurrent.pubsub.PubSub.Message;
import pl.symentis.concurrent.pubsub.PubSub.Producer;

/**
 * JCStress test for PubSub system that verifies both consumer groups receive messages
 * when a single producer sends a message to a topic.
 */
@JCStressTest
@Outcome(id = "1:1", expect = Expect.ACCEPTABLE, desc = "Both consumer groups received the message")
@Outcome(id = "0:1", expect = Expect.ACCEPTABLE_INTERESTING, desc = "Only consumer group 2 received the message")
@Outcome(id = "1:0", expect = Expect.ACCEPTABLE_INTERESTING, desc = "Only consumer group 1 received the message")
@Outcome(id = "0:0", expect = Expect.ACCEPTABLE_INTERESTING, desc = "Neither consumer group received the message")
@State
public class ConsumerGroupsDeliveryTest {

    // Topic name for the test
    private static final String TOPIC_NAME = "test-topic";

    // Consumer group names
    private static final String CONSUMER_GROUP_1 = "group-1";
    private static final String CONSUMER_GROUP_2 = "group-2";

    // Test message key and value
    private static final String MESSAGE_KEY = "test-key";
    private static final String MESSAGE_VALUE = "test-value";

    // The PubSub system to test
    private final PubSub pubSub = new PubSub();

    // Producer for the test topic
    private final Producer producer = pubSub.getProducer(TOPIC_NAME);

    // Consumers for the test topic, one from each consumer group
    private final Consumer consumer1 = pubSub.getConsumer(TOPIC_NAME, CONSUMER_GROUP_1);
    private final Consumer consumer2 = pubSub.getConsumer(TOPIC_NAME, CONSUMER_GROUP_2);

    // Track message receipt for each consumer group
    private final AtomicInteger group1Received = new AtomicInteger(0);
    private final AtomicInteger group2Received = new AtomicInteger(0);

    // Latch to ensure consumers have time to poll after the message is produced
    private final CountDownLatch producerLatch = new CountDownLatch(1);

    // Time to wait for message processing
    private static final long WAIT_TIME_MS = 100;

    /**
     * Producer thread that sends a single message to the topic.
     */
    @Actor
    public void producer() {
        // Send a message to the topic
        producer.send(MESSAGE_KEY, MESSAGE_VALUE);

        // Signal that the message has been produced
        producerLatch.countDown();
    }

    /**
     * Consumer thread for group 1 that polls for messages.
     */
    @Actor
    public void consumer1() {
        try {
            // Wait for producer to send the message
            producerLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS);

            // Poll for messages
            List<Message> messages = consumer1.poll(10);

            // Check if the message was received
            if (!messages.isEmpty()) {
                // Check if the received message matches the expected message
                for (Message message : messages) {
                    if (MESSAGE_KEY.equals(message.getKey()) && MESSAGE_VALUE.equals(message.getValue())) {
                        group1Received.incrementAndGet();
                    }
                }

                // Commit the offset if we processed messages
                if (!messages.isEmpty()) {
                    consumer1.commitOffset(messages.size());
                }
            }
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
            // Wait for producer to send the message
            producerLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS);

            // Poll for messages
            List<Message> messages = consumer2.poll(10);

            // Check if the message was received
            if (!messages.isEmpty()) {
                // Check if the received message matches the expected message
                for (Message message : messages) {
                    if (MESSAGE_KEY.equals(message.getKey()) && MESSAGE_VALUE.equals(message.getValue())) {
                        group2Received.incrementAndGet();
                    }
                }

                // Commit the offset if we processed messages
                if (!messages.isEmpty()) {
                    consumer2.commitOffset(messages.size());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Arbiter that records the results after all actors have run.
     */
    @Arbiter
    public void arbiter(II_Result result) {
        // Give some additional time for message processing
        try {
            Thread.sleep(WAIT_TIME_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Record the results - how many messages each consumer group received
        result.r1 = group1Received.get();
        result.r2 = group2Received.get();
    }
}
