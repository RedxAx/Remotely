package redxax.oxy.remotely.flow.sync;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import java.util.ArrayList;
import java.util.List;

public class NodePluginPayload {
    private String pluginId;
    private String version;
    private String description;
    private String checksum;
    private List<NodeDefinition> nodes = new ArrayList<>();

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public List<NodeDefinition> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeDefinition> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }
}
