package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.data.managed.ManagedPlayer;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.data.player.IPlayerActionProvider;
import redxax.oxy.remotely.data.player.IPlayerDataProvider;
import restudio.rebase.backend.feature.PlayerManagementFeature;
import restudio.rescreen.ui.core.ScreenManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class BackendPlayerDataProvider implements IPlayerDataProvider, IPlayerActionProvider {
    private final PlayerManagementFeature feature;
    private final Map<UUID, ManagedPlayer> cache = new ConcurrentHashMap<>();
    private final List<Consumer<List<ManagedPlayer>>> listeners = new CopyOnWriteArrayList<>();

    public BackendPlayerDataProvider(PlayerManagementFeature feature) {
        this.feature = feature;
    }

    @Override
    public void initialize() {
        fullRefresh();
    }

    @Override
    public void shutdown() {
        listeners.clear();
        cache.clear();
    }

    @Override
    public CompletableFuture<Void> fullRefresh() {
        return feature.getOnlinePlayers().thenAccept(simplePlayers -> {
            cache.clear();
            for (PlayerManagementFeature.SimplePlayer sp : simplePlayers) {
                ManagedPlayer mp = new ManagedPlayer(sp.uuid(), sp.name());
                mp.isOnline = true;
                cache.put(sp.uuid(), mp);
            }
            notifyListeners();
        });
    }

    @Override
    public Map<UUID, ManagedPlayer> getCachedPlayers() {
        return cache;
    }

    @Override
    public void addUpdateListener(Consumer<List<ManagedPlayer>> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeUpdateListener(Consumer<List<ManagedPlayer>> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        List<ManagedPlayer> list = new ArrayList<>(cache.values());
        ScreenManager.getInstance().execute(() -> {
            for (Consumer<List<ManagedPlayer>> l : listeners) l.accept(list);
        });
    }

    @Override
    public CompletableFuture<Void> kickPlayer(ManagedPlayer player, String reason) {
        return feature.kick(player.uuid, reason);
    }

    @Override
    public CompletableFuture<Void> banPlayer(ManagedPlayer player, String reason, boolean ipBan) {
        return feature.ban(player.uuid, reason, ipBan);
    }

    @Override
    public CompletableFuture<Void> unbanPlayer(ManagedPlayer player) {
        return feature.unban(player.uuid);
    }

    @Override
    public CompletableFuture<Void> toggleOp(ManagedPlayer player) {
        return feature.setOp(player.uuid, !player.isOp);
    }

    @Override
    public CompletableFuture<Void> runCustomCommand(ManagedPlayer player, String commandTemplate) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Backend does not support arbitrary command execution."));
    }

    @Override
    public CompletableFuture<List<PlayerAction>> getCustomActions() {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}
