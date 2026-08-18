package redxax.oxy.remotely.data.player.management;

import java.util.UUID;
import restudio.rebase.platform.Async;

public interface PlayerManagementProvider {
    String getId();
    PlayerProviderCapabilities capabilities(UUID playerId, boolean online);
    Async<PlayerDataContribution> refresh(UUID playerId, String playerName, boolean online);
}
