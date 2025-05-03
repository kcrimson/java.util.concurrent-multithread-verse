// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.actor;

public abstract class ActorRef<T> {
    private final String actorId;

    public ActorRef(String actorId) {
        this.actorId = actorId;
    }

    public String actorId() {
        return actorId;
    }

    public abstract boolean send(T message);
}
