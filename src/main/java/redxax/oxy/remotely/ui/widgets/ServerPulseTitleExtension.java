package redxax.oxy.remotely.ui.widgets;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.ui.server.ServerIconManager;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rebase.Rebase;
import restudio.rebase.account.Account;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.backend.feature.ResourceUsageFeature;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.ExpandableWindowTitleWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.WindowTitleBarRenderer;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.ResourceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ServerPulseTitleExtension extends ExpandableWindowTitleWidget {
    private record ServerSnapshot(Instance instance, long uptimeMs, double cpuPercent, long memoryBytes, long memoryLimitBytes, List<UnifiedPlayer> players) {
    }

    private record PlayerEvent(UUID playerId, String name, boolean joined, long createdAt) {
    }

    private record PlayerSubscription(PlayerManagerController controller, Consumer<PlayerManagerController.PlayerSnapshot> listener) {
    }

    private static final long REFRESH_INTERVAL_MS = 5_000L;
    private static final long DISCOVERY_INTERVAL_MS = 30_000L;
    private static final long PLAYER_STABILITY_MS = 750L;
    private static final long PLAYER_INITIALIZATION_MS = 5_000L;
    private static final long EVENT_DURATION_MS = 4_200L;
    private static final float EVENT_EXIT_START = 0.78f;
    private static final int BASE_HEIGHT = 8;
    private static final int EVENT_HEAD_SIZE = 8;
    private static final int EVENT_HEIGHT = 12;
    private static final int EVENT_HORIZONTAL_PADDING = 5;
    private static final long FACE_RETRY_MS = 30_000L;
    private static final Identifier MISSING_FACE = Identifier.icon("steve.png");
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final ServerIconManager iconManager = new ServerIconManager(Config.remotelyDir);
    private final Map<String, Instance> reactorInstances = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, UnifiedPlayer>> knownPlayers = new HashMap<>();
    private final Map<String, Map<UUID, UnifiedPlayer>> observedPlayers = new HashMap<>();
    private final Map<String, Long> observedPlayersSince = new HashMap<>();
    private final Map<String, Long> playerSubscriptionStartedAt = new HashMap<>();
    private final Map<UUID, Identifier> playerFaces = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerFaceRetryAt = new ConcurrentHashMap<>();
    private final Set<UUID> requestedPlayerFaces = ConcurrentHashMap.newKeySet();
    private final Set<String> initializedPlayers = new HashSet<>();
    private final Map<String, PlayerSubscription> subscriptions = new HashMap<>();
    private final ConcurrentLinkedDeque<PlayerEvent> playerEvents = new ConcurrentLinkedDeque<>();
    private final Map<String, MountableButtonWidget> rows = new LinkedHashMap<>();
    private final MountableButtonWidget emptyRow = new MountableButtonWidget.Builder("No Running Servers").description("Statuses update automatically").build();
    private volatile List<ServerSnapshot> snapshots = List.of();
    private volatile long nextRefreshAt;
    private volatile long nextDiscoveryAt;

    public ServerPulseTitleExtension() {
        super(WindowTitleBarRenderer.BUTTON_WIDTH, WindowTitleBarRenderer.BUTTON_HEIGHT);
        selectable = true;
        setSelected(true);
    }

    @Override
    protected int getExpandedWidth() {
        int contentWidth = snapshots.stream().limit(8).mapToInt(server -> Math.max(TextRenderer.getWidth(server.instance().getName()),
                TextRenderer.getWidth(serverDescription(server))) + 20).max().orElse(TextRenderer.getWidth("Statuses update automatically") + 20);
        return Math.clamp(contentWidth, 280, 560);
    }

    @Override
    protected int getExpandedHeight() {
        return 22 + Math.clamp(snapshots.size(), 1, 8) * 31;
    }

    @Override
    protected int getRestingWidth(long now) {
        PlayerEvent event = currentEvent(now);
        if (event == null) return getCollapsedWidth();
        int fullWidth = eventWidth(event);
        float visibility = eventVisibility(event, now);
        return getCollapsedWidth() + Math.round((fullWidth - getCollapsedWidth()) * visibility);
    }

    @Override
    protected int getRestingHeight(long now) {
        PlayerEvent event = currentEvent(now);
        if (event == null) return BASE_HEIGHT;
        return BASE_HEIGHT + Math.round((EVENT_HEIGHT - BASE_HEIGHT) * eventVisibility(event, now));
    }

    @Override
    public void tick() {
        PlayerEvent event = currentEvent(System.currentTimeMillis());
        setAccent(ThemeManager.getAccent(event == null ? "default" : event.joined() ? "nice" : "danger"));
        setSelected(true);
        super.tick();
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        if (isExpanded()) {
            for (MountableButtonWidget row : rows.values()) {
                if (row.isVisible() && row.isMouseOver(event.x(), event.y()) && row.mouseClicked(event.retarget(row, event.x(), event.y()))) {
                    return event.finish(true);
                }
            }
        }
        return super.mouseClicked(event);
    }

    @Override
    protected void drawContent(IDrawContext context, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        settlePlayerSnapshots(now);
        requestRefresh(now);
        if (isExpanded() && getWidth() >= 80 && getHeight() >= 20) {
            drawExpandedContent(context, mouseX, mouseY);
            return;
        }
        PlayerEvent event = currentEvent(now);
        if (event == null) return;
        drawPlayerEvent(context, event, now);
    }

    private void drawPlayerEvent(IDrawContext context, PlayerEvent event, long now) {
        float progress = Math.clamp((now - event.createdAt()) / (float) EVENT_DURATION_MS, 0f, 1f);
        float entrance = Math.clamp(progress / 0.16f, 0f, 1f);
        float exit = Math.clamp((progress - EVENT_EXIT_START) / (1f - EVENT_EXIT_START), 0f, 1f);
        float easedEntrance = 1f - (1f - entrance) * (1f - entrance) * (1f - entrance);
        float easedExit = exit * exit * exit;
        int innerTop = getY() + 1;
        int innerHeight = getHeight() - 2;
        int textY = innerTop + Math.round((innerHeight - ITextRenderer.fontHeight) / 2f);
        int headY = innerTop + (innerHeight - EVENT_HEAD_SIZE) / 2;
        int contentWidth = eventContentWidth(event);
        float contentOffsetX = -(1f - easedEntrance) * contentWidth + easedExit * contentWidth;
        context.pushScissorState();
        context.enableScissor(getX() + 1, innerTop, getX() + getWidth() - 1, innerTop + innerHeight);
        context.getMatrices().push();
        context.getMatrices().translate(contentOffsetX, 0, 0);
        int contentX = getX() + getWidth() - EVENT_HORIZONTAL_PADDING - contentWidth;
        Identifier face = playerFace(event);
        context.drawPixelArt(face, contentX, headY, EVENT_HEAD_SIZE, EVENT_HEAD_SIZE);
        contentX += EVENT_HEAD_SIZE + 4;
        String sign = event.joined() ? "+" : "-";
        context.drawText(sign, contentX, textY, ThemeManager.getAccent(event.joined() ? "nice" : "danger").getAccentColor(), false);
        contentX += TextRenderer.getWidth(sign) + 3;
        context.drawText(eventMessage(event), contentX, textY, ThemeManager.getColor(ThemeColor.text), false);
        context.getMatrices().pop();
        context.disableScissor();
        context.popScissorState();
    }

    private void requestRefresh(long now) {
        if (now < nextRefreshAt || !refreshing.compareAndSet(false, true)) return;
        nextRefreshAt = now + REFRESH_INTERVAL_MS;
        discoverInstances(now).thenCompose(this::collectSnapshots).whenComplete((result, throwable) -> {
            if (throwable == null && result != null) ScreenManager.getInstance().execute(() -> applySnapshots(result));
            refreshing.set(false);
        });
    }

    private CompletableFuture<List<Instance>> discoverInstances(long now) {
        InstanceManager manager = Rebase.get().getInstanceManager();
        List<CompletableFuture<Void>> discoveries = new ArrayList<>();
        if (now >= nextDiscoveryAt) {
            nextDiscoveryAt = now + DISCOVERY_INTERVAL_MS;
            for (RemoteHost host : manager.getRemoteHosts()) {
                manager.fetchRemoteInstances(host).whenComplete((ignored, throwable) -> nextRefreshAt = 0L);
            }
            discoveries.add(refreshReactorInstances().completeOnTimeout(null, 8, TimeUnit.SECONDS).exceptionally(ignored -> null));
        }
        return CompletableFuture.allOf(discoveries.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            Map<String, Instance> instances = new LinkedHashMap<>();
            for (Instance instance : manager.getLocalInstances()) addDiscoveredInstance(instances, instance);
            for (RemoteHost host : manager.getRemoteHosts()) {
                for (Instance instance : manager.getRemoteInstances(host)) addDiscoveredInstance(instances, instance);
            }
            for (Instance instance : reactorInstances.values()) addDiscoveredInstance(instances, instance);
            return instances.values().stream().filter(this::shouldProbe).toList();
        });
    }

    private CompletableFuture<Void> refreshReactorInstances() {
        if (!ReStudio.getInstance().isAuthenticated()) {
            reactorInstances.clear();
            return CompletableFuture.completedFuture(null);
        }
        return ReStudio.getInstance().getApi().getServers().thenAccept(servers -> {
            Set<String> identifiers = new HashSet<>();
            if (servers != null) {
                for (ServerModels.ClientServerView server : servers) {
                    if (server == null || server.identifier == null || server.identifier.isBlank()) continue;
                    identifiers.add(server.identifier);
                    Instance instance = reactorInstances.computeIfAbsent(server.identifier, ignored -> createReactorInstance(server));
                    if (server.name != null && !server.name.isBlank()) instance.setName(server.name);
                    applyServerType(instance, server.loader);
                    if (server.isInstalling) instance.setState(InstanceState.INSTALLING);
                    if (server.isSuspended) instance.setState(InstanceState.STOPPED);
                }
            }
            reactorInstances.keySet().retainAll(identifiers);
        });
    }

    private Instance createReactorInstance(ServerModels.ClientServerView server) {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("identifier", server.identifier);
        credentials.put("host", server.sftpIp == null ? "" : server.sftpIp);
        credentials.put("port", String.valueOf(server.sftpPort));
        credentials.put("user", server.sftpUser == null ? "" : server.sftpUser);
        credentials.put("password", "");
        Instance instance = new Instance(server.name == null || server.name.isBlank() ? server.identifier : server.name, "unknown", "");
        instance.setBackendConfig(new BackendConfig("RESTUDIO", credentials));
        instance.setServer(true);
        applyServerType(instance, server.loader);
        instance.setState(server.isInstalling ? InstanceState.INSTALLING : InstanceState.STOPPED);
        return instance;
    }

    private void applyServerType(Instance instance, String loader) {
        if (loader == null || loader.isBlank()) return;
        instance.setServerSoftwareType(loader);
        try {
            instance.setModLoader(ModLoader.valueOf(loader.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void addDiscoveredInstance(Map<String, Instance> instances, Instance instance) {
        if (instance == null) return;
        BackendConfig config = instance.getBackendConfig();
        String identifier = config != null && config.credentials != null ? config.credentials.get("identifier") : null;
        String type = config == null || config.type == null ? "INSTANCE" : config.type.toUpperCase(Locale.ROOT);
        String key = identifier == null || identifier.isBlank() ? instance.getInstanceId() : type + ":" + identifier;
        instances.putIfAbsent(key, instance);
    }

    private boolean shouldProbe(Instance instance) {
        if (isLifecycleActive(instance.getState())) return true;
        BackendConfig config = instance.getBackendConfig();
        return config != null && config.type != null && !"LOCAL".equalsIgnoreCase(config.type);
    }

    private CompletableFuture<List<ServerSnapshot>> collectSnapshots(List<Instance> instances) {
        List<CompletableFuture<ServerSnapshot>> futures = instances.stream().map(this::collectSnapshot).toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).filter(this::isActiveSnapshot).toList());
    }

    private boolean isActiveSnapshot(ServerSnapshot snapshot) {
        if (isLifecycleActive(snapshot.instance().getState())) return true;
        boolean hasUsage = snapshot.uptimeMs() > 0 || snapshot.cpuPercent() > 0 || snapshot.memoryBytes() > 0;
        if (hasUsage) snapshot.instance().setState(InstanceState.RUNNING);
        return hasUsage;
    }

    private boolean isLifecycleActive(InstanceState state) {
        return state == InstanceState.RUNNING || state == InstanceState.STARTING || state == InstanceState.STOPPING;
    }

    private CompletableFuture<ServerSnapshot> collectSnapshot(Instance instance) {
        ResourceUsageFeature.ResourceUsage empty = emptyUsage();
        return resourceUsage(instance).completeOnTimeout(empty, 4, TimeUnit.SECONDS).exceptionally(ignored -> empty).thenApply(usage -> {
            List<UnifiedPlayer> players = snapshots.stream().filter(snapshot -> snapshot.instance().getInstanceId().equals(instance.getInstanceId()))
                    .findFirst().map(ServerSnapshot::players).orElseGet(List::of);
            return new ServerSnapshot(instance, usage.uptimeMs(), usage.cpuPercent(), usage.memoryBytes(), usage.memoryLimitBytes(), players);
        });
    }

    private CompletableFuture<ResourceUsageFeature.ResourceUsage> resourceUsage(Instance instance) {
        ServerBackend backend = instance.getBackend();
        if (backend == null) return CompletableFuture.completedFuture(emptyUsage());
        return backend.getFeature(ResourceUsageFeature.class).map(ResourceUsageFeature::getResources).orElseGet(() -> CompletableFuture.completedFuture(emptyUsage()));
    }

    private ResourceUsageFeature.ResourceUsage emptyUsage() {
        return new ResourceUsageFeature.ResourceUsage(0, 0, 0, 0, 0, 0, 0);
    }

    private void applySnapshots(List<ServerSnapshot> updated) {
        reconcileSubscriptions(updated.stream().map(ServerSnapshot::instance).toList());
        List<ServerSnapshot> merged = new ArrayList<>();
        for (ServerSnapshot snapshot : updated) {
            Map<UUID, UnifiedPlayer> players = knownPlayers.get(snapshot.instance().getInstanceId());
            merged.add(new ServerSnapshot(snapshot.instance(), snapshot.uptimeMs(), snapshot.cpuPercent(), snapshot.memoryBytes(), snapshot.memoryLimitBytes(),
                    players == null ? snapshot.players() : List.copyOf(players.values())));
        }
        snapshots = List.copyOf(merged);
    }

    private void reconcileSubscriptions(List<Instance> running) {
        Set<String> runningIds = running.stream().map(Instance::getInstanceId).collect(Collectors.toSet());
        List<String> removed = subscriptions.keySet().stream().filter(id -> !runningIds.contains(id)).toList();
        for (String id : removed) {
            PlayerSubscription subscription = subscriptions.remove(id);
            subscription.controller().removePlayerListener(subscription.listener());
            knownPlayers.remove(id);
            observedPlayers.remove(id);
            observedPlayersSince.remove(id);
            playerSubscriptionStartedAt.remove(id);
            initializedPlayers.remove(id);
        }
        for (Instance instance : running) {
            PlayerSubscription subscription = subscriptions.computeIfAbsent(instance.getInstanceId(), ignored -> {
                PlayerManagerController controller = PlayerManagerController.getOrCreate(instance);
                Consumer<PlayerManagerController.PlayerSnapshot> listener = snapshot -> applyPlayers(instance, snapshot);
                playerSubscriptionStartedAt.put(instance.getInstanceId(), System.currentTimeMillis());
                controller.addPlayerListener(listener);
                return new PlayerSubscription(controller, listener);
            });
            subscription.controller().refreshPlayerSnapshots();
        }
    }

    private void applyPlayers(Instance instance, PlayerManagerController.PlayerSnapshot snapshot) {
        String id = instance.getInstanceId();
        Map<UUID, UnifiedPlayer> current = new LinkedHashMap<>();
        for (UnifiedPlayer player : snapshot.players()) {
            if (player != null && player.isOnline() && player.getUuid() != null) {
                current.put(player.getUuid(), player);
            }
        }
        if (snapshot.baseline()) {
            observedPlayers.put(id, current);
            observedPlayersSince.put(id, System.currentTimeMillis());
            knownPlayers.put(id, current);
            initializedPlayers.add(id);
            updateSnapshotPlayers(id, current);
            return;
        }
        Map<UUID, UnifiedPlayer> observed = observedPlayers.get(id);
        if (observed == null || !observed.keySet().equals(current.keySet())) observedPlayersSince.put(id, System.currentTimeMillis());
        observedPlayers.put(id, current);
    }

    private void settlePlayerSnapshots(long now) {
        for (Map.Entry<String, Map<UUID, UnifiedPlayer>> entry : new ArrayList<>(observedPlayers.entrySet())) {
            String id = entry.getKey();
            Map<UUID, UnifiedPlayer> current = entry.getValue();
            long observedSince = observedPlayersSince.getOrDefault(id, now);
            if (now - observedSince < PLAYER_STABILITY_MS) continue;
            if (!initializedPlayers.contains(id)) {
                long subscribedAt = playerSubscriptionStartedAt.getOrDefault(id, now);
                if (current.isEmpty() && now - subscribedAt < PLAYER_INITIALIZATION_MS) continue;
                initializedPlayers.add(id);
                knownPlayers.put(id, current);
                updateSnapshotPlayers(id, current);
                continue;
            }
            Map<UUID, UnifiedPlayer> previous = knownPlayers.getOrDefault(id, Map.of());
            current.values().stream().filter(player -> !previous.containsKey(player.getUuid())).forEach(player -> enqueuePlayerEvent(player, true));
            previous.values().stream().filter(player -> !current.containsKey(player.getUuid())).forEach(player -> enqueuePlayerEvent(player, false));
            knownPlayers.put(id, current);
            updateSnapshotPlayers(id, current);
        }
    }

    private void updateSnapshotPlayers(String instanceId, Map<UUID, UnifiedPlayer> players) {
        List<ServerSnapshot> updated = snapshots.stream().map(snapshot -> snapshot.instance().getInstanceId().equals(instanceId)
                ? new ServerSnapshot(snapshot.instance(), snapshot.uptimeMs(), snapshot.cpuPercent(), snapshot.memoryBytes(), snapshot.memoryLimitBytes(), List.copyOf(players.values()))
                : snapshot).toList();
        snapshots = List.copyOf(updated);
    }

    private void enqueuePlayerEvent(UnifiedPlayer player, boolean joined) {
        PlayerEvent queued = playerEvents.peekLast();
        long createdAt = Math.max(System.currentTimeMillis(), queued == null ? 0L : queued.createdAt() + EVENT_DURATION_MS);
        String name = player.getName() == null || player.getName().isBlank() ? "Unknown" : player.getName();
        playerEvents.add(new PlayerEvent(player.getUuid(), name, joined, createdAt));
    }

    private PlayerEvent currentEvent(long now) {
        PlayerEvent event = playerEvents.peekFirst();
        while (event != null && now - event.createdAt() > EVENT_DURATION_MS) {
            playerEvents.pollFirst();
            event = playerEvents.peekFirst();
        }
        return event;
    }

    private String formatStatus(ServerSnapshot server) {
        if (server.instance().getState() == InstanceState.STARTING) return "Starting";
        if (server.instance().getState() == InstanceState.STOPPING) return "Stopping";
        long uptimeMs = server.uptimeMs();
        if (uptimeMs <= 0) return "Running";
        long minutes = uptimeMs / 60_000L;
        long hours = minutes / 60L;
        long days = hours / 24L;
        if (days > 0) return days + "d " + hours % 24 + "h";
        if (hours > 0) return hours + "h " + minutes % 60 + "m";
        return Math.max(1, minutes) + "m";
    }

    private String formatMemory(long used, long limit) {
        double usedGb = used / 1_073_741_824.0;
        if (limit <= 0) return String.format(Locale.ROOT, "%.1f GB", usedGb);
        return String.format(Locale.ROOT, "%.1f/%.1f GB", usedGb, limit / 1_073_741_824.0);
    }

    private String formatPlayers(List<UnifiedPlayer> players) {
        if (players.isEmpty()) return "No Players";
        String visible = String.join(", ", players.stream().map(UnifiedPlayer::getName).map(name -> name == null || name.isBlank() ? "Unknown" : name).limit(3).toList());
        return players.size() > 3 ? visible + " +" + (players.size() - 3) : visible;
    }

    private String eventMessage(PlayerEvent event) {
        return event.name() + (event.joined() ? " Joined" : " Left");
    }

    private int eventWidth(PlayerEvent event) {
        return EVENT_HORIZONTAL_PADDING * 2 + eventContentWidth(event);
    }

    private int eventContentWidth(PlayerEvent event) {
        String sign = event.joined() ? "+" : "-";
        return EVENT_HEAD_SIZE + 4 + TextRenderer.getWidth(sign) + 3 + TextRenderer.getWidth(eventMessage(event));
    }

    private float eventVisibility(PlayerEvent event, long now) {
        float progress = Math.clamp((now - event.createdAt()) / (float) EVENT_DURATION_MS, 0f, 1f);
        float exit = Math.clamp((progress - EVENT_EXIT_START) / (1f - EVENT_EXIT_START), 0f, 1f);
        return 1f - exit * exit * exit;
    }

    private Identifier playerFace(PlayerEvent event) {
        Identifier face = playerFaces.get(event.playerId());
        if (face != null) return face;
        requestPlayerFace(event.playerId(), event.name());
        return MISSING_FACE;
    }

    private void requestPlayerFace(UUID playerId, String playerName) {
        if (System.currentTimeMillis() < playerFaceRetryAt.getOrDefault(playerId, 0L)) return;
        if (!requestedPlayerFaces.add(playerId)) return;
        String name = playerName == null || playerName.isBlank() ? "Unknown" : playerName;
        new Account(name, playerId.toString(), null, 0).getFaceIdAsync().whenComplete((identifier, error) -> {
            requestedPlayerFaces.remove(playerId);
            if (error == null && identifier != null && !MISSING_FACE.equals(identifier)) {
                ResourceManager.getInstance().retainImage(identifier);
                playerFaces.put(playerId, identifier);
                playerFaceRetryAt.remove(playerId);
            } else {
                playerFaceRetryAt.put(playerId, System.currentTimeMillis() + FACE_RETRY_MS);
            }
        });
    }

    private String serverDescription(ServerSnapshot server) {
        return formatStatus(server) + " • " + Math.round(server.cpuPercent()) + "% CPU • "
                + formatMemory(server.memoryBytes(), server.memoryLimitBytes()) + " • " + formatPlayers(server.players());
    }

    private void drawExpandedContent(IDrawContext context, int mouseX, int mouseY) {
        List<ServerSnapshot> current = snapshots.stream().limit(8).toList();
        synchronizeRows(current);
        context.pushScissorState();
        context.enableScissor(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2);
        context.drawText("Running Servers", getX() + 6, getY() + 6, ThemeManager.getColor(ThemeColor.text), false);
        int rowY = getY() + 18;
        if (current.isEmpty()) {
            emptyRow.setActive(false);
            emptyRow.setPosition(getX() + 5, rowY);
            emptyRow.setSize(getWidth() - 10, 28);
            emptyRow.render(context, mouseX, mouseY, 0f);
        } else {
            for (ServerSnapshot server : current) {
                MountableButtonWidget row = rows.get(server.instance().getInstanceId());
                row.setName(server.instance().getName());
                row.setDescription(serverDescription(server));
                row.setAccent(ThemeManager.getDefaultAccent());
                row.setPosition(getX() + 5, rowY);
                row.setSize(getWidth() - 10, 28);
                row.render(context, mouseX, mouseY, 0f);
                rowY += 31;
            }
        }
        context.disableScissor();
        context.popScissorState();
    }

    private void synchronizeRows(List<ServerSnapshot> servers) {
        rows.keySet().retainAll(servers.stream().map(server -> server.instance().getInstanceId()).toList());
        for (ServerSnapshot server : servers) {
            rows.computeIfAbsent(server.instance().getInstanceId(), ignored -> {
                Identifier quickIcon = iconManager.getQuickIconId(server.instance());
                MountableButtonWidget row = new MountableButtonWidget.Builder(server.instance().getName())
                        .icon(quickIcon == null ? Identifier.icon("server.png") : quickIcon)
                        .onClick(() -> openServerDetails(server.instance())).build();
                iconManager.loadIconIdAsync(server.instance(), row::setIcon);
                iconManager.loadRemoteIconAsync(server.instance(), () -> iconManager.loadIconIdAsync(server.instance(), row::setIcon));
                return row;
            });
        }
    }

    private void openServerDetails(Instance instance) {
        RemotelyClient client = RemotelyClient.INSTANCE;
        if (client == null || instance == null) return;
        Object parent = ScreenManager.getInstance().getCurrentScreen();
        client.openInstanceInTerminal(parent, instance);
    }
}
