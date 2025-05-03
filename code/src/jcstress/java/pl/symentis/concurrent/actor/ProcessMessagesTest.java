// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.actor;

import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

/**
 * This test verifies that a single actor correctly processes messages
 * sent from two distinct threads.
 */
@JCStressTest
@Outcome(id = "2", expect = Expect.ACCEPTABLE, desc = "Both messages were processed")
@Outcome(id = "1", expect = Expect.ACCEPTABLE_INTERESTING, desc = "Only one message was processed")
@Outcome(id = "0", expect = Expect.FORBIDDEN, desc = "No messages were processed")
@State
public class ProcessMessagesTest {

    // The actor system to test
    private final ActorSystem actorSystem = new ActorSystem(Runtime.getRuntime().availableProcessors());

    // The actor ID
    private static final String ACTOR_ID = "counter-actor";
    private final ActorRef<Integer> actorRef;

    // Volatile counter that will be incremented by the actor on message receipt
    private volatile int counter = 0;

    public ProcessMessagesTest() {
        // Register the actor that will increment the counter for each message
        actorRef = actorSystem.registerActor(ACTOR_ID, 10, (Integer msg) -> {
            counter += msg;
        });
    }

    @org.openjdk.jcstress.annotations.Actor
    public void actor1() {
        // Send a message from the first thread
        actorRef.send(1);
    }

    @org.openjdk.jcstress.annotations.Actor
    public void actor2() {
        // Send a message from the second thread
        actorRef.send(1);
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
