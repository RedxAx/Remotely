package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.ui.settings.controllers.PlayerActionsFileProvider;
import restudio.rescreen.platform.Async;

public final class BrowserPlayerActionsFileProvider implements PlayerActionsFileProvider {
    private static final String PATH = "Remotely/player-actions.json";

    private final BrowserRemotelyServerApi api;
    private final String serverId;

    public BrowserPlayerActionsFileProvider(BrowserRemotelyServerApi api, String serverId) {
        this.api = api;
        this.serverId = serverId;
    }

    @Override
    public Async<String> read() {
        return api.getFileContentAllowMissing(serverId, PATH).thenApply(content -> content == null ? "" : content);
    }

    @Override
    public Async<Void> write(String content) {
        return api.writeFile(serverId, PATH, content);
    }
}
