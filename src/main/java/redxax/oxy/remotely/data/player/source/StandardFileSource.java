package redxax.oxy.remotely.data.player.source;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.data.managed.BanEntry;
import redxax.oxy.remotely.data.managed.IpBanEntry;
import redxax.oxy.remotely.data.managed.OpEntry;
import redxax.oxy.remotely.data.player.PlayerService;
import redxax.oxy.remotely.data.player.PlayerUpdateBatch;
import redxax.oxy.remotely.data.player.model.BanInfo;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.instance.Instance;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StandardFileSource implements IPlayerSource {
    private final RebaseAPI api;
    private PlayerService service;
    private final Gson gson = new Gson();
    private boolean enabled = false;

    private final Path opsPath;
    private final Path bannedPlayersPath;
    private final Path bannedIpsPath;

    public StandardFileSource(Instance instance, RebaseAPI api) {
        this.api = api;
        Path instancePath = Path.of(instance.getPath());
        this.opsPath = instancePath.resolve("ops.json");
        this.bannedPlayersPath = instancePath.resolve("banned-players.json");
        this.bannedIpsPath = instancePath.resolve("banned-ips.json");
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
    }

    public void updateFromContent(String fileName, String content) {
        if (!enabled || content == null || content.isEmpty()) return;
        try {
            if (fileName.endsWith("ops.json")) {
                List<OpEntry> ops = gson.fromJson(content, new TypeToken<List<OpEntry>>() {}.getType());
                processOps(ops);
            } else if (fileName.endsWith("banned-players.json")) {
                List<BanEntry> bans = gson.fromJson(content, new TypeToken<List<BanEntry>>() {}.getType());
                processBans(bans);
            } else if (fileName.endsWith("banned-ips.json")) {
                List<IpBanEntry> ipBans = gson.fromJson(content, new TypeToken<List<IpBanEntry>>() {}.getType());
                processIpBans(ipBans);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processOps(List<OpEntry> ops) {
        if (ops == null || service == null) return;
        PlayerUpdateBatch batch = new PlayerUpdateBatch("files", getPriority());
        for (OpEntry op : ops) {
            UUID uuid = UUID.fromString(op.uuid);
            PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(uuid, op.name);
            update.setOp(true);
            batch.add(update);
        }

        service.getRegistry().getAll().stream()
            .filter(UnifiedPlayer::isOp)
            .filter(p -> ops.stream().noneMatch(o -> o.uuid.equalsIgnoreCase(p.getUuid().toString())))
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
            UUID uuid = UUID.fromString(ban.uuid);
            PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(uuid, ban.name);
            BanInfo banInfo = new BanInfo(ban.uuid, ban.name, ban.created, ban.source, ban.expires, ban.reason);
            update.setBan(banInfo);
            batch.add(update);
        }

        service.getRegistry().getAll().stream()
            .filter(p -> p.getBan().getValue() != null)
            .filter(p -> bans.stream().noneMatch(b -> b.uuid.equalsIgnoreCase(p.getUuid().toString())))
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

        List<String> bannedIps = ipBans.stream().map(b -> b.ip).toList();

        service.getRegistry().getAll().stream()
            .filter(p -> p.getIp().getValue() != null)
            .forEach(p -> {
                boolean isBanned = bannedIps.contains(p.getIp().getValue());
            });
    }

    private <T> CompletableFuture<List<T>> loadJsonFile(Path path, TypeToken<List<T>> typeToken) {
        return api.fileExists(path).thenCompose(exists -> {
            if (!exists) return CompletableFuture.completedFuture(null);
            return api.readFile(path).thenApply(content -> {
                if (content == null || content.isEmpty()) return null;
                Type type = typeToken.getType();
                return gson.fromJson(content, type);
            });
        });
    }
}
