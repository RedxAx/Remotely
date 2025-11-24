package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.data.integrations.luckperms.LuckPermsService;
import redxax.oxy.remotely.data.managed.ManagedPlayer;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.data.managed.PlayerSession;
import redxax.oxy.remotely.data.managed.SessionEventType;
import redxax.oxy.remotely.data.player.IPlayerActionProvider;
import redxax.oxy.remotely.data.player.IPlayerDataProvider;
import redxax.oxy.remotely.data.player.IPlayerHistoryCollector;
import redxax.oxy.remotely.data.player.IPlayerHistoryProvider;
import redxax.oxy.remotely.data.player.msmp.MsmpPlayerProvider;
import redxax.oxy.remotely.data.player.standard.StandardPlayerActionProvider;
import redxax.oxy.remotely.data.player.standard.StandardPlayerDataProvider;
import redxax.oxy.remotely.data.player.standard.StandardPlayerHistoryProvider;
import redxax.oxy.remotely.ui.integrations.luckperms.LuckPermsDashboardScreen;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.backend.feature.PlayerManagementFeature;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PlayerManagerController {

    private static final Map<String, PlayerManagerController> registry = new HashMap<>();

    private final Instance instance;
    private Container container;

    private final CompositePlayerDataProvider compositeDataProvider = new CompositePlayerDataProvider();

    private List<IPlayerActionProvider> actionProviderChain;
    private IPlayerHistoryProvider historyProvider;
    private IPlayerHistoryCollector historyCollector;
    private LuckPermsService luckPermsService;
    private TerminalWidget terminalWidget;
    private boolean isInitialized = false;

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
        if (actionProviderChain != null) for (IPlayerActionProvider p : actionProviderChain) p.shutdown();
        if (historyProvider != null) historyProvider.shutdown();
        compositeDataProvider.shutdown();

        initializeProviders();

        if (container != null) {
            compositeDataProvider.addUpdateListener(players -> ScreenManager.getInstance().execute(this::rebuildPlayerWidgets));
            compositeDataProvider.fullRefresh();
        }
    }

    private void initializeProviders() {
        TerminalWidget tw = this.terminalWidget;
        RebaseAPI api = RebaseApiFactory.get(instance);

        MsmpPlayerProvider msmpProvider = null;
        boolean msmpEnabled = Boolean.parseBoolean(instance.getSettings().getProperty("provider.msmp.enabled", "true"));
        if (msmpEnabled) {
            boolean propEnabled = Boolean.parseBoolean(instance.getServerProperties().getProperty("management-server-enabled", "false")) || Boolean.parseBoolean(instance.getServerProperties().getProperty("management.server.enabled", "false"));
            if (propEnabled) msmpProvider = new MsmpPlayerProvider(instance.getMSMPManager());
        }

        BackendPlayerDataProvider backendProvider = null;
        boolean backendEnabled = Boolean.parseBoolean(instance.getSettings().getProperty("provider.backend.enabled", "true"));
        if (backendEnabled && instance.getBackend() != null) {
            Optional<PlayerManagementFeature> feat = instance.getBackend().getFeature(PlayerManagementFeature.class);
            if (feat.isPresent()) backendProvider = new BackendPlayerDataProvider(feat.get());
        }

        StandardPlayerHistoryProvider standardHistory = new StandardPlayerHistoryProvider(instance, api, tw, Path.of(instance.getPath()), name -> resolveUuidFromCache(instance, name));
        StandardPlayerDataProvider standardProvider = new StandardPlayerDataProvider(instance, api, tw, standardHistory);
        StandardPlayerActionProvider standardAction = new StandardPlayerActionProvider(instance, api, tw);

        compositeDataProvider.setBaseProvider(standardProvider);
        List<IPlayerDataProvider> overlays = new ArrayList<>();
        if (msmpProvider != null) overlays.add(msmpProvider);
        if (backendProvider != null) overlays.add(backendProvider);
        compositeDataProvider.setOverlayProviders(overlays);

        List<IPlayerActionProvider> chain = new ArrayList<>();
        String priorityString = instance.getSettings().getProperty("provider.priority.players", "msmp,backend,standard");
        String[] priorities = priorityString.split(",");

        for (String p : priorities) {
            String key = p.trim().toLowerCase(Locale.ROOT);
            if ("msmp".equals(key) && msmpProvider != null) chain.add(msmpProvider);
            else if ("backend".equals(key) && backendProvider != null) chain.add(backendProvider);
            else if ("standard".equals(key)) chain.add(standardAction);
        }
        boolean standardEnabled = Boolean.parseBoolean(instance.getSettings().getProperty("provider.standard.enabled", "true"));
        if (standardEnabled && !chain.contains(standardAction)) {
            chain.add(standardAction);
        }
        this.actionProviderChain = chain;

        this.historyProvider = standardHistory;
        this.historyCollector = standardHistory;
        this.luckPermsService = new LuckPermsService(api, Path.of(instance.getPath()));

        compositeDataProvider.initialize();
        for (IPlayerActionProvider p : this.actionProviderChain) p.initialize();
        this.historyProvider.initialize();
        this.luckPermsService.initialize();

        if (msmpProvider != null) {
            instance.getMSMPManager().addStatusListener(status -> compositeDataProvider.fullRefresh());
        }
    }

    private static UUID resolveUuidFromCache(Instance instance, String name) {
        PlayerManagerController c = registry.get(instance.getInstanceId());
        if (c != null) {
            return c.getDataProvider().getCachedPlayers().values().stream().filter(p -> p.name.equalsIgnoreCase(name)).map(p -> p.uuid).findFirst().orElse(null);
        }
        return null;
    }

    public void setUiBindings(Container container, TerminalWidget terminalWidget) {
        this.container = container;
        boolean terminalChanged = this.terminalWidget != terminalWidget;
        this.terminalWidget = terminalWidget;

        if (!isInitialized || terminalChanged) {
            reloadProviders();
            isInitialized = true;
        } else {
            this.compositeDataProvider.addUpdateListener(players -> ScreenManager.getInstance().execute(this::rebuildPlayerWidgets));
            ScreenManager.getInstance().execute(this::rebuildPlayerWidgets);
        }
    }

    public CompletableFuture<Void> fullRefresh() {
        return compositeDataProvider.fullRefresh();
    }

    public IPlayerDataProvider getDataProvider() { return compositeDataProvider; }
    public LuckPermsService getLuckPermsService() { return luckPermsService; }

    public List<PlayerAction> getPlayerActions() {
        List<PlayerAction> all = new ArrayList<>();
        if (actionProviderChain == null) return all;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (IPlayerActionProvider p : actionProviderChain) {
            futures.add(p.getCustomActions().thenAccept(all::addAll).exceptionally(e -> null));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return all;
    }

    public IPlayerHistoryProvider getHistoryProvider() { return historyProvider; }

    public void rebuildPlayerWidgets() {
        if (container == null) return;
        container.clearWidgets();
        Map<UUID, ManagedPlayer> players = compositeDataProvider.getCachedPlayers();
        if (players.isEmpty()) {
            container.addWidget(new AnimatedButton.Builder().label("No players found.").active(false).build());
        } else {
            List<ManagedPlayer> sortedPlayers;
            synchronized (players) {
                sortedPlayers = players.values().stream().sorted(Comparator.comparing((ManagedPlayer p) -> !p.isOnline).thenComparing(p -> p.name.toLowerCase(Locale.ROOT))).toList();
            }
            for (ManagedPlayer p : sortedPlayers) {
                container.addWidget(new PlayerEntryWidget(p, this));
            }
        }
        container.updateWidgetPositions();
    }

    private CompletableFuture<Void> performAction(Function<IPlayerActionProvider, CompletableFuture<Void>> action) {
        if (actionProviderChain == null || actionProviderChain.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No valid player action provider available."));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        tryActionChain(actionProviderChain.iterator(), action, future);
        return future;
    }

    private void tryActionChain(Iterator<IPlayerActionProvider> iterator, Function<IPlayerActionProvider, CompletableFuture<Void>> action, CompletableFuture<Void> future) {
        if (!iterator.hasNext()) {
            future.completeExceptionally(new IllegalStateException("All providers failed to execute action."));
            return;
        }
        IPlayerActionProvider provider = iterator.next();
        action.apply(provider).whenComplete((v, e) -> {
            if (e == null) {
                future.complete(null);
            } else {
                tryActionChain(iterator, action, future);
            }
        });
    }

    public void kickPlayer(ManagedPlayer player, String reason) {
        performAction(p -> p.kickPlayer(player, reason)).whenComplete((v, e) -> {
            if (e != null) new Notification("Error", e.getMessage(), Notification.Type.ERROR);
            else if (historyCollector != null) historyCollector.recordAccessChange(player.uuid, player.name, SessionEventType.KICK, reason, System.currentTimeMillis());
        });
    }

    public void banPlayer(ManagedPlayer player, String reason, boolean ipBan) {
        performAction(p -> p.banPlayer(player, reason, ipBan)).whenComplete((v, e) -> {
            if (e != null) new Notification("Error", e.getMessage(), Notification.Type.ERROR);
            else if (historyCollector != null) historyCollector.recordAccessChange(player.uuid, player.name, SessionEventType.BAN, (ipBan ? "ip=true;" : "ip=false;") + reason, System.currentTimeMillis());
        });
    }

    public void unbanPlayer(ManagedPlayer player) {
        performAction(p -> p.unbanPlayer(player)).whenComplete((v, e) -> {
            if (e != null) new Notification("Error", e.getMessage(), Notification.Type.ERROR);
            else if (historyCollector != null) historyCollector.recordAccessChange(player.uuid, player.name, SessionEventType.UNBAN, "", System.currentTimeMillis());
        });
    }

    public void toggleOp(ManagedPlayer player) {
        performAction(p -> p.toggleOp(player)).whenComplete((v, e) -> {
            if (e != null) new Notification("Error", e.getMessage(), Notification.Type.ERROR);
            else if (historyCollector != null) historyCollector.recordAccessChange(player.uuid, player.name, SessionEventType.OP_CHANGE, player.isOp ? "op=false" : "op=true", System.currentTimeMillis());
        });
    }

    public void runCustomCommand(ManagedPlayer player, String commandTemplate) {
        performAction(p -> p.runCustomCommand(player, commandTemplate)).whenComplete((v, e) -> {
            if (e != null) new Notification("Error", "Failed to execute command: " + e.getMessage(), Notification.Type.ERROR);
        });
    }

    public boolean isServerRunning() {
        return instance.getState() == InstanceState.RUNNING;
    }

    public CompletableFuture<List<PlayerSession>> getPlayerSessions(UUID uuid) {
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

    public String getDebugChainInfo() {
        if (actionProviderChain == null) return "None";
        return actionProviderChain.stream().map(p -> p.getClass().getSimpleName()).collect(Collectors.joining(" -> "));
    }

    private static class CompositePlayerDataProvider implements IPlayerDataProvider {
        private IPlayerDataProvider baseProvider;
        private List<IPlayerDataProvider> overlayProviders = new ArrayList<>();
        private final List<Consumer<List<ManagedPlayer>>> listeners = new CopyOnWriteArrayList<>();

        private final Map<UUID, ManagedPlayer> mergedCache = new ConcurrentHashMap<>();

        void setBaseProvider(IPlayerDataProvider base) {
            this.baseProvider = base;
        }

        void setOverlayProviders(List<IPlayerDataProvider> overlays) {
            this.overlayProviders = overlays;
        }

        @Override
        public void initialize() {
            if (baseProvider != null) {
                baseProvider.initialize();
                baseProvider.addUpdateListener(l -> mergeAndNotify());
            }
            for (IPlayerDataProvider p : overlayProviders) {
                p.initialize();
                p.addUpdateListener(l -> mergeAndNotify());
            }
            mergeAndNotify();
        }

        @Override
        public void shutdown() {
            if (baseProvider != null) baseProvider.shutdown();
            for (IPlayerDataProvider p : overlayProviders) p.shutdown();
            listeners.clear();
            mergedCache.clear();
        }

        @Override
        public CompletableFuture<Void> fullRefresh() {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            if (baseProvider != null) futures.add(baseProvider.fullRefresh());
            for (IPlayerDataProvider p : overlayProviders) futures.add(p.fullRefresh());
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        }

        @Override
        public Map<UUID, ManagedPlayer> getCachedPlayers() {
            return mergedCache;
        }

        @Override
        public void addUpdateListener(Consumer<List<ManagedPlayer>> listener) {
            listeners.add(listener);
        }

        @Override
        public void removeUpdateListener(Consumer<List<ManagedPlayer>> listener) {
            listeners.remove(listener);
        }

        private synchronized void mergeAndNotify() {
            mergedCache.clear();

            if (baseProvider != null) {
                for (ManagedPlayer mp : baseProvider.getCachedPlayers().values()) {
                    ManagedPlayer clone = copyPlayer(mp);
                    clone.isOnline = false;
                    mergedCache.put(clone.uuid, clone);
                }
            }

            boolean onlineSourceFound = false;
            for (IPlayerDataProvider overlay : overlayProviders) {
                Map<UUID, ManagedPlayer> overlayPlayers = overlay.getCachedPlayers();
                if (!overlayPlayers.isEmpty()) onlineSourceFound = true;

                for (ManagedPlayer onlineMp : overlayPlayers.values()) {
                    if (onlineMp.isOnline) {
                        ManagedPlayer existing = mergedCache.get(onlineMp.uuid);
                        if (existing != null) {
                            existing.isOnline = true;
                            if (onlineMp.ping >= 0) existing.ping = onlineMp.ping;
                        } else {
                            mergedCache.put(onlineMp.uuid, copyPlayer(onlineMp));
                        }
                    }
                }
            }

            if (!onlineSourceFound && baseProvider != null) {
                 for (ManagedPlayer mp : baseProvider.getCachedPlayers().values()) {
                     if (mp.isOnline) {
                         ManagedPlayer target = mergedCache.get(mp.uuid);
                         if (target != null) target.isOnline = true;
                     }
                 }
            }

            List<ManagedPlayer> list = new ArrayList<>(mergedCache.values());
            for (Consumer<List<ManagedPlayer>> l : listeners) {
                l.accept(list);
            }
        }

        private ManagedPlayer copyPlayer(ManagedPlayer original) {
            ManagedPlayer p = new ManagedPlayer(original.uuid, original.name);
            p.isOnline = original.isOnline;
            p.ping = original.ping;
            p.address = original.address;
            p.lastSeen = original.lastSeen;
            p.isOp = original.isOp;
            p.opLevel = original.opLevel;
            p.isBanned = original.isBanned;
            p.banInfo = original.banInfo;
            p.isIpBanned = original.isIpBanned;
            p.ipBanInfo = original.ipBanInfo;
            return p;
        }
    }
}
