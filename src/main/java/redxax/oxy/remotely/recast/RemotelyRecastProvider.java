package redxax.oxy.remotely.recast;

import redxax.oxy.remotely.util.TaskSchedulers;

import redxax.oxy.remotely.util.AsyncTools;

import dev.restudio.recast.bridge.BridgeAction;
import dev.restudio.recast.bridge.BridgeActionResult;
import dev.restudio.recast.bridge.BridgeEntry;
import dev.restudio.recast.bridge.BridgeFlowStep;
import dev.restudio.recast.bridge.BridgeProvider;
import dev.restudio.recast.bridge.BridgeSearchRequest;
import dev.restudio.recast.api.surface.DeclarativeSurface;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.ui.server.ServerConfigurationScreen;
import restudio.rebase.Rebase;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.backend.feature.PlayerDirectoryFeature;
import restudio.rebase.backend.feature.PlayerManagementFeature;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.localcontrol.LifecycleManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import restudio.rescreen.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;
import java.util.concurrent.CompletionStage;

public final class RemotelyRecastProvider implements BridgeProvider {
    private static final String SERVER = "server:";
    private static final String TERMINAL = "terminal:";
    private static final String FILES = "files:";
    private static final String PLAYERS = "players:";
    private static final String PLAYER = "player:";
    private static final String PLAYER_ACTION = "player-action:";
    private static final String RESYNC = "resync:";
    private static final String RESYNC_FLOWS = "resync-flows:";
    private static final String RESYNC_GUIS = "resync-guis:";
    private static final String RESYNC_TABS = "resync-tabs:";
    private static final String RESYNC_SCOREBOARDS = "resync-scoreboards:";
    private static final String FLOW_ITEM = "flow-item:";
    private static final String GUI_ITEM = "gui-item:";
    private static final String TAB_ITEM = "tab-item:";
    private static final String SCOREBOARD_ITEM = "scoreboard-item:";
    private static final String SETTINGS = "settings:";

    private final RemotelyClient client;

    public RemotelyRecastProvider(RemotelyClient client) {
        this.client = client;
    }

    @Override
    public AutoCloseable subscribe(Runnable invalidation) {
        InstanceManager manager = Rebase.get().getInstanceManager();
        manager.addChangeListener(invalidation);
        return () -> manager.removeChangeListener(invalidation);
    }

    @Override
    public CompletionStage<List<BridgeEntry>> search(BridgeSearchRequest request) {
        if (!request.flow().isEmpty()) {
            BridgeFlowStep step = request.flow().getLast();
            return stage(AsyncTools.supply(TaskSchedulers.current(), () -> filter(children(step.entry()), request.query())));
        }
        List<BridgeEntry> entries = instances().stream().map(this::server).toList();
        return stage(Async.completed(filter(entries, request.query())));
    }

    @Override
    public CompletionStage<List<BridgeEntry>> continueFrom(String entry, String continuation) {
        return stage(AsyncTools.supply(TaskSchedulers.current(), () -> children(entry)));
    }

    @Override
    public CompletionStage<DeclarativeSurface> inspect(String entry) {
        if (entry.startsWith(PLAYER)) {
            PlayerTarget target = playerTarget(entry);
            return stage(Async.completed(target == null ? null
                    : DeclarativeSurface.detail(target.name(), "Online In " + target.instance().getName())));
        }
        if (reSyncItem(entry)) {
            return stage(Async.completed(reSyncInspection(entry)));
        }
        Instance instance = instance(identifier(entry));
        if (instance == null) {
            return stage(Async.completed(null));
        }
        String state = instance.getState() == null ? "Stopped" : title(instance.getState().name());
        String location = instance.getPath() == null || instance.getPath().isBlank() ? "" : "\n" + instance.getPath();
        return stage(Async.completed(DeclarativeSurface.detail(instance.getName(), state + location)));
    }

