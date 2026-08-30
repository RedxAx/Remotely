package redxax.oxy.remotely.data.flow.player;

import java.util.List;

public class PlayerTrackingUpdate {
    private String type;
    private String reason;
    private String playerId;
    private PlayerDossier dossier;
    private List<PlayerDossier> dossiers;

    public PlayerTrackingUpdate() {
    }

    public PlayerTrackingUpdate(String type, String reason, String playerId, PlayerDossier dossier, List<PlayerDossier> dossiers) {
        this.type = type; this.reason = reason; this.playerId = playerId; this.dossier = dossier; this.dossiers = dossiers;
    }

    public String getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public String getPlayerId() {
        return playerId;
    }

    public PlayerDossier getDossier() {
        return dossier;
    }

    public List<PlayerDossier> getDossiers() {
        return dossiers == null ? List.of() : dossiers;
    }
}
