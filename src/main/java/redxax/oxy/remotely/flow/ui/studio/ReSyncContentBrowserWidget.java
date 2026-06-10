package redxax.oxy.remotely.flow.ui.studio;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.ui.ContentDesignerScreen;
import redxax.oxy.remotely.packcontent.PackContentRegistry;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.ui.screens.editor.CompactWorkspaceBrowserWidget;
import restudio.rebase.ui.screens.editor.WorkspaceTreeExplorer;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;
import restudio.rescreen.ui.core.ScreenManager;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ReSyncContentBrowserWidget extends AnimatedWidget {
    private final StudioScreen screen;
    private static final int STUDIO_CONTENT_BROWSER_DEFAULT_WIDTH = 190;
    private static final int STUDIO_CONTENT_BROWSER_MIN_WIDTH = 150;
    public static final int STUDIO_CONTENT_BROWSER_TOP = 38;
    public static final int STUDIO_CONTENT_BROWSER_BOTTOM = 8;
    public static final int STUDIO_CONTENT_BROWSER_GAP = 4;
    private static final int STUDIO_CONTENT_BROWSER_ENTRY_HEIGHT = 10;
    private static final int STUDIO_CONTENT_BROWSER_TOOL_SIZE = 16;
    private final Path projectRoot = Path.of("ReSync");
    private String currentFolder = "";
    private final Deque<String> backHistory = new ArrayDeque<>();
    private final Deque<String> forwardHistory = new ArrayDeque<>();
    private ReSyncProjectMetadata.ResourceEntry selectedResource;
    private ReSyncProjectMetadata.FolderEntry selectedFolder;
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
    private ItemSelectorWidget createContentSelector;
    private AssetBrowserSnapshot lastAssetBrowserSnapshot;
    private final Map<String, String> resourceIconPaths = new HashMap<>();
    private boolean treeInitialized;

    private record AssetBrowserSnapshot(List<String> folders, List<String> resources) {
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
        sidePanel.show();
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
        return sidePanel != null && sidePanel.isVisible() ? sidePanel.getDesiredWidth() + 8 : 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = (int) mouseX;
        lastMouseY = (int) mouseY;
        if (handleHistoryMouseButton(button)) {
            return true;
        }
        return sidePanel != null && sidePanel.mouseClicked(mouseX, mouseY, button);
    }

    public boolean handleHistoryMouseButton(int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            navigateHistoryBack();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
            navigateHistoryForward();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (sidePanel != null && sidePanel.mouseReleased(mouseX, mouseY, button)) {
            updateContainers();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (sidePanel != null && sidePanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            updateContainers();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
        if (sidePanel != null && sidePanel.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (sidePanel != null && sidePanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (sidePanel != null && sidePanel.charTyped(chr, modifiers)) {
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
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
            nameInput.setText(resource.getId());
        }
    }

    private void rightClickTreeNode(WorkspaceTreeExplorer.NodeRef ref) {
        selectedFolder = null;
        selectedResource = null;
        if (ref != null && ref.directory()) {
            selectedFolder = treeProvider.folder(ref.path());
            if (selectedFolder != null) {
                nameInput.setText(selectedFolder.getName());
            } else if (projectRoot.equals(ref.path())) {
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
        if (selectedResource == null && selectedFolder == null) {
            return false;
        }
        String targetFolder = selectedCreateTargetFolder();
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(screen)
            .addHeaderButton("edit.png", this::renameSelected, "Rename")
            .addHeaderButton("delete.png", this::deleteSelected, "Delete", ThemeManager.getAccent("danger"))
            .addIconItem("Create", "add.png", () -> ScreenManager.getInstance().execute(() -> showCreateMenu(mouseX + 12, mouseY, targetFolder)), "Create");
        screen.showStudioContextMenu(mouseX, mouseY, builder);
        return true;
    }

    private void showCreateMenu() {
        showCreateMenu(createButton.getX(), createButton.getY() + createButton.getHeight() + 2, createTargetFolder());
    }

    private void showCreateMenu(int mouseX, int mouseY, String targetFolder) {
        ContextMenuWidget.Builder builder = addCreateMenuItems(new ContextMenuWidget.Builder(screen), targetFolder);
        screen.showStudioContextMenu(mouseX, mouseY, builder);
    }

    private ContextMenuWidget.Builder addCreateMenuItems(ContextMenuWidget.Builder builder, String targetFolder) {
        return builder
            .addIconItem("New Folder", "folder.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FOLDER, targetFolder), "Create Folder")
            .addIconItem("New Flow", "graph.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FLOW, targetFolder), "Create Flow")
            .addIconItem("New Function", "snippets.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FUNCTION, targetFolder), "Create Function")
            .addIconItem("New Command", "terminal.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.COMMAND, targetFolder), "Create Command")
            .addIconItem("New Content", "resources.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.CUSTOM_CONTENT, targetFolder), "Create Content")
            .addIconItem("New GUI", "fullPanel.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.GUI, targetFolder), "Create GUI")
            .addIconItem("New Scoreboard", "panel.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.SCOREBOARD, targetFolder), "Create Scoreboard")
            .addIconItem("New Tab", "topPanel.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.TAB, targetFolder), "Create Tab")
            .addIconItem("New Chat", "chat.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.CHAT, targetFolder), "Create Chat")
            .addIconItem("New MOTD", "hi.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.MOTD_PROFILE, targetFolder), "Create MOTD")
            .addIconItem("New Message Rule", "edit.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.MESSAGE_RULE, targetFolder), "Create Message Rule")
            .addIconItem("New Recipe", "crafting.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.RECIPE_DEFINITION, targetFolder), "Create Recipe")
            .addIconItem("New Advancement", "advancement.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.ADVANCEMENT_TREE, targetFolder), "Create Advancement")
            .addIconItem("New Dialog", "VanillaButton.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.DIALOG, targetFolder), "Create Dialog")
            .addIconItem("New Text", "text.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.TEXT_TEMPLATE, targetFolder), "Create Text")
            .addIconItem("New World", "earth.png", () -> showCreateWorldPopup(targetFolder), "Create World")
            .addIconItem("Import Worlds", "download.png", () -> {
                FlowManager manager = FlowManager.getInstance();
                if (manager != null) {
                    manager.importWorlds(screen.studioServerId());
                }
            }, "Import Worlds")
            .addIconItem("Scan Worlds", "search.png", () -> {
                FlowManager manager = FlowManager.getInstance();
                if (manager != null) {
                    manager.scanWorlds(screen.studioServerId());
                }
            }, "Scan Worlds")
            .addIconItem("New WorldGen", "map.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.WORLDGEN, targetFolder), "Create WorldGen");
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
        AnimatedButton assetButton = new AnimatedButton.Builder()
            .label(selectedAsset[0])
            .size(220, 20)
            .entranceAnimation(false)
            .build();
        DropDownWidget<String> typeDropdown = createContentDropdown(List.of("item", "armor", "block"), selectedType[0], value -> {
            selectedType[0] = value;
            selectedAsset[0] = "vanilla".equalsIgnoreCase(selectedProvider[0]) ? defaultContentMaterial(value) : "";
            assetButton.setMessage(assetButtonLabel(selectedAsset[0], selectedProvider[0]));
        });
        DropDownWidget<String> providerDropdown = createContentDropdown(providerOptions(), selectedProvider[0], value -> {
            selectedProvider[0] = value;
            selectedAsset[0] = "vanilla".equalsIgnoreCase(value) ? defaultContentMaterial(selectedType[0]) : "";
            assetButton.setMessage(assetButtonLabel(selectedAsset[0], value));
        });
        assetButton.setAction(() -> {
            List<String> options = contentAssetOptions(selectedType[0], selectedProvider[0]);
            if (options.size() == 1 && "Loading".equals(options.getFirst())) {
                requestContentAssetCatalogs(selectedType[0], selectedProvider[0]);
                return;
            }
            showCreateContentSearchSelector(options, selectedAsset[0], value -> {
                if (!isRealContentOption(value)) {
                    return;
                }
                selectedAsset[0] = "vanilla".equalsIgnoreCase(selectedProvider[0]) ? value.toUpperCase(Locale.ROOT) : value;
                assetButton.setMessage(assetButtonLabel(selectedAsset[0], selectedProvider[0]));
            }, assetButton.getX(), assetButton.getY() + assetButton.getHeight());
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

    private void showCreateContentSearchSelector(List<String> options, String selected, Consumer<String> onSelected, int x, int y) {
        if (options == null || options.isEmpty()) {
            return;
        }
        closeCreateContentSearchSelector();
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        var overlay = ScreenManager.getInstance().getPopupOverlay();
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(overlay)
            .size(220, 240)
            .dismissOnSelect(true)
            .onClose(() -> closeCreateContentSearchSelector(selectorRef[0]))
            .build();
        selector.setLayer(900);
        selector.setPriority(30);
        selectorRef[0] = selector;
        for (String option : options.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            selector.addItem(option, () -> onSelected.accept(option));
        }
        selector.setSelectedItem(selected);
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
        List<String> catalogProviders = catalogOptions("server:custom_content:provider");
        List<String> providers = new ArrayList<>(catalogProviders);
        providers.remove("Loading");
        if (!providers.contains("vanilla")) {
            providers.add("vanilla");
        }
        for (PackContentRegistry.ProviderStatus status : PackContentRegistry.get().statuses()) {
            String name = status.providerName().toLowerCase(Locale.ROOT);
            if (name.contains("nexo") && !providers.contains("nexo")) {
                providers.add("nexo");
            }
            if (name.contains("itemsadder") && !providers.contains("itemsadder")) {
                providers.add("itemsadder");
            }
        }
        return providers;
    }

    private List<String> contentAssetOptions(String type, String provider) {
        if ("vanilla".equalsIgnoreCase(provider)) {
            return catalogOptions("server:minecraft:material");
        }
        List<String> catalogAssets = providerCatalogAssets(type, provider);
        if (!catalogAssets.isEmpty() && !catalogAssets.equals(List.of("Loading"))) {
            return catalogAssets;
        }
        List<String> result = new ArrayList<>();
        for (PackContentRegistry.PackAssetOption option : PackContentRegistry.get().assetOptions(provider)) {
            result.add(option.id());
        }
        if (result.isEmpty() && catalogAssets.equals(List.of("Loading"))) {
            return catalogAssets;
        }
        return result;
    }

    private List<String> providerCatalogAssets(String type, String provider) {
        List<String> values = new ArrayList<>();
        for (String source : providerCatalogSources(type, provider)) {
            values.addAll(catalogOptions(source));
        }
        List<String> assets = values.stream()
            .filter(value -> !"Loading".equals(value))
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        return assets.isEmpty() && values.contains("Loading") ? List.of("Loading") : assets;
    }

    private List<String> providerCatalogSources(String type, String provider) {
        if (provider == null || !provider.equalsIgnoreCase("nexo")) {
            return List.of();
        }
        return switch (type) {
            case "block" -> List.of("server:custom_content:nexo_block", "server:custom_content:nexo_furniture");
            case "armor" -> List.of("server:custom_content:nexo_armor");
            default -> List.of("server:custom_content:nexo_item");
        };
    }

    private List<String> catalogOptions(String source) {
        List<String> values = OptionCatalogCache.getInstance().getValues(screen.studioServerId(), source);
        if (!values.isEmpty()) {
            return values;
        }
        if (!OptionCatalogCache.getInstance().hasCatalog(screen.studioServerId(), source)) {
            requestCatalog(source);
            return List.of("Loading");
        }
        return List.of();
    }

    private void requestContentAssetCatalogs(String type, String provider) {
        if ("vanilla".equalsIgnoreCase(provider)) {
            requestCatalog("server:minecraft:material");
            return;
        }
        for (String source : providerCatalogSources(type, provider)) {
            requestCatalog(source);
        }
    }

    private void requestCatalog(String source) {
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && source != null && !OptionCatalogCache.getInstance().hasCatalog(screen.studioServerId(), source)) {
            manager.ensureFlowClient(screen.studioServerId()).requestOptionCatalog(source);
        }
    }

    private String defaultContentMaterial(String type) {
        return switch (type) {
            case "block" -> "STONE";
            case "armor" -> "IRON_CHESTPLATE";
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
                 ReSyncResourceDragPayload.DIALOG -> screen.openStudioDesigner(type, id);
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
            case "block", "armor" -> requestedType;
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
        screen.openStudioViewDocument(ReSyncResourceDragPayload.CUSTOM_CONTENT, id, name, contentGraph, new ScreenBackedStudioView(screen, new ContentDesignerScreen(screen.studioServerId(), null, contentGraph.getId(), screen)));
        return true;
    }

    private String createTargetFolder() {
        if (selectedFolder != null) {
            return selectedFolder.getPath();
        }
        return ReSyncProjectMetadata.normalizePath(currentFolder);
    }

    private String selectedCreateTargetFolder() {
        if (selectedFolder != null) {
            return selectedFolder.getPath();
        }
        if (selectedResource != null) {
            return selectedResource.getPath();
        }
        return ReSyncProjectMetadata.normalizePath(currentFolder);
    }

    private boolean resourceExists(FlowManager manager, String type, String id) {
        return ReSyncResourceCreator.exists(manager, screen.studioServerId(), type, id);
    }

    private String resourceTypeName(String type) {
        return ReSyncResourceCreator.resourceTypeName(type);
    }

    private void renameSelected() {
        if (selectedFolder == null && selectedResource == null) {
            return;
        }
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
            renameSelectedFolder(manager, newId);
            return true;
        }
        if (selectedResource != null && ReSyncResourceDragPayload.WORLD.equals(selectedResource.getType())) {
            new Notification("World", "World Rename Unsupported", Notification.Type.ERROR);
            return false;
        }
        if (selectedResource == null || newId.isBlank() || selectedResource.getId().equals(newId)) {
            return true;
        }
        if (resourceExists(manager, selectedResource.getType(), newId)) {
            new Notification("Error", resourceTypeName(selectedResource.getType()) + " ID already exists", Notification.Type.ERROR);
            return false;
        }
        boolean renamed = switch (selectedResource.getType()) {
            case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION, ReSyncResourceDragPayload.CUSTOM_CONTENT -> manager.renameFlow(screen.studioServerId(), selectedResource.getId(), newId);
            case ReSyncResourceDragPayload.COMMAND -> renameCommandResource(manager, selectedResource.getId(), newId);
            case ReSyncResourceDragPayload.GUI -> manager.renameGui(screen.studioServerId(), selectedResource.getId(), newId);
            case ReSyncResourceDragPayload.SCOREBOARD -> manager.renameScoreboard(screen.studioServerId(), selectedResource.getId(), newId);
            case ReSyncResourceDragPayload.TAB -> manager.renameTab(screen.studioServerId(), selectedResource.getId(), newId);
            case ReSyncResourceDragPayload.CHAT, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                 ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                 ReSyncResourceDragPayload.DIALOG -> {
                ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(selectedResource.getType());
                yield resourceType != null && manager.renameJsonResource(screen.studioServerId(), resourceType, selectedResource.getId(), newId);
            }
            default -> false;
        };
        if (!renamed) {
            new Notification("Explorer", "Rename Failed", Notification.Type.ERROR);
            return false;
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(screen.studioServerId());
        ReSyncProjectMetadata.ResourceEntry entry = metadata.findResource(selectedResource.getType(), selectedResource.getId());
        String oldKey = selectedResource.key();
        if (entry != null) {
            entry.setId(newId);
            entry.setDisplayName(newId);
            manager.saveProjectMetadata(screen.studioServerId(), metadata);
        }
        for (int i = 0; i < screen.studioDocuments.size(); i++) {
            StudioDocument document = screen.studioDocuments.get(i);
            if (document.key().equals(oldKey)) {
                screen.studioDocuments.set(i, new StudioDocument(document.type(), newId, newId, document.graph(), document.view(), document.viewport()));
                break;
            }
        }
        rebuild();
        screen.syncStudioDocumentTabs();
        return true;
    }

    private void deleteSelected() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        if (selectedFolder != null) {
            deleteSelectedFolder(manager);
            return;
        }
        if (selectedResource == null) {
            return;
        }
        switch (selectedResource.getType()) {
            case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION -> manager.deleteFlow(screen.studioServerId(), selectedResource.getId());
            case ReSyncResourceDragPayload.COMMAND -> {
                manager.clearCommandBinding(screen.studioServerId(), selectedResource.getId());
                manager.deleteFlow(screen.studioServerId(), selectedResource.getId());
            }
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> manager.deleteCustomContent(screen.studioServerId(), selectedResource.getId());
            case ReSyncResourceDragPayload.GUI -> manager.deleteGui(screen.studioServerId(), selectedResource.getId());
            case ReSyncResourceDragPayload.SCOREBOARD -> manager.deleteScoreboard(screen.studioServerId(), selectedResource.getId());
            case ReSyncResourceDragPayload.TAB -> manager.deleteTab(screen.studioServerId(), selectedResource.getId());
            case ReSyncResourceDragPayload.CHAT, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                 ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                 ReSyncResourceDragPayload.DIALOG -> {
                ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(selectedResource.getType());
                if (resourceType == null) {
                    return;
                }
                manager.deleteJsonResource(screen.studioServerId(), resourceType, selectedResource.getId());
            }
            case ReSyncResourceDragPayload.WORLDGEN -> WorldGenManager.getInstance().deleteProject(screen.studioServerId(), selectedResource.getId());
            case ReSyncResourceDragPayload.WORLD -> WorldResourceCreator.showDeletePopup(screen, screen.studioServerId(), selectedResource.getId(), this::rebuild);
            default -> {
                return;
            }
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(screen.studioServerId());
        metadata.getResources().removeIf(entry -> entry.key().equals(selectedResource.key()));
        manager.saveProjectMetadata(screen.studioServerId(), metadata);
        screen.studioDocuments.removeIf(document -> document.key().equals(selectedResource.key()));
        screen.syncStudioDocumentTabs();
        rebuild();
    }

    private boolean renameCommandResource(FlowManager manager, String oldId, String newId) {
        String serverId = screen.studioServerId();
        if (manager.isCatalogResourceIdTaken(serverId, newId, ReSyncResourceDragPayload.COMMAND, oldId)) {
            new Notification("Command", "ID Exists", Notification.Type.ERROR);
            return false;
        }
        TriggerBinding binding = manager.getCommandBinding(screen.studioServerId(), oldId);
        String context = binding != null ? binding.getContext() : oldId;
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        ReSyncProjectMetadata.ResourceEntry entry = metadata.findResource(ReSyncResourceDragPayload.COMMAND, oldId);
        boolean createdEntry = entry == null;
        if (entry == null) {
            entry = metadata.ensureResource(ReSyncResourceDragPayload.COMMAND, oldId, oldId, ReSyncResourceType.defaultFolderFor(ReSyncResourceDragPayload.COMMAND));
        }
        String oldDisplayName = entry.getDisplayName();
        String oldPath = entry.getPath();
        entry.setId(newId);
        entry.setDisplayName(newId);
        manager.saveProjectMetadata(serverId, metadata);
        if (!manager.renameFlow(serverId, oldId, newId)) {
            if (createdEntry) {
                metadata.getResources().removeIf(resource -> resource != null && resource.key().equals(ReSyncProjectMetadata.resourceKey(ReSyncResourceDragPayload.COMMAND, newId)));
            } else {
                entry.setId(oldId);
                entry.setDisplayName(oldDisplayName);
                entry.setPath(oldPath);
            }
            manager.saveProjectMetadata(serverId, metadata);
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

    private void renameSelectedFolder(FlowManager manager, String newName) {
        String name = newName == null ? "" : newName.trim();
        if (name.isBlank()) {
            return;
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(screen.studioServerId());
        String oldPath = selectedFolder.getPath();
        String parent = selectedFolder.getParentPath();
        String newPath = parent.isBlank() ? name : parent + "/" + name;
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
    }

    private void deleteSelectedFolder(FlowManager manager) {
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(screen.studioServerId());
        String path = selectedFolder.getPath();
        metadata.getFolders().removeIf(folder -> folder.getPath().equals(path) || folder.getPath().startsWith(path + "/"));
        metadata.getResources().removeIf(resource -> resource.getPath().equals(path) || resource.getPath().startsWith(path + "/"));
        if (currentFolder.equals(path) || currentFolder.startsWith(path + "/")) {
            currentFolder = selectedFolder.getParentPath();
        }
        manager.saveProjectMetadata(screen.studioServerId(), metadata);
        rebuild();
    }

    private String iconPathFor(ReSyncProjectMetadata.ResourceEntry resource) {
        return resourceIconPaths.getOrDefault(resource.key(), screen.studioResourceIconPath(resource.getType(), resource.getId()));
    }

    private String customContentIconPath(CustomContentDefinition content) {
        return switch (content != null && content.getType() != null ? content.getType().toLowerCase(Locale.ROOT) : "") {
            case "armor" -> "armor.png";
            case "block" -> "block.png";
            case "item" -> "item.png";
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
        if (sidePanel != null) {
            sidePanel.show();
        }
        screen.refreshStudioLayoutPositions();
    }

    public void slideOut() {
        if (sidePanel != null) {
            sidePanel.hide();
        }
        screen.refreshStudioLayoutPositions();
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
                FileSystemProvider.FileEntry entry = new FileSystemProvider.FileEntry(path, false, "", "", resource.getDisplayName());
                entry.metadata.put("icon", iconPathFor(resource));
                entriesByFolder.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(entry);
            }
            for (List<FileSystemProvider.FileEntry> entries : entriesByFolder.values()) {
                entries.sort((left, right) -> Boolean.compare(!left.isDirectory, !right.isDirectory) != 0
                    ? Boolean.compare(!left.isDirectory, !right.isDirectory)
                    : left.displayName.compareToIgnoreCase(right.displayName));
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
}
