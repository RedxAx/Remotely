package redxax.oxy.remotely.data.player.management;

import redxax.oxy.remotely.data.playerdata.PlayerData;

public record PlayerOperationResult(String operationId, boolean success, String reason, long revision, PlayerData authoritativeData) {
    public static PlayerOperationResult failed(String operationId, String reason) {
        return new PlayerOperationResult(operationId, false, reason, 0L, null);
    }
}
