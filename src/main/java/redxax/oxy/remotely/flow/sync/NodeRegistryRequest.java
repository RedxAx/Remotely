package redxax.oxy.remotely.flow.sync;

import java.util.HashMap;
import java.util.Map;

public class NodeRegistryRequest {
    private Map<String, String> pluginChecksums = new HashMap<>();

    public Map<String, String> getPluginChecksums() {
        return pluginChecksums;
    }

    public void setPluginChecksums(Map<String, String> pluginChecksums) {
        this.pluginChecksums = pluginChecksums != null ? pluginChecksums : new HashMap<>();
    }
}
