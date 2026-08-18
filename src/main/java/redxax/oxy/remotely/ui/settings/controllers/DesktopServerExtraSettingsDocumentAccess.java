package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.util.DesktopAsyncTools;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.platform.Async;

import java.nio.file.Path;

public final class DesktopServerExtraSettingsDocumentAccess implements ServerExtraSettingsController.DocumentAccess {
    private final Instance instance;

    public DesktopServerExtraSettingsDocumentAccess(Instance instance) {
        this.instance = instance;
    }

    @Override public Async<String> read(String relativePath) {
        return DesktopAsyncTools.adapt(RebaseApiFactory.get(instance).readFile(path(relativePath)));
    }

    @Override public Async<Void> write(String relativePath, String content) {
        return DesktopAsyncTools.adapt(RebaseApiFactory.get(instance).writeFile(path(relativePath), content));
    }

    private Path path(String relativePath) {
        return Path.of(instance.getPath()).resolve(relativePath);
    }
}
