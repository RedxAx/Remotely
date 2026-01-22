package redxax.oxy.remotely.flow.sync;

import java.util.ArrayList;
import java.util.List;

public class NodeRegistrySnapshot {
    private boolean fullSync;
    private List<String> nodeIds = new ArrayList<>();
    private List<NodePluginPayload> plugins = new ArrayList<>();
    private List<String> removedPlugins = new ArrayList<>();

    public boolean isFullSync() {
        return fullSync;
    }

    public void setFullSync(boolean fullSync) {
        this.fullSync = fullSync;
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
}
