package redxax.oxy.remotely.quickserver;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.mixin.accessor.MinecraftAccessor;
import redxax.oxy.remotely.servers.QuickServerSyncManager;
import redxax.oxy.remotely.servers.ReProxyManager;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceFactory;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.instance.InstanceRepairer;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.localcontrol.LocalServerControllerClient;
import restudio.rebase.localcontrol.LocalServerControllerModels;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static redxax.oxy.remotely.config.Config.quickServerAutoRestart;
import static redxax.oxy.remotely.config.Config.quickServerKeepRunning;
import static redxax.oxy.remotely.config.Config.quickServerMirrorMods;
import static redxax.oxy.remotely.config.Config.quickServerPrecreate;
import static redxax.oxy.remotely.config.Config.remotelyDir;

public final class QuickServerManager {
    private static final Gson GSON = new Gson();
    private static final String QUICK_SERVER_ENABLED_KEY = "quickServer.enabled";
    private static final String SOURCE_WORLD_PATH_KEY = "quickServer.sourceWorldPath";
    private static final String SOURCE_WORLD_ID_KEY = "quickServer.sourceWorldId";
    private static final String LAST_WORLD_SYNC_KEY = "quickServer.lastWorldSync";
    private static final String LAST_MOD_SYNC_KEY = "quickServer.lastModSync";
    private static final String SYNC_STATE_KEY = "quickServer.syncState";
    private static final String LAST_SERVER_DIRTY_KEY = "quickServer.lastServerDirty";
    private static final String SYNC_STATE_CLEAN = "clean";
    private static final String SYNC_STATE_SERVER_DIRTY = "serverDirty";
    private static final String SYNC_STATE_SYNCING_BACK = "syncingBack";
    private static final String METADATA_FILE = "remotely-quick-server.properties";
    private static final String MOD_MANIFEST_FILE = "quick-server-mods.json";
    private static final Pattern GAME_VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+(?:\\.\\d+)?(?:-(?:pre|rc|snapshot)-\\d+)?");
    private static final Path QUICK_SERVERS_DIR = remotelyDir.resolve("instances").resolve("quick-servers");
    private static final Set<String> WORLD_SYNC_EXCLUDES = Set.of("session.lock", "remotely-quick-server.properties");
    private static final Set<String> MOD_METADATA_ENTRIES = Set.of("fabric.mod.json", "quilt.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml");
    private static final Set<String> activeQuickServers = ConcurrentHashMap.newKeySet();
    private static final Set<String> activeDisconnectStops = ConcurrentHashMap.newKeySet();
    private static final Map<String, AtomicBoolean> activeTasks = new ConcurrentHashMap<>();
    private static final AtomicBoolean ownerTransferInProgress = new AtomicBoolean(false);
    private static volatile String precreatedWorldId = "";
    private static volatile String activeQuickServerInstanceId = "";
    private static volatile String connectedQuickServerInstanceId = "";
    private static volatile IntegratedServer pendingOwnerTransferServer;
    private static volatile String pendingOwnerTransferAddress = "";
    private static volatile long pendingOwnerTransferDeadlineMillis = 0L;
    private static volatile String pendingOwnerConnectAddress = "";
    private static volatile long pendingOwnerConnectAfterMillis = 0L;
    private static final AtomicBoolean ownerConnectStarted = new AtomicBoolean(false);
    private static volatile long quickServerJoinCleanupUntilMillis = 0L;

    private QuickServerManager() {
    }

    public static void startForCurrentWorld(Screen parent) {
        startForCurrentWorld(parent, state -> {});
    }

    public static void startForCurrentWorld(Screen parent, Consumer<String> stateConsumer) {
        WorldContext world = currentWorld();
        if (world == null) {
            if (openCurrentWorldTerminal(parent)) {
                stateConsumer.accept("Online");
                return;
            }
            stateConsumer.accept("Unavailable");
            Notification notification = new Notification.Builder().message("Quick Server").description("Open A Singleplayer World First").type(Notification.Type.WARN).autoSlideOut(true).build();
            change(notification, "Quick Server Unavailable", "Open A Singleplayer World First", Notification.Type.WARN, null, false);
            return;
        }
        Instance mapped = findMappedInstance(world);
        if (mapped != null && isLocalServerRunning(mapped)) {
            mapped.setState(InstanceState.RUNNING);
            setActiveQuickServer(mapped);
            registerTerminalTab(mapped);
            stateConsumer.accept("Online");
            connectOwnerToLocalServer(mapped);
            return;
        }
        runForWorld(world, stateConsumer, true);
    }

    public static void precreateForCurrentWorld() {
        if (!quickServerPrecreate()) {
            return;
        }
        WorldContext world = currentWorld();
        if (world == null || world.worldId().equals(precreatedWorldId)) {
            return;
        }
        precreatedWorldId = world.worldId();
        runForWorld(world, state -> {}, false);
    }

    public static void clientTick() {
        precreateForCurrentWorld();
        advanceOwnerTransfer();
        advanceOwnerConnect();
        clearJoinHandoffScreen();
        handleOwnerDisconnect();
    }

    public static void shutdownAll() {
        for (String instanceId : activeQuickServers) {
            Instance instance = resolveInstance(instanceId);
            if (instance == null) {
                continue;
            }
            ReProxyManager.stopQuietly(instance.getPort(), null);
            if (!instance.isLocalLifecyclePersistent()) {
                LocalServerControllerClient.stop(instance);
                waitForStop(instance);
                syncServerWorldBack(instance);
            }
            activeQuickServers.remove(instanceId);
        }
    }

    public static boolean isQuickServerOnline() {
        Instance instance = resolveActiveQuickServer();
        return instance != null && isQuickServerManagedRunning(instance);
    }

