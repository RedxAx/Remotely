package redxax.oxy.remotely.flow.ui.studio;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.DesignerSaveNotifications;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogLoader;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowSerializer;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.ui.ContentDesignerScreen;
import redxax.oxy.remotely.flow.ui.OptionCatalogSelector;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.ui.screens.editor.CompactWorkspaceBrowserWidget;
import restudio.rebase.ui.screens.editor.WorkspaceTreeExplorer;
import restudio.rebase.ui.widgets.FileEntryWidget;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReKey;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseButton;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReScrollEvent;
import restudio.rescreen.platform.input.ReTextInputEvent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.ContextMenuWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ReSyncContentBrowserWidget extends AnimatedWidget {
    private static final int BROWSER_HISTORY_LIMIT = 30;
    private static final OptionCatalogLoader.Profile CONTENT_CATALOGS = OptionCatalogLoader.profile(
        "server:custom_content:provider", "server:minecraft:material");
    private final Gson gson = new Gson();
    private final StudioScreen screen;
    private static final int STUDIO_CONTENT_BROWSER_DEFAULT_WIDTH = 190;
    private static final int STUDIO_CONTENT_BROWSER_MIN_WIDTH = 150;
    public static final int STUDIO_CONTENT_BROWSER_TOP = 38;
    public static final int STUDIO_CONTENT_BROWSER_BOTTOM = 8;
    public static final int STUDIO_CONTENT_BROWSER_GAP = 4;
    private static final int STUDIO_CONTENT_BROWSER_ENTRY_HEIGHT = 16;
    private static final int STUDIO_CONTENT_BROWSER_TOOL_SIZE = 16;
    private final Path projectRoot = Path.of("ReSync");
    private String currentFolder = "";
    private final Deque<String> backHistory = new ArrayDeque<>();
    private final Deque<String> forwardHistory = new ArrayDeque<>();
    private ReSyncProjectMetadata.ResourceEntry selectedResource;
    private ReSyncProjectMetadata.FolderEntry selectedFolder;
    private boolean selectedProjectRoot;
    private BrowserClipboard clipboard;
    private final Deque<BrowserHistoryEntry> undoHistory = new ArrayDeque<>();
    private final Deque<BrowserHistoryEntry> redoHistory = new ArrayDeque<>();
    private int lastMouseX;
    private int lastMouseY;
    private final Container treeContainer;
    private final TextInputWidget nameInput;
    private final TextInputWidget searchInput;
    private final ReSyncProjectTreeProvider treeProvider;
    private final WorkspaceTreeExplorer treeExplorer;
    private final CompactWorkspaceBrowserWidget browser;
    private final SidePanel sidePanel;
    private final SquareButtonWidget createButton;
    private final SquareButtonWidget marketplaceButton;
    private final SquareButtonWidget permissionsButton;
    private final SquareButtonWidget updateButton;
    private ItemSelectorWidget createSelector;
    private ItemSelectorWidget createContentSelector;
    private AssetBrowserSnapshot lastAssetBrowserSnapshot;
    private final Map<String, String> resourceIconPaths = new HashMap<>();
    private boolean treeInitialized;
    private boolean temporarilyHidden;
    private boolean shortcutFocused;

    private record AssetBrowserSnapshot(List<String> folders, List<String> resources) {
    }

    private record ClipboardResource(String type, String id, String path) {
    }

    private record BrowserClipboard(List<ClipboardResource> resources, List<String> folderPaths, boolean cut) {
        private BrowserClipboard {
            resources = List.copyOf(resources);
            folderPaths = List.copyOf(folderPaths);
        }
    }

    private record BrowserSelection(List<ReSyncProjectMetadata.ResourceEntry> resources, List<ReSyncProjectMetadata.FolderEntry> folders, boolean projectRoot, int selectedCount) {
        private int size() {
            return resources.size() + folders.size();
        }
    }

    private record ResourceSnapshot(String type, String id, String payload, String context) {
    }

    private record BrowserEditStart(String label, String metadata, Map<String, ResourceSnapshot> resources) {
    }

    private record BrowserHistoryEntry(String label, String beforeMetadata, String afterMetadata, Map<String, ResourceSnapshot> beforeResources, Map<String, ResourceSnapshot> afterResources, Set<String> affectedKeys) {
    }

    private static class CommandBindingContext {
        private String command;
        private List<String> subcommands;
        private Boolean structured;
    }

    public ReSyncContentBrowserWidget(StudioScreen screen, int x, int y, int width, int height) {
        super(x, y, width, height, "");
        this.screen = screen;
        animateElevation = false;
        entranceAnimationEnabled = false;
        enableHoverColors = false;
        nameInput = new TextInputWidget.Builder()
            .placeholder("Selected")
            .size(110, 18)
            .build();
        treeProvider = new ReSyncProjectTreeProvider();
        createButton = new SquareButtonWidget.Builder()
            .imagePath("add.png")
            .size(STUDIO_CONTENT_BROWSER_TOOL_SIZE, STUDIO_CONTENT_BROWSER_TOOL_SIZE)
            .hint("Create")
            .onClick(this::showCreateMenu)
            .build();
        marketplaceButton = new SquareButtonWidget.Builder()
            .imagePath("market.png")
            .size(STUDIO_CONTENT_BROWSER_TOOL_SIZE, STUDIO_CONTENT_BROWSER_TOOL_SIZE)
            .hint("Marketplace")
            .onClick(screen::openReSyncMarketplace)
            .build();
        permissionsButton = new SquareButtonWidget.Builder()
            .imagePath("op.png")
            .size(STUDIO_CONTENT_BROWSER_TOOL_SIZE, STUDIO_CONTENT_BROWSER_TOOL_SIZE)
            .hint("Permissions")
            .onClick(screen::openReSyncPermissions)
            .build();
        updateButton = new SquareButtonWidget.Builder()
            .imagePath("ReSync.png")
            .size(STUDIO_CONTENT_BROWSER_TOOL_SIZE, STUDIO_CONTENT_BROWSER_TOOL_SIZE)
            .hint("Update ReSync")
            .onClick(screen::updateReSyncFromContentBrowser)
            .build();
        updateButton.setVisible(false);
        browser = new CompactWorkspaceBrowserWidget(
            screen,
            "studioContentBrowser",
            STUDIO_CONTENT_BROWSER_TOP,
            STUDIO_CONTENT_BROWSER_BOTTOM,
            STUDIO_CONTENT_BROWSER_DEFAULT_WIDTH,
            STUDIO_CONTENT_BROWSER_MIN_WIDTH,
            120,
            STUDIO_CONTENT_BROWSER_ENTRY_HEIGHT,
            this::openTreeFile,
            false,
            SidePanel.Anchor.LEFT,
            updateButton,
            permissionsButton,
            marketplaceButton,
            createButton
        );
        sidePanel = browser.sidePanel();
        searchInput = browser.searchInput();
        treeContainer = browser.treeContainer();
        treeExplorer = browser.treeExplorer();
        treeExplorer.setToggleDirectoriesOnActivation(false);
        treeExplorer.setOnNodeActivated(this::activateTreeNode);
        treeExplorer.setOnNodeOpened(this::openTreeNode);
        treeExplorer.setOnNodeRightClick(this::rightClickTreeNode);
        treeExplorer.setOnNodeDragStarted(this::startResourceDrag);
        sidePanel.show();
        CONTENT_CATALOGS.preload(screen.studioServerId());
        updateContainers();
        rebuild();
    }

    @Override
    protected void drawContent(IDrawContext context, int mouseX, int mouseY) {
    }

    private void updateSearch(String query) {
        if (treeExplorer != null) {
            treeExplorer.setSearchQuery(query);
        }
    }

    public SidePanel sidePanel() {
        return sidePanel;
    }

    public int visibleLayoutWidth() {
        if (temporarilyHidden || sidePanel == null || !sidePanel.isVisible()) {
            return 0;
        }
        int renderedWidth = Math.max(sidePanel.getDesiredWidth(), (int) Math.ceil(sidePanel.getAnimatedWidth()));
        if (sidePanel.container() != null) {
            renderedWidth = Math.max(renderedWidth, sidePanel.container().getWidth());
        }
        return renderedWidth + 8;
    }

    public void updateShortcutFocus(ReMouseEvent event) {
        shortcutFocused = !temporarilyHidden && sidePanel != null && sidePanel.isVisible() && sidePanel.isMouseOver(event.x(), event.y());
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        if (temporarilyHidden) {
            return false;
        }
        lastMouseX = (int) event.x();
        lastMouseY = (int) event.y();
        if (handleHistoryMouseButton(event)) {
            return true;
        }
        if (sidePanel == null) {
            return false;
        }
        boolean handled = sidePanel.mouseClicked(event.retarget(sidePanel, event.x(), event.y()));
        return handled || event.button() == ReMouseButton.RIGHT && sidePanel.isMouseOver(event.x(), event.y());
    }

    public boolean handleHistoryMouseButton(int button) {
        if (temporarilyHidden) {
            return false;
        }
        if (button == 3) {
            navigateHistoryBack();
            return true;
        }
        if (button == 4) {
            navigateHistoryForward();
            return true;
        }
        return false;
    }

    public boolean handleHistoryMouseButton(ReMouseEvent event) {
        if (temporarilyHidden) {
            return false;
        }
        if (event.button() == ReMouseButton.BACK) {
            navigateHistoryBack();
            return true;
        }
        if (event.button() == ReMouseButton.FORWARD) {
            navigateHistoryForward();
            return true;
        }
        return handleHistoryMouseButton(event.nativeButton());
    }

    private boolean handleHistoryMouseButton(ReMouseButton button) {
        if (temporarilyHidden) {
            return false;
        }
        if (button == ReMouseButton.BACK) {
            navigateHistoryBack();
            return true;
        }
        if (button == ReMouseButton.FORWARD) {
            navigateHistoryForward();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        if (temporarilyHidden) {
            return false;
        }
        if (sidePanel != null && sidePanel.mouseReleased(event.retarget(sidePanel, event.x(), event.y()))) {
            updateContainers();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(ReMouseEvent event) {
        if (temporarilyHidden) {
            return false;
        }
        if (sidePanel != null && sidePanel.mouseDragged(event.retarget(sidePanel, event.x(), event.y(), event.deltaX(), event.deltaY()))) {
            updateContainers();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(ReScrollEvent event) {
        if (temporarilyHidden) {
            return false;
        }
        if (sidePanel != null && sidePanel.mouseScrolled(event.retarget(sidePanel, event.x(), event.y()))) {
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        if (temporarilyHidden) {
            return false;
        }
        if (!browserShortcutActive()) return false;
        if (sidePanel != null && sidePanel.keyPressed(event.retarget(sidePanel))) {
            return true;
        }
        boolean shortcut = event.modifiers().control() || event.modifiers().superKey();
        if (shortcut && event.key() == ReKey.Z) {
            if (event.modifiers().shift()) redoBrowserEdit();
            else undoBrowserEdit();
            return true;
        }
        if (shortcut && event.key() == ReKey.Y) {
            redoBrowserEdit();
            return true;
        }
        if (shortcut && event.key() == ReKey.C) {
            copySelected();
            return true;
        }
        if (shortcut && event.key() == ReKey.X) {
            cutSelected();
            return true;
        }
        if (shortcut && event.key() == ReKey.V) {
            pasteSelected();
            return true;
        }
        if (event.key() == ReKey.F2) {
            renameSelected();
            return true;
        }
        if (event.key() == ReKey.DELETE) {
            deleteSelected();
            return true;
        }
        return false;
    }

    private boolean browserShortcutActive() {
        return shortcutFocused && !temporarilyHidden && sidePanel != null && sidePanel.isVisible();
    }

    private BrowserEditStart beginBrowserEdit(String label, List<ReSyncProjectMetadata.ResourceEntry> resources) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) return null;
        Map<String, ResourceSnapshot> snapshots = new LinkedHashMap<>();
        for (ReSyncProjectMetadata.ResourceEntry resource : resources) {
            ResourceSnapshot snapshot = snapshotResource(manager, resource);
            if (snapshot != null) snapshots.put(resource.key(), snapshot);
        }
        return new BrowserEditStart(label, gson.toJson(manager.getProjectMetadata(screen.studioServerId())), Map.copyOf(snapshots));
    }

    private void commitBrowserEdit(BrowserEditStart edit) {
        if (edit == null) return;
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) return;
        String afterMetadata = gson.toJson(manager.getProjectMetadata(screen.studioServerId()));
        if (edit.metadata().equals(afterMetadata)) return;
        ReSyncProjectMetadata before = gson.fromJson(edit.metadata(), ReSyncProjectMetadata.class);
        ReSyncProjectMetadata after = gson.fromJson(afterMetadata, ReSyncProjectMetadata.class);
        Map<String, ReSyncProjectMetadata.ResourceEntry> beforeEntries = resourceEntries(before);
        Map<String, ReSyncProjectMetadata.ResourceEntry> afterEntries = resourceEntries(after);
        Set<String> affectedKeys = new HashSet<>(beforeEntries.keySet());
        affectedKeys.addAll(afterEntries.keySet());
        affectedKeys.removeIf(key -> beforeEntries.containsKey(key) && afterEntries.containsKey(key));
        Map<String, ResourceSnapshot> afterSnapshots = new LinkedHashMap<>();
        for (String key : affectedKeys) {
            ReSyncProjectMetadata.ResourceEntry resource = afterEntries.get(key);
            if (resource == null) continue;
            ResourceSnapshot snapshot = snapshotResource(manager, resource);
            if (snapshot != null) afterSnapshots.put(key, snapshot);
        }
        boolean reversible = affectedKeys.stream().allMatch(key -> !beforeEntries.containsKey(key) || edit.resources().containsKey(key))
            && affectedKeys.stream().allMatch(key -> !afterEntries.containsKey(key) || afterSnapshots.containsKey(key));
        if (!reversible) {
            undoHistory.clear();
            redoHistory.clear();
            return;
        }
        undoHistory.addLast(new BrowserHistoryEntry(edit.label(), edit.metadata(), afterMetadata, edit.resources(), Map.copyOf(afterSnapshots), Set.copyOf(affectedKeys)));
        while (undoHistory.size() > BROWSER_HISTORY_LIMIT) undoHistory.removeFirst();
        redoHistory.clear();
    }

    private Map<String, ReSyncProjectMetadata.ResourceEntry> resourceEntries(ReSyncProjectMetadata metadata) {
        Map<String, ReSyncProjectMetadata.ResourceEntry> entries = new LinkedHashMap<>();
        if (metadata != null) {
            for (ReSyncProjectMetadata.ResourceEntry resource : metadata.getResources()) entries.put(resource.key(), resource);
        }
        return entries;
    }

    private ResourceSnapshot snapshotResource(FlowManager manager, ReSyncProjectMetadata.ResourceEntry resource) {
        String serverId = screen.studioServerId();
        String payload = switch (resource.getType()) {
            case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION -> {
                FlowGraph graph = manager.getFlowsForServer(serverId).get(resource.getId());
                yield graph == null ? null : FlowSerializer.serialize(graph);
            }
            case ReSyncResourceDragPayload.COMMAND -> {
                FlowGraph graph = manager.resolveCommandFlowGraph(serverId, resource.getId());
                yield graph == null ? null : FlowSerializer.serialize(graph);
            }
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> {
                CustomContentDefinition content = manager.getCustomContentForServer(serverId).get(resource.getId());
                yield content == null ? null : FlowSerializer.serializeCustomContent(content);
            }
            case ReSyncResourceDragPayload.GUI -> {
                GuiDefinition gui = manager.getGuisForServer(serverId).get(resource.getId());
                yield gui == null ? null : FlowSerializer.serializeGui(gui);
            }
            case ReSyncResourceDragPayload.SCOREBOARD -> {
                ScoreboardDefinition scoreboard = manager.getScoreboardsForServer(serverId).get(resource.getId());
                yield scoreboard == null ? null : FlowSerializer.serializeScoreboard(scoreboard);
            }
            case ReSyncResourceDragPayload.TAB -> {
                TabDefinition tab = manager.getTabsForServer(serverId).get(resource.getId());
                yield tab == null ? null : FlowSerializer.serializeTab(tab);
            }
            case ReSyncResourceDragPayload.WORLDGEN -> {
                WorldGenProject project = WorldGenManager.getInstance().getCachedProject(serverId, resource.getId());
                yield project == null ? null : gson.toJson(project);
            }
            case ReSyncResourceDragPayload.WORLD -> null;
            default -> {
                ReSyncResourceType type = ReSyncResourceType.byTypeId(resource.getType());
                JsonObject json = type == null ? null : manager.getJsonResourcesForServer(serverId, type).get(resource.getId());
                yield json == null ? null : json.toString();
            }
        };
        if (payload == null) return null;
        TriggerBinding binding = ReSyncResourceDragPayload.COMMAND.equals(resource.getType()) ? manager.getCommandBinding(serverId, resource.getId()) : null;
        return new ResourceSnapshot(resource.getType(), resource.getId(), payload, binding == null || binding.getContext() == null ? "" : binding.getContext());
    }

    private boolean restoreResource(FlowManager manager, ResourceSnapshot snapshot) {
        return DesignerSaveNotifications.withoutAutomaticNotifications(() -> {
            String serverId = screen.studioServerId();
            switch (snapshot.type()) {
                case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION -> manager.saveFlow(serverId, FlowSerializer.deserialize(snapshot.payload()));
                case ReSyncResourceDragPayload.COMMAND -> {
                    manager.saveFlow(serverId, FlowSerializer.deserialize(snapshot.payload()));
                    manager.setCommandBinding(serverId, snapshot.id(), snapshot.context().isBlank() ? snapshot.id() : snapshot.context());
                }
                case ReSyncResourceDragPayload.CUSTOM_CONTENT -> manager.saveCustomContent(serverId, FlowSerializer.deserializeCustomContent(snapshot.payload()));
                case ReSyncResourceDragPayload.GUI -> manager.saveGui(serverId, FlowSerializer.deserializeGui(snapshot.payload()));
                case ReSyncResourceDragPayload.SCOREBOARD -> manager.saveScoreboard(serverId, FlowSerializer.deserializeScoreboard(snapshot.payload()));
                case ReSyncResourceDragPayload.TAB -> manager.saveTab(serverId, FlowSerializer.deserializeTab(snapshot.payload()));
                case ReSyncResourceDragPayload.WORLDGEN -> WorldGenManager.getInstance().saveWorldGen(serverId, gson.fromJson(snapshot.payload(), WorldGenProject.class), false);
                case ReSyncResourceDragPayload.WORLD -> {
                    return false;
                }
                default -> {
                    ReSyncResourceType type = ReSyncResourceType.byTypeId(snapshot.type());
                    if (type == null) return false;
                    manager.saveJsonResource(serverId, type, gson.fromJson(snapshot.payload(), JsonObject.class));
                }
            }
            return true;
        });
    }

    private void undoBrowserEdit() {
        if (undoHistory.isEmpty()) return;
        BrowserHistoryEntry edit = undoHistory.removeLast();
        if (restoreBrowserEdit(edit, true)) {
            redoHistory.addLast(edit);
            new Notification("Undo", edit.label(), Notification.Type.SUCCESS);
        } else {
            undoHistory.addLast(edit);
        }
    }

    private void redoBrowserEdit() {
        if (redoHistory.isEmpty()) return;
        BrowserHistoryEntry edit = redoHistory.removeLast();
        if (restoreBrowserEdit(edit, false)) {
            undoHistory.addLast(edit);
            new Notification("Redo", edit.label(), Notification.Type.SUCCESS);
        } else {
            redoHistory.addLast(edit);
        }
    }

    private boolean restoreBrowserEdit(BrowserHistoryEntry edit, boolean before) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) return false;
        ReSyncProjectMetadata target = gson.fromJson(before ? edit.beforeMetadata() : edit.afterMetadata(), ReSyncProjectMetadata.class);
        Map<String, ResourceSnapshot> targetSnapshots = before ? edit.beforeResources() : edit.afterResources();
        if (!applyBrowserState(manager, target, targetSnapshots, edit.affectedKeys())) return false;
        Map<String, ReSyncProjectMetadata.ResourceEntry> targetEntries = resourceEntries(target);
        screen.studioDocuments.removeIf(document -> edit.affectedKeys().contains(document.key()) && !targetEntries.containsKey(document.key()));
        screen.syncStudioDocumentTabs();
        selectedResource = null;
        selectedFolder = null;
        selectedProjectRoot = false;
        rebuild();
        return true;
    }

    private boolean applyBrowserState(FlowManager manager, ReSyncProjectMetadata target, Map<String, ResourceSnapshot> targetSnapshots, Set<String> affectedKeys) {
        ReSyncProjectMetadata current = gson.fromJson(gson.toJson(manager.getProjectMetadata(screen.studioServerId())), ReSyncProjectMetadata.class);
        Map<String, ReSyncProjectMetadata.ResourceEntry> currentEntries = resourceEntries(current);
        Map<String, ReSyncProjectMetadata.ResourceEntry> targetEntries = resourceEntries(target);
        Map<String, ResourceSnapshot> currentSnapshots = new LinkedHashMap<>();
        for (String key : affectedKeys) {
            ReSyncProjectMetadata.ResourceEntry entry = currentEntries.get(key);
            if (entry == null) continue;
            ResourceSnapshot snapshot = snapshotResource(manager, entry);
            if (snapshot == null) return false;
            currentSnapshots.put(key, snapshot);
        }
        StudioMutationTransaction transaction = new StudioMutationTransaction();
        for (String key : affectedKeys) {
            ReSyncProjectMetadata.ResourceEntry currentEntry = currentEntries.get(key);
            ReSyncProjectMetadata.ResourceEntry targetEntry = targetEntries.get(key);
            ResourceSnapshot currentSnapshot = currentSnapshots.get(key);
            ResourceSnapshot targetSnapshot = targetSnapshots.get(key);
            if (currentEntry != null && targetEntry == null) {
                transaction.add(() -> deleteResource(manager, currentEntry), () -> restoreResource(manager, currentSnapshot));
            } else if (targetEntry != null && targetSnapshot != null) {
                transaction.add(
                    () -> restoreResource(manager, targetSnapshot),
                    () -> currentSnapshot != null ? restoreResource(manager, currentSnapshot) : deleteResource(manager, targetEntry)
                );
            } else if (targetEntry != null) {
                return false;
            }
        }
        transaction.add(
            () -> saveProjectMetadata(manager, target),
            () -> saveProjectMetadata(manager, current)
        );
        return transaction.execute();
    }

    private boolean saveProjectMetadata(FlowManager manager, ReSyncProjectMetadata metadata) {
        manager.saveProjectMetadata(screen.studioServerId(), metadata);
        return true;
    }

    @Override
    public boolean textInput(ReTextInputEvent event) {
        if (temporarilyHidden) {
            return false;
        }
        if (sidePanel != null && sidePanel.textInput(event.retarget(sidePanel))) {
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        updateButton.setVisible(screen.hasReSyncUpdateAvailable() && !screen.isReSyncUpdateRunning());
        updateContainers();
    }

    @Override
    public void setPosition(int x, int y) {
        super.setPosition(x, y);
        updateContainers();
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        updateContainers();
    }

    public void rebuild() {
        rebuild(null);
    }

    private void rebuild(Path revealPath) {
        List<ReSyncProjectMetadata.FolderEntry> folders = screen.studioAllFolders();
        List<ReSyncProjectMetadata.ResourceEntry> resources = screen.studioAllResources();
        rebuildResourceIconPaths(resources);
        AssetBrowserSnapshot snapshot = assetBrowserSnapshot(folders, resources);
        if (snapshot.equals(lastAssetBrowserSnapshot)) {
            if (revealPath != null) {
                treeExplorer.expandToPath(revealPath);
            }
            return;
        }
        lastAssetBrowserSnapshot = snapshot;
        rebuildTree(folders, resources, revealPath);
    }

    private void rebuildResourceIconPaths(List<ReSyncProjectMetadata.ResourceEntry> resources) {
        resourceIconPaths.clear();
        FlowManager manager = FlowManager.getInstance();
        Map<String, CustomContentDefinition> customContent = manager != null ? manager.getCustomContentForServer(screen.studioServerId()) : Map.of();
        for (ReSyncProjectMetadata.ResourceEntry resource : resources) {
            String iconPath = ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(resource.getType())
                ? customContentIconPath(customContent.get(resource.getId()))
                : screen.studioResourceIconPath(resource.getType(), resource.getId());
            resourceIconPaths.put(resource.key(), iconPath);
        }
    }

    private AssetBrowserSnapshot assetBrowserSnapshot(List<ReSyncProjectMetadata.FolderEntry> folders, List<ReSyncProjectMetadata.ResourceEntry> resources) {
        List<String> folderSnapshots = new ArrayList<>();
        for (ReSyncProjectMetadata.FolderEntry folder : folders) {
            folderSnapshots.add(String.join("\u0001",
                folder.getPath(),
                folder.getParentPath(),
                folder.getName(),
                String.valueOf(folder.getSortOrder()),
                String.valueOf(folder.isCollapsed())));
        }
        Collections.sort(folderSnapshots);
        List<String> resourceSnapshots = new ArrayList<>();
        for (ReSyncProjectMetadata.ResourceEntry resource : resources) {
            resourceSnapshots.add(String.join("\u0001",
                resource.getType(),
                resource.getId(),
                resource.getDisplayName(),
                resource.getPath(),
                String.valueOf(resource.getSortOrder()),
                iconPathFor(resource)));
        }
        Collections.sort(resourceSnapshots);
        return new AssetBrowserSnapshot(folderSnapshots, resourceSnapshots);
    }

    private void rebuildTree(List<ReSyncProjectMetadata.FolderEntry> folders, List<ReSyncProjectMetadata.ResourceEntry> resources) {
        rebuildTree(folders, resources, null);
    }

    private void rebuildTree(List<ReSyncProjectMetadata.FolderEntry> folders, List<ReSyncProjectMetadata.ResourceEntry> resources, Path revealPath) {
        List<Path> expandedPaths = new ArrayList<>(treeExplorer.getExpandedDirectories());
        treeProvider.rebuild(folders, resources);
        if (revealPath != null && treeProvider.folderPath(revealPath) != null && !expandedPaths.contains(revealPath)) {
            expandedPaths.add(revealPath);
        }
        boolean expandAll = !treeInitialized;
        browser.setWorkspace(projectRoot, treeProvider, expandAll, expandedPaths);
        treeInitialized = true;
    }

    private void rebuildCurrentFolderView() {
        List<ReSyncProjectMetadata.FolderEntry> folders = screen.studioAllFolders();
        List<ReSyncProjectMetadata.ResourceEntry> resources = screen.studioAllResources();
        rebuildResourceIconPaths(resources);
        rebuildTree(folders, resources);
    }

    private void selectFolder(String path) {
        selectFolder(path, true);
    }

    private void selectFolder(String path, boolean recordHistory) {
        String normalizedPath = ReSyncProjectMetadata.normalizePath(path);
        if (recordHistory && !Objects.equals(normalizedPath, currentFolder)) {
            backHistory.push(currentFolder);
            forwardHistory.clear();
        }
        currentFolder = normalizedPath;
        nameInput.setText("");
        selectedResource = null;
        selectedFolder = null;
        selectedProjectRoot = false;
        rebuildCurrentFolderView();
    }

    private void navigateHistoryBack() {
        if (!backHistory.isEmpty()) {
            forwardHistory.push(currentFolder);
            selectFolder(backHistory.pop(), false);
            return;
        }
        String parentFolder = parentFolder(currentFolder);
        if (parentFolder != null) {
            selectFolder(parentFolder, false);
        }
    }

    private void navigateHistoryForward() {
        if (forwardHistory.isEmpty()) {
            return;
        }
        backHistory.push(currentFolder);
        selectFolder(forwardHistory.pop(), false);
    }

    private String parentFolder(String path) {
        String normalizedPath = ReSyncProjectMetadata.normalizePath(path);
        if (normalizedPath.isBlank()) {
            return null;
        }
        int separator = normalizedPath.lastIndexOf('/');
        return separator >= 0 ? normalizedPath.substring(0, separator) : "";
    }

    private void openTreeFile(Path path) {
        ReSyncProjectMetadata.ResourceEntry resource = treeProvider.resource(path);
        if (resource == null) {
            return;
        }
        selectedResource = resource;
        selectedFolder = null;
        selectedProjectRoot = false;
        nameInput.setText(resource.getId());
        screen.openStudioResource(resource);
    }

    private void openTreeNode(WorkspaceTreeExplorer.NodeRef ref) {
        if (ref == null) {
            return;
        }
        if (ref.directory()) {
            String path = treeProvider.folderPath(ref.path());
            if (path != null) {
                selectFolder(path);
            }
            return;
        }
        openTreeFile(ref.path());
    }

    private void activateTreeNode(WorkspaceTreeExplorer.NodeRef ref) {
        if (ref == null) {
            return;
        }
        if (ref.directory()) {
            selectedResource = null;
            selectedFolder = treeProvider.folder(ref.path());
            selectedProjectRoot = projectRoot.equals(ref.path());
            if (selectedFolder != null) {
                nameInput.setText(selectedFolder.getName());
            } else if (projectRoot.equals(ref.path())) {
                nameInput.setText("");
            }
            return;
        }
        ReSyncProjectMetadata.ResourceEntry resource = treeProvider.resource(ref.path());
        if (resource != null) {
            selectedResource = resource;
            selectedFolder = null;
            selectedProjectRoot = false;
            nameInput.setText(resource.getId());
        }
    }

    private void startResourceDrag(WorkspaceTreeExplorer.NodeRef ref) {
        if (ref == null || ref.directory()) {
            return;
        }
        ReSyncProjectMetadata.ResourceEntry resource = treeProvider.resource(ref.path());
        if (resource == null) {
            return;
        }
        FileEntryWidget source = treeContainer.getWidgets().stream()
            .filter(FileEntryWidget.class::isInstance)
            .map(FileEntryWidget.class::cast)
            .filter(entry -> ref.path().equals(entry.getFileEntry().path))
            .findFirst()
            .orElse(null);
        if (source == null) {
            return;
        }
        FileEntryWidget transition = new FileEntryWidget.Builder(source.getFileEntry(), treeProvider, Collections.emptyList(), new Object())
            .pos(source.getX(), source.getY())
            .size(source.getWidth(), source.getHeight())
            .minimal(true)
            .treeRow(true)
            .entranceAnimation(false)
            .animateElevation(false)
            .transparent(true)
            .animateLayout(true)
            .animateLayoutPosition(false)
            .build();
        transition.setSelected(true);
        transition.setRelativeScissor(0, 0, 0, 0);
        transition.snapLayout();
        screen.beginStudioResourceDrag(
            new ReSyncResourceDragPayload(resource.getType(), resource.getId(), resource.getDisplayName(), resource.getPath()),
            transition,
            Math.clamp(lastMouseX - source.getX(), 0, source.getWidth()),
            Math.clamp(lastMouseY - source.getY(), 0, source.getHeight())
        );
    }

    private void rightClickTreeNode(WorkspaceTreeExplorer.NodeRef ref) {
        selectedFolder = null;
        selectedResource = null;
        selectedProjectRoot = false;
        if (ref != null && ref.directory()) {
            selectedFolder = treeProvider.folder(ref.path());
            if (selectedFolder != null) {
                nameInput.setText(selectedFolder.getName());
            } else if (projectRoot.equals(ref.path())) {
                selectedProjectRoot = true;
                nameInput.setText("");
            }
        } else if (ref != null) {
            selectedResource = treeProvider.resource(ref.path());
            if (selectedResource != null) {
                nameInput.setText(selectedResource.getId());
            }
        }
        showExplorerMenu(lastMouseX, lastMouseY);
    }

    private boolean showExplorerMenu(int mouseX, int mouseY) {
        BrowserSelection selection = browserSelection();
        if (selection.size() == 0 && !selection.projectRoot()) {
            return false;
        }
        String targetFolder = selectionDestination(selection);
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(screen);
        if (selection.size() > 0) {
            builder.addHeaderButton("copy.png", this::copySelected, "Copy")
                .addHeaderButton("cut.png", this::cutSelected, "Cut");
        }
        if (clipboard != null && targetFolder != null) {
            builder.addHeaderButton("paste.png", this::pasteSelected, "Paste");
        }
        if (selection.selectedCount() == 1 && canRename(selection)) {
            builder.addHeaderButton("edit.png", this::renameSelected, "Rename");
        }
        if (canDelete(selection)) {
            builder.addHeaderButton("delete.png", this::deleteSelected, "Delete", ThemeManager.getAccent("danger"));
        }
        if (targetFolder != null) {
            builder.addIconItem("Create", "add.png", () -> ScreenManager.getInstance().execute(() -> showCreateMenu(mouseX + 12, mouseY, targetFolder)), "Create");
        }
        screen.showStudioContextMenu(mouseX, mouseY, builder);
        return true;
    }

    private void showCreateMenu() {
        showCreateMenu(createButton.getX(), createButton.getY() + createButton.getHeight() + 2, createTargetFolder());
    }

    private void showCreateMenu(int mouseX, int mouseY, String targetFolder) {
        closeCreateSelector();
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        var overlay = ScreenManager.getInstance().getPopupOverlay();
        ItemSelectorWidget selector = addCreateSelectorItems(new ItemSelectorWidget.Builder(overlay), targetFolder)
            .size(220, 260)
            .entryHeight(18)
            .searchPlaceholder("Search Actions")
            .emptyMessage("No Actions")
            .usageScope("resync-content-browser-create")
            .dismissOnSelect(true)
            .onClose(() -> closeCreateSelector(selectorRef[0]))
            .build();
        selector.setLayer(900);
        selector.setPriority(30);
        selectorRef[0] = selector;
        createSelector = selector;
        overlay.addDrawableChild(selector);
        selector.show(mouseX, mouseY);
    }

    private ItemSelectorWidget.Builder addCreateSelectorItems(ItemSelectorWidget.Builder builder, String targetFolder) {
        return builder
            .addItem("New Folder", "folder.png", "Create Folder", "folder directory", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FOLDER, targetFolder))
            .addItem("New Flow", "graph.png", "Create Flow", "flow graph", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FLOW, targetFolder))
            .addItem("New Function", "snippets.png", "Create Function", "function mcfunction", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FUNCTION, targetFolder))
            .addItem("New Command", "terminal.png", "Create Command", "command terminal", () -> showCreateResourcePopup(ReSyncResourceDragPayload.COMMAND, targetFolder))
            .addItem("New Content", "resources.png", "Create Content", "content item block armor", () -> showCreateResourcePopup(ReSyncResourceDragPayload.CUSTOM_CONTENT, targetFolder))
            .addItem("New GUI", "fullPanel.png", "Create GUI", "gui interface inventory", () -> showCreateResourcePopup(ReSyncResourceDragPayload.GUI, targetFolder))
            .addItem("New Scoreboard", "panel.png", "Create Scoreboard", "scoreboard sidebar", () -> showCreateResourcePopup(ReSyncResourceDragPayload.SCOREBOARD, targetFolder))
            .addItem("New Tab", "topPanel.png", "Create Tab", "tab player list", () -> showCreateResourcePopup(ReSyncResourceDragPayload.TAB, targetFolder))
            .addItem("New Chat", "chat.png", "Create Chat", "chat format", () -> showCreateResourcePopup(ReSyncResourceDragPayload.CHAT, targetFolder))
            .addItem("New MOTD", "hi.png", "Create MOTD", "motd server list", () -> showCreateResourcePopup(ReSyncResourceDragPayload.MOTD_PROFILE, targetFolder))
            .addItem("New Message Rule", "edit.png", "Create Message Rule", "message rule", () -> showCreateResourcePopup(ReSyncResourceDragPayload.MESSAGE_RULE, targetFolder))
            .addItem("New Recipe", "crafting.png", "Create Recipe", "recipe crafting", () -> showCreateResourcePopup(ReSyncResourceDragPayload.RECIPE_DEFINITION, targetFolder))
            .addItem("New Advancement", "advancement.png", "Create Advancement", "advancement achievement", () -> showCreateResourcePopup(ReSyncResourceDragPayload.ADVANCEMENT_TREE, targetFolder))
            .addItem("New Dialog", "VanillaButton.png", "Create Dialog", "dialog dialogue", () -> showCreateResourcePopup(ReSyncResourceDragPayload.DIALOG, targetFolder))
            .addItem("New Trade", "trade.png", "Create Trade", "trade profile merchant", () -> showCreateResourcePopup(ReSyncResourceDragPayload.TRADE_PROFILE, targetFolder))
            .addItem("New NPC", "steve.png", "Create NPC", "npc entity", () -> showCreateResourcePopup(ReSyncResourceDragPayload.NPC_DEFINITION, targetFolder))
            .addItem("New Loot Table", "resources.png", "Create Loot Table", "loot table drops", () -> showCreateResourcePopup(ReSyncResourceDragPayload.LOOT_TABLE, targetFolder))
            .addItem("New Text", "text.png", "Create Text", "text template", () -> showCreateResourcePopup(ReSyncResourceDragPayload.TEXT_TEMPLATE, targetFolder))
            .addItem("New World", "earth.png", "Create World", "world level", () -> showCreateWorldPopup(targetFolder))
            .addItem("Import Worlds", "download.png", "Import Worlds", "import existing worlds", () -> {
                FlowManager manager = FlowManager.getInstance();
                if (manager != null) {
                    manager.importWorlds(screen.studioServerId());
                }
            })
            .addItem("Scan Worlds", "search.png", "Scan Worlds", "scan discover worlds", () -> {
                FlowManager manager = FlowManager.getInstance();
                if (manager != null) {
                    manager.scanWorlds(screen.studioServerId());
                }
            })
            .addItem("New WorldGen", "map.png", "Create WorldGen", "worldgen world generation", () -> showCreateResourcePopup(ReSyncResourceDragPayload.WORLDGEN, targetFolder));
    }

    private void closeCreateSelector() {
        closeCreateSelector(createSelector);
    }

    private void closeCreateSelector(ItemSelectorWidget selector) {
        if (selector != null) {
            selector.onClose = null;
            selector.hide();
            ScreenManager.getInstance().getPopupOverlay().remove(selector);
        }
        if (selector == createSelector) {
            createSelector = null;
        }
        screen.clearStudioFocus();
    }

    private void showCreateWorldPopup(String targetFolder) {
        WorldResourceCreator.showCreatePopup(screen, screen.studioServerId(), targetFolder, worldName -> {
            rebuild(pathForFolder(targetFolder));
            screen.openStudioWorldDocument(worldName, worldName);
        });
    }

    private void showCreateResourcePopup(String type) {
        showCreateResourcePopup(type, selectedCreateTargetFolder());
    }

    private void showCreateResourcePopup(String type, String targetFolder) {
        if (ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(type)) {
            showCreateContentPopup(targetFolder);
            return;
        }
        ReSyncResourceCreator.showCreatePopup(screen, screen.studioServerId(), type, targetFolder, null, result -> openCreatedResource(result, targetFolder));
    }

    private void showCreateContentPopup(String targetFolder) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Create Content")
            .setResizable(false)
            .onClose(this::closeCreateContentSearchSelector);
        TextInputWidget nameInput = new TextInputWidget.Builder()
            .placeholder("Content Name")
            .size(240, 22)
            .build();
        TextInputWidget idInput = new TextInputWidget.Builder()
            .placeholder("Content ID (e.g. fire_sword)")
            .size(240, 22)
            .build();
        String[] selectedType = {"item"};
        String[] selectedProvider = {"vanilla"};
        String[] selectedAsset = {defaultContentMaterial(selectedType[0])};
        requestContentAssetCatalogs(selectedType[0], selectedProvider[0]);
        AnimatedButton assetButton = new AnimatedButton.Builder()
            .label(selectedAsset[0])
            .size(220, 20)
            .entranceAnimation(false)
            .build();
        DropDownWidget<String> typeDropdown = createContentDropdown(List.of("item", "armor", "block", "projectile"), selectedType[0], value -> {
            selectedType[0] = value;
            selectedAsset[0] = "vanilla".equalsIgnoreCase(selectedProvider[0]) ? defaultContentMaterial(value) : "";
            assetButton.setMessage(assetButtonLabel(selectedAsset[0], selectedProvider[0]));
            requestContentAssetCatalogs(selectedType[0], selectedProvider[0]);
        });
        DropDownWidget<String> providerDropdown = createContentDropdown(providerOptions(), selectedProvider[0], value -> {
            selectedProvider[0] = value;
            selectedAsset[0] = "vanilla".equalsIgnoreCase(value) ? defaultContentMaterial(selectedType[0]) : "";
            assetButton.setMessage(assetButtonLabel(selectedAsset[0], value));
            requestContentAssetCatalogs(selectedType[0], selectedProvider[0]);
        });
        assetButton.setAction(() -> {
            List<String> options = contentAssetOptions(selectedType[0], selectedProvider[0]);
            showCreateContentSearchSelector(options, selectedAsset[0], value -> {
                if (!isRealContentOption(value)) {
                    return;
                }
                selectedAsset[0] = "vanilla".equalsIgnoreCase(selectedProvider[0]) ? value.toUpperCase(Locale.ROOT) : value;
                assetButton.setMessage(assetButtonLabel(selectedAsset[0], selectedProvider[0]));
            }, assetButton.getX(), assetButton.getY() + assetButton.getHeight(), selectedType[0], selectedProvider[0]);
        });
        builder.addRow("Name", true, 22, nameInput);
        builder.addRow("ID", true, 22, idInput);
        builder.addRow("Type", true, 22, typeDropdown);
        builder.addRow("Provider", true, 22, providerDropdown);
        builder.addRow("Asset", true, 22, assetButton);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton createButton = new AnimatedButton.Builder()
            .label("Create")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String id = idInput.getText() != null ? idInput.getText().trim() : "";
                String name = nameInput.getText() != null ? nameInput.getText().trim() : "";
                if (!id.matches("^[a-zA-Z0-9_]+$")) {
                    new Notification("Error", "Invalid ID. Alphanumeric only.", Notification.Type.ERROR);
                    return;
                }
                if (name.isBlank()) {
                    name = id;
                }
                if (createContentResource(id, name, selectedType[0], selectedProvider[0], selectedAsset[0], targetFolder)) {
                    closeCreateContentSearchSelector();
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                }
            })
            .build();
        builder.addRow("", true, 20, createButton);
        popupRef[0] = builder.build();
        screen.addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private DropDownWidget<String> createContentDropdown(List<String> options, String selected, Consumer<String> onSelected) {
        List<String> safeOptions = options == null || options.isEmpty() ? List.of("No Options") : options.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return new DropDownWidget.Builder<>(safeOptions)
            .displayFunction(this::contentOptionLabel)
            .selectedItem(safeOptions.contains(selected) ? selected : safeOptions.getFirst())
            .onSelectionChanged(value -> {
                if (isRealContentOption(value)) {
                    onSelected.accept(value);
                }
            })
            .size(110, 20)
            .maxVisibleItems(8)
            .entranceAnimation(false)
            .build();
    }

    private String contentOptionLabel(String value) {
        if (value == null || value.isBlank()) {
            return "None";
        }
        String cleaned = value.trim().replace("minecraft:", "").replace('_', ' ').replace('-', ' ');
        StringBuilder builder = new StringBuilder();
        for (String part : cleaned.split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.isEmpty() ? value : builder.toString();
    }

    private boolean isRealContentOption(String value) {
        return value != null && !"Loading".equals(value) && !"No Options".equals(value);
    }

    private void showCreateContentSearchSelector(List<String> options, String selected, Consumer<String> onSelected, int x, int y,
        String type, String provider) {
        closeCreateContentSearchSelector();
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        var overlay = ScreenManager.getInstance().getPopupOverlay();
        String source = "vanilla".equalsIgnoreCase(provider) ? "server:minecraft:material" : "server:custom_content:asset";
        Map<String, Object> context = "vanilla".equalsIgnoreCase(provider) ? Map.of() : customContentCatalogContext(type, provider);
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(overlay)
            .size(220, 240)
            .dismissOnSelect(true)
            .emptyMessage("No Assets")
            .asyncItems(OptionCatalogSelector.refreshAction(screen.studioServerId(), source, context),
                () -> OptionCatalogSelector.snapshot(screen.studioServerId(), source, context, () -> options, () -> selected, onSelected, "No Assets"))
            .onClose(() -> closeCreateContentSearchSelector(selectorRef[0]))
            .build();
        selector.setLayer(900);
        selector.setPriority(30);
        selectorRef[0] = selector;
        selector.setSelectedItem(OptionCatalogSelector.label(screen.studioServerId(), source, context, selected));
        createContentSelector = selector;
        overlay.addDrawableChild(createContentSelector);
        int selectorX = Math.clamp(x, 8, Math.max(8, screen.screenWidth() - selector.getWidth() - 8));
        int selectorY = Math.clamp(y, 32, Math.max(32, screen.screenHeight() - selector.getHeight() - 20));
        createContentSelector.show(selectorX, selectorY);
    }

    private void closeCreateContentSearchSelector() {
        closeCreateContentSearchSelector(createContentSelector);
    }

    private void closeCreateContentSearchSelector(ItemSelectorWidget selector) {
        if (selector != null) {
            selector.onClose = null;
            selector.hide();
            ScreenManager.getInstance().getPopupOverlay().remove(selector);
        }
        if (selector == createContentSelector) {
            createContentSelector = null;
        }
        screen.clearStudioFocus();
    }

    private List<String> providerOptions() {
        Set<String> providers = new LinkedHashSet<>();
        providers.add("vanilla");
        providers.addAll(catalogOptions("server:custom_content:provider"));
        return new ArrayList<>(providers);
    }

    private List<String> contentAssetOptions(String type, String provider) {
        if ("vanilla".equalsIgnoreCase(provider)) {
            return catalogOptions("server:minecraft:material");
        }
        return catalogOptions("server:custom_content:asset", customContentCatalogContext(type, provider));
    }

    private List<String> catalogOptions(String source) {
        return catalogOptions(source, Map.of());
    }

    private List<String> catalogOptions(String source, Map<String, Object> context) {
        return OptionCatalogLoader.snapshot(screen.studioServerId(), source, context).values();
    }

    private void requestContentAssetCatalogs(String type, String provider) {
        if ("vanilla".equalsIgnoreCase(provider)) {
            requestCatalog("server:minecraft:material");
            return;
        }
        requestCatalog("server:custom_content:asset", customContentCatalogContext(type, provider));
    }

    private void requestCatalog(String source) {
        requestCatalog(source, Map.of());
    }

    private void requestCatalog(String source, Map<String, Object> context) {
        OptionCatalogLoader.preload(screen.studioServerId(), source, context);
    }

    private Map<String, Object> customContentCatalogContext(String type, String provider) {
        return Map.of(
            "provider", provider != null ? provider : "",
            "content_type", type != null ? type : ""
        );
    }

    private String defaultContentMaterial(String type) {
        return switch (type) {
            case "block" -> "STONE";
            case "armor" -> "IRON_CHESTPLATE";
            case "projectile" -> "ARROW";
            default -> "STICK";
        };
    }

    private String assetButtonLabel(String asset, String provider) {
        if (asset != null && !asset.isBlank()) {
            return asset;
        }
        return "vanilla".equalsIgnoreCase(provider) ? "Material" : "External ID";
    }

    private void openCreatedResource(ReSyncResourceCreator.Result result, String targetFolder) {
        if (result == null) {
            return;
        }
        String type = result.type();
        String id = result.id();
        Object resource = result.resource();
        rebuild(pathForFolder(targetFolder));
        switch (type) {
            case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION, ReSyncResourceDragPayload.COMMAND -> {
                if (resource instanceof FlowGraph graph) {
                    screen.openStudioGraphDocument(type, id, id, graph);
                }
            }
            case ReSyncResourceDragPayload.GUI, ReSyncResourceDragPayload.SCOREBOARD, ReSyncResourceDragPayload.TAB, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                 ReSyncResourceDragPayload.DIALOG, ReSyncResourceDragPayload.TRADE_PROFILE, ReSyncResourceDragPayload.NPC_DEFINITION,
                 ReSyncResourceDragPayload.LOOT_TABLE -> screen.openStudioDesigner(type, id);
            case ReSyncResourceDragPayload.CHAT, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                 ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE -> {
                if (resource instanceof JsonObject json) {
                    screen.openFocusedResourceDocument(type, id, id, json);
                }
            }
            case ReSyncResourceDragPayload.WORLDGEN -> {
                if (resource instanceof WorldGenProject project) {
                    screen.openStudioWorldGenDocument(id, id, project);
                }
            }
            case ReSyncResourceDragPayload.WORLD -> screen.openStudioWorldDocument(id, id);
            default -> {
            }
        }
    }

    private boolean createContentResource(String id, String name, String contentType, String provider, String asset, String targetFolder) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return false;
        }
        String normalizedTargetFolder = ReSyncProjectMetadata.normalizePath(targetFolder);
        if (manager.getProjectMetadata(screen.studioServerId()).findResource(ReSyncResourceDragPayload.CUSTOM_CONTENT, id) != null || resourceExists(manager, ReSyncResourceDragPayload.CUSTOM_CONTENT, id)) {
            new Notification("Error", "Content ID already exists", Notification.Type.ERROR);
            return false;
        }
        String requestedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String normalizedType = switch (requestedType) {
            case "block", "armor", "projectile" -> requestedType;
            default -> "item";
        };
        String selectedProvider = provider == null || provider.isBlank() ? "vanilla" : provider;
        FlowGraph contentGraph = manager.createContentFlow(screen.studioServerId(), id, normalizedType, name);
        CustomContentGraphAdapter.setContentProperty(contentGraph, "content_id", id);
        CustomContentGraphAdapter.setContentProperty(contentGraph, "name", name);
        CustomContentGraphAdapter.setContentProperty(contentGraph, "provider", selectedProvider);
        if ("vanilla".equalsIgnoreCase(selectedProvider)) {
            CustomContentGraphAdapter.setContentProperty(contentGraph, "material", asset == null || asset.isBlank() ? defaultContentMaterial(normalizedType) : asset.toUpperCase(Locale.ROOT));
            CustomContentGraphAdapter.setContentProperty(contentGraph, "external_id", "");
        } else {
            CustomContentGraphAdapter.setContentProperty(contentGraph, "material", defaultContentMaterial(normalizedType));
            CustomContentGraphAdapter.setContentProperty(contentGraph, "external_id", asset == null ? "" : asset);
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(screen.studioServerId());
        ReSyncProjectMetadata.ResourceEntry entry = metadata.ensureResource(ReSyncResourceDragPayload.CUSTOM_CONTENT, id, name, normalizedTargetFolder);
        entry.setPath(normalizedTargetFolder);
        manager.saveProjectMetadata(screen.studioServerId(), metadata);
        rebuild(pathForFolder(normalizedTargetFolder));
        screen.openStudioViewDocument(ReSyncResourceDragPayload.CUSTOM_CONTENT, id, name, contentGraph, new ScreenBackedStudioView(screen, new ContentDesignerScreen(screen.studioServerId(), contentGraph, screen)));
        return true;
    }

    private String createTargetFolder() {
        String destination = selectionDestination(browserSelection());
        if (destination != null) return destination;
        return ReSyncProjectMetadata.normalizePath(currentFolder);
    }

    private String selectedCreateTargetFolder() {
        return createTargetFolder();
    }

    private BrowserSelection browserSelection() {
        Map<String, ReSyncProjectMetadata.ResourceEntry> resources = new LinkedHashMap<>();
        Map<String, ReSyncProjectMetadata.FolderEntry> folders = new LinkedHashMap<>();
        boolean rootSelected = false;
        List<WorkspaceTreeExplorer.NodeRef> selectedRefs = treeExplorer.selectedNodeRefs();
        for (WorkspaceTreeExplorer.NodeRef ref : selectedRefs) {
            if (ref.directory()) {
                ReSyncProjectMetadata.FolderEntry folder = treeProvider.folder(ref.path());
                if (folder != null) folders.put(folder.getPath(), folder);
                else if (projectRoot.equals(ref.path())) rootSelected = true;
            } else {
                ReSyncProjectMetadata.ResourceEntry resource = treeProvider.resource(ref.path());
                if (resource != null) resources.put(resource.key(), resource);
            }
        }
        if (resources.isEmpty() && folders.isEmpty() && !rootSelected) {
            if (selectedResource != null) resources.put(selectedResource.key(), selectedResource);
            if (selectedFolder != null) folders.put(selectedFolder.getPath(), selectedFolder);
            rootSelected = selectedProjectRoot;
        }
        List<ReSyncProjectMetadata.FolderEntry> rootFolders = new ArrayList<>();
        folders.values().stream().sorted(Comparator.comparingInt(folder -> folder.getPath().length())).forEach(folder -> {
            if (rootFolders.stream().noneMatch(parent -> folder.getPath().startsWith(parent.getPath() + "/"))) rootFolders.add(folder);
        });
        List<ReSyncProjectMetadata.ResourceEntry> rootResources = resources.values().stream()
            .filter(resource -> rootFolders.stream().noneMatch(folder -> resource.getPath().equals(folder.getPath()) || resource.getPath().startsWith(folder.getPath() + "/")))
            .toList();
        int selectedCount = !selectedRefs.isEmpty() ? selectedRefs.size() : rootResources.size() + rootFolders.size() + (rootSelected ? 1 : 0);
        return new BrowserSelection(rootResources, List.copyOf(rootFolders), rootSelected && rootResources.isEmpty() && rootFolders.isEmpty(), selectedCount);
    }

    private String selectionDestination(BrowserSelection selection) {
        if (selection.projectRoot()) return "";
        if (selection.selectedCount() != 1 || selection.size() != 1) return null;
        if (!selection.folders().isEmpty()) return selection.folders().getFirst().getPath();
        return selection.resources().getFirst().getPath();
    }

    private boolean canRename(BrowserSelection selection) {
        if (selection.selectedCount() != 1 || selection.size() != 1) return false;
        if (!selection.folders().isEmpty()) return true;
        return switch (selection.resources().getFirst().getType()) {
            case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION, ReSyncResourceDragPayload.COMMAND,
                 ReSyncResourceDragPayload.CUSTOM_CONTENT, ReSyncResourceDragPayload.GUI, ReSyncResourceDragPayload.SCOREBOARD,
                 ReSyncResourceDragPayload.TAB, ReSyncResourceDragPayload.CHAT, ReSyncResourceDragPayload.MOTD_PROFILE,
                 ReSyncResourceDragPayload.MESSAGE_RULE, ReSyncResourceDragPayload.RECIPE_DEFINITION,
                 ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                 ReSyncResourceDragPayload.DIALOG, ReSyncResourceDragPayload.TRADE_PROFILE,
                 ReSyncResourceDragPayload.NPC_DEFINITION, ReSyncResourceDragPayload.LOOT_TABLE -> true;
            default -> false;
        };
    }

    private boolean canDelete(BrowserSelection selection) {
        if (selection.size() == 0) return false;
        return selectedResources(selection).stream().allMatch(resource -> switch (resource.getType()) {
            case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION, ReSyncResourceDragPayload.COMMAND,
                 ReSyncResourceDragPayload.CUSTOM_CONTENT, ReSyncResourceDragPayload.GUI, ReSyncResourceDragPayload.SCOREBOARD,
                 ReSyncResourceDragPayload.TAB, ReSyncResourceDragPayload.CHAT, ReSyncResourceDragPayload.MOTD_PROFILE,
                 ReSyncResourceDragPayload.MESSAGE_RULE, ReSyncResourceDragPayload.RECIPE_DEFINITION,
                 ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                 ReSyncResourceDragPayload.DIALOG, ReSyncResourceDragPayload.TRADE_PROFILE,
                 ReSyncResourceDragPayload.NPC_DEFINITION, ReSyncResourceDragPayload.LOOT_TABLE,
                 ReSyncResourceDragPayload.WORLDGEN, ReSyncResourceDragPayload.WORLD -> true;
            default -> false;
        });
    }

    private List<ReSyncProjectMetadata.ResourceEntry> selectedResources(BrowserSelection selection) {
        Map<String, ReSyncProjectMetadata.ResourceEntry> resources = new LinkedHashMap<>();
        for (ReSyncProjectMetadata.ResourceEntry resource : selection.resources()) resources.put(resource.key(), resource);
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) return List.copyOf(resources.values());
        for (ReSyncProjectMetadata.ResourceEntry resource : manager.getProjectMetadata(screen.studioServerId()).getResources()) {
            if (selection.folders().stream().anyMatch(folder -> resource.getPath().equals(folder.getPath()) || resource.getPath().startsWith(folder.getPath() + "/"))) {
                resources.put(resource.key(), resource);
            }
        }
        return List.copyOf(resources.values());
    }

    private void copySelected() {
        BrowserClipboard selected = selectedClipboard(false);
        if (selected == null) {
            return;
        }
        clipboard = selected;
    }

    private void cutSelected() {
        BrowserClipboard selected = selectedClipboard(true);
        if (selected == null) {
            return;
        }
        clipboard = selected;
    }

    private BrowserClipboard selectedClipboard(boolean cut) {
        BrowserSelection selection = browserSelection();
        List<String> folders = selection.folders().stream().map(ReSyncProjectMetadata.FolderEntry::getPath).toList();
        List<ClipboardResource> resources = selection.resources().stream()
            .filter(resource -> folders.stream().noneMatch(folder -> resource.getPath().equals(folder) || resource.getPath().startsWith(folder + "/")))
            .map(resource -> new ClipboardResource(resource.getType(), resource.getId(), resource.getPath()))
            .toList();
        return resources.isEmpty() && folders.isEmpty() ? null : new BrowserClipboard(resources, folders, cut);
    }

    private void pasteSelected() {
        if (clipboard == null) {
            new Notification("Paste", "Clipboard Empty", Notification.Type.WARN);
            return;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        BrowserSelection selection = browserSelection();
        String destination = selectionDestination(selection);
        if (destination == null) {
            new Notification("Paste", "Select One Destination", Notification.Type.WARN);
            return;
        }
        BrowserClipboard activeClipboard = clipboard;
        BrowserEditStart edit = beginBrowserEdit(activeClipboard.cut() ? "Move" : "Paste", List.of());
        List<ClipboardResource> failedResources = new ArrayList<>();
        List<String> failedFolders = new ArrayList<>();
        int pasted = 0;
        for (ClipboardResource resource : activeClipboard.resources()) {
            if (pasteResource(manager, resource, destination, activeClipboard.cut())) pasted++;
            else failedResources.add(resource);
        }
        for (String folder : activeClipboard.folderPaths()) {
            if (pasteFolder(manager, folder, destination, activeClipboard.cut())) pasted++;
            else failedFolders.add(folder);
        }
        if (pasted == 0) {
            return;
        }
        if (activeClipboard.cut()) {
            clipboard = failedResources.isEmpty() && failedFolders.isEmpty() ? null : new BrowserClipboard(failedResources, failedFolders, true);
        }
        manager.saveProjectMetadata(screen.studioServerId(), manager.getProjectMetadata(screen.studioServerId()));
        commitBrowserEdit(edit);
        rebuild(pathForFolder(destination));
        new Notification(activeClipboard.cut() ? "Moved" : "Pasted", pasted + " Items", Notification.Type.SUCCESS);
    }

    private boolean pasteResource(FlowManager manager, ClipboardResource source, String destination, boolean cut) {
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(screen.studioServerId());
        ReSyncProjectMetadata.ResourceEntry entry = metadata.findResource(source.type(), source.id());
        if (entry == null) {
            new Notification("Paste", "Resource Missing", Notification.Type.ERROR);
            return false;
        }
        String targetFolder = ReSyncProjectMetadata.normalizePath(destination);
        if (cut) {
            if (entry.getPath().equals(targetFolder)) {
                return true;
            }
            if (folderContainsId(metadata, targetFolder, entry.getId(), entry.key())) {
                new Notification("Move", "ID Exists In Folder", Notification.Type.ERROR);
                return false;
            }
            entry.setPath(targetFolder);
            return true;
        }
        String copyId = nextCopyId(manager, entry.getType(), entry.getId(), targetFolder);
        if (!duplicateResource(manager, entry, copyId)) {
            new Notification("Copy", "Resource Cannot Be Copied", Notification.Type.ERROR);
            return false;
        }
        ReSyncProjectMetadata.ResourceEntry copy = metadata.ensureResource(entry.getType(), copyId, copyId, targetFolder);
        copy.setPath(targetFolder);
        return true;
    }

    private boolean pasteFolder(FlowManager manager, String sourcePath, String destination, boolean cut) {
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(screen.studioServerId());
        ReSyncProjectMetadata.FolderEntry source = metadata.findFolder(sourcePath);
        if (source == null) {
            new Notification("Paste", "Folder Missing", Notification.Type.ERROR);
            return false;
        }
        String targetParent = ReSyncProjectMetadata.normalizePath(destination);
        if (cut && (targetParent.equals(sourcePath) || targetParent.startsWith(sourcePath + "/"))) {
            new Notification("Move", "Choose Another Folder", Notification.Type.ERROR);
            return false;
        }
        if (cut) {
            String oldPath = source.getPath();
            String targetPath = targetParent.isBlank() ? source.getName() : targetParent + "/" + source.getName();
            if (targetPath.equals(oldPath)) {
                return true;
            }
            if (metadata.findFolder(targetPath) != null) {
                new Notification("Move", "Folder Already Exists", Notification.Type.ERROR);
                return false;
            }
            relocateFolder(metadata, oldPath, targetPath, targetParent);
            if (currentFolder.equals(oldPath) || currentFolder.startsWith(oldPath + "/")) {
                currentFolder = targetPath + currentFolder.substring(oldPath.length());
            }
            return true;
        }
        copyFolder(manager, metadata, source, targetParent);
        return true;
    }

    private void relocateFolder(ReSyncProjectMetadata metadata, String oldPath, String newPath, String newParent) {
        for (ReSyncProjectMetadata.FolderEntry folder : metadata.getFolders()) {
            if (folder.getPath().equals(oldPath)) {
                folder.setPath(newPath);
                folder.setParentPath(newParent);
            } else if (folder.getPath().startsWith(oldPath + "/")) {
                folder.setPath(newPath + folder.getPath().substring(oldPath.length()));
            }
            if (folder.getParentPath().equals(oldPath)) {
                folder.setParentPath(newPath);
            } else if (folder.getParentPath().startsWith(oldPath + "/")) {
                folder.setParentPath(newPath + folder.getParentPath().substring(oldPath.length()));
            }
        }
        for (ReSyncProjectMetadata.ResourceEntry resource : metadata.getResources()) {
            if (resource.getPath().equals(oldPath) || resource.getPath().startsWith(oldPath + "/")) {
                resource.setPath(newPath + resource.getPath().substring(oldPath.length()));
            }
        }
    }

    private void copyFolder(FlowManager manager, ReSyncProjectMetadata metadata, ReSyncProjectMetadata.FolderEntry source, String targetParent) {
        String copyName = nextFolderCopyName(metadata, targetParent, source.getName());
        String copyRoot = targetParent.isBlank() ? copyName : targetParent + "/" + copyName;
        String sourceRoot = source.getPath();
        List<ReSyncProjectMetadata.FolderEntry> folders = metadata.getFolders().stream()
            .filter(folder -> folder.getPath().equals(sourceRoot) || folder.getPath().startsWith(sourceRoot + "/"))
            .sorted((left, right) -> Integer.compare(left.getPath().length(), right.getPath().length()))
            .toList();
        for (ReSyncProjectMetadata.FolderEntry folder : folders) {
            String path = copyRoot + folder.getPath().substring(sourceRoot.length());
            String parent = path.equals(copyRoot) ? targetParent : parentFolder(path);
            metadata.ensureFolder(path, parent, metadata.getFolders().size());
        }
        List<ReSyncProjectMetadata.ResourceEntry> resources = metadata.getResources().stream()
            .filter(resource -> resource.getPath().equals(sourceRoot) || resource.getPath().startsWith(sourceRoot + "/"))
            .toList();
        for (ReSyncProjectMetadata.ResourceEntry resource : resources) {
            String folder = copyRoot + resource.getPath().substring(sourceRoot.length());
            String copyId = nextCopyId(manager, resource.getType(), resource.getId(), folder);
            if (duplicateResource(manager, resource, copyId)) {
                ReSyncProjectMetadata.ResourceEntry copy = metadata.ensureResource(resource.getType(), copyId, copyId, folder);
                copy.setPath(folder);
            }
        }
    }

    private boolean duplicateResource(FlowManager manager, ReSyncProjectMetadata.ResourceEntry source, String copyId) {
        if (ReSyncResourceDragPayload.WORLDGEN.equals(source.getType())) {
            WorldGenManager.getInstance().duplicateProject(screen.studioServerId(), source.getId(), copyId, false);
            return true;
        }
        if (ReSyncResourceDragPayload.WORLD.equals(source.getType())) {
            manager.suppressNextWorldSuccessNotification(screen.studioServerId(), "cloneWorld");
            manager.cloneWorld(screen.studioServerId(), source.getId(), copyId, false);
            return true;
        }
        return DesignerSaveNotifications.withoutAutomaticNotifications(() -> manager.duplicateResource(screen.studioServerId(), source.getType(), source.getId(), copyId));
    }

    private String nextCopyId(FlowManager manager, String type, String sourceId, String folder) {
        String base = sourceId + "_copy";
        String candidate = base;
        int suffix = 2;
        while (ReSyncResourceCreator.exists(manager, screen.studioServerId(), type, candidate, folder)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private String nextFolderCopyName(ReSyncProjectMetadata metadata, String parent, String sourceName) {
        String base = sourceName + " Copy";
        String candidate = base;
        int suffix = 2;
        while (metadata.findFolder(parent.isBlank() ? candidate : parent + "/" + candidate) != null) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private boolean folderContainsId(ReSyncProjectMetadata metadata, String folder, String id, String ignoredKey) {
        return metadata.getResources().stream().anyMatch(resource -> resource.getPath().equals(folder) && resource.getId().equals(id) && !resource.key().equals(ignoredKey));
    }

    private boolean resourceExists(FlowManager manager, String type, String id) {
        return ReSyncResourceCreator.exists(manager, screen.studioServerId(), type, id, selectedCreateTargetFolder());
    }

    private String resourceTypeName(String type) {
        return ReSyncResourceCreator.resourceTypeName(type);
    }

    private void renameSelected() {
        BrowserSelection selection = browserSelection();
        if (!canRename(selection)) return;
        selectedFolder = selection.folders().isEmpty() ? null : selection.folders().getFirst();
        selectedResource = selection.resources().isEmpty() ? null : selection.resources().getFirst();
        selectedProjectRoot = false;
        PopupWidget.Builder builder = new PopupWidget.Builder(selectedFolder != null ? "Rename Folder" : "Rename " + resourceTypeName(selectedResource.getType())).setResizable(false);
        TextInputWidget idInput = new TextInputWidget.Builder()
            .text(selectedFolder != null ? selectedFolder.getName() : selectedResource.getId())
            .placeholder(selectedFolder != null ? "Folder Name" : resourceTypeName(selectedResource.getType()) + " ID")
            .size(220, 22)
            .build();
        builder.addRow(selectedFolder != null ? "Name" : "ID", true, 22, idInput);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton saveButton = new AnimatedButton.Builder()
            .label("Save")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String value = idInput.getText() != null ? idInput.getText().trim() : "";
                if (selectedFolder != null) {
                    if (value.isBlank()) {
                        new Notification("Explorer", "Invalid Name", Notification.Type.ERROR);
                        return;
                    }
                } else if (!value.matches("^[a-zA-Z0-9_]+$")) {
                    new Notification("Error", "Invalid ID. Alphanumeric only.", Notification.Type.ERROR);
                    return;
                }
                if (renameSelectedTo(value)) {
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                }
            })
            .build();
        builder.addRow("", true, 20, saveButton);
        popupRef[0] = builder.build();
        screen.addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private boolean renameSelectedTo(String newId) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return false;
        }
        if (selectedFolder != null) {
            return renameSelectedFolder(manager, newId);
        }
        if (selectedResource != null && ReSyncResourceDragPayload.WORLD.equals(selectedResource.getType())) {
            new Notification("World", "World Rename Unsupported", Notification.Type.ERROR);
            return false;
        }
        if (selectedResource == null || newId.isBlank() || selectedResource.getId().equals(newId)) {
            return true;
        }
        if (ReSyncResourceCreator.exists(manager, screen.studioServerId(), selectedResource.getType(), newId, selectedResource.getPath())) {
            new Notification("Error", resourceTypeName(selectedResource.getType()) + " ID already exists", Notification.Type.ERROR);
            return false;
        }
        String oldType = selectedResource.getType();
        String oldId = selectedResource.getId();
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(screen.studioServerId());
        metadata.deduplicateResources();
        ReSyncProjectMetadata.ResourceEntry entry = metadata.findResource(oldType, oldId);
        String oldDisplayName = entry != null ? entry.getDisplayName() : oldId;
        if (entry != null) {
            entry.setId(newId);
            entry.setDisplayName(newId);
            manager.saveProjectMetadata(screen.studioServerId(), metadata, false);
        }
        boolean renamed = switch (selectedResource.getType()) {
            case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION -> manager.renameFlow(screen.studioServerId(), oldId, newId);
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> manager.renameCustomContent(screen.studioServerId(), oldId, newId);
            case ReSyncResourceDragPayload.COMMAND -> renameCommandResource(manager, oldId, newId);
            case ReSyncResourceDragPayload.GUI -> manager.renameGui(screen.studioServerId(), oldId, newId);
            case ReSyncResourceDragPayload.SCOREBOARD -> manager.renameScoreboard(screen.studioServerId(), oldId, newId);
            case ReSyncResourceDragPayload.TAB -> manager.renameTab(screen.studioServerId(), oldId, newId);
            case ReSyncResourceDragPayload.CHAT, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                 ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                 ReSyncResourceDragPayload.DIALOG, ReSyncResourceDragPayload.TRADE_PROFILE, ReSyncResourceDragPayload.NPC_DEFINITION,
                 ReSyncResourceDragPayload.LOOT_TABLE -> {
                ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(selectedResource.getType());
                yield resourceType != null && manager.renameJsonResource(screen.studioServerId(), resourceType, oldId, newId);
            }
            default -> false;
        };
        if (!renamed) {
            if (entry != null) {
                entry.setId(oldId);
                entry.setDisplayName(oldDisplayName);
                manager.saveProjectMetadata(screen.studioServerId(), metadata, false);
            }
            new Notification("Explorer", "Rename Failed", Notification.Type.ERROR);
            return false;
        }
        screen.renameStudioDocument(oldType, oldId, newId);
        manager.saveProjectMetadata(screen.studioServerId(), metadata);
        rebuild();
        return true;
    }

    private void deleteSelected() {
        FlowManager manager = FlowManager.getInstance();
        BrowserSelection selection = browserSelection();
        if (manager == null || !canDelete(selection)) return;
        List<ReSyncProjectMetadata.ResourceEntry> resources = selectedResources(selection);
        List<ReSyncProjectMetadata.ResourceEntry> worlds = resources.stream().filter(resource -> ReSyncResourceDragPayload.WORLD.equals(resource.getType())).toList();
        if (!worlds.isEmpty()) {
            if (worlds.size() == 1 && resources.size() == 1 && selection.folders().isEmpty()) {
                deleteResource(manager, worlds.getFirst());
            } else {
                new Notification("Delete", "Delete Worlds Separately", Notification.Type.ERROR);
            }
            return;
        }
        BrowserEditStart edit = beginBrowserEdit("Delete", resources);
        if (edit == null) return;
        ReSyncProjectMetadata metadata = gson.fromJson(edit.metadata(), ReSyncProjectMetadata.class);
        Set<String> deletedKeys = new HashSet<>(resources.stream().map(ReSyncProjectMetadata.ResourceEntry::key).toList());
        Set<String> deletedFolders = new HashSet<>();
        for (ReSyncProjectMetadata.FolderEntry folder : selection.folders()) {
            deletedFolders.add(folder.getPath());
        }
        metadata.getResources().removeIf(resource -> deletedKeys.contains(resource.key()));
        metadata.getFolders().removeIf(folder -> deletedFolders.stream().anyMatch(path -> folder.getPath().equals(path) || folder.getPath().startsWith(path + "/")));
        if (!applyBrowserState(manager, metadata, Map.of(), deletedKeys)) {
            new Notification("Delete", "Delete Failed", Notification.Type.ERROR);
            return;
        }
        String deletedCurrentFolder = deletedFolders.stream().filter(path -> currentFolder.equals(path) || currentFolder.startsWith(path + "/")).findFirst().orElse(null);
        if (deletedCurrentFolder != null) currentFolder = parentFolder(deletedCurrentFolder);
        commitBrowserEdit(edit);
        screen.studioDocuments.removeIf(document -> deletedKeys.contains(document.key()));
        screen.syncStudioDocumentTabs();
        selectedResource = null;
        selectedFolder = null;
        selectedProjectRoot = false;
        rebuild();
        int deleted = deletedKeys.size() + deletedFolders.size();
        if (deleted > 0) new Notification("Deleted", deleted + " Items", Notification.Type.SUCCESS);
    }

    private boolean deleteResource(FlowManager manager, ReSyncProjectMetadata.ResourceEntry resource) {
        switch (resource.getType()) {
            case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION -> {
                return manager.deleteFlow(screen.studioServerId(), resource.getId());
            }
            case ReSyncResourceDragPayload.COMMAND -> {
                if (!manager.deleteFlow(screen.studioServerId(), resource.getId())) return false;
                manager.clearCommandBinding(screen.studioServerId(), resource.getId());
                return true;
            }
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> manager.deleteCustomContent(screen.studioServerId(), resource.getId());
            case ReSyncResourceDragPayload.GUI -> manager.deleteGui(screen.studioServerId(), resource.getId());
            case ReSyncResourceDragPayload.SCOREBOARD -> manager.deleteScoreboard(screen.studioServerId(), resource.getId());
            case ReSyncResourceDragPayload.TAB -> manager.deleteTab(screen.studioServerId(), resource.getId());
            case ReSyncResourceDragPayload.CHAT, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                 ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                 ReSyncResourceDragPayload.DIALOG, ReSyncResourceDragPayload.TRADE_PROFILE, ReSyncResourceDragPayload.NPC_DEFINITION,
                 ReSyncResourceDragPayload.LOOT_TABLE -> {
                ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(resource.getType());
                if (resourceType == null) return false;
                manager.deleteJsonResource(screen.studioServerId(), resourceType, resource.getId());
            }
            case ReSyncResourceDragPayload.WORLDGEN -> WorldGenManager.getInstance().deleteProject(screen.studioServerId(), resource.getId());
            case ReSyncResourceDragPayload.WORLD -> {
                WorldResourceCreator.showDeletePopup(screen, screen.studioServerId(), resource.getId(), () -> {
                    screen.studioDocuments.removeIf(document -> document.key().equals(resource.key()));
                    screen.syncStudioDocumentTabs();
                    if (selectedResource != null && selectedResource.key().equals(resource.key())) selectedResource = null;
                    rebuild();
                });
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean renameCommandResource(FlowManager manager, String oldId, String newId) {
        String serverId = screen.studioServerId();
        TriggerBinding binding = manager.getCommandBinding(screen.studioServerId(), oldId);
        String context = binding != null ? binding.getContext() : oldId;
        if (!manager.renameFlow(serverId, oldId, newId)) {
            return false;
        }
        manager.clearCommandBinding(serverId, oldId);
        CommandBindingContext command = parseCommandContext(context);
        if (command.command == null || command.command.isBlank() || oldId.equals(command.command)) {
            command.command = newId;
        }
        manager.setCommandBinding(serverId, newId, encodeCommandContext(command));
        return true;
    }

    private CommandBindingContext parseCommandContext(String context) {
        CommandBindingContext parsed = new CommandBindingContext();
        String trimmed = context == null ? "" : context.trim();
        if (trimmed.isBlank()) {
            return parsed;
        }
        if (trimmed.startsWith("{")) {
            try {
                CommandBindingContext decoded = new Gson().fromJson(trimmed, CommandBindingContext.class);
                if (decoded != null) {
                    return decoded;
                }
            } catch (RuntimeException ignored) {
            }
        }
        parsed.command = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        parsed.subcommands = new ArrayList<>();
        parsed.structured = false;
        return parsed;
    }

    private String encodeCommandContext(CommandBindingContext command) {
        return new Gson().toJson(command);
    }

    private boolean renameSelectedFolder(FlowManager manager, String newName) {
        String name = newName == null ? "" : newName.trim();
        if (name.isBlank() || name.contains("/") || name.contains("\\")) {
            new Notification("Explorer", "Invalid Name", Notification.Type.ERROR);
            return false;
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(screen.studioServerId());
        String oldPath = selectedFolder.getPath();
        String parent = selectedFolder.getParentPath();
        String newPath = parent.isBlank() ? name : parent + "/" + name;
        if (!oldPath.equals(newPath) && metadata.findFolder(newPath) != null) {
            new Notification("Explorer", "Folder Already Exists", Notification.Type.ERROR);
            return false;
        }
        for (ReSyncProjectMetadata.FolderEntry folder : metadata.getFolders()) {
            if (folder.getPath().equals(oldPath)) {
                folder.setPath(newPath);
                folder.setName(name);
            } else if (folder.getPath().startsWith(oldPath + "/")) {
                folder.setPath(newPath + folder.getPath().substring(oldPath.length()));
            }
            if (folder.getParentPath().equals(oldPath)) {
                folder.setParentPath(newPath);
            } else if (folder.getParentPath().startsWith(oldPath + "/")) {
                folder.setParentPath(newPath + folder.getParentPath().substring(oldPath.length()));
            }
        }
        for (ReSyncProjectMetadata.ResourceEntry resource : metadata.getResources()) {
            if (resource.getPath().equals(oldPath)) {
                resource.setPath(newPath);
            } else if (resource.getPath().startsWith(oldPath + "/")) {
                resource.setPath(newPath + resource.getPath().substring(oldPath.length()));
            }
        }
        if (currentFolder.equals(oldPath) || currentFolder.startsWith(oldPath + "/")) {
            currentFolder = newPath + currentFolder.substring(oldPath.length());
        }
        manager.saveProjectMetadata(screen.studioServerId(), metadata);
        rebuild();
        return true;
    }

    private String iconPathFor(ReSyncProjectMetadata.ResourceEntry resource) {
        return resourceIconPaths.getOrDefault(resource.key(), screen.studioResourceIconPath(resource.getType(), resource.getId()));
    }

    private String customContentIconPath(CustomContentDefinition content) {
        return switch (content != null && content.getType() != null ? content.getType().toLowerCase(Locale.ROOT) : "") {
            case "armor" -> "armor.png";
            case "block" -> "block.png";
            case "item" -> "item.png";
            case "projectile" -> "item.png";
            default -> "item.png";
        };
    }

    private void updateContainers() {
        if (browser == null) {
            return;
        }
        browser.layout();
    }

    public int browserHeight() {
        return 0;
    }

    public int defaultHeight() {
        return Math.max(120, screen.screenHeight() - STUDIO_CONTENT_BROWSER_TOP - STUDIO_CONTENT_BROWSER_BOTTOM);
    }

    public void resetToDefaultHeight() {
    }

    public void collapse() {
        temporarilyHidden = false;
        if (sidePanel != null) {
            sidePanel.show();
        }
        screen.refreshStudioLayoutPositions();
    }

    public void slideOut() {
        temporarilyHidden = true;
        shortcutFocused = false;
        if (sidePanel != null) {
            sidePanel.hide();
        }
        screen.refreshStudioLayoutPositions();
    }

    public void setTemporarilyHidden(boolean hidden) {
        temporarilyHidden = hidden;
        if (hidden) {
            shortcutFocused = false;
            screen.clearStudioFocus();
        }
        if (sidePanel != null) {
            if (hidden) {
                sidePanel.hideImmediately();
            } else {
                sidePanel.show();
            }
        }
        screen.refreshStudioLayoutPositions();
    }

    public boolean isTemporarilyHidden() {
        return temporarilyHidden;
    }

    public boolean isSlideOutFinished() {
        return sidePanel == null || sidePanel.getAnimatedWidth() <= 1f;
    }

    public boolean isCollapsed() {
        return sidePanel == null || !sidePanel.isVisible();
    }

    public void clampHeight() {
    }

    public void layoutInScreen() {
        setPosition(0, STUDIO_CONTENT_BROWSER_TOP);
        setSize(sidePanel != null ? sidePanel.getDesiredWidth() : STUDIO_CONTENT_BROWSER_DEFAULT_WIDTH, defaultHeight());
        updateContainers();
    }

    public int editorHeight() {
        return screen.screenHeight();
    }

    public int panelBottomReserve() {
        return 0;
    }

    private class ReSyncProjectTreeProvider implements FileSystemProvider {
        private final Map<Path, String> folderPaths = new HashMap<>();
        private final Map<Path, ReSyncProjectMetadata.FolderEntry> folders = new HashMap<>();
        private final Map<Path, ReSyncProjectMetadata.ResourceEntry> resources = new HashMap<>();
        private final Map<Path, List<FileSystemProvider.FileEntry>> entriesByFolder = new HashMap<>();

        private void rebuild(List<ReSyncProjectMetadata.FolderEntry> allFolders, List<ReSyncProjectMetadata.ResourceEntry> allResources) {
            folderPaths.clear();
            folders.clear();
            resources.clear();
            entriesByFolder.clear();
            folderPaths.put(projectRoot, "");
            for (ReSyncProjectMetadata.FolderEntry folder : allFolders) {
                Path path = pathForFolder(folder.getPath());
                folderPaths.put(path, folder.getPath());
                folders.put(path, folder);
                String parentPath = folder.getParentPath();
                Path parent = parentPath.isBlank() ? projectRoot : pathForFolder(parentPath);
                FileSystemProvider.FileEntry entry = new FileSystemProvider.FileEntry(path, true, "-", "", folder.getName());
                entry.metadata.put("icon", "explorer.png");
                entriesByFolder.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(entry);
            }
            for (ReSyncProjectMetadata.ResourceEntry resource : allResources) {
                Path path = pathForResource(resource);
                resources.put(path, resource);
                String folder = ReSyncProjectMetadata.normalizePath(resource.getPath());
                Path parent = folder.isBlank() ? projectRoot : pathForFolder(folder);
                FileSystemProvider.FileEntry entry = new FileSystemProvider.FileEntry(path, false, "", "", resourceBrowserLabel(resource));
                entry.metadata.put("icon", iconPathFor(resource));
                entriesByFolder.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(entry);
            }
            for (List<FileSystemProvider.FileEntry> entries : entriesByFolder.values()) {
                entries.sort((left, right) -> Boolean.compare(!left.isDirectory, !right.isDirectory) != 0
                    ? Boolean.compare(!left.isDirectory, !right.isDirectory)
                    : left.displayName.compareToIgnoreCase(right.displayName));
                for (FileSystemProvider.FileEntry entry : entries) {
                    if (entry.isDirectory) {
                        entry.metadata.put("hasChildren", String.valueOf(!entriesByFolder.getOrDefault(entry.path, List.of()).isEmpty()));
                    }
                }
            }
        }

        private String folderPath(Path path) {
            return folderPaths.get(path);
        }

        private ReSyncProjectMetadata.FolderEntry folder(Path path) {
            return folders.get(path);
        }

        private ReSyncProjectMetadata.ResourceEntry resource(Path path) {
            return resources.get(path);
        }

        @Override
        public CompletableFuture<List<FileSystemProvider.FileEntry>> ls(Path path) {
            String folder = folderPaths.get(path);
            if (folder == null) {
                return CompletableFuture.completedFuture(List.of());
            }
            return CompletableFuture.completedFuture(entriesByFolder.getOrDefault(path, List.of()));
        }

        @Override
        public CompletableFuture<Void> copy(List<Path> sources, Path destination) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> move(List<Path> sources, Path destination) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> delete(List<Path> paths) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<String> read(Path path) {
            return CompletableFuture.completedFuture("");
        }

        @Override
        public CompletableFuture<Void> write(Path path, String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> upload(List<Path> localPaths, Path remotePath) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> download(List<Path> remotePaths, Path localPath) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> rename(Path oldPath, Path newPath) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> createFile(Path path) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> createDirectory(Path path) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> exists(Path path) {
            return CompletableFuture.completedFuture(folderPaths.containsKey(path) || resources.containsKey(path));
        }

        @Override
        public String getMetadata(String key) {
            return switch (key) {
                case "type" -> "RESYNC";
                case "rootIcon" -> "ReSync.png";
                default -> null;
            };
        }
    }

    private Path pathForFolder(String path) {
        if (path == null || path.isBlank()) {
            return projectRoot;
        }
        Path result = projectRoot;
        for (String part : path.split("/")) {
            if (!part.isBlank()) {
                result = result.resolve(part);
            }
        }
        return result;
    }

    private Path pathForResource(ReSyncProjectMetadata.ResourceEntry resource) {
        return pathForFolder(resource.getPath()).resolve(resource.getType()).resolve(resource.getId());
    }

    private String resourceBrowserLabel(ReSyncProjectMetadata.ResourceEntry resource) {
        String id = resource.getId();
        if (id != null && !id.isBlank()) {
            return id;
        }
        return resource.getDisplayName();
    }
}
