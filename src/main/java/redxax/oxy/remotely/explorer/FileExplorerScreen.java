package redxax.oxy.remotely.explorer;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.api.RemotelyAPI;
import redxax.oxy.remotely.api.RemotelyApiFactory;
import redxax.oxy.remotely.servers.RemoteHostInfo;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.ui.widgets.FileEntryWidget;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.RestrictedLayout;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.ContextMenuWidget;
import restudio.rescreen.util.FileUtils;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;
import static restudio.rescreen.util.SearchUtils.isFuzzyMatch;
import static restudio.rescreen.util.SoundUtils.playSound;

public class FileExplorerScreen extends ReScreen {
    private final Screen parent;
    private final ScreenManager client = ScreenManager.getInstance();
    private final ServerInfo serverInfo;
    private final boolean isRemote;
    private final RemotelyAPI fileAPI;
    private Path currentPath;
    private final List<Path> favoritePaths = new ArrayList<>();
    private final Object favoritePathsLock = new Object();
    private final List<FileEntryWidget> allWidgets = new CopyOnWriteArrayList<>();
    private final boolean importMode;
    private ContextMenuWidget itemsContextMenu;
    private final Map<Container, Path> containerPaths = new HashMap<>();
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

    public FileExplorerScreen(Screen parent, Path path) {
        this(parent, new ServerInfo(path.toString()), false);
    }

    public FileExplorerScreen(Screen parent, ServerInfo info) {
        this(parent, info, false);
    }

    public FileExplorerScreen(Screen parent, RemoteHostInfo host) {
        this(parent, new ServerInfo(true, host, host.getHomeDirectory()), false);
    }

    public FileExplorerScreen(Screen parent, ServerInfo info, boolean importMode) {
        this.parent = parent;
        this.serverInfo = info;
        this.importMode = importMode;
        this.isRemote = info.isRemote;
        this.fileAPI = RemotelyApiFactory.get(info);

        if (this.isRemote) {
            String homeDir = info.remoteHost.getHomeDirectory();
            String normalized = info.path == null ? "" : info.path.replace("\\", "/").trim();
            if (normalized.isEmpty() || normalized.equals("/")) {
                this.currentPath = Paths.get(homeDir);
            } else {
                if (!normalized.startsWith("/")) normalized = "/" + normalized;
                this.currentPath = Paths.get(normalized);
            }
        } else {
            if (info.path == null) {
                throw new IllegalArgumentException("Path cannot be null for local file explorer.");
            }
            this.currentPath = Paths.get(info.path).toAbsolutePath().normalize();
        }

        loadIcons();
    }

