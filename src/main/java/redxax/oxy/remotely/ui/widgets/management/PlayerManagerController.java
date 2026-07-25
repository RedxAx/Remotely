package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsClient;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.data.managed.PlayerSession;
import redxax.oxy.remotely.data.player.IPlayerHistoryProvider;
import redxax.oxy.remotely.data.player.PlayerService;
import redxax.oxy.remotely.data.player.management.PlayerManagementService;
import redxax.oxy.remotely.data.player.management.PlayerOperation;
import redxax.oxy.remotely.data.player.management.PlayerOperationResult;
import redxax.oxy.remotely.data.player.management.PlayerOperationRouter;
import redxax.oxy.remotely.data.playerdata.PlayerDataManager;
import redxax.oxy.remotely.data.playerdata.PlayerItem;
import redxax.oxy.remotely.data.playerdata.sources.RconPlayerDataSource;
import redxax.oxy.remotely.data.playerdata.sources.WorldPlayerDataSource;
import redxax.oxy.remotely.data.player.action.BackendActionExecutor;
import redxax.oxy.remotely.data.player.action.MsmpActionExecutor;
import redxax.oxy.remotely.data.player.action.StandardActionExecutor;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.data.player.source.BackendPlayerSource;
import redxax.oxy.remotely.data.player.source.MsmpPlayerSource;
import redxax.oxy.remotely.data.player.source.StandardFileSource;
import redxax.oxy.remotely.data.player.source.StandardLogSource;
import redxax.oxy.remotely.data.player.standard.StandardPlayerHistoryProvider;
import redxax.oxy.remotely.session.StreamDataParser;
import redxax.oxy.remotely.ui.server.containers.PlayersContainer;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.feature.DataStreamFeature;
import restudio.rebase.backend.feature.PlayerManagementFeature;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.Optional;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public class PlayerManagerController {

    public record PlayerSnapshot(List<UnifiedPlayer> players, boolean baseline) {
    }

    private static final Map<String, PlayerManagerController> registry = new HashMap<>();

    private final Instance instance;
    private PlayersContainer container;
    private PlayerService playerService;
    private PlayerDataManager playerDataManager;
    private PlayerManagementService playerManagementService;

    private IPlayerHistoryProvider historyProvider;

    private TerminalWidget terminalWidget;
    private boolean isInitialized = false;
    private boolean listenerRegistered = false;
    private StandardFileSource standardFileSource;
    private volatile List<PlayerAction> playerActions = List.of();
    private final Gson gson = new Gson();
    private int uiToken = 0;
    private volatile int playerActionsLoadToken = 0;
    private final Map<UUID, Integer> reSyncWatchCounts = new HashMap<>();
    private long reSyncWatchGeneration;
    private final Map<UUID, UnifiedPlayer> operationPlayers = new ConcurrentHashMap<>();
    private final PlayerOperationRouter operationRouter = new PlayerOperationRouter();
    private final List<Consumer<PlayerSnapshot>> playerListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean backgroundDataStreamActive = new AtomicBoolean();
    private volatile boolean playerBaselineActive;
    private volatile boolean nextPlayerSnapshotBaseline;

    private PlayerManagerController(Instance instance) {
        this.instance = instance;
        operationRouter.register("resync", 200, operation -> operation.type() == PlayerOperation.Type.INVENTORY_EDIT && operationPlayers.containsKey(operation.playerId())
                && canEditPlayerInventory(operationPlayers.get(operation.playerId())) && operation.baseRevision() >= 0L, this::executeReSyncOperation);
        operationRouter.register("player-service", 100, operation -> operation.type() != PlayerOperation.Type.INVENTORY_EDIT && isServerRunning()
                && playerService != null && playerService.supportsAction(actionName(operation.type())), this::executePlayerServiceOperation);
    }

    public static PlayerManagerController getOrCreate(Instance instance) {
        synchronized (registry) {
            PlayerManagerController c = registry.get(instance.getInstanceId());
            if (c == null) {
                c = new PlayerManagerController(instance);
                registry.put(instance.getInstanceId(), c);
            }
            return c;
        }
    }

    public void reloadProviders() {
        uiToken++;
        ensureServiceInitialized();
        ensureListenerRegistered();
        if (container != null && playerService != null) container.syncUi(playerService.getRegistry().getAll());
    }

    private void ensureServiceInitialized() {
        if (this.playerService != null && this.playerDataManager != null && this.historyProvider != null) return;
        initializeService(false);
    }

    private void reinitializeService() {
        uiToken++;
        initializeService(true);
        ensureListenerRegistered();
        if (container != null && playerService != null) container.syncUi(playerService.getRegistry().getAll());
    }

    private void ensureListenerRegistered() {
        if (playerService == null || listenerRegistered) return;
        playerService.addListener(snapshot -> {
            int token = uiToken;
            boolean baseline = nextPlayerSnapshotBaseline;
            nextPlayerSnapshotBaseline = false;
            ScreenManager.getInstance().execute(() -> {
                if (container != null && token == uiToken) container.syncUi(snapshot);
                List<UnifiedPlayer> stableSnapshot = snapshot == null ? List.of() : List.copyOf(snapshot);
                PlayerSnapshot playerSnapshot = new PlayerSnapshot(stableSnapshot, baseline);
                for (Consumer<PlayerSnapshot> listener : playerListeners) {
                    try {
                        listener.accept(playerSnapshot);
                    } catch (Exception ignored) {
                    }
                }
            });
        });
        listenerRegistered = true;
    }

    private void initializeService(boolean shutdownExisting) {
        if (shutdownExisting) {
            clearReSyncWatches();
            if (this.playerService != null) this.playerService.shutdown();
            if (this.historyProvider != null) this.historyProvider.shutdown();
            if (this.playerManagementService != null) this.playerManagementService.shutdown();
        }

        this.playerService = new PlayerService();
        this.playerDataManager = new PlayerDataManager();
        listenerRegistered = false;
        TerminalWidget tw = this.terminalWidget;
        RebaseAPI api = RebaseApiFactory.get(instance);
        Properties settings = instance.getSettings();

        StandardPlayerHistoryProvider standardHistory = new StandardPlayerHistoryProvider(instance, api, tw, Path.of(instance.getPath()), name -> playerService.getRegistry().getAll().stream()
            .filter(p -> p.getName().equalsIgnoreCase(name))
            .map(UnifiedPlayer::getUuid)
            .findFirst().orElse(null));
        this.historyProvider = standardHistory;

        boolean msmpEnabled = Boolean.parseBoolean(settings.getProperty("provider.msmp.enabled", "true"));
        if (msmpEnabled) {
            playerService.registerSource(new MsmpPlayerSource(instance.getMSMPManager()));
            playerService.registerExecutor(new MsmpActionExecutor(instance.getMSMPManager()));
        }

        boolean backendEnabled = Boolean.parseBoolean(settings.getProperty("provider.backend.enabled", "true"));
        if (backendEnabled && instance.getBackend() != null) {
            Optional<PlayerManagementFeature> feat = instance.getBackend().getFeature(PlayerManagementFeature.class);
            if (feat.isPresent() && feat.get().supportsOnlinePlayers()) {
                BackendPlayerSource backendPlayerSource = new BackendPlayerSource(feat.get());
                playerService.registerSource(backendPlayerSource);
                playerService.registerExecutor(new BackendActionExecutor(feat.get()));
            }
        }

        boolean standardEnabled = Boolean.parseBoolean(settings.getProperty("provider.standard.enabled", "true"));
        if (standardEnabled) {
            standardFileSource = new StandardFileSource(instance, api);
            playerService.registerSource(standardFileSource);
            playerService.registerSource(new StandardLogSource(instance, standardHistory));
            playerService.registerExecutor(new StandardActionExecutor(tw));
        }

        playerDataManager.registerSource(new WorldPlayerDataSource(instance, api));
        boolean rconEnabled = Boolean.parseBoolean(instance.getServerProperties().getProperty("enable-rcon", "false"));
        if (rconEnabled) {
            playerDataManager.registerSource(new RconPlayerDataSource(instance, false));
        }

        if (this.historyProvider != null) this.historyProvider.initialize();
        this.playerManagementService = new PlayerManagementService(playerDataManager, historyProvider, this::getPlayerDossier, this::requestPlayerDossier, this::isReSyncPlayerManagementAvailable);
        loadActionsAsync();
    }

    private synchronized void clearReSyncWatches() {
        FlowManager flowManager = RemotelyClient.INSTANCE != null ? RemotelyClient.INSTANCE.getFlowManager() : null;
        String serverId = getReSyncServerId();
        if (flowManager != null && serverId != null && flowManager.isFlowClientConnected(serverId)) {
            for (UUID playerId : reSyncWatchCounts.keySet()) flowManager.ensureFlowClient(serverId).unwatchPlayer(playerId);
        }
        reSyncWatchCounts.clear();
        reSyncWatchGeneration++;
    }

    private void loadActionsAsync() {
        int loadToken = ++playerActionsLoadToken;
        Path actionsPath = Path.of(instance.getPath(), "Remotely", "player-actions.json");
        RebaseApiFactory.get(instance).readFile(actionsPath).thenAccept(content -> {
            List<PlayerAction> loadedActions = List.of();
            if (content != null && !content.isEmpty()) {
                try {
                    List<PlayerAction> loaded = gson.fromJson(content, new TypeToken<List<PlayerAction>>(){}.getType());
                    loadedActions = loaded != null ? List.copyOf(loaded) : List.of();
                } catch (Exception e) {
                    loadedActions = List.of();
                }
            }

            applyLoadedActions(loadToken, loadedActions);
        }).exceptionally(e -> {
            applyLoadedActions(loadToken, List.of());
            return null;
        });
    }

    private void applyLoadedActions(int loadToken, List<PlayerAction> loadedActions) {
        if (loadToken != playerActionsLoadToken) {
            return;
        }
        this.playerActions = loadedActions;
        ScreenManager.getInstance().execute(() -> {
            if (loadToken != playerActionsLoadToken) {
                return;
            }
            if (container != null && playerService != null) {
                container.syncUi(playerService.getRegistry().getAll());
            }
        });
    }

    public void setUiBindings(PlayersContainer container, TerminalWidget terminalWidget) {
        this.container = container;
        boolean terminalChanged = this.terminalWidget != terminalWidget;
        this.terminalWidget = terminalWidget;

        if (!isInitialized || terminalChanged) {
            if (terminalChanged && playerService != null) {
                reinitializeService();
            } else {
                reloadProviders();
            }
            isInitialized = true;
        } else {
             if (container != null && playerService != null) container.syncUi(playerService.getRegistry().getAll());
        }
    }

    public void handleFileUpdate(String fileName, String content) {
        if (standardFileSource != null) {
            standardFileSource.updateFromContent(fileName, content);
        }
    }

    public void fullRefresh() {
        if (Boolean.parseBoolean(instance.getSettings().getProperty("provider.msmp.enabled", "true"))) {
            if (!instance.getMSMPManager().isConnected) {
                instance.getMSMPManager().connect();
            }
        }
        playerService.refreshSources();
    }

    public ReSyncLuckPermsClient getLuckPermsClient() {
        FlowManager flowManager = RemotelyClient.INSTANCE != null ? RemotelyClient.INSTANCE.getFlowManager() : null;
        String serverId = getReSyncServerId();
        if (flowManager == null || serverId == null || serverId.isBlank() || !flowManager.isFlowClientConnected(serverId)) {
            return null;
        }
        return flowManager.ensureFlowClient(serverId).luckPerms();
    }

    public List<PlayerAction> getPlayerActions() {
        return new ArrayList<>(playerActions);
    }

    public int getOnlinePlayerCount() {
        if (playerService == null) {
            return 0;
        }
        return (int) playerService.getRegistry().getAll().stream().filter(UnifiedPlayer::isOnline).count();
    }

    public List<UnifiedPlayer> getOnlinePlayers() {
        if (playerService == null) {
            return List.of();
        }
        return playerService.getRegistry().getAll().stream().filter(UnifiedPlayer::isOnline).toList();
    }

    public void addPlayerListener(Consumer<PlayerSnapshot> listener) {
        if (listener == null || playerListeners.contains(listener)) return;
        playerListeners.add(listener);
        ensureServiceInitialized();
        ensureListenerRegistered();
        listener.accept(new PlayerSnapshot(List.copyOf(playerService.getRegistry().getAll()), true));
        playerService.refreshSources();
        ensureBackgroundDataStream();
    }

    public void removePlayerListener(Consumer<PlayerSnapshot> listener) {
        playerListeners.remove(listener);
        if (playerListeners.isEmpty()) stopBackgroundDataStream();
    }

    public void refreshPlayerSnapshots() {
        ensureServiceInitialized();
        ensureListenerRegistered();
        playerService.refreshSources();
        ensureBackgroundDataStream();
    }

    public void beginPlayerBaseline() {
        ensureServiceInitialized();
        if (playerBaselineActive) return;
        playerBaselineActive = true;
        playerService.beginNotificationBatch();
    }

    public void endPlayerBaseline() {
        if (!playerBaselineActive || playerService == null) return;
        playerBaselineActive = false;
        nextPlayerSnapshotBaseline = true;
        playerService.endNotificationBatch();
    }

    private void ensureBackgroundDataStream() {
        if (playerListeners.isEmpty() || !isServerRunning() || !backgroundDataStreamActive.compareAndSet(false, true)) return;
        if (instance.getBackend() == null) {
            backgroundDataStreamActive.set(false);
            return;
        }
        Optional<DataStreamFeature> feature = instance.getBackend().getFeature(DataStreamFeature.class);
        if (feature.isEmpty()) {
            backgroundDataStreamActive.set(false);
            return;
        }
        String root = instance.getPath() == null ? "" : instance.getPath().replace('\\', '/');
        String prefix = root.isBlank() || root.endsWith("/") ? root : root + "/";
        String logPath = prefix + "logs/latest.log";
        List<String> preLoadFiles = List.of("ops.json", "banned-players.json", "banned-ips.json", "whitelist.json", "usercache.json").stream()
                .map(file -> prefix + file).toList();
        feature.get().streamData(logPath, preLoadFiles, new StreamDataParser(this))
                .whenComplete((ignored, throwable) -> {
                    endPlayerBaseline();
                    backgroundDataStreamActive.set(false);
                });
    }

    private void stopBackgroundDataStream() {
        if (!backgroundDataStreamActive.compareAndSet(true, false) || instance.getBackend() == null) return;
        endPlayerBaseline();
        instance.getBackend().getFeature(DataStreamFeature.class).ifPresent(DataStreamFeature::stopStream);
    }

    public void refreshPlayerActions() {
        loadActionsAsync();
    }

    public IPlayerHistoryProvider getHistoryProvider() { return historyProvider; }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public PlayerManagementService getPlayerManagementService() {
        ensureServiceInitialized();
        return playerManagementService;
    }

    public Instance getInstance() {
        return instance;
    }

    public String getReSyncServerId() {
        BackendConfig backendConfig = instance.getBackendConfig();
        if (backendConfig != null && backendConfig.credentials != null) {
            String identifier = backendConfig.credentials.get("identifier");
            if (identifier != null && !identifier.isBlank()) {
                return identifier;
            }
        }
        return instance.getInstanceId();
    }

    public void requestPlayerDossier(UUID playerId) {
        if (playerId == null) {
            return;
        }
        FlowManager flowManager = RemotelyClient.INSTANCE != null ? RemotelyClient.INSTANCE.getFlowManager() : null;
        String serverId = getReSyncServerId();
        if (flowManager == null || serverId == null || serverId.isBlank()) {
            return;
        }
        if (!flowManager.isFlowClientConnected(serverId)) {
            if (flowManager.getFlowAvailabilityIssue(serverId, null) != null) return;
            flowManager.ensureFlowClientForStartup(serverId, null, false);
            return;
        }
        flowManager.ensureFlowClient(serverId).watchPlayer(playerId);
        if (flowManager.getPlayerControlCapabilities(serverId) == null) flowManager.ensureFlowClient(serverId).requestPlayerControlCapabilities();
        flowManager.requestPlayerDossier(serverId, playerId);
    }

    public boolean isReSyncPlayerManagementAvailable() {
        FlowManager flowManager = RemotelyClient.INSTANCE != null ? RemotelyClient.INSTANCE.getFlowManager() : null;
        String serverId = getReSyncServerId();
        return flowManager != null && serverId != null && !serverId.isBlank() && flowManager.isFlowClientConnected(serverId);
    }

    public synchronized AutoCloseable watchPlayer(UUID playerId) {
        if (playerId == null) return () -> {};
        long generation = reSyncWatchGeneration;
        int count = reSyncWatchCounts.getOrDefault(playerId, 0);
        reSyncWatchCounts.put(playerId, count + 1);
        if (isReSyncPlayerManagementAvailable()) RemotelyClient.INSTANCE.getFlowManager().watchPlayer(getReSyncServerId(), playerId);
        return () -> {
            if (unwatchPlayer(playerId, generation)) {
                FlowManager flowManager = RemotelyClient.INSTANCE != null ? RemotelyClient.INSTANCE.getFlowManager() : null;
                if (flowManager != null && flowManager.isFlowClientConnected(getReSyncServerId())) flowManager.ensureFlowClient(getReSyncServerId()).unwatchPlayer(playerId);
            }
        };
    }

    private synchronized boolean unwatchPlayer(UUID playerId, long generation) {
        if (generation != reSyncWatchGeneration) return false;
        int next = reSyncWatchCounts.getOrDefault(playerId, 1) - 1;
        if (next > 0) {
            reSyncWatchCounts.put(playerId, next);
            return false;
        }
        reSyncWatchCounts.remove(playerId);
        return true;
    }

    public boolean canEditPlayerInventory(UnifiedPlayer player) {
        if (player == null || !player.isOnline() || !isReSyncPlayerManagementAvailable()) return false;
        if (getInventoryRevision(player.getUuid()) < 0L) return false;
        PlayerDossier dossier = getPlayerDossier(player.getUuid());
        if (dossier == null || dossier.getFacets().get("playerData") == null) return false;
        FlowManager flowManager = RemotelyClient.INSTANCE.getFlowManager();
        JsonObject capabilities = flowManager.getPlayerControlCapabilities(getReSyncServerId());
        if (capabilities == null || !capabilities.has("operations") || !capabilities.get("operations").isJsonArray()) return false;
        for (var operation : capabilities.getAsJsonArray("operations")) {
            if ("onlineInventoryEdit".equals(operation.getAsString())) return true;
        }
        return false;
    }

    public CompletableFuture<Boolean> editPlayerInventory(UnifiedPlayer player, Map<String, PlayerItem> edits, long baseRevision) {
        if (!canEditPlayerInventory(player) || edits == null || edits.isEmpty() || baseRevision < 0L) return CompletableFuture.completedFuture(false);
        List<PlayerOperation.InventoryEdit> inventoryEdits = edits.entrySet().stream().map(entry -> new PlayerOperation.InventoryEdit(entry.getKey(), entry.getValue())).toList();
        PlayerOperation operation = new PlayerOperation(UUID.randomUUID().toString(), PlayerOperation.Type.INVENTORY_EDIT, player.getUuid(), "", false, baseRevision, inventoryEdits);
        operationPlayers.put(player.getUuid(), player);
        return operationRouter.execute(operation).thenApply(PlayerOperationResult::success).whenComplete((success, error) -> operationPlayers.remove(player.getUuid(), player));
    }

    private CompletableFuture<Boolean> editPlayerInventoryDirect(UnifiedPlayer player, Map<String, PlayerItem> edits, long baseRevision) {
        List<Map<String, Object>> payloadEdits = new ArrayList<>();
        for (Map.Entry<String, PlayerItem> entry : edits.entrySet()) {
            PlayerItem item = entry.getValue();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("slot", entry.getKey());
            payload.put("itemId", item != null ? item.id() : "minecraft:air");
            payload.put("count", item != null ? item.count() : 0);
            payload.put("item", item != null ? item.tag() : Map.of());
            payloadEdits.add(payload);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("baseInventoryRevision", baseRevision);
        payload.put("edits", payloadEdits);
        FlowManager flowManager = RemotelyClient.INSTANCE.getFlowManager();
        return flowManager.requestPlayerControl(getReSyncServerId(), "inventoryEditBatch", player.getUuid(), payload).thenApply(response -> response.has("success") && response.get("success").getAsBoolean()).whenComplete((success, error) -> {
            if (playerManagementService != null) playerManagementService.refresh(player, true);
        });
    }

    public long getInventoryRevision(UUID playerId) {
        PlayerDossier dossier = getPlayerDossier(playerId);
        if (dossier == null || dossier.getFacets().get("playerData") == null) return -1L;
        Object revision = dossier.getFacets().get("playerData").getData().get("inventoryRevision");
        return revision instanceof Number number ? number.longValue() : -1L;
    }

    public PlayerDossier getPlayerDossier(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        FlowManager flowManager = RemotelyClient.INSTANCE != null ? RemotelyClient.INSTANCE.getFlowManager() : null;
        String serverId = getReSyncServerId();
        if (flowManager == null || serverId == null || serverId.isBlank()) {
            return null;
        }
        return flowManager.getPlayerDossier(serverId, playerId);
    }

    public void kickPlayer(UnifiedPlayer player, String reason) {
        executeOperation(player, PlayerOperation.Type.KICK, reason, false).whenComplete((v, e) -> {
            if (e != null) {
                ScreenManager.getInstance().execute(() -> new Notification("Error", e.getMessage(), Notification.Type.ERROR));
            } else {
                playerService.refreshSources();
            }
        });
    }

    public void banPlayer(UnifiedPlayer player, String reason, boolean ipBan) {
        executeOperation(player, PlayerOperation.Type.BAN, reason, ipBan).whenComplete((v, e) -> {
            if (e != null) {
                ScreenManager.getInstance().execute(() -> new Notification("Error", e.getMessage(), Notification.Type.ERROR));
            } else {
                playerService.refreshSources();
            }
        });
    }

    public void unbanPlayer(UnifiedPlayer player) {
        executeOperation(player, PlayerOperation.Type.UNBAN, "", false).whenComplete((v, e) -> {
            if (e != null) {
                ScreenManager.getInstance().execute(() -> new Notification("Error", e.getMessage(), Notification.Type.ERROR));
            } else {
                playerService.refreshSources();
            }
        });
    }

    public void toggleOp(UnifiedPlayer player) {
        String action = player.isOp() ? "deop" : "op";
        executeOperation(player, player.isOp() ? PlayerOperation.Type.DEOP : PlayerOperation.Type.OP, "", false).whenComplete((v, e) -> {
            if (e != null) {
                ScreenManager.getInstance().execute(() -> new Notification("Error", e.getMessage(), Notification.Type.ERROR));
            } else {
                playerService.refreshSources();
            }
        });
    }

    public void runCustomCommand(UnifiedPlayer player, String commandTemplate) {
        if (commandTemplate == null || commandTemplate.isEmpty()) return;
        String command = commandTemplate
            .replace("$name", player.getName())
            .replace("$uuid", player.getUuid().toString());

        executeOperation(player, PlayerOperation.Type.COMMAND, command, false).whenComplete((v, e) -> {
            if (e != null) {
                ScreenManager.getInstance().execute(() -> new Notification("Error", e.getMessage(), Notification.Type.ERROR));
            } else {
                playerService.refreshSources();
            }
        });
    }

    public boolean isServerRunning() {
        return instance.getState() == InstanceState.RUNNING || (instance.getMSMPManager() != null && instance.getMSMPManager().isConnected);
    }

    public boolean canExecutePlayerAction(String actionType) {
        PlayerOperation.Type type = operationType(actionType);
        return type != null && operationRouter.supports(new PlayerOperation("capability", type, null, "", false, 0L, List.of()));
    }

    private CompletableFuture<PlayerOperationResult> executeOperation(UnifiedPlayer player, PlayerOperation.Type type, String text, boolean flag) {
        PlayerOperation operation = new PlayerOperation(UUID.randomUUID().toString(), type, player.getUuid(), text, flag, 0L, List.of());
        operationPlayers.put(player.getUuid(), player);
        return operationRouter.execute(operation).thenCompose(result -> result.success() ? CompletableFuture.completedFuture(result)
                : CompletableFuture.failedFuture(new IllegalStateException(result.reason()))).whenComplete((result, error) -> operationPlayers.remove(player.getUuid(), player));
    }

    private CompletableFuture<PlayerOperationResult> executePlayerServiceOperation(PlayerOperation operation) {
        UnifiedPlayer target = operationPlayers.get(operation.playerId());
        if (target == null) return CompletableFuture.completedFuture(PlayerOperationResult.failed(operation.operationId(), "Player Unavailable"));
        CompletableFuture<Void> execution = switch (operation.type()) {
            case KICK, COMMAND -> playerService.executeAction(target, actionName(operation.type()), operation.text());
            case BAN -> playerService.executeAction(target, actionName(operation.type()), operation.text(), operation.flag());
            case UNBAN, OP, DEOP -> playerService.executeAction(target, actionName(operation.type()));
            case INVENTORY_EDIT -> CompletableFuture.failedFuture(new IllegalStateException("Invalid Operation Route"));
        };
        return execution.thenApply(ignored -> new PlayerOperationResult(operation.operationId(), true, "", 0L, null));
    }

    private CompletableFuture<PlayerOperationResult> executeReSyncOperation(PlayerOperation operation) {
        UnifiedPlayer target = operationPlayers.get(operation.playerId());
        if (target == null) return CompletableFuture.completedFuture(PlayerOperationResult.failed(operation.operationId(), "Player Unavailable"));
        Map<String, PlayerItem> edits = new LinkedHashMap<>();
        for (PlayerOperation.InventoryEdit edit : operation.inventoryEdits()) edits.put(edit.slot(), edit.item());
        return editPlayerInventoryDirect(target, edits, operation.baseRevision()).thenApply(success -> success
                ? new PlayerOperationResult(operation.operationId(), true, "", getInventoryRevision(operation.playerId()), null)
                : PlayerOperationResult.failed(operation.operationId(), "Inventory Edit Rejected"));
    }

    private PlayerOperation.Type operationType(String action) {
        if (action == null) return null;
        try {
            return PlayerOperation.Type.valueOf(action.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String actionName(PlayerOperation.Type type) {
        return type == null ? "" : type.name().toLowerCase(Locale.ROOT);
    }

    public CompletableFuture<List<PlayerSession>> getPlayerSessions(UUID uuid) {
        if (historyProvider == null) return CompletableFuture.completedFuture(new ArrayList<>());
        return historyProvider.getSessions(uuid);
    }

}
