package pl.symentis.concurrent.actor;

public abstract class ActorRef<T> {
    private final String actorId;
    private final ActorSystem actorSystem;

    public ActorRef(String actorId, ActorSystem actorSystem) {
        this.actorId = actorId;
        this.actorSystem = actorSystem;
    }

    public abstract boolean send(T message);
}
