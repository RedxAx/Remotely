package redxax.oxy.remotely.explorer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.api.LocalAPI;
import redxax.oxy.remotely.api.RemoteAPI;
import redxax.oxy.remotely.api.RemotelyCoreAPI;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.ui.ReScreen;
import redxax.oxy.remotely.ui.widgets.FileEntryWidget;
import redxax.oxy.remotely.ui.widgets.TextAreaWidget;
import redxax.oxy.remotely.util.Sound;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

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
    private RemotelyCoreAPI fileAPI;
    private double originalMCScale;

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

            this.textAreaWidget = new TextAreaWidget.Builder().build();
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
                    List<TabsManager.Tab> uiTabs = tabs().getTabs();
                    if (tabIndex < uiTabs.size() && uiTabs.get(tabIndex).getData() == this) {
                        tabs().setTabUnsaved(tabIndex, this.unsaved);
                    }
                }
            }
        }

        private void loadFileContent() {
            ArrayList<String> fileContent = new ArrayList<>();
            if (serverInfo.isRemote) {
                try {
                    if (serverInfo.remoteSSHManager == null) {
                        serverInfo.remoteSSHManager = new SSHManager(serverInfo);
                        serverInfo.remoteSSHManager.connectToRemoteHost(serverInfo.remoteHost.getUser(), serverInfo.remoteHost.ip, serverInfo.remoteHost.port, serverInfo.remoteHost.password);
                        serverInfo.remoteSSHManager.connectSFTP();
                    } else if (!serverInfo.remoteSSHManager.isSFTPConnected()) {
                        serverInfo.remoteSSHManager.connectSFTP();
                    }
                    String remotePath = path.toString().replace("\\", "/");
                    String content = serverInfo.remoteSSHManager.readRemoteFile(remotePath);
                    String[] lines = content.split("\\r?\\n");
                    for (int i = 0; i < lines.length; i++) {
                        lines[i] = lines[i].replace("\\t", "\t");
                    }
                    Collections.addAll(fileContent, lines);
                } catch (Exception e) {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File load error (remote): " + e.getMessage() + "\n");
                    }
                }
            } else {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    reader.lines().forEach(line -> {
                        line = line.replace("\\t", "\t");
                        fileContent.add(line);
                    });
                } catch (IOException e) {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File load error: " + e.getMessage() + "\n");
                    }
                }
            }
            String contentString = String.join("\n", fileContent);
            this.textAreaWidget.setText(contentString);
            this.originalContent = contentString;
            this.unsaved = false;
        }

        public void saveFile() {
            String newContent = textAreaWidget.getText();
            ArrayList<String> newContentLines = new ArrayList<>(Arrays.asList(newContent.split("\n")));

            if (serverInfo.isRemote) {
                try {
                    if (serverInfo.remoteSSHManager == null || !serverInfo.remoteSSHManager.isSFTPConnected()) {
                        serverInfo.remoteSSHManager = new SSHManager(serverInfo);
                        serverInfo.remoteSSHManager.connectToRemoteHost(serverInfo.remoteHost.getUser(), serverInfo.remoteHost.ip, serverInfo.remoteHost.port, serverInfo.remoteHost.password);
                        serverInfo.remoteSSHManager.connectSFTP();
                    }
                    String remotePath = path.toString().replace("\\", "/");
                    serverInfo.remoteSSHManager.writeRemoteFile(remotePath, newContent);
                    this.unsaved = false;
                    this.originalContent = newContent;
                } catch (Exception e) {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File save error (remote): " + e.getMessage() + "\n");
                    }
                }
            } else {
                try {
                    Files.write(path, newContentLines);
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File saved: " + path + "\n");
                    }
                    this.unsaved = false;
                    this.originalContent = newContent;
                } catch (IOException e) {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File save error: " + e.getMessage() + "\n");
                    }
                }
            }
            onTextChange(newContent);
        }
    }

    public FileEditorScreen(MinecraftClient mc, Screen parent, Path filePath, ServerInfo info) {
        super(Text.literal("File Editor"));
        this.parent = parent;
        this.serverInfo = info;

        if (serverInfo.isRemote) {
            this.fileAPI = new RemoteAPI(serverInfo.remoteHost);
        } else {
            this.fileAPI = new LocalAPI();
        }

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

        this.originalMCScale = mc.getWindow().getScaleFactor();
        mc.getWindow().setScaleFactor(globalScaleFactor);
    }

    @Override
    protected void init() {
        super.init();

        header().addRight("/assets/remotely/icons/close.png", this::close, "Close")
                .addRight("/assets/remotely/icons/save.png", () -> {
                    int idx = tabs().getActiveTabIndex();
                    if (idx != -1) {
                        Tab tab = (Tab) tabs().getTabs().get(idx).getData();
                        tab.saveFile();
                    }
                }, "Save File")
                .addRight("/assets/remotely/icons/explorer.png", this::toggleExplorerPanel, "Toggle Explorer")
                .setSearchMode(new SearchMode(false), false)
                .build();

        if (header().searchBox != null) {
            ((SearchTextInputWidget) header().searchBox).onEnter = this::performSearch;
        }

        tabs().builder()
                .position(5, 35)
                .size(width - 10, 18)
                .allowClose(tabCloseButtons)
                .allowReorder(true)
                .onTabSelected(this::onTabSelected)
                .onTabClosed(this::onTabClosed)
                .onTabsReordered(this::onTabsReordered)
                .build();

        List<Path> loadedPaths = RemotelyClient.INSTANCE.loadFileEditorTabs();
        for (Path path : loadedPaths) {
            if (tabs.stream().noneMatch(t -> t.path.equals(path.normalize()))) {
                tabs.add(new Tab(path));
            }
        }

        for (Tab tab : tabs) {
            Container c = createContainer("container_for_" + tab.name, 5, 60, width - 10, height - 5);
            TabsManager.Tab uiTab = tabs().addTab(tab.name, c);
            uiTab.setData(tab);
            tabs().setTabUnsaved(tabs().getTabs().size() - 1, tab.unsaved);
        }

        explorerPanel = createSidePanel("explorer").y(60).height(this.height - 65).width(200);

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
        if (uiTab == null) {
            if (activeContainer != null) activeContainer.clearWidgets();
            return;
        }
        setActiveContainer(uiTab.getContainer());
        if (activeContainer != null) {
            activeContainer.clearWidgets();
            Tab dataTab = (Tab) uiTab.getData();
            if (dataTab != null) {
                activeContainer.addWidget(dataTab.textAreaWidget);
                dataTab.textAreaWidget.setFocused(true);
                explorerPath = dataTab.path.getParent();
                if (showExplorerPanel) {
                    loadExplorerDirectory(explorerPath);
                }
            }
        }
        if (header().searchBox != null) {
            updateSearchResults(header().searchBox.getText());
        }
    }

    private void onTabClosed(TabsManager.Tab uiTab) {
        Tab dataTab = (Tab) uiTab.getData();
        SAVED_TABS.remove(dataTab.path);
        tabs.remove(dataTab);
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
            RemotelyCoreAPI.FileEntry upEntry = new RemotelyCoreAPI.FileEntry(path.getParent(), true, "", "", "..");
            FileEntryWidget upWidget = new FileEntryWidget.Builder(upEntry, fileAPI, serverInfo.isRemote, Collections.emptyList(), new Object()).onClick(w -> loadExplorerDirectory(w.getFileEntry().path)).build();
            explorerPanel.container().addWidget(upWidget);
        }

        fileAPI.listDirectory(path).thenAccept(children -> {
            children.sort(Comparator.comparing(e -> e.displayName));
            children.sort(Comparator.comparing(e -> !e.isDirectory));
            client.execute(() -> {
                for (RemotelyCoreAPI.FileEntry child : children) {
                    FileEntryWidget widget = new FileEntryWidget.Builder(child, fileAPI, serverInfo.isRemote, Collections.emptyList(), new Object())
                            .onClick(this::onExplorerEntryClicked)
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
        RemotelyCoreAPI.FileEntry entry = widget.getFileEntry();
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
                Container c = createContainer("container_for_" + newTab.name, 5, 60, width - 10, height - 5);
                TabsManager.Tab uiTab = tabs().addTab(newTab.name, c);
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
        int activeTabIndex = tabs().getActiveTabIndex();
        if (activeTabIndex != -1) {
            Tab tab = (Tab) tabs().getTabs().get(activeTabIndex).getData();
            tab.textAreaWidget.setCursor(pos.line, pos.start);
        }
    }

    private void updateSearchResults(String query) {
        searchResults.clear();
        if (query.isEmpty()) {
            return;
        }

        int activeTabIndex = tabs().getActiveTabIndex();
        if (activeTabIndex == -1) return;

        Tab tab = (Tab) tabs().getTabs().get(activeTabIndex).getData();
        String text = tab.textAreaWidget.getText();
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
            int idx = tabs().getActiveTabIndex();
            if (idx != -1) {
                ((Tab) tabs().getTabs().get(idx).getData()).saveFile();
            }
            return true;
        }

        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_F) {
            if (header().searchBox != null) {
                header().searchBox.setFocused(true);
            }
            return true;
        }

        if (keyCode == this.client.options.backKey.getDefaultKey().getCode() && (header().searchBox == null || !header().searchBox.isFocused())) {
            close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (activeContainer != null) {
            TabsManager.Tab activeTab = tabs().getActiveTab();
            if (activeTab != null && activeTab.getData() instanceof Tab tab && tab.textAreaWidget != null) {
                tab.textAreaWidget.setHeight(height - 85);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.FILEEDITOR);
    }

    @Override
    public void removed() {
        client.getWindow().setScaleFactor(originalMCScale);
        targetScaleFactor = globalScaleFactor = animScaleFactor;
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