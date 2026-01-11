package redxax.oxy.remotely.data.player.model;

public class PlayerAttribute<T> {
    private final T value;
    private final String source;
    private final long timestamp;
    private final int priority;

    public PlayerAttribute(T value, String source, int priority) {
        this.value = value;
        this.source = source;
        this.priority = priority;
        this.timestamp = System.currentTimeMillis();
    }

    public T getValue() {
        return value;
    }

    public String getSource() {
        return source;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getPriority() {
        return priority;
    }
}
