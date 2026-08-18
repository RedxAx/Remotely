package redxax.oxy.remotely.data.playerdata;

import restudio.rebase.platform.Async;

import java.util.UUID;

public interface PlayerDataSource {
    String getId();
    int getPriority();
    boolean isOnlineOnly();
    Async<PlayerDataSnapshot> fetch(UUID uuid, String name);
}
