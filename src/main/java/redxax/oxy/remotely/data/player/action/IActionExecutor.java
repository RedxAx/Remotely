package redxax.oxy.remotely.data.player.action;

import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import java.util.concurrent.CompletableFuture;

public interface IActionExecutor {
    boolean canExecute(String actionType);
    CompletableFuture<Void> execute(UnifiedPlayer player, String actionType, Object... args);
    int getPriority();
}