    @Override
    public void init() {
        super.init();

        Container explorerContainer = createContainer("explorer", 5, 60, width - 10, height - 5);
        explorerContainer.columns(1).padding(2).layout(new RestrictedLayout()).enableSelecting(true);
        setActiveContainer(explorerContainer);

        SearchMode searchMode = new SearchMode(false);
        searchMode.setOnSearchEnter(this::onSearch);
        searchMode.setOnTextChange(this::onSearchTextChange);

        header().addLeft("goback.png", this::navigateUp, "Go Back")
                .addLeft("goforward.png", this::navigateBack, "Go Up")
                .addLeft("newFile.png", this::createNewFile, "Create New")
                .addLeft("external.png", this::openExternally, "Open Externally")
                .addRight("close.png", () -> client.setScreen(parent), "Close")
                .addRight("paste.png", this::paste, "Paste")
                .addRight("copy.png", this::copy, "Copy")
                .addRight("favorite.png", this::toggleFavorites, "Toggle Favorites")
                .setSearchMode(searchMode, true)
                .build();
        tabs().builder().allowReorder(true).allowAdd(true).position(5, 36).size(width - 5, 18).onTabClosed(this::onTabClosed).onPlusButtonClicked(() -> this.client.setScreen(new DeskSelectionScreen(this))).onTabsReordered(this::onTabReordered).onTabSelected(this::onTabSelected).build();

        loadExplorerTabs();

        if (tabs().getTabs().isEmpty()) {
            tabs().addTab(getTabName(currentPath), explorerContainer);
            containerPaths.put(explorerContainer, currentPath);
            tabs().setActiveTab(0);
        }

        itemsContextMenu = new ContextMenuWidget.Builder(this)
                .addHeaderButton("copy.png", () -> { playSound(Sound.COPY); copy(); }, "Copy Items")
                .addHeaderButton("cut.png", () -> { playSound(Sound.COPY); cut(); }, "Cut Items")
                .addHeaderButton("paste.png", () -> { playSound(Sound.PASTE); paste(); }, "Paste Items")
                .addHeaderButton("favorite.png", () -> { playSound(Sound.CLICK); toggleFavorites(); }, "Favorite Items")
                .addHeaderButton("edit.png", () -> { playSound(Sound.CLICK); renameSelected(); }, "Rename Items")
                .addHeaderButton("delete.png", () -> { playSound(Sound.DELETE); deleteSelected(); }, "Delete Items")
                .addIconItem("Open In New Tab", "newTab.png", () -> {
                    for (AnimatedWidget widget : currentSelectedWidgets) widget.onClick(0, 0, 2);
                }, "")
                .addIconItem("Open Externally", "external.png", () -> {
                    playSound(Sound.CLICK);
                    for (AnimatedWidget widget : currentSelectedWidgets) {
                        openExternally(((FileEntryWidget) widget).getFileEntry().path);
                    }
                }, "")
                .addIconItem("Create File", "newFile.png", () -> { playSound(Sound.CREATE); createNewFile(); }, "")
                .addIconItem("Copy Path", "snippets.png", () -> {
                    FileEntryWidget firstWidget = (FileEntryWidget) currentSelectedWidgets.getFirst();
                    FileUtils.setClipboard(firstWidget.getFileEntry().path.toString());
                }, "")
                .addIconItem("Undo", "goback.png", this::undo, "")
                .addIconItem("Refresh", "reload.png", () -> { playSound(Sound.CLICK); loadDirectory(currentPath); }, "")
                .build();
        addDrawableChild(itemsContextMenu);
    }

    private void loadIcons() {
        try {
            fileIcon = loadResourceIcon("file.png");
            folderIcon = loadResourceIcon("folder.png");
            pinIcon = loadResourceIcon("pin.png");
            appsIcon = loadResourceIcon("apps.png");
            cssIcon = loadResourceIcon("css.png");
            jsIcon = loadResourceIcon("js.png");
            jsonIcon = loadResourceIcon("json.png");
            minecraftIcon = loadResourceIcon("minecraft.png");
            pyIcon = loadResourceIcon("py.png");
            javaIcon = loadResourceIcon("java.png");
            scriptIcon = loadResourceIcon("script.png");
            shadersIcon = loadResourceIcon("shaders.png");
            textIcon = loadResourceIcon("text.png");
            zipIcon = loadResourceIcon("zip.png");
            audioIcon = loadResourceIcon("audio.png");
            videoIcon = loadResourceIcon("video.png");
            imageIcon = loadResourceIcon("image.png");
            docxIcon = loadResourceIcon("docx.png");
            pdfIcon = loadResourceIcon("pdf.png");
            pptxIcon = loadResourceIcon("pptx.png");
            xlsxIcon = loadResourceIcon("xlsx.png");
        } catch (Exception ignored) {}
    }

