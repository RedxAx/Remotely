package redxax.oxy.remotely.data.player.management;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerManagementProvider {
    String getId();
    PlayerProviderCapabilities capabilities(UUID playerId, boolean online);
    CompletableFuture<PlayerDataContribution> refresh(UUID playerId, String playerName, boolean online);
}
