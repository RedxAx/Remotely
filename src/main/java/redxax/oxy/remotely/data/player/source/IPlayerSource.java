package redxax.oxy.remotely.data.player.source;

import redxax.oxy.remotely.data.player.PlayerService;

public interface IPlayerSource {
    void init(PlayerService context);
    void enable();
    void disable();
    int getPriority();
    void refresh();
}
