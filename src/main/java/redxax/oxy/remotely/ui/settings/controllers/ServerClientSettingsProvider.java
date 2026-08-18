package redxax.oxy.remotely.ui.settings.controllers;

import java.util.List;
import java.util.function.Supplier;

import restudio.rebase.platform.Async;

public interface ServerClientSettingsProvider {
    String SCAN = "servers.scan";
    String CUSTOM_PROXY = "servers.custom-proxy";
    String PROXY_HOST = "servers.proxy-host";
    String PROXY_USER = "servers.proxy-user";
    String TERMINAL_SUGGESTIONS = "servers.terminal-suggestions";
    String QUICK_PRECREATE = "servers.quick-precreate";
    String QUICK_KEEP_RUNNING = "servers.quick-keep-running";
    String QUICK_AUTO_RESTART = "servers.quick-auto-restart";
    String QUICK_MIRROR_MODS = "servers.quick-mirror-mods";

    default boolean available(String control) {
        return true;
    }

    default boolean desktopInventory() {
        return true;
    }

    default List<QuickServer> quickServers() {
        return List.of();
    }

    default List<HiddenServer> hiddenServers() {
        return List.of();
    }

    record QuickServer(String name, String version, boolean hidden, Runnable toggleVisibility, Supplier<Async<Void>> delete) {
    }

    record HiddenServer(String name, String location, String version, Runnable show) {
    }
}
