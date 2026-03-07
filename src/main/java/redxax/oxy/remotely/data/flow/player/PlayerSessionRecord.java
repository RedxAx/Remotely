package redxax.oxy.remotely.data.flow.player;

public class PlayerSessionRecord {
    private String sessionId;
    private String source;
    private long startedAt;
    private long endedAt;
    private long durationMs;

    public String getSessionId() {
        return sessionId;
    }

    public String getSource() {
        return source;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getEndedAt() {
        return endedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
