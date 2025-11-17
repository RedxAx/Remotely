package redxax.oxy.remotely.data.player;

import redxax.oxy.remotely.data.managed.PlayerSession;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IPlayerHistoryProvider {
    void initialize();
    void shutdown();
    CompletableFuture<List<PlayerSession>> getSessions(UUID uuid);
}