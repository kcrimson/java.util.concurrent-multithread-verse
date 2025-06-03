// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.actor;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * A simple actor system implementation that allows multiple actors to share the same thread pool
 * while ensuring each actor is processed by only one thread at a time.
 */
public class ActorSystem {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ActorSystem.class);
    private final ExecutorService executorService;
    private final Map<String, ActorContext<?>> actors = new ConcurrentHashMap<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    /**
     * Creates a new ActorSystem with a fixed thread pool.
     *
     * @param threadCount the number of threads in the pool
     */
    public ActorSystem(int threadCount) {
        this.executorService = Executors.newFixedThreadPool(
                threadCount, Thread.ofPlatform().name("actor-system-", 0).factory());
    }

    /**
     * Registers a new actor with the system.
     *
     * @param <T>             the type of messages this actor processes
     * @param actorId         a unique identifier for the actor
     * @param mailboxCapacity the maximum capacity of the actor's mailbox
     * @param messageHandler  the function that processes messages
     * @return null if an actor with the same ID already exists, otherwise a new ActorRef
     */
    public <T> ActorRef<T> registerActor(String actorId, int mailboxCapacity, Consumer<T> messageHandler) {
        ActorContext<T> actorContext = new ActorContext<>(actorId, mailboxCapacity, messageHandler);
        if (actors.putIfAbsent(actorId, actorContext) != null) {
            return null; // Actor with this ID already exists
        }

        // Schedule the initial processing task
        scheduleProcessing(actorContext);
        return new ActorRef<>(actorId) {
            @Override
            public boolean send(T message) {
                return ActorSystem.this.send(actorId, message);
            }
        };
    }

    /**
     * Sends a message to a specific actor.
     *
     * @param <T>     the type of the message
     * @param actorId the ID of the target actor
     * @param message the message to send
     * @return true if the message was sent, false if the actor doesn't exist or the mailbox is full
     */
    @SuppressWarnings("unchecked")
    private <T> boolean send(String actorId, T message) {
        ActorContext<T> actorContext = (ActorContext<T>) actors.get(actorId);
        if (actorContext == null) {
            return false;
        }

        return actorContext.enqueue(message);
    }

    /**
     * Schedules message processing for an actor.
     */
    private <T> void scheduleProcessing(ActorContext<T> actorContext) {
        executorService.submit(() -> {
            try {
                while (isRunning.get() && actorContext.processNextMessage()) {
                    // in other thread some other actor can process messages
                }
                if (isRunning.get() && actorContext.setProcessing(false)) {
                    if (!actorContext.isEmpty()) {
                        actorContext.setProcessing(true);
                        scheduleProcessing(actorContext);
                    }
                }
            } catch (Exception e) {
                LOG.error("Error processing messages for actor {}", actorContext.actorId, e);
                actorContext.setProcessing(false);
            }
        });
    }

    /**
     * Shuts down the actor system and its thread pool.
     */
    public void shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            executorService.close();
        }
    }

    /**
     * Internal class representing an actor's context, including its mailbox and processing state.
     */
    private class ActorContext<T> {
        private final String actorId;
        private final BlockingQueue<T> mailbox;
        private final Consumer<T> messageHandler;
        private final AtomicBoolean processing = new AtomicBoolean(false);

        ActorContext(String actorId, int mailboxCapacity, Consumer<T> messageHandler) {
            this.actorId = actorId;
            this.mailbox = new LinkedBlockingQueue<>(mailboxCapacity);
            this.messageHandler = messageHandler;
        }

        /**
         * Adds a message to this actor's mailbox.
         */
        boolean enqueue(T message) {
            boolean result = mailbox.offer(message);

            // If the message was added and the actor isn't currently being processed,
            // try to start processing
            if (result && setProcessing(true)) {
                scheduleProcessing(this);
            }

            return result;
        }

        /**
         * Processes the next message in the mailbox, if any.
         *
         * @return true if a message was processed, false if the mailbox was empty
         */
        boolean processNextMessage() {
            T message = mailbox.poll();
            if (message != null) {
                messageHandler.accept(message);
                return true;
            }
            return false;
        }

        /**
         * Attempts to set the processing state of this actor.
         *
         * @param newState the new processing state
         * @return true if the state was changed, false if it was already in the requested state
         */
        boolean setProcessing(boolean newState) {
            if (newState) {
                return !processing.getAndSet(true);
            } else {
                processing.set(false);
                return true;
            }
        }

        /**
         * Checks if the mailbox is empty.
         */
        boolean isEmpty() {
            return mailbox.isEmpty();
        }
    }
}