    public void loadDirectory(Path path) {
        loading = true;
        final Container targetContainer = activeContainer;
        currentPath = path;
        containerPaths.put(targetContainer, currentPath);
        targetContainer.clearWidgets();
        if (targetContainer == activeContainer) {
            allWidgets.clear();
            currentSelectedWidgets.clear();
        }

        fileAPI.listDirectory(path).thenAccept(entries -> client.execute(() -> {
            for (RemotelyAPI.FileEntry entry : entries) {
                FileEntryWidget widget = new FileEntryWidget.Builder(entry, fileAPI, isRemote, favoritePaths, favoritePathsLock)
                        .size(0, 20)
                        .onClick(this::onFileDoubleClick)
                        .onRightClick(this::onFileRightClick)
                        .build();
                targetContainer.addWidget(widget);
                if (targetContainer == activeContainer) allWidgets.add(widget);
            }
            if (targetContainer == activeContainer) loading = false;
        })).exceptionally(e -> {
            client.execute(() -> {
                if (targetContainer == activeContainer) loading = false;
                new Notification("Failed to load directory: ", e.getMessage(), Notification.Type.ERROR);
            });
            return null;
        });
    }

    private void onFileDoubleClick(FileEntryWidget widget) {
        RemotelyAPI.FileEntry entry = widget.getFileEntry();
        if (entry.isDirectory) {
            playSound(Sound.CLICK);
            navigateTo(entry.path);
        } else {
            if (importMode && entry.path.getFileName().toString().equalsIgnoreCase("server.jar")) {
                client.setScreen(parent);
                return;
            }
            if (isSupportedFile(entry.path)) {
                client.setScreen(new FileEditorScreen(this, entry.path, this.serverInfo));
            } else {
                openExternally(entry.path);
            }
        }
    }

    private void onFileRightClick(FileEntryWidget widget) {
    }

    private void onTabClosed(TabsManager.Tab tab) {
        if (tabs().getTabs().isEmpty()) client.setScreen(parent);
        saveExplorerTabs();
    }

    private void onTabReordered(List<TabsManager.Tab> tabs) {
        saveExplorerTabs();
    }

    private void onTabSelected(TabsManager.Tab tab) {
        if (tab.getContainer().getWidgets().isEmpty()) {
            Path path = containerPaths.get(tab.getContainer());
            if (path != null) loadDirectory(path);
        }
    }

    public void createTab(Path newPath, boolean allowDuplicate) {
        createTab(newPath, allowDuplicate, false);
    }

    public void createTab(Path newPath, boolean allowDuplicate, boolean silent) {
        for (TabsManager.Tab tab : tabs().getTabs()) {
            if (containerPaths.get(tab.getContainer()).equals(newPath) && !allowDuplicate) {
                tabs().setActiveTab(tabs().getTabs().indexOf(tab));
                return;
            }
        }
        if (!silent) playSound(Sound.CREATE);
        Container newContainer = createContainer(5, 60, width - 10, height - 5);
        newContainer.columns(1).padding(2).enableSelecting(true).layout(new RestrictedLayout());
        TabsManager.Tab newTab = tabs().addTab(getTabName(newPath), newContainer);
        containerPaths.put(newContainer, newPath);
        tabs().setActiveTab(tabs().getTabs().indexOf(newTab));
    }

    private void onSearch(String query) {
        filterEntries(query);
    }

    private void onSearchTextChange(String query) {
        if (header().liveUpdate) filterEntries(query);
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
        if (activeTab != null) activeTab.setName(getTabName(path));
        saveExplorerTabs();
    }

    private void navigateUp() {
        Path parentPath = currentPath.getParent();
        if (parentPath != null) navigateTo(parentPath);
        else client.setScreen(parent);
        saveExplorerTabs();
    }

    private void navigateBack() {}

    private void createNewFile() {}

    private void openExternally() {
        openExternally(currentPath);
    }

    public void openExternally(Path path) {
        if (isRemote) {
            new Notification("Not Allowed On Remote Explorer.", Notification.Type.WARN);
            return;
        }
        try {
            new ProcessBuilder("explorer.exe", path.toString()).start();
        } catch (Exception e) {
            new Notification("Failed To Open Externally.", e.getMessage(), Notification.Type.ERROR);
        }
    }

