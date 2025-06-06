package redxax.oxy.remotely.explorer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.api.RemotelyCoreAPI;
import redxax.oxy.remotely.api.LocalAPI;
import redxax.oxy.remotely.api.RemoteAPI;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.ui.ReScreen;
import redxax.oxy.remotely.ui.widgets.AnimatedWidget;
import redxax.oxy.remotely.ui.widgets.ContextMenuWidget;
import redxax.oxy.remotely.ui.widgets.FileEntryWidget;
import redxax.oxy.remotely.util.Notification;
import redxax.oxy.remotely.util.Sound;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;
import static redxax.oxy.remotely.util.SoundUtils.playSound;
import static redxax.oxy.remotely.util.searchUtils.isFuzzyMatch;

public class FileExplorerScreen extends ReScreen {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private final RemotelyCoreAPI fileAPI;
    private Path currentPath;
    private final List<Path> favoritePaths = new ArrayList<>();
    private final Object favoritePathsLock = new Object();
    private final List<FileEntryWidget> allWidgets = new CopyOnWriteArrayList<>();
    private final boolean importMode;
    private ContextMenuWidget itemsContextMenu;

    public static BufferedImage fileIcon;
    public static BufferedImage folderIcon;
    public static BufferedImage pinIcon;
    public static BufferedImage appsIcon, textIcon, shadersIcon, scriptIcon, javaIcon, pyIcon, minecraftIcon, jsonIcon, jsIcon, cssIcon, zipIcon, audioIcon, videoIcon, imageIcon, docxIcon, pdfIcon, pptxIcon, xlsxIcon;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".txt", ".md", ".json", ".yml", ".yaml", ".conf", ".properties",
            ".xml", ".cfg", ".sk", ".log", ".mcmeta", ".bat", ".sh", ".json5", ".jsonc",
            ".html", ".js", ".java", ".py", ".css", ".vsh", ".fsh", ".glsl", ".nu",
            ".bash", ".fish", ".toml", ".mcfunction", ".nbt"
    );

    public FileExplorerScreen(MinecraftClient mc, Screen parent, ServerInfo info) {
        this(mc, parent, info, false);
    }

    public FileExplorerScreen(MinecraftClient mc, Screen parent, ServerInfo info, boolean importMode) {
        super(Text.literal("File Explorer"));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
        this.importMode = importMode;

        if (serverInfo.isRemote) {
            this.fileAPI = new RemoteAPI(serverInfo.remoteHost);
            String normalized = serverInfo.path == null ? "" : serverInfo.path.replace("\\", "/").trim();
            if (normalized.isEmpty()) {
                normalized = "/";
            }
            if (!normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }
            this.currentPath = Paths.get(normalized);
        } else {
            this.fileAPI = new LocalAPI();
            this.currentPath = Paths.get(serverInfo.path).toAbsolutePath().normalize();
        }

        loadIcons();
    }

    @Override
    protected void init() {
        super.init();
        Container explorerContainer = createContainer("explorer", 5, 60, width - 10, height - 5);
        explorerContainer.columns(1).padding(2).layoutStyle(Container.LayoutStyle.RESTRICTED).enableSelecting(true);
        setActiveContainer(explorerContainer);

        SearchMode searchMode = new SearchMode(false);
        searchMode.setOnSearchEnter(this::onSearch);
        searchMode.setOnTextChange(this::onSearchTextChange);

        header().addLeft("/assets/remotely/icons/goback.png", this::navigateUp, "Go Back")
                .addLeft("/assets/remotely/icons/goforward.png", this::navigateBack, "Go Up")
                .addLeft("/assets/remotely/icons/newFile.png", this::createNewFile, "Create New")
                .addLeft("/assets/remotely/icons/external.png", this::openExternally, "Open Externally")
                .addRight("/assets/remotely/icons/close.png", () -> minecraftClient.setScreen(parent), "Close")
                .addRight("/assets/remotely/icons/paste.png", this::paste, "Paste")
                .addRight("/assets/remotely/icons/copy.png", this::copy, "Copy")
                .addRight("/assets/remotely/icons/favorite.png", this::toggleFavorites, "Toggle Favorites")
                .setSearchMode(searchMode, true)
                .build();
        tabs().builder().allowReorder(true).allowAdd(true).position(5, 36).size(width - 5, 18).onTabClosed(this::onTabClosed).onPlusButtonClicked(this::onNewTab).build();

        TabsManager.Tab initialTab = tabs().addTab(getTabName(currentPath), explorerContainer);
        tabs().setActiveTab(0);

        loadDirectory(currentPath);

        itemsContextMenu = new ContextMenuWidget.Builder(this)
                .addHeaderButton("copy.png", () -> {
                    playSound(Sound.COPY);
                    copy();
                }, "Copy Items")
                .addHeaderButton("cut.png", () -> {
                    playSound(Sound.COPY);
                    cut();
                }, "Cut Items")
                .addHeaderButton("paste.png", () -> {
                    playSound(Sound.PASTE);
                    paste();
                }, "Paste Items")
                .addHeaderButton("favorite.png", () -> {
                    playSound(Sound.CLICK);
                    toggleFavorites();
                }, "Favorite Items")
                .addHeaderButton("edit.png", () -> {
                    playSound(Sound.CLICK);
                    renameSelected();
                }, "Rename Items")
                .addHeaderButton("delete.png", () -> {
                    playSound(Sound.DELETE);
                    deleteSelected();
                }, "Delete Items")
                .addIconItem("Open In New Tab", "newTab.png", () -> {
                    for (AnimatedWidget widget : currentSelectedWidgets) {
                        widget.onClick(0, 0, 2);
                    }
                }, "")
                .addIconItem("Open Externally", "external.png", () -> {
                    playSound(Sound.CLICK);
                    for (AnimatedWidget widget : currentSelectedWidgets) {
                         openExternally(((FileEntryWidget) widget).getFileEntry().path);
                    }
                }, "")
                .addIconItem("Create File", "newFile.png", () -> {
                    playSound(Sound.CREATE);
                    createNewFile();
                }, "")
                .addIconItem("Copy Path", "snippets.png", () -> {
                    FileEntryWidget firstWidget = (FileEntryWidget) currentSelectedWidgets.getFirst();
                    minecraftClient.keyboard.setClipboard(firstWidget.getFileEntry().path.toString());
                }, "")
                .addIconItem("Undo", "goback.png", this::undo, "")
                .addIconItem("Refresh", "reload.png", () -> {
                    playSound(Sound.CLICK);
                    loadDirectory(currentPath);
                }, "").build();
        addDrawableChild(itemsContextMenu);
    }

    private void loadIcons() {
        try {
            fileIcon = loadResourceIcon("/assets/remotely/icons/file.png");
            folderIcon = loadResourceIcon("/assets/remotely/icons/folder.png");
            pinIcon = loadResourceIcon("/assets/remotely/icons/pin.png");
            appsIcon = loadResourceIcon("/assets/remotely/icons/apps.png");
            cssIcon = loadResourceIcon("/assets/remotely/icons/css.png");
            jsIcon = loadResourceIcon("/assets/remotely/icons/js.png");
            jsonIcon = loadResourceIcon("/assets/remotely/icons/json.png");
            minecraftIcon = loadResourceIcon("/assets/remotely/icons/minecraft.png");
            pyIcon = loadResourceIcon("/assets/remotely/icons/py.png");
            javaIcon = loadResourceIcon("/assets/remotely/icons/java.png");
            scriptIcon = loadResourceIcon("/assets/remotely/icons/script.png");
            shadersIcon = loadResourceIcon("/assets/remotely/icons/shaders.png");
            textIcon = loadResourceIcon("/assets/remotely/icons/text.png");
            zipIcon = loadResourceIcon("/assets/remotely/icons/zip.png");
            audioIcon = loadResourceIcon("/assets/remotely/icons/audio.png");
            videoIcon = loadResourceIcon("/assets/remotely/icons/video.png");
            imageIcon = loadResourceIcon("/assets/remotely/icons/image.png");
            docxIcon = loadResourceIcon("/assets/remotely/icons/docx.png");
            pdfIcon = loadResourceIcon("/assets/remotely/icons/pdf.png");
            pptxIcon = loadResourceIcon("/assets/remotely/icons/pptx.png");
            xlsxIcon = loadResourceIcon("/assets/remotely/icons/xlsx.png");
        } catch (Exception ignored) {}
    }

    private void loadDirectory(Path path) {
        loading = true;
        currentPath = path;
        activeContainer.clearWidgets();
        allWidgets.clear();
        currentSelectedWidgets.clear();

        fileAPI.listDirectory(path).thenAccept(entries -> {
            minecraftClient.execute(() -> {
                for (RemotelyCoreAPI.FileEntry entry : entries) {
                    FileEntryWidget widget = new FileEntryWidget.Builder(entry, fileAPI, serverInfo.isRemote, favoritePaths, favoritePathsLock).size(0, 20)
                            .onClick(this::onFileDoubleClick).onRightClick(this::onFileRightClick).build();

                    activeContainer.addWidget(widget);
                    allWidgets.add(widget);
                }
                loading = false;
            });
        }).exceptionally(e -> {
            minecraftClient.execute(() -> {
                loading = false;
                new Notification("Failed to load directory: ", e.getMessage(), Notification.Type.ERROR);
            });
            return null;
        });
    }

    private void onFileDoubleClick(FileEntryWidget widget) {
        RemotelyCoreAPI.FileEntry entry = widget.getFileEntry();
        if (entry.isDirectory) {
            playSound(Sound.CLICK);
            navigateTo(entry.path);
        } else {
            if (importMode && entry.path.getFileName().toString().equalsIgnoreCase("server.jar")) {
                minecraftClient.setScreen(parent);
                return;
            }
            if (isSupportedFile(entry.path)) {
                minecraftClient.setScreen(new FileEditorScreen(minecraftClient, this, entry.path, serverInfo));
            } else {
                openExternally(entry.path);
            }

        }
    }

    private void onFileRightClick(FileEntryWidget widget) {

    }


    private void onTabClosed(TabsManager.Tab tab) {
        if (tabs().getTabs().isEmpty()) {
            minecraftClient.setScreen(parent);
        }
    }

    public void onNewTab() {
        Path homePath = serverInfo.isRemote ? Paths.get("/") : Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        createTab(homePath);
    }

    public void createTab(Path newPath) {
        Container newContainer = createContainer(5, 60, width - 10, height - 5);
        newContainer.columns(1).padding(2).enableSelecting(true).layoutStyle(Container.LayoutStyle.RESTRICTED);
        TabsManager.Tab newTab = tabs().addTab(getTabName(newPath), newContainer);
        tabs().setActiveTab(tabs().getTabs().indexOf(newTab));
        loadDirectory(newPath);
    }

    private void onSearch(String query) {
        filterEntries(query);
    }

    private void onSearchTextChange(String query) {
        if (header().liveUpdate) {
            filterEntries(query);
        }
    }

    private void filterEntries(String query) {
        if (query.trim().isEmpty()) {
            for (FileEntryWidget widget : allWidgets) {
                widget.setMatched(false);
                widget.visible = true;
            }
        } else {
            for (FileEntryWidget widget : allWidgets) {
                String filename = widget.getFileEntry().displayName.toLowerCase();
                boolean matches = filename.contains(query.toLowerCase()) || isFuzzyMatch(filename, query.toLowerCase());
                widget.setMatched(matches);
                widget.visible = matches;
            }
        }
        activeContainer.updateWidgetPositions();
    }

    private void navigateTo(Path path) {
        loadDirectory(path);
        TabsManager.Tab activeTab = tabs().getActiveTab();
        if (activeTab != null) {
            activeTab.setName(getTabName(path));
        }
    }

    private void navigateUp() {
        Path parentPath = currentPath.getParent();
        if (parentPath != null) {
            navigateTo(parentPath);
        } else {
            minecraftClient.setScreen(parent);
        }
    }

    private void navigateBack() {
        // Implement history navigation
    }

    private void createNewFile() {
        // add a new entry and set it to be focused and renaming
    }

    private void openExternally() {
        openExternally(currentPath);
    }

    public void openExternally(Path path) {
        if (serverInfo.isRemote) {
            new Notification("Not Allowed On Remote Explorer.", Notification.Type.WARN);
            return;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("explorer.exe", path.toString());
            processBuilder.start();
        } catch (Exception e) {
            new Notification("Failed To Open Externally.", e.getMessage(), Notification.Type.ERROR);
        }
    }

    private void paste() {
        fileAPI.paste(currentPath).thenRun(() -> {
            new Notification("Files Pasted! ", "(" + currentSelectedWidgets.size() + ") Items Pasted.", Notification.Type.SUCCESS);
            loadDirectory(currentPath);
        }).exceptionally(e -> {
            new Notification("Failed To Paste.", e.getMessage(), Notification.Type.ERROR);
            return null;
        });
    }

    private void cut() {
        if (!currentSelectedWidgets.isEmpty()) {
            List<Path> paths = currentSelectedWidgets.stream().map(w -> ((FileEntryWidget) w).getFileEntry().path).toList();
            fileAPI.cut(paths, null);
            new Notification("Files Cut!", "(" + currentSelectedWidgets.size() + ") Items Cut.", Notification.Type.SUCCESS);
        }
    }

    private void copy() {
        if (!currentSelectedWidgets.isEmpty()) {
            List<Path> paths = currentSelectedWidgets.stream().map(w -> ((FileEntryWidget) w).getFileEntry().path).toList();
            fileAPI.copy(paths, null);
            new Notification("Files Copied!", "(" + currentSelectedWidgets.size() + ") Items Copied.", Notification.Type.SUCCESS);
        }
    }

    private void toggleFavorites() {
        for (AnimatedWidget widget : currentSelectedWidgets) {
            ((FileEntryWidget) widget).toggleFavorite();
        }
    }

    private void updateButtonStates() {
        boolean hasSelection = !currentSelectedWidgets.isEmpty();
        // Update button visibility based on selection
    }

    private String getTabName(Path path) {
        return path.getFileName() != null ? path.getFileName().toString() : path.toString();
    }

    public static BufferedImage getIconForFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".exe")) return appsIcon;
        if (fileName.endsWith(".css")) return cssIcon;
        if (fileName.endsWith(".js")) return jsIcon;
        if (fileName.endsWith(".json")) return jsonIcon;
        if (fileName.endsWith(".mcfunction") || fileName.endsWith(".mcmeta")) return minecraftIcon;
        if (fileName.endsWith(".py")) return pyIcon;
        if (fileName.endsWith(".java") || fileName.endsWith(".class") || fileName.endsWith(".jar")) return javaIcon;
        if (fileName.endsWith(".bat") || fileName.endsWith(".sh") || fileName.endsWith(".bash") || fileName.endsWith(".sk")) return scriptIcon;
        if (fileName.endsWith(".vsh") || fileName.endsWith(".fsh") || fileName.endsWith(".glsl")) return shadersIcon;
        if (fileName.endsWith(".zip") || fileName.endsWith(".rar") || fileName.endsWith(".tar") || fileName.endsWith(".gz") || fileName.endsWith(".7z")) return zipIcon;
        if (fileName.endsWith(".mp3") || fileName.endsWith(".wav") || fileName.endsWith(".flac") || fileName.endsWith(".aac") || fileName.endsWith(".ogg")) return audioIcon;
        if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") || fileName.endsWith(".mkv") || fileName.endsWith(".mov") || fileName.endsWith(".flv")) return videoIcon;
        if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".webp") || fileName.endsWith(".gif")) return imageIcon;
        if (fileName.endsWith(".docx") || fileName.endsWith(".doc")) return docxIcon;
        if (fileName.endsWith(".pdf")) return pdfIcon;
        if (fileName.endsWith(".pptx") || fileName.endsWith(".ppt")) return pptxIcon;
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) return xlsxIcon;
        if (fileName.endsWith(".txt") || fileName.endsWith(".yml") || fileName.endsWith(".yaml") || fileName.endsWith(".properties") ||
                fileName.endsWith(".toml") || fileName.endsWith(".md") || fileName.endsWith(".log") || fileName.endsWith(".html")) return textIcon;
        return fileIcon;
    }

    public static boolean isSupportedFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (itemsContextMenu.isOpen() && itemsContextMenu.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 1) {
            itemsContextMenu.show((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_X -> { cut(); return true; }
                case GLFW.GLFW_KEY_C -> { copy(); return true; }
                case GLFW.GLFW_KEY_V -> { paste(); return true; }
                case GLFW.GLFW_KEY_R -> { loadDirectory(currentPath); return true; }
                case GLFW.GLFW_KEY_N -> { createNewFile(); return true; }
                case GLFW.GLFW_KEY_Z -> { undo(); return true; }
            }
        }

        if (hasAltDown()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_UP -> {
                    activeContainer.columnsGlobally(activeContainer.getColumns() + 1);
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    if (activeContainer.getColumns() > 1) {
                        activeContainer.columnsGlobally(activeContainer.getColumns() - 1);
                    }
                    return true;
                }
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {

        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            deleteSelected();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F2) {
            renameSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void deleteSelected() {
        if (!currentSelectedWidgets.isEmpty()) {
            List<Path> paths = currentSelectedWidgets.stream().map(w -> ((FileEntryWidget) w).getFileEntry().path).toList();
            fileAPI.delete(paths).thenRun(() -> {
                new Notification("Files Deleted!", "(" + currentSelectedWidgets.size() + ") Items Deleted.", Notification.Type.SUCCESS);
                loadDirectory(currentPath);
            }).exceptionally(e -> {
                new Notification("Failed To Delete: ", e.getMessage(), Notification.Type.ERROR);
                return null;
            });
        }
    }

    private void renameSelected() {
        if (currentSelectedWidgets.size() == 1) {
            ((FileEntryWidget) currentSelectedWidgets.getFirst()).startRename();
        }
    }

    private void undo() {
        if (fileAPI.canUndo()) {
            fileAPI.undo().thenRun(() -> {
                new Notification("Undo Successful!", Notification.Type.SUCCESS);
                loadDirectory(currentPath);
            }).exceptionally(e -> {
                new Notification("Undo Failed.", e.getMessage(), Notification.Type.ERROR);
                return null;
            });
        }
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.FILEEXPLORER);
    }
}