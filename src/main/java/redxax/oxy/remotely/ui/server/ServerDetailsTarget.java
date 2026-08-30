package redxax.oxy.remotely.ui.server;

import restudio.rebase.ui.widgets.TerminalWidget;

import java.util.function.BiConsumer;

public interface ServerDetailsTarget {
    Object value();
    String id();
    String name();

    default String path() { return ""; }
    default String backendType() { return ""; }
    default ServerScreenHost.ServerState state() { return ServerScreenHost.ServerState.UNKNOWN; }
    default boolean server() { return false; }
    default boolean local() { return backendType().isBlank() || "LOCAL".equalsIgnoreCase(backendType()); }
    default boolean quickServer() { return "true".equalsIgnoreCase(setting("quickServer.enabled", "false")); }
    default String version() { return ""; }
    default int port() { return 0; }
    default String setting(String key, String fallback) { return fallback; }
    default void setState(ServerScreenHost.ServerState state) { }
    default Object nativeState(ServerScreenHost.ServerState state) { return state; }
    default void addLogListener(BiConsumer<Integer, String> listener) { }
    default void removeLogListener(BiConsumer<Integer, String> listener) { }
    default void attachTerminal(TerminalWidget terminal) { }
    default void detachTerminal() { }

    static ServerDetailsTarget unavailable(Object value) {
        return new ServerDetailsTarget() {
            @Override public Object value() { return value; }
            @Override public String id() { return ""; }
            @Override public String name() { return ""; }
        };
    }
}
