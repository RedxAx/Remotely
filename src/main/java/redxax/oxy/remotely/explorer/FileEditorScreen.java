package redxax.oxy.remotely.explorer;

import redxax.oxy.remotely.ui.widgets.FileEntryWidget;
import restudio.rescreen.platform.IDrawContext;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.api.RemotelyAPI;
import redxax.oxy.remotely.api.RemotelyApiFactory;
import redxax.oxy.remotely.servers.ServerInfo;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.TextAreaWidget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static restudio.rescreen.util.SoundUtils.playSound;

public class FileEditorScreen extends ReScreen {
    private static final Map<Path, SavedTabState> SAVED_TABS = new HashMap<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final Screen parent;
    private final ServerInfo serverInfo;
    private final List<Position> searchResults = new ArrayList<>();
    private int currentSearchIndex = 0;
    private SidePanel explorerPanel;
    private boolean showExplorerPanel = false;
    private Path explorerPath;
    private final RemotelyAPI fileAPI;
    private double originalMCScale;
    private TextAreaWidget activeTextArea;

    private static class SavedTabState {
        String content;
        boolean unsaved;
        String originalContent;

        SavedTabState() {
            this.content = "";
        }
    }

    public class Tab {
        Path path;
        public String name;
        public TextAreaWidget textAreaWidget;
        public boolean unsaved;
        String originalContent;

        Tab(Path path) {
            this.path = path.normalize();
            this.name = path.getFileName() != null ? path.getFileName().toString() : path.toString();

            TextAreaWidget.Builder builder = new TextAreaWidget.Builder();
            this.textAreaWidget = builder.build();
            this.textAreaWidget.onChange = this::onTextChange;

            if (SAVED_TABS.containsKey(this.path)) {
                SavedTabState st = SAVED_TABS.get(this.path);
                this.textAreaWidget.setText(st.content);
                this.originalContent = st.originalContent;
                this.unsaved = st.unsaved;
            } else {
                loadFileContent();
            }
        }

        private void onTextChange(String newContent) {
            this.unsaved = !newContent.equals(originalContent);
            if (tabs() != null) {
                int tabIndex = -1;
                for (int i = 0; i < tabs.size(); i++) {
                    if (tabs.get(i) == this) {
                        tabIndex = i;
                        break;
                    }
                }
                if (tabIndex != -1) {
                    List<restudio.rescreen.ui.rescreen.ReScreen.TabsManager.Tab> uiTabs = tabs().getTabs();
                    if (tabIndex < uiTabs.size() && uiTabs.get(tabIndex).getData() == this) {
                        tabs().setTabUnsaved(tabIndex, this.unsaved);
                    }
                }
            }
        }

        private void loadFileContent() {
            fileAPI.readFile(path).thenAccept(content -> client.execute(() -> {
                String sanitizedContent = content.replace("\r\n", "\n").replace("\r", "\n").replace("\t", "\t");
                this.textAreaWidget.setText(sanitizedContent);
                this.originalContent = sanitizedContent;
                this.unsaved = false;
            })).exceptionally(e -> {
                client.execute(() -> new Notification("Failed To Load File", e.getCause().getMessage(), Notification.Type.ERROR));
                return null;
            });
        }

        public void saveFile() {
            String newContent = textAreaWidget.getText();
            fileAPI.writeFile(path, newContent).thenRun(() -> client.execute(() -> {
                this.unsaved = false;
                this.originalContent = newContent;
                onTextChange(newContent);
                new Notification("File Saved!", path.getFileName().toString(), Notification.Type.SUCCESS);
            })).exceptionally(e -> {
                client.execute(() -> new Notification("Failed To Save", e.getCause().getMessage(), Notification.Type.ERROR));
                return null;
            });
        }
    }

