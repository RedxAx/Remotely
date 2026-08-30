package redxax.oxy.remotely.host;

import redxax.oxy.remotely.ui.server.ServerDetailsTarget;
import redxax.oxy.remotely.ui.server.ServerScreenHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.ui.widgets.TerminalWidget;

import java.util.function.BiConsumer;

final class DesktopServerDetailsTarget implements ServerDetailsTarget {
    private final Instance instance;

    DesktopServerDetailsTarget(Instance instance) {
        this.instance = instance;
    }

    @Override public Object value() { return instance; }
    @Override public String id() { return instance == null || instance.getInstanceId() == null ? "" : instance.getInstanceId(); }
    @Override public String name() { return instance == null || instance.getName() == null || instance.getName().isBlank() ? id() : instance.getName(); }
    @Override public String path() { return instance == null || instance.getPath() == null ? "" : instance.getPath(); }
    @Override public String backendType() {
        return instance == null || instance.getBackendConfig() == null || instance.getBackendConfig().type == null ? "" : instance.getBackendConfig().type;
    }
    @Override public ServerScreenHost.ServerState state() {
        return instance == null || instance.getState() == null ? ServerScreenHost.ServerState.UNKNOWN
                : ServerScreenHost.ServerState.parse(instance.getState().name());
    }
    @Override public boolean server() { return instance != null && instance.isServer(); }
    @Override public String version() { return instance == null || instance.getVersionId() == null ? "" : instance.getVersionId(); }
    @Override public int port() { return instance == null ? 0 : instance.getPort(); }
    @Override public String setting(String key, String fallback) {
        if (instance == null || instance.getSettings() == null) return fallback;
        return instance.getSettings().getProperty(key, fallback);
    }
    @Override public void setState(ServerScreenHost.ServerState state) {
        if (instance == null || state == null || state == ServerScreenHost.ServerState.UNKNOWN) return;
        instance.setState(InstanceState.valueOf(state.name()));
    }
    @Override public Object nativeState(ServerScreenHost.ServerState state) {
        if (state == null || state == ServerScreenHost.ServerState.UNKNOWN) return state;
        return InstanceState.valueOf(state.name());
    }
    @Override public void addLogListener(BiConsumer<Integer, String> listener) { if (instance != null && listener != null) instance.addLogListener(listener); }
    @Override public void removeLogListener(BiConsumer<Integer, String> listener) { if (instance != null && listener != null) instance.removeLogListener(listener); }
    @Override public void attachTerminal(TerminalWidget terminal) { if (instance != null && terminal != null) instance.attachTerminalListener(terminal); }
    @Override public void detachTerminal() { if (instance != null) instance.detachTerminalListener(); }
}
