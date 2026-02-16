package redxax.oxy.remotely.data.playerdata;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerDataSource {
    String getId();
    int getPriority();
    boolean isOnlineOnly();
    CompletableFuture<PlayerDataSnapshot> fetch(UUID uuid, String name);
}
