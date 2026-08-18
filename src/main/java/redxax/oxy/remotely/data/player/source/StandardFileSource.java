package redxax.oxy.remotely.data.player.source;

import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import redxax.oxy.remotely.data.managed.BanEntry;
import redxax.oxy.remotely.data.managed.IpBanEntry;
import redxax.oxy.remotely.data.managed.OpEntry;
import redxax.oxy.remotely.data.managed.UserCacheEntry;
import redxax.oxy.remotely.data.managed.WhitelistEntry;
import redxax.oxy.remotely.data.player.PlayerService;
import redxax.oxy.remotely.data.player.PlayerUpdateBatch;
import redxax.oxy.remotely.data.player.model.BanInfo;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.instance.Instance;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import restudio.rebase.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;
import java.util.stream.Collectors;

public class StandardFileSource implements IPlayerSource {
    private final RebaseAPI api;
    private PlayerService service;
    private final Gson gson = new GsonBuilder().setLenient().create();
    private boolean enabled = false;

    private final Path opsPath;
    private final Path bannedPlayersPath;
    private final Path bannedIpsPath;
    private final Path whitelistPath;
    private final Path usercachePath;

    public StandardFileSource(Instance instance, RebaseAPI api) {
        this.api = api;
        Path instancePath = Path.of(instance.getPath());
        this.opsPath = instancePath.resolve("ops.json");
        this.bannedPlayersPath = instancePath.resolve("banned-players.json");
        this.bannedIpsPath = instancePath.resolve("banned-ips.json");
        this.whitelistPath = instancePath.resolve("whitelist.json");
        this.usercachePath = instancePath.resolve("usercache.json");
    }

    @Override
    public void init(PlayerService context) {
        this.service = context;
    }

    @Override
    public void enable() {
        if (!enabled) {
            enabled = true;
            refreshAll();
        }
    }

    @Override
    public void disable() {
        enabled = false;
    }

    @Override
    public void refresh() {
        refreshAll();
    }

    @Override
    public int getPriority() {
        return 20;
    }

    public void refreshAll() {
        loadJsonFile(opsPath, new TypeToken<List<OpEntry>>() {}).thenAccept(this::processOps);
        loadJsonFile(bannedPlayersPath, new TypeToken<List<BanEntry>>() {}).thenAccept(this::processBans);
        loadJsonFile(bannedIpsPath, new TypeToken<List<IpBanEntry>>() {}).thenAccept(this::processIpBans);
        loadJsonFile(whitelistPath, new TypeToken<List<WhitelistEntry>>() {}).thenAccept(this::processWhitelist);
        loadJsonFile(usercachePath, new TypeToken<List<UserCacheEntry>>() {}).thenAccept(this::processUsercache);
    }

    public void updateFromContent(String fileName, String content) {
        if (!enabled || content == null || content.isEmpty()) return;
        try {
            JsonReader reader = new JsonReader(new StringReader(content));
            reader.setLenient(true);
            
            if (fileName.endsWith("ops.json")) {
                List<OpEntry> ops = gson.fromJson(reader, new TypeToken<List<OpEntry>>() {}.getType());
                processOps(ops);
            } else if (fileName.endsWith("banned-players.json")) {
                List<BanEntry> bans = gson.fromJson(reader, new TypeToken<List<BanEntry>>() {}.getType());
                processBans(bans);
            } else if (fileName.endsWith("banned-ips.json")) {
                List<IpBanEntry> ipBans = gson.fromJson(reader, new TypeToken<List<IpBanEntry>>() {}.getType());
                processIpBans(ipBans);
            } else if (fileName.endsWith("whitelist.json")) {
                List<WhitelistEntry> whitelist = gson.fromJson(reader, new TypeToken<List<WhitelistEntry>>() {}.getType());
                processWhitelist(whitelist);
            } else if (fileName.endsWith("usercache.json")) {
                List<UserCacheEntry> cache = gson.fromJson(reader, new TypeToken<List<UserCacheEntry>>() {}.getType());
                processUsercache(cache);
            }
        } catch (Exception e) {
            ReLog.logger(LogTypes.FILESYSTEM).source(LogSource.resource(fileName, fileName)).component(StandardFileSource.class).operation("Read Player Data").error("Could not parse player data", e);
        }
    }

