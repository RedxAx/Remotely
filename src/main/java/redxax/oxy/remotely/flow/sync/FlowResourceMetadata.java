package redxax.oxy.remotely.flow.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlowResourceMetadata {
    private int schemaVersion = 2;
    private String typeId;
    private String displayName;
    private String referenceType = "resource_reference";
    private String identityRules;
    private String lifecycle;
    private String catalogSource;
    private List<String> operations = new ArrayList<>();
    private Map<String, String> operationAvailability = new LinkedHashMap<>();
    private String authoritativeService;
    private String authorizationPolicy = "trusted_server_flow";
    private boolean audited = true;
    private String defaultFolder;
    private String owner = "builtin";
    private boolean durable;
    private boolean changeEvents;
    private boolean activeRefresh;
    private boolean available;
    private String unavailableReason;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getIdentityRules() {
        return identityRules;
    }

    public void setIdentityRules(String identityRules) {
        this.identityRules = identityRules;
    }

    public String getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(String lifecycle) {
        this.lifecycle = lifecycle;
    }

    public String getCatalogSource() {
        return catalogSource;
    }

    public void setCatalogSource(String catalogSource) {
        this.catalogSource = catalogSource;
    }

    public List<String> getOperations() {
        return operations;
    }

    public void setOperations(List<String> operations) {
        this.operations = operations != null ? operations : new ArrayList<>();
    }

    public Map<String, String> getOperationAvailability() {
        return operationAvailability;
    }

    public void setOperationAvailability(Map<String, String> operationAvailability) {
        this.operationAvailability = operationAvailability != null ? new LinkedHashMap<>(operationAvailability) : new LinkedHashMap<>();
    }

    public String getAuthoritativeService() {
        return authoritativeService;
    }

    public void setAuthoritativeService(String authoritativeService) {
        this.authoritativeService = authoritativeService;
    }

    public String getAuthorizationPolicy() {
        return authorizationPolicy;
    }

    public void setAuthorizationPolicy(String authorizationPolicy) {
        this.authorizationPolicy = authorizationPolicy;
    }

    public boolean isAudited() {
        return audited;
    }

    public void setAudited(boolean audited) {
        this.audited = audited;
    }

    public String getDefaultFolder() {
        return defaultFolder;
    }

    public void setDefaultFolder(String defaultFolder) {
        this.defaultFolder = defaultFolder;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public boolean isChangeEvents() {
        return changeEvents;
    }

    public void setChangeEvents(boolean changeEvents) {
        this.changeEvents = changeEvents;
    }

    public boolean isActiveRefresh() {
        return activeRefresh;
    }

    public void setActiveRefresh(boolean activeRefresh) {
        this.activeRefresh = activeRefresh;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }
}
