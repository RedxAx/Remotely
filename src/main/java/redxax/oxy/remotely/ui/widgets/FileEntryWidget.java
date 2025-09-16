package redxax.oxy.remotely.ui.widgets;

import redxax.oxy.remotely.api.RemotelyAPI;
import redxax.oxy.remotely.explorer.FileExplorerScreen;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.util.Notification;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static restudio.rescreen.config.Config.globalTextColor;
import static redxax.oxy.remotely.RemotelyClient.tr;

public class FileEntryWidget extends AnimatedWidget {
    private final RemotelyAPI.FileEntry fileEntry;
    private final RemotelyAPI fileAPI;
    private final boolean isRemote;
    private final List<Path> favoritePaths;
    private final Object favoritePathsLock;
    private Consumer<FileEntryWidget> onDoubleClick;
    private Consumer<FileEntryWidget> onRightClick;
    private Consumer<FileEntryWidget> onSelectionChanged;
    private Consumer<List<FileEntryWidget>> onMultiSelect;
    private boolean isRenaming = false;
    private StringBuilder renameBuffer = new StringBuilder();
    private int renameCursorPos = 0;
    private boolean isMatched = false;
    private long lastClickTime = 0;
    private static final int DOUBLE_CLICK_INTERVAL = 500;

    public static class Builder extends AnimatedWidget.Builder<FileEntryWidget, Builder> {
        public Builder(RemotelyAPI.FileEntry entry, RemotelyAPI api, boolean remote, List<Path> favorites, Object lock) {
            super(new FileEntryWidget(0, 0, 100, 20, entry, api, remote, favorites, lock));
        }
        public Builder onClick(Consumer<FileEntryWidget> callback) { widget.onDoubleClick = callback; return this; }
        public Builder onRightClick(Consumer<FileEntryWidget> callback) { widget.onRightClick = callback; return this; }
        public Builder onSelectionChanged(Consumer<FileEntryWidget> callback) { widget.onSelectionChanged = callback; return this; }
        public Builder onMultiSelect(Consumer<List<FileEntryWidget>> callback) { widget.onMultiSelect = callback; return this; }
        @Override protected Builder self() { return this; }
    }

    public FileEntryWidget(int x, int y, int width, int height, RemotelyAPI.FileEntry entry, RemotelyAPI api, boolean remote, List<Path> favorites, Object lock) {
        super(x, y, width, height, (entry.displayName));
        this.fileEntry = entry;
        this.fileAPI = api;
        this.isRemote = remote;
        this.favoritePaths = favorites;
        this.favoritePathsLock = lock;
        this.renameBuffer.append(entry.displayName);
        this.renameCursorPos = renameBuffer.length();
        this.hintDelay = .7f;
        this.selectable = true;
    }

    @Override
    protected void drawContent(IDrawContext context, int mouseX, int mouseY) {
        boolean isFavorite;
        synchronized (favoritePathsLock) {
            isFavorite = favoritePaths.contains(fileEntry.path);
            accentType = isFavorite ? Config.AccentType.DANGER : Config.AccentType.DEFAULT;
        }
        BufferedImage icon = fileEntry.isDirectory ? FileExplorerScreen.folderIcon : FileExplorerScreen.getIconForFile(fileEntry.path);
        context.drawPixelArt(icon, getX() + 8, getY() + 2, 16, 16);
        if (isFavorite) {
            context.drawPixelArt(FileExplorerScreen.pinIcon, fileEntry.isDirectory ? getX() + 3 : getX() + 5, getY() + 2, 16, 16);
        }
        if (!isRemote) {
            int createdX = getX() + getWidth() - tr.getWidth(fileEntry.created) - 2;
            int sizeX = createdX - tr.getWidth(fileEntry.size) - 8;
            context.drawText((fileEntry.displayName), getX() + 28, getY() + 6, globalTextColor, Config.shadow);
            context.drawText((fileEntry.created), createdX, getY() + 6, globalTextColor, Config.shadow);
            context.drawText((fileEntry.size), sizeX, getY() + 6, globalTextColor, Config.shadow);
        } else {
            context.drawText((fileEntry.displayName), getX() + 30, getY() + 5, globalTextColor, Config.shadow);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            onDoubleClick.accept(this);
        } else if (button == 1 && onRightClick != null) {
            onRightClick.accept(this);
        } else if (button == 2) {
            Screen currentScreen = ScreenManager.currentScreen;
            if (currentScreen instanceof FileExplorerScreen explorer) {
                if (fileEntry.isDirectory) {
                    explorer.createTab(fileEntry.path, true);
                }
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isRenaming) {
            if (keyCode == 257) {
                finishRename();
                return true;
            } else if (keyCode == 256) {
                cancelRename();
                return true;
            } else if (keyCode == 259) {
                if (renameCursorPos > 0) {
                    renameBuffer.deleteCharAt(renameCursorPos - 1);
                    renameCursorPos--;
                }
                return true;
            } else if (keyCode == 261) {
                if (renameCursorPos < renameBuffer.length()) {
                    renameBuffer.deleteCharAt(renameCursorPos);
                }
                return true;
            } else if (keyCode == 263) {
                if (renameCursorPos > 0) {
                    renameCursorPos--;
                }
                return true;
            } else if (keyCode == 262) {
                if (renameCursorPos < renameBuffer.length()) {
                    renameCursorPos++;
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (isRenaming && chr != '\b' && chr != '\r' && chr != '\n') {
            renameBuffer.insert(renameCursorPos, chr);
            renameCursorPos++;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    public void startRename() {
        isRenaming = true;
        renameBuffer.setLength(0);
        renameBuffer.append(fileEntry.displayName);
        renameCursorPos = renameBuffer.length();
        setSelected(true);
    }

    public void finishRename() {
        if (isRenaming && !renameBuffer.toString().trim().isEmpty()) {
            String newName = renameBuffer.toString().trim();
            Path newPath = fileEntry.path.getParent().resolve(newName);
            fileAPI.rename(fileEntry.path, newPath).thenRun(() -> ScreenManager.getInstance().execute(() -> {
                fileEntry.displayName = newName;
                setMessage((newName));
            })).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> new Notification("Rename failed: " + e.getCause().getMessage(), Notification.Type.ERROR));
                return null;
            });
        }
        isRenaming = false;
    }

    public void cancelRename() {
        isRenaming = false;
        renameBuffer.setLength(0);
        renameBuffer.append(fileEntry.displayName);
        renameCursorPos = renameBuffer.length();
    }

    public void toggleFavorite() {
        synchronized (favoritePathsLock) {
            if (favoritePaths.contains(fileEntry.path)) {
                favoritePaths.remove(fileEntry.path);
            } else {
                favoritePaths.add(fileEntry.path);
            }
        }
    }

    public RemotelyAPI.FileEntry getFileEntry() {
        return fileEntry;
    }

    public boolean isRenaming() {
        return isRenaming;
    }

    public void setMatched(boolean matched) {
        this.isMatched = matched;
    }

    public boolean isMatched() {
        return isMatched;
    }
}