    @Override
    public CompletionStage<List<BridgeAction>> actions(String entry) {
        if (reSyncItem(entry)) {
            return stage(Async.completed(List.of(
                    new BridgeAction("open-resync-item", "Open", "ReSync", "default", List.of(), false, true))));
        }
        if (entry.startsWith(PLAYER_ACTION)) {
            PlayerActionTarget target = playerActionTarget(entry);
            if (target == null) {
                return stage(Async.completed(List.of()));
            }
            return stage(AsyncTools.supply(TaskSchedulers.current(), () -> playerActions(target.player()).stream()
                    .filter(action -> action.id().equals(target.action())).toList()));
        }
        if (entry.startsWith(PLAYER)) {
            return stage(AsyncTools.supply(TaskSchedulers.current(), () -> playerActions(entry)));
        }
        Instance instance = instance(identifier(entry));
        if (instance == null) {
            return stage(Async.completed(List.of()));
        }
        List<BridgeAction> actions = new ArrayList<>();
        actions.add(new BridgeAction("open", "Open Server", "remotely", "default", List.of(), false, true));
        actions.add(new BridgeAction("files", "Open Files", "folder", "default", List.of(), false, true));
        actions.add(new BridgeAction("settings", "Server Settings", "edit", "default", List.of(), false, true));
        if (running(instance)) {
            actions.add(new BridgeAction("stop", "Stop Server", "stop", "danger", List.of(), true, true));
        } else {
            actions.add(new BridgeAction("start", "Start Server", "start", "nice", List.of(), false, true));
        }
        return stage(Async.completed(List.copyOf(actions)));
    }

    @Override
    public CompletionStage<BridgeActionResult> execute(String entry, String action) {
        if (reSyncItem(entry)) {
            return "open-resync-item".equals(action)
                    ? openReSyncItem(entry)
                    : stage(Async.completed(BridgeActionResult.failed("Action is unavailable")));
        }
        if (entry.startsWith(PLAYER_ACTION)) {
            PlayerActionTarget target = playerActionTarget(entry);
            return target == null || !target.action().equals(action) ? stage(Async.completed(BridgeActionResult.failed("Player action is unavailable")))
                    : playerAction(target.player(), target.action());
        }
        if (entry.startsWith(PLAYER)) {
            return playerAction(entry, action);
        }
        Instance instance = instance(identifier(entry));
        if (instance == null) {
            return stage(Async.completed(BridgeActionResult.failed("Server is unavailable")));
        }
        return switch (action) {
            case "open", "open-terminal" -> ui(() -> client.openInstanceInTerminal(null, instance));
            case "files", "open-files" -> ui(() -> client.openInstanceFiles(null, instance));
            case "settings", "open-settings" -> ui(() -> openSettings(instance));
            case "open-resync" -> ui(() -> client.openReSyncStudio(null, instance));
            case "open-flows" -> ui(() -> client.getFlowManager().openFlowEditor(flowId(instance), null));
            case "open-guis" -> ui(() -> client.getFlowManager().openGuiDesigner(flowId(instance), null));
            case "open-tabs" -> ui(() -> client.getFlowManager().openTabDesigner(flowId(instance), null));
            case "open-scoreboards" -> ui(() -> client.getFlowManager().openScoreboardDesigner(flowId(instance), null));
            case "start" -> lifecycle(instance, true);
            case "stop" -> lifecycle(instance, false);
            default -> stage(Async.completed(BridgeActionResult.failed("Action is unavailable")));
        };
    }

    private CompletionStage<BridgeActionResult> lifecycle(Instance instance, boolean start) {
        InstanceState previousState = instance.getState();
        String operationId = start ? LifecycleManager.requestStart(instance) : LifecycleManager.requestStop(instance);
        try {
            instance.getBackend().connect();
            CompletionStage<?> operation = start ? instance.getBackend().getExecution().startServer() : instance.getBackend().getExecution().stopServer();
            ScreenManager.getInstance().execute(() -> instance.setState(start ? InstanceState.STARTING : InstanceState.STOPPING));
            return operation.handle((ignored, throwable) -> {
                if (throwable != null) {
                    String message = throwable.getMessage() == null || throwable.getMessage().isBlank() ? (start ? "Server Start Failed" : "Server Stop Failed") : throwable.getMessage();
                    ScreenManager.getInstance().execute(() -> {
                        if (!start && previousState == InstanceState.RUNNING) {
                            LifecycleManager.restoreRunning(instance, operationId, message);
                        } else if (start && previousState == InstanceState.RUNNING) {
                            LifecycleManager.restoreRunning(instance, operationId, message);
                        } else {
                            LifecycleManager.fail(instance, operationId, InstanceState.CRASHED, message);
                        }
                    });
                    return BridgeActionResult.failed(message);
                }
                if (start && ignored == null) {
                    return BridgeActionResult.completed(false);
                }
                ScreenManager.getInstance().execute(() -> instance.setState(start ? InstanceState.RUNNING : InstanceState.STOPPED));
                return BridgeActionResult.completed(false);
            });
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank() ? (start ? "Server Start Failed" : "Server Stop Failed") : exception.getMessage();
            ScreenManager.getInstance().execute(() -> {
                if (!start && previousState == InstanceState.RUNNING) {
                    LifecycleManager.restoreRunning(instance, operationId, message);
                } else if (start && previousState == InstanceState.RUNNING) {
                    LifecycleManager.restoreRunning(instance, operationId, message);
                } else {
                    LifecycleManager.fail(instance, operationId, InstanceState.CRASHED, message);
                }
            });
            return stage(Async.completed(BridgeActionResult.failed(message)));
        }
    }

    private CompletionStage<BridgeActionResult> ui(Runnable action) {
        Async<BridgeActionResult> future = Async.pending();
        ScreenManager.getInstance().execute(() -> {
            try {
                action.run();
                future.complete(BridgeActionResult.completed(true));
            } catch (RuntimeException exception) {
                future.complete(BridgeActionResult.failed(exception.getMessage()));
            }
        });
        return stage(future);
    }

    private static <T> CompletionStage<T> stage(Async<T> value) {
        return JvmAsyncBridge.toFuture(value);
    }

    private void openSettings(Instance instance) {
        Screen parent = ScreenManager.getInstance().getCurrentScreen();
        client.getHost().setScreen(new ServerConfigurationScreen(parent, instance, null, client));
    }

    private BridgeEntry server(Instance instance) {
        String identifier = id(instance);
        String state = instance.getState() == null ? "Stopped" : title(instance.getState().name());
        return new BridgeEntry(SERVER + identifier, instance.getName(), state, "remotely", List.of(), List.of("Minecraft", state), "open", "server", state);
    }

