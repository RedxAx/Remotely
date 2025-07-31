package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import redxax.oxy.remotely.api.RemotelyCoreAPI;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.explorer.FileEditorScreen;
import redxax.oxy.remotely.explorer.FileExplorerScreen;
import redxax.oxy.remotely.util.Notification;
import redxax.oxy.remotely.util.Sound;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.ui.widgets.AnimatedWidget.EntranceCorner.TOP_LEFT;
import static redxax.oxy.remotely.util.ImageUtil.drawPixelArt;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

public class FileEntryWidget extends AnimatedWidget {
    private final RemotelyCoreAPI.FileEntry fileEntry;
    private final RemotelyCoreAPI fileAPI;
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
        public Builder(RemotelyCoreAPI.FileEntry entry, RemotelyCoreAPI api, boolean remote, List<Path> favorites, Object lock) {
            super(new FileEntryWidget(0, 0, 100, 20, entry, api, remote, favorites, lock));
        }
        public Builder onClick(Consumer<FileEntryWidget> callback) { widget.onDoubleClick = callback; return this; }
        public Builder onRightClick(Consumer<FileEntryWidget> callback) { widget.onRightClick = callback; return this; }
        public Builder onSelectionChanged(Consumer<FileEntryWidget> callback) { widget.onSelectionChanged = callback; return this; }
        public Builder onMultiSelect(Consumer<List<FileEntryWidget>> callback) { widget.onMultiSelect = callback; return this; }
        @Override protected Builder self() { return this; }
    }

    public FileEntryWidget(int x, int y, int width, int height, RemotelyCoreAPI.FileEntry entry, RemotelyCoreAPI api, boolean remote, List<Path> favorites, Object lock) {
        super(x, y, width, height, Text.literal(entry.displayName));
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
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
    }

    @Override
    protected void drawContent(DrawContext context, int mouseX, int mouseY) {
        boolean isFavorite;
        synchronized (favoritePathsLock) {
            isFavorite = favoritePaths.contains(fileEntry.path);
            accentType = isFavorite ? AccentType.DANGER : AccentType.DEFAULT;
        }
        BufferedImage icon = fileEntry.isDirectory ? FileExplorerScreen.folderIcon : FileExplorerScreen.getIconForFile(fileEntry.path);
        drawPixelArt(context, getX() + 8, getY() + 2, 16, 16, icon);
        if (isFavorite) {
            drawPixelArt(context, fileEntry.isDirectory ? getX() + 3 : getX() + 5, getY() + 2, 16, 16, FileExplorerScreen.pinIcon);
        }
        if (!isRemote) {
            int createdX = getX() + getWidth() - tr.getWidth(fileEntry.created) - 2;
            int sizeX = createdX - tr.getWidth(fileEntry.size) - 8;
            context.drawText(tr, Text.literal(fileEntry.displayName), getX() + 28, getY() + 6, globalTextColor, Config.shadow);
            context.drawText(tr, Text.literal(fileEntry.created), createdX, getY() + 6, globalTextColor, Config.shadow);
            context.drawText(tr, Text.literal(fileEntry.size), sizeX, getY() + 6, globalTextColor, Config.shadow);
        } else {
            context.drawText(tr, Text.literal(fileEntry.displayName), getX() + 30, getY() + 5, globalTextColor, Config.shadow);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            onDoubleClick.accept(this);
        } else if (button == 1 && onRightClick != null) {
            onRightClick.accept(this);
        } else if (button == 2) {
            Screen currentScreen = MinecraftClient.getInstance().currentScreen;
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
            fileAPI.rename(fileEntry.path, newPath).thenRun(() -> {
                MinecraftClient.getInstance().execute(() -> {
                    fileEntry.displayName = newName;
                    setMessage(Text.literal(newName));
                });
            }).exceptionally(e -> {
                MinecraftClient.getInstance().execute(() -> new Notification("Rename failed: " + e.getMessage(), Notification.Type.ERROR));
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

    public RemotelyCoreAPI.FileEntry getFileEntry() {
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