    public static boolean copyCurrentWorldAddress() {
        Instance instance = resolveActiveQuickServer();
        if (instance == null || !isQuickServerManagedRunning(instance)) {
            return false;
        }
        registerTerminalTab(instance);
        copyAddress(quickServerAddress(instance));
        return true;
    }

    public static void activateGameButton(Screen parent) {
        if (currentWorld() != null) {
            startForCurrentWorld(parent);
            return;
        }
        if (openConnectedQuickServerTerminal(parent)) {
            return;
        }
        ScreenManager.getInstance().execute(() -> new Notification("Quick Server", "No Linked Server", Notification.Type.WARN));
    }

    public static boolean isInQuickServer() {
        return resolveConnectedQuickServer() != null;
    }

    public static boolean shouldShowGameButton() {
        return currentWorld() != null || resolveConnectedQuickServer() != null;
    }

    public static boolean openCurrentWorldTerminal(Object parent) {
        Instance instance = resolveActiveQuickServer();
        if (instance == null || !isQuickServerManagedRunning(instance)) {
            return false;
        }
        registerTerminalTab(instance);
        ScreenManager.getInstance().execute(() -> RemotelyClient.INSTANCE.openInstanceInTerminal(parent, instance));
        return true;
    }

    public static boolean openConnectedQuickServerTerminal(Object parent) {
        Instance instance = resolveConnectedQuickServer();
        if (instance == null) {
            return false;
        }
        refreshConnectedQuickServerState(instance);
        setActiveQuickServer(instance);
        registerTerminalTab(instance);
        ScreenManager.getInstance().execute(() -> RemotelyClient.INSTANCE.openInstanceInTerminal(parent, instance));
        return true;
    }

    public static void runForWorld(WorldContext world, Consumer<String> stateConsumer, boolean startServer) {
        AtomicBoolean running = activeTasks.computeIfAbsent(world.worldId(), id -> new AtomicBoolean(false));
        Notification notification = new Notification.Builder().message("Quick Server").description(world.worldName()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        if (!running.compareAndSet(false, true)) {
            if (recoverRunningQuickServer(world, stateConsumer, notification)) {
                running.set(false);
                return;
            }
            stateConsumer.accept("Starting");
            change(notification, "Quick Server", "Already Working", Notification.Type.INFO, null, false);
            return;
        }
        stateConsumer.accept(startServer ? "Starting" : "Syncing");
        CompletableFuture.runAsync(() -> {
            try {
                change(notification, startServer ? "Starting Quick Server" : "Preparing Quick Server", world.worldName(), Notification.Type.INFO, null, true);
                Instance instance = getOrCreateInstance(world);
                boolean alreadyRunning = isLocalServerRunning(instance);
                boolean serverDirty = isServerDirty(instance);
                if (!alreadyRunning && serverDirty) {
                    change(notification, startServer ? "Recovering World" : "Recovery Pending", instance.getName(), Notification.Type.INFO, null, true);
                    resolveDirtyWorldState(world, instance, notification, startServer);
                    serverDirty = isServerDirty(instance);
                }
                if (!alreadyRunning) {
                    if (serverDirty && !startServer) {
                        change(notification, "Recovery Pending", "Start Server To Recover", Notification.Type.WARN, null, false);
                        return;
                    }
                    change(notification, "Syncing World", world.worldName(), Notification.Type.INFO, null, true);
                    syncWorldToServer(world, instance);
                    syncMods(instance, notification);
                    ensureDynamicServerPort(instance, notification);
                    prepareServer(instance, notification);
                }
                if (startServer) {
                    change(notification, "Starting Server", instance.getName(), Notification.Type.INFO, null, true);
                    markServerDirty(instance);
                    startLocalServer(instance, notification);
                    setActiveQuickServer(instance);
                    registerTerminalTab(instance);
                    stateConsumer.accept("Online");
                    change(notification, "Joining Server", "127.0.0.1:" + instance.getPort(), Notification.Type.INFO, null, true);
                    connectOwnerToLocalServer(instance);
                    if (ReProxyManager.isForwarded(instance)) {
                        String address = quickServerAddress(instance);
                        copyAddress(address);
                        change(notification, "Quick Server Online", address, Notification.Type.SUCCESS, () -> copyAddress(address), false);
                    } else {
                        change(notification, "Quick Server Online", "Starting ReProxy", Notification.Type.INFO, null, false);
                        startReProxy(instance, () -> ScreenManager.getInstance().execute(() -> {
                            boolean forwarded = ReProxyManager.isForwarded(instance);
                            stateConsumer.accept("Online");
                            String address = quickServerAddress(instance);
                            if (forwarded) {
                                copyAddress(address);
                                notification.change("Quick Server Online", address, Notification.Type.SUCCESS, () -> copyAddress(address));
                            } else {
                                notification.change("Quick Server Online", address, Notification.Type.SUCCESS, null);
                            }
                            notification.loading = false;
                        }));
                    }
                } else {
                    change(notification, "Quick Server Ready", world.worldName(), Notification.Type.SUCCESS, null, false);
                }
                instance.save().join();
            } catch (Exception e) {
                stateConsumer.accept("Unavailable");
                change(notification, "Quick Server Failed", cleanMessage(e), Notification.Type.ERROR, null, false);
            } finally {
                running.set(false);
            }
        });
    }

    private static boolean recoverRunningQuickServer(WorldContext world, Consumer<String> stateConsumer, Notification notification) {
        Instance instance = findMappedInstance(world);
        if (instance == null || !isQuickServer(instance) || !isLocalServerRunning(instance)) {
            return false;
        }
        instance.setState(InstanceState.RUNNING);
        setActiveQuickServer(instance);
        registerTerminalTab(instance);
        stateConsumer.accept("Online");
        String address = quickServerAddress(instance);
        copyAddress(address);
        change(notification, "Quick Server Online", address, Notification.Type.SUCCESS, () -> copyAddress(address), false);
        connectOwnerToLocalServer(instance);
        return true;
    }

    private static void handleOwnerDisconnect() {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData serverData = minecraft.getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
            Instance connected = resolveConnectedQuickServer();
            if (connected != null && connected.getInstanceId() != null) {
                connectedQuickServerInstanceId = connected.getInstanceId();
            }
            return;
        }
        String instanceId = connectedQuickServerInstanceId;
        if (instanceId.isBlank() || ownerTransferInProgress.get() || !pendingOwnerConnectAddress.isBlank()) {
            return;
        }
        connectedQuickServerInstanceId = "";
        Instance instance = resolveInstance(instanceId);
        if (instance == null || instance.isLocalLifecyclePersistent() || !isLocalServerRunning(instance) || !activeDisconnectStops.add(instanceId)) {
            return;
        }
        new Thread(() -> {
            try {
                ReProxyManager.stopQuietly(instance.getPort(), null);
                LocalServerControllerClient.stop(instance);
                waitForStop(instance);
                syncServerWorldBack(instance);
                activeQuickServers.remove(instanceId);
            } finally {
                activeDisconnectStops.remove(instanceId);
            }
        }, "Remotely Quick Server Disconnect Stop").start();
    }

