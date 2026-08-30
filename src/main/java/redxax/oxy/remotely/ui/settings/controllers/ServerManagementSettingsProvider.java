package redxax.oxy.remotely.ui.settings.controllers;

public interface ServerManagementSettingsProvider {
    boolean remote();

    String property(String key);

    default String property(String key, String fallback) {
        String value = property(key);
        return value == null ? fallback : value;
    }

    void setProperty(String key, String value);

    void remove(String key);
}
