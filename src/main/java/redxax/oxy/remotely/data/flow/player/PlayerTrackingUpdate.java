package redxax.oxy.remotely.data.flow.player;

import java.util.List;

public class PlayerTrackingUpdate {
    private String type;
    private String reason;
    private String playerId;
    private PlayerDossier dossier;
    private List<PlayerDossier> dossiers;

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
