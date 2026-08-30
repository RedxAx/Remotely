package redxax.oxy.remotely.ui.settings.controllers;

import java.util.Map;
import java.util.Set;

public interface ServerStartupSettingsProvider {
    boolean available(String key);

    String value(String key);

    void value(String key, String value);

    static ServerStartupSettingsProvider map(Map<String, String> values) {
        return map(values, Set.of());
    }

    static ServerStartupSettingsProvider map(Map<String, String> values, Set<String> hidden) {
        return new ServerStartupSettingsProvider() {
            @Override
            public boolean available(String key) {
                return values != null && values.containsKey(key) && (hidden == null || !hidden.contains(key));
            }

            @Override
            public String value(String key) {
                return values == null ? "" : values.getOrDefault(key, "");
            }

            @Override
            public void value(String key, String value) {
                if (values != null && values.containsKey(key)) values.put(key, value);
            }
        };
    }
}
