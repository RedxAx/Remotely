package redxax.oxy.remotely.ui.widgets.msmp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.data.managed.*;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.msmp.IMSMPApi;
import restudio.rebase.msmp.dto.Player;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.util.Notification;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PlayerManagerController {

    private final Instance instance;
    private final RebaseAPI api;
    private final Gson gson = new Gson();
    private final Map<UUID, ManagedPlayer> players = new LinkedHashMap<>();
    private final Path remotelyDir;
    private final Path playerLogPath;
    private final Path playerActionsPath;
    private final Path opsPath;
    private final Path bannedPlayersPath;
    private final Path bannedIpsPath;
    private IMSMPApi msmpApi;
    private final Container container;
    private final TerminalWidget terminalWidget;
    private List<PlayerAction> cachedPlayerActions = new ArrayList<>();

    public PlayerManagerController(Instance instance, RebaseAPI api, Container container, TerminalWidget terminalWidget) {
        this.instance = instance;
        this.api = api;
        this.container = container;
        this.terminalWidget = terminalWidget;
        this.msmpApi = instance.getMSMPManager().getApi();
        Path instancePath = Path.of(instance.getPath());
        this.remotelyDir = instancePath.resolve("Remotely");

        this.playerLogPath = remotelyDir.resolve("player-log.json");
        this.playerActionsPath = remotelyDir.resolve("player-actions.json");

        this.opsPath = instancePath.resolve("ops.json");
        this.bannedPlayersPath = instancePath.resolve("banned-players.json");
        this.bannedIpsPath = instancePath.resolve("banned-ips.json");

        ensureRemotelyDirectory();
        loadPlayerActions();
    }

    private void ensureRemotelyDirectory() {
        api.fileExists(remotelyDir).thenAccept(exists -> {
            if (!exists) {
                api.createDirectory(remotelyDir);
            }
        });
    }

    public void setMsmpApi(IMSMPApi msmpApi) {
        this.msmpApi = msmpApi;
    }

    public CompletableFuture<Void> fullRefresh() {
        CompletableFuture<List<PlayerLogEntry>> playerLogFuture = loadJsonFile(playerLogPath, new TypeToken<>() {});
        CompletableFuture<List<OpEntry>> opsFuture = loadJsonFile(opsPath, new TypeToken<>() {});
        CompletableFuture<List<BanEntry>> bannedPlayersFuture = loadJsonFile(bannedPlayersPath, new TypeToken<>() {});
        CompletableFuture<List<IpBanEntry>> bannedIpsFuture = loadJsonFile(bannedIpsPath, new TypeToken<>() {});
        CompletableFuture<List<Player>> onlinePlayersFuture;

        loadPlayerActions();

        if (isMsmpConnected()) {
            onlinePlayersFuture = msmpApi.getPlayers();
        } else {
            onlinePlayersFuture = CompletableFuture.completedFuture(new ArrayList<>());
        }

        return CompletableFuture.allOf(playerLogFuture, opsFuture, bannedPlayersFuture, bannedIpsFuture, onlinePlayersFuture)
                .thenAccept(v -> {
                    List<PlayerLogEntry> playerLog = playerLogFuture.join();
                    Map<UUID, OpEntry> ops = opsFuture.join().stream().collect(Collectors.toMap(op -> UUID.fromString(op.uuid), Function.identity(), (a, b) -> a));
                    Map<UUID, BanEntry> bannedPlayersMap = bannedPlayersFuture.join().stream().collect(Collectors.toMap(ban -> UUID.fromString(ban.uuid), Function.identity(), (a, b) -> a));
                    Map<String, IpBanEntry> bannedIps = bannedIpsFuture.join().stream().collect(Collectors.toMap(ban -> ban.ip, Function.identity(), (a, b) -> a));
                    List<Player> onlinePlayers = onlinePlayersFuture.join();

                    Map<UUID, PlayerLogEntry> allKnownPlayers = new HashMap<>();
                    playerLog.forEach(p -> allKnownPlayers.put(p.uuid, p));
                    ops.values().forEach(p -> allKnownPlayers.computeIfAbsent(UUID.fromString(p.uuid), u -> new PlayerLogEntry(u, p.name)));
                    bannedPlayersMap.values().forEach(p -> allKnownPlayers.computeIfAbsent(UUID.fromString(p.uuid), u -> new PlayerLogEntry(u, p.name)));

                    synchronized (players) {
                        players.clear();
                        allKnownPlayers.values().forEach(p -> {
                            ManagedPlayer mp = new ManagedPlayer(p.uuid, p.name);
                            mp.lastSeen = p.lastSeen;
                            players.put(p.uuid, mp);
                        });

                        updateFromOnlinePlayers(onlinePlayers, true);

                        players.values().forEach(p -> {
                            p.isOp = ops.containsKey(p.uuid);
                            if (p.isOp) p.opLevel = ops.get(p.uuid).level;

                            p.isBanned = bannedPlayersMap.containsKey(p.uuid);
                            if (p.isBanned) p.banInfo = bannedPlayersMap.get(p.uuid);

                            if (p.address != null) {
                                String playerIp = p.address.split(":")[0].replace("/", "");
                                p.isIpBanned = bannedIps.containsKey(playerIp);
                                if (p.isIpBanned) p.ipBanInfo = bannedIps.get(playerIp);
                            }
                        });
                    }
                    ScreenManager.getInstance().execute(this::rebuildPlayerWidgets);
                }).exceptionally(e -> {
                    new Notification("Error", "Failed to refresh player data: " + e.getMessage(), Notification.Type.ERROR);
                    return null;
                });
    }

    public void updateOnlinePlayers(List<Player> onlinePlayers) {
        synchronized (players) {
            boolean logChanged = false;
            Set<UUID> onlineUuids = onlinePlayers.stream().map(p -> p.uuid).collect(Collectors.toSet());

            for (ManagedPlayer p : players.values()) {
                if (p.isOnline && !onlineUuids.contains(p.uuid)) {
                    p.lastSeen = System.currentTimeMillis();
                    logChanged = true;
                }
            }
            updateFromOnlinePlayers(onlinePlayers, true);

            if (logChanged) {
                savePlayerLog();
            }
        }
        fullRefresh();
    }

    private void updateFromOnlinePlayers(List<Player> onlinePlayers, boolean clearOldOnlineStatus) {
        if (clearOldOnlineStatus) {
            players.values().forEach(p -> {
                p.isOnline = false;
                p.ping = -1;
                p.address = null;
            });
        }

        boolean logChanged = false;

        for (Player onlinePlayer : onlinePlayers) {
            ManagedPlayer p = players.computeIfAbsent(onlinePlayer.uuid, u -> {
                ManagedPlayer newPlayer = new ManagedPlayer(u, onlinePlayer.name);
                newPlayer.lastSeen = System.currentTimeMillis();
                return newPlayer;
            });

            if (!players.containsKey(p.uuid)) {
                logChanged = true;
            }

            p.isOnline = true;
            p.name = onlinePlayer.name;
            p.ping = onlinePlayer.ping;
            p.address = onlinePlayer.address;
        }

        if (logChanged) {
            savePlayerLog();
        }
    }

    private void savePlayerLog() {
        List<PlayerLogEntry> playerLog = new ArrayList<>();
        for (ManagedPlayer p : players.values()) {
            PlayerLogEntry ple = new PlayerLogEntry(p.uuid, p.name);
            ple.lastSeen = p.lastSeen;
            playerLog.add(ple);
        }
        saveJsonFile(playerLogPath, playerLog);
    }

    private <T> CompletableFuture<List<T>> loadJsonFile(Path path, TypeToken<List<T>> typeToken) {
        return api.fileExists(path).thenCompose(exists -> {
            if (!exists) {
                return CompletableFuture.completedFuture(new ArrayList<>());
            }
            return api.readFile(path).thenApply(content -> {
                if (content == null || content.isEmpty()) {
                    return new ArrayList<>();
                }
                Type type = typeToken.getType();
                return gson.fromJson(content, type);
            });
        });
    }

    private CompletableFuture<Void> saveJsonFile(Path path, Object data) {
        try {
            String json = gson.toJson(data);
            return api.writeFile(path, json);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void loadPlayerActions() {
        loadJsonFile(playerActionsPath, new TypeToken<List<PlayerAction>>() {})
            .thenAccept(actions -> {
                if (actions != null) {
                    this.cachedPlayerActions = actions;
                } else {
                    this.cachedPlayerActions = new ArrayList<>();
                }
            });
    }

    public List<PlayerAction> getPlayerActions() {
        return cachedPlayerActions;
    }

    public void rebuildPlayerWidgets() {
        container.clearWidgets();
        if (players.isEmpty()) {
            container.addWidget(new AnimatedButton.Builder().label("No players found.").active(false).build());
        } else {
            List<ManagedPlayer> sortedPlayers = players.values().stream()
                    .sorted(Comparator.comparing((ManagedPlayer p) -> !p.isOnline)
                            .thenComparing(p -> p.name.toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());

            for (ManagedPlayer p : sortedPlayers) {
                container.addWidget(new PlayerEntryWidget(p, this));
            }
        }
        container.updateWidgetPositions();
    }

    public CompletableFuture<Object> kickPlayer(ManagedPlayer player, String reason) {
        return msmpApi.kickPlayer(player.uuid.toString(), reason);
    }

    public CompletableFuture<Void> banPlayer(ManagedPlayer player, String reason, String expires, boolean ipBan) {
        CompletableFuture<Void> playerBanFuture = msmpApi.banPlayer(player.uuid.toString(), player.name, reason, expires);
        if (ipBan && player.address != null) {
            String ip = player.address.split(":")[0].replace("/", "");
            return playerBanFuture.thenCompose(v -> msmpApi.banIp(ip, player.uuid.toString(), reason, expires));
        }
        return playerBanFuture;
    }

    public CompletableFuture<Void> unbanPlayer(ManagedPlayer player) {
        CompletableFuture<Void> future = msmpApi.unbanPlayer(player.uuid.toString());
        if (player.isIpBanned && player.ipBanInfo != null) {
            return future.thenCompose(v -> msmpApi.unbanIp(player.ipBanInfo.ip));
        }
        return future;
    }

    public CompletableFuture<Void> toggleOp(ManagedPlayer player) {
        CompletableFuture<Void> future = player.isOp ?
                msmpApi.deopPlayer(player.uuid.toString()) :
                msmpApi.opPlayer(player.uuid.toString(), 4);
        return future.thenCompose(v -> fullRefresh());
    }

    public boolean isMsmpConnected() {
        return this.msmpApi != null && this.msmpApi.isConnected();
    }

    public boolean isServerRunning() {
        return instance.getState() == InstanceState.RUNNING;
    }

    public CompletableFuture<Void> runCustomCommand(ManagedPlayer player, String commandTemplate) {
        if (terminalWidget == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Terminal not available."));
        }
        String command = commandTemplate
                .replace("$name", player.name)
                .replace("$uuid", player.uuid.toString());

        terminalWidget.executeCommand(command);
        return CompletableFuture.completedFuture(null);
    }
}