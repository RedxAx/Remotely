package redxax.oxy.remotely.data.player;

import redxax.oxy.remotely.data.managed.PlayerSession;

import java.util.List;
import java.util.UUID;
import restudio.rebase.platform.Async;

public interface IPlayerHistoryProvider {
    void initialize();
    void shutdown();
    Async<List<PlayerSession>> getSessions(UUID uuid);
}