    public static WorldContext currentWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return null;
        }
        Path worldPath = resolveWorldPath(server);
        if (worldPath == null || !Files.isDirectory(worldPath)) {
            return null;
        }
        String worldName = worldPath.getFileName() != null ? worldPath.getFileName().toString() : "World";
        String worldId = stableWorldId(worldPath);
        return new WorldContext(worldId, worldName, worldPath.toAbsolutePath().normalize());
    }

    private static Instance getOrCreateInstance(WorldContext world) throws IOException {
        Instance existing = findMappedInstance(world);
        if (existing != null) {
            applyRuntimeDefaults(existing, world);
            existing.save().join();
            return existing;
        }
        Path instancePath = QUICK_SERVERS_DIR.resolve(sanitizePathName(world.worldName()) + "-" + world.worldId()).normalize();
        Files.createDirectories(instancePath);
        Instance instance = new Instance(world.worldName() + " | Quick Server", resolveGameVersion(), instancePath.toString());
        instance.setServer(true);
        instance.setHidden(true);
        instance.setImported(false);
        applyRuntimeDefaults(instance, world);
        instance.getServerProperties().setProperty("level-name", "world");
        instance.getServerProperties().setProperty("server-port", String.valueOf(resolvePort()));
        instance.getServerProperties().setProperty("online-mode", "true");
        instance.getServerProperties().setProperty("enable-command-block", "true");
        instance.getServerProperties().setProperty("motd", "Remotely | Quick Server");
        instance.getServerProperties().setProperty("eula", "true");
        instance.saveServerProperties().join();
        instance.save().join();
        InstanceManager.getInstance().registerLocalInstance(instance);
        writeWorldMetadata(world, instance);
        return instance;
    }

    private static Instance findMappedInstance(WorldContext world) {
        try {
            Properties metadata = readWorldMetadata(world.worldPath());
            String instanceId = metadata.getProperty("instanceId", "");
            if (!instanceId.isBlank()) {
                Instance byId = InstanceManager.getInstance().getInstanceById(instanceId);
                if (byId != null) {
                    return byId;
                }
            }
            String instancePath = metadata.getProperty("instancePath", "");
            if (!instancePath.isBlank()) {
                Instance loaded = Instance.load(Path.of(instancePath));
                if (loaded != null) {
                    InstanceManager.getInstance().registerLocalInstance(loaded);
                    return loaded;
                }
            }
            for (Instance instance : InstanceManager.getInstance().getLocalInstances()) {
                if ("true".equalsIgnoreCase(instance.getSettings().getProperty(QUICK_SERVER_ENABLED_KEY))
                    && Objects.equals(world.worldId(), instance.getSettings().getProperty(SOURCE_WORLD_ID_KEY))) {
                    return instance;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Instance resolveActiveQuickServer() {
        WorldContext world = currentWorld();
        if (world != null) {
            Instance mapped = findMappedInstance(world);
            if (mapped != null) {
                setActiveQuickServer(mapped);
                return mapped;
            }
        }
        if (!activeQuickServerInstanceId.isBlank()) {
            Instance active = resolveInstance(activeQuickServerInstanceId);
            if (active != null) {
                return active;
            }
        }
        for (Instance instance : allKnownQuickServers()) {
            if (isQuickServerManagedRunning(instance)) {
                setActiveQuickServer(instance);
                return instance;
            }
        }
        return null;
    }

    private static Instance resolveConnectedQuickServer() {
        ServerData serverData = Minecraft.getInstance().getCurrentServer();
        if (serverData == null || serverData.ip == null || serverData.ip.isBlank()) {
            return null;
        }
        if (!activeQuickServerInstanceId.isBlank()) {
            Instance active = resolveInstance(activeQuickServerInstanceId);
            if (isConnectedQuickServer(serverData.ip, active)) {
                setActiveQuickServer(active);
                return active;
            }
        }
        for (Instance instance : allKnownQuickServers()) {
            if (isConnectedQuickServer(serverData.ip, instance)) {
                setActiveQuickServer(instance);
                return instance;
            }
        }
        return null;
    }

    private static List<Instance> allKnownQuickServers() {
        List<Instance> instances = new ArrayList<>();
        Set<String> seen = ConcurrentHashMap.newKeySet();
        for (Instance instance : InstanceManager.getInstance().getLocalInstances()) {
            if (isQuickServer(instance) && seen.add(quickServerIdentity(instance))) {
                instances.add(instance);
            }
        }
        if (!Files.isDirectory(QUICK_SERVERS_DIR)) {
            return instances;
        }
        try (var paths = Files.list(QUICK_SERVERS_DIR)) {
            paths.filter(Files::isDirectory).forEach(path -> {
                try {
                    Instance instance = Instance.load(path);
                    if (isQuickServer(instance) && seen.add(quickServerIdentity(instance))) {
                        InstanceManager.getInstance().registerLocalInstance(instance);
                        instances.add(instance);
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        return instances;
    }

    private static String quickServerIdentity(Instance instance) {
        if (instance == null) {
            return "";
        }
        if (instance.getInstanceId() != null && !instance.getInstanceId().isBlank()) {
            return instance.getInstanceId();
        }
        return instance.getPath() == null ? "" : instance.getPath();
    }

    private static boolean isQuickServer(Instance instance) {
        return instance != null && "true".equalsIgnoreCase(instance.getSettings().getProperty(QUICK_SERVER_ENABLED_KEY));
    }

    private static boolean isConnectedQuickServer(String address, Instance instance) {
        if (!isQuickServer(instance)) {
            return false;
        }
        String forwarded = ReProxyManager.getForwardedAddress(instance);
        if (forwarded != null && !forwarded.isBlank() && normalizeAddress(forwarded).equals(normalizeAddress(address))) {
            return true;
        }
        ServerAddress serverAddress = ServerAddress.parseString(address);
        String host = normalizeHost(serverAddress.getHost());
        return serverAddress.getPort() == configuredPort(instance) && isLocalHost(host);
    }

    private static void refreshConnectedQuickServerState(Instance instance) {
        if (instance == null) {
            return;
        }
        LocalServerControllerModels.StatusResponse status = LocalServerControllerClient.status(instance);
        if (status != null && status.knownSession && status.pid > 0 && "RUNNING".equalsIgnoreCase(status.state)) {
            instance.setState(InstanceState.RUNNING);
            return;
        }
        if (isMinecraftServerReady(instance)) {
            instance.setState(InstanceState.RUNNING);
        }
    }

    private static String normalizeAddress(String address) {
        ServerAddress serverAddress = ServerAddress.parseString(address);
        return normalizeHost(serverAddress.getHost()) + ":" + serverAddress.getPort();
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isLocalHost(String host) {
        return host.equals("127.0.0.1") || host.equals("localhost") || host.equals("0.0.0.0") || host.equals("::1") || host.equals("0:0:0:0:0:0:0:1");
    }

    private static void setActiveQuickServer(Instance instance) {
        if (instance != null && instance.getInstanceId() != null) {
            activeQuickServerInstanceId = instance.getInstanceId();
            activeQuickServers.add(instance.getInstanceId());
        }
    }

    private static void applyRuntimeDefaults(Instance instance, WorldContext world) {
        instance.setVersionId(resolveGameVersion());
        instance.setModLoader(ModLoader.VANILLA);
        instance.setModLoaderVersion("");
        instance.setLocalLifecyclePersistent(quickServerKeepRunning());
        instance.setLocalRestartOnCrash(quickServerAutoRestart());
        instance.getSettings().setProperty(QUICK_SERVER_ENABLED_KEY, "true");
        instance.getSettings().setProperty(SOURCE_WORLD_PATH_KEY, world.worldPath().toString());
        instance.getSettings().setProperty(SOURCE_WORLD_ID_KEY, world.worldId());
    }

    private static void syncWorldToServer(WorldContext world, Instance instance) throws IOException {
        saveCurrentWorld();
        Path target = Path.of(instance.getPath()).resolve("world");
        QuickServerSyncManager.syncWorld(world.worldPath(), target);
        markServerClean(instance);
    }

    private static void syncServerWorldBack(Instance instance) {
        try {
            String sourceWorldPath = instance.getSettings().getProperty(SOURCE_WORLD_PATH_KEY, "");
            if (sourceWorldPath.isBlank()) {
                return;
            }
            Path source = Path.of(instance.getPath()).resolve("world");
            Path target = Path.of(sourceWorldPath);
            if (Files.isDirectory(source) && Files.isDirectory(target)) {
                instance.getSettings().setProperty(SYNC_STATE_KEY, SYNC_STATE_SYNCING_BACK);
                instance.save().join();
                QuickServerSyncManager.syncWorld(source, target);
                markServerClean(instance);
            }
        } catch (Exception e) {
            ScreenManager.getInstance().execute(() -> new Notification("Quick Server Sync Failed", cleanMessage(e), Notification.Type.ERROR));
        }
    }

    private static void resolveDirtyWorldState(WorldContext world, Instance instance, Notification notification, boolean startServer) throws IOException {
        if (!isServerDirty(instance)) {
            return;
        }
        if (!serverWorldLooksNewer(instance)) {
            markServerClean(instance);
            return;
        }
        if (startServer && sourceChangedAfterDirty(instance)) {
            change(notification, "Backing Up World", world.worldName(), Notification.Type.INFO, null, true);
            backupSourceWorld(world, instance);
        }
        syncServerWorldBack(instance);
    }

    private static boolean isServerDirty(Instance instance) {
        String state = instance.getSettings().getProperty(SYNC_STATE_KEY);
        if (SYNC_STATE_SERVER_DIRTY.equalsIgnoreCase(state) || SYNC_STATE_SYNCING_BACK.equalsIgnoreCase(state)) {
            return true;
        }
        if (SYNC_STATE_CLEAN.equalsIgnoreCase(state)) {
            return false;
        }
        return serverWorldLooksNewer(instance);
    }

    private static void markServerDirty(Instance instance) {
        instance.getSettings().setProperty(SYNC_STATE_KEY, SYNC_STATE_SERVER_DIRTY);
        instance.getSettings().setProperty(LAST_SERVER_DIRTY_KEY, Instant.now().toString());
        instance.save().join();
    }

    private static void markServerClean(Instance instance) {
        instance.getSettings().setProperty(SYNC_STATE_KEY, SYNC_STATE_CLEAN);
        instance.getSettings().setProperty(LAST_WORLD_SYNC_KEY, Instant.now().toString());
        instance.save().join();
    }

    private static boolean serverWorldLooksNewer(Instance instance) {
        String sourceWorldPath = instance.getSettings().getProperty(SOURCE_WORLD_PATH_KEY, "");
        if (sourceWorldPath.isBlank()) {
            return false;
        }
        Path serverWorld = Path.of(instance.getPath()).resolve("world");
        Path sourceWorld = Path.of(sourceWorldPath);
        if (!Files.isDirectory(serverWorld) || !Files.isDirectory(sourceWorld)) {
            return false;
        }
        return newestWorldModifiedMillis(serverWorld) > newestWorldModifiedMillis(sourceWorld) + 1000L;
    }

    private static boolean sourceChangedAfterDirty(Instance instance) {
        String dirtyAt = instance.getSettings().getProperty(LAST_SERVER_DIRTY_KEY, "");
        if (dirtyAt.isBlank()) {
            return false;
        }
        String sourceWorldPath = instance.getSettings().getProperty(SOURCE_WORLD_PATH_KEY, "");
        if (sourceWorldPath.isBlank()) {
            return false;
        }
        try {
            Path sourceWorld = Path.of(sourceWorldPath);
            return Files.isDirectory(sourceWorld) && newestWorldModifiedMillis(sourceWorld) > Instant.parse(dirtyAt).toEpochMilli() + 1000L;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void backupSourceWorld(WorldContext world, Instance instance) throws IOException {
        saveCurrentWorld();
        String stamp = Instant.now().toString().replace(':', '-').replace('.', '-');
        Path backup = Path.of(instance.getPath()).resolve("source-backups").resolve(stamp);
        QuickServerSyncManager.syncWorld(world.worldPath(), backup);
    }

    private static long newestWorldModifiedMillis(Path root) {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                .filter(path -> !isExcluded(root.relativize(path), WORLD_SYNC_EXCLUDES))
                .mapToLong(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis();
                    } catch (IOException ignored) {
                        return 0L;
                    }
                })
                .max()
                .orElse(0L);
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static void syncMods(Instance instance, Notification notification) throws IOException {
        if (!quickServerMirrorMods()) {
            return;
        }
        change(notification, "Syncing Mods", instance.getName(), Notification.Type.INFO, null, true);
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        Path source = gameDir.resolve("mods");
        if (!Files.isDirectory(source)) {
            return;
        }
        Path target = Path.of(instance.getPath()).resolve("mods");
        Files.createDirectories(target);
        Map<String, ModEntry> previous = readModManifest(instance);
        Map<String, ModEntry> next = new HashMap<>();
        int skipped = 0;
        try (var stream = Files.list(source)) {
            for (Path mod : stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                if (isClientOnlyMod(mod)) {
                    skipped++;
                    continue;
                }
                ModEntry entry = ModEntry.from(mod);
                next.put(mod.getFileName().toString(), entry);
                ModEntry old = previous.get(mod.getFileName().toString());
                Path targetFile = target.resolve(mod.getFileName().toString());
                if (!entry.equals(old) || !Files.exists(targetFile)) {
                    Files.copy(mod, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        try (var stream = Files.list(target)) {
            for (Path mod : stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                if (!next.containsKey(mod.getFileName().toString())) {
                    Files.deleteIfExists(mod);
                }
            }
        }
        writeModManifest(instance, next);
        instance.getSettings().setProperty(LAST_MOD_SYNC_KEY, Instant.now().toString());
        if (skipped > 0) {
            change(notification, "Syncing Mods", "Skipped " + skipped + " Client Mods", Notification.Type.INFO, null, true);
        }
    }

    private static void prepareServer(Instance instance, Notification notification) {
        Path jar = instance.resolveServerJarPath();
        instance.getServerProperties().setProperty("eula", "true");
        instance.getServerProperties().setProperty("motd", "Remotely | Quick Server");
        change(notification, "Preparing Server", instance.getName(), Notification.Type.INFO, null, true);
        CompletableFuture<Void> jarFuture = Files.isRegularFile(jar)
            ? CompletableFuture.completedFuture(null)
            : new InstanceFactory().downloadMissingServerJar(instance, notification);
        jarFuture.thenCompose(v -> InstanceRepairer.createStartScript(instance))
            .thenCompose(v -> instance.saveServerProperties())
            .thenCompose(v -> instance.save())
            .join();
    }

    private static void ensureDynamicServerPort(Instance instance, Notification notification) throws IOException {
        int currentPort = configuredPort(instance);
        if (currentPort > 0 && currentPort <= 65535 && isPortAvailable(currentPort)) {
            return;
        }
        int nextPort = resolvePort();
        if (nextPort <= 0 || nextPort > 65535 || !isPortAvailable(nextPort)) {
            throw new IOException("No Available Server Port");
        }
        if (currentPort > 0 && ReProxyManager.isForwarded(currentPort)) {
            ReProxyManager.stopQuietly(currentPort, null);
        }
        instance.getServerProperties().setProperty("server-port", String.valueOf(nextPort));
        instance.saveServerProperties().join();
        change(notification, "Binding Port", "Using " + nextPort, Notification.Type.INFO, null, true);
    }

    private static int configuredPort(Instance instance) {
        String value = instance.getServerProperties().getProperty("server-port");
        if (value != null && !value.isBlank()) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return instance.getPort();
    }

    private static void startLocalServer(Instance instance, Notification notification) throws IOException {
        LocalServerControllerModels.StatusResponse status = LocalServerControllerClient.status(instance);
        if (status == null || !status.knownSession || status.pid <= 0 || !"RUNNING".equalsIgnoreCase(status.state)) {
            instance.setState(InstanceState.STARTING);
            LocalServerControllerClient.start(instance);
            activeQuickServers.add(instance.getInstanceId());
        }
        long deadline = System.currentTimeMillis() + 120_000L;
        boolean processStarted = false;
        int readyPings = 0;
        while (System.currentTimeMillis() < deadline) {
            status = LocalServerControllerClient.status(instance);
            if (status != null && status.pid > 0 && "RUNNING".equalsIgnoreCase(status.state)) {
                instance.setState(InstanceState.RUNNING);
                if (!processStarted) {
                    processStarted = true;
                    change(notification, "Waiting Server", instance.getName(), Notification.Type.INFO, null, true);
                }
                if (isMinecraftServerReady(instance)) {
                    readyPings++;
                    if (readyPings >= 2) {
                        return;
                    }
                } else {
                    readyPings = 0;
                }
            }
            sleep(500);
        }
        throw new IOException(processStarted ? "Server Is Still Starting" : "Server Did Not Start");
    }

    private static boolean isLocalServerRunning(Instance instance) {
        LocalServerControllerModels.StatusResponse status = LocalServerControllerClient.status(instance);
        return status != null && status.knownSession && status.pid > 0 && "RUNNING".equalsIgnoreCase(status.state);
    }

    private static boolean isQuickServerManagedRunning(Instance instance) {
        return isLocalServerRunning(instance) || (ReProxyManager.isForwarded(instance) && isMinecraftServerReady(instance));
    }

    private static boolean isMinecraftServerReady(Instance instance) {
        int port = configuredPort(instance);
        if (port <= 0 || port > 65535) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 1500);
            socket.setSoTimeout(1500);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream handshakeOutput = new DataOutputStream(handshake);
            writeVarInt(handshakeOutput, 0);
            writeVarInt(handshakeOutput, SharedConstants.getProtocolVersion());
            writeString(handshakeOutput, "127.0.0.1");
            handshakeOutput.writeShort(port);
            writeVarInt(handshakeOutput, 1);
            writePacket(output, handshake.toByteArray());
            ByteArrayOutputStream request = new ByteArrayOutputStream();
            DataOutputStream requestOutput = new DataOutputStream(request);
            writeVarInt(requestOutput, 0);
            writePacket(output, request.toByteArray());
            DataInputStream input = new DataInputStream(socket.getInputStream());
            int length = readVarInt(input);
            if (length <= 0) {
                return false;
            }
            int packetId = readVarInt(input);
            if (packetId != 0) {
                return false;
            }
            String response = readString(input, 32767);
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            return root.has("version") && root.has("description");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void writePacket(OutputStream output, byte[] packet) throws IOException {
        writeVarInt(output, packet.length);
        output.write(packet);
        output.flush();
    }

    private static void writeString(OutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(output, bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, int maxLength) throws IOException {
        int length = readVarInt(input);
        if (length < 0 || length > maxLength * 4) {
            throw new IOException("Invalid Server Status");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Incomplete Server Status");
        }
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.length() > maxLength) {
            throw new IOException("Server Status Too Long");
        }
        return value;
    }

    private static void writeVarInt(OutputStream output, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            output.write(value & 0x7F | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }

    private static int readVarInt(DataInputStream input) throws IOException {
        int value = 0;
        int position = 0;
        byte current;
        do {
            current = input.readByte();
            value |= (current & 0x7F) << position;
            position += 7;
            if (position >= 35) {
                throw new IOException("Invalid VarInt");
            }
        } while ((current & 0x80) != 0);
        return value;
    }

    private static void waitForStop(Instance instance) {
        long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline) {
            LocalServerControllerModels.StatusResponse status = LocalServerControllerClient.status(instance);
            if (status == null || !status.knownSession || status.pid <= 0 || "STOPPED".equalsIgnoreCase(status.state) || "CRASHED".equalsIgnoreCase(status.state)) {
                return;
            }
            sleep(500);
        }
    }

    private static Path resolveWorldPath(IntegratedServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    private static void saveCurrentWorld() throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Runnable saveTask = () -> {
            try {
                server.saveEverything(false, true, true);
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        };
        server.execute(saveTask);
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IOException("World Save Timed Out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("World Save Interrupted", e);
        }
        if (error.get() != null) {
            throw new IOException("World Save Failed", error.get());
        }
    }

    private static String resolveGameVersion() {
        //#if MC >= 1.21.6 || MC >= 26.1
        String currentVersion = extractGameVersion(SharedConstants.getCurrentVersion().name());
        //#else
        //$$ String currentVersion = extractGameVersion(SharedConstants.getCurrentVersion().getName());
        //#endif
        if (currentVersion != null) {
            return currentVersion;
        }
        try {
            if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
                String hostVersion = extractGameVersion(RemotelyClient.INSTANCE.getHost().getGameVersion());
                if (hostVersion != null) {
                    return hostVersion;
                }
            }
        } catch (Exception ignored) {
        }
        String launchedVersion = extractGameVersion(Minecraft.getInstance().getLaunchedVersion());
        if (launchedVersion != null) {
            return launchedVersion;
        }
        return "latest";
    }

    private static boolean isClientOnlyMod(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String name : MOD_METADATA_ENTRIES) {
                ZipEntry entry = zip.getEntry(name);
                if (entry == null) {
                    continue;
                }
                String content = new String(zip.getInputStream(entry).readAllBytes()).toLowerCase(Locale.ROOT);
                if (content.contains("\"environment\":\"client\"")
                    || content.contains("\"environment\": \"client\"")
                    || content.contains("side=\"client\"")
                    || content.contains("side = \"client\"")
                    || content.contains("clientsideonly=true")
                    || content.contains("clientsideonly = true")) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static boolean isExcluded(Path relative, Set<String> excludes) {
        for (Path part : relative) {
            if (excludes.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static int resolvePort() {
        for (int port = 25565; port <= 25665; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException ignored) {
            return -1;
        }
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String extractGameVersion(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        Matcher matcher = GAME_VERSION_PATTERN.matcher(version);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private static boolean quickServerPrecreate() {
        return quickServerPrecreate;
    }

    private static boolean quickServerKeepRunning() {
        return quickServerKeepRunning;
    }

    private static boolean quickServerAutoRestart() {
        return quickServerAutoRestart;
    }

    private static boolean quickServerMirrorMods() {
        return quickServerMirrorMods;
    }

    private static String stableWorldId(Path worldPath) {
        String input = worldPath.toAbsolutePath().normalize().toString();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(input.getBytes());
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                builder.append(String.format(Locale.ROOT, "%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private static String sanitizePathName(String value) {
        String sanitized = value == null ? "world" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "world" : sanitized;
    }

    private static Properties readWorldMetadata(Path worldPath) {
        Properties properties = new Properties();
        Path file = worldPath.resolve(METADATA_FILE);
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            } catch (IOException ignored) {
            }
        }
        return properties;
    }

    private static void writeWorldMetadata(WorldContext world, Instance instance) {
        Properties properties = readWorldMetadata(world.worldPath());
        properties.setProperty("instanceId", instance.getInstanceId());
        properties.setProperty("instancePath", instance.getPath());
        properties.setProperty("worldId", world.worldId());
        try (var output = Files.newOutputStream(world.worldPath().resolve(METADATA_FILE))) {
            properties.store(output, "Remotely Quick Server");
        } catch (IOException ignored) {
        }
    }

    private static Map<String, ModEntry> readModManifest(Instance instance) {
        Path file = Path.of(instance.getPath()).resolve(MOD_MANIFEST_FILE);
        if (!Files.isRegularFile(file)) {
            return new HashMap<>();
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            Map<String, ModEntry> manifest = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                manifest.put(entry.getKey(), new ModEntry(value.get("size").getAsLong(), value.get("modified").getAsLong()));
            }
            return manifest;
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private static void writeModManifest(Instance instance, Map<String, ModEntry> manifest) throws IOException {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, ModEntry> entry : manifest.entrySet()) {
            JsonObject value = new JsonObject();
            value.addProperty("size", entry.getValue().size());
            value.addProperty("modified", entry.getValue().modified());
            root.add(entry.getKey(), value);
        }
        Files.writeString(Path.of(instance.getPath()).resolve(MOD_MANIFEST_FILE), GSON.toJson(root));
    }

    private static Instance resolveInstance(String instanceId) {
        try {
            return InstanceManager.getInstance().getInstanceById(instanceId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void startReProxy(Instance instance, Runnable onComplete) {
        ReProxyManager.startQuietly(instance, onComplete);
    }

    private static void connectOwnerToLocalServer(Instance instance) {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || currentWorld() == null) {
            return;
        }
        if (!ownerTransferInProgress.compareAndSet(false, true)) {
            return;
        }
        String address = "127.0.0.1:" + instance.getPort();
        quickServerJoinCleanupUntilMillis = System.currentTimeMillis() + 120_000L;
        pendingOwnerTransferServer = server;
        pendingOwnerTransferAddress = address;
        pendingOwnerTransferDeadlineMillis = System.currentTimeMillis() + 60_000L;
        minecraft.execute(() -> beginOwnerTransfer(server));
    }

    private static void beginOwnerTransfer(IntegratedServer server) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setOverlay(null);
        minecraft.setScreen(new QuickServerJoinScreen());
        CompletableFuture.runAsync(() -> {
            try {
                server.halt(false);
            } catch (Exception e) {
                ownerTransferInProgress.set(false);
                pendingOwnerTransferServer = null;
                pendingOwnerTransferAddress = "";
                pendingOwnerTransferDeadlineMillis = 0L;
                pendingOwnerConnectAddress = "";
                pendingOwnerConnectAfterMillis = 0L;
                ownerConnectStarted.set(false);
                quickServerJoinCleanupUntilMillis = 0L;
                ScreenManager.getInstance().execute(() -> new Notification("Quick Server Join Failed", cleanMessage(e), Notification.Type.WARN));
            }
        });
    }

    private static void advanceOwnerTransfer() {
        IntegratedServer server = pendingOwnerTransferServer;
        if (!ownerTransferInProgress.get() || server == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!server.isShutdown()) {
            if (System.currentTimeMillis() > pendingOwnerTransferDeadlineMillis) {
                ownerTransferInProgress.set(false);
                pendingOwnerTransferServer = null;
                pendingOwnerTransferAddress = "";
                pendingOwnerTransferDeadlineMillis = 0L;
                pendingOwnerConnectAddress = "";
                pendingOwnerConnectAfterMillis = 0L;
                ownerConnectStarted.set(false);
                quickServerJoinCleanupUntilMillis = 0L;
                ScreenManager.getInstance().execute(() -> new Notification("Quick Server Join Failed", "World Did Not Close", Notification.Type.WARN));
            }
            return;
        }
        String address = pendingOwnerTransferAddress;
        pendingOwnerTransferServer = null;
        pendingOwnerTransferAddress = "";
        pendingOwnerTransferDeadlineMillis = 0L;
        MinecraftAccessor accessor = (MinecraftAccessor) minecraft;
        accessor.remotely$setSingleplayerServer(null);
        accessor.remotely$setLocalServer(false);
        pendingOwnerConnectAddress = address;
        pendingOwnerConnectAfterMillis = System.currentTimeMillis() + 250L;
        ownerConnectStarted.set(false);
        //#if MC >= 1.21.1
        minecraft.clearClientLevel(new QuickServerJoinScreen());
        //#else
        //$$ minecraft.clearLevel(new QuickServerJoinScreen());
        //#endif
    }

    private static void advanceOwnerConnect() {
        String address = pendingOwnerConnectAddress;
        if (address.isBlank()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null || minecraft.getSingleplayerServer() != null || System.currentTimeMillis() < pendingOwnerConnectAfterMillis) {
            return;
        }
        if (!ownerConnectStarted.compareAndSet(false, true)) {
            return;
        }
        pendingOwnerConnectAddress = "";
        pendingOwnerConnectAfterMillis = 0L;
        connectFromJoinScreen(address);
    }

    private static void connectFromJoinScreen(String address) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ServerAddress serverAddress = ServerAddress.parseString(address);
            //#if MC >= 1.21.1
            ServerData serverData = new ServerData("Quick Server", address, ServerData.Type.OTHER);
            //#else
            //$$ ServerData serverData = new ServerData("Quick Server", address, false);
            //#endif
            quickServerJoinCleanupUntilMillis = System.currentTimeMillis() + 120_000L;
            minecraft.prepareForMultiplayer();
            minecraft.setOverlay(null);
            //#if MC >= 1.21.1
            ConnectScreen.startConnecting(new TitleScreen(), minecraft, serverAddress, serverData, false, null);
            //#else
            //$$ ConnectScreen.startConnecting(new TitleScreen(), minecraft, serverAddress, serverData, false);
            //#endif
        } catch (Exception e) {
            ownerTransferInProgress.set(false);
            pendingOwnerConnectAddress = "";
            pendingOwnerConnectAfterMillis = 0L;
            ownerConnectStarted.set(false);
            quickServerJoinCleanupUntilMillis = 0L;
            ScreenManager.getInstance().execute(() -> new Notification("Quick Server Join Failed", cleanMessage(e), Notification.Type.WARN));
        }
    }

    private static void clearJoinHandoffScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (pendingOwnerTransferServer != null || !pendingOwnerConnectAddress.isBlank()) {
            return;
        }
        if (ownerTransferInProgress.get() && !ownerConnectStarted.get()) {
            return;
        }
        if (!ownerTransferInProgress.get() && System.currentTimeMillis() > quickServerJoinCleanupUntilMillis) {
            return;
        }
        if (System.currentTimeMillis() > quickServerJoinCleanupUntilMillis) {
            ownerTransferInProgress.set(false);
            pendingOwnerConnectAddress = "";
            pendingOwnerConnectAfterMillis = 0L;
            ownerConnectStarted.set(false);
            quickServerJoinCleanupUntilMillis = 0L;
            return;
        }
        if (minecraft.level == null || minecraft.gameMode == null) {
            return;
        }
        minecraft.setOverlay(null);
        if (minecraft.screen != null) {
            minecraft.setScreen(null);
        }
        ownerTransferInProgress.set(false);
        quickServerJoinCleanupUntilMillis = 0L;
    }

    private static String quickServerAddress(Instance instance) {
        String address = ReProxyManager.getForwardedAddress(instance);
        if (address == null || address.isBlank()) {
            return "127.0.0.1:" + instance.getPort();
        }
        return address;
    }

    private static void copyAddress(String address) {
        if (address == null || address.isBlank()) {
            return;
        }
        ScreenManager.getInstance().execute(() -> RemotelyClient.INSTANCE.getHost().setClipboard(address));
    }

    private static void registerTerminalTab(Instance instance) {
        ScreenManager.getInstance().execute(() -> {
            var tabs = RemotelyClient.INSTANCE.getMultiTerminalTabs();
            boolean found = tabs.stream().anyMatch(tab -> tab instanceof Instance existing && existing.equals(instance));
            if (!found) {
                tabs.add(instance);
            }
            for (int i = 0; i < tabs.size(); i++) {
                Object tab = tabs.get(i);
                if (tab instanceof Instance existing && existing.equals(instance)) {
                    RemotelyClient.INSTANCE.setActiveMultiTerminalTabIndex(i);
                    return;
                }
            }
        });
    }

    private static void change(Notification notification, String message, String description, Notification.Type type, Runnable onClick, boolean loading) {
        ScreenManager.getInstance().execute(() -> {
            notification.change(message, description, type, onClick);
            notification.update().loading(loading).autoSlideOut(!loading);
        });
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String cleanMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    public record WorldContext(String worldId, String worldName, Path worldPath) {
    }

    private record ModEntry(long size, long modified) {
        private static ModEntry from(Path file) throws IOException {
            return new ModEntry(Files.size(file), Files.getLastModifiedTime(file).toMillis());
        }
    }
}
