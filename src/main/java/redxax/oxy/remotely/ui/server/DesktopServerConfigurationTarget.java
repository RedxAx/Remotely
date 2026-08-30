package redxax.oxy.remotely.ui.server;

import restudio.rebase.backend.BackendConfig;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DesktopServerConfigurationTarget implements ServerConfigurationTarget {
    private final Instance instance;

    public DesktopServerConfigurationTarget(Instance instance) {
        this.instance = instance;
    }

    @Override public Object raw() { return instance; }
    @Override public String id() { return instance == null ? "" : instance.getInstanceId(); }
    @Override public String name() { return instance == null ? "" : instance.getName(); }
    @Override public void name(String value) { if (instance != null) instance.setName(value); }
    @Override public String backendType() {
        return instance == null || instance.getBackendConfig() == null ? "LOCAL" : instance.getBackendConfig().type;
    }
    @Override public Map<String, String> backendCredentials() {
        if (instance == null || instance.getBackendConfig() == null || instance.getBackendConfig().credentials == null) return Map.of();
        return Map.copyOf(instance.getBackendConfig().credentials);
    }
    @Override public void backend(String type, Map<String, String> credentials) {
        if (instance != null) instance.setBackendConfig(new BackendConfig(type, new LinkedHashMap<>(credentials == null ? Map.of() : credentials)));
    }
    @Override public Object modLoader() { return instance == null ? null : instance.getModLoader(); }
    @Override public void modLoader(Object value) { if (instance != null && value instanceof ModLoader loader) instance.setModLoader(loader); }
    @Override public String version() { return instance == null ? "" : value(instance.getVersionId()); }
    @Override public String software() { return instance == null ? "" : value(instance.getServerSoftwareType()); }
    @Override public String build() { return instance == null ? "" : value(instance.getServerBuildNumber()); }
    @Override public boolean linkedModpack() { return instance != null && instance.hasLinkedModpack(); }
    @Override public Map<String, String> properties() {
        if (instance == null) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        instance.getServerProperties().forEach((key, value) -> values.put(String.valueOf(key), String.valueOf(value)));
        return Map.copyOf(values);
    }
    @Override public void property(String key, String value) { if (instance != null && key != null) instance.getServerProperties().setProperty(key, value == null ? "" : value); }
    @Override public void removeProperty(String key) { if (instance != null && key != null) instance.getServerProperties().remove(key); }
    @Override public void state(String value) {
        if (instance == null || value == null) return;
        try { instance.setState(InstanceState.valueOf(value.toUpperCase())); } catch (IllegalArgumentException ignored) { }
    }
    @Override public void log(String message) { if (instance != null) instance.getLogger().addLog(message); }

    private static String value(String value) { return value == null ? "" : value; }
}
