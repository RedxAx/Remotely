package redxax.oxy.remotely.ui.server;

import java.util.Map;

public interface ServerConfigurationTarget {
    Object raw();

    String id();

    String name();

    void name(String value);

    String backendType();

    Map<String, String> backendCredentials();

    void backend(String type, Map<String, String> credentials);

    Object modLoader();

    void modLoader(Object value);

    String version();

    String software();

    String build();

    boolean linkedModpack();

    Map<String, String> properties();

    void property(String key, String value);

    void removeProperty(String key);

    void state(String value);

    void log(String message);

    default boolean remote() {
        return !"LOCAL".equalsIgnoreCase(backendType());
    }

    default boolean restudio() {
        return "RESTUDIO".equalsIgnoreCase(backendType());
    }

    static ServerConfigurationTarget unavailable(Object raw) {
        return new ServerConfigurationTarget() {
            @Override public Object raw() { return raw; }
            @Override public String id() { return ""; }
            @Override public String name() { return ""; }
            @Override public void name(String value) { }
            @Override public String backendType() { return "LOCAL"; }
            @Override public Map<String, String> backendCredentials() { return Map.of(); }
            @Override public void backend(String type, Map<String, String> credentials) { }
            @Override public Object modLoader() { return null; }
            @Override public void modLoader(Object value) { }
            @Override public String version() { return ""; }
            @Override public String software() { return ""; }
            @Override public String build() { return ""; }
            @Override public boolean linkedModpack() { return false; }
            @Override public Map<String, String> properties() { return Map.of(); }
            @Override public void property(String key, String value) { }
            @Override public void removeProperty(String key) { }
            @Override public void state(String value) { }
            @Override public void log(String message) { }
        };
    }
}
