package redxax.oxy.remotely.data.playerdata.sources;

import redxax.oxy.remotely.util.TaskSchedulers;

import redxax.oxy.remotely.util.AsyncTools;

import redxax.oxy.remotely.DesktopRemotelyPaths;
import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.data.playerdata.PlayerDataSnapshot;
import redxax.oxy.remotely.data.playerdata.PlayerDataSource;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.instance.Instance;
import restudio.rebase.minecraft.MinecraftWorldPaths;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import restudio.rebase.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;
import java.util.stream.Stream;

public class WorldPlayerDataSource implements PlayerDataSource {
    private static final String ID = "world";

    private final Instance instance;
    private final RebaseAPI api;

    public WorldPlayerDataSource(Instance instance, RebaseAPI api) {
        this.instance = instance;
        this.api = api;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public boolean isOnlineOnly() {
        return false;
    }

    @Override
    public Async<PlayerDataSnapshot> fetch(UUID uuid, String name) {
        if (uuid == null || instance == null) {
            return Async.completed(null);
        }
        String id = uuid.toString();
        return loadOfflinePlayer(id).thenApply(data -> data == null ? null : new PlayerDataSnapshot(uuid, data, ID, getPriority()));
    }

    private Async<PlayerData> loadOfflinePlayer(String uuid) {
        Path worldRoot = MinecraftWorldPaths.worldRoot(Path.of(instance.getPath()), instance.getServerProperties());
        List<Path> dataDirectories = MinecraftWorldPaths.playerDataDirs(worldRoot);
        List<Path> playerDataPaths = Stream.concat(dataDirectories.stream().map(dir -> dir.resolve(uuid + ".dat")), dataDirectories.stream().map(dir -> dir.resolve(uuid + ".dat_old"))).toList();
        List<Path> statsPaths = MinecraftWorldPaths.statsDirs(worldRoot).stream().map(dir -> dir.resolve(uuid + ".json")).toList();
        return loadFirstPlayerData(playerDataPaths, 0).thenCompose(data -> {
            if (data == null) return Async.completed(null);
            return readFirstText(statsPaths).thenApply(statsJson -> {
                if (data == null) return null;
                if (statsJson != null && !statsJson.isBlank()) {
                    Map<String, Object> stats = PlayerDataParser.parseStats(statsJson);
                    return new PlayerData(data.health(), data.food(), data.saturation(), data.experienceLevel(),
                            data.experienceProgress(), data.totalExperience(), data.location(), data.gameMode(),
                            data.flying(), data.fallFlying(), data.inventory(), data.armor(), data.offhand(),
                            data.enderChest(), data.effects(), data.attributes(), stats,
                            PlayerDataParser.flattenStats(stats), data.lastModified(), data.onlineOnly());
                }
                return data;
            });
        });
    }

    private Async<PlayerData> loadFirstPlayerData(List<Path> paths, int index) {
        if (index >= paths.size()) return Async.completed(null);
        return readBytes(paths.get(index)).thenCompose(raw -> {
            if (raw == null || raw.length == 0) return loadFirstPlayerData(paths, index + 1);
            return PlayerDataParser.parsePlayerData(raw).thenCompose(data -> data != null ? Async.completed(data) : loadFirstPlayerData(paths, index + 1));
        });
    }

    private Async<byte[]> readFirstBytes(List<Path> paths) {
        Async<byte[]> result = Async.completed(null);
        for (Path path : paths) {
            result = result.thenCompose(raw -> raw != null && raw.length > 0 ? Async.completed(raw) : readBytes(path));
        }
        return result;
    }

    private Async<String> readFirstText(List<Path> paths) {
        Async<String> result = Async.completed(null);
        for (Path path : paths) {
            result = result.thenCompose(text -> text != null && !text.isBlank() ? Async.completed(text) : JvmAsyncBridge.fromFuture(api.readFile(path)).exceptionally(e -> null));
        }
        return result;
    }

    private Async<byte[]> readBytes(Path path) {
        ServerBackend backend = instance != null ? instance.getBackend() : null;
        String type = backend != null ? backend.getFileSystem().getMetadata("type") : null;
        if (type == null || "LOCAL".equalsIgnoreCase(type)) {
            return readLocalBytes(path);
        }
        Path cacheDir = DesktopRemotelyPaths.appDir().resolve("cache").resolve("playerdata");
        if (instance.getInstanceId() != null) {
            cacheDir = cacheDir.resolve(instance.getInstanceId());
        }
        Path finalDir = cacheDir;
        return AsyncTools.supply(TaskSchedulers.current(), () -> {
            try {
                Files.createDirectories(finalDir);
                return finalDir;
            } catch (IOException e) {
                return null;
            }
        }).thenCompose(dir -> {
            if (dir == null) return Async.completed(null);
            return JvmAsyncBridge.fromFuture(api.download(List.of(path), dir)).thenApply(v -> dir);
        }).thenCompose(dir -> {
            if (dir == null || path == null || path.getFileName() == null) return Async.completed(null);
            Path fileName = path.getFileName();
            Path localFile = dir.resolve(fileName);
            return readLocalBytes(localFile);
        }).exceptionallyCompose(ex -> JvmAsyncBridge.fromFuture(api.readFile(path)).thenApply(PlayerDataParser::decodeBinary));
    }

    private Async<byte[]> readLocalBytes(Path path) {
        return AsyncTools.supply(TaskSchedulers.current(), () -> {
            try {
                if (path == null || !Files.exists(path)) return null;
                for (int attempt = 0; attempt < 3; attempt++) {
                    long modified = Files.getLastModifiedTime(path).toMillis();
                    long size = Files.size(path);
                    byte[] data = Files.readAllBytes(path);
                    if (modified == Files.getLastModifiedTime(path).toMillis() && size == Files.size(path)) return data;
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        });
    }
}
