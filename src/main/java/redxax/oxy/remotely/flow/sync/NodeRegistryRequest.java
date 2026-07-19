package redxax.oxy.remotely.flow.sync;

import java.util.HashMap;
import java.util.Map;

public class NodeRegistryRequest {
    private int contractVersion;
    private String registryChecksum = "";
    private Map<String, String> pluginChecksums = new HashMap<>();

    public int getContractVersion() {
        return contractVersion;
    }

    public void setContractVersion(int contractVersion) {
        this.contractVersion = contractVersion;
    }

    public String getRegistryChecksum() {
        return registryChecksum != null ? registryChecksum : "";
    }

    public void setRegistryChecksum(String registryChecksum) {
        this.registryChecksum = registryChecksum != null ? registryChecksum : "";
    }

    public Map<String, String> getPluginChecksums() {
        return pluginChecksums;
    }

    public void setPluginChecksums(Map<String, String> pluginChecksums) {
        this.pluginChecksums = pluginChecksums != null ? pluginChecksums : new HashMap<>();
    }
}