    private List<BridgeEntry> children(String entry) {
        if (entry.startsWith(PLAYERS)) {
            String identifier = identifier(entry);
            if (client.getFlowManager() == null) {
                return List.of();
            }
            Instance instance = instance(identifier);
            String serverId = instance == null ? identifier : flowId(instance);
            return client.getFlowManager().getOnlinePlayerNamesForServer(serverId).stream()
                    .map(player -> new BridgeEntry(PLAYER + encode(identifier) + ":" + encode(player), player, "Online Player", "minecraft", List.of(),
                            List.of("Player"), null, "player-actions", player + " Is Online"))
                    .toList();
        }
        if (entry.startsWith(PLAYER)) {
            PlayerTarget target = playerTarget(entry);
            if (target == null) {
                return List.of();
            }
            return playerActions(entry).stream().map(action -> new BridgeEntry(
                    PLAYER_ACTION + encode(entry) + ":" + encode(action.id()), action.title(), target.name(), action.icon(), List.of(),
                    List.of("Player Action"), action.id(), null, action.title() + " " + target.name())).toList();
        }
        if (entry.startsWith(RESYNC_FLOWS)) {
            Instance instance = instance(identifier(entry));
            if (instance == null || client.getFlowManager() == null) {
                return List.of();
            }
            String server = flowId(instance);
            return client.getFlowManager().getGraphsForServer(server, ReSyncResourceType.FLOW).keySet().stream()
                    .map(identifier -> reSyncEntry(FLOW_ITEM, server, identifier, client.getFlowManager().getFlowName(server, identifier), "Flow", "flow"))
                    .toList();
        }
        if (entry.startsWith(RESYNC_GUIS)) {
            Instance instance = instance(identifier(entry));
            if (instance == null || client.getFlowManager() == null) {
                return List.of();
            }
            String server = flowId(instance);
            return client.getFlowManager().getGuisForServer(server).keySet().stream()
                    .map(identifier -> reSyncEntry(GUI_ITEM, server, identifier, client.getFlowManager().getGuiName(server, identifier), "GUI", "panel"))
                    .toList();
        }
        if (entry.startsWith(RESYNC_TABS)) {
            Instance instance = instance(identifier(entry));
            if (instance == null || client.getFlowManager() == null) {
                return List.of();
            }
            String server = flowId(instance);
            return client.getFlowManager().getTabsForServer(server).keySet().stream()
                    .map(identifier -> reSyncEntry(TAB_ITEM, server, identifier, client.getFlowManager().getTabName(server, identifier), "Tab List", "layout"))
                    .toList();
        }
        if (entry.startsWith(RESYNC_SCOREBOARDS)) {
            Instance instance = instance(identifier(entry));
            if (instance == null || client.getFlowManager() == null) {
                return List.of();
            }
            String server = flowId(instance);
            return client.getFlowManager().getScoreboardsForServer(server).keySet().stream()
                    .map(identifier -> reSyncEntry(SCOREBOARD_ITEM, server, identifier, client.getFlowManager().getScoreboardName(server, identifier), "Scoreboard", "report"))
                    .toList();
        }
        if (entry.startsWith(RESYNC)) {
            Instance instance = instance(identifier(entry));
            if (instance == null || client.getFlowManager() == null) {
                return List.of();
            }
            String identifier = id(instance);
            return List.of(
                    new BridgeEntry(RESYNC_FLOWS + identifier, "Flows", instance.getName(), "flow", List.of(), List.of("Functions", "Nodes"),
                            "open-flows", "flows", "Flow Editor"),
                    new BridgeEntry(RESYNC_GUIS + identifier, "GUIs", instance.getName(), "panel", List.of("Menus"), List.of("Inventory", "Designer"),
                            "open-guis", "guis", "GUI Designer"),
                    new BridgeEntry(RESYNC_TABS + identifier, "Tab Lists", instance.getName(), "layout", List.of("Tab"), List.of("Players", "Designer"),
                            "open-tabs", "tabs", "Tab List Designer"),
                    new BridgeEntry(RESYNC_SCOREBOARDS + identifier, "Scoreboards", instance.getName(), "report", List.of(), List.of("Sidebar", "Designer"),
                            "open-scoreboards", "scoreboards", "Scoreboard Designer"));
        }
        Instance instance = instance(identifier(entry));
        if (instance == null) {
            return List.of();
        }
        String identifier = id(instance);
        List<BridgeEntry> entries = new ArrayList<>();
        entries.add(new BridgeEntry(TERMINAL + identifier, "Terminal", instance.getName(), "terminal", List.of(), List.of("Console"), "open-terminal", null, "Server Terminal"));
        entries.add(new BridgeEntry(FILES + identifier, "Files", instance.getName(), "folder", List.of(), List.of("Explorer"), "open-files", null, instance.getPath()));
        entries.add(new BridgeEntry(SETTINGS + identifier, "Settings", instance.getName(), "edit", List.of(), List.of("Configuration"), "open-settings", null, "Server Settings"));
        entries.add(new BridgeEntry(RESYNC + identifier, "ReSync Studio", instance.getName(), "ReSync", List.of(), List.of("Flows", "GUIs", "Scoreboards", "Tabs"),
                "open-resync", "resync", "ReSync Studio"));
        int players = client.getFlowManager() == null ? 0 : client.getFlowManager().getOnlinePlayerNamesForServer(flowId(instance)).size();
        entries.add(new BridgeEntry(PLAYERS + identifier, "Players", players + " Online", "minecraft", List.of(), List.of("Online"), null, "players", players + " Online"));
        return List.copyOf(entries);
    }

    private List<BridgeEntry> filter(List<BridgeEntry> entries, String query) {
        if (query == null || query.isBlank()) {
            return entries;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> entry.title().toLowerCase(Locale.ROOT).contains(normalized)
                || entry.subtitle().toLowerCase(Locale.ROOT).contains(normalized)
                || entry.keywords().stream().anyMatch(keyword -> keyword.toLowerCase(Locale.ROOT).contains(normalized))).toList();
    }

