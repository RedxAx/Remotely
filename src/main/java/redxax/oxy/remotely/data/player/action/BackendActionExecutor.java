package redxax.oxy.remotely.data.player.action;

import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import restudio.rebase.backend.feature.PlayerManagementFeature;

import restudio.rescreen.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;

public class BackendActionExecutor implements IActionExecutor {
    private final PlayerManagementFeature feature;

    public BackendActionExecutor(PlayerManagementFeature feature) {
        this.feature = feature;
    }

    @Override
    public boolean canExecute(String actionType) {
        return switch (actionType) {
            case "kick", "ban", "unban", "op", "deop" -> true;
            default -> false;
        };
    }

    @Override
    public Async<Void> execute(UnifiedPlayer player, String actionType, Object... args) {
        return switch (actionType) {
            case "kick" -> {
                String reason = args.length > 0 ? (String) args[0] : "Kicked by operator";
                yield JvmAsyncBridge.fromFuture(feature.kick(player.getUuid(), reason));
            }
            case "ban" -> {
                String reason = args.length > 0 ? (String) args[0] : "Banned by operator";
                boolean ipBan = args.length > 1 && (boolean) args[1];
                yield JvmAsyncBridge.fromFuture(feature.ban(player.getUuid(), reason, ipBan));
            }
            case "unban" -> JvmAsyncBridge.fromFuture(feature.unban(player.getUuid()));
            case "op" -> JvmAsyncBridge.fromFuture(feature.setOp(player.getUuid(), true));
            case "deop" -> JvmAsyncBridge.fromFuture(feature.setOp(player.getUuid(), false));
            default -> Async.failed(new UnsupportedOperationException("Unknown action: " + actionType));
        };
    }

    @Override
    public int getPriority() {
        return 20;
    }
}
