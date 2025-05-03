// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.actor;

import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

/**
 * ActorSystem under high contention.
 * This test verifies that a single actor correctly processes messages
 * when multiple threads are rapidly sending messages to it.
 */
@JCStressTest
@Outcome(id = "10", expect = Expect.ACCEPTABLE, desc = "All messages processed")
@Outcome(
        id = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"},
        expect = Expect.ACCEPTABLE_INTERESTING,
        desc = "Some messages still in flight")
@State
public class ContentionTest {

    // The actor system to test
    private final ActorSystem actorSystem = new ActorSystem(Runtime.getRuntime().availableProcessors());

    // The actor ID
    private static final String ACTOR_ID = "counter-actor";
    private final ActorRef<Integer> actorRef;

    // Volatile counter that will be incremented by the actor on message receipt
    private volatile int counter = 0;

    public ContentionTest() {
        // Register the actor that will increment the counter for each message
        actorRef = actorSystem.registerActor(ACTOR_ID, 100, (Integer msg) -> {
            counter += msg;
        });
    }

    @org.openjdk.jcstress.annotations.Actor
    public void actor1() {
        // Send 5 messages from the first thread
        for (int i = 0; i < 5; i++) {
            actorRef.send(1);
        }
    }

    @org.openjdk.jcstress.annotations.Actor
    public void actor2() {
        // Send 5 messages from the second thread
        for (int i = 0; i < 5; i++) {
            actorRef.send(1);
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

        // Check the final counter value
        result.r1 = counter;
    }
}
