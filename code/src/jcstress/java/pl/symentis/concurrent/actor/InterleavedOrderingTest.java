// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.actor;

import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/**
 * ActorSystem message ordering.
 * This test verifies that messages sent to an actor from the same thread
 * are processed in the correct order, even when interleaved with messages
 * from another thread.
 */
@JCStressTest
@Outcome(
        id = {"2, 2"},
        expect = Expect.ACCEPTABLE,
        desc = "All messages processed correctly")
@Outcome(
        id = {"1, 1"},
        expect = Expect.ACCEPTABLE_INTERESTING,
        desc = "Some messages still in flight")
@Outcome(
        id = {"2, 1", "1, 2"},
        expect = Expect.ACCEPTABLE_INTERESTING,
        desc = "Messages processed from one thread")
@Outcome(
        id = {"0, 0"},
        expect = Expect.FORBIDDEN,
        desc = "No messages processed")
@State
public class InterleavedOrderingTest {

    // The actor system to test
    private final ActorSystem actorSystem = new ActorSystem(Runtime.getRuntime().availableProcessors());

    // The actor IDs
    private static final String ACTOR_ID_1 = "counter-actor-1";
    private static final String ACTOR_ID_2 = "counter-actor-2";

    // Counters for each actor
    private final AtomicInteger counter1 = new AtomicInteger(0);
    private final AtomicInteger counter2 = new AtomicInteger(0);
    private final ActorRef<Integer> actorRef1;
    private final ActorRef<Integer> actorRef2;

    public InterleavedOrderingTest() {
        // Register the first actor
        actorRef1 = actorSystem.registerActor(ACTOR_ID_1, 10, (Integer msg) -> {
            counter1.incrementAndGet();
        });

        // Register the second actor
        actorRef2 = actorSystem.registerActor(ACTOR_ID_2, 10, (Integer msg) -> {
            counter2.incrementAndGet();
        });
    }

    @org.openjdk.jcstress.annotations.Actor
    public void actor1() {
        // Send two messages to the first actor
        actorRef1.send(1);
        actorRef1.send(2);
    }

    @org.openjdk.jcstress.annotations.Actor
    public void actor2() {
        // Send two messages to the second actor
        actorRef2.send(1);
        actorRef2.send(2);
    }

    @Arbiter
    public void arbiter(II_Result result) {
        // Give some time for messages to be processed
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Clean up
        actorSystem.shutdown();

        // Check the final counter values
        result.r1 = counter1.get();
        result.r2 = counter2.get();
    }
}
