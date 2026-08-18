package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import redxax.oxy.remotely.util.DesktopAsyncTools;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.platform.Async;

import java.nio.file.Path;

public final class DesktopPlayerActionsFileProvider implements PlayerActionsFileProvider {
    private final Instance instance;
    private final Path directory;
    private final Path path;

    public DesktopPlayerActionsFileProvider(Instance instance) {
        this.instance = instance;
        directory = Path.of(instance.getPath(), "Remotely");
        path = directory.resolve("player-actions.json");
    }

    @Override
    public Async<String> read() {
        return ensureDirectory().thenCompose(ignored -> DesktopAsyncTools.adapt(RebaseApiFactory.get(instance).readFile(path)));
    }

    @Override
    public Async<Void> write(String content) {
        return ensureDirectory().thenCompose(ignored -> DesktopAsyncTools.adapt(RebaseApiFactory.get(instance).writeFile(path, content)));
    }

    @Override
    public void refresh() {
        PlayerManagerController.getOrCreate(instance).refreshPlayerActions();
    }

    private Async<Void> ensureDirectory() {
        return DesktopAsyncTools.<Boolean>adapt(RebaseApiFactory.get(instance).fileExists(directory))
                .thenCompose(exists -> exists ? Async.completed(null) : DesktopAsyncTools.adapt(RebaseApiFactory.get(instance).createDirectory(directory)));
    }
}
