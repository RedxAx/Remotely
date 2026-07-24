package redxax.oxy.remotely.host;

import restudio.rescreen.platform.HostActionHandler;
import restudio.rescreen.platform.desktop.DesktopHostActionHandler;

import java.nio.file.Path;
import java.util.List;

public final class MinecraftHostActionHandler implements HostActionHandler {
    private final DesktopHostActionHandler desktop = new DesktopHostActionHandler();

    @Override
    public boolean openBrowser(String url) {
        return desktop.openBrowser(url);
    }

    @Override
    public boolean trashFiles(List<Path> paths, boolean permanentDelete) {
        return desktop.trashFiles(paths, permanentDelete);
    }
}