    private List<Instance> instances() {
        InstanceManager manager = Rebase.get().getInstanceManager();
        List<Instance> instances = new ArrayList<>(manager.getLocalInstances());
        for (RemoteHost host : manager.getRemoteHosts()) {
            instances.addAll(manager.getRemoteInstances(host));
        }
        return List.copyOf(instances);
    }

    private Instance instance(String identifier) {
        return instances().stream().filter(instance -> id(instance).equals(identifier)).findFirst().orElse(null);
    }

    private String identifier(String entry) {
        int separator = entry.indexOf(':');
        return separator < 0 ? entry : entry.substring(separator + 1);
    }

    private String id(Instance instance) {
        String identifier = instance.getInstanceId();
        return identifier == null || identifier.isBlank() ? instance.getPath() : identifier;
    }

    private String flowId(Instance instance) {
        if (instance.getBackendConfig() != null && instance.getBackendConfig().credentials != null && "RESTUDIO".equalsIgnoreCase(instance.getBackendConfig().type)) {
            return instance.getBackendConfig().credentials.getOrDefault("identifier", id(instance));
        }
        return id(instance);
    }

    private boolean running(Instance instance) {
        return instance.getState() == InstanceState.RUNNING || instance.getState() == InstanceState.STARTING;
    }

    private List<BridgeAction> playerActions(String entry) {
        PlayerTarget target = playerTarget(entry);
        if (target == null) {
            return List.of();
        }
        PlayerManagementFeature management = management(target.instance());
        if (management == null) {
            return List.of();
        }
        PlayerManagementFeature.SimplePlayer player = player(management, target.name());
        if (player == null) {
            return List.of();
        }
        List<BridgeAction> actions = new ArrayList<>();
        actions.add(new BridgeAction("kick", "Kick Player", "delete", "danger", List.of(), true, true));
        actions.add(new BridgeAction("ban", "Ban Player", "close", "danger", List.of(), true, true));
        Boolean operator = operator(target.instance(), player.uuid());
        if (operator != null) {
            actions.add(new BridgeAction(operator ? "deop" : "op", operator ? "Remove Operator" : "Grant Operator",
                    operator ? "deop" : "op", "calm", List.of(), false, true));
        }
        return List.copyOf(actions);
    }

    private CompletionStage<BridgeActionResult> playerAction(String entry, String action) {
        PlayerTarget target = playerTarget(entry);
        if (target == null) {
            return stage(Async.completed(BridgeActionResult.failed("Player is unavailable")));
        }
        PlayerManagementFeature management = management(target.instance());
        if (management == null) {
            return stage(Async.completed(BridgeActionResult.failed("Player management is unavailable")));
        }
        return management.getOnlinePlayers().thenCompose(players -> {
            PlayerManagementFeature.SimplePlayer player = players.stream().filter(candidate -> candidate.name().equalsIgnoreCase(target.name())).findFirst().orElse(null);
            if (player == null) {
                return stage(Async.completed(BridgeActionResult.failed("Player is no longer online")));
            }
            CompletionStage<Void> operation = switch (action) {
                case "kick" -> management.kick(player.uuid(), "Kicked by operator");
                case "ban" -> management.ban(player.uuid(), "Banned by operator", false);
                case "op" -> management.setOp(player.uuid(), true);
                case "deop" -> management.setOp(player.uuid(), false);
                default -> null;
            };
            if (operation == null) {
                return stage(Async.completed(BridgeActionResult.failed("Player action is unavailable")));
            }
            return operation.handle((ignored, throwable) -> throwable == null ? BridgeActionResult.completed(false)
                    : BridgeActionResult.failed(throwable.getMessage()));
        });
    }

    private PlayerManagementFeature management(Instance instance) {
        if (instance.getBackend() == null) {
            return null;
        }
        instance.getBackend().connect();
        return instance.getBackend().getFeature(PlayerManagementFeature.class).orElse(null);
    }

