package redxax.oxy.remotely.data.player;

import redxax.oxy.remotely.data.player.action.IActionExecutor;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.data.player.source.IPlayerSource;
import restudio.rescreen.debug.DebugManager;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class PlayerService {
    private final PlayerRegistry registry = new PlayerRegistry();
    private final List<IPlayerSource> sources = new CopyOnWriteArrayList<>();
    private final List<IActionExecutor> executors = new CopyOnWriteArrayList<>();
    private final List<Consumer<List<UnifiedPlayer>>> listeners = new CopyOnWriteArrayList<>();

    public void registerSource(IPlayerSource source) {
        sources.add(source);
        source.init(this);
        source.enable();
    }
    
    public void registerExecutor(IActionExecutor executor) {
        executors.add(executor);
    }
    
    public void refreshSources() {
        for (IPlayerSource source : sources) {
            source.refresh();
        }
    }
    
    public void submitUpdate(PlayerUpdateBatch batch) {
        for (PlayerUpdateBatch.PlayerUpdate update : batch.getUpdates()) {
            UnifiedPlayer player = registry.getOrCreate(update.getUuid(), update.getName());
            
            if (update.getName() != null && !update.getName().isEmpty()) {
                player.setName(update.getName());
            }

            String src = batch.getSource();
            int prio = batch.getPriority();

            if (update.getOnline() != null) player.updateOnline(update.getOnline(), src, prio);
            if (update.getPing() != null) player.updatePing(update.getPing(), src, prio);
            if (update.getOp() != null) player.updateOp(update.getOp(), src, prio);
            if (update.getLastSeen() != null) player.updateLastSeen(update.getLastSeen(), src, prio);
            
            if (update.shouldClearBan()) player.updateBan(null, src, prio);
            else if (update.getBan() != null) player.updateBan(update.getBan(), src, prio);
            
            if (update.shouldClearIp()) player.updateIp(null, src, prio);
            else if (update.getIp() != null) player.updateIp(update.getIp(), src, prio);
        }
        
        notifyListeners();
    }

    public CompletableFuture<Void> executeAction(UnifiedPlayer player, String actionType, Object... args) {
        DebugManager.getInstance().log("PlayerService", "Executing action: " + actionType + " for " + player.getName());
        List<IActionExecutor> candidates = executors.stream()
                .filter(e -> {
                    boolean can = e.canExecute(actionType);
                    DebugManager.getInstance().log("PlayerService", "Executor " + e.getClass().getSimpleName() + " canExecute(" + actionType + "): " + can);
                    return can;
                })
                .sorted(Comparator.comparingInt(IActionExecutor::getPriority).reversed())
                .toList();

        if (candidates.isEmpty()) {
            DebugManager.getInstance().log("PlayerService", "No executors found for " + actionType);
            return CompletableFuture.failedFuture(new IllegalStateException("No executor found for action: " + actionType));
        }

        DebugManager.getInstance().log("PlayerService", "Candidates: " + candidates.size() + ". Starting chain.");
        return executeChain(candidates, 0, player, actionType, args);
    }

    private CompletableFuture<Void> executeChain(List<IActionExecutor> executors, int index, UnifiedPlayer player, String actionType, Object... args) {
        if (index >= executors.size()) {
            DebugManager.getInstance().log("PlayerService", "Chain exhausted. All failed.");
            return CompletableFuture.failedFuture(new IllegalStateException("All executors failed for action: " + actionType));
        }

        IActionExecutor current = executors.get(index);
        DebugManager.getInstance().log("PlayerService", "Trying executor: " + current.getClass().getSimpleName());
        return current.execute(player, actionType, args)
                .exceptionallyCompose(ex -> {
                    DebugManager.getInstance().log("PlayerService", "Executor " + current.getClass().getSimpleName() + " failed: " + ex.getMessage());
                    return executeChain(executors, index + 1, player, actionType, args);
                });
    }
    
    public void addListener(Consumer<List<UnifiedPlayer>> listener) {
        listeners.add(listener);
    }
    
    private void notifyListeners() {
        List<UnifiedPlayer> allPlayers = registry.getAll();
        for (Consumer<List<UnifiedPlayer>> listener : listeners) {
            listener.accept(allPlayers);
        }
    }
    
    public PlayerRegistry getRegistry() {
        return registry;
    }
}
