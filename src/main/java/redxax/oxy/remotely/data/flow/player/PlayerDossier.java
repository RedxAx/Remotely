package redxax.oxy.remotely.data.flow.player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlayerDossier {
    private String playerId;
    private String playerName;
    private boolean online;
    private long firstSeenAt;
    private long lastSeenAt;
    private long totalPlayTimeMs;
    private PlayerSessionRecord activeSession;
    private List<PlayerSessionRecord> sessions = new ArrayList<>();
    private List<PlayerEventRecord> recentEvents = new ArrayList<>();
    private Map<String, PlayerFacetState> facets = new LinkedHashMap<>();

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public boolean isOnline() {
        return online;
    }

    public long getFirstSeenAt() {
        return firstSeenAt;
    }

    public long getLastSeenAt() {
        return lastSeenAt;
    }

    public long getTotalPlayTimeMs() {
        return totalPlayTimeMs;
    }

    public PlayerSessionRecord getActiveSession() {
        return activeSession;
    }

    public List<PlayerSessionRecord> getSessions() {
        return sessions == null ? List.of() : sessions;
    }

    public List<PlayerEventRecord> getRecentEvents() {
        return recentEvents == null ? List.of() : recentEvents;
    }

    public Map<String, PlayerFacetState> getFacets() {
        return facets == null ? Map.of() : facets;
    }
}
