package redxax.oxy.remotely.data.player.action;

import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import restudio.rescreen.platform.Async;

public interface IActionExecutor {
    boolean canExecute(String actionType);
    Async<Void> execute(UnifiedPlayer player, String actionType, Object... args);
    int getPriority();
}
