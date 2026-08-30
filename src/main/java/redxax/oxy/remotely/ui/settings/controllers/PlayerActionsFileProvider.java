package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rescreen.platform.Async;

public interface PlayerActionsFileProvider {
    default boolean available() {
        return true;
    }

    default String reason() {
        return "";
    }

    Async<String> read();

    Async<Void> write(String content);

    default void refresh() {
    }
}
