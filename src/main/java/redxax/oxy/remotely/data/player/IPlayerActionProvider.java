package redxax.oxy.remotely.data.player;

import redxax.oxy.remotely.data.managed.ManagedPlayer;
import redxax.oxy.remotely.data.managed.PlayerAction;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IPlayerActionProvider {
    void initialize();
    void shutdown();
    CompletableFuture<Void> kickPlayer(ManagedPlayer player, String reason);
    CompletableFuture<Void> banPlayer(ManagedPlayer player, String reason, boolean ipBan);
    CompletableFuture<Void> unbanPlayer(ManagedPlayer player);
    CompletableFuture<Void> toggleOp(ManagedPlayer player);
    CompletableFuture<Void> runCustomCommand(ManagedPlayer player, String commandTemplate);
    CompletableFuture<List<PlayerAction>> getCustomActions();
}