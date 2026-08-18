package redxax.oxy.remotely.data.flow.player;

public class PlayerSessionRecord {
    private String sessionId;
    private String source;
    private long startedAt;
    private long endedAt;
    private long durationMs;

    public PlayerSessionRecord() {
    }

    public PlayerSessionRecord(String sessionId, String source, long startedAt, long endedAt, long durationMs) {
        this.sessionId = sessionId; this.source = source; this.startedAt = startedAt; this.endedAt = endedAt; this.durationMs = durationMs;
    }

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
