package redxax.oxy.remotely.ui.widgets.msmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.data.managed.*;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rebase.util.RebaseLogger;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.util.Notification;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PlayerManagerController {

    private static final Map<String, PlayerManagerController> registry = new HashMap<>();

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
    private Container container;
    private TerminalWidget terminalWidget;
    private List<PlayerAction> cachedPlayerActions = new ArrayList<>();
    private final PlayerHistoryService historyService;

    private static final Pattern PLAYER_JOIN_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*?(\\w+)\\[/([0-9.:]+)] logged in with entity id \\d+ at .*");
    private static final Pattern PLAYER_LEAVE_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*?(\\w+) left the game");
    private static final Pattern PLAYER_UUID_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*?UUID of player (\\w+) is ([0-9a-f\\-]+)");
    private static final Pattern SERVER_DONE_PATTERN = Pattern.compile(".*Done \\(.*\\)! For help, type \"help\".*");
    private static final Pattern SERVER_STOP_PATTERN = Pattern.compile(".*Stopping server.*");
    private static final Pattern PLAYER_OP_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*Made (\\w+) a server operator.*");
    private static final Pattern PLAYER_DEOP_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*Made (\\w+) no longer a server operator.*");
    private static final Pattern PLAYER_BAN_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*?Banned (\\w+): (.*)");
    private static final Pattern PLAYER_UNBAN_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*?Unbanned (\\w+)");
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

    public PlayerManagerController(Instance instance, RebaseAPI api, Container container, TerminalWidget terminalWidget) {
        this.instance = instance;
        this.api = api;
        this.container = container;
        this.terminalWidget = terminalWidget;
        Path instancePath = Path.of(instance.getPath());
        this.remotelyDir = instancePath.resolve("Remotely");

        this.playerLogPath = remotelyDir.resolve("player-log.json");
        this.playerActionsPath = remotelyDir.resolve("player-actions.json");

        this.opsPath = instancePath.resolve("ops.json");
        this.bannedPlayersPath = instancePath.resolve("banned-players.json");
        this.bannedIpsPath = instancePath.resolve("banned-ips.json");

        ensureRemotelyDirectory();
        loadPlayerActions();
        this.historyService = new PlayerHistoryService(api, remotelyDir.resolve("player-history"), name -> {
            synchronized (players) {
                return players.values().stream().filter(p -> p.name.equalsIgnoreCase(name)).map(p -> p.uuid).findFirst().orElse(null);
            }
        });
        fullRefresh();
    }

    public static PlayerManagerController getOrCreate(Instance instance, RebaseAPI api) {
        PlayerManagerController c = registry.get(instance.getInstanceId());
        if (c == null) {
            c = new PlayerManagerController(instance, api, null, null);
            registry.put(instance.getInstanceId(), c);
            c.attachGlobalOutputListener();
        }
        return c;
    }

    public void setUiBindings(Container container, TerminalWidget terminalWidget) {
        this.container = container;
        this.terminalWidget = terminalWidget;
        ScreenManager.getInstance().execute(this::rebuildPlayerWidgets);
    }

    private void attachGlobalOutputListener() {
        TerminalWidget tw = TerminalWidget.getOrCreate(instance, null, 0, 0, 0, 0);
        tw.addOutputListener(this::processConsoleLine);
    }

    private void ensureRemotelyDirectory() {
        api.fileExists(remotelyDir).thenAccept(exists -> {
            if (!exists) {
                api.createDirectory(remotelyDir);
            }
        });
    }

    public void processConsoleLine(String line) {
        if (line == null) return;
        line = ANSI_PATTERN.matcher(line).replaceAll("").trim();
        if (line.isEmpty()) return;

        historyService.onConsoleLine(line);

        if (SERVER_DONE_PATTERN.matcher(line).matches()) {
            instance.setState(InstanceState.RUNNING);
            fullRefresh();
            return;
        }
        if (SERVER_STOP_PATTERN.matcher(line).matches()) {
            instance.setState(InstanceState.STOPPED);
            return;
        }

        Matcher uuidMatcher = PLAYER_UUID_PATTERN.matcher(line);
        if (uuidMatcher.matches()) {
            handlePlayerLogon(UUID.fromString(uuidMatcher.group(2)), uuidMatcher.group(1));
            return;
        }

        Matcher joinMatcher = PLAYER_JOIN_PATTERN.matcher(line);
        if (joinMatcher.matches()) {
            handlePlayerJoin(joinMatcher.group(1), joinMatcher.group(2));
            return;
        }

        Matcher leaveMatcher = PLAYER_LEAVE_PATTERN.matcher(line);
        if (leaveMatcher.matches()) {
            handlePlayerLeave(leaveMatcher.group(1));
            return;
        }

        Matcher opMatcher = PLAYER_OP_PATTERN.matcher(line);
        if (opMatcher.matches()) {
            updatePlayerStatus(opMatcher.group(1), p -> p.isOp = true);
            historyService.recordAccessChangeByName(opMatcher.group(1), SessionEventType.OP_CHANGE, "op=true", System.currentTimeMillis());
            return;
        }

        Matcher deopMatcher = PLAYER_DEOP_PATTERN.matcher(line);
        if (deopMatcher.matches()) {
            updatePlayerStatus(deopMatcher.group(1), p -> p.isOp = false);
            historyService.recordAccessChangeByName(deopMatcher.group(1), SessionEventType.OP_CHANGE, "op=false", System.currentTimeMillis());
            return;
        }

        Matcher banMatcher = PLAYER_BAN_PATTERN.matcher(line);
        if (banMatcher.matches()) {
            updatePlayerStatus(banMatcher.group(1), p -> p.isBanned = true);
            historyService.recordAccessChangeByName(banMatcher.group(1), SessionEventType.BAN, "reason=" + banMatcher.group(2), System.currentTimeMillis());
            return;
        }

        Matcher unbanMatcher = PLAYER_UNBAN_PATTERN.matcher(line);
        if (unbanMatcher.matches()) {
            updatePlayerStatus(unbanMatcher.group(1), p -> {
                p.isBanned = false;
                p.isIpBanned = false;
            });
            historyService.recordAccessChangeByName(unbanMatcher.group(1), SessionEventType.UNBAN, "", System.currentTimeMillis());
        }
    }

    private void updatePlayerStatus(String name, Consumer<ManagedPlayer> updater) {
        synchronized (players) {
            players.values().stream().filter(p -> p.name.equalsIgnoreCase(name)).findFirst().ifPresent(player -> {
                updater.accept(player);
                ScreenManager.getInstance().execute(this::rebuildPlayerWidgets);
            });
        }
    }

    private void handlePlayerLogon(UUID uuid, String name) {
        synchronized (players) {
            ManagedPlayer player = players.computeIfAbsent(uuid, u -> {
                ManagedPlayer newPlayer = new ManagedPlayer(u, name);
                savePlayerLog();
                return newPlayer;
            });

            if (!player.name.equals(name)) {
                player.name = name;
                savePlayerLog();
            }
        }
    }

    private void handlePlayerJoin(String name, String address) {
        synchronized (players) {
            Optional<ManagedPlayer> playerOpt = players.values().stream().filter(p -> p.name.equalsIgnoreCase(name)).findFirst();
            if (playerOpt.isPresent()) {
                ManagedPlayer player = playerOpt.get();
                player.isOnline = true;
                player.address = address;
                historyService.startSession(player.uuid, player.name, address, System.currentTimeMillis());
                ScreenManager.getInstance().execute(this::rebuildPlayerWidgets);
            } else {
                terminalWidget.executeCommand("uuid " + name);
            }
        }
    }

    private void handlePlayerLeave(String name) {
        synchronized (players) {
            players.values().stream().filter(p -> p.name.equalsIgnoreCase(name)).findFirst().ifPresent(player -> {
                player.isOnline = false;
                player.address = null;
                player.lastSeen = System.currentTimeMillis();
                savePlayerLog();
                historyService.endSession(player.uuid, player.lastSeen);
                ScreenManager.getInstance().execute(this::rebuildPlayerWidgets);
            });
        }
    }

    public CompletableFuture<Void> fullRefresh() {
        CompletableFuture<List<PlayerLogEntry>> playerLogFuture = loadJsonFile(playerLogPath, new TypeToken<>() {});
        CompletableFuture<List<OpEntry>> opsFuture = loadJsonFile(opsPath, new TypeToken<>() {});
        CompletableFuture<List<BanEntry>> bannedPlayersFuture = loadJsonFile(bannedPlayersPath, new TypeToken<>() {});
        CompletableFuture<List<IpBanEntry>> bannedIpsFuture = loadJsonFile(bannedIpsPath, new TypeToken<>() {});

        loadPlayerActions();

        return CompletableFuture.allOf(playerLogFuture, opsFuture, bannedPlayersFuture, bannedIpsFuture)
                .thenAccept(v -> {
                    List<PlayerLogEntry> playerLog = playerLogFuture.join();
                    Map<UUID, OpEntry> ops = opsFuture.join().stream().collect(Collectors.toMap(op -> UUID.fromString(op.uuid), Function.identity(), (a, b) -> a));
                    Map<UUID, BanEntry> bannedPlayersMap = bannedPlayersFuture.join().stream().collect(Collectors.toMap(ban -> UUID.fromString(ban.uuid), Function.identity(), (a, b) -> a));
                    Map<String, IpBanEntry> bannedIps = bannedIpsFuture.join().stream().collect(Collectors.toMap(ban -> ban.ip, Function.identity(), (a, b) -> a));

                    synchronized (players) {
                        Map<UUID, PlayerLogEntry> allKnownPlayers = new HashMap<>();
                        playerLog.forEach(p -> allKnownPlayers.put(p.uuid, p));
                        ops.values().forEach(p -> allKnownPlayers.computeIfAbsent(UUID.fromString(p.uuid), u -> new PlayerLogEntry(u, p.name)));
                        bannedPlayersMap.values().forEach(p -> allKnownPlayers.computeIfAbsent(UUID.fromString(p.uuid), u -> new PlayerLogEntry(u, p.name)));

                        allKnownPlayers.forEach((uuid, entry) -> {
                            ManagedPlayer p = players.computeIfAbsent(uuid, u -> new ManagedPlayer(u, entry.name));
                            p.lastSeen = entry.lastSeen;
                        });

                        players.values().forEach(p -> {
                            p.isOp = ops.containsKey(p.uuid);
                            if (p.isOp) p.opLevel = ops.get(p.uuid).level;

                            p.isBanned = bannedPlayersMap.containsKey(p.uuid);
                            if (p.isBanned) p.banInfo = bannedPlayersMap.get(p.uuid);

                            if (p.isOnline && p.address != null) {
                                String playerIp = p.address.split(":")[0].replace("/", "");
                                p.isIpBanned = bannedIps.containsKey(playerIp);
                                if (p.isIpBanned) p.ipBanInfo = bannedIps.get(playerIp);
                            } else {
                                p.isIpBanned = false;
                            }
                        });
                    }
                    ScreenManager.getInstance().execute(this::rebuildPlayerWidgets);
                }).exceptionally(e -> {
                    new Notification("Error", "Failed to refresh player data: " + e.getMessage(), Notification.Type.ERROR);
                    return null;
                });
    }

    private void savePlayerLog() {
        List<PlayerLogEntry> playerLog = new ArrayList<>();
        synchronized(players) {
            for (ManagedPlayer p : players.values()) {
                PlayerLogEntry ple = new PlayerLogEntry(p.uuid, p.name);
                ple.lastSeen = p.lastSeen;
                playerLog.add(ple);
            }
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
                List<T> result = gson.fromJson(content, type);
                return result != null ? result : new ArrayList<>();
            });
        });
    }

    private CompletableFuture<Void> saveJsonFile(Path path, Object data) {
        try {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(data);
            return api.writeFile(path, json);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void loadPlayerActions() {
        loadJsonFile(playerActionsPath, new TypeToken<List<PlayerAction>>() {}).thenAccept(actions -> this.cachedPlayerActions = Objects.requireNonNullElseGet(actions, ArrayList::new));
    }

    public List<PlayerAction> getPlayerActions() {
        return cachedPlayerActions;
    }

    public PlayerHistoryService getHistoryService() {
        return historyService;
    }

    public void rebuildPlayerWidgets() {
        if (container == null) return;
        container.clearWidgets();
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
        runCustomCommand(player, "kick " + player.name + " " + reason);
        historyService.recordAccessChange(player.uuid, player.name, SessionEventType.KICK, reason, System.currentTimeMillis());
    }

    public void banPlayer(ManagedPlayer player, String reason, boolean ipBan) {
        String command = ipBan ? "ban-ip " : "ban ";
        command += player.name + " " + reason;
        runCustomCommand(player, command);
        historyService.recordAccessChange(player.uuid, player.name, SessionEventType.BAN, (ipBan ? "ip=true;" : "ip=false;") + reason, System.currentTimeMillis());
    }

    public void unbanPlayer(ManagedPlayer player) {
        String command = player.isIpBanned && player.ipBanInfo != null ? "pardon-ip " + player.ipBanInfo.ip : "pardon " + player.name;
        runCustomCommand(player, command);
        historyService.recordAccessChange(player.uuid, player.name, SessionEventType.UNBAN, "", System.currentTimeMillis());
    }

    public void toggleOp(ManagedPlayer player) {
        String command = player.isOp ? "deop " : "op ";
        runCustomCommand(player, command + player.name);
        historyService.recordAccessChange(player.uuid, player.name, SessionEventType.OP_CHANGE, player.isOp ? "op=false" : "op=true", System.currentTimeMillis());
    }

    public boolean isServerRunning() {
        return instance.getState() == InstanceState.RUNNING;
    }

    public void runCustomCommand(ManagedPlayer player, String commandTemplate) {
        if (terminalWidget == null) {
            new Notification("Error", "Terminal not available.", Notification.Type.ERROR);
            return;
        }
        String command = commandTemplate.replace("$name", player.name).replace("$uuid", player.uuid.toString());

        terminalWidget.executeCommand(command);
    }
}