package redxax.oxy.remotely.data.player.source;

import redxax.oxy.remotely.data.player.PlayerService;
import redxax.oxy.remotely.data.player.PlayerUpdateBatch;
import redxax.oxy.remotely.data.player.model.BanInfo;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import restudio.rebase.msmp.IMSMPApi;
import restudio.rebase.msmp.MSMPManager;
import restudio.rebase.msmp.dto.OpEntry;
import restudio.rebase.msmp.dto.Player;
import restudio.rescreen.debug.DebugManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MsmpPlayerSource implements IPlayerSource {
    private final MSMPManager msmpManager;
    private PlayerService service;
    private boolean enabled = false;

    public MsmpPlayerSource(MSMPManager msmpManager) {
        this.msmpManager = msmpManager;
    }

    @Override
    public void init(PlayerService context) {
        this.service = context;
    }

    @Override
    public void enable() {
        if (!enabled) {
            enabled = true;
            msmpManager.setOnPlayersChange(this::onMsmpPlayersUpdate);
            if (msmpManager.isConnected) {
                refreshAll();
            } else {
                msmpManager.connect();
            }
        }
    }

    @Override
    public void disable() {
        enabled = false;
        msmpManager.setOnPlayersChange(null);
    }

    @Override
    public void refresh() {
        refreshAll();
    }

    @Override
    public int getPriority() {
        return 30;
    }

    private void onMsmpPlayersUpdate(List<Player> msmpPlayers) {
        if (msmpPlayers != null) {
            refreshAll();
        }
    }

    public void refreshAll() {
        IMSMPApi api = msmpManager.getApi();
        if (api == null || !api.isConnected()) return;

        CompletableFuture<List<Player>> playersF = api.getPlayers();
        CompletableFuture<List<restudio.rebase.msmp.dto.BanEntry>> bansF = api.getBans();
        CompletableFuture<List<restudio.rebase.msmp.dto.BanEntry>> ipBansF = api.getIpBans();
        CompletableFuture<List<OpEntry>> opsF = api.getOps();

        CompletableFuture.allOf(playersF, bansF, ipBansF, opsF).thenRun(() -> {
            try {
                updateCache(playersF.join(), bansF.join(), ipBansF.join(), opsF.join());
            } catch (Exception e) {
                DebugManager.getInstance().log("MsmpPlayerSource", "Failed to refresh data: " + e.getMessage());
            }
        });
    }

    private void updateCache(List<Player> msmpPlayers, List<restudio.rebase.msmp.dto.BanEntry> bans, List<restudio.rebase.msmp.dto.BanEntry> ipBans, List<OpEntry> ops) {
        if (service == null) return;
        PlayerUpdateBatch batch = new PlayerUpdateBatch("msmp", getPriority());

        if (msmpPlayers != null) {
            for (Player mp : msmpPlayers) {
                PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(mp.uuid, mp.name);
                update.setOnline(true);
                update.setPing(mp.ping);
                update.setIp(mp.address);
                batch.add(update);
            }

            service.getRegistry().getAll().stream()
                .filter(UnifiedPlayer::isOnline)
                .filter(p -> msmpPlayers.stream().noneMatch(mp -> mp.uuid.equals(p.getUuid())))
                .forEach(p -> {
                    PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(p.getUuid(), p.getName());
                    update.setOnline(false);
                    update.setPing(-1);
                    batch.add(update);
                });
        }

        if (bans != null) {
            for (restudio.rebase.msmp.dto.BanEntry b : bans) {
                if (b.uuid == null) continue;
                try {
                    UUID u = UUID.fromString(b.uuid);
                    PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(u, b.name);
                    BanInfo banInfo = new BanInfo(b.uuid, b.name, b.created, b.source, b.expires, b.reason);
                    update.setBan(banInfo);
                    batch.add(update);
                } catch (Exception ignored) {}
            }
            service.getRegistry().getAll().stream()
                .filter(p -> p.getBan().getValue() != null)
                .filter(p -> bans.stream().noneMatch(b -> b.uuid != null && b.uuid.equalsIgnoreCase(p.getUuid().toString())))
                .forEach(p -> {
                    PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(p.getUuid(), p.getName());
                    update.clearBan();
                    batch.add(update);
                });
        }

        if (ops != null) {
            for (OpEntry op : ops) {
                if (op.uuid == null) continue;
                try {
                    UUID u = UUID.fromString(op.uuid);
                    PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(u, op.name);
                    update.setOp(true);
                    batch.add(update);
                } catch (Exception ignored) {}
            }
            service.getRegistry().getAll().stream()
                .filter(UnifiedPlayer::isOp)
                .filter(p -> ops.stream().noneMatch(o -> o.uuid != null && o.uuid.equalsIgnoreCase(p.getUuid().toString())))
                .forEach(p -> {
                    PlayerUpdateBatch.PlayerUpdate update = new PlayerUpdateBatch.PlayerUpdate(p.getUuid(), p.getName());
                    update.setOp(false);
                    batch.add(update);
                });
        }

        service.submitUpdate(batch);
    }
}
