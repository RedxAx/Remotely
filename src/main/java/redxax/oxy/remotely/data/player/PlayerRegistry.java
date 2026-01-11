package redxax.oxy.remotely.data.player;

import redxax.oxy.remotely.data.player.model.UnifiedPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerRegistry {
    private final Map<UUID, UnifiedPlayer> players = new ConcurrentHashMap<>();

    public UnifiedPlayer getOrCreate(UUID uuid, String name) {
        return players.computeIfAbsent(uuid, k -> new UnifiedPlayer(k, name));
    }

    public UnifiedPlayer get(UUID uuid) {
        return players.get(uuid);
    }

    public List<UnifiedPlayer> getAll() {
        return new ArrayList<>(players.values());
    }

    public void cleanup() {

    }
}
