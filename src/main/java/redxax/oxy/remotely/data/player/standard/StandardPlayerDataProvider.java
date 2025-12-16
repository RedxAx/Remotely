package redxax.oxy.remotely.data.player.standard;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.data.managed.*;
import redxax.oxy.remotely.data.player.IPlayerDataProvider;
import redxax.oxy.remotely.data.player.IPlayerHistoryCollector;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.debug.DebugManager;
import restudio.rescreen.ui.core.ScreenManager;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StandardPlayerDataProvider implements IPlayerDataProvider {

    private final RebaseAPI api;
    private final Instance instance;
    private final TerminalWidget terminalWidget;
    private final IPlayerHistoryCollector historyCollector;
    private final Gson gson = new Gson();
    private final Map<UUID, ManagedPlayer> players = new LinkedHashMap<>();
    private final List<Consumer<List<ManagedPlayer>>> updateListeners = new CopyOnWriteArrayList<>();
    private final String instanceId;

    private final Path remotelyDir;
    private final Path playerLogPath;
    private final Path opsPath;
    private final Path bannedPlayersPath;
    private final Path bannedIpsPath;

    private static final Pattern PLAYER_JOIN_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*?(\\w+)\\[/([0-9.:]+)] logged in with entity id \\d+ at .*");
    private static final Pattern PLAYER_LEAVE_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*?(\\w+) left the game");
    private static final Pattern PLAYER_UUID_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*?UUID of player (\\w+) is ([0-9a-f\\-]+)");
    private static final Pattern PLAYER_OP_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*Made (\\w+) a server operator.*");
    private static final Pattern PLAYER_DEOP_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*Made (\\w+) no longer a server operator.*");
    private static final Pattern PLAYER_BAN_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*?Banned (\\w+): (.*)");
    private static final Pattern PLAYER_UNBAN_PATTERN = Pattern.compile("(?:.*\\[INFO]: )?.*?Unbanned (\\w+)");
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

    private List<PlayerLogEntry> cachedPlayerLog = new ArrayList<>();
    private List<OpEntry> cachedOps = new ArrayList<>();
    private List<BanEntry> cachedBans = new ArrayList<>();
    private List<IpBanEntry> cachedIpBans = new ArrayList<>();

    public StandardPlayerDataProvider(Instance instance, RebaseAPI api, TerminalWidget terminalWidget, IPlayerHistoryCollector historyCollector) {
        this.instance = instance;
        this.api = api;
        this.terminalWidget = terminalWidget;
        this.historyCollector = historyCollector;
        this.instanceId = instance.getInstanceId();

        Path instancePath = Path.of(instance.getPath());
        this.remotelyDir = instancePath.resolve("Remotely");
        this.playerLogPath = remotelyDir.resolve("player-log.json");
        this.opsPath = instancePath.resolve("ops.json");
        this.bannedPlayersPath = instancePath.resolve("banned-players.json");
        this.bannedIpsPath = instancePath.resolve("banned-ips.json");
    }

    @Override
    public void initialize() {
        ensureRemotelyDirectory();
        instance.addLogListener(this::processLogLine);
        fullRefresh();
    }

    @Override
    public void shutdown() {
        instance.removeLogListener(this::processLogLine);
        players.clear();
        updateListeners.clear();
    }

    @Override
    public CompletableFuture<Void> fullRefresh() {
        CompletableFuture<List<PlayerLogEntry>> playerLogFuture = loadJsonFile(playerLogPath, new TypeToken<>() {});
        CompletableFuture<List<OpEntry>> opsFuture = loadJsonFile(opsPath, new TypeToken<>() {});
        CompletableFuture<List<BanEntry>> bannedPlayersFuture = loadJsonFile(bannedPlayersPath, new TypeToken<>() {});
        CompletableFuture<List<IpBanEntry>> bannedIpsFuture = loadJsonFile(bannedIpsPath, new TypeToken<>() {});

        return CompletableFuture.allOf(playerLogFuture, opsFuture, bannedPlayersFuture, bannedIpsFuture).thenAccept(v -> {
            this.cachedPlayerLog = playerLogFuture.join();
            this.cachedOps = opsFuture.join();
            this.cachedBans = bannedPlayersFuture.join();
            this.cachedIpBans = bannedIpsFuture.join();
            rebuildPlayerCache();
        }).thenRun(this::notifyListeners);
    }

    public void updateFromContent(String fileName, String content) {
        if (content == null || content.isEmpty()) return;
        try {
            if (fileName.endsWith("ops.json")) {
                this.cachedOps = gson.fromJson(content, new TypeToken<List<OpEntry>>() {}.getType());
            } else if (fileName.endsWith("banned-players.json")) {
                this.cachedBans = gson.fromJson(content, new TypeToken<List<BanEntry>>() {}.getType());
            } else if (fileName.endsWith("banned-ips.json")) {
                this.cachedIpBans = gson.fromJson(content, new TypeToken<List<IpBanEntry>>() {}.getType());
            }
            if (this.cachedOps == null) this.cachedOps = new ArrayList<>();
            if (this.cachedBans == null) this.cachedBans = new ArrayList<>();
            if (this.cachedIpBans == null) this.cachedIpBans = new ArrayList<>();

            rebuildPlayerCache();
            notifyListeners();
        } catch (Exception e) {
            DebugManager.getInstance().log("StandardPlayerDataProvider", "Failed to parse streamed content for " + fileName + ": " + e.getMessage());
        }
    }

    private void rebuildPlayerCache() {
        Map<UUID, OpEntry> ops = cachedOps.stream().collect(Collectors.toMap(op -> UUID.fromString(op.uuid), Function.identity(), (a, b) -> a));
        Map<UUID, BanEntry> bannedPlayersMap = cachedBans.stream().collect(Collectors.toMap(ban -> UUID.fromString(ban.uuid), Function.identity(), (a, b) -> a));
        Map<String, IpBanEntry> bannedIps = cachedIpBans.stream().collect(Collectors.toMap(ban -> ban.ip, Function.identity(), (a, b) -> a));

        synchronized (players) {
            Map<UUID, PlayerLogEntry> allKnownPlayers = new HashMap<>();
            cachedPlayerLog.forEach(p -> allKnownPlayers.put(p.uuid, p));
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
    }

    @Override
    public Map<UUID, ManagedPlayer> getCachedPlayers() {
        return players;
    }

    @Override
    public void addUpdateListener(Consumer<List<ManagedPlayer>> listener) {
        updateListeners.add(listener);
    }

    @Override
    public void removeUpdateListener(Consumer<List<ManagedPlayer>> listener) {
        updateListeners.remove(listener);
    }

    private void notifyListeners() {
        List<ManagedPlayer> playerList;
        synchronized (players) {
            playerList = new ArrayList<>(players.values());
        }
        ScreenManager.getInstance().execute(() -> {
            for (Consumer<List<ManagedPlayer>> listener : updateListeners) {
                listener.accept(playerList);
            }
        });
    }

    private void logAction(String action, String target) {
        DebugManager.getInstance().recordEvent(instanceId, "Player Action", "Standard", String.format("%s %s", action, target));
    }

    private void processLogLine(int lineNum, String line) {
        if (line == null) return;
        line = ANSI_PATTERN.matcher(line).replaceAll("").trim();
        if (line.isEmpty()) return;

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
            if (historyCollector instanceof StandardPlayerHistoryProvider history) {
                history.recordAccessChange(getPlayerUUID(opMatcher.group(1)), opMatcher.group(1), SessionEventType.OP_CHANGE, "op=true", System.currentTimeMillis(), lineNum);
            } else {
                historyCollector.recordAccessChange(getPlayerUUID(opMatcher.group(1)), opMatcher.group(1), SessionEventType.OP_CHANGE, "op=true", System.currentTimeMillis());
            }
            logAction("Opped", opMatcher.group(1));
            return;
        }

        Matcher deopMatcher = PLAYER_DEOP_PATTERN.matcher(line);
        if (deopMatcher.matches()) {
            updatePlayerStatus(deopMatcher.group(1), p -> p.isOp = false);
            if (historyCollector instanceof StandardPlayerHistoryProvider history) {
                history.recordAccessChange(getPlayerUUID(deopMatcher.group(1)), deopMatcher.group(1), SessionEventType.OP_CHANGE, "op=false", System.currentTimeMillis(), lineNum);
            } else {
                historyCollector.recordAccessChange(getPlayerUUID(deopMatcher.group(1)), deopMatcher.group(1), SessionEventType.OP_CHANGE, "op=false", System.currentTimeMillis());
            }
            logAction("De-opped", deopMatcher.group(1));
            return;
        }

        Matcher banMatcher = PLAYER_BAN_PATTERN.matcher(line);
        if (banMatcher.matches()) {
            updatePlayerStatus(banMatcher.group(1), p -> p.isBanned = true);
            if (historyCollector instanceof StandardPlayerHistoryProvider history) {
                history.recordAccessChange(getPlayerUUID(banMatcher.group(1)), banMatcher.group(1), SessionEventType.BAN, "reason=" + banMatcher.group(2), System.currentTimeMillis(), lineNum);
            } else {
                historyCollector.recordAccessChange(getPlayerUUID(banMatcher.group(1)), banMatcher.group(1), SessionEventType.BAN, "reason=" + banMatcher.group(2), System.currentTimeMillis());
            }
            logAction("Banned", banMatcher.group(1));
            return;
        }

        Matcher unbanMatcher = PLAYER_UNBAN_PATTERN.matcher(line);
        if (unbanMatcher.matches()) {
            updatePlayerStatus(unbanMatcher.group(1), p -> {
                p.isBanned = false;
                p.isIpBanned = false;
            });
            if (historyCollector instanceof StandardPlayerHistoryProvider history) {
                history.recordAccessChange(getPlayerUUID(unbanMatcher.group(1)), unbanMatcher.group(1), SessionEventType.UNBAN, "", System.currentTimeMillis(), lineNum);
            } else {
                historyCollector.recordAccessChange(getPlayerUUID(unbanMatcher.group(1)), unbanMatcher.group(1), SessionEventType.UNBAN, "", System.currentTimeMillis());
            }
            logAction("Unbanned", unbanMatcher.group(1));
        }
    }

    private UUID getPlayerUUID(String name) {
        synchronized (players) {
            return players.values().stream().filter(p -> p.name.equalsIgnoreCase(name)).map(p -> p.uuid).findFirst().orElse(null);
        }
    }

    private void updatePlayerStatus(String name, Consumer<ManagedPlayer> updater) {
        synchronized (players) {
            players.values().stream().filter(p -> p.name.equalsIgnoreCase(name)).findFirst().ifPresent(updater);
        }
        notifyListeners();
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
                historyCollector.startSession(player.uuid, player.name, address, System.currentTimeMillis());
                notifyListeners();
                DebugManager.getInstance().recordEvent(instanceId, "Player", "Standard", "Join: " + name);
            } else {
                if (terminalWidget != null) terminalWidget.executeCommand("uuid " + name);
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
                historyCollector.endSession(player.uuid, player.lastSeen);
                notifyListeners();
                DebugManager.getInstance().recordEvent(instanceId, "Player", "Standard", "Leave: " + name);
            });
        }
    }

    private void ensureRemotelyDirectory() {
        api.fileExists(remotelyDir).thenAccept(exists -> {
            if (!exists) {
                api.createDirectory(remotelyDir);
            }
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
        String json = new Gson().newBuilder().setPrettyPrinting().create().toJson(data);
        return api.writeFile(path, json);
    }
}
