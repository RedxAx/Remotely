package redxax.oxy.remotely.data.playerdata;

import java.util.UUID;

public record PlayerDataSnapshot(UUID uuid, PlayerData data, String source, int priority) {
}
