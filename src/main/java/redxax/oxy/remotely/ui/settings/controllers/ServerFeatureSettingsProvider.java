package redxax.oxy.remotely.ui.settings.controllers;

public interface ServerFeatureSettingsProvider {
    boolean local();

    boolean lifecyclePersistent();

    void lifecyclePersistent(boolean value);

    boolean restartOnCrash();

    void restartOnCrash(boolean value);

    int restartDelaySeconds();

    void restartDelaySeconds(int value);

    int restartMaxAttempts();

    void restartMaxAttempts(int value);

    String property(String key, String fallback);

    void setProperty(String key, String value);
}