    public FileEditorScreen(Screen parent, Path filePath, ServerInfo info) {
        super();
        this.parent = parent;
        this.serverInfo = info;
        this.fileAPI = RemotelyApiFactory.get(info);

        boolean found = false;
        int foundIndex = -1;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).path.equals(filePath.normalize())) {
                found = true;
                foundIndex = i;
                break;
            }
        }

        if (!found) {
            Tab initialTab = new Tab(filePath);
            tabs.add(initialTab);
        } else {
            Tab tab = tabs.remove(foundIndex);
            tabs.addFirst(tab);
        }
    }

    @Override
    public void init() {
        super.init();

        header().addRight("close.png", this::close, "Close").addRight("save.png", () -> {
            if (activeTextArea != null) {
                int idx = tabs().getActiveTabIndex();
                if (idx != -1) {
                    Tab tab = (Tab) tabs().getTabs().get(idx).getData();
                    tab.saveFile();
                }
            }
        }, "Save File").addRight("explorer.png", this::toggleExplorerPanel, "Toggle Explorer").setSearchMode(new SearchMode(false), true).build();

        if (header().searchBox != null) {
            ((SearchTextInputWidget) header().searchBox).onEnter = this::performSearch;
        }

        tabs().builder().position(5, 35).size(width - 10, 18).allowClose(true).allowReorder(true).allowAdd(true).onTabSelected(this::onTabSelected).onTabClosed(this::onTabClosed).onTabsReordered(this::onTabsReordered).build();

        List<Path> loadedPaths = RemotelyClient.INSTANCE.loadFileEditorTabs();
        for (Path path : loadedPaths) {
            if (tabs.stream().noneMatch(t -> t.path.equals(path.normalize()))) {
                tabs.add(new Tab(path));
            }
        }

        for (Tab tab : tabs) {
            TabsManager.Tab uiTab = tabs().addTab(tab.name, null);
            uiTab.setData(tab);
            tabs().setTabUnsaved(tabs().getTabs().size() - 1, tab.unsaved);
        }

        explorerPanel = createSidePanel("explorer").y(60).height(this.height - 5).width(200);

        if (!tabs.isEmpty()) {
            tabs().setActiveTab(0);
            onTabSelected(tabs().getActiveTab());
        }
    }

    @Override
    public void close() {
        for (Tab t : tabs) {
            saveTabState(t);
        }
        RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
        client.setScreen(parent);
    }

    private void saveTabState(Tab tab) {
        SavedTabState st = new SavedTabState();
        st.content = tab.textAreaWidget.getText();
        st.unsaved = tab.unsaved;
        st.originalContent = tab.originalContent;
        SAVED_TABS.put(tab.path, st);
    }

    private void onTabSelected(TabsManager.Tab uiTab) {
        if (this.activeTextArea != null) {
            this.remove(this.activeTextArea);
        }

        if (uiTab == null) {
            this.activeTextArea = null;
            return;
        }

        Tab dataTab = (Tab) uiTab.getData();
        if (dataTab != null) {
            this.activeTextArea = dataTab.textAreaWidget;
            this.addDrawableChild(this.activeTextArea);

            explorerPath = dataTab.path.getParent();
            if (showExplorerPanel) {
                loadExplorerDirectory(explorerPath);
            }
        } else {
            this.activeTextArea = null;
        }

        if (header().searchBox != null) {
            updateSearchResults(header().searchBox.getText());
        }
    }

    private void onTabClosed(TabsManager.Tab uiTab) {
        Tab dataTab = (Tab) uiTab.getData();
        SAVED_TABS.remove(dataTab.path);
        tabs.remove(dataTab);
        if (dataTab.textAreaWidget == activeTextArea) {
            this.remove(activeTextArea);
            activeTextArea = null;
        }
        if (tabs.isEmpty()) {
            close();
        } else {
            RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
        }
    }

    private void onTabsReordered(List<TabsManager.Tab> uiTabs) {
        List<Tab> newTabsOrder = new ArrayList<>();
        for (TabsManager.Tab uiTab : uiTabs) {
            newTabsOrder.add((Tab) uiTab.getData());
        }
        tabs.clear();
        tabs.addAll(newTabsOrder);
        RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
    }

    private void toggleExplorerPanel() {
        playSound(Sound.PANEL);
        showExplorerPanel = !showExplorerPanel;
        if (showExplorerPanel) {
            explorerPanel.show();
            if (explorerPath != null) {
                loadExplorerDirectory(explorerPath);
            }
        } else {
            explorerPanel.hide();
        }
    }

    private void loadExplorerDirectory(Path path) {
        if (path == null) return;
        this.explorerPath = path;
        explorerPanel.container().clearWidgets();

        if (path.getParent() != null) {
            RemotelyAPI.FileEntry upEntry = new RemotelyAPI.FileEntry(path.getParent(), true, "", "", "..");
            FileEntryWidget upWidget = new FileEntryWidget.Builder(upEntry, fileAPI, serverInfo.isRemote, Collections.emptyList(), new Object()).onClick(w -> loadExplorerDirectory(w.getFileEntry().path)).entranceCorner(AnimatedWidget.EntranceCorner.TOP_LEFT).build();
            explorerPanel.container().addWidget(upWidget);
        }

        fileAPI.listDirectory(path).thenAccept(children -> {
            children.sort(Comparator.comparing(e -> e.displayName));
            children.sort(Comparator.comparing(e -> !e.isDirectory));
            client.execute(() -> {
                for (RemotelyAPI.FileEntry child : children) {
                    FileEntryWidget widget = new FileEntryWidget.Builder(child, fileAPI, serverInfo.isRemote, Collections.emptyList(), new Object())
                            .onClick(this::onExplorerEntryClicked).entranceCorner(AnimatedWidget.EntranceCorner.TOP_LEFT)
                            .build();
                    explorerPanel.container().addWidget(widget);
                }
            });
        }).exceptionally(e -> {
            client.execute(() -> {
                if (path.getParent() != null) loadExplorerDirectory(path.getParent());
            });
            return null;
        });
    }

    private void onExplorerEntryClicked(FileEntryWidget widget) {
        RemotelyAPI.FileEntry entry = widget.getFileEntry();
        if (entry.isDirectory) {
            loadExplorerDirectory(entry.path);
        } else {
            if (FileExplorerScreen.isSupportedFile(entry.path)) {
                for (int i = 0; i < tabs.size(); i++) {
                    if (tabs.get(i).path.equals(entry.path.normalize())) {
                        tabs().setActiveTab(i);
                        return;
                    }
                }
                Tab newTab = new Tab(entry.path);
                tabs.add(newTab);
                TabsManager.Tab uiTab = tabs().addTab(newTab.name, null);
                uiTab.setData(newTab);
                tabs().setActiveTab(tabs().getTabs().size() - 1);
            }
        }
    }

    private void performSearch(String query) {
        updateSearchResults(query);
        if (searchResults.isEmpty()) return;

        currentSearchIndex++;
        if (currentSearchIndex >= searchResults.size()) {
            currentSearchIndex = 0;
        }

        Position pos = searchResults.get(currentSearchIndex);
        if (activeTextArea != null) {
            activeTextArea.setCursor(pos.line, pos.start);
        }
    }

    private void updateSearchResults(String query) {
        searchResults.clear();
        if (query.isEmpty() || activeTextArea == null) {
            return;
        }

        String text = activeTextArea.getText();
        String[] lines = text.split("\n", -1);
        query = query.toLowerCase();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].toLowerCase();
            int index = 0;
            while ((index = line.indexOf(query, index)) != -1) {
                searchResults.add(new Position(i, index, index + query.length()));
                index += query.length();
            }
        }
        currentSearchIndex = -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrlHeld = hasControlDown();

        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_S) {
            if (activeTextArea != null) {
                int idx = tabs().getActiveTabIndex();
                if (idx != -1) {
                    ((Tab) tabs().getTabs().get(idx).getData()).saveFile();
                }
            }
            return true;
        }

        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_F) {
            if (header().searchBox != null) {
                header().searchBox.setFocused(true);
            }
            return true;
        }

        if (activeTextArea != null && activeTextArea.isFocused()) {
            return activeTextArea.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (header().searchBox == null || !header().searchBox.isFocused())) {
            close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (activeTextArea != null) {
            int x = 5;
            int y = 60;
            int w = width - 10;
            int h = height - y - 5;

            if (showExplorerPanel && explorerPanel.isVisible()) {
                int panelWidth = (int) explorerPanel.getAnimatedWidth();
                w -= panelWidth ;
            }
            activeTextArea.setPosition(x, y);
            activeTextArea.setSize(w, h);
        }
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.FILEEDITOR);
    }

    @Override
    public void removed() {
        if (parent == null) playSound(Sound.SCREEN);
    }

    static class Position {
        int line;
        int start;
        int end;

        Position(int line, int start, int end) {
            this.line = line;
            this.start = start;
            this.end = end;
        }
    }
}