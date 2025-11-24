package redxax.oxy.remotely.data.player.msmp;

import redxax.oxy.remotely.data.managed.ManagedPlayer;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.data.player.IPlayerActionProvider;
import redxax.oxy.remotely.data.player.IPlayerDataProvider;
import restudio.rebase.msmp.IMSMPApi;
import restudio.rebase.msmp.MSMPManager;
import restudio.rebase.msmp.dto.Player;
import restudio.rescreen.debug.DebugManager;
import restudio.rescreen.ui.core.ScreenManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class MsmpPlayerProvider implements IPlayerDataProvider, IPlayerActionProvider {

    private final MSMPManager msmpManager;
    private final Map<UUID, ManagedPlayer> playerCache = new ConcurrentHashMap<>();
    private final List<Consumer<List<ManagedPlayer>>> updateListeners = new CopyOnWriteArrayList<>();
    private boolean initialized = false;

    public MsmpPlayerProvider(MSMPManager msmpManager) {
        this.msmpManager = msmpManager;
    }

    public boolean isConnectionActive() {
        IMSMPApi api = msmpManager.getApi();
        return api != null && api.isConnected();
    }

    @Override
    public void initialize() {
        if (initialized) return;
        initialized = true;
        msmpManager.setOnPlayersChange(this::onMsmpPlayersUpdate);
        if (msmpManager.isConnected) {
            fullRefresh();
        } else {
            msmpManager.connect();
        }
    }

    @Override
    public void shutdown() {
        initialized = false;
        msmpManager.setOnPlayersChange(null);
        updateListeners.clear();
        playerCache.clear();
    }

    @Override
    public CompletableFuture<Void> fullRefresh() {
        IMSMPApi api = msmpManager.getApi();
        if (api == null || !api.isConnected()) {
            msmpManager.connect();
            return CompletableFuture.completedFuture(null);
        }
        return api.getPlayers().thenAccept(this::updateCacheFromMsmp);
    }

    @Override
    public Map<UUID, ManagedPlayer> getCachedPlayers() {
        return playerCache;
    }

    @Override
    public void addUpdateListener(Consumer<List<ManagedPlayer>> listener) {
        updateListeners.add(listener);
    }

    @Override
    public void removeUpdateListener(Consumer<List<ManagedPlayer>> listener) {
        updateListeners.remove(listener);
    }

    private void onMsmpPlayersUpdate(List<Player> msmpPlayers) {
        updateCacheFromMsmp(msmpPlayers);
    }

    private void updateCacheFromMsmp(List<Player> msmpPlayers) {
        if (msmpPlayers == null) return;
        Set<UUID> currentUuids = new HashSet<>();
        for (Player mp : msmpPlayers) {
            currentUuids.add(mp.uuid);
            ManagedPlayer managed = playerCache.computeIfAbsent(mp.uuid, u -> new ManagedPlayer(u, mp.name));
            managed.name = mp.name;
            managed.isOnline = true;
            managed.ping = mp.ping;
            managed.isOp = mp.isOperator;
            managed.address = mp.address;
            managed.lastSeen = System.currentTimeMillis();
        }
        for (ManagedPlayer cached : playerCache.values()) {
            if (!currentUuids.contains(cached.uuid)) {
                cached.isOnline = false;
                cached.ping = -1;
            }
        }
        notifyListeners();
    }

    private void notifyListeners() {
        List<ManagedPlayer> snapshot = new ArrayList<>(playerCache.values());
        ScreenManager.getInstance().execute(() -> {
            for (Consumer<List<ManagedPlayer>> listener : updateListeners) {
                listener.accept(snapshot);
            }
        });
    }

    private void logAction(String action, String target) {
        DebugManager.getInstance().recordEvent(msmpManager.getInstance().getInstanceId(), "Player Action", "MSMP", String.format("%s %s", action, target));
    }

    @Override
    public CompletableFuture<Void> kickPlayer(ManagedPlayer player, String reason) {
        IMSMPApi api = msmpManager.getApi();
        if (api == null) return failedFuture("Not connected to MSMP");
        logAction("Kicked", player.name);
        return api.kickPlayer(player.uuid.toString(), reason).thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Void> banPlayer(ManagedPlayer player, String reason, boolean ipBan) {
        IMSMPApi api = msmpManager.getApi();
        if (api == null) return failedFuture("Not connected to MSMP");
        if (ipBan && player.address != null) {
            String ip = player.address.split(":")[0].replace("/", "");
            logAction("IP Banned", player.name + " (" + ip + ")");
            return api.banIp(ip, player.uuid.toString(), reason, null);
        } else {
            logAction("Banned", player.name);
            return api.banPlayer(player.uuid.toString(), player.name, reason, null);
        }
    }

    @Override
    public CompletableFuture<Void> unbanPlayer(ManagedPlayer player) {
        IMSMPApi api = msmpManager.getApi();
        if (api == null) return failedFuture("Not connected to MSMP");
        logAction("Unbanned", player.name);
        return api.unbanPlayer(player.uuid.toString());
    }

    @Override
    public CompletableFuture<Void> toggleOp(ManagedPlayer player) {
        IMSMPApi api = msmpManager.getApi();
        if (api == null) return failedFuture("Not connected to MSMP");
        if (player.isOp) {
            logAction("De-opped", player.name);
            return api.deopPlayer(player.uuid.toString());
        }
        else {
            logAction("Opped", player.name);
            return api.opPlayer(player.uuid.toString(), 4);
        }
    }

    @Override
    public CompletableFuture<Void> runCustomCommand(ManagedPlayer player, String commandTemplate) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("MSMP does not support arbitrary command execution."));
    }

    @Override
    public CompletableFuture<List<PlayerAction>> getCustomActions() {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    private <T> CompletableFuture<T> failedFuture(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }
}
