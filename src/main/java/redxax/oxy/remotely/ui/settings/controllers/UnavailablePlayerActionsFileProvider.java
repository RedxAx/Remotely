package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.platform.Async;

public final class UnavailablePlayerActionsFileProvider implements PlayerActionsFileProvider {
    private final String reason;

    public UnavailablePlayerActionsFileProvider(String reason) {
        this.reason = reason;
    }

    @Override public boolean available() { return false; }
    @Override public String reason() { return reason; }
    @Override public Async<String> read() { return Async.failed(new IllegalStateException(reason)); }
    @Override public Async<Void> write(String content) { return Async.failed(new IllegalStateException(reason)); }
}
