package redxax.oxy.remotely.data.player.action;

import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import restudio.rebase.msmp.IMSMPApi;
import restudio.rebase.msmp.MSMPManager;

import java.util.concurrent.CompletableFuture;

public class MsmpActionExecutor implements IActionExecutor {
    private final MSMPManager msmpManager;

    public MsmpActionExecutor(MSMPManager msmpManager) {
        this.msmpManager = msmpManager;
    }

    @Override
    public boolean canExecute(String actionType) {
        if (msmpManager.getApi() == null || !msmpManager.isConnected) return false;
        return switch (actionType) {
            case "kick", "ban", "unban", "op", "deop" -> true;
            default -> false;
        };
    }

    @Override
    public CompletableFuture<Void> execute(UnifiedPlayer player, String actionType, Object... args) {
        IMSMPApi api = msmpManager.getApi();
        if (api == null || !msmpManager.isConnected) return CompletableFuture.failedFuture(new IllegalStateException("Not connected to MSMP"));

        String uuid = player.getUuid().toString();
        String name = player.getName();

        CompletableFuture<?> future = switch (actionType) {
            case "kick" -> {
                String reason = args.length > 0 ? (String) args[0] : "Kicked by operator";
                yield api.kickPlayer(uuid, reason);
            }
            case "ban" -> {
                String reason = args.length > 0 ? (String) args[0] : "Banned by operator";
                boolean ipBan = args.length > 1 && (boolean) args[1];
                if (ipBan && player.getIp().getValue() != null) {
                    yield api.banIp(player.getIp().getValue(), uuid, reason, null);
                } else {
                    yield api.banPlayer(uuid, name, reason, null);
                }
            }
            case "unban" -> api.unbanPlayer(uuid);
            case "op" -> api.opPlayer(uuid, 4);
            case "deop" -> api.deopPlayer(uuid);
            default -> CompletableFuture.failedFuture(new UnsupportedOperationException("Unknown action: " + actionType));
        };

        return future.thenApply(v -> null);
    }

    @Override
    public int getPriority() {
        return 30;
    }
}
