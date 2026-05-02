package redxax.oxy.remotely.flow.sync;

import redxax.oxy.remotely.flow.data.FlowDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NodeRegistrySnapshot {
    private boolean fullSync;
    private String registryChecksum;
    private long generatedAt;
    private List<String> nodeIds = new ArrayList<>();
    private List<NodePluginPayload> plugins = new ArrayList<>();
    private List<String> removedPlugins = new ArrayList<>();
    private Map<String, Map<String, List<String>>> propertyActions;
    private Map<String, Map<String, FlowDataType>> propertyOutputTypes;
    private List<FlowTypeMetadata> typeMetadata = new ArrayList<>();
    private List<FlowCategoryMetadata> categoryMetadata = new ArrayList<>();
    private List<FlowOptionSourceMetadata> optionSourceMetadata = new ArrayList<>();
    private List<FlowConversionRule> conversionRules = new ArrayList<>();

    public boolean isFullSync() {
        return fullSync;
    }

    public void setFullSync(boolean fullSync) {
        this.fullSync = fullSync;
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
        return nodeIds;
    }

    public void setNodeIds(List<String> nodeIds) {
        this.nodeIds = nodeIds != null ? nodeIds : new ArrayList<>();
    }

    public List<NodePluginPayload> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<NodePluginPayload> plugins) {
        this.plugins = plugins != null ? plugins : new ArrayList<>();
    }

    public List<String> getRemovedPlugins() {
        return removedPlugins;
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

    public List<FlowTypeMetadata> getTypeMetadata() {
        return typeMetadata;
    }

    public void setTypeMetadata(List<FlowTypeMetadata> typeMetadata) {
        this.typeMetadata = typeMetadata != null ? typeMetadata : new ArrayList<>();
    }

    public List<FlowCategoryMetadata> getCategoryMetadata() {
        return categoryMetadata;
    }

    public void setCategoryMetadata(List<FlowCategoryMetadata> categoryMetadata) {
        this.categoryMetadata = categoryMetadata != null ? categoryMetadata : new ArrayList<>();
    }

    public List<FlowOptionSourceMetadata> getOptionSourceMetadata() {
        return optionSourceMetadata;
    }

    public void setOptionSourceMetadata(List<FlowOptionSourceMetadata> optionSourceMetadata) {
        this.optionSourceMetadata = optionSourceMetadata != null ? optionSourceMetadata : new ArrayList<>();
    }

    public List<FlowConversionRule> getConversionRules() {
        return conversionRules;
    }

    public void setConversionRules(List<FlowConversionRule> conversionRules) {
        this.conversionRules = conversionRules != null ? conversionRules : new ArrayList<>();
    }
}
