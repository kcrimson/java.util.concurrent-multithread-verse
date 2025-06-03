// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.actor;

import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Control;

/**
 * Benchmark for measuring message round-trip time in ActorSystem.
 * Uses an Exchanger to create a producer-consumer pattern that avoids
 * hot loops by requiring the consumer to explicitly exchange with the producer.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ActorSystemBenchmark {

    private ActorSystem actorSystem;
    private ActorRef<Control> actorRef;
    private Exchanger<Object> exchanger;

    @Param({"1", "2", "4"})
    private int threadCount;

    @Param({"1", "5", "10"})
    private int delayMs;

    /**
     * Setup for the entire benchmark - creates a new ActorSystem with specified threads.
     */
    @Setup(Level.Trial)
    public void setup() {
        // Create a new ActorSystem with the specified thread count
        actorSystem = new ActorSystem(threadCount);

        // Create a new Exchanger for producer-consumer coordination
        exchanger = new Exchanger<>();

        // Register an actor that exchanges messages with the consumer
        actorRef = actorSystem.registerActor("message-exchanger", 10_000, (Control control) -> {
            if (!control.stopMeasurement) {
                try {
                    // Exchange with consumer to signal message receipt
                    exchanger.exchange("processed", 1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (TimeoutException e) {
                    // Timeout is okay in this benchmark context
                }
            }
        });
    }

    /**
     * Cleanup after the benchmark is complete.
     */
    @TearDown(Level.Trial)
    public void tearDown() {
        actorSystem.shutdown();
    }

    /**
     * Producer benchmark - sends messages to the actor with a specified delay
     * to avoid overloading the actor system.
     */
    @Benchmark
    @Group("messageExchange")
    @GroupThreads(8)
    public void producer(Control control) throws InterruptedException {
        if (control.stopMeasurement) {
            return;
        }

        // Send a message to the actor
        boolean sent = actorRef.send(control);

        // Add delay to prevent hot loop - crucial for allowing other threads to process
        if (sent && delayMs > 0) {
            Thread.sleep(delayMs);
        }
    }

    /**
     * Consumer benchmark - waits to exchange with the actor after it processes messages.
     */
    @Benchmark
    @Group("messageExchange")
    @GroupThreads(1)
    public Object consumer(Control control) {
        if (control.stopMeasurement) {
            return null;
        }

        try {
            // Wait for exchange from actor, indicating message was processed
            return exchanger.exchange(null, 1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        } catch (TimeoutException e) {
            return "timeout";
        }
    }
}