    private void paste() {
        if (fileAPI.getClipboard().isEmpty()) {
            new Notification("Clipboard Is Empty.", Notification.Type.WARN);
            return;
        }
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
        for (AnimatedWidget widget : currentSelectedWidgets) ((FileEntryWidget) widget).toggleFavorite();
    }

    private String getTabName(Path path) {
        return path.getFileName() != null ? path.getFileName().toString() : path.toString();
    }

    public void saveExplorerTabs() {
        try {
            Path tabsFile = Paths.get(String.valueOf(remotelyDir), "data", "file_explorer_tabs.json");
            if (!Files.exists(tabsFile.getParent())) Files.createDirectories(tabsFile.getParent());
            Map<String, Object> data = new HashMap<>();
            List<Map<String, Object>> tabList = new ArrayList<>();
            for (TabsManager.Tab tab : tabs().getTabs()) {
                Map<String, Object> tabMap = new HashMap<>();
                Path tabPath = containerPaths.get(tab.getContainer());
                tabMap.put("path", tabPath.toString());
                tabMap.put("isRemote", isRemote);
                tabMap.put("scrollOffset", 0);
                if (isRemote && serverInfo.remoteHost != null) {
                    Map<String, Object> hostMap = new HashMap<>();
                    hostMap.put("user", serverInfo.remoteHost.getUser());
                    hostMap.put("ip", serverInfo.remoteHost.getIp());
                    hostMap.put("port", serverInfo.remoteHost.getPort());
                    hostMap.put("password", serverInfo.remoteHost.getPassword());
                    tabMap.put("remoteHostInfo", hostMap);
                }
                tabList.add(tabMap);
            }
            data.put("tabs", tabList);
            data.put("currentTabIndex", tabs().getActiveTabIndex());
            Files.write(tabsFile, new Gson().toJson(data).getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadExplorerTabs() {
        try {
            Path tabsFile = Paths.get(String.valueOf(remotelyDir), "data", "file_explorer_tabs.json");
            if (Files.exists(tabsFile)) {
                String json = new String(Files.readAllBytes(tabsFile));
                Map<String, Object> data = new Gson().fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
                List<Map<String, Object>> tabList = (List<Map<String, Object>>) data.get("tabs");
                int activeTabIndex = ((Number) data.getOrDefault("currentTabIndex", 0)).intValue();
                for (Map<String, Object> tabMap : tabList) {
                    String pathStr = (String) tabMap.get("path");
                    Path path = Paths.get(pathStr);
                    createTab(path, true, true);
                }
                if (!tabs().getTabs().isEmpty()) tabs().setActiveTab(Math.min(activeTabIndex, tabs().getTabs().size() - 1));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        if (itemsContextMenu.isOpen() && itemsContextMenu.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 1) {
            itemsContextMenu.show((int) mouseX, (int) mouseY);
            if (currentSelectedWidgets.isEmpty()) {
                for (AnimatedWidget widget : activeContainer.getWidgets()) {
                    if (widget.isMouseOver(mouseX, mouseY)) activeContainer.addSelectedWidget(widget);
                }
            }
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
                case GLFW.GLFW_KEY_UP -> { activeContainer.columns(activeContainer.getColumns() + 1); return true; }
                case GLFW.GLFW_KEY_DOWN -> { if (activeContainer.getColumns() > 1) activeContainer.columns(activeContainer.getColumns() - 1); return true; }
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            for (FileEntryWidget widget : allWidgets) if (widget.isFocused()) onFileDoubleClick(widget);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) { deleteSelected(); return true; }
        if (keyCode == GLFW.GLFW_KEY_F2) { renameSelected(); return true; }
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
    public void setActiveContainer(Container container) {
        super.setActiveContainer(container);
        if (containerPaths.containsKey(container)) currentPath = containerPaths.get(container);
        else containerPaths.put(container, currentPath);
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.FILEEXPLORER);
    }
}