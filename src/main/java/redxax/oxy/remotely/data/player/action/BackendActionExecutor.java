package redxax.oxy.remotely.data.player.action;

import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import restudio.rebase.backend.feature.PlayerManagementFeature;

import java.util.concurrent.CompletableFuture;

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
    public CompletableFuture<Void> execute(UnifiedPlayer player, String actionType, Object... args) {
        return switch (actionType) {
            case "kick" -> {
                String reason = args.length > 0 ? (String) args[0] : "Kicked by operator";
                yield feature.kick(player.getUuid(), reason);
            }
            case "ban" -> {
                String reason = args.length > 0 ? (String) args[0] : "Banned by operator";
                boolean ipBan = args.length > 1 && (boolean) args[1];
                yield feature.ban(player.getUuid(), reason, ipBan);
            }
            case "unban" -> feature.unban(player.getUuid());
            case "op" -> feature.setOp(player.getUuid(), true);
            case "deop" -> feature.setOp(player.getUuid(), false);
            default -> CompletableFuture.failedFuture(new UnsupportedOperationException("Unknown action: " + actionType));
        };
    }

    @Override
    public int getPriority() {
        return 20;
    }
}
