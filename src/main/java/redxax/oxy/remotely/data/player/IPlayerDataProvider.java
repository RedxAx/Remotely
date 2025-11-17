package redxax.oxy.remotely.data.player;

import redxax.oxy.remotely.data.managed.ManagedPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface IPlayerDataProvider {
    void initialize();
    void shutdown();
    CompletableFuture<Void> fullRefresh();
    Map<UUID, ManagedPlayer> getCachedPlayers();
    void addUpdateListener(Consumer<List<ManagedPlayer>> listener);
    void removeUpdateListener(Consumer<List<ManagedPlayer>> listener);
}