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
    private String status;
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

    public String getStatus() {
        return status;
    }

    void setSuccess(boolean success) {
        this.success = success;
    }

    void setAction(String action) {
        this.action = action;
    }

    void setMessage(String message) {
        this.message = message;
    }

    void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    void setActorClientId(String actorClientId) {
        this.actorClientId = actorClientId;
    }

    void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    void setFinishedAt(long finishedAt) {
        this.finishedAt = finishedAt;
    }

    void setSafetyBackupId(String safetyBackupId) {
        this.safetyBackupId = safetyBackupId;
    }

    void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    void setStatus(String status) {
        this.status = status;
    }

    void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }

    void setData(Map<String, Object> data) {
        this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
    }
}
