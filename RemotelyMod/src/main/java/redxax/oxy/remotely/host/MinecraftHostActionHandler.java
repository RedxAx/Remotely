package redxax.oxy.remotely.host;

import restudio.rescreen.platform.desktop.DesktopHostActionHandler;
import restudio.rescreen.platform.desktop.DesktopFileActionHandler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class MinecraftHostActionHandler implements DesktopFileActionHandler {
    private final DesktopHostActionHandler desktop = new DesktopHostActionHandler();

    @Override
    public boolean openBrowser(String url) {
        return desktop.openBrowser(url);
    }

    @Override
    public boolean trashFiles(List<Path> paths, boolean permanentDelete) {
        return desktop.trashFiles(paths, permanentDelete);
    }

    @Override
    public void openAssociated(Path path) throws IOException {
        desktop.openAssociated(path);
    }

    @Override
    public void copyFilesToClipboard(List<Path> paths) {
        desktop.copyFilesToClipboard(paths);
    }

    @Override
    public void pickImageFileAsync(String title, Consumer<Path> onSelected) {
        desktop.pickImageFileAsync(title, onSelected);
    }

    @Override
    public boolean filePickerAvailable() {
        return desktop.filePickerAvailable();
    }

    @Override
    public void pickFilesAsync(boolean multiple, Consumer<List<Path>> onSelected) {
        desktop.pickFilesAsync(multiple, onSelected);
    }
}
