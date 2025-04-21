// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.actor;

/**
 * Interface representing an actor in the actor system.
 *
 * @param <T> the type of messages this actor can process
 */
public interface Actor<T> {

    /**
     * Processes a message received by this actor.
     *
     * @param message the message to process
     */
    void receive(T message);

    /**
     * Gets the unique ID of this actor.
     *
     * @return the actor's ID
     */
    String getId();
}