    private void processOps(List<OpEntry> ops) {
        if (ops == null || service == null) return;
        PlayerUpdateBatch batch = new PlayerUpdateBatch("files", getPriority());
        for (OpEntry op : ops) {
            UUID uuid = safeUuid(op.uuid);
            if (uuid == null) continue;
            PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(uuid, op.name);
            update.setOp(true);
            batch.add(update);
            service.ensurePlayer(uuid, op.name, "files", getPriority());
        }

        Set<String> opUuids = ops.stream()
            .map(o -> o.uuid)
            .filter(u -> u != null && !u.isBlank())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        service.getRegistry().getAll().stream()
            .filter(UnifiedPlayer::isOp)
            .filter(p -> !opUuids.contains(p.getUuid().toString().toLowerCase()))
            .forEach(p -> {
                PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(p.getUuid(), p.getName());
                update.setOp(false);
                batch.add(update);
            });

        service.submitUpdate(batch);
    }

    private void processBans(List<BanEntry> bans) {
        if (bans == null || service == null) return;
        PlayerUpdateBatch batch = new PlayerUpdateBatch("files", getPriority());
        for (BanEntry ban : bans) {
            UUID uuid = safeUuid(ban.uuid);
            if (uuid == null) continue;
            PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(uuid, ban.name);
            BanInfo banInfo = new BanInfo(ban.uuid, ban.name, ban.created, ban.source, ban.expires, ban.reason);
            update.setBan(banInfo);
            batch.add(update);
            service.ensurePlayer(uuid, ban.name, "files", getPriority());
        }

        Set<String> bannedUuids = bans.stream()
            .map(b -> b.uuid)
            .filter(u -> u != null && !u.isBlank())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        service.getRegistry().getAll().stream()
            .filter(p -> p.getBan().getValue() != null)
            .filter(p -> !bannedUuids.contains(p.getUuid().toString().toLowerCase()))
            .forEach(p -> {
                PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(p.getUuid(), p.getName());
                update.clearBan();
                batch.add(update);
            });

        service.submitUpdate(batch);
    }

    private void processIpBans(List<IpBanEntry> ipBans) {
        if (ipBans == null || service == null) return;
        PlayerUpdateBatch batch = new PlayerUpdateBatch("files", getPriority());

        Set<String> bannedIps = ipBans.stream()
            .map(b -> b.ip)
            .filter(ip -> ip != null && !ip.isBlank())
            .collect(Collectors.toSet());

        service.getRegistry().getAll().stream()
            .filter(p -> p.getIp().getValue() != null)
            .forEach(p -> {
                String ip = p.getIp().getValue();
                if (ip == null || ip.isBlank()) return;
                boolean isBanned = bannedIps.contains(ip);
                if (isBanned) {
                    PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(p.getUuid(), p.getName());
                    BanInfo banInfo = new BanInfo(p.getUuid().toString(), p.getName(), "", "IP Ban", "", "IP banned");
                    update.setBan(banInfo);
                    batch.add(update);
                }
            });

        if (!batch.getUpdates().isEmpty()) {
            service.submitUpdate(batch);
        }
    }

    private void processWhitelist(List<WhitelistEntry> whitelist) {
        if (whitelist == null || service == null) return;
        for (WhitelistEntry entry : whitelist) {
            UUID uuid = safeUuid(entry.uuid);
            if (uuid != null) {
                service.ensurePlayer(uuid, entry.name, "files", getPriority());
            }
        }
    }

    private void processUsercache(List<UserCacheEntry> cache) {
        if (cache == null || service == null) return;
        PlayerUpdateBatch batch = new PlayerUpdateBatch("files", getPriority());
        Set<UUID> seen = new HashSet<>();
        for (UserCacheEntry entry : cache) {
            UUID uuid = safeUuid(entry.uuid);
            if (uuid == null) continue;
            if (!seen.add(uuid)) continue;
            PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(uuid, entry.name);
            batch.add(update);
        }
        if (!batch.getUpdates().isEmpty()) {
            service.submitUpdate(batch);
        }
    }

    private UUID safeUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        try {
            return UUID.fromString(uuid);
        } catch (Exception ignored) {
            return null;
        }
    }

    private <T> Async<List<T>> loadJsonFile(Path path, TypeToken<List<T>> typeToken) {
        return JvmAsyncBridge.fromFuture(api.fileExists(path)).thenCompose(exists -> {
            if (!exists) return Async.completed(null);
            return JvmAsyncBridge.fromFuture(api.readFile(path)).thenApply(content -> {
                if (content == null || content.isEmpty()) return null;
                Type type = typeToken.getType();
                return gson.fromJson(content, type);
            });
        });
    }
}