    private PlayerManagementFeature.SimplePlayer player(PlayerManagementFeature management, String name) {
        return management.getOnlinePlayers().join().stream().filter(player -> player.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private Boolean operator(Instance instance, UUID player) {
        if (instance.getBackend() == null) {
            return null;
        }
        PlayerDirectoryFeature directory = instance.getBackend().getFeature(PlayerDirectoryFeature.class).orElse(null);
        if (directory == null) {
            return null;
        }
        return directory.getOnlinePlayersDetailed().join().stream().filter(candidate -> candidate.uuid().equals(player))
                .map(PlayerDirectoryFeature.DetailedPlayer::isOp).findFirst().orElse(null);
    }

    private PlayerTarget playerTarget(String entry) {
        String[] values = entry.substring(PLAYER.length()).split(":", 2);
        if (values.length != 2) {
            return null;
        }
        Instance instance = instance(decode(values[0]));
        return instance == null ? null : new PlayerTarget(instance, decode(values[1]));
    }

    private PlayerActionTarget playerActionTarget(String entry) {
        String[] values = entry.substring(PLAYER_ACTION.length()).split(":", 2);
        if (values.length != 2) {
            return null;
        }
        String player = decode(values[0]);
        return playerTarget(player) == null ? null : new PlayerActionTarget(player, decode(values[1]));
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private boolean reSyncItem(String entry) {
        return entry.startsWith(FLOW_ITEM) || entry.startsWith(GUI_ITEM) || entry.startsWith(TAB_ITEM) || entry.startsWith(SCOREBOARD_ITEM);
    }

    private BridgeEntry reSyncEntry(String prefix, String server, String identifier, String name, String type, String icon) {
        String title = name == null || name.isBlank() ? identifier : name;
        return new BridgeEntry(prefix + encode(server) + ":" + encode(identifier), title, type, icon, List.of(identifier), List.of("ReSync"),
                "open-resync-item", null, type + " · " + identifier);
    }

    private CompletionStage<BridgeActionResult> openReSyncItem(String entry) {
        int prefixEnd = entry.indexOf(':') + 1;
        String[] values = entry.substring(prefixEnd).split(":", 2);
        if (values.length != 2 || client.getFlowManager() == null) {
            return stage(Async.completed(BridgeActionResult.failed("ReSync resource is unavailable")));
        }
        String prefix = entry.substring(0, prefixEnd);
        String server = decode(values[0]);
        String identifier = decode(values[1]);
        return ui(() -> {
            switch (prefix) {
                case FLOW_ITEM -> client.getFlowManager().openFlowEditor(server, null, identifier);
                case GUI_ITEM -> client.getFlowManager().openGuiDesigner(server, null, identifier);
                case TAB_ITEM -> client.getFlowManager().openTabDesigner(server, null, identifier);
                case SCOREBOARD_ITEM -> client.getFlowManager().openScoreboardDesigner(server, null, identifier);
                default -> throw new IllegalArgumentException("ReSync resource is unavailable");
            }
        });
    }

    private DeclarativeSurface reSyncInspection(String entry) {
        int prefixEnd = entry.indexOf(':') + 1;
        String[] values = entry.substring(prefixEnd).split(":", 2);
        if (values.length != 2 || client.getFlowManager() == null) {
            return null;
        }
        String prefix = entry.substring(0, prefixEnd);
        String server = decode(values[0]);
        String identifier = decode(values[1]);
        String type;
        String name;
        switch (prefix) {
            case FLOW_ITEM -> {
                type = "Flow";
                name = client.getFlowManager().getFlowName(server, identifier);
            }
            case GUI_ITEM -> {
                type = "GUI";
                name = client.getFlowManager().getGuiName(server, identifier);
            }
            case TAB_ITEM -> {
                type = "Tab List";
                name = client.getFlowManager().getTabName(server, identifier);
            }
            case SCOREBOARD_ITEM -> {
                type = "Scoreboard";
                name = client.getFlowManager().getScoreboardName(server, identifier);
            }
            default -> {
                return null;
            }
        }
        return DeclarativeSurface.detail(name == null || name.isBlank() ? type : name, type + " In ReSync Studio");
    }

    private String title(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record PlayerTarget(Instance instance, String name) {
    }

    private record PlayerActionTarget(String player, String action) {
    }
}
