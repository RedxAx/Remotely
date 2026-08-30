package redxax.oxy.remotely.ui.settings.controllers;

import java.util.Map;
import java.util.function.Consumer;

public interface ServerReSyncSettingsProvider {
    Map<String, String> credentials();

    boolean reStudioBackend();

    String serverIdentifier();

    void provision(Consumer<Boolean> callback);
}
