package redxax.oxy.remotely.flow.sync;

import redxax.oxy.remotely.flow.data.FlowDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NodeRegistrySnapshot {
    public static final int CURRENT_CONTRACT_VERSION = 2;
    public static final int MINIMUM_SUPPORTED_CONTRACT_VERSION = 2;
    private int contractVersion;
    private int minimumClientContractVersion;
    private String serverIdentity = "";
    private long compatibleUntil;
    private List<String> capabilities = new ArrayList<>();
    private Map<String, Object> registryDiagnostics = Map.of();
    private boolean fullSync;
    private String baseRegistryChecksum = "";
    private String registryChecksum;
    private long generatedAt;
    private List<String> nodeIds = new ArrayList<>();
    private List<NodePluginPayload> plugins = new ArrayList<>();
    private List<String> removedPlugins = new ArrayList<>();
    private Map<String, Map<String, List<String>>> propertyActions;
    private Map<String, Map<String, FlowDataType>> propertyOutputTypes;
    private List<FlowPropertyMetadata> propertyMetadata = new ArrayList<>();
    private List<FlowResourceMetadata> resourceMetadata = new ArrayList<>();
    private List<FlowTypeMetadata> typeMetadata = new ArrayList<>();
    private List<FlowCategoryMetadata> categoryMetadata = new ArrayList<>();
    private List<FlowOptionSourceMetadata> optionSourceMetadata = new ArrayList<>();
    private List<FlowConversionRule> conversionRules = new ArrayList<>();

    public int getContractVersion() {
        return contractVersion;
    }

    public void setContractVersion(int contractVersion) {
        this.contractVersion = contractVersion;
    }

    public int getMinimumClientContractVersion() {
        return minimumClientContractVersion;
    }

    public void setMinimumClientContractVersion(int minimumClientContractVersion) {
        this.minimumClientContractVersion = minimumClientContractVersion;
    }

    public String getServerIdentity() {
        return serverIdentity != null ? serverIdentity : "";
    }

    public void setServerIdentity(String serverIdentity) {
        this.serverIdentity = serverIdentity != null ? serverIdentity : "";
    }

    public long getCompatibleUntil() {
        return compatibleUntil;
    }

    public void setCompatibleUntil(long compatibleUntil) {
        this.compatibleUntil = compatibleUntil;
    }

    public List<String> getCapabilities() {
        return capabilities != null ? capabilities : List.of();
    }

    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities != null ? capabilities : new ArrayList<>();
    }

    public Map<String, Object> getRegistryDiagnostics() {
        return registryDiagnostics != null ? registryDiagnostics : Map.of();
    }

    public void setRegistryDiagnostics(Map<String, Object> registryDiagnostics) {
        this.registryDiagnostics = registryDiagnostics != null ? Map.copyOf(registryDiagnostics) : Map.of();
    }

    public boolean isFullSync() {
        return fullSync;
    }

    public void setFullSync(boolean fullSync) {
        this.fullSync = fullSync;
    }

    public String getBaseRegistryChecksum() {
        return baseRegistryChecksum != null ? baseRegistryChecksum : "";
    }

    public void setBaseRegistryChecksum(String baseRegistryChecksum) {
        this.baseRegistryChecksum = baseRegistryChecksum != null ? baseRegistryChecksum : "";
    }

    public boolean canApplyTo(String currentRegistryChecksum) {
        return fullSync || !getBaseRegistryChecksum().isBlank() && getBaseRegistryChecksum().equals(currentRegistryChecksum);
    }

    public String getRegistryChecksum() {
        return registryChecksum;
    }

    public void setRegistryChecksum(String registryChecksum) {
        this.registryChecksum = registryChecksum;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(long generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<String> getNodeIds() {
        return nodeIds != null ? nodeIds : List.of();
    }

    public void setNodeIds(List<String> nodeIds) {
        this.nodeIds = nodeIds != null ? nodeIds : new ArrayList<>();
    }

    public List<NodePluginPayload> getPlugins() {
        return plugins != null ? plugins : List.of();
    }

    public void setPlugins(List<NodePluginPayload> plugins) {
        this.plugins = plugins != null ? plugins : new ArrayList<>();
    }

    public List<String> getRemovedPlugins() {
        return removedPlugins != null ? removedPlugins : List.of();
    }

    public void setRemovedPlugins(List<String> removedPlugins) {
        this.removedPlugins = removedPlugins != null ? removedPlugins : new ArrayList<>();
    }

    public Map<String, Map<String, List<String>>> getPropertyActions() {
        return propertyActions;
    }

    public void setPropertyActions(Map<String, Map<String, List<String>>> propertyActions) {
        this.propertyActions = propertyActions;
    }

    public Map<String, Map<String, FlowDataType>> getPropertyOutputTypes() {
        return propertyOutputTypes;
    }

    public void setPropertyOutputTypes(Map<String, Map<String, FlowDataType>> propertyOutputTypes) {
        this.propertyOutputTypes = propertyOutputTypes;
    }

    public List<FlowPropertyMetadata> getPropertyMetadata() {
        return propertyMetadata != null ? propertyMetadata : List.of();
    }

    public void setPropertyMetadata(List<FlowPropertyMetadata> propertyMetadata) {
        this.propertyMetadata = propertyMetadata != null ? propertyMetadata : new ArrayList<>();
    }

    public List<FlowResourceMetadata> getResourceMetadata() {
        return resourceMetadata != null ? resourceMetadata : List.of();
    }

    public void setResourceMetadata(List<FlowResourceMetadata> resourceMetadata) {
        this.resourceMetadata = resourceMetadata != null ? resourceMetadata : new ArrayList<>();
    }

    public List<FlowTypeMetadata> getTypeMetadata() {
        return typeMetadata != null ? typeMetadata : List.of();
    }

    public void setTypeMetadata(List<FlowTypeMetadata> typeMetadata) {
        this.typeMetadata = typeMetadata != null ? typeMetadata : new ArrayList<>();
        resolveOptionSourceTypes();
    }

    public List<FlowCategoryMetadata> getCategoryMetadata() {
        return categoryMetadata != null ? categoryMetadata : List.of();
    }

    public void setCategoryMetadata(List<FlowCategoryMetadata> categoryMetadata) {
        this.categoryMetadata = categoryMetadata != null ? categoryMetadata : new ArrayList<>();
    }

    public List<FlowOptionSourceMetadata> getOptionSourceMetadata() {
        resolveOptionSourceTypes();
        return optionSourceMetadata != null ? optionSourceMetadata : List.of();
    }

    public void setOptionSourceMetadata(List<FlowOptionSourceMetadata> optionSourceMetadata) {
        this.optionSourceMetadata = optionSourceMetadata != null ? optionSourceMetadata : new ArrayList<>();
        resolveOptionSourceTypes();
    }

    private void resolveOptionSourceTypes() {
        List<FlowOptionSourceMetadata> sources = optionSourceMetadata != null ? optionSourceMetadata : List.of();
        for (FlowOptionSourceMetadata source : sources) {
            if (source == null || source.getId() == null || !"string".equals(source.getValueType())) {
                continue;
            }
            List<String> matchingTypes = getTypeMetadata().stream()
                .filter(type -> type != null && source.getId().equals(type.getCatalogSource()))
                .map(FlowTypeMetadata::getId)
                .filter(typeId -> typeId != null && !typeId.isBlank())
                .distinct()
                .toList();
            if (matchingTypes.size() == 1) {
                source.setValueType(matchingTypes.getFirst());
            }
        }
    }

    public List<FlowConversionRule> getConversionRules() {
        return conversionRules != null ? conversionRules : List.of();
    }

    public void setConversionRules(List<FlowConversionRule> conversionRules) {
        this.conversionRules = conversionRules != null ? conversionRules : new ArrayList<>();
    }
}
