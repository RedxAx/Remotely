package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.data.managed.ManagedPlayer;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.data.managed.PlayerSession;
import redxax.oxy.remotely.data.managed.SessionEventType;
import redxax.oxy.remotely.data.player.IPlayerActionProvider;
import redxax.oxy.remotely.data.player.IPlayerDataProvider;
import redxax.oxy.remotely.data.player.IPlayerHistoryCollector;
import redxax.oxy.remotely.data.player.IPlayerHistoryProvider;
import redxax.oxy.remotely.data.player.standard.StandardPlayerActionProvider;
import redxax.oxy.remotely.data.player.standard.StandardPlayerDataProvider;
import redxax.oxy.remotely.data.player.standard.StandardPlayerHistoryProvider;
import redxax.oxy.remotely.ui.integrations.luckperms.LuckPermsDashboardScreen;
import restudio.rebase.api.RebaseAPI;
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

public class PlayerManagerController {

    private static final Map<String, PlayerManagerController> registry = new HashMap<>();

    private final Instance instance;
    private Container container;
    private final IPlayerDataProvider dataProvider;
    private final IPlayerActionProvider actionProvider;
    private final IPlayerHistoryProvider historyProvider;
    private final IPlayerHistoryCollector historyCollector;

    private PlayerManagerController(Instance instance, IPlayerDataProvider dataProvider, IPlayerActionProvider actionProvider, IPlayerHistoryProvider historyProvider, IPlayerHistoryCollector historyCollector) {
        this.instance = instance;
        this.dataProvider = dataProvider;
        this.actionProvider = actionProvider;
        this.historyProvider = historyProvider;
        this.historyCollector = historyCollector;
    }

    public static PlayerManagerController getOrCreate(Instance instance, RebaseAPI api) {
        synchronized (registry) {
            PlayerManagerController c = registry.get(instance.getInstanceId());
            if (c == null) {
                TerminalWidget tw = TerminalWidget.getOrCreate(instance, null, 0, 0, 0, 0);

                StandardPlayerHistoryProvider hp = new StandardPlayerHistoryProvider(api, tw, Path.of(instance.getPath()), name -> {
                    if (registry.containsKey(instance.getInstanceId())) {
                        IPlayerDataProvider dp = registry.get(instance.getInstanceId()).getDataProvider();
                        return dp.getCachedPlayers().values().stream().filter(p -> p.name.equalsIgnoreCase(name)).map(p -> p.uuid).findFirst().orElse(null);
                    }
                    return null;
                });

                IPlayerDataProvider dp = new StandardPlayerDataProvider(instance, api, tw, hp);
                IPlayerActionProvider ap = new StandardPlayerActionProvider(instance, api, tw);

                c = new PlayerManagerController(instance, dp, ap, hp, hp);
                registry.put(instance.getInstanceId(), c);
                c.initializeProviders();
            }
            return c;
        }
    }

    private void initializeProviders() {
        dataProvider.initialize();
        actionProvider.initialize();
        historyProvider.initialize();
    }

    public void setUiBindings(Container container) {
        this.container = container;
        this.dataProvider.addUpdateListener(players -> ScreenManager.getInstance().execute(this::rebuildPlayerWidgets));
        ScreenManager.getInstance().execute(this::rebuildPlayerWidgets);
    }

    public CompletableFuture<Void> fullRefresh() {
        CompletableFuture<Void> standardRefresh = dataProvider.fullRefresh();

        if (dataProvider instanceof StandardPlayerDataProvider sdp) {
            sdp.getLuckPermsService().clearCache();
        }

        return standardRefresh;
    }

    public IPlayerDataProvider getDataProvider() {
        return dataProvider;
    }

    public List<PlayerAction> getPlayerActions() {
        return actionProvider.getCustomActions().join();
    }

    public IPlayerHistoryProvider getHistoryProvider() {
        return historyProvider;
    }

    public void rebuildPlayerWidgets() {
        if (container == null) return;
        container.clearWidgets();
        Map<UUID, ManagedPlayer> players = dataProvider.getCachedPlayers();
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

    public void kickPlayer(ManagedPlayer player, String reason) {
        actionProvider.kickPlayer(player, reason).thenRun(() -> historyCollector.recordAccessChange(player.uuid, player.name, SessionEventType.KICK, reason, System.currentTimeMillis()));
    }

    public void banPlayer(ManagedPlayer player, String reason, boolean ipBan) {
        actionProvider.banPlayer(player, reason, ipBan).thenRun(() -> historyCollector.recordAccessChange(player.uuid, player.name, SessionEventType.BAN, (ipBan ? "ip=true;" : "ip=false;") + reason, System.currentTimeMillis()));
    }

    public void unbanPlayer(ManagedPlayer player) {
        actionProvider.unbanPlayer(player).thenRun(() -> historyCollector.recordAccessChange(player.uuid, player.name, SessionEventType.UNBAN, "", System.currentTimeMillis()));
    }

    public void toggleOp(ManagedPlayer player) {
        actionProvider.toggleOp(player).thenRun(() -> historyCollector.recordAccessChange(player.uuid, player.name, SessionEventType.OP_CHANGE, player.isOp ? "op=false" : "op=true", System.currentTimeMillis()));
    }

    public boolean isServerRunning() {
        return instance.getState() == InstanceState.RUNNING;
    }

    public void runCustomCommand(ManagedPlayer player, String commandTemplate) {
        actionProvider.runCustomCommand(player, commandTemplate).exceptionally(ex -> {
            new Notification("Error", ex.getMessage(), Notification.Type.ERROR);
            return null;
        });
    }

    public CompletableFuture<List<PlayerSession>> getPlayerSessions(UUID uuid) {
        return historyProvider.getSessions(uuid);
    }

    public void openLuckPermsSettings() {
        if (dataProvider instanceof StandardPlayerDataProvider sdp) {
            ScreenManager.getInstance().getCurrentScreen().addDrawableChild(new LuckPermsSettingsPopup(sdp.getLuckPermsService(), this::fullRefresh));
        }
    }

    public void openLuckPermsDashboard() {
        if (dataProvider instanceof StandardPlayerDataProvider sdp && sdp.getLuckPermsService().isEnabled()) {
            ScreenManager.getInstance().setScreen(new LuckPermsDashboardScreen(ScreenManager.getInstance().getCurrentScreen(), sdp.getLuckPermsService()));
        } else {
            new Notification("Error", "LuckPerms integration is disabled.", Notification.Type.ERROR);
        }
    }
}
