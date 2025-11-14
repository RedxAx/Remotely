package redxax.oxy.remotely.msmp;

import redxax.oxy.remotely.msmp.dto.GameRule;
import redxax.oxy.remotely.msmp.dto.Player;
import redxax.oxy.remotely.msmp.dto.TickInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface IMSMPApi extends AutoCloseable {
    CompletableFuture<Boolean> connect(String host, int port, String token);
    boolean isConnected();
    void close();

    void subscribeToTicks(Consumer<TickInfo> onTick);
    void subscribeToPlayers(Consumer<List<Player>> onPlayerChange);
    void subscribeToChat(Consumer<String> onChatMessage);
    void subscribeToLifecycle(Consumer<String> onEvent);

    CompletableFuture<List<Player>> getPlayers();
    CompletableFuture<Void> kickPlayer(String uuid, String reason);
    CompletableFuture<Void> banPlayer(String uuid, String reason);
    CompletableFuture<Void> opPlayer(String uuid, int level);
    CompletableFuture<Void> deopPlayer(String uuid);

    CompletableFuture<List<GameRule>> getGameRules();
    CompletableFuture<Void> setGameRule(String ruleName, String value);

    void discoverApi();
}