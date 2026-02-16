package redxax.oxy.remotely.data.playerdata.sources;

import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.data.playerdata.PlayerDataSnapshot;
import redxax.oxy.remotely.data.playerdata.PlayerDataSource;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.instance.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import restudio.rebase.util.Executors;

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
    public CompletableFuture<PlayerDataSnapshot> fetch(UUID uuid, String name) {
        if (uuid == null || instance == null) {
            return CompletableFuture.completedFuture(null);
        }
        String id = uuid.toString();
        return loadOfflinePlayer(id).thenApply(data -> data == null ? null : new PlayerDataSnapshot(uuid, data, ID, getPriority()));
    }

    private CompletableFuture<PlayerData> loadOfflinePlayer(String uuid) {
        Path base = Path.of(instance.getPath());
        Path playerDataPath = base.resolve("world").resolve("playerdata").resolve(uuid + ".dat");
        Path statsPath = base.resolve("world").resolve("stats").resolve(uuid + ".json");
        return readBytes(playerDataPath).thenCompose(raw -> {
            if (raw == null || raw.length == 0) return CompletableFuture.completedFuture(null);
            return PlayerDataParser.parsePlayerData(raw).thenCompose(data -> api.readFile(statsPath).thenApply(statsJson -> {
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
            }));
        });
    }

    private CompletableFuture<byte[]> readBytes(Path path) {
        ServerBackend backend = instance != null ? instance.getBackend() : null;
        String type = backend != null ? backend.getFileSystem().getMetadata("type") : null;
        if (type == null || "LOCAL".equalsIgnoreCase(type)) {
            return readLocalBytes(path);
        }
        Path cacheDir = Config.remotelyDir.resolve("cache").resolve("playerdata");
        if (instance.getInstanceId() != null) {
            cacheDir = cacheDir.resolve(instance.getInstanceId());
        }
        Path finalDir = cacheDir;
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(finalDir);
                return finalDir;
            } catch (IOException e) {
                return null;
            }
        }, Executors.IO).thenCompose(dir -> {
            if (dir == null) return CompletableFuture.completedFuture(null);
            return api.download(List.of(path), dir).thenApply(v -> dir);
        }).thenCompose(dir -> {
            if (dir == null || path == null || path.getFileName() == null) return CompletableFuture.completedFuture(null);
            Path fileName = path.getFileName();
            Path localFile = dir.resolve(fileName);
            return readLocalBytes(localFile);
        }).exceptionallyCompose(ex -> api.readFile(path).thenApply(PlayerDataParser::decodeBinary));
    }

    private CompletableFuture<byte[]> readLocalBytes(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (path == null || !Files.exists(path)) return null;
                return Files.readAllBytes(path);
            } catch (Exception e) {
                return null;
            }
        }, Executors.IO);
    }
}
