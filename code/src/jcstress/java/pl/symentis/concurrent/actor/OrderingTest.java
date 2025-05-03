// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.actor;

import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

/**
 * ActorSystem message ordering within a single actor.
 * This test verifies that messages sent to an actor from a single thread
 * are processed in the order they were sent.
 */
@JCStressTest
@Outcome(id = "0", expect = Expect.ACCEPTABLE, desc = "No ordering violations detected")
@Outcome(
        id = {"1", "2", "3", "4", "5"},
        expect = Expect.FORBIDDEN,
        desc = "Ordering violation detected")
@State
public class OrderingTest {

    // The actor system to test
    private final ActorSystem actorSystem = new ActorSystem(Runtime.getRuntime().availableProcessors());

    // The actor ID
    private static final String ACTOR_ID = "sequence-actor";

    // Track the last message received by the actor
    private final AtomicInteger lastMessage = new AtomicInteger(-1);

    // Track ordering violations
    private final AtomicInteger orderingViolations = new AtomicInteger(0);
    private final ActorRef<Integer> actorRef;

    public OrderingTest() {
        // Register the actor that will check message ordering
        actorRef = actorSystem.registerActor(ACTOR_ID, 100, (Integer msg) -> {
            int last = lastMessage.get();
            if (last >= msg) {
                // This is an ordering violation - messages should arrive in increasing order
                orderingViolations.incrementAndGet();
            }
            lastMessage.set(msg);
        });
    }

    @org.openjdk.jcstress.annotations.Actor
    public void actor1() {
        // Send 5 sequential messages
        for (int i = 0; i < 5; i++) {
            actorRef.send(i);
        }
    }

    @Arbiter
    public void arbiter(I_Result result) {
        // Give some time for messages to be processed
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Clean up
        actorSystem.shutdown();

        // Report the number of ordering violations
        result.r1 = orderingViolations.get();
    }
}
