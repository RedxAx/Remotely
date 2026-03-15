package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.data.integrations.luckperms.LuckPermsService;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.data.managed.PlayerSession;
import redxax.oxy.remotely.data.player.IPlayerHistoryProvider;
import redxax.oxy.remotely.data.player.PlayerService;
import redxax.oxy.remotely.data.playerdata.PlayerDataManager;
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
import redxax.oxy.remotely.ui.integrations.luckperms.LuckPermsDashboardScreen;
import redxax.oxy.remotely.ui.server.containers.PlayersContainer;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.feature.PlayerManagementFeature;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class PlayerManagerController {

    private static final Map<String, PlayerManagerController> registry = new HashMap<>();

    private final Instance instance;
    private PlayersContainer container;
    private PlayerService playerService;
    private PlayerDataManager playerDataManager;

    private IPlayerHistoryProvider historyProvider;

    private LuckPermsService luckPermsService;
    private TerminalWidget terminalWidget;
    private boolean isInitialized = false;
    private boolean listenerRegistered = false;
    private StandardFileSource standardFileSource;
    private volatile List<PlayerAction> playerActions = List.of();
    private final Gson gson = new Gson();
    private int uiToken = 0;
    private volatile int playerActionsLoadToken = 0;

    private PlayerManagerController(Instance instance) {
        this.instance = instance;
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
            ScreenManager.getInstance().execute(() -> {
                if (container != null && token == uiToken) container.syncUi(snapshot);
            });
        });
        listenerRegistered = true;
    }

    private void initializeService(boolean shutdownExisting) {
        if (shutdownExisting) {
            if (this.playerService != null) this.playerService.shutdown();
            if (this.historyProvider != null) this.historyProvider.shutdown();
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
            if (feat.isPresent()) {
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

        this.luckPermsService = new LuckPermsService(api, Path.of(instance.getPath()));
        this.luckPermsService.initialize();
        if (this.historyProvider != null) this.historyProvider.initialize();
        loadActionsAsync();
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
            if (terminalChanged && isInitialized) {
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

    public LuckPermsService getLuckPermsService() { return luckPermsService; }

    public List<PlayerAction> getPlayerActions() {
        return new ArrayList<>(playerActions);
    }

    public void refreshPlayerActions() {
        loadActionsAsync();
    }

    public IPlayerHistoryProvider getHistoryProvider() { return historyProvider; }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
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
        flowManager.requestPlayerDossier(serverId, playerId);
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
        playerService.executeAction(player, "kick", reason).whenComplete((v, e) -> {
            if (e != null) {
                ScreenManager.getInstance().execute(() -> new Notification("Error", e.getMessage(), Notification.Type.ERROR));
            } else {
                playerService.refreshSources();
            }
        });
    }

    public void banPlayer(UnifiedPlayer player, String reason, boolean ipBan) {
        playerService.executeAction(player, "ban", reason, ipBan).whenComplete((v, e) -> {
            if (e != null) {
                ScreenManager.getInstance().execute(() -> new Notification("Error", e.getMessage(), Notification.Type.ERROR));
            } else {
                playerService.refreshSources();
            }
        });
    }

    public void unbanPlayer(UnifiedPlayer player) {
        playerService.executeAction(player, "unban").whenComplete((v, e) -> {
            if (e != null) {
                ScreenManager.getInstance().execute(() -> new Notification("Error", e.getMessage(), Notification.Type.ERROR));
            } else {
                playerService.refreshSources();
            }
        });
    }

    public void toggleOp(UnifiedPlayer player) {
        String action = player.isOp() ? "deop" : "op";
        playerService.executeAction(player, action).whenComplete((v, e) -> {
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

        playerService.executeAction(player, "command", command).whenComplete((v, e) -> {
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

    public CompletableFuture<List<PlayerSession>> getPlayerSessions(UUID uuid) {
        if (historyProvider == null) return CompletableFuture.completedFuture(new ArrayList<>());
        return historyProvider.getSessions(uuid);
    }

    public void openLuckPermsSettings() {
        ScreenManager.getInstance().getCurrentScreen().addDrawableChild(new LuckPermsSettingsPopup(luckPermsService, this::fullRefresh));
    }

    public void openLuckPermsDashboard() {
        if (luckPermsService.isEnabled()) {
            ScreenManager.getInstance().setScreen(new LuckPermsDashboardScreen(ScreenManager.getInstance().getCurrentScreen(), luckPermsService));
        } else {
            new Notification("Error", "LuckPerms integration is disabled or not available.", Notification.Type.ERROR);
        }
    }
}
