package redxax.oxy.remotely.data.player.action;

import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import restudio.rebase.msmp.IMSMPApi;
import restudio.rebase.msmp.MSMPManager;

import restudio.rescreen.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;

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
    public Async<Void> execute(UnifiedPlayer player, String actionType, Object... args) {
        IMSMPApi api = msmpManager.getApi();
        if (api == null || !msmpManager.isConnected) return Async.failed(new IllegalStateException("Not connected to MSMP"));

        String uuid = player.getUuid().toString();
        String name = player.getName();

        Async<?> future = switch (actionType) {
            case "kick" -> {
                String reason = args.length > 0 ? (String) args[0] : "Kicked by operator";
                yield JvmAsyncBridge.fromFuture(api.kickPlayer(uuid, reason));
            }
            case "ban" -> {
                String reason = args.length > 0 ? (String) args[0] : "Banned by operator";
                boolean ipBan = args.length > 1 && (boolean) args[1];
                if (ipBan && player.getIp().getValue() != null) {
                    yield JvmAsyncBridge.fromFuture(api.banIp(player.getIp().getValue(), uuid, reason, null));
                } else {
                    yield JvmAsyncBridge.fromFuture(api.banPlayer(uuid, name, reason, null));
                }
            }
            case "unban" -> JvmAsyncBridge.fromFuture(api.unbanPlayer(uuid));
            case "op" -> JvmAsyncBridge.fromFuture(api.opPlayer(uuid, 4));
            case "deop" -> JvmAsyncBridge.fromFuture(api.deopPlayer(uuid));
            default -> Async.failed(new UnsupportedOperationException("Unknown action: " + actionType));
        };

        return future.thenApply(v -> null);
    }

    @Override
    public int getPriority() {
        return 30;
    }
}
