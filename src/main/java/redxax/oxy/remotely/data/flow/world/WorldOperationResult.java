package redxax.oxy.remotely.data.flow.world;

import java.util.LinkedHashMap;
import java.util.Map;

public class WorldOperationResult {
    private boolean success;
    private String action;
    private String message;
    private String worldName;
    private String operationId;
    private String actorClientId;
    private long startedAt;
    private long finishedAt;
    private String safetyBackupId;
    private String auditId;
    private boolean requiresConfirmation;
    private Map<String, Object> data = new LinkedHashMap<>();

    public boolean isSuccess() {
        return success;
    }

    public String getAction() {
        return action;
    }

    public String getMessage() {
        return message;
    }

    public String getWorldName() {
        return worldName;
    }

    public Map<String, Object> getData() {
        return data == null ? Map.of() : data;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getActorClientId() {
        return actorClientId;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public String getSafetyBackupId() {
        return safetyBackupId;
    }

    public String getAuditId() {
        return auditId;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }
}
