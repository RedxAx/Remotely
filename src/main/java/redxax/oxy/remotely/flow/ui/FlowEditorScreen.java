package redxax.oxy.remotely.flow.ui;

import com.google.gson.Gson;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.FlowDebugController;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.sync.FlowCategoryMetadata;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioView;
import redxax.oxy.remotely.flow.ui.studio.ScreenBackedStudioView;
import redxax.oxy.remotely.flow.ui.studio.StudioHeaderProvider;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.ui.WorldGenEditorScreen;
import org.lwjgl.glfw.GLFW;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.ui.screens.editor.WorkspaceTreeExplorer;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.UiHost;
import restudio.rescreen.ui.desktop.DesktopIconWidget;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.rescreen.layout.DesktopLayout;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.InfiniteScreen;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.*;
import restudio.rescreen.ui.rescreen.ReScreen.HeaderBuilder.Position;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.ResourceManager;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public class FlowEditorScreen extends InfiniteScreen implements UiHost, StudioHeaderProvider {
    private static final String CUSTOM_FUNCTION_NODE_PREFIX = "custom_function:";
    private static final Set<FlowEditorScreen> OPEN_SCREENS = new CopyOnWriteArraySet<>();
    protected FlowGraph graph;
    protected final String serverId;
    private static Screen parent;
    protected final Map<String, FlowNodeWidget> widgetCache = new HashMap<>();
    private static final float WIRE_HIT_RADIUS = 6.0f;
    private static final int WIRE_OUT_OFFSET = 26;
    private final Set<String> selectedNodeIds = new HashSet<>();
    private final Set<String> selectionBase = new HashSet<>();
    private final Map<String, int[]> selectedDragStartPositions = new HashMap<>();
    private boolean isSelecting = false;
    private boolean selectionAdditive = false;
    private double selectionStartX = 0;
    private double selectionStartY = 0;
    private double selectionEndX = 0;
    private double selectionEndY = 0;

    protected SidePanel paletteSidePanel;
    private final Map<NodeDefinition.NodeCategory, PopupWidget> categoryPopups = new HashMap<>();
    private List<NodeDefinition.NodeCategory> categoryOrder = List.of();

    private IconButton headerBackground;
    protected final List<IconButton> headerButtons = new ArrayList<>();
    private final List<AnimatedWidget> activeViewHeaderButtons = new ArrayList<>();
    private IconButton debugToggleButton;
    private IconButton debugResumeButton;
    private IconButton debugStepButton;
    private IconButton debugStopButton;
    private boolean initialized;
    private boolean debugMode;

    private int initialWidth;
    private int initialHeight;
    private boolean studioMode;
    private TabsManager studioTabsManager;
    private ReSyncContentBrowserWidget studioContentBrowser;
    private SidePanel studioResourcePanel;
    private final List<StudioDocument> studioDocuments = new ArrayList<>();
    private StudioDocument activeStudioDocument;
    private final FlowGraph studioEmptyGraph = new FlowGraph();
    private String activeNodeRegistryServerId;
    private final Gson gson = new Gson();
    private TextInputWidget commandLabelInput;
    private TextInputWidget commandPathsInput;
    private ToggleWidget commandStructuredToggle;
    private static final int STUDIO_CONTENT_BROWSER_HEIGHT = 158;

    private record StudioDocument(String type, String id, String title, FlowGraph graph, ReSyncStudioView view) {
        String key() {
            return ReSyncProjectMetadata.resourceKey(type, id);
        }
    }

    private static class CommandBindingContext {
        private String command;
        private List<String> subcommands;
        private Boolean structured;
    }

    private class ReSyncContentBrowserWidget extends AnimatedWidget {
        private final Path projectRoot = Path.of("ReSync");
        private String currentFolder = "";
        private ReSyncProjectMetadata.ResourceEntry selectedResource;
        private ReSyncProjectMetadata.FolderEntry selectedFolder;
        private AnimatedWidget lastGridReleasedWidget;
        private long lastGridReleaseTime;
        private final Container treeContainer;
        private final Container gridContainer;
        private final TextInputWidget nameInput;
        private final ReSyncProjectTreeProvider treeProvider;
        private final WorkspaceTreeExplorer treeExplorer;
        private final SquareButtonWidget createButton;
        private final BufferedImage folderIcon;
        private final BufferedImage flowIcon;
        private final BufferedImage commandIcon;
        private final BufferedImage contentIcon;
        private final BufferedImage guiIcon;
        private final BufferedImage scoreboardIcon;
        private final BufferedImage tabIcon;
        private final BufferedImage worldGenIcon;

        private ReSyncContentBrowserWidget(int x, int y, int width, int height) {
            super(x, y, width, height, "");
            animateElevation = false;
            entranceAnimationEnabled = false;
            enableHoverColors = false;
            nameInput = new TextInputWidget.Builder()
                .placeholder("Selected")
                .size(110, 18)
                .build();
            treeContainer = new Container("studio-content-tree", x + 4, y + 6, 210, height - 10);
            treeContainer.layout(new ManagedLayout()).columns(1).padding(2).scrolling(true).backgroundDrawing(false);
            gridContainer = new Container("studio-content-grid", x + 220, y + 6, width - 224, height - 10);
            gridContainer.layout(new DesktopLayout()).backgroundDrawing(false).enableSelecting(true).enableDoubleClick(false);
            treeProvider = new ReSyncProjectTreeProvider();
            treeExplorer = new WorkspaceTreeExplorer(FlowEditorScreen.this, treeContainer, this::openTreeFile, false);
            treeExplorer.setToggleDirectoriesOnActivation(false);
            treeExplorer.setOnNodeActivated(this::activateTreeNode);
            treeExplorer.setOnNodeOpened(this::openTreeNode);
            treeExplorer.setOnNodeRightClick(this::rightClickTreeNode);
            createButton = new SquareButtonWidget.Builder()
                .imagePath("add.png")
                .size(18, 18)
                .hint("Create")
                .onClick(this::showCreateMenu)
                .build();
            gridContainer.setOnSelectionChanged(widgets -> {
                selectedResource = null;
                selectedFolder = null;
                if (!widgets.isEmpty() && widgets.getFirst() instanceof DesktopIconWidget<?> icon) {
                    Object item = icon.getItem();
                    if (item instanceof ReSyncProjectMetadata.ResourceEntry resource) {
                        selectedResource = resource;
                        nameInput.setText(resource.getId());
                    } else if (item instanceof ReSyncProjectMetadata.FolderEntry folder) {
                        selectedFolder = folder;
                        nameInput.setText(folder.getName());
                    }
                }
            });
            ResourceManager resources = ResourceManager.getInstance();
            folderIcon = resources.getImage(Identifier.icon("folder.png"));
            flowIcon = resources.getImage(Identifier.icon("graph.png"));
            commandIcon = resources.getImage(Identifier.icon("terminal.png"));
            contentIcon = resources.getImage(Identifier.icon("resources.png"));
            guiIcon = resources.getImage(Identifier.icon("panel.png"));
            scoreboardIcon = resources.getImage(Identifier.icon("report.png"));
            tabIcon = resources.getImage(Identifier.icon("newTab.png"));
            worldGenIcon = resources.getImage(Identifier.icon("earth.png"));
            rebuild();
        }

        @Override
        protected void drawContent(IDrawContext context, int mouseX, int mouseY) {
            int border = ThemeManager.getColor(ThemeColor.innerBorder);
            int dividerX = getX() + currentFolderWidth() + 4;
            context.fill(dividerX, getY() + 5, dividerX + 1, getY() + getHeight() - 1, border);
            treeContainer.render(context, mouseX, mouseY, 0);
            gridContainer.render(context, mouseX, mouseY, 0);
            createButton.render(context, mouseX, mouseY, 0);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver(mouseX, mouseY)) {
                return false;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (treeContainer.isMouseOver(mouseX, mouseY) && treeContainer.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                selectGridItemAt(mouseX, mouseY);
                showExplorerMenu((int) mouseX, (int) mouseY);
                return true;
            }
            if (createButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (treeContainer.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return gridContainer.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (!isMouseOver(mouseX, mouseY)) {
                return false;
            }
            if (treeContainer.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
            if (handleGridIconRelease(mouseX, mouseY, button)) {
                return true;
            }
            return gridContainer.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (!isMouseOver(mouseX, mouseY)) {
                return false;
            }
            return treeContainer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) || gridContainer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
            if (!isMouseOver(mouseX, mouseY)) {
                return false;
            }
            return treeContainer.mouseScrolled(mouseX, mouseY, amount) || gridContainer.mouseScrolled(mouseX, mouseY, amount);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return treeContainer.keyPressed(keyCode, scanCode, modifiers) || gridContainer.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            return treeContainer.charTyped(chr, modifiers) || gridContainer.charTyped(chr, modifiers);
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

        private void rebuild() {
            rebuildTree();
            rebuildGrid();
        }

        private void rebuildTree() {
            treeProvider.rebuild();
            treeExplorer.setWorkspace(projectRoot, treeProvider, true);
        }

        private void rebuildGrid() {
            gridContainer.clearWidgets();
            selectedResource = null;
            selectedFolder = null;
            for (ReSyncProjectMetadata.FolderEntry folder : studioFolders(currentFolder)) {
                DesktopIconWidget<ReSyncProjectMetadata.FolderEntry> widget = new DesktopIconWidget.Builder<>(folder, folderIcon, folder.getName()).build();
                gridContainer.addWidget(widget);
            }
            for (ReSyncProjectMetadata.ResourceEntry resource : studioResources(currentFolder)) {
                DesktopIconWidget<ReSyncProjectMetadata.ResourceEntry> widget = new DesktopIconWidget.Builder<>(resource, iconFor(resource), resource.getDisplayName()).build();
                gridContainer.addWidget(widget);
            }
            gridContainer.updateWidgetPositions();
        }

        private void selectFolder(String path) {
            currentFolder = path;
            nameInput.setText("");
            rebuildTree();
            rebuildGrid();
        }

        private void openTreeFile(Path path) {
            ReSyncProjectMetadata.ResourceEntry resource = treeProvider.resource(path);
            if (resource == null) {
                return;
            }
            selectedResource = resource;
            selectedFolder = null;
            nameInput.setText(resource.getId());
            openStudioResource(resource);
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
            showExplorerMenu(getMouseX(), getMouseY());
        }

        private void openFolder(ReSyncProjectMetadata.FolderEntry folder, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                selectFolder(folder.getPath());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                selectedFolder = folder;
                selectedResource = null;
                nameInput.setText(folder.getName());
            }
        }

        private void openResource(ReSyncProjectMetadata.ResourceEntry resource, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                selectedResource = resource;
                selectedFolder = null;
                nameInput.setText(resource.getId());
                openStudioResource(resource);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                selectedResource = resource;
                selectedFolder = null;
                nameInput.setText(resource.getId());
            }
        }

        private boolean showExplorerMenu(int mouseX, int mouseY) {
            if (selectedResource == null && selectedFolder == null) {
                return false;
            }
            ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(FlowEditorScreen.this)
                .addHeaderButton("edit.png", this::renameSelected, "Rename")
                .addHeaderButton("delete.png", this::deleteSelected, "Delete", ThemeManager.getAccent("danger"))
                .addIconItem("Open", "open.png", this::openSelected, "Open");
            if (selectedResource != null) {
                builder.addIconItem("Reveal", "explorer.png", () -> selectFolder(selectedResource.getPath()), "Reveal");
            }
            showContextMenu(mouseX, mouseY, builder);
            return true;
        }

        private void showCreateMenu() {
            ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(FlowEditorScreen.this)
                .addIconItem("New Folder", "folder.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FOLDER), "Create Folder")
                .addIconItem("New Flow", "graph.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FLOW), "Create Flow")
                .addIconItem("New Function", "snippets.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FUNCTION), "Create Function")
                .addIconItem("New Command", "terminal.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.COMMAND), "Create Command")
                .addIconItem("New Item", "resources.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.CUSTOM_CONTENT), "Create Item")
                .addIconItem("New GUI", "panel.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.GUI), "Create GUI")
                .addIconItem("New Scoreboard", "report.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.SCOREBOARD), "Create Scoreboard")
                .addIconItem("New Tab", "newTab.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.TAB), "Create Tab")
                .addIconItem("New WorldGen", "earth.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.WORLDGEN), "Create WorldGen");
            showContextMenu(createButton.getX(), createButton.getY() + createButton.getHeight() + 2, builder);
        }

        private void selectGridItemAt(double mouseX, double mouseY) {
            selectedResource = null;
            selectedFolder = null;
            for (AnimatedWidget widget : gridContainer.getWidgets()) {
                if (widget.isMouseOver(mouseX, mouseY) && widget instanceof DesktopIconWidget<?> icon) {
                    Object item = icon.getItem();
                    if (item instanceof ReSyncProjectMetadata.ResourceEntry resource) {
                        selectedResource = resource;
                        nameInput.setText(resource.getId());
                    } else if (item instanceof ReSyncProjectMetadata.FolderEntry folder) {
                        selectedFolder = folder;
                        nameInput.setText(folder.getName());
                    }
                    gridContainer.clearSelection();
                    gridContainer.addSelectedWidget(widget);
                    return;
                }
            }
            gridContainer.clearSelection();
        }

        private boolean handleGridIconRelease(double mouseX, double mouseY, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return false;
            }
            for (AnimatedWidget widget : gridContainer.getWidgets()) {
                if (!widget.isMouseOver(mouseX, mouseY) || !(widget instanceof DesktopIconWidget<?> icon)) {
                    continue;
                }
                long now = System.currentTimeMillis();
                boolean doubleClick = widget == lastGridReleasedWidget && now - lastGridReleaseTime <= 320L;
                lastGridReleasedWidget = widget;
                lastGridReleaseTime = now;
                if (!doubleClick) {
                    return true;
                }
                lastGridReleasedWidget = null;
                lastGridReleaseTime = 0L;
                Object item = icon.getItem();
                if (item instanceof ReSyncProjectMetadata.ResourceEntry resource) {
                    openResource(resource, button);
                    return true;
                }
                if (item instanceof ReSyncProjectMetadata.FolderEntry folder) {
                    openFolder(folder, button);
                    return true;
                }
            }
            return false;
        }

        private void openSelected() {
            if (selectedResource != null) {
                openStudioResource(selectedResource);
            } else if (selectedFolder != null) {
                selectFolder(selectedFolder.getPath());
            }
        }

        private void showCreateResourcePopup(String type) {
            PopupWidget.Builder builder = new PopupWidget.Builder(createPopupTitle(type)).setResizable(false);
            TextInputWidget idInput = new TextInputWidget.Builder()
                .placeholder(createIdPlaceholder(type))
                .size(220, 22)
                .build();
            builder.addRow(ReSyncResourceDragPayload.FOLDER.equals(type) ? "Name" : "ID", true, 22, idInput);
            DropDownWidget<String> templateSelect = null;
            FlowManager manager = FlowManager.getInstance();
            if (ReSyncResourceDragPayload.FLOW.equals(type) && manager != null && !manager.getFlowTemplates().isEmpty()) {
                templateSelect = new DropDownWidget.Builder<>(manager.getFlowTemplates())
                    .size(220, 22)
                    .selectedItem(manager.getFlowTemplates().getFirst())
                    .build();
                builder.addRow("Template", true, 22, templateSelect);
            }

            PopupWidget[] popupRef = new PopupWidget[1];
            DropDownWidget<String> finalTemplateSelect = templateSelect;
            AnimatedButton createButton = new AnimatedButton.Builder()
                .label("Create")
                .accentType(ThemeManager.getAccent("nice"))
                .onClick(() -> {
                    String value = idInput.getText() != null ? idInput.getText().trim() : "";
                    if (ReSyncResourceDragPayload.FOLDER.equals(type)) {
                        if (value.isBlank()) {
                            new Notification("Explorer", "Invalid Name", Notification.Type.ERROR);
                            return;
                        }
                    } else if (!value.matches("^[a-zA-Z0-9_]+$")) {
                        new Notification("Error", "Invalid ID. Alphanumeric only.", Notification.Type.ERROR);
                        return;
                    }
                    String template = finalTemplateSelect != null ? finalTemplateSelect.getSelectedItem() : null;
                    if (createResource(type, value, template)) {
                        if (popupRef[0] != null) {
                            popupRef[0].hide();
                        }
                    }
                })
                .build();
            builder.addRow("", true, 20, createButton);
            popupRef[0] = builder.build();
            addDrawableChild(popupRef[0]);
            popupRef[0].show();
        }

        private String createPopupTitle(String type) {
            return switch (type) {
                case ReSyncResourceDragPayload.FOLDER -> "Create Folder";
                case ReSyncResourceDragPayload.FUNCTION -> "Create New Function";
                case ReSyncResourceDragPayload.COMMAND -> "Create New Command";
                case ReSyncResourceDragPayload.CUSTOM_CONTENT -> "Create Item";
                case ReSyncResourceDragPayload.GUI -> "Create New GUI";
                case ReSyncResourceDragPayload.SCOREBOARD -> "Create New Scoreboard";
                case ReSyncResourceDragPayload.TAB -> "Create New Tab";
                case ReSyncResourceDragPayload.WORLDGEN -> "Create WorldGen Project";
                default -> "Create New Flow";
            };
        }

        private String createIdPlaceholder(String type) {
            return switch (type) {
                case ReSyncResourceDragPayload.FOLDER -> "Folder Name";
                case ReSyncResourceDragPayload.FUNCTION -> "Function ID (e.g. calculateDamage)";
                case ReSyncResourceDragPayload.COMMAND -> "Command ID (e.g. shop)";
                case ReSyncResourceDragPayload.CUSTOM_CONTENT -> "Item ID (e.g. fire_sword)";
                case ReSyncResourceDragPayload.GUI -> "GUI ID (e.g. main_menu)";
                case ReSyncResourceDragPayload.SCOREBOARD -> "Scoreboard ID (e.g. main_sidebar)";
                case ReSyncResourceDragPayload.TAB -> "Tab ID (e.g. default_tab)";
                case ReSyncResourceDragPayload.WORLDGEN -> "Project ID (e.g. overworld)";
                default -> "Flow ID (e.g. openLootBox)";
            };
        }

        private boolean createResource(String type, String id, String template) {
            FlowManager manager = FlowManager.getInstance();
            if (manager == null) {
                return false;
            }
            if (ReSyncResourceDragPayload.FOLDER.equals(type)) {
                manager.createProjectFolder(serverId, currentFolder, id);
                rebuild();
                return true;
            }
            if (manager.getProjectMetadata(serverId).findResource(type, id) != null || resourceExists(manager, type, id)) {
                new Notification("Error", resourceTypeName(type) + " ID already exists", Notification.Type.ERROR);
                return false;
            }
            switch (type) {
                case ReSyncResourceDragPayload.FLOW -> {
                    String selectedTemplate = template != null ? template : manager.getFlowTemplates().isEmpty() ? "Empty" : manager.getFlowTemplates().getFirst();
                    openStudioGraphDocument(type, id, id, manager.createFlow(serverId, id, false, selectedTemplate));
                }
                case ReSyncResourceDragPayload.FUNCTION -> openStudioGraphDocument(type, id, id, manager.createFlow(serverId, id, true));
                case ReSyncResourceDragPayload.COMMAND -> {
                    FlowGraph commandGraph = manager.createFlow(serverId, id, false, "Command");
                    manager.setCommandBinding(serverId, id, id);
                    openStudioGraphDocument(type, id, id, commandGraph);
                }
                case ReSyncResourceDragPayload.CUSTOM_CONTENT -> {
                    FlowGraph contentGraph = manager.createContentFlow(serverId, id, "item", id);
                    openStudioViewDocument(type, id, id, contentGraph, new ScreenBackedStudioView(FlowEditorScreen.this, new ContentStudioScreen(serverId, null, contentGraph.getId(), FlowEditorScreen.this)));
                }
                case ReSyncResourceDragPayload.GUI -> manager.createGui(serverId, id);
                case ReSyncResourceDragPayload.SCOREBOARD -> {
                    ScoreboardDefinition scoreboard = manager.createScoreboard(serverId, id);
                    scoreboard.setDisplaySlot("sidebar");
                }
                case ReSyncResourceDragPayload.TAB -> manager.createTab(serverId, id);
                case ReSyncResourceDragPayload.WORLDGEN -> {
                    WorldGenProject project = WorldGenManager.getInstance().createProjectTemplate("Continental", id);
                    WorldGenManager.getInstance().saveWorldGen(serverId, project);
                    WorldGenManager.getInstance().ensureLocalDefinitions(serverId);
                    openStudioWorldGenDocument(id, id, project);
                }
                default -> {
                    return false;
                }
            }
            ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
            metadata.ensureResource(type, id, id, currentFolder);
            manager.saveProjectMetadata(serverId, metadata);
            rebuild();
            if (ReSyncResourceDragPayload.GUI.equals(type) || ReSyncResourceDragPayload.SCOREBOARD.equals(type) || ReSyncResourceDragPayload.TAB.equals(type)) {
                openStudioDesigner(type, id);
            }
            return true;
        }

        private boolean resourceExists(FlowManager manager, String type, String id) {
            return switch (type) {
                case ReSyncResourceDragPayload.FLOW -> manager.getProjectMetadata(serverId).findResource(ReSyncResourceDragPayload.FLOW, id) != null;
                case ReSyncResourceDragPayload.FUNCTION -> manager.getProjectMetadata(serverId).findResource(ReSyncResourceDragPayload.FUNCTION, id) != null;
                case ReSyncResourceDragPayload.CUSTOM_CONTENT -> manager.getCustomContentForServer(serverId).containsKey(id);
                case ReSyncResourceDragPayload.COMMAND -> manager.getProjectMetadata(serverId).findResource(ReSyncResourceDragPayload.COMMAND, id) != null || manager.getCommandBinding(serverId, id) != null;
                case ReSyncResourceDragPayload.GUI -> manager.getGuisForServer(serverId).containsKey(id);
                case ReSyncResourceDragPayload.SCOREBOARD -> manager.getScoreboardsForServer(serverId).containsKey(id);
                case ReSyncResourceDragPayload.TAB -> manager.getTabsForServer(serverId).containsKey(id);
                default -> false;
            };
        }

        private String resourceTypeName(String type) {
            return switch (type) {
                case ReSyncResourceDragPayload.FUNCTION -> "Function";
                case ReSyncResourceDragPayload.COMMAND -> "Command";
                case ReSyncResourceDragPayload.CUSTOM_CONTENT -> "Item";
                case ReSyncResourceDragPayload.GUI -> "GUI";
                case ReSyncResourceDragPayload.SCOREBOARD -> "Scoreboard";
                case ReSyncResourceDragPayload.TAB -> "Tab";
                case ReSyncResourceDragPayload.WORLDGEN -> "WorldGen";
                default -> "Flow";
            };
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
            addDrawableChild(popupRef[0]);
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
            if (selectedResource == null || newId.isBlank() || selectedResource.getId().equals(newId)) {
                return true;
            }
            if (resourceExists(manager, selectedResource.getType(), newId)) {
                new Notification("Error", resourceTypeName(selectedResource.getType()) + " ID already exists", Notification.Type.ERROR);
                return false;
            }
            boolean renamed = switch (selectedResource.getType()) {
                case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION, ReSyncResourceDragPayload.CUSTOM_CONTENT -> manager.renameFlow(serverId, selectedResource.getId(), newId);
                case ReSyncResourceDragPayload.COMMAND -> renameCommandResource(manager, selectedResource.getId(), newId);
                case ReSyncResourceDragPayload.GUI -> manager.renameGui(serverId, selectedResource.getId(), newId);
                case ReSyncResourceDragPayload.SCOREBOARD -> manager.renameScoreboard(serverId, selectedResource.getId(), newId);
                case ReSyncResourceDragPayload.TAB -> manager.renameTab(serverId, selectedResource.getId(), newId);
                default -> false;
            };
            if (!renamed) {
                new Notification("Explorer", "Rename Failed", Notification.Type.ERROR);
                return false;
            }
            ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
            ReSyncProjectMetadata.ResourceEntry entry = metadata.findResource(selectedResource.getType(), selectedResource.getId());
            String oldKey = selectedResource.key();
            if (entry != null) {
                entry.setId(newId);
                entry.setDisplayName(newId);
                manager.saveProjectMetadata(serverId, metadata);
            }
            for (int i = 0; i < studioDocuments.size(); i++) {
                StudioDocument document = studioDocuments.get(i);
                if (document.key().equals(oldKey)) {
                    studioDocuments.set(i, new StudioDocument(document.type(), newId, newId, document.graph(), document.view()));
                    break;
                }
            }
            rebuild();
            rebuildStudioDocumentTabs();
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
                case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION -> manager.deleteFlow(serverId, selectedResource.getId());
                case ReSyncResourceDragPayload.COMMAND -> {
                    manager.clearCommandBinding(serverId, selectedResource.getId());
                    manager.deleteFlow(serverId, selectedResource.getId());
                }
                case ReSyncResourceDragPayload.CUSTOM_CONTENT -> manager.deleteCustomContent(serverId, selectedResource.getId());
                case ReSyncResourceDragPayload.GUI -> manager.deleteGui(serverId, selectedResource.getId());
                case ReSyncResourceDragPayload.SCOREBOARD -> manager.deleteScoreboard(serverId, selectedResource.getId());
                case ReSyncResourceDragPayload.TAB -> manager.deleteTab(serverId, selectedResource.getId());
                case ReSyncResourceDragPayload.WORLDGEN -> WorldGenManager.getInstance().deleteProject(serverId, selectedResource.getId());
                default -> {
                    return;
                }
            }
            ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
            metadata.getResources().removeIf(entry -> entry.key().equals(selectedResource.key()));
            manager.saveProjectMetadata(serverId, metadata);
            studioDocuments.removeIf(document -> document.key().equals(selectedResource.key()));
            rebuildStudioDocumentTabs();
            rebuild();
        }

        private boolean renameCommandResource(FlowManager manager, String oldId, String newId) {
            TriggerBinding binding = manager.getCommandBinding(serverId, oldId);
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

        private void renameSelectedFolder(FlowManager manager, String newName) {
            String name = newName == null ? "" : newName.trim();
            if (name.isBlank()) {
                return;
            }
            ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
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
            manager.saveProjectMetadata(serverId, metadata);
            rebuild();
        }

        private void deleteSelectedFolder(FlowManager manager) {
            ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
            String path = selectedFolder.getPath();
            metadata.getFolders().removeIf(folder -> folder.getPath().equals(path) || folder.getPath().startsWith(path + "/"));
            metadata.getResources().removeIf(resource -> resource.getPath().equals(path) || resource.getPath().startsWith(path + "/"));
            if (currentFolder.equals(path) || currentFolder.startsWith(path + "/")) {
                currentFolder = selectedFolder.getParentPath();
            }
            manager.saveProjectMetadata(serverId, metadata);
            rebuild();
        }

        private String sanitizeResourceId(String text) {
            String value = text == null ? "" : text.trim();
            return value.matches("^[a-zA-Z0-9_]+$") ? value : "";
        }

        private BufferedImage iconFor(ReSyncProjectMetadata.ResourceEntry resource) {
            return switch (iconPathFor(resource)) {
                case "terminal.png" -> commandIcon;
                case "resources.png" -> contentIcon;
                case "panel.png" -> guiIcon;
                case "report.png" -> scoreboardIcon;
                case "newTab.png" -> tabIcon;
                case "earth.png" -> worldGenIcon;
                default -> flowIcon;
            };
        }

        private String iconPathFor(ReSyncProjectMetadata.ResourceEntry resource) {
            return switch (resource.getType()) {
                case ReSyncResourceDragPayload.COMMAND -> "terminal.png";
                case ReSyncResourceDragPayload.CUSTOM_CONTENT -> "resources.png";
                case ReSyncResourceDragPayload.GUI -> "panel.png";
                case ReSyncResourceDragPayload.SCOREBOARD -> "report.png";
                case ReSyncResourceDragPayload.TAB -> "newTab.png";
                case ReSyncResourceDragPayload.WORLDGEN -> "earth.png";
                case ReSyncResourceDragPayload.WORLD -> "earth.png";
                default -> "graph.png";
            };
        }

        private void updateContainers() {
            int folderWidth = currentFolderWidth();
            treeContainer.setPosition(getX() + 4, getY() + 6);
            treeContainer.setSize(folderWidth - 8, getHeight() - 10);
            gridContainer.setPosition(getX() + folderWidth + 8, getY() + 6);
            gridContainer.setSize(getWidth() - folderWidth - 12, getHeight() - 10);
            createButton.setPosition(getX() + getWidth() - 24, getY() + 8);
            treeContainer.updateWidgetPositions();
            gridContainer.updateWidgetPositions();
        }

        private int currentFolderWidth() {
            return Math.min(216, Math.max(160, getWidth() / 5));
        }

        private class ReSyncProjectTreeProvider implements FileSystemProvider {
            private final Map<Path, String> folderPaths = new HashMap<>();
            private final Map<Path, ReSyncProjectMetadata.FolderEntry> folders = new HashMap<>();
            private final Map<Path, ReSyncProjectMetadata.ResourceEntry> resources = new HashMap<>();

            private void rebuild() {
                folderPaths.clear();
                folders.clear();
                resources.clear();
                folderPaths.put(projectRoot, "");
                for (ReSyncProjectMetadata.FolderEntry folder : studioAllFolders()) {
                    Path path = pathForFolder(folder.getPath());
                    folderPaths.put(path, folder.getPath());
                    folders.put(path, folder);
                }
                for (ReSyncProjectMetadata.ResourceEntry resource : studioAllResources()) {
                    resources.put(pathForResource(resource), resource);
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
                List<FileSystemProvider.FileEntry> entries = new ArrayList<>();
                for (ReSyncProjectMetadata.FolderEntry child : studioFolders(folder)) {
                    FileSystemProvider.FileEntry entry = new FileSystemProvider.FileEntry(pathForFolder(child.getPath()), true, "-", "", child.getName());
                    entry.metadata.put("icon", "folder.png");
                    entries.add(entry);
                }
                for (ReSyncProjectMetadata.ResourceEntry resource : studioResources(folder)) {
                    FileSystemProvider.FileEntry entry = new FileSystemProvider.FileEntry(pathForResource(resource), false, "", "", resource.getDisplayName());
                    entry.metadata.put("icon", iconPathFor(resource));
                    entries.add(entry);
                }
                return CompletableFuture.completedFuture(entries);
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
                return "type".equals(key) ? "RESYNC" : null;
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
            return pathForFolder(resource.getPath()).resolve(resource.getType() + "__" + resource.getId());
        }
    }

    private static class DragState {
        String sourceNodeId;
        String sourcePin;
        boolean isDragging;
        boolean sourceIsInput;
    }

    private record NodeSelectorVariant(NodeDefinition.PinDefinition selectorPin, String option) {
    }

    private final DragState dragState;
    private FlowNodeWidget dragPinWidget;
    private ItemSelectorWidget nodeItemSelector;
    private FlowNodeWidget focusedNode;
    private boolean movingSelectedNodes = false;

    private String pendingSourceNodeId;
    private String pendingSourcePin;
    private boolean pendingSourceIsInput;

    private double dragMouseX = 0;
    private double dragMouseY = 0;

    private static class ClipboardData {
        final List<CopiedNode> nodes = new ArrayList<>();
        final List<CopiedConnection> connections = new ArrayList<>();
    }

    private static class CopiedNode {
        final String type;
        final double relativeX;
        final double relativeY;
        final Map<String, Object> inputValues;

        CopiedNode(String type, double relativeX, double relativeY, Map<String, Object> inputValues) {
            this.type = type;
            this.relativeX = relativeX;
            this.relativeY = relativeY;
            this.inputValues = new HashMap<>(inputValues);
        }
    }

    private static class CopiedConnection {
        final int sourceIndex;
        final String sourcePin;
        final int targetIndex;
        final String targetPin;

        CopiedConnection(int sourceIndex, String sourcePin, int targetIndex, String targetPin) {
            this.sourceIndex = sourceIndex;
            this.sourcePin = sourcePin;
            this.targetIndex = targetIndex;
            this.targetPin = targetPin;
        }
    }

    private final ClipboardData clipboard = new ClipboardData();

    private static class GraphSnapshot {
        final Map<String, FlowNode> nodes;
        final List<FlowConnection> connections;
        final Set<String> selectedIds;
        final boolean function;
        final List<FlowGraph.FunctionParameter> functionInputs;
        final List<FlowGraph.FunctionParameter> functionOutputs;
        final List<FlowGraph.EditorPassthrough> editorPassthroughs;

        private GraphSnapshot(Map<String, FlowNode> nodes, List<FlowConnection> connections, Set<String> selectedIds,
                              boolean function, List<FlowGraph.FunctionParameter> functionInputs,
                              List<FlowGraph.FunctionParameter> functionOutputs, List<FlowGraph.EditorPassthrough> editorPassthroughs) {
            this.nodes = nodes;
            this.connections = connections;
            this.selectedIds = selectedIds;
            this.function = function;
            this.functionInputs = functionInputs;
            this.functionOutputs = functionOutputs;
            this.editorPassthroughs = editorPassthroughs;
        }

        GraphSnapshot(Map<String, FlowNode> nodes, List<FlowConnection> connections, Set<String> selectedIds) {
            Map<String, FlowNode> copiedNodes = new HashMap<>();
            for (Map.Entry<String, FlowNode> entry : nodes.entrySet()) {
                FlowNode node = entry.getValue();
                copiedNodes.put(entry.getKey(), new FlowNode(
                        node.getType(),
                        node.getX(),
                        node.getY(),
                        new HashMap<>(node.getInputValues())
                ));
            }
            Map<String, FlowNode> immutableNodes = copiedNodes;
            List<FlowConnection> copiedConnections = new ArrayList<>(connections);
            Set<String> copiedSelectedIds = new HashSet<>(selectedIds);
            List<FlowGraph.FunctionParameter> emptyInputs = new ArrayList<>();
            List<FlowGraph.FunctionParameter> emptyOutputs = new ArrayList<>();
            List<FlowGraph.EditorPassthrough> emptyPassthroughs = new ArrayList<>();
            this.nodes = immutableNodes;
            this.connections = copiedConnections;
            this.selectedIds = copiedSelectedIds;
            this.function = false;
            this.functionInputs = emptyInputs;
            this.functionOutputs = emptyOutputs;
            this.editorPassthroughs = emptyPassthroughs;
        }

        GraphSnapshot(FlowGraph graph, Set<String> selectedIds) {
            this(
                copyNodes(graph.getNodes()),
                new ArrayList<>(graph.getConnections()),
                new HashSet<>(selectedIds),
                graph.isFunction(),
                copyFunctionParameters(graph.getFunctionInputs()),
                copyFunctionParameters(graph.getFunctionOutputs()),
                copyEditorPassthroughs(graph.getEditorPassthroughs())
            );
        }

        private static Map<String, FlowNode> copyNodes(Map<String, FlowNode> nodes) {
            Map<String, FlowNode> copied = new HashMap<>();
            for (Map.Entry<String, FlowNode> entry : nodes.entrySet()) {
                FlowNode node = entry.getValue();
                copied.put(entry.getKey(), new FlowNode(
                    node.getType(),
                    node.getX(),
                    node.getY(),
                    new HashMap<>(node.getInputValues())
                ));
            }
            return copied;
        }
    }

    private record InboundBoundary(String sourceNodeId, String sourcePin, String targetNodeId, String targetPin) {
    }

    private record OutboundBoundary(String sourceNodeId, String sourcePin, String targetNodeId, String targetPin) {
    }

    private record WireSegment(double x1, double y1, double x2, double y2) {
    }

    private record FanoutKey(String sourceNodeId, String sourcePin) {
    }

    private record PinPoint(double x, double y) {
    }

    private final List<GraphSnapshot> undoStack = new ArrayList<>();
    private final List<GraphSnapshot> redoStack = new ArrayList<>();
    private static final int MAX_UNDO_SIZE = 50;
    private boolean isUndoing = false;

    public FlowEditorScreen(FlowGraph graph, String serverId, Screen parent) {
        super();
        this.graph = graph;
        this.serverId = serverId;
        if (!(parent instanceof FlowEditorScreen)) {
            FlowEditorScreen.parent = parent;
        }
        this.dragState = new DragState();
        this.dragPinWidget = null;
        this.nodeItemSelector = null;
        OPEN_SCREENS.add(this);

        for (var entry : graph.getNodes().entrySet()) {
            String nodeId = entry.getKey();
            FlowNodeWidget widget = createNodeWidget(nodeId, entry.getValue());
            addWorldWidget(widget);
            widgetCache.put(entry.getKey(), widget);
        }
    }

    public FlowEditorScreen enableStudioMode() {
        this.studioMode = true;
        String title = FlowManager.getInstance() != null ? FlowManager.getInstance().getFlowName(serverId, graph.getId()) : graph.getId();
        if (title == null || title.isBlank()) {
            title = graph.getId();
        }
        addStudioDocument(graph.isFunction() ? ReSyncResourceDragPayload.FUNCTION : ReSyncResourceDragPayload.FLOW, graph.getId(), title, graph, null);
        return this;
    }

    public void openStudioFlow(FlowGraph targetGraph, String title) {
        if (targetGraph == null) {
            return;
        }
        openStudioGraphDocument(targetGraph.isFunction() ? ReSyncResourceDragPayload.FUNCTION : ReSyncResourceDragPayload.FLOW,
            targetGraph.getId(), title == null || title.isBlank() ? targetGraph.getId() : title, targetGraph);
    }

    private void openStudioGraphDocument(String type, String id, String title, FlowGraph targetGraph) {
        if (targetGraph == null) {
            return;
        }
        addStudioDocument(type, id, title == null || title.isBlank() ? id : title, targetGraph, null);
        rebuildStudioDocumentTabs();
        selectStudioDocument(ReSyncProjectMetadata.resourceKey(type, id));
    }

    private void openStudioDocument(String type, String id, String title) {
        addStudioDocument(type, id, title == null || title.isBlank() ? id : title, null, null);
        rebuildStudioDocumentTabs();
        selectStudioDocument(ReSyncProjectMetadata.resourceKey(type, id));
    }

    private void openStudioDesigner(String type, String id) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || id == null) {
            return;
        }
        if (ReSyncResourceDragPayload.GUI.equals(type)) {
            GuiDefinition gui = manager.getGuisForServer(serverId).get(id);
            if (gui != null) {
                openStudioViewDocument(type, id, manager.getGuiName(serverId, id), new ScreenBackedStudioView(this, new GuiDesignerScreen(gui, serverId, this)));
            }
            return;
        }
        if (ReSyncResourceDragPayload.SCOREBOARD.equals(type)) {
            ScoreboardDefinition scoreboard = manager.getScoreboardsForServer(serverId).get(id);
            if (scoreboard != null) {
                openStudioViewDocument(type, id, manager.getScoreboardName(serverId, id), new ScreenBackedStudioView(this, new ScoreboardDesignerScreen(scoreboard, serverId, this)));
            }
            return;
        }
        if (ReSyncResourceDragPayload.TAB.equals(type)) {
            TabDefinition tab = manager.getTabsForServer(serverId).get(id);
            if (tab != null) {
                openStudioViewDocument(type, id, manager.getTabName(serverId, id), new ScreenBackedStudioView(this, new TabDesignerScreen(tab, serverId, this)));
            }
        }
    }

    private void openStudioWorldGenDocument(String id, String title, WorldGenProject project) {
        WorldGenManager.getInstance().ensureLocalDefinitions(serverId);
        WorldGenProject initialProject = project != null ? project : WorldGenManager.getInstance().createProjectTemplate("Continental", id);
        openStudioViewDocument(ReSyncResourceDragPayload.WORLDGEN, id, title, new ScreenBackedStudioView(this, new WorldGenEditorScreen(serverId, null, this, initialProject)));
        if (project == null) {
            WorldGenManager.getInstance().requestProject(serverId, id);
        }
    }

    private void openStudioViewDocument(String type, String id, String title, ReSyncStudioView view) {
        openStudioViewDocument(type, id, title, null, view);
    }

    private void openStudioViewDocument(String type, String id, String title, FlowGraph targetGraph, ReSyncStudioView view) {
        addStudioDocument(type, id, title == null || title.isBlank() ? id : title, targetGraph, view);
        rebuildStudioDocumentTabs();
        selectStudioDocument(ReSyncProjectMetadata.resourceKey(type, id));
    }

    private void addStudioDocument(String type, String id, String title, FlowGraph targetGraph, ReSyncStudioView view) {
        String key = ReSyncProjectMetadata.resourceKey(type, id);
        for (StudioDocument document : studioDocuments) {
            if (document.key().equals(key)) {
                activeStudioDocument = document;
                FlowManager manager = FlowManager.getInstance();
                if (manager != null) {
                    ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
                    metadata.addOpenDocument(type, id, title);
                    manager.saveProjectMetadata(serverId, metadata);
                }
                return;
            }
        }
        StudioDocument document = new StudioDocument(type, id, title, targetGraph, view);
        studioDocuments.add(document);
        activeStudioDocument = document;
        FlowManager manager = FlowManager.getInstance();
        if (manager != null) {
            ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
            metadata.addOpenDocument(type, id, title);
            manager.saveProjectMetadata(serverId, metadata);
        }
    }

    public String getServerId() {
        return serverId;
    }

    public boolean loadStudioWorldGenProject(WorldGenProject project) {
        if (project == null) {
            return false;
        }
        boolean loaded = false;
        for (StudioDocument document : studioDocuments) {
            if (!ReSyncResourceDragPayload.WORLDGEN.equals(document.type()) || !project.getId().equals(document.id())) {
                continue;
            }
            if (document.view() instanceof ScreenBackedStudioView screenView && screenView.screen() instanceof WorldGenEditorScreen worldGenEditor) {
                worldGenEditor.loadProject(project);
                loaded = true;
            }
        }
        return loaded;
    }

    private String nodeRegistryServerId() {
        return activeNodeRegistryServerId != null && !activeNodeRegistryServerId.isBlank() ? activeNodeRegistryServerId : serverId;
    }

    public static void refreshCatalogForServer(String serverId) {
        for (FlowEditorScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.getServerId())) {
                screen.onOptionCatalogRefreshed();
            }
        }
    }

    protected void onOptionCatalogRefreshed() {
        closeNodeItemSelector();
        refreshNodeRegistry();
    }

    public String getFlowId() {
        return graph.getId();
    }

    public void focusContentBranch(String branchPin) {
        FlowNode startNode = CustomContentGraphAdapter.findStartNode(graph);
        String nodeId = startNode != null ? findNodeIdForNode(startNode) : null;
        if (nodeId == null) {
            return;
        }
        FlowNode focusNode = startNode;
        if (branchPin != null && graph.getConnections() != null) {
            for (FlowConnection connection : graph.getConnections()) {
                if (nodeId.equals(connection.getSourceNodeId()) && branchPin.equals(connection.getSourcePin())) {
                    FlowNode target = graph.getNodes().get(connection.getTargetNodeId());
                    if (target != null) {
                        focusNode = target;
                    }
                    break;
                }
            }
        }
        selectedNodeIds.clear();
        String focusNodeId = findNodeIdForNode(focusNode);
        selectedNodeIds.add(focusNodeId);
        focusedNode = widgetCache.get(focusNodeId);
        setZoomLevel(1.0f);
        setPan((float) (width / 2.0 - focusNode.getX()), (float) (height / 2.0 - focusNode.getY()));
    }

    public void applyGraph(FlowGraph sourceGraph) {
        if (sourceGraph == null) {
            return;
        }
        graph.setId(sourceGraph.getId());
        graph.getNodes().clear();
        if (sourceGraph.getNodes() != null) {
            for (Map.Entry<String, FlowNode> entry : sourceGraph.getNodes().entrySet()) {
                FlowNode node = entry.getValue();
                if (node == null) {
                    continue;
                }
                graph.getNodes().put(entry.getKey(), new FlowNode(
                        node.getType(),
                        node.getX(),
                        node.getY(),
                        node.getInputValues() != null ? new HashMap<>(node.getInputValues()) : new HashMap<>()
                ));
            }
        }
        graph.getConnections().clear();
        if (sourceGraph.getConnections() != null) {
            for (FlowConnection connection : sourceGraph.getConnections()) {
                if (connection == null) {
                    continue;
                }
                graph.getConnections().add(new FlowConnection(
                        connection.getSourceNodeId(),
                        connection.getSourcePin(),
                        connection.getTargetNodeId(),
                        connection.getTargetPin()
                ));
            }
        }
        graph.getLocalVariables().clear();
        if (sourceGraph.getLocalVariables() != null) {
            graph.getLocalVariables().addAll(sourceGraph.getLocalVariables());
        }
        graph.setFunction(sourceGraph.isFunction());
        graph.setFunctionInputs(copyFunctionParameters(sourceGraph.getFunctionInputs()));
        graph.setFunctionOutputs(copyFunctionParameters(sourceGraph.getFunctionOutputs()));
        graph.setEditorPassthroughs(copyEditorPassthroughs(sourceGraph.getEditorPassthroughs()));
        selectedNodeIds.clear();
        selectionBase.clear();
        selectedDragStartPositions.clear();
        focusedNode = null;
        dragState.sourceNodeId = null;
        dragState.sourcePin = null;
        dragState.isDragging = false;
        dragState.sourceIsInput = false;
        undoStack.clear();
        redoStack.clear();
        refreshNodeRegistry();
    }

    public String getDesktopAppId() {
        return "flow-editor";
    }

    public String getDesktopAppTitle() {
        return "Flow Editor";
    }

    public String getDesktopAppIconPath() {
        return "flow.png";
    }

    public void refreshNodeRegistry() {
        for (FlowNodeWidget widget : widgetCache.values()) {
            removeWorldWidget(widget);
        }
        widgetCache.clear();
        for (var entry : graph.getNodes().entrySet()) {
            String nodeId = entry.getKey();
            FlowNodeWidget widget = createNodeWidget(nodeId, entry.getValue());
            addWorldWidget(widget);
            widgetCache.put(entry.getKey(), widget);
        }
        refreshPalette();
    }

    protected FlowNodeWidget createNodeWidget(String nodeId, FlowNode node) {
        return new FlowNodeWidget((int) node.getX(), (int) node.getY(), node, graph, nodeId, nodeRegistryServerId(), () -> deleteNode(nodeId));
    }

    @Override
    public void init() {
        super.init();
        this.initialWidth = width;
        this.initialHeight = height;

        if (!initialized) {
            if (!studioMode) {
                headerBackground = new IconButton.Builder().pos(10, 10).size(1, 28).entranceAnimation(false).build();
                headerBackground.active = false;
                addHudWidget(headerBackground);
            } else {
                header().position(Position.TOP).size(30).visible(true);
            }

            createPaletteSidePanel();
            createHeaderButtons();
            if (studioMode) {
                createStudioWorkspaceChrome();
            }
            initialized = true;
        }

        if (headerBackground != null) {
            syncDebugHeaderVisibility();
            layoutHeaderButtons();
        }
        if (studioMode) {
            updateStudioLayout();
        }
    }

    private void refreshPalette() {
        if (paletteSidePanel == null) {
            return;
        }
        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(nodeRegistryServerId())) {
            populateCategoryPopups();
        } else {
            populateFallbackPopups();
        }
    }

    private void createPaletteSidePanel() {
        paletteSidePanel = new SidePanel(this, "palettePanel", this::updatePositions).width(120).y(palettePanelTop()).height(palettePanelHeight()).show();
        paletteSidePanel.container().layout(new ManagedLayout()).columns(1).padding(5);

        categoryPopups.clear();
        categoryOrder = resolveCategoryOrder();
        for (NodeDefinition.NodeCategory category : categoryOrder) {
            PopupWidget popup = new PopupWidget.Builder(getCategoryLabel(category)).enableCollapseOnClose(true).build();
            popup.collapse(true);
            categoryPopups.put(category, popup);
            paletteSidePanel.addWidget(popup);
        }

        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(nodeRegistryServerId())) {
            populateCategoryPopups();
        } else {
            populateFallbackPopups();
        }
    }

    private void createStudioWorkspaceChrome() {
        studioTabsManager = tabs().builder()
            .position(10, 10)
            .size(width - 220, 18)
            .allowAdd(false)
            .allowClose(true)
            .allowReorder(true)
            .onTabClosed(tab -> {
                Object data = tab.getData();
                if (data instanceof String key) {
                    studioDocuments.removeIf(document -> {
                        boolean match = document.key().equals(key);
                        if (match && document.view() != null) {
                            document.view().closed();
                        }
                        return match;
                    });
                    activeStudioDocument = studioDocuments.isEmpty() ? null : studioDocuments.getFirst();
                    if (activeStudioDocument != null) {
                        selectStudioDocument(activeStudioDocument.key());
                    } else {
                        refreshActiveViewHeaderButtons();
                        refreshStudioResourcePanel();
                    }
                }
            })
            .onTabSelected(tab -> {
                Object data = tab.getData();
                if (data instanceof String key) {
                    selectStudioDocument(key);
                }
            })
            .build();
        studioContentBrowser = new ReSyncContentBrowserWidget(8, height - STUDIO_CONTENT_BROWSER_HEIGHT - 8, width - 16, STUDIO_CONTENT_BROWSER_HEIGHT);
        addHudWidget(studioContentBrowser);
        studioResourcePanel = new SidePanel(this, "studioResourcePanel", this::updatePositions).width(180).y(54).height(height - 65 - STUDIO_CONTENT_BROWSER_HEIGHT).show();
        studioResourcePanel.container().layout(new ManagedLayout()).columns(1).padding(5);
        studioResourcePanel.hide();
        rebuildStudioDocumentTabs();
        if (activeStudioDocument != null) {
            selectStudioDocument(activeStudioDocument.key());
        } else {
            refreshActiveViewHeaderButtons();
        }
    }

    private void openStudioResource(ReSyncProjectMetadata.ResourceEntry resource) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || resource == null) {
            return;
        }
        if (ReSyncResourceDragPayload.FLOW.equals(resource.getType()) || ReSyncResourceDragPayload.FUNCTION.equals(resource.getType()) || ReSyncResourceDragPayload.COMMAND.equals(resource.getType())) {
            FlowGraph targetGraph = manager.getFlowsForServer(serverId).get(resource.getId());
            if (targetGraph == null && ReSyncResourceDragPayload.COMMAND.equals(resource.getType())) {
                targetGraph = manager.createFlow(serverId, resource.getId(), false, "Command");
                manager.setCommandBinding(serverId, resource.getId(), resource.getId());
            }
            if (targetGraph != null) {
                openStudioGraphDocument(resource.getType(), resource.getId(), resource.getDisplayName(), targetGraph);
            }
            return;
        }
        if (ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(resource.getType())) {
            String graphId = resource.getId();
            CustomContentDefinition content = manager.getCustomContentForServer(serverId).get(resource.getId());
            if (content != null && content.getFlowId() != null && !content.getFlowId().isBlank()) {
                graphId = content.getFlowId();
            }
            FlowGraph contentGraph = manager.getFlowsForServer(serverId).get(graphId);
            openStudioViewDocument(resource.getType(), resource.getId(), resource.getDisplayName(), contentGraph, new ScreenBackedStudioView(this, new ContentStudioScreen(serverId, null, graphId, this)));
            return;
        }
        if (ReSyncResourceDragPayload.WORLDGEN.equals(resource.getType())) {
            WorldGenManager worldGenManager = WorldGenManager.getInstance();
            WorldGenProject project = worldGenManager.getCachedProject(serverId, resource.getId());
            openStudioWorldGenDocument(resource.getId(), resource.getDisplayName(), project);
            return;
        }
        if (ReSyncResourceDragPayload.GUI.equals(resource.getType())
            || ReSyncResourceDragPayload.SCOREBOARD.equals(resource.getType())
            || ReSyncResourceDragPayload.TAB.equals(resource.getType())) {
            openStudioDesigner(resource.getType(), resource.getId());
            return;
        }
        if (ReSyncResourceDragPayload.WORLD.equals(resource.getType())) {
            openStudioDocument(resource.getType(), resource.getId(), resource.getDisplayName());
        }
    }

    private List<ReSyncProjectMetadata.FolderEntry> studioFolders(String parentPath) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return List.of();
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        return metadata.getFolders().stream()
            .filter(folder -> parentPath.equals(folder.getParentPath()))
            .sorted(Comparator.comparingInt(ReSyncProjectMetadata.FolderEntry::getSortOrder).thenComparing(ReSyncProjectMetadata.FolderEntry::getName))
            .toList();
    }

    private List<ReSyncProjectMetadata.FolderEntry> studioAllFolders() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return List.of();
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        return metadata.getFolders();
    }

    private List<ReSyncProjectMetadata.ResourceEntry> studioResources(String folderPath) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return List.of();
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        return metadata.getResources().stream()
            .filter(resource -> folderPath.equals(resource.getPath()))
            .sorted(Comparator.comparing(ReSyncProjectMetadata.ResourceEntry::getDisplayName))
            .toList();
    }

    private List<ReSyncProjectMetadata.ResourceEntry> studioAllResources() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return List.of();
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        return metadata.getResources();
    }

    private void rebuildStudioDocumentTabs() {
        if (studioTabsManager == null) {
            return;
        }
        studioTabsManager.clearTabs();
        for (StudioDocument document : studioDocuments) {
            Container container = new Container("document-" + document.key(), 0, 0, 1, 1);
            TabsManager.Tab tab = studioTabsManager.addTab(document.title(), container);
            tab.setData(document.key());
            if (document == activeStudioDocument) {
                studioTabsManager.setActiveTab(container);
            }
        }
    }

    private void refreshStudioResourcePanel() {
        if (studioResourcePanel == null) {
            return;
        }
        commandLabelInput = null;
        commandPathsInput = null;
        commandStructuredToggle = null;
        studioResourcePanel.container().clearWidgets();
        if (activeStudioDocument == null) {
            studioResourcePanel.hide();
            return;
        }
        if (ReSyncResourceDragPayload.FLOW.equals(activeStudioDocument.type())
            || ReSyncResourceDragPayload.FUNCTION.equals(activeStudioDocument.type())
            || ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(activeStudioDocument.type())) {
            studioResourcePanel.hide();
            return;
        }
        if (ReSyncResourceDragPayload.COMMAND.equals(activeStudioDocument.type())) {
            studioResourcePanel.left();
        } else {
            studioResourcePanel.right();
        }
        studioResourcePanel.show();
        if (ReSyncResourceDragPayload.GUI.equals(activeStudioDocument.type())) {
            buildGuiResourcePanel();
        } else if (ReSyncResourceDragPayload.COMMAND.equals(activeStudioDocument.type())) {
            buildCommandResourcePanel();
        } else if (ReSyncResourceDragPayload.SCOREBOARD.equals(activeStudioDocument.type())) {
            buildScoreboardResourcePanel();
        } else if (ReSyncResourceDragPayload.TAB.equals(activeStudioDocument.type())) {
            buildTabResourcePanel();
        } else if (ReSyncResourceDragPayload.WORLDGEN.equals(activeStudioDocument.type())) {
            buildWorldGenResourcePanel();
        } else if (ReSyncResourceDragPayload.WORLD.equals(activeStudioDocument.type())) {
            buildWorldResourcePanel();
        }
        studioResourcePanel.container().updateWidgetPositions();
    }

    private void buildCommandResourcePanel() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        TriggerBinding binding = manager.getCommandBinding(serverId, activeStudioDocument.id());
        CommandBindingContext command = parseCommandContext(binding != null ? binding.getContext() : activeStudioDocument.id());
        commandLabelInput = panelInput("Command", command.command != null && !command.command.isBlank() ? command.command : activeStudioDocument.id());
        commandPathsInput = panelInput("Paths | separated", String.join("|", command.subcommands != null ? command.subcommands : List.of()));
        commandStructuredToggle = new ToggleWidget.Builder()
            .label("Structured")
            .toggled(command.structured != null && command.structured)
            .size(170, 18)
            .build();
        studioResourcePanel.addWidget(commandLabelInput, commandPathsInput, commandStructuredToggle);
    }

    private void buildGuiResourcePanel() {
        FlowManager manager = FlowManager.getInstance();
        GuiDefinition gui = manager != null ? manager.getGuisForServer(serverId).get(activeStudioDocument.id()) : null;
        if (gui == null) {
            return;
        }
        TextInputWidget title = panelInput("Title", gui.getTitle());
        TextInputWidget rows = panelInput("Rows", String.valueOf(gui.getRows()));
        ToggleWidget playerInventory = new ToggleWidget.Builder()
            .label("Inventory")
            .toggled(gui.isExtendToPlayerInventory())
            .size(170, 18)
            .build();
        studioResourcePanel.addWidget(title, rows, playerInventory, panelSaveButton(() -> {
            gui.setTitle(title.getText());
            gui.setRows(parseInt(rows.getText(), gui.getRows(), 1, 6));
            gui.setExtendToPlayerInventory(playerInventory.getValue());
            manager.saveGui(serverId, gui);
        }));
    }

    private void buildScoreboardResourcePanel() {
        FlowManager manager = FlowManager.getInstance();
        ScoreboardDefinition scoreboard = manager != null ? manager.getScoreboardsForServer(serverId).get(activeStudioDocument.id()) : null;
        if (scoreboard == null) {
            return;
        }
        TextInputWidget title = panelInput("Title", scoreboard.getTitle());
        TextInputWidget objective = panelInput("Objective", scoreboard.getObjectiveId());
        TextInputWidget lines = panelInput("Lines | separated", String.join("|", scoreboard.getLines()));
        studioResourcePanel.addWidget(title, objective, lines, panelSaveButton(() -> {
            scoreboard.setTitle(title.getText());
            scoreboard.setObjectiveId(objective.getText());
            scoreboard.setLines(parseLines(lines.getText()));
            manager.saveScoreboard(serverId, scoreboard);
        }));
    }

    private void buildTabResourcePanel() {
        FlowManager manager = FlowManager.getInstance();
        TabDefinition tab = manager != null ? manager.getTabsForServer(serverId).get(activeStudioDocument.id()) : null;
        if (tab == null) {
            return;
        }
        TextInputWidget header = panelInput("Header", tab.getHeader());
        TextInputWidget entry = panelInput("Entry", tab.getEntryFormat());
        TextInputWidget footer = panelInput("Footer", tab.getFooter());
        studioResourcePanel.addWidget(header, entry, footer, panelSaveButton(() -> {
            tab.setHeader(header.getText());
            tab.setEntryFormat(entry.getText());
            tab.setFooter(footer.getText());
            manager.saveTab(serverId, tab);
        }));
    }

    private void buildWorldGenResourcePanel() {
        TextInputWidget id = panelInput("Project", activeStudioDocument.id());
        id.active = false;
        studioResourcePanel.addWidget(id, panelSaveButton(() -> {
            WorldGenProject project = WorldGenManager.getInstance().createProjectTemplate("Continental", activeStudioDocument.id());
            WorldGenManager.getInstance().saveWorldGen(serverId, project);
        }));
    }

    private void buildWorldResourcePanel() {
        TextInputWidget id = panelInput("World", activeStudioDocument.id());
        id.active = false;
        studioResourcePanel.addWidget(id);
    }

    private TextInputWidget panelInput(String placeholder, String value) {
        return new TextInputWidget.Builder()
            .placeholder(placeholder)
            .text(value != null ? value : "")
            .size(170, 20)
            .build();
    }

    private AnimatedButton panelSaveButton(Runnable action) {
        return new AnimatedButton.Builder()
            .label("Save")
            .size(170, 18)
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(action)
            .build();
    }

    private int parseInt(String value, int fallback, int min, int max) {
        try {
            return Math.clamp(Integer.parseInt(value), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<String> parseLines(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\|")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private List<String> parseCommandPaths(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        List<String> paths = new ArrayList<>();
        for (String path : text.split("\\|")) {
            String value = path.trim();
            if (!value.isBlank()) {
                paths.add(value);
            }
        }
        return paths;
    }

    private CommandBindingContext parseCommandContext(String context) {
        CommandBindingContext parsed = new CommandBindingContext();
        parsed.subcommands = new ArrayList<>();
        parsed.structured = false;
        if (context == null || context.isBlank()) {
            return parsed;
        }
        String trimmed = context.trim();
        if (trimmed.startsWith("{")) {
            try {
                CommandBindingContext decoded = gson.fromJson(trimmed, CommandBindingContext.class);
                if (decoded != null) {
                    parsed.command = normalizeCommandLabel(decoded.command);
                    parsed.subcommands = decoded.subcommands != null ? decoded.subcommands : new ArrayList<>();
                    parsed.structured = decoded.structured != null && decoded.structured;
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }
        parsed.command = normalizeCommandLabel(trimmed);
        return parsed;
    }

    private String encodeCommandContext(CommandBindingContext command) {
        if (command == null) {
            return "";
        }
        command.command = normalizeCommandLabel(command.command);
        command.subcommands = command.subcommands != null ? command.subcommands : new ArrayList<>();
        command.structured = command.structured != null && command.structured;
        return command.subcommands.isEmpty() && !command.structured ? command.command : gson.toJson(command);
    }

    private String normalizeCommandLabel(String label) {
        String command = label != null ? label.trim().toLowerCase(Locale.ROOT) : "";
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        return command.matches("^[a-zA-Z0-9:_-]+$") ? command : "";
    }

    private void selectStudioDocument(String key) {
        for (StudioDocument document : studioDocuments) {
            if (document.key().equals(key)) {
                syncNodePositions();
                activeStudioDocument = document;
                if (document.view() != null) {
                    document.view().selected();
                }
                refreshActiveViewHeaderButtons();
                activeNodeRegistryServerId = ReSyncResourceDragPayload.WORLDGEN.equals(document.type()) ? WorldGenManager.registryServerId(serverId) : serverId;
                graph = document.graph() != null ? document.graph() : studioEmptyGraph;
                selectedNodeIds.clear();
                selectionBase.clear();
                selectedDragStartPositions.clear();
                focusedNode = null;
                dragState.isDragging = false;
                pendingSourceNodeId = null;
                pendingSourcePin = null;
                undoStack.clear();
                redoStack.clear();
                refreshNodeRegistry();
                if (paletteSidePanel != null) {
                    if (document.view() != null || document.graph() == null || ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(document.type())) {
                        paletteSidePanel.hide();
                    } else {
                        paletteSidePanel.show();
                    }
                }
                if (studioResourcePanel != null) {
                    if (document.view() != null) {
                        studioResourcePanel.hide();
                    } else {
                        studioResourcePanel.show();
                    }
                }
                if (document.view() == null) {
                    refreshStudioResourcePanel();
                }
                updatePositions();
                return;
            }
        }
    }

    private void populateCategoryPopups() {
        List<NodeDefinition.NodeCategory> order = categoryOrder.isEmpty() ? resolveCategoryOrder() : categoryOrder;
        Map<NodeDefinition.NodeCategory, List<NodeDefinition>> categories = new HashMap<>();
        for (NodeDefinition.NodeCategory category : order) {
            categories.put(category, new ArrayList<>());
        }

        for (NodeDefinition def : NodeRegistry.getInstance().getAllDefinitions(nodeRegistryServerId()).values()) {
            if (def.isHidden() || !isAllowedInCurrentEditor(def)) {
                continue;
            }
            NodeDefinition.NodeCategory category = def.getCategory();
            if (def.getId().startsWith("event:")) {
                category = NodeDefinition.NodeCategory.EVENT;
            }
            categories.computeIfAbsent(category, ignored -> new ArrayList<>()).add(def);
        }

        Comparator<NodeDefinition> comparator = Comparator
                .comparingInt(NodeDefinition::getPriority)
                .thenComparing(NodeDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER);

        for (NodeDefinition.NodeCategory category : order) {
            PopupWidget targetPopup = getCategoryPopup(category);
            if (targetPopup == null) {
                continue;
            }
            targetPopup.clearRows();
            List<NodeDefinition> nodes = categories.getOrDefault(category, new ArrayList<>());
            nodes.sort(comparator);
            if (isContentEditor() && category == NodeDefinition.NodeCategory.ABILITY && !nodes.isEmpty()) {
                targetPopup.collapse(false);
            }
            for (NodeDefinition def : nodes) {
                IconButton btn = new IconButton.Builder()
                        .label(def.getDisplayName())
                        .onClick(() -> addNodeAtCenter(def.getId()))
                        .build();
                targetPopup.addRow("", Collections.singletonList(btn), 20, true, false);
            }
        }
    }

    private List<NodeDefinition.NodeCategory> resolveCategoryOrder() {
        List<FlowCategoryMetadata> meta = NodeRegistry.getInstance().getServerCategories(nodeRegistryServerId());
        List<NodeDefinition.NodeCategory> result = new ArrayList<>();
        for (FlowCategoryMetadata m : meta) {
            result.add(NodeDefinition.NodeCategory.fromString(m.getId()));
        }
        return result;
    }

    private void populateFallbackPopups() {
        PopupWidget eventsPopup = getCategoryPopup(NodeDefinition.NodeCategory.EVENT);
        PopupWidget actionsPopup = getCategoryPopup(NodeDefinition.NodeCategory.ACTION);
        PopupWidget logicPopup = getCategoryPopup(NodeDefinition.NodeCategory.LOGIC);
        PopupWidget dataPopup = getCategoryPopup(NodeDefinition.NodeCategory.DATA);

        if (eventsPopup != null) {
            eventsPopup.clearRows();
        }
        if (actionsPopup != null) {
            actionsPopup.clearRows();
        }
        if (logicPopup != null) {
            logicPopup.clearRows();
        }
        if (dataPopup != null) {
            dataPopup.clearRows();
        }
    }

    private PopupWidget getCategoryPopup(NodeDefinition.NodeCategory category) {
        return categoryPopups.get(category);
    }

    private String getCategoryLabel(NodeDefinition.NodeCategory category) {
        return category.getDisplayName();
    }

    FlowDebugController debugController() {
        FlowManager flowManager = FlowManager.getInstance();
        return flowManager != null ? flowManager.getDebugController() : null;
    }

    public void refreshDebugState() {
        FlowDebugController controller = debugController();
        debugMode = controller != null && controller.isEnabled();
        syncDebugHeaderVisibility();
        if (headerBackground != null) {
            layoutHeaderButtons();
        }
    }

    private boolean isContentEditor() {
        return CustomContentGraphAdapter.isContentGraph(graph);
    }

    private boolean isAllowedInCurrentEditor(NodeDefinition definition) {
        if (!isContentEditor()) {
            return true;
        }
        String id = definition.getId();
        if (id != null && (id.startsWith("custom_content.") || id.startsWith("ability."))) {
            return true;
        }
        NodeDefinition.NodeCategory category = definition.getCategory();
        return category == NodeDefinition.NodeCategory.LOGIC
            || category == NodeDefinition.NodeCategory.DATA
            || category == NodeDefinition.NodeCategory.VARIABLE
            || category == NodeDefinition.NodeCategory.FUNCTION
            || category == NodeDefinition.NodeCategory.ENTITY
            || category == NodeDefinition.NodeCategory.BLOCK
            || category == NodeDefinition.NodeCategory.ITEM
            || category == NodeDefinition.NodeCategory.WORLD
            || category == NodeDefinition.NodeCategory.VISUAL
            || category == NodeDefinition.NodeCategory.UTILITY;
    }

    private void addNodeAtCenter(String type) {
        double[] center = screenToWorld(width / 2.0, height / 2.0);
        int x = (int) (center[0] - 50);
        int y = (int) (center[1] - 20);
        addNode(x, y, type, null);
    }

    protected void createHeaderButtons() {
        IconButton backButton = new IconButton.Builder().size(18, 18).imagePath("close.png")
                .onClick(() -> {
                    if (parent != null) {
                        client.setScreen(parent);
                    } else {
                        close();
                    }
                })
                .build();
        addHeaderButton(backButton);

        IconButton saveButton = new IconButton.Builder()
                .size(18, 18)
                .imagePath("save.png")
                .onClick(this::onSave)
                .build();
        addHeaderButton(saveButton);

        IconButton organizeButton = new IconButton.Builder()
                .size(18, 18)
                .imagePath("layout.png")
                .onClick(this::organizeGraph)
                .build();
        addHeaderButton(organizeButton);

        debugToggleButton = new IconButton.Builder()
                .size(18, 18)
                .imagePath("report.png")
                .hint("Debug")
                .onClick(this::toggleDebugMode)
                .build();
        addHeaderButton(debugToggleButton);

        debugResumeButton = debugHeaderButton("Continue", 62, () -> {
            FlowDebugController debug = debugController();
            if (debug != null) {
                FlowDebugController.DebugSession session = debug.getActiveSession();
                debug.resume(serverId, session != null ? session.sessionId() : "");
            }
        });
        debugStepButton = debugHeaderButton("Step", 42, () -> stepDebug("into"));
        debugStopButton = debugHeaderButton("Stop", 42, () -> {
            FlowDebugController debug = debugController();
            if (debug != null) {
                FlowDebugController.DebugSession session = debug.getActiveSession();
                debug.stop(serverId, session != null ? session.sessionId() : "");
            }
        });
        addHeaderButton(debugResumeButton);
        addHeaderButton(debugStepButton);
        addHeaderButton(debugStopButton);

        if (showExtractButton()) {
            IconButton extractButton = new IconButton.Builder()
                    .size(18, 18)
                    .imagePath("copy.png")
                    .onClick(this::showExtractFunctionPopup)
                    .build();
            addHeaderButton(extractButton);
        }
        addCustomHeaderButtons();
        syncDebugHeaderVisibility();
    }

    private IconButton debugHeaderButton(String label, int width, Runnable action) {
        return new IconButton.Builder()
                .size(width, 18)
                .label(label)
                .hint(label)
                .onClick(action)
                .autoWidthOnTextChange(true)
                .build();
    }

    private void toggleDebugMode() {
        debugMode = !debugMode;
        FlowDebugController debug = debugController();
        if (debug != null) {
            debug.setEnabled(serverId, debugMode);
        }
        syncDebugHeaderVisibility();
        layoutHeaderButtons();
    }

    private void syncDebugHeaderVisibility() {
        boolean showControls = debugMode;
        if (debugResumeButton != null) {
            debugResumeButton.visible = showControls;
        }
        if (debugStepButton != null) {
            debugStepButton.visible = showControls;
        }
        if (debugStopButton != null) {
            debugStopButton.visible = showControls;
        }
    }

    private void stepDebug(String mode) {
        FlowDebugController debug = debugController();
        if (debug == null) {
            return;
        }
        FlowDebugController.DebugSession session = debug.getActiveSession();
        String sessionId = session != null ? session.sessionId() : "";
        switch (mode) {
            case "into" -> debug.stepInto(serverId, sessionId);
            case "over" -> debug.stepOver(serverId, sessionId);
            case "out" -> debug.stepOut(serverId, sessionId);
            default -> {
            }
        }
    }

    protected void addHeaderButton(IconButton button) {
        if (button == null) {
            return;
        }
        headerButtons.add(button);
        button.entranceAnimationEnabled = false;
        if (studioMode) {
            button.visible = false;
        } else {
            addDrawableChild(button);
        }
    }

    @Override
    public List<AnimatedWidget> getStudioHeaderButtons() {
        List<IconButton> source = headerButtons;
        if (parent instanceof FlowEditorScreen && !headerButtons.isEmpty()) {
            source = headerButtons.subList(1, headerButtons.size());
        }
        return source.stream()
            .filter(button -> button != null && button.visible)
            .map(button -> (AnimatedWidget) button)
            .toList();
    }

    protected boolean showExtractButton() {
        return true;
    }

    protected void addCustomHeaderButtons() {
    }

    private void showExtractFunctionPopup() {
        if (selectedNodeIds.isEmpty()) {
            new Notification("Error", "Select Nodes", Notification.Type.ERROR);
            return;
        }
        PopupWidget.Builder builder = new PopupWidget.Builder("Extract Function").setResizable(false);
        TextInputWidget idInput = new TextInputWidget.Builder()
                .placeholder("function_id")
                .size(200, 22)
                .build();
        builder.addRow("ID", true, 22, idInput);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton extractButton = new AnimatedButton.Builder()
                .label("Extract")
                .accentType(ThemeManager.getAccent("nice"))
                .onClick(() -> {
                    String id = idInput.getText() != null ? idInput.getText().trim() : "";
                    if (!id.matches("^[a-zA-Z0-9_]+$")) {
                        new Notification("Error", "Invalid ID", Notification.Type.ERROR);
                        return;
                    }
                    if (extractSelectionToFunction(id)) {
                        if (popupRef[0] != null) {
                            popupRef[0].hide();
                        }
                    }
                })
                .build();

        builder.addRow("", true, 20, extractButton);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private boolean extractSelectionToFunction(String functionId) {
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager == null || serverId == null || functionId == null || functionId.isBlank()) {
            return false;
        }
        if (flowManager.getFlowsForServer(serverId).containsKey(functionId)) {
            new Notification("Error", "Function ID Exists", Notification.Type.ERROR);
            return false;
        }
        if (selectedNodeIds.isEmpty()) {
            new Notification("Error", "Select Nodes", Notification.Type.ERROR);
            return false;
        }
        captureSnapshot();

        Set<String> selected = new HashSet<>(selectedNodeIds);
        List<InboundBoundary> inbound = new ArrayList<>();
        List<OutboundBoundary> outbound = new ArrayList<>();
        List<FlowConnection> internal = new ArrayList<>();

        for (FlowConnection connection : graph.getConnections()) {
            boolean sourceSelected = selected.contains(connection.getSourceNodeId());
            boolean targetSelected = selected.contains(connection.getTargetNodeId());
            if (sourceSelected && targetSelected) {
                internal.add(new FlowConnection(
                        connection.getSourceNodeId(),
                        connection.getSourcePin(),
                        connection.getTargetNodeId(),
                        connection.getTargetPin()
                ));
                continue;
            }
            if (!sourceSelected && targetSelected) {
                inbound.add(new InboundBoundary(
                        connection.getSourceNodeId(),
                        connection.getSourcePin(),
                        connection.getTargetNodeId(),
                        connection.getTargetPin()
                ));
                continue;
            }
            if (sourceSelected) {
                outbound.add(new OutboundBoundary(
                        connection.getSourceNodeId(),
                        connection.getSourcePin(),
                        connection.getTargetNodeId(),
                        connection.getTargetPin()
                ));
            }
        }

        FlowGraph functionGraph = flowManager.createFlow(serverId, functionId, true);
        functionGraph.getNodes().clear();
        functionGraph.getConnections().clear();
        functionGraph.getLocalVariables().clear();
        functionGraph.setFunction(true);
        functionGraph.setFunctionInputs(new ArrayList<>());
        functionGraph.setFunctionOutputs(new ArrayList<>());

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        double centerX = 0;
        double centerY = 0;
        int count = 0;

        for (String nodeId : selected) {
            FlowNode node = graph.getNodes().get(nodeId);
            if (node == null) {
                continue;
            }
            functionGraph.getNodes().put(nodeId, new FlowNode(
                    node.getType(),
                    node.getX(),
                    node.getY(),
                    node.getInputValues() != null ? new HashMap<>(node.getInputValues()) : new HashMap<>())
            );
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX());
            maxY = Math.max(maxY, node.getY());
            centerX += node.getX();
            centerY += node.getY();
            count++;
        }
        if (count == 0) {
            return false;
        }
        centerX /= count;
        centerY /= count;

        functionGraph.getConnections().addAll(internal);
        String functionStartId = UUID.randomUUID().toString();
        String functionEndId = UUID.randomUUID().toString();

        functionGraph.getNodes().put(functionStartId, new FlowNode("function_start", minX - 220, minY, new HashMap<>()));
        functionGraph.getNodes().put(functionEndId, new FlowNode("function_end", maxX + 220, maxY, new HashMap<>()));

        Set<String> entryTargets = new HashSet<>();
        Set<String> exitSources = new HashSet<>();
        for (InboundBoundary connection : inbound) {
            if ("flow".equals(connection.targetPin())) {
                entryTargets.add(connection.targetNodeId());
            }
        }
        for (OutboundBoundary connection : outbound) {
            if ("flow".equals(connection.sourcePin())) {
                exitSources.add(connection.sourceNodeId());
            }
        }
        for (String entryTarget : entryTargets) {
            functionGraph.getConnections().add(new FlowConnection(functionStartId, "flow", entryTarget, "flow"));
        }
        for (String exitSource : exitSources) {
            functionGraph.getConnections().add(new FlowConnection(exitSource, "flow", functionEndId, "flow"));
        }

        Map<InboundBoundary, String> inboundParams = new HashMap<>();
        Map<OutboundBoundary, String> outboundParams = new HashMap<>();
        Set<String> usedInputNames = new HashSet<>();
        Set<String> usedOutputNames = new HashSet<>();

        for (InboundBoundary connection : inbound) {
            if ("flow".equals(connection.targetPin())) {
                continue;
            }
            String parameterName = uniqueParameterName(connection.targetPin(), usedInputNames);
            usedInputNames.add(parameterName);
            FlowDataType parameterType = resolveTargetPinType(connection.targetNodeId(), connection.targetPin());
            functionGraph.getFunctionInputs().add(new FlowGraph.FunctionParameter(parameterName, parameterType));
            functionGraph.getConnections().add(new FlowConnection(functionStartId, parameterName, connection.targetNodeId(), connection.targetPin()));
            inboundParams.put(connection, parameterName);
        }

        for (OutboundBoundary connection : outbound) {
            if ("flow".equals(connection.sourcePin())) {
                continue;
            }
            String parameterName = uniqueParameterName(connection.sourcePin(), usedOutputNames);
            usedOutputNames.add(parameterName);
            FlowDataType parameterType = resolveSourcePinType(connection.sourceNodeId(), connection.sourcePin());
            functionGraph.getFunctionOutputs().add(new FlowGraph.FunctionParameter(parameterName, parameterType));
            functionGraph.getConnections().add(new FlowConnection(connection.sourceNodeId(), connection.sourcePin(), functionEndId, parameterName));
            outboundParams.put(connection, parameterName);
        }

        flowManager.saveFlow(serverId, functionGraph);

        String callNodeType = CUSTOM_FUNCTION_NODE_PREFIX + functionId;
        NodeDefinition callDef = buildCustomFunctionNodeDefinition(callNodeType, functionId, functionGraph);
        if (NodeRegistry.getInstance() != null) {
            NodeRegistry.getInstance().registerServerDefinition(serverId, callDef);
        }

        graph.getConnections().removeIf(connection -> selected.contains(connection.getSourceNodeId()) || selected.contains(connection.getTargetNodeId()));
        for (String nodeId : selected) {
            FlowNodeWidget widget = widgetCache.remove(nodeId);
            if (widget != null) {
                removeWorldWidget(widget);
            }
            graph.getNodes().remove(nodeId);
        }

        String callNodeId = UUID.randomUUID().toString();
        FlowNode callNode = new FlowNode(callNodeType, centerX, centerY, new HashMap<>());
        graph.getNodes().put(callNodeId, callNode);
        FlowNodeWidget callWidget = createNodeWidget(callNodeId, callNode);
        addWorldWidget(callWidget);
        widgetCache.put(callNodeId, callWidget);

        for (InboundBoundary connection : inbound) {
            if ("flow".equals(connection.targetPin())) {
                graph.getConnections().add(new FlowConnection(connection.sourceNodeId(), connection.sourcePin(), callNodeId, "flow"));
                continue;
            }
            String parameterName = inboundParams.get(connection);
            if (parameterName == null) {
                continue;
            }
            removeExistingInputConnection(callNodeId, parameterName);
            graph.getConnections().add(new FlowConnection(connection.sourceNodeId(), connection.sourcePin(), callNodeId, parameterName));
        }

        for (OutboundBoundary connection : outbound) {
            if ("flow".equals(connection.sourcePin())) {
                removeExistingInputConnection(connection.targetNodeId(), connection.targetPin());
                graph.getConnections().add(new FlowConnection(callNodeId, "flow", connection.targetNodeId(), connection.targetPin()));
                continue;
            }
            String parameterName = outboundParams.get(connection);
            if (parameterName == null) {
                continue;
            }
            removeExistingInputConnection(connection.targetNodeId(), connection.targetPin());
            graph.getConnections().add(new FlowConnection(callNodeId, parameterName, connection.targetNodeId(), connection.targetPin()));
        }

        refreshInputWidgets(callNodeId);
        for (InboundBoundary connection : inbound) {
            refreshInputWidgets(connection.sourceNodeId());
            refreshInputWidgets(connection.targetNodeId());
        }
        for (OutboundBoundary connection : outbound) {
            refreshInputWidgets(connection.targetNodeId());
        }

        selectedNodeIds.clear();
        selectedNodeIds.add(callNodeId);
        focusedNode = callWidget;
        new Notification("Function Extracted", functionId, Notification.Type.SUCCESS);
        return true;
    }

    private String uniqueParameterName(String baseName, Set<String> usedNames) {
        String normalized = (baseName == null || baseName.isBlank()) ? "value" : baseName.trim();
        normalized = normalized.replaceAll("[^a-zA-Z0-9_]", "_");
        if (!normalized.matches("^[a-zA-Z_].*")) {
            normalized = "p_" + normalized;
        }
        String candidate = normalized;
        int index = 2;
        while (usedNames.contains(candidate)) {
            candidate = normalized + "_" + index;
            index++;
        }
        return candidate;
    }

    private FlowDataType resolveTargetPinType(String nodeId, String pinName) {
        if (nodeId == null || pinName == null || nodeId.isBlank() || pinName.isBlank()) {
            return FlowDataType.ANY;
        }
        FlowNodeWidget widget = widgetCache.get(nodeId);
        if (widget == null) {
            return FlowDataType.ANY;
        }
        FlowDataType type = widget.getPinType(pinName, true);
        return type != null ? type : FlowDataType.ANY;
    }

    private FlowDataType resolveSourcePinType(String nodeId, String pinName) {
        if (nodeId == null || pinName == null || nodeId.isBlank() || pinName.isBlank()) {
            return FlowDataType.ANY;
        }
        FlowNodeWidget widget = widgetCache.get(nodeId);
        if (widget == null) {
            return FlowDataType.ANY;
        }
        FlowDataType type = widget.getPinType(pinName, false);
        return type != null ? type : FlowDataType.ANY;
    }

    private NodeDefinition buildCustomFunctionNodeDefinition(String nodeType, String functionId, FlowGraph functionGraph) {
        NodeDefinition.Builder builder = new NodeDefinition.Builder(nodeType, formatFunctionDisplayName(functionId), NodeDefinition.NodeCategory.FUNCTION);
        builder.input("flow", NodeDefinition.PinType.FLOW, FlowDataType.EXECUTION);
        builder.output("flow", NodeDefinition.PinType.FLOW, FlowDataType.EXECUTION);
        if (functionGraph.getFunctionInputs() != null) {
            for (FlowGraph.FunctionParameter param : functionGraph.getFunctionInputs()) {
                if (param != null && param.getName() != null && !param.getName().isBlank()) {
                    builder.input(param.getName(), NodeDefinition.PinType.DATA, param.getType() != null ? param.getType() : FlowDataType.ANY);
                }
            }
        }
        if (functionGraph.getFunctionOutputs() != null) {
            for (FlowGraph.FunctionParameter param : functionGraph.getFunctionOutputs()) {
                if (param != null && param.getName() != null && !param.getName().isBlank()) {
                    builder.output(param.getName(), NodeDefinition.PinType.DATA, param.getType() != null ? param.getType() : FlowDataType.ANY);
                }
            }
        }
        builder.priority(220).color(NodeDefinition.NodeCategory.FUNCTION);
        return builder.build();
    }

    private String formatFunctionDisplayName(String functionId) {
        if (functionId == null || functionId.isBlank()) return "Function";
        String[] parts = functionId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void layoutHeaderButtons() {
        if (studioMode) return;
        int padding = 5;
        int totalWidth = 0;

        for (IconButton button : headerButtons) {
            if (button.visible) {
                totalWidth += button.getWidth();
            }
        }
        long visibleButtons = headerButtons.stream().filter(button -> button.visible).count();
        totalWidth += Math.max(0, (int) visibleButtons - 1) * padding;

        headerBackground.setWidth(totalWidth + (padding * 2));
        headerBackground.setHeight(30);
        headerBackground.setPosition(width - headerBackground.getWidth() - 10, 10);

        int startY = headerBackground.getY();
        int currentX = headerBackground.getX() + headerBackground.getWidth() - padding;
        for (IconButton button : headerButtons) {
            if (!button.visible) {
                continue;
            }
            currentX -= button.getWidth();
            button.setPosition(currentX, startY + (headerBackground.getHeight() - button.getHeight()) / 2);
            currentX -= padding;
        }
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        if (paletteSidePanel != null && paletteSidePanel.isVisible()) {
            updatePalettePanelBounds();
        }
        if (studioResourcePanel != null && studioResourcePanel.isVisible()) {
            int bottomReserve = studioMode ? STUDIO_CONTENT_BROWSER_HEIGHT + 18 : 0;
            studioResourcePanel.height(Math.max(80, height - 65 - bottomReserve)).y(54).update();
        }
        if (headerBackground != null) {
            syncDebugHeaderVisibility();
            layoutHeaderButtons();
        }
        if (studioMode) {
            updateStudioLayout();
        }
    }

    private void updateStudioLayout() {
        if (studioTabsManager != null) {
            studioTabsManager.setPosition(10, 5);
            studioTabsManager.setSize(Math.max(80, width - studioHeaderRightReserve() - 20), 18);
        }
        if (studioContentBrowser != null) {
            studioContentBrowser.setPosition(8, height - STUDIO_CONTENT_BROWSER_HEIGHT - 8);
            studioContentBrowser.setSize(width - 16, STUDIO_CONTENT_BROWSER_HEIGHT);
        }
        if (paletteSidePanel != null && paletteSidePanel.isVisible()) {
            updatePalettePanelBounds();
        }
        if (studioResourcePanel != null) {
            studioResourcePanel.width(180).y(54).height(Math.max(80, height - 65 - STUDIO_CONTENT_BROWSER_HEIGHT - 18)).update();
        }
        for (StudioDocument document : studioDocuments) {
            if (document.view() != null) {
                document.view().resize(width, studioEditorHeight());
            }
        }
    }

    private int studioEditorHeight() {
        int bottom = studioContentBrowser != null ? studioContentBrowser.getY() - 8 : height;
        return Math.max(80, bottom);
    }

    private int palettePanelHeight() {
        int bottom = studioMode && studioContentBrowser != null ? studioContentBrowser.getY() - 5 : height - 11;
        return Math.max(0, bottom - palettePanelTop());
    }

    private int palettePanelTop() {
        return 59;
    }

    private void updatePalettePanelBounds() {
        int panelHeight = palettePanelHeight();
        if (panelHeight <= 0) {
            paletteSidePanel.hide();
            return;
        }
        paletteSidePanel.y(palettePanelTop()).height(panelHeight).update();
    }

    private int studioHeaderRightReserve() {
        int reserve = 0;
        List<AnimatedWidget> buttons = visibleStudioHeaderButtons();
        int visibleCount = 0;
        for (AnimatedWidget button : buttons) {
            if (button != null) {
                reserve += button.getWidth();
                visibleCount++;
            }
        }
        return reserve + visibleCount * 5;
    }

    private void organizeGraph() {
        if (graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return;
        }
        captureSnapshot();

        Map<String, List<String>> predecessors = new HashMap<>();
        for (String nodeId : graph.getNodes().keySet()) {
            predecessors.put(nodeId, new ArrayList<>());
        }
        if (graph.getConnections() != null) {
            for (FlowConnection connection : graph.getConnections()) {
                if (connection == null) {
                    continue;
                }
                if (!graph.getNodes().containsKey(connection.getSourceNodeId()) || !graph.getNodes().containsKey(connection.getTargetNodeId())) {
                    continue;
                }
                predecessors.computeIfAbsent(connection.getTargetNodeId(), ignored -> new ArrayList<>()).add(connection.getSourceNodeId());
            }
        }

        Map<String, Integer> layers = new HashMap<>();
        for (String nodeId : graph.getNodes().keySet()) {
            computeOrganizeLayer(nodeId, predecessors, layers, new HashSet<>());
        }

        Map<Integer, List<String>> layerNodes = new LinkedHashMap<>();
        int maxLayer = 0;
        for (Map.Entry<String, Integer> entry : layers.entrySet()) {
            int layer = Math.max(0, entry.getValue());
            maxLayer = Math.max(maxLayer, layer);
            layerNodes.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(entry.getKey());
        }
        for (int layer = 0; layer <= maxLayer; layer++) {
            layerNodes.computeIfAbsent(layer, ignored -> new ArrayList<>());
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxWidth = 0;
        for (Map.Entry<String, FlowNode> entry : graph.getNodes().entrySet()) {
            FlowNode node = entry.getValue();
            FlowNodeWidget widget = widgetCache.get(entry.getKey());
            if (node == null) {
                continue;
            }
            minX = Math.min(minX, (int) Math.round(node.getX()));
            minY = Math.min(minY, (int) Math.round(node.getY()));
            if (widget != null) {
                maxWidth = Math.max(maxWidth, widget.getWidth());
            }
        }
        if (minX == Integer.MAX_VALUE) {
            minX = (int) screenToWorld(width / 2.0, height / 2.0)[0];
            minY = (int) screenToWorld(width / 2.0, height / 2.0)[1];
        }

        int xSpacing = Math.max(280, maxWidth + 120);
        int ySpacing = 48;
        for (int layer = 0; layer <= maxLayer; layer++) {
            List<String> nodes = layerNodes.getOrDefault(layer, new ArrayList<>());
            nodes.sort(Comparator
                .comparingInt(this::organizeSortPriority)
                .thenComparingDouble(id -> graph.getNodes().get(id).getY())
                .thenComparingDouble(id -> graph.getNodes().get(id).getX())
                .thenComparing(id -> id));
            int y = minY;
            for (String nodeId : nodes) {
                FlowNode node = graph.getNodes().get(nodeId);
                FlowNodeWidget widget = widgetCache.get(nodeId);
                if (node == null || widget == null) {
                    continue;
                }
                int x = minX + layer * xSpacing;
                widget.setX(x);
                widget.setY(y);
                node.setX(x);
                node.setY(y);
                y += widget.getHeight() + ySpacing;
            }
        }
    }

    private int computeOrganizeLayer(String nodeId, Map<String, List<String>> predecessors, Map<String, Integer> layers, Set<String> visiting) {
        Integer existing = layers.get(nodeId);
        if (existing != null) {
            return existing;
        }
        if (!visiting.add(nodeId)) {
            return 0;
        }
        int layer = 0;
        for (String predecessor : predecessors.getOrDefault(nodeId, new ArrayList<>())) {
            if (predecessor == null || predecessor.equals(nodeId)) {
                continue;
            }
            layer = Math.max(layer, computeOrganizeLayer(predecessor, predecessors, layers, visiting) + 1);
        }
        visiting.remove(nodeId);
        layers.put(nodeId, layer);
        return layer;
    }

    private int organizeSortPriority(String nodeId) {
        FlowNode node = graph.getNodes().get(nodeId);
        if (node == null || node.getType() == null) {
            return 50;
        }
        String type = node.getType();
        if (type.startsWith("event:") || "function_start".equals(type)) {
            return 0;
        }
        if ("function_end".equals(type)) {
            return 90;
        }
        return 50;
    }

    public void showNodeInputSelector(List<String> options, String selected, Consumer<String> onSelected, int worldX, int worldY) {
        closeNodeItemSelector();
        if (options == null || options.isEmpty() || onSelected == null) {
            return;
        }
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(this)
                .size(180, 220)
                .dismissOnSelect(true)
                .onClose(() -> removeNodeItemSelector(selectorRef[0]))
                .build();
        selectorRef[0] = selector;
        for (String option : options) {
            selector.addItem(option, () -> onSelected.accept(option));
        }
        selector.setSelectedItem(selected);
        nodeItemSelector = selector;
        addDrawableChild(nodeItemSelector);
        double[] screen = worldToScreen(worldX, worldY);
        nodeItemSelector.show((int) screen[0], (int) screen[1]);
    }

    @Override
    public void close() {
        OPEN_SCREENS.remove(this);
        if (parent != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void renderHandler(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateTransforms(delta);

        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        int undistortedMouseX = (int) undistortedCoords[0];
        int undistortedMouseY = (int) undistortedCoords[1];

        if (dragState.isDragging) {
            dragMouseX = undistortedMouseX;
            dragMouseY = undistortedMouseY;
        }

        renderBackground(context, mouseX, mouseY, delta);

        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            activeView.resize(width, studioEditorHeight());
            activeView.render(context, mouseX, mouseY, delta);
            renderStudioOverlays(context, mouseX, mouseY, delta);
            return;
        }

        context.getMatrices().push();
        context.getMatrices().translate(getWidth() / 2.0f, getHeight() / 2.0f, 0);
        context.getMatrices().scale(zoomLevel, zoomLevel, 1.0f);
        context.getMatrices().translate(-getWidth() / 2.0f + panX, -getHeight() / 2.0f + panY, 0);

        double[] worldMouse = screenToWorld(undistortedMouseX, undistortedMouseY);
        int worldMouseX = (int) worldMouse[0];
        int worldMouseY = (int) worldMouse[1];
        IDrawContext worldContext = createWorldDrawContext(context);

        renderWires(worldContext);
        renderDebugWireOverlay(worldContext);

        FlowDebugController debug = debugController();
        for (Widget widget : worldWidgets) {
            if (widget instanceof FlowNodeWidget flowNodeWidget) {
                String nodeId = findNodeId(flowNodeWidget);
                flowNodeWidget.setSelected(nodeId != null && selectedNodeIds.contains(nodeId));
                boolean breakpoint = debug != null && nodeId != null && debug.hasBreakpoint(graph, nodeId);
                boolean pausedHere = debug != null && nodeId != null && debug.isPausedAt(graph.getId(), nodeId);
                if (pausedHere && breakpoint) {
                    flowNodeWidget.setAccent(ThemeManager.getAccent("calm"));
                } else if (pausedHere) {
                    flowNodeWidget.setAccent(ThemeManager.getAccent("nice"));
                } else {
                    flowNodeWidget.setAccent(breakpoint ? ThemeManager.getAccent("danger") : ThemeManager.getDefaultAccent());
                }
            }
            widget.render(worldContext, worldMouseX, worldMouseY, delta);
        }
        renderDebugNodeOverlay(worldContext);
        for (Widget widget : worldWidgets) {
            if (widget instanceof AnimatedWidget animated) {
                animated.renderHintOverlay(worldContext);
            }
        }
        context.getMatrices().pop();

        renderSelectionBox(context);
        renderStudioDocumentPreview(context);

        renderStudioOverlays(context, mouseX, mouseY, delta);
    }

    private ReSyncStudioView activeStudioView() {
        return studioMode && activeStudioDocument != null ? activeStudioDocument.view() : null;
    }

    private void refreshActiveViewHeaderButtons() {
        List<AnimatedWidget> nextButtons = new ArrayList<>();
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            nextButtons.addAll(view.headerButtons());
        }
        activeViewHeaderButtons.clear();
        activeViewHeaderButtons.addAll(nextButtons);
        for (AnimatedWidget button : activeViewHeaderButtons) {
            if (button != null) {
                button.entranceAnimationEnabled = false;
            }
        }
        rebuildStudioHeaderButtons();
    }

    private List<AnimatedWidget> visibleStudioHeaderButtons() {
        List<AnimatedWidget> buttons = new ArrayList<>();
        if (activeStudioView() == null) {
            buttons.addAll(headerButtons);
        } else {
            buttons.addAll(activeViewHeaderButtons);
        }
        return buttons;
    }

    private void layoutStudioHeaderButtons() {
        if (!studioMode) {
            return;
        }
        header().build();
    }

    private void rebuildStudioHeaderButtons() {
        if (!studioMode) {
            return;
        }
        header().clearHeaderWidgets();
        for (AnimatedWidget button : headerButtons) {
            if (button != null) {
                button.visible = false;
            }
        }
        for (AnimatedWidget button : activeViewHeaderButtons) {
            if (button != null) {
                button.visible = false;
            }
        }
        List<AnimatedWidget> buttons = visibleStudioHeaderButtons();
        for (AnimatedWidget button : buttons) {
            if (button != null) {
                button.visible = true;
                button.entranceAnimationEnabled = false;
                header().addRight(button);
            }
        }
        if (activeStudioView() == null) {
            syncDebugHeaderVisibility();
        }
        header().build();
    }

    private void renderStudioOverlays(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (studioMode) {
            renderDesktopChromeBackground(context, mouseX, mouseY, delta);
            layoutStudioHeaderButtons();
            for (AnimatedWidget button : header().leftButtons) {
                if (button != null && button.visible) {
                    button.render(context, mouseX, mouseY, delta);
                }
            }
            for (AnimatedWidget button : header().rightButtons) {
                if (button != null && button.visible) {
                    button.render(context, mouseX, mouseY, delta);
                }
            }
        }

        if (studioTabsManager != null) {
            studioTabsManager.render(context, mouseX, mouseY, delta);
        }

        for (Widget widget : hudWidgets) {
            widget.render(context, mouseX, mouseY, delta);
        }

        if (activeStudioView() == null && paletteSidePanel != null) {
            paletteSidePanel.update();
            paletteSidePanel.container().render(context, mouseX, mouseY, delta);
            paletteSidePanel.renderHeader(context);
        }
        if (activeStudioView() == null && studioResourcePanel != null) {
            studioResourcePanel.update();
            studioResourcePanel.container().render(context, mouseX, mouseY, delta);
            studioResourcePanel.renderHeader(context);
        }

        for (Widget widget : widgets) {
            if (widget instanceof ItemSelectorWidget || widget instanceof ContextMenuWidget) {
                widget.render(context, mouseX, mouseY, delta);
            }
        }

        for (Widget widget : hudWidgets) {
            if (widget instanceof AnimatedWidget animated) {
                animated.renderHintOverlay(context);
            }
        }
        for (Widget widget : widgets) {
            if (widget instanceof ItemSelectorWidget selector && selector.visible) {
                selector.renderHintOverlay(context);
            } else if (widget instanceof ContextMenuWidget menu && menu.isVisible()) {
                menu.renderHintOverlay(context);
            }
        }
    }

    private void renderStudioDocumentPreview(IDrawContext context) {
        if (!studioMode || activeStudioDocument == null || activeStudioDocument.graph() != null) {
            return;
        }
        int top = 42;
        int bottom = studioContentBrowser != null ? studioContentBrowser.getY() - 8 : height - 8;
        int left = 12;
        int right = width - (studioResourcePanel != null && studioResourcePanel.isVisible() ? studioResourcePanel.getDesiredWidth() + 12 : 12);
        int areaWidth = Math.max(20, right - left);
        int areaHeight = Math.max(20, bottom - top);
        if (ReSyncResourceDragPayload.GUI.equals(activeStudioDocument.type())) {
            renderGuiDocumentPreview(context, left, top, areaWidth, areaHeight);
        } else if (ReSyncResourceDragPayload.SCOREBOARD.equals(activeStudioDocument.type())) {
            renderScoreboardDocumentPreview(context, left, top, areaWidth, areaHeight);
        } else if (ReSyncResourceDragPayload.TAB.equals(activeStudioDocument.type())) {
            renderTabDocumentPreview(context, left, top, areaWidth, areaHeight);
        } else if (ReSyncResourceDragPayload.WORLD.equals(activeStudioDocument.type())) {
            int text = ThemeManager.getColor(ThemeColor.text);
            context.drawText(activeStudioDocument.title(), left + 12, top + 12, text, false);
        }
    }

    private void renderGuiDocumentPreview(IDrawContext context, int x, int y, int width, int height) {
        FlowManager manager = FlowManager.getInstance();
        GuiDefinition gui = manager != null ? manager.getGuisForServer(serverId).get(activeStudioDocument.id()) : null;
        if (gui == null) {
            return;
        }
        int rows = Math.clamp(gui.getRows(), 1, 6);
        int slot = Math.clamp(Math.min((width - 40) / 9, (height - 40) / rows), 12, 26);
        int gridWidth = slot * 9;
        int gridHeight = slot * rows;
        int startX = x + Math.max(0, (width - gridWidth) / 2);
        int startY = y + Math.max(0, (height - gridHeight) / 2);
        int border = ThemeManager.getColor(ThemeColor.innerBorder);
        int background = ThemeManager.getColor(ThemeColor.innerBackground);
        context.fill(startX - 6, startY - 18, startX + gridWidth + 6, startY + gridHeight + 6, background);
        context.fillBorder(startX - 6, startY - 18, startX + gridWidth + 6, startY + gridHeight + 6, 1, border);
        context.drawText(gui.getTitle() == null || gui.getTitle().isBlank() ? gui.getId() : gui.getTitle(), startX, startY - 12, ThemeManager.getColor(ThemeColor.text), false);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = startX + col * slot;
                int slotY = startY + row * slot;
                context.fill(slotX, slotY, slotX + slot - 1, slotY + slot - 1, ThemeManager.getColor(ThemeColor.background));
                context.fillBorder(slotX, slotY, slotX + slot - 1, slotY + slot - 1, 1, border);
            }
        }
    }

    private void renderScoreboardDocumentPreview(IDrawContext context, int x, int y, int width, int height) {
        FlowManager manager = FlowManager.getInstance();
        ScoreboardDefinition scoreboard = manager != null ? manager.getScoreboardsForServer(serverId).get(activeStudioDocument.id()) : null;
        if (scoreboard == null) {
            return;
        }
        List<String> lines = scoreboard.getLines() == null ? List.of() : scoreboard.getLines();
        int maxLines = Math.min(15, lines.size());
        int panelWidth = Math.min(Math.max(120, width / 3), width - 24);
        int rowHeight = 12;
        int panelHeight = Math.min(height - 24, (maxLines + 1) * rowHeight + 8);
        int startX = x + Math.max(0, (width - panelWidth) / 2);
        int startY = y + Math.max(0, (height - panelHeight) / 2);
        context.fill(startX, startY, startX + panelWidth, startY + panelHeight, 0x7F101010);
        context.drawText(scoreboard.getTitle() == null || scoreboard.getTitle().isBlank() ? scoreboard.getId() : scoreboard.getTitle(), startX + 6, startY + 4, 0xFFFFFFFF, true);
        for (int i = 0; i < maxLines; i++) {
            context.drawText(lines.get(i), startX + 6, startY + 18 + i * rowHeight, 0xFFFFFFFF, true);
        }
    }

    private void renderTabDocumentPreview(IDrawContext context, int x, int y, int width, int height) {
        FlowManager manager = FlowManager.getInstance();
        TabDefinition tab = manager != null ? manager.getTabsForServer(serverId).get(activeStudioDocument.id()) : null;
        if (tab == null) {
            return;
        }
        int panelWidth = Math.min(Math.max(160, width / 3), width - 24);
        int panelHeight = Math.min(120, height - 24);
        int startX = x + Math.max(0, (width - panelWidth) / 2);
        int startY = y + Math.max(0, (height - panelHeight) / 2);
        context.fill(startX, startY, startX + panelWidth, startY + panelHeight, 0x7F101010);
        int text = 0xFFFFFFFF;
        context.drawText(tab.getHeader() == null || tab.getHeader().isBlank() ? tab.getId() : tab.getHeader(), startX + 6, startY + 6, text, true);
        context.drawText(tab.getEntryFormat() == null || tab.getEntryFormat().isBlank() ? "%player%" : tab.getEntryFormat(), startX + 6, startY + 48, text, true);
        context.drawText(tab.getFooter() == null ? "" : tab.getFooter(), startX + 6, startY + panelHeight - 18, text, true);
    }

    private void renderWires(IDrawContext context) {
        if (graph.getConnections() == null) return;

        normalizePassthroughConnections();
        Set<FanoutKey> renderedFanouts = new HashSet<>();
        for (FlowConnection conn : graph.getConnections()) {
            int wireColor = wireColor(conn);
            if (drawPassthroughConnection(context, conn, wireColor)) {
                continue;
            }
            List<FlowConnection> fanout = fanoutConnections(conn);
            if (fanout.size() > 1) {
                FanoutKey key = new FanoutKey(editorSourceNodeId(conn), editorSourcePin(conn));
                if (renderedFanouts.add(key)) {
                    drawFanoutGroup(context, fanout, wireColor);
                }
                continue;
            }
            drawDirectConnection(context, conn, wireColor);
        }

        if (dragState.isDragging && dragState.sourceNodeId != null) {
            double[] sourcePinWorld = null;
            FlowNodeWidget source = widgetCache.get(dragState.sourceNodeId);
            FlowDataType sourceType = null;
            if (source != null) {
                double[] bounds = source.getPinBounds(dragState.sourcePin, dragState.sourceIsInput);
                if (bounds != null) {
                    sourcePinWorld = new double[] { bounds[0] + bounds[2]/2, bounds[1] + bounds[3]/2 };
                }
            }
            if (source != null) {
                sourceType = source.getPinType(dragState.sourcePin, dragState.sourceIsInput);
            }

            if (sourcePinWorld != null) {
                double[] mouseWorld = screenToWorld(dragMouseX, dragMouseY);
                int dragWireColor = (sourceType != null) ? sourceType.getColor() : (ThemeManager.getColor(ThemeColor.innerBorder) & 0x00FFFFFF) | 0x88000000;
                drawWire(context, (float)sourcePinWorld[0], (float)sourcePinWorld[1], (float)mouseWorld[0], (float)mouseWorld[1], dragWireColor, 0);
            }
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        dragMouseX = undistortedCoords[0];
        dragMouseY = undistortedCoords[1];
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (studioTabsManager != null && studioTabsManager.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (handleNodeItemSelectorMouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && studioContentBrowser.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            return activeView.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        if (studioResourcePanel != null && studioResourcePanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (paletteSidePanel != null && paletteSidePanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        dragMouseX = undistortedCoords[0];
        dragMouseY = undistortedCoords[1];
        double[] worldMouse = screenToWorld(dragMouseX, dragMouseY);
        int wx = (int) worldMouse[0];
        int wy = (int) worldMouse[1];

        if (isSelecting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selectionEndX = undistortedCoords[0];
            selectionEndY = undistortedCoords[1];
            updateSelectionFromBox();
            return true;
        }

        if (dragState.isDragging) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && movingSelectedNodes && draggedWidget instanceof FlowNodeWidget draggedNode) {
            double[] worldMouseNow = screenToWorld(dragMouseX, dragMouseY);
            int newX = (int) (worldMouseNow[0] - dragOffsetX);
            int newY = (int) (worldMouseNow[1] - dragOffsetY);
            String draggedNodeId = findNodeId(draggedNode);
            int[] draggedStart = draggedNodeId != null ? selectedDragStartPositions.get(draggedNodeId) : null;
            if (draggedStart != null) {
                int moveX = newX - draggedStart[0];
                int moveY = newY - draggedStart[1];
                for (String nodeId : selectedNodeIds) {
                    FlowNodeWidget widget = widgetCache.get(nodeId);
                    int[] start = selectedDragStartPositions.get(nodeId);
                    if (widget != null && start != null) {
                        widget.setX(start[0] + moveX);
                        widget.setY(start[1] + moveY);
                    }
                }
                return true;
            }
        }

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);
            if (widget.mouseDragged(wx, wy, button, deltaX, deltaY)) {
                return true;
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private int wireColor(FlowConnection connection) {
        FlowNodeWidget source = widgetCache.get(editorSourceNodeId(connection));
        FlowDataType sourceType = source != null ? source.getPinType(editorSourcePin(connection), false) : null;
        return sourceType != null ? sourceType.getColor() : ThemeManager.getColor(ThemeColor.innerBorder);
    }

    private void drawConnectionRoute(IDrawContext context, FlowConnection connection, int color) {
        if (drawPassthroughConnection(context, connection, color)) {
            return;
        }
        List<FlowConnection> fanout = fanoutConnections(connection);
        if (fanout.size() > 1) {
            drawWireSegments(context, fanoutSegments(connection, fanout, true), color);
            return;
        }
        drawDirectConnection(context, connection, color);
    }

    private void drawDirectConnection(IDrawContext context, FlowConnection connection, int color) {
        PinPoint start = sourceOutputPoint(connection);
        PinPoint end = targetInputPoint(connection);
        if (start == null || end == null) {
            return;
        }
        drawWire(context, (float) start.x(), (float) start.y(), (float) end.x(), (float) end.y(), color, 0);
    }

    private boolean drawPassthroughConnection(IDrawContext context, FlowConnection connection, int color) {
        FlowGraph.EditorPassthrough passthrough = findPassthroughRoute(connection);
        PinPoint output = passthroughOutputPoint(passthrough);
        if (output == null) {
            return false;
        }
        if (passthrough.getNodeId().equals(connection.getTargetNodeId()) && passthrough.getInputPin().equals(connection.getTargetPin())) {
            return true;
        }
        PinPoint end = targetInputPoint(connection);
        if (end == null) {
            return true;
        }
        drawWire(context, (float) output.x(), (float) output.y(), (float) end.x(), (float) end.y(), color, 0);
        return true;
    }

    private void drawFanoutGroup(IDrawContext context, List<FlowConnection> connections, int color) {
        if (connections.isEmpty()) {
            return;
        }
        FlowConnection first = connections.getFirst();
        PinPoint source = sourceOutputPoint(first);
        if (source == null) {
            return;
        }
        List<FlowConnection> sorted = sortedFanoutConnections(connections);
        double branchX = fanoutBranchX(source, sorted);
        double minY = source.y();
        double maxY = source.y();
        for (FlowConnection connection : sorted) {
            PinPoint end = targetInputPoint(connection);
            if (end == null) {
                continue;
            }
            minY = Math.min(minY, end.y());
            maxY = Math.max(maxY, end.y());
        }
        drawWireSegments(context, List.of(
            new WireSegment(source.x(), source.y(), branchX, source.y()),
            new WireSegment(branchX, minY, branchX, maxY)
        ), color);
        for (FlowConnection connection : sorted) {
            PinPoint end = targetInputPoint(connection);
            if (end == null) {
                continue;
            }
            drawWireSegments(context, List.of(new WireSegment(branchX, end.y(), end.x(), end.y())), color);
        }
    }

    private List<WireSegment> fanoutSegments(FlowConnection connection, List<FlowConnection> connections, boolean includeShared) {
        PinPoint source = sourceOutputPoint(connection);
        PinPoint end = targetInputPoint(connection);
        if (source == null || end == null) {
            return List.of();
        }
        List<FlowConnection> sorted = sortedFanoutConnections(connections);
        double branchX = fanoutBranchX(source, sorted);
        List<WireSegment> segments = new ArrayList<>();
        if (includeShared) {
            segments.add(new WireSegment(source.x(), source.y(), branchX, source.y()));
            segments.add(new WireSegment(branchX, source.y(), branchX, end.y()));
        }
        segments.add(new WireSegment(branchX, end.y(), end.x(), end.y()));
        return segments;
    }

    private List<FlowConnection> fanoutConnections(FlowConnection seed) {
        if (!isDataSourceConnection(seed) || graph.getConnections() == null) {
            return List.of();
        }
        String sourceNodeId = editorSourceNodeId(seed);
        String sourcePin = editorSourcePin(seed);
        List<FlowConnection> connections = new ArrayList<>();
        for (FlowConnection connection : graph.getConnections()) {
            if (!sourceNodeId.equals(editorSourceNodeId(connection)) || !sourcePin.equals(editorSourcePin(connection))) {
                continue;
            }
            if (hasVisiblePassthroughRoute(connection)) {
                continue;
            }
            if (sourceOutputPoint(connection) != null && targetInputPoint(connection) != null) {
                connections.add(connection);
            }
        }
        return sortedFanoutConnections(connections);
    }

    private boolean isDataSourceConnection(FlowConnection connection) {
        FlowNodeWidget source = widgetCache.get(editorSourceNodeId(connection));
        return source != null && source.getPinKind(editorSourcePin(connection), false) == NodeDefinition.PinType.DATA;
    }

    private boolean hasVisiblePassthroughRoute(FlowConnection connection) {
        return passthroughOutputPoint(findPassthroughRoute(connection)) != null;
    }

    private PinPoint sourceOutputPoint(FlowConnection connection) {
        FlowNodeWidget source = widgetCache.get(editorSourceNodeId(connection));
        return pinPoint(source, editorSourcePin(connection), false);
    }

    private PinPoint targetInputPoint(FlowConnection connection) {
        FlowNodeWidget target = widgetCache.get(connection.getTargetNodeId());
        return pinPoint(target, connection.getTargetPin(), true);
    }

    private PinPoint passthroughOutputPoint(FlowGraph.EditorPassthrough passthrough) {
        if (passthrough == null) {
            return null;
        }
        FlowNodeWidget passthroughWidget = widgetCache.get(passthrough.getNodeId());
        return pinPoint(passthroughWidget, NodeWidget.passthroughOutputPin(passthrough.getInputPin()), false);
    }

    private PinPoint pinPoint(FlowNodeWidget widget, String pin, boolean input) {
        double[] bounds = widget != null ? widget.getPinBounds(pin, input) : null;
        if (bounds == null) {
            return null;
        }
        return new PinPoint(bounds[0] + bounds[2] / 2, bounds[1] + bounds[3] / 2);
    }

    private List<FlowConnection> sortedFanoutConnections(List<FlowConnection> connections) {
        List<FlowConnection> sorted = new ArrayList<>(connections);
        sorted.sort(Comparator
            .comparingDouble(this::targetPinY)
            .thenComparingDouble(this::targetNodeY)
            .thenComparingDouble(this::targetNodeX)
            .thenComparing(connection -> safeString(connection.getTargetNodeId()))
            .thenComparing(connection -> safeString(connection.getTargetPin())));
        return sorted;
    }

    private double targetPinY(FlowConnection connection) {
        PinPoint point = targetInputPoint(connection);
        return point != null ? point.y() : Double.MAX_VALUE;
    }

    private double targetNodeY(FlowConnection connection) {
        FlowNodeWidget widget = widgetCache.get(connection.getTargetNodeId());
        return widget != null ? widget.getY() : Double.MAX_VALUE;
    }

    private double targetNodeX(FlowConnection connection) {
        FlowNodeWidget widget = widgetCache.get(connection.getTargetNodeId());
        return widget != null ? widget.getX() : Double.MAX_VALUE;
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private double fanoutBranchX(PinPoint source, List<FlowConnection> connections) {
        double minTargetX = Double.MAX_VALUE;
        for (FlowConnection connection : connections) {
            PinPoint target = targetInputPoint(connection);
            if (target != null) {
                minTargetX = Math.min(minTargetX, target.x());
            }
        }
        double targetX = minTargetX == Double.MAX_VALUE ? source.x() + 180 : minTargetX;
        if (targetX > source.x() + WIRE_OUT_OFFSET * 3) {
            return Math.round(source.x() + Math.min(220, Math.max(WIRE_OUT_OFFSET, (targetX - source.x()) * 0.45)));
        }
        return Math.round(source.x() + WIRE_OUT_OFFSET);
    }

    private void drawWire(IDrawContext context, float startX, float startY, float endX, float endY, int color, int laneOffset) {
        drawWireSegments(context, wireSegments(startX, startY, endX, endY, laneOffset), color);
    }

    private void drawWireSegments(IDrawContext context, List<WireSegment> segments, int color) {
        int alpha = (color >> 24) & 0xFF;
        int borderBase = ThemeManager.getColor(ThemeColor.innerBorder);
        int borderColor = (borderBase & 0x00FFFFFF) | (alpha << 24);

        for (WireSegment segment : segments) {
            drawSegmentBorder(context, (int) segment.x1(), (int) segment.y1(), (int) segment.x2(), (int) segment.y2(), borderColor);
        }
        for (WireSegment segment : segments) {
            drawSegmentFill(context, (int) segment.x1(), (int) segment.y1(), (int) segment.x2(), (int) segment.y2(), color);
        }
    }

    private List<WireSegment> wireSegments(double x1, double y1, double x2, double y2, int laneOffset) {
        int startX = Math.round((float) x1);
        int startY = Math.round((float) y1);
        int endX = Math.round((float) x2);
        int endY = Math.round((float) y2);
        int outX = startX + WIRE_OUT_OFFSET + laneOffset;
        int inX = endX - WIRE_OUT_OFFSET - laneOffset;
        int midY = Math.round((startY + endY) / 2f) + laneOffset;
        return List.of(
            new WireSegment(startX, startY, outX, startY),
            new WireSegment(outX, startY, outX, midY),
            new WireSegment(outX, midY, inX, midY),
            new WireSegment(inX, midY, inX, endY),
            new WireSegment(inX, endY, endX, endY)
        );
    }

    private void drawSegmentBorder(IDrawContext context, int x1, int y1, int x2, int y2, int color) {
        drawSegment(context, x1, y1, x2, y2, color, 5);
    }

    private void drawSegmentFill(IDrawContext context, int x1, int y1, int x2, int y2, int color) {
        drawSegment(context, x1, y1, x2, y2, color, 3);
    }

    private void drawSegment(IDrawContext context, int x1, int y1, int x2, int y2, int color, int thickness) {
        int half = thickness / 2;

        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            context.fill(x1 - half, minY - half, x1 + half + 1, maxY + half + 1, color);
            return;
        }

        if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            context.fill(minX - half, y1 - half, maxX + half + 1, y1 + half + 1, color);
            return;
        }

        context.fill(x1 - half, y1 - half, x1 + half + 1, y1 + half + 1, color);
    }

    private String editorSourceNodeId(FlowConnection connection) {
        if (connection == null) {
            return "";
        }
        String editorNodeId = connection.getEditorSourceNodeId();
        return editorNodeId != null && !editorNodeId.isBlank() ? editorNodeId : connection.getSourceNodeId();
    }

    private String editorSourcePin(FlowConnection connection) {
        if (connection == null) {
            return "";
        }
        String editorPin = connection.getEditorSourcePin();
        return editorPin != null && !editorPin.isBlank() ? editorPin : connection.getSourcePin();
    }

    private FlowGraph.EditorPassthrough findPassthroughRoute(FlowConnection connection) {
        if (connection != null && connection.getEditorSourceNodeId() != null && !connection.getEditorSourceNodeId().isBlank()) {
            return null;
        }
        if (connection == null || graph.getConnections() == null) {
            return null;
        }
        FlowGraph.EditorPassthrough best = null;
        double bestScore = Double.MAX_VALUE;
        for (FlowGraph.EditorPassthrough passthrough : graph.getEditorPassthroughs()) {
            if (passthrough == null) {
                continue;
            }
            if (passthrough.getNodeId().equals(connection.getTargetNodeId()) && passthrough.getInputPin().equals(connection.getTargetPin())) {
                continue;
            }
            FlowConnection incoming = findIncomingConnection(passthrough.getNodeId(), passthrough.getInputPin());
            if (incoming == null) {
                continue;
            }
            if (connection.getSourceNodeId().equals(incoming.getSourceNodeId()) && connection.getSourcePin().equals(incoming.getSourcePin())) {
                double score = passthroughRouteScore(connection, passthrough);
                if (score < bestScore) {
                    best = passthrough;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private double passthroughRouteScore(FlowConnection connection, FlowGraph.EditorPassthrough passthrough) {
        FlowNodeWidget passthroughWidget = widgetCache.get(passthrough.getNodeId());
        FlowNodeWidget targetWidget = widgetCache.get(connection.getTargetNodeId());
        if (passthroughWidget == null || targetWidget == null) {
            return Double.MAX_VALUE;
        }
        double passthroughCenterX = passthroughWidget.getX() + passthroughWidget.getWidth() / 2.0;
        double passthroughCenterY = passthroughWidget.getY() + passthroughWidget.getHeight() / 2.0;
        double targetCenterX = targetWidget.getX() + targetWidget.getWidth() / 2.0;
        double targetCenterY = targetWidget.getY() + targetWidget.getHeight() / 2.0;
        double dx = targetCenterX - passthroughCenterX;
        double dy = targetCenterY - passthroughCenterY;
        return dx * dx + dy * dy;
    }

    private FlowConnection findIncomingConnection(String nodeId, String inputPin) {
        if (graph.getConnections() == null) {
            return null;
        }
        for (FlowConnection connection : graph.getConnections()) {
            if (nodeId.equals(connection.getTargetNodeId()) && inputPin.equals(connection.getTargetPin())) {
                return connection;
            }
        }
        return null;
    }

    private void renderDebugWireOverlay(IDrawContext context) {
        FlowDebugController debug = debugController();
        if (debug == null || graph.getConnections() == null) {
            return;
        }
        FlowDebugController.DebugRecord activeConnection = debug.getLatestConnection(graph.getId());
        if (activeConnection == null) {
            return;
        }
        int color = (ThemeManager.getDefaultAccent().getAccentColor() & 0x00FFFFFF) | 0xCC000000;
        for (FlowConnection conn : graph.getConnections()) {
            if (!conn.getSourceNodeId().equals(activeConnection.sourceNodeId())
                || !conn.getSourcePin().equals(activeConnection.sourcePin())
                || !conn.getTargetNodeId().equals(activeConnection.targetNodeId())
                || !conn.getTargetPin().equals(activeConnection.targetPin())) {
                continue;
            }
            drawConnectionRoute(context, conn, color);
        }
    }

    private void renderDebugNodeOverlay(IDrawContext context) {
        FlowDebugController debug = debugController();
        if (debug == null) {
            return;
        }
        for (Map.Entry<String, FlowNodeWidget> entry : widgetCache.entrySet()) {
            String nodeId = entry.getKey();
            FlowNodeWidget widget = entry.getValue();
            FlowDebugController.DebugRecord record = debug.getLatestRecordForNode(graph.getId(), nodeId);
            boolean activeRecord = record != null && "failure".equals(record.status());
            if (!activeRecord) {
                continue;
            }
            int color = 0x00000000;
            if (activeRecord && "failure".equals(record.status())) {
                color = 0xFFFF4D4D;
            }
            context.fillBorder(widget.getX() - 2, widget.getY() - 2, widget.getX() + widget.getWidth() + 2, widget.getY() + widget.getHeight() + 2, 2, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleContextMenuMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (handleNodeItemSelectorMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (handleStudioHudMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        double[] headerCoords = unDistortMouse(mouseX, mouseY);
        if (handleHeaderButtonsClick((int) headerCoords[0], (int) headerCoords[1], button)) {
            return true;
        }
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            return activeView.mouseClicked(mouseX, mouseY, button);
        }
        if (paletteSidePanel != null && paletteSidePanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (studioResourcePanel != null && studioResourcePanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }


        double[] undistortedCoords = headerCoords;
        double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
        int wx = (int)worldMouse[0];
        int wy = (int)worldMouse[1];

        if (handleHeaderButtonsClick((int) undistortedCoords[0], (int) undistortedCoords[1], button)) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (debugMode && toggleBreakpointAt(wx, wy)) {
                return true;
            }
            if (handleRightClick(wx, wy, (int)mouseX, (int)mouseY)) {
                return true;
            }
            return true;
        }

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);

            Widget outputWidget = widget.getOutputWidgetAt(wx, wy);
            if (outputWidget != null) {
                outputWidget.mouseClicked(wx, wy, button);
                if (outputWidget instanceof DropDownWidget<?>) {
                    setFocusedWidget(outputWidget);
                } else {
                    setFocusedWidget(null);
                }
                focusedNode = widget;
                bringToFront(widget);
                selectNode(widget, hasShiftDown() || hasControlDown());
                return true;
            }

            if (widget.isMouseOverPin(wx, wy)) {
                String pinName = widget.getPinAtPosition(wx, wy);
                if (pinName != null) {
                    double[] inputBounds = widget.getPinBounds(pinName, true);
                    if (isInside(wx, wy, inputBounds)) {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && widget.getPinKind(pinName, true) == NodeDefinition.PinType.DATA) {
                            toggleInputPassthrough(widget, pinName);
                            return true;
                        }
                        dragMouseX = undistortedCoords[0];
                        dragMouseY = undistortedCoords[1];
                        startWireDrag(widget, pinName, true);
                        setFocusedWidget(null);
                        return true;
                    }

                    double[] outputBounds = widget.getPinBounds(pinName, false);
                    if (isInside(wx, wy, outputBounds)) {
                        dragMouseX = undistortedCoords[0];
                        dragMouseY = undistortedCoords[1];
                        startWireDrag(widget, pinName, false);
                        setFocusedWidget(null);
                        return true;
                    }
                    setFocusedWidget(null);
                    return true;
                }
            }

            Widget inputWidget = widget.getInputWidgetAt(wx, wy);
            if (inputWidget != null) {
                inputWidget.mouseClicked(wx, wy, button);
                if (inputWidget instanceof TextInputWidget || inputWidget instanceof DropDownWidget<?>) {
                    setFocusedWidget(inputWidget);
                } else {
                    setFocusedWidget(null);
                }
                focusedNode = widget;
                bringToFront(widget);
                selectNode(widget, hasShiftDown() || hasControlDown());
                return true;
            }

            if (widget.isMouseOver(wx, wy)) {
                focusedNode = widget;
                setFocusedWidget(null);
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    draggedWidget = widget;
                    dragOffsetX = wx - widget.getX();
                    dragOffsetY = wy - widget.getY();
                }
                bringToFront(widget);
                selectNode(widget, hasShiftDown() || hasControlDown());
                startSelectedNodeMove(widget, button);
                widget.setLastScreenMouse((int) undistortedCoords[0], (int) undistortedCoords[1]);
                widget.mouseClicked(wx, wy, button);
                return true;
            }
        }

        focusedNode = null;
        setFocusedWidget(null);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (hasShiftDown() || hasControlDown()) {
                startSelection(undistortedCoords[0], undistortedCoords[1]);
                return true;
            }
            clearSelection();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleContextMenuMouseClicked(double mouseX, double mouseY, int button) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        boolean hadContextMenu = false;
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (!(widget instanceof ContextMenuWidget menu) || !menu.isVisible()) {
                continue;
            }
            hadContextMenu = true;
            boolean overMenu = menu.isMouseOver(mouseX, mouseY);
            boolean handled = menu.mouseClicked(mouseX, mouseY, button);
            hideContextMenu();
            if (handled || overMenu) {
                return true;
            }
            return button != GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        }
        return hadContextMenu;
    }

    private boolean handleStudioHudMouseClicked(double mouseX, double mouseY, int button) {
        if (!studioMode) {
            return false;
        }
        if (studioTabsManager != null && studioTabsManager.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return studioContentBrowser != null && studioContentBrowser.mouseClicked(mouseX, mouseY, button);
    }

    private boolean toggleBreakpointAt(int wx, int wy) {
        FlowDebugController debug = debugController();
        if (debug == null) {
            return false;
        }
        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);
            if (widget.isMouseOver(wx, wy)) {
                String nodeId = findNodeId(widget);
                if (nodeId != null) {
                    debug.toggleBreakpoint(serverId, graph, nodeId);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleHeaderButtonsClick(int mouseX, int mouseY, int button) {
        List<AnimatedWidget> buttons = new ArrayList<>();
        if (studioMode) {
            buttons.addAll(header().leftButtons);
            buttons.addAll(header().rightButtons);
        } else {
            buttons.addAll(headerButtons);
        }
        for (AnimatedWidget headerButton : buttons) {
            if (headerButton != null && headerButton.visible && headerButton.isMouseOver(mouseX, mouseY)) {
                return headerButton.mouseClicked(mouseX, mouseY, button);
            }
        }
        return false;
    }

    private void startWireDrag(FlowNodeWidget widget, String pinName, boolean isInput) {
        dragState.isDragging = true;
        dragState.sourceNodeId = findNodeId(widget);
        dragState.sourcePin = pinName;
        dragState.sourceIsInput = isInput;
        dragPinWidget = widget;
        pendingSourceNodeId = null;
        pendingSourcePin = null;
        pendingSourceIsInput = false;
    }

    private void toggleInputPassthrough(FlowNodeWidget widget, String inputPin) {
        String nodeId = findNodeId(widget);
        if (nodeId == null) {
            return;
        }
        captureSnapshot();
        boolean removed = graph.getEditorPassthroughs().removeIf(passthrough -> nodeId.equals(passthrough.getNodeId()) && inputPin.equals(passthrough.getInputPin()));
        if (!removed) {
            graph.getEditorPassthroughs().add(new FlowGraph.EditorPassthrough(nodeId, inputPin));
        }
        widget.refreshInputWidgets();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (studioTabsManager != null && studioTabsManager.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (handleNodeItemSelectorMouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && studioContentBrowser.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            return activeView.mouseReleased(mouseX, mouseY, button);
        }
        if (paletteSidePanel != null && paletteSidePanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (studioResourcePanel != null && studioResourcePanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
        int wx = (int) worldMouse[0];
        int wy = (int) worldMouse[1];

        if (isSelecting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selectionEndX = undistortedCoords[0];
            selectionEndY = undistortedCoords[1];
            updateSelectionFromBox();
            isSelecting = false;
            return true;
        }

        if (dragState.isDragging) {
            tryCompleteWire(worldMouse[0], worldMouse[1], undistortedCoords[0], undistortedCoords[1]);

            dragState.isDragging = false;
            dragState.sourceNodeId = null;
            dragState.sourcePin = null;
            dragState.sourceIsInput = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggedWidget instanceof FlowNodeWidget) {
            captureSnapshot();
            if (movingSelectedNodes) {
                syncNodePositions();
            } else {
                syncNodePosition((FlowNodeWidget) draggedWidget);
            }
            movingSelectedNodes = false;
            selectedDragStartPositions.clear();
            draggedWidget = null;
        }

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);
            if (widget.mouseReleased(wx, wy, button)) {
                return true;
            }
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (studioTabsManager != null && studioTabsManager.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (nodeItemSelector != null && nodeItemSelector.visible && nodeItemSelector.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && studioContentBrowser.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            return activeView.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (studioResourcePanel != null && studioResourcePanel.isVisible() && studioResourcePanel.container().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)
                && !(getFocusedWidget() instanceof TextInputWidget)) {
            if (!selectedNodeIds.isEmpty()) {
                captureSnapshot();
                deleteSelectedNodes();
                return true;
            }
            if (focusedNode != null) {
                captureSnapshot();
                deleteNode(findNodeId(focusedNode));
                return true;
            }
        }

        boolean hasControl = hasControlDown();
        boolean hasShift = hasShiftDown();

        if (hasControl && !(getFocusedWidget() instanceof TextInputWidget)) {
            if (keyCode == GLFW.GLFW_KEY_C) {
                if (!selectedNodeIds.isEmpty()) {
                    copyNodes();
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                if (!clipboard.nodes.isEmpty()) {
                    captureSnapshot();
                    pasteNodes();
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_X) {
                if (!selectedNodeIds.isEmpty()) {
                    cutNodes();
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_D) {
                if (!selectedNodeIds.isEmpty()) {
                    captureSnapshot();
                    duplicateNodes();
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if (hasShift) {
                    redo();
                } else {
                    undo();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (studioTabsManager != null && studioTabsManager.charTyped(chr, modifiers)) {
            return true;
        }
        if (nodeItemSelector != null && nodeItemSelector.visible && nodeItemSelector.charTyped(chr, modifiers)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && studioContentBrowser.charTyped(chr, modifiers)) {
            return true;
        }
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            return activeView.charTyped(chr, modifiers);
        }
        if (studioResourcePanel != null && studioResourcePanel.isVisible() && studioResourcePanel.container().charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    protected void syncNodePositions() {
        for (Map.Entry<String, FlowNodeWidget> entry : widgetCache.entrySet()) {
            FlowNode node = graph.getNodes().get(entry.getKey());
            FlowNodeWidget widget = entry.getValue();
            if (node != null && widget != null) {
                node.setX(widget.getX());
                node.setY(widget.getY());
            }
        }
    }

    private void syncNodePosition(FlowNodeWidget widget) {
        String nodeId = findNodeId(widget);
        if (nodeId == null) {
            return;
        }
        FlowNode node = graph.getNodes().get(nodeId);
        if (node != null) {
            node.setX(widget.getX());
            node.setY(widget.getY());
        }
    }

    private void refreshInputWidgets(String nodeId) {
        FlowNodeWidget widget = widgetCache.get(nodeId);
        if (widget != null) {
            widget.refreshInputWidgets();
        }
    }

    private void deleteSelectedNodes() {
        if (selectedNodeIds.isEmpty()) {
            return;
        }
        Set<String> toDelete = new HashSet<>(selectedNodeIds);
        selectedNodeIds.clear();
        for (String nodeId : toDelete) {
            deleteNode(nodeId);
        }
    }

    private void deleteNode(String nodeId) {
        if (nodeId == null) {
            return;
        }
        selectedNodeIds.remove(nodeId);
        FlowNodeWidget widget = widgetCache.remove(nodeId);
        if (widget != null) {
            removeWorldWidget(widget);
        }
        if (graph.getNodes() != null) {
            graph.getNodes().remove(nodeId);
        }
        if (graph.getConnections() != null) {
            Set<String> affectedTargets = new HashSet<>();
            graph.getConnections().removeIf(conn -> {
                boolean remove = nodeId.equals(conn.getSourceNodeId()) || nodeId.equals(conn.getTargetNodeId());
                if (remove && !nodeId.equals(conn.getTargetNodeId())) {
                    affectedTargets.add(conn.getTargetNodeId());
                }
                return remove;
            });
            for (String targetId : affectedTargets) {
                refreshInputWidgets(targetId);
            }
        }
        graph.getEditorPassthroughs().removeIf(passthrough -> nodeId.equals(passthrough.getNodeId()));
        if (dragState.isDragging && nodeId.equals(dragState.sourceNodeId)) {
            dragState.isDragging = false;
            dragState.sourceNodeId = null;
            dragState.sourcePin = null;
        }
        if (widget == focusedNode) {
            focusedNode = null;
        }
        if (draggedWidget == widget) {
            draggedWidget = null;
        }
    }

    private boolean handleRightClick(int wx, int wy, int screenX, int screenY) {
        FlowConnection hit = findConnectionAt(wx, wy);
        if (hit != null) {
            removeConnection(hit);
            return true;
        }

        FlowNodeWidget widget = findNodeAt(wx, wy);
        if (widget != null) {
            String pinName = widget.getPinAtPosition(wx, wy);
            if (pinName != null && disconnectPin(widget, pinName, wx, wy)) {
                return true;
            }
            selectNode(widget, hasShiftDown() || hasControlDown());
            if (widget.isFunctionStartOrEnd()) {
                showFunctionNodeContextMenu(screenX, screenY, widget);
            }
            return true;
        }

        showAllNodesMenu(screenX, screenY);
        return true;
    }

    private FlowNodeWidget findNodeAt(int wx, int wy) {
        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            Widget widget = worldWidgets.get(i);
            if (widget instanceof FlowNodeWidget FlowNodeWidget) {
                if (FlowNodeWidget.isMouseOver(wx, wy)) {
                    return FlowNodeWidget;
                }
            }
        }
        return null;
    }

    private void showFunctionNodeContextMenu(int screenX, int screenY, FlowNodeWidget widget) {
        List<FlowGraph.FunctionParameter> params = widget.getFunctionParameterList();
        if (params == null) return;

        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this);
        builder.addHeaderButton("add.png", () -> widget.showAddFunctionParameterPopup(), "Add Parameter", ThemeManager.getAccent("nice"));
        for (FlowGraph.FunctionParameter p : params) {
            if (p != null && p.getName() != null) {
                String name = p.getName();
                builder.addItem("Remove: " + name, () -> widget.removeFunctionParameter(name), name, ThemeManager.getAccent("danger"));
            }
        }
        ContextMenuWidget menu = builder.build();
        addDrawableChild(menu);
        menu.show(screenX, screenY);
    }


    private void selectNode(FlowNodeWidget widget, boolean toggle) {
        String nodeId = findNodeId(widget);
        if (nodeId == null) {
            return;
        }
        if (toggle) {
            if (selectedNodeIds.contains(nodeId)) {
                selectedNodeIds.remove(nodeId);
            } else {
                selectedNodeIds.add(nodeId);
            }
        } else {
            if (selectedNodeIds.contains(nodeId)) {
                return;
            }
            selectedNodeIds.clear();
            selectedNodeIds.add(nodeId);
        }
    }

    private void clearSelection() {
        selectedNodeIds.clear();
    }

    private void startSelection(double screenX, double screenY) {
        isSelecting = true;
        selectionStartX = screenX;
        selectionStartY = screenY;
        selectionEndX = screenX;
        selectionEndY = screenY;
        selectionAdditive = hasShiftDown() || hasControlDown();
        selectionBase.clear();
        if (selectionAdditive) {
            selectionBase.addAll(selectedNodeIds);
        } else {
            selectedNodeIds.clear();
        }
        updateSelectionFromBox();
    }

    private void updateSelectionFromBox() {
        double minScreenX = Math.min(selectionStartX, selectionEndX);
        double minScreenY = Math.min(selectionStartY, selectionEndY);
        double maxScreenX = Math.max(selectionStartX, selectionEndX);
        double maxScreenY = Math.max(selectionStartY, selectionEndY);

        double[] worldStart = screenToWorld(minScreenX, minScreenY);
        double[] worldEnd = screenToWorld(maxScreenX, maxScreenY);

        double minWorldX = Math.min(worldStart[0], worldEnd[0]);
        double minWorldY = Math.min(worldStart[1], worldEnd[1]);
        double maxWorldX = Math.max(worldStart[0], worldEnd[0]);
        double maxWorldY = Math.max(worldStart[1], worldEnd[1]);

        Set<String> selection = new HashSet<>();
        for (Map.Entry<String, FlowNodeWidget> entry : widgetCache.entrySet()) {
            FlowNodeWidget widget = entry.getValue();
            if (widget == null) {
                continue;
            }
            double nodeX = widget.getX();
            double nodeY = widget.getY();
            double nodeX2 = nodeX + widget.getWidth();
            double nodeY2 = nodeY + widget.getHeight();
            if (nodeX2 >= minWorldX && nodeX <= maxWorldX && nodeY2 >= minWorldY && nodeY <= maxWorldY) {
                selection.add(entry.getKey());
            }
        }

        selectedNodeIds.clear();
        if (selectionAdditive) {
            selectedNodeIds.addAll(selectionBase);
        }
        selectedNodeIds.addAll(selection);
    }

    private void renderSelectionBox(IDrawContext context) {
        if (!isSelecting) {
            return;
        }
        int accentColor = ThemeManager.getDefaultAccent().getAccentColor();
        int fill = ThemeManager.getAnimatedColor("selection_fill".hashCode(), (accentColor & 0x00FFFFFF) | 0x44000000);
        int border = ThemeManager.getAnimatedColor("selection_border".hashCode(), (accentColor & 0x00FFFFFF) | 0xAA000000);

        int x1 = (int) Math.min(selectionStartX, selectionEndX);
        int y1 = (int) Math.min(selectionStartY, selectionEndY);
        int x2 = (int) Math.max(selectionStartX, selectionEndX);
        int y2 = (int) Math.max(selectionStartY, selectionEndY);
        context.fill(x1, y1, x2, y2, fill);
        context.fillBorder(x1, y1, x2, y2, 1, border);
    }

    protected boolean canConnect(FlowNodeWidget sourceWidget, String sourcePin, FlowNodeWidget targetWidget, String targetPin) {
        FlowDataType sourceType = sourceWidget.getPinType(sourcePin, false);
        FlowDataType targetType = targetWidget.getPinType(targetPin, true);
        NodeDefinition.PinType sourceKind = sourceWidget.getPinKind(sourcePin, false);
        NodeDefinition.PinType targetKind = targetWidget.getPinKind(targetPin, true);

        if (sourceType == null || targetType == null) {
            return false;
        }

        if (!allowFlowPins() && (sourceKind == NodeDefinition.PinType.FLOW || targetKind == NodeDefinition.PinType.FLOW || sourceKind == NodeDefinition.PinType.EXEC || targetKind == NodeDefinition.PinType.EXEC)) {
            return false;
        }

        if (sourceKind == NodeDefinition.PinType.FLOW || targetKind == NodeDefinition.PinType.FLOW) {
            return sourceKind == NodeDefinition.PinType.FLOW && targetKind == NodeDefinition.PinType.FLOW && sourceType == FlowDataType.EXECUTION && targetType == FlowDataType.EXECUTION;
        }

        if (sourceKind != NodeDefinition.PinType.DATA || targetKind != NodeDefinition.PinType.DATA) {
            return false;
        }

        return isTypeCompatible(sourceType, targetType);
    }

    protected boolean isTypeCompatible(FlowDataType sourceType, FlowDataType targetType) {
        if (sourceType == null || targetType == null) {
            return false;
        }
        if (useStrictTypeCompatibility()) {
            return sourceType.equals(targetType);
        }
        if (sourceType.canConvertTo(targetType)) {
            return true;
        }
        return NodeRegistry.getInstance().canConvertTypes(nodeRegistryServerId(), sourceType, targetType);
    }

    protected boolean useStrictTypeCompatibility() {
        return false;
    }

    protected boolean allowFlowPins() {
        return true;
    }

    private void tryCompleteWire(double worldMouseX, double worldMouseY, double screenMouseX, double screenMouseY) {
        int wx = (int) worldMouseX;
        int wy = (int) worldMouseY;

        boolean connected = false;

        for (Widget widget : worldWidgets) {
            if (widget instanceof FlowNodeWidget targetWidget) {
                if (targetWidget != dragPinWidget) {
                    String targetPin = targetWidget.getPinAtPosition(wx, wy);
                    if (targetPin != null) {
                        String targetNodeId = findNodeId(targetWidget);
                        double[] inputBounds = targetWidget.getPinBounds(targetPin, true);
                        double[] outputBounds = targetWidget.getPinBounds(targetPin, false);
                        boolean onInput = isInside(wx, wy, inputBounds);
                        boolean onOutput = isInside(wx, wy, outputBounds);

                        if (!dragState.sourceIsInput && onInput && canConnect(dragPinWidget, dragState.sourcePin, targetWidget, targetPin)) {
                                FlowConnection sourceConnection = resolveDragSourceConnection();
                                if (sourceConnection == null) {
                                    break;
                                }
                                FlowConnection newConnection = new FlowConnection(sourceConnection.getSourceNodeId(), sourceConnection.getSourcePin(), targetNodeId, targetPin);
                                removeExistingInputConnection(targetNodeId, targetPin);
                                graph.getConnections().add(newConnection);
                                refreshInputWidgets(targetNodeId);
                                captureSnapshot();
                                connected = true;
                                break;
                        }

                        if (dragState.sourceIsInput && onOutput && canConnect(targetWidget, targetPin, dragPinWidget, dragState.sourcePin)) {
                                FlowConnection newConnection = new FlowConnection(targetNodeId, targetPin, dragState.sourceNodeId, dragState.sourcePin);
                                removeExistingInputConnection(dragState.sourceNodeId, dragState.sourcePin);
                                graph.getConnections().add(newConnection);
                                refreshInputWidgets(dragState.sourceNodeId);
                                captureSnapshot();
                                connected = true;
                                break;
                        }
                    }
                }
            }
        }

        if (!connected && dragPinWidget != null) {
            FlowDataType sourceType = dragPinWidget.getPinType(dragState.sourcePin, dragState.sourceIsInput);
            FlowConnection sourceConnection = !dragState.sourceIsInput ? resolveDragSourceConnection() : null;
            if (sourceType != null && (dragState.sourceIsInput || sourceConnection != null)) {
                pendingSourceNodeId = dragState.sourceIsInput ? dragState.sourceNodeId : sourceConnection.getSourceNodeId();
                pendingSourcePin = dragState.sourceIsInput ? dragState.sourcePin : sourceConnection.getSourcePin();
                pendingSourceIsInput = dragState.sourceIsInput;
                showAddNodeMenu((int) screenMouseX, (int) screenMouseY, sourceType, dragState.sourceIsInput);
            }
        }
    }

    private FlowConnection resolveDragSourceConnection() {
        return new FlowConnection(dragState.sourceNodeId, dragState.sourcePin, "", "");
    }


    private void bringToFront(FlowNodeWidget widget) {
        if (widget == null) {
            return;
        }
        worldWidgets.remove(widget);
        worldWidgets.add(widget);
    }

    private void startSelectedNodeMove(FlowNodeWidget widget, int button) {
        movingSelectedNodes = false;
        selectedDragStartPositions.clear();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        String nodeId = findNodeId(widget);
        if (nodeId == null || !selectedNodeIds.contains(nodeId) || selectedNodeIds.size() <= 1) {
            return;
        }
        movingSelectedNodes = true;
        for (String selectedNodeId : selectedNodeIds) {
            FlowNodeWidget selectedWidget = widgetCache.get(selectedNodeId);
            if (selectedWidget != null) {
                selectedDragStartPositions.put(selectedNodeId, new int[] { selectedWidget.getX(), selectedWidget.getY() });
            }
        }
    }

    protected void closeNodeItemSelector() {
        removeNodeItemSelector(nodeItemSelector);
    }

    private void removeNodeItemSelector(ItemSelectorWidget selector) {
        if (selector != null) {
            remove(selector);
        }
        if (selector == nodeItemSelector) {
            nodeItemSelector = null;
        }
    }

    private boolean handleNodeItemSelectorMouseClicked(double mouseX, double mouseY, int button) {
        ItemSelectorWidget selector = nodeItemSelector;
        if (selector == null || !selector.visible) {
            return false;
        }
        if (selector.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (selector.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        removeNodeItemSelector(selector);
        return false;
    }

    private boolean handleNodeItemSelectorMouseReleased(double mouseX, double mouseY, int button) {
        ItemSelectorWidget selector = nodeItemSelector;
        return selector != null && selector.visible && selector.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleNodeItemSelectorMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        ItemSelectorWidget selector = nodeItemSelector;
        return selector != null && selector.visible && selector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void removeExistingInputConnection(String nodeId, String pinName) {
        if (graph.getConnections() == null) return;

        boolean removed = graph.getConnections().removeIf(conn -> conn.getTargetNodeId().equals(nodeId) && conn.getTargetPin().equals(pinName)
        );
        if (removed) {
            refreshInputWidgets(nodeId);
        }
    }

    private String findNodeId(FlowNodeWidget widget) {
        for (Map.Entry<String, FlowNodeWidget> entry : widgetCache.entrySet()) {
            if (entry.getValue() == widget) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String findNodeIdForNode(FlowNode node) {
        for (Map.Entry<String, FlowNode> entry : graph.getNodes().entrySet()) {
            if (entry.getValue() == node) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void showAddNodeMenu(int x, int y, FlowDataType sourceType, boolean sourceIsInput) {
        closeNodeItemSelector();

        clearSelection();

        double[] worldPos = screenToWorld(x, y);
        int worldX = (int) worldPos[0];
        int worldY = (int) worldPos[1];

        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget.Builder builder = new ItemSelectorWidget.Builder(this)
                .onClose(() -> removeNodeItemSelector(selectorRef[0]));

        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(nodeRegistryServerId())) {
            List<NodeDefinition> definitions = new ArrayList<>(NodeRegistry.getInstance().getAllDefinitions(nodeRegistryServerId()).values());
            definitions.removeIf(def -> def.isHidden() || !isAllowedInCurrentEditor(def));
            definitions.sort(Comparator
                    .comparingInt(NodeDefinition::getPriority)
                    .thenComparing(NodeDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
            for (NodeDefinition def : definitions) {
                String compatiblePin = null;
                if (sourceType != null) {
                    compatiblePin = findCompatiblePin(def, sourceType, sourceIsInput);
                    if (compatiblePin == null) {
                        continue;
                    }
                }
                String pinName = compatiblePin;
                int finalWorldX = worldX;
                int finalWorldY = worldY;
                addSelectorItem(builder, selectorLabel(def), selectorHint(def), selectorSearchTerms(def), () -> {
                    captureSnapshot();
                    addNode(finalWorldX, finalWorldY, def.getId(), pinName);
                });
                addSelectorVariantItems(builder, def, finalWorldX, finalWorldY, pinName);
            }
        }

        nodeItemSelector = builder.build();
        selectorRef[0] = nodeItemSelector;
        addDrawableChild(nodeItemSelector);
        nodeItemSelector.show(x, y);
    }

    private String findCompatiblePin(NodeDefinition definition, FlowDataType sourceType, boolean sourceIsInput) {
        List<NodeDefinition.PinDefinition> pins = sourceIsInput ? definition.getOutputs() : definition.getInputs();
        for (NodeDefinition.PinDefinition pin : pins) {
            if (pin.getVisibleWhen() != null && !pin.getVisibleWhen().isEmpty() && !canExposeCompatibleFamilyPin(definition, pin)) {
                continue;
            }
            if (sourceType == FlowDataType.EXECUTION) {
                if (pin.getType() == NodeDefinition.PinType.FLOW && pin.getDataType() == FlowDataType.EXECUTION) {
                    return pin.getName();
                }
                continue;
            }
            if (pin.getType() == NodeDefinition.PinType.DATA && isTypeCompatible(sourceType, pin.getDataType())) {
                return pin.getName();
            }
        }
        return null;
    }

    private boolean canExposeCompatibleFamilyPin(NodeDefinition definition, NodeDefinition.PinDefinition candidate) {
        if (definition.getKind() != NodeDefinition.NodeKind.FAMILY || candidate.getVisibleWhen() == null || candidate.getVisibleWhen().isEmpty()) {
            return false;
        }
        for (NodeDefinition.PinDefinition input : definition.getInputs()) {
            if (input.getDirection() == NodeDefinition.PinDirection.INPUT
                    && input.getType() == NodeDefinition.PinType.DATA
                    && (input.getName().equalsIgnoreCase("mode") || input.getName().equalsIgnoreCase("action"))) {
                if (input.getOptions() == null) {
                    return false;
                }
                for (String value : candidate.getVisibleWhen().values()) {
                    for (String option : value.split(",")) {
                        if (input.getOptions().contains(option.trim())) {
                            return true;
                        }
                    }
                }
                return false;
            }
        }
        return false;
    }

    private void showAllNodesMenu(int screenX, int screenY) {
        closeNodeItemSelector();

        clearSelection();

        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget.Builder builder = new ItemSelectorWidget.Builder(this)
                .onClose(() -> removeNodeItemSelector(selectorRef[0]));

        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(nodeRegistryServerId())) {
            List<NodeDefinition> definitions = new ArrayList<>(NodeRegistry.getInstance().getAllDefinitions(nodeRegistryServerId()).values());
            definitions.removeIf(def -> def.isHidden() || !isAllowedInCurrentEditor(def));
            definitions.sort(Comparator
                    .comparingInt(NodeDefinition::getPriority)
                    .thenComparing(NodeDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
            for (NodeDefinition def : definitions) {
                addSelectorItem(builder, selectorLabel(def), selectorHint(def), selectorSearchTerms(def), () -> {
                    captureSnapshot();
                    addNodeAtCenter(def.getId());
                });
                addSelectorVariantItemsAtCenter(builder, def);
            }
        }

        nodeItemSelector = builder.build();
        selectorRef[0] = nodeItemSelector;
        addDrawableChild(nodeItemSelector);
        nodeItemSelector.show(screenX, screenY);
    }

    private String selectorLabel(NodeDefinition definition) {
        StringBuilder label = new StringBuilder(definition.getDisplayName());
        if (definition.getCategory() != null) {
            label.append(" - ").append(definition.getCategory().getDisplayName());
        }
        return label.toString();
    }

    private void addSelectorItem(ItemSelectorWidget.Builder builder, String label, String hint, String searchTerms, Runnable action) {
        builder.addItem(label, hint, searchTerms, action);
    }

    private void addSelectorVariantItems(ItemSelectorWidget.Builder builder, NodeDefinition definition, int x, int y, String autoWirePin) {
        for (NodeSelectorVariant variant : selectorVariants(definition)) {
            if (autoWirePin != null && !variantExposesPin(definition, variant, autoWirePin)) {
                continue;
            }
            addSelectorItem(builder, selectorVariantLabel(definition, variant), selectorVariantHint(definition, variant), selectorVariantSearchTerms(definition, variant), () -> {
                captureSnapshot();
                addNode(x, y, definition.getId(), autoWirePin, Map.of(variant.selectorPin().getName(), variant.option()));
            });
        }
    }

    private void addSelectorVariantItemsAtCenter(ItemSelectorWidget.Builder builder, NodeDefinition definition) {
        for (NodeSelectorVariant variant : selectorVariants(definition)) {
            addSelectorItem(builder, selectorVariantLabel(definition, variant), selectorVariantHint(definition, variant), selectorVariantSearchTerms(definition, variant), () -> {
                captureSnapshot();
                addNodeAtCenter(definition.getId(), Map.of(variant.selectorPin().getName(), variant.option()));
            });
        }
    }

    private List<NodeSelectorVariant> selectorVariants(NodeDefinition definition) {
        List<NodeSelectorVariant> variants = new ArrayList<>();
        for (NodeDefinition.PinDefinition input : definition.getInputs()) {
            if (!isModeSelectorPin(input)) {
                continue;
            }
            for (String option : input.getOptions()) {
                if (option != null && !option.isBlank()) {
                    variants.add(new NodeSelectorVariant(input, option));
                }
            }
        }
        return variants;
    }

    private boolean isModeSelectorPin(NodeDefinition.PinDefinition input) {
        return input.getDirection() == NodeDefinition.PinDirection.INPUT
            && input.getType() == NodeDefinition.PinType.DATA
            && (input.getName().equalsIgnoreCase("mode") || input.getName().equalsIgnoreCase("action"))
            && input.getOptions() != null
            && !input.getOptions().isEmpty();
    }

    private boolean variantExposesPin(NodeDefinition definition, NodeSelectorVariant variant, String pinName) {
        NodeDefinition.PinDefinition pin = findPin(definition, pinName);
        if (pin == null || pin.getVisibleWhen() == null || pin.getVisibleWhen().isEmpty()) {
            return true;
        }
        String expected = pin.getVisibleWhen().get(variant.selectorPin().getName());
        if (expected == null || expected.isBlank()) {
            return true;
        }
        for (String option : expected.split(",")) {
            if (option.trim().equalsIgnoreCase(variant.option())) {
                return true;
            }
        }
        return false;
    }

    private NodeDefinition.PinDefinition findPin(NodeDefinition definition, String pinName) {
        for (NodeDefinition.PinDefinition input : definition.getInputs()) {
            if (input.getName().equals(pinName)) {
                return input;
            }
        }
        for (NodeDefinition.PinDefinition output : definition.getOutputs()) {
            if (output.getName().equals(pinName)) {
                return output;
            }
        }
        return null;
    }

    private String selectorVariantLabel(NodeDefinition definition, NodeSelectorVariant variant) {
        StringBuilder label = new StringBuilder(definition.getDisplayName())
            .append(" - ")
            .append(formatSelectorOption(variant.option()))
            .append(" ")
            .append(formatSelectorModeName(variant.selectorPin().getName()));
        if (definition.getCategory() != null) {
            label.append(" - ").append(definition.getCategory().getDisplayName());
        }
        return label.toString();
    }

    private String selectorVariantHint(NodeDefinition definition, NodeSelectorVariant variant) {
        String modeText = formatSelectorOption(variant.option()) + " " + formatSelectorModeName(variant.selectorPin().getName());
        String baseHint = selectorHint(definition);
        if (baseHint.isBlank()) {
            return "Adds " + definition.getDisplayName() + " In " + modeText;
        }
        return "Adds " + definition.getDisplayName() + " In " + modeText + "\n" + baseHint;
    }

    private String selectorVariantSearchTerms(NodeDefinition definition, NodeSelectorVariant variant) {
        StringBuilder label = new StringBuilder(selectorSearchTerms(definition));
        label.append(" ")
            .append(definition.getDisplayName())
            .append(" ")
            .append(variant.selectorPin().getName())
            .append(" ")
            .append(variant.option())
            .append(" ")
            .append(formatSelectorOption(variant.option()))
            .append(" ")
            .append(formatSelectorModeName(variant.selectorPin().getName()));
        if (definition.getCategory() != null) {
            label.append(" ").append(definition.getCategory().getDisplayName());
        }
        return label.toString();
    }

    private String formatSelectorModeName(String name) {
        return "action".equalsIgnoreCase(name) ? "Action" : "Mode";
    }

    private String formatSelectorOption(String option) {
        if (option == null || option.isBlank()) {
            return "";
        }
        String[] parts = option.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(part.substring(0, 1).toUpperCase());
            if (part.length() > 1) {
                result.append(part.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }

    private String selectorHint(NodeDefinition definition) {
        if (definition.getDescription() != null && !definition.getDescription().isBlank()) {
            return definition.getDescription();
        }
        return "";
    }

    private String selectorSearchTerms(NodeDefinition definition) {
        StringBuilder label = new StringBuilder();
        if (definition.getDescription() != null && !definition.getDescription().isBlank()) {
            label.append(definition.getDescription());
        }
        appendSearchTerms(label, definition.getAliases());
        appendSearchTerms(label, definition.getTags());
        appendSearchTerms(label, definition.getExamples());
        for (NodeDefinition.PinDefinition pin : definition.getInputs()) {
            label.append(" ").append(pin.getName());
        }
        for (NodeDefinition.PinDefinition pin : definition.getOutputs()) {
            label.append(" ").append(pin.getName());
        }
        return label.toString();
    }

    private void appendSearchTerms(StringBuilder label, List<String> values) {
        if (values != null && !values.isEmpty()) {
            label.append(" - ").append(String.join(" ", values));
        }
    }

    private void addNode(int x, int y, String type) {
        addNode(x, y, type, null);
    }

    private void addNode(int x, int y, String type, String autoWirePin) {
        addNode(x, y, type, autoWirePin, Map.of());
    }

    private void addNodeAtCenter(String type, Map<String, Object> inputValues) {
        double[] center = screenToWorld(width / 2.0, height / 2.0);
        int x = (int) (center[0] - 50);
        int y = (int) (center[1] - 20);
        addNode(x, y, type, null, inputValues);
    }

    private void addNode(int x, int y, String type, String autoWirePin, Map<String, Object> inputValues) {
        String id = UUID.randomUUID().toString();
        FlowNode node = new FlowNode(type, x, y, new HashMap<>(inputValues));
        graph.getNodes().put(id, node);

        FlowNodeWidget widget = createNodeWidget(id, node);
        widgetCache.put(id, widget);
        addWorldWidget(widget);

        if (pendingSourceNodeId != null && pendingSourcePin != null && autoWirePin != null) {
            FlowConnection newConnection;
            if (pendingSourceIsInput) {
                newConnection = new FlowConnection(id, autoWirePin, pendingSourceNodeId, pendingSourcePin);
                removeExistingInputConnection(pendingSourceNodeId, pendingSourcePin);
                refreshInputWidgets(pendingSourceNodeId);
            } else {
                newConnection = new FlowConnection(pendingSourceNodeId, pendingSourcePin, id, autoWirePin);
                removeExistingInputConnection(id, autoWirePin);
                refreshInputWidgets(id);
            }
            graph.getConnections().add(newConnection);
        }

        pendingSourceNodeId = null;
        pendingSourcePin = null;
        pendingSourceIsInput = false;
    }

    protected void onSave() {
        syncNodePositions();
        saveGraph();
    }

    protected void saveGraph() {
        normalizePassthroughConnections();
        if (studioMode && activeStudioDocument != null && ReSyncResourceDragPayload.WORLDGEN.equals(activeStudioDocument.type())) {
            WorldGenProject project = WorldGenManager.getInstance().getCachedProject(serverId, activeStudioDocument.id());
            if (project == null) {
                project = WorldGenManager.getInstance().createProjectTemplate("Continental", activeStudioDocument.id());
            }
            project.setTerrainGraph(WorldGenManager.getInstance().toWorldGenGraph(graph));
            WorldGenManager.getInstance().saveWorldGen(serverId, project);
            return;
        }
        FlowManager flowManager = FlowManager.getInstance();
        if (studioMode && activeStudioDocument != null && ReSyncResourceDragPayload.COMMAND.equals(activeStudioDocument.type())) {
            if (!saveCommandDocument(flowManager)) {
                return;
            }
        }
        if (flowManager != null && serverId != null) {
            flowManager.saveFlow(serverId, graph);
        }
    }

    private boolean saveCommandDocument(FlowManager manager) {
        if (manager == null || activeStudioDocument == null) {
            return false;
        }
        CommandBindingContext next = new CommandBindingContext();
        next.command = normalizeCommandLabel(commandLabelInput != null ? commandLabelInput.getText() : activeStudioDocument.id());
        if (next.command.isBlank()) {
            new Notification("Command", "Invalid Label", Notification.Type.ERROR);
            return false;
        }
        next.subcommands = parseCommandPaths(commandPathsInput != null ? commandPathsInput.getText() : "");
        next.structured = commandStructuredToggle != null && commandStructuredToggle.getValue();
        String oldId = activeStudioDocument.id();
        String newId = next.command;
        if (!oldId.equals(newId)) {
            if (manager.getCommandBinding(serverId, newId) != null || manager.getProjectMetadata(serverId).findResource(ReSyncResourceDragPayload.COMMAND, newId) != null) {
                new Notification("Command", "ID Exists", Notification.Type.ERROR);
                return false;
            }
            if (!renameCommandDocument(manager, oldId, newId, next)) {
                return false;
            }
        } else {
            manager.setCommandBinding(serverId, oldId, encodeCommandContext(next));
        }
        new Notification("Saved", "/" + next.command, Notification.Type.SUCCESS);
        return true;
    }

    private boolean renameCommandDocument(FlowManager manager, String oldId, String newId, CommandBindingContext command) {
        String oldKey = ReSyncProjectMetadata.resourceKey(ReSyncResourceDragPayload.COMMAND, oldId);
        if (!manager.renameFlow(serverId, oldId, newId)) {
            new Notification("Command", "Rename Failed", Notification.Type.ERROR);
            return false;
        }
        manager.clearCommandBinding(serverId, oldId);
        manager.setCommandBinding(serverId, newId, encodeCommandContext(command));
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        ReSyncProjectMetadata.ResourceEntry entry = metadata.findResource(ReSyncResourceDragPayload.COMMAND, oldId);
        if (entry != null) {
            entry.setId(newId);
            entry.setDisplayName(newId);
            manager.saveProjectMetadata(serverId, metadata);
        }
        for (int i = 0; i < studioDocuments.size(); i++) {
            StudioDocument document = studioDocuments.get(i);
            if (document.key().equals(oldKey)) {
                studioDocuments.set(i, new StudioDocument(document.type(), newId, newId, graph, document.view()));
                activeStudioDocument = studioDocuments.get(i);
                break;
            }
        }
        graph.setId(newId);
        rebuildStudioDocumentTabs();
        if (studioContentBrowser != null) {
            studioContentBrowser.rebuild();
        }
        return true;
    }

    private void normalizePassthroughConnections() {
        if (graph == null || graph.getConnections() == null) {
            return;
        }
        for (FlowConnection connection : graph.getConnections()) {
            String editorNodeId = connection.getEditorSourceNodeId();
            String editorPin = connection.getEditorSourcePin();
            if (editorNodeId != null && !editorNodeId.isBlank() && NodeWidget.isPassthroughOutputPin(editorPin)) {
                connection.setSourceNodeId(editorNodeId);
                connection.setSourcePin(editorPin);
                connection.setEditorSourceNodeId(null);
                connection.setEditorSourcePin(null);
            }
        }
    }

    private boolean disconnectPin(FlowNodeWidget widget, String pinName, int wx, int wy) {
        String nodeId = findNodeId(widget);
        if (nodeId == null || graph.getConnections() == null) {
            return false;
        }

        double[] inputBounds = widget.getPinBounds(pinName, true);
        if (isInside(wx, wy, inputBounds)) {
            boolean removed = graph.getConnections().removeIf(conn -> nodeId.equals(conn.getTargetNodeId()) && pinName.equals(conn.getTargetPin()));
            if (removed) {
                captureSnapshot();
                refreshInputWidgets(nodeId);
            }
            return removed;
        }

        double[] outputBounds = widget.getPinBounds(pinName, false);
        if (isInside(wx, wy, outputBounds)) {
            Set<String> affectedTargets = new HashSet<>();
            boolean removed = graph.getConnections().removeIf(conn -> {
                boolean match = nodeId.equals(editorSourceNodeId(conn)) && pinName.equals(editorSourcePin(conn));
                if (match) {
                    affectedTargets.add(conn.getTargetNodeId());
                }
                return match;
            });
            if (removed) {
                captureSnapshot();
                for (String targetId : affectedTargets) {
                    refreshInputWidgets(targetId);
                }
            }
            return removed;
        }

        return false;
    }

    private FlowConnection findConnectionAt(double worldX, double worldY) {
        if (graph.getConnections() == null) {
            return null;
        }
        double hitRadius = WIRE_HIT_RADIUS / Math.max(zoomLevel, 0.1f);

        for (FlowConnection conn : graph.getConnections()) {
            for (WireSegment segment : hitTestSegments(conn)) {
                if (isNearWireSegment(worldX, worldY, segment, hitRadius)) {
                    return conn;
                }
            }
        }
        return null;
    }

    private List<WireSegment> hitTestSegments(FlowConnection connection) {
        FlowGraph.EditorPassthrough passthrough = findPassthroughRoute(connection);
        PinPoint output = passthroughOutputPoint(passthrough);
        PinPoint end = targetInputPoint(connection);
        if (output != null) {
            if (passthrough.getNodeId().equals(connection.getTargetNodeId()) && passthrough.getInputPin().equals(connection.getTargetPin())) {
                return List.of();
            }
            return end != null ? wireSegments(output.x(), output.y(), end.x(), end.y(), 0) : List.of();
        }
        List<FlowConnection> fanout = fanoutConnections(connection);
        if (fanout.size() > 1) {
            return fanoutSegments(connection, fanout, false);
        }
        PinPoint start = sourceOutputPoint(connection);
        return start != null && end != null ? wireSegments(start.x(), start.y(), end.x(), end.y(), 0) : List.of();
    }

    private boolean isNearWireSegment(double x, double y, WireSegment segment, double radius) {
        double minX = Math.min(segment.x1(), segment.x2()) - radius;
        double maxX = Math.max(segment.x1(), segment.x2()) + radius;
        double minY = Math.min(segment.y1(), segment.y2()) - radius;
        double maxY = Math.max(segment.y1(), segment.y2()) + radius;
        if (segment.x1() == segment.x2()) {
            return Math.abs(x - segment.x1()) <= radius && y >= minY && y <= maxY;
        }
        if (segment.y1() == segment.y2()) {
            return Math.abs(y - segment.y1()) <= radius && x >= minX && x <= maxX;
        }
        double dx = segment.x2() - segment.x1();
        double dy = segment.y2() - segment.y1();
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq == 0) {
            double pointDx = x - segment.x1();
            double pointDy = y - segment.y1();
            return pointDx * pointDx + pointDy * pointDy <= radius * radius;
        }
        double t = ((x - segment.x1()) * dx + (y - segment.y1()) * dy) / lengthSq;
        t = Math.max(0, Math.min(1, t));
        double nearestX = segment.x1() + t * dx;
        double nearestY = segment.y1() + t * dy;
        double pointDx = x - nearestX;
        double pointDy = y - nearestY;
        return pointDx * pointDx + pointDy * pointDy <= radius * radius;
    }

    private void removeConnection(FlowConnection conn) {
        if (conn == null || graph.getConnections() == null) {
            return;
        }
        if (graph.getConnections().remove(conn)) {
            captureSnapshot();
            refreshInputWidgets(conn.getTargetNodeId());
        }
    }

    private void copyNodes() {
        clipboard.nodes.clear();
        clipboard.connections.clear();

        if (selectedNodeIds.isEmpty()) {
            return;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;

        for (String nodeId : selectedNodeIds) {
            FlowNode node = graph.getNodes().get(nodeId);
            if (node != null) {
                minX = Math.min(minX, node.getX());
                minY = Math.min(minY, node.getY());
            }
        }

        Map<String, Integer> nodeIdToIndex = new HashMap<>();
        int index = 0;
        for (String nodeId : selectedNodeIds) {
            FlowNode node = graph.getNodes().get(nodeId);
            if (node != null) {
                nodeIdToIndex.put(nodeId, index++);
                clipboard.nodes.add(new CopiedNode(node.getType(), node.getX() - minX, node.getY() - minY, node.getInputValues()));
            }
        }

        if (graph.getConnections() != null) {
            for (FlowConnection conn : graph.getConnections()) {
                if (selectedNodeIds.contains(conn.getSourceNodeId()) && selectedNodeIds.contains(conn.getTargetNodeId())) {
                    Integer sourceIdx = nodeIdToIndex.get(conn.getSourceNodeId());
                    Integer targetIdx = nodeIdToIndex.get(conn.getTargetNodeId());
                    if (sourceIdx != null && targetIdx != null) {
                        clipboard.connections.add(new CopiedConnection(sourceIdx, conn.getSourcePin(), targetIdx, conn.getTargetPin()));
                    }
                }
            }
        }
    }

    private void pasteNodes() {
        if (clipboard.nodes.isEmpty()) {
            return;
        }

        double[] screenCenter = screenToWorld(width / 2.0, height / 2.0);
        double[] mouseScreen = new double[] { dragMouseX, dragMouseY };
        double[] mouseWorld = screenToWorld(mouseScreen[0], mouseScreen[1]);

        double pasteX = mouseWorld[0];
        double pasteY = mouseWorld[1];

        clearSelection();

        Map<Integer, String> indexToNewNodeId = new HashMap<>();
        List<String> newSelectedIds = new ArrayList<>();

        for (int i = 0; i < clipboard.nodes.size(); i++) {
            CopiedNode copied = clipboard.nodes.get(i);
            double x = pasteX + copied.relativeX;
            double y = pasteY + copied.relativeY;

            String newId = UUID.randomUUID().toString();
            FlowNode newNode = new FlowNode(copied.type, x, y, new HashMap<>(copied.inputValues));
            graph.getNodes().put(newId, newNode);

            FlowNodeWidget widget = createNodeWidget(newId, newNode);
            addWorldWidget(widget);
            widgetCache.put(newId, widget);

            indexToNewNodeId.put(i, newId);
            newSelectedIds.add(newId);
        }

        for (CopiedConnection conn : clipboard.connections) {
            String sourceId = indexToNewNodeId.get(conn.sourceIndex);
            String targetId = indexToNewNodeId.get(conn.targetIndex);
            if (sourceId != null && targetId != null) {
                FlowConnection newConnection = new FlowConnection(sourceId, conn.sourcePin, targetId, conn.targetPin);
                removeExistingInputConnection(targetId, conn.targetPin);
                graph.getConnections().add(newConnection);
                refreshInputWidgets(targetId);
            }
        }

        for (String id : newSelectedIds) {
            selectedNodeIds.add(id);
        }
    }

    private void cutNodes() {
        copyNodes();
        deleteSelectedNodes();
    }

    private void duplicateNodes() {
        copyNodes();
        pasteNodes();
    }

    private void captureSnapshot() {
        if (isUndoing) return;
        undoStack.add(new GraphSnapshot(graph, selectedNodeIds));
        if (undoStack.size() > MAX_UNDO_SIZE) {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;

        isUndoing = true;

        GraphSnapshot redoSnapshot = new GraphSnapshot(graph, selectedNodeIds);
        redoStack.add(redoSnapshot);
        if (redoStack.size() > MAX_UNDO_SIZE) {
            redoStack.remove(0);
        }

        GraphSnapshot snapshot = undoStack.remove(undoStack.size() - 1);
        restoreSnapshot(snapshot);

        isUndoing = false;
    }

    private void redo() {
        if (redoStack.isEmpty()) return;

        isUndoing = true;

        GraphSnapshot undoSnapshot = new GraphSnapshot(graph, selectedNodeIds);
        undoStack.add(undoSnapshot);
        if (undoStack.size() > MAX_UNDO_SIZE) {
            undoStack.remove(0);
        }

        GraphSnapshot snapshot = redoStack.remove(redoStack.size() - 1);
        restoreSnapshot(snapshot);

        isUndoing = false;
    }

    private void restoreSnapshot(GraphSnapshot snapshot) {
        for (FlowNodeWidget widget : widgetCache.values()) {
            removeWorldWidget(widget);
        }
        widgetCache.clear();

        selectedNodeIds.clear();
        selectedNodeIds.addAll(snapshot.selectedIds);

        graph.getNodes().clear();
        for (Map.Entry<String, FlowNode> entry : snapshot.nodes.entrySet()) {
            String nodeId = entry.getKey();
            FlowNode node = entry.getValue();
            graph.getNodes().put(nodeId, node);
            FlowNodeWidget widget = createNodeWidget(nodeId, node);
            addWorldWidget(widget);
            widgetCache.put(nodeId, widget);
        }

        graph.getConnections().clear();
        graph.getConnections().addAll(snapshot.connections);
        graph.setFunction(snapshot.function);
        graph.setFunctionInputs(copyFunctionParameters(snapshot.functionInputs));
        graph.setFunctionOutputs(copyFunctionParameters(snapshot.functionOutputs));
        graph.setEditorPassthroughs(copyEditorPassthroughs(snapshot.editorPassthroughs));

        for (String nodeId : snapshot.nodes.keySet()) {
            refreshInputWidgets(nodeId);
        }
    }

    private boolean isInside(int x, int y, double[] bounds) {
        if (bounds == null) {
            return false;
        }
        return x >= bounds[0] && x <= bounds[0] + bounds[2]
                && y >= bounds[1] && y <= bounds[1] + bounds[3];
    }

    private static List<FlowGraph.FunctionParameter> copyFunctionParameters(List<FlowGraph.FunctionParameter> parameters) {
        List<FlowGraph.FunctionParameter> copied = new ArrayList<>();
        if (parameters == null) {
            return copied;
        }
        for (FlowGraph.FunctionParameter parameter : parameters) {
            if (parameter == null) {
                continue;
            }
            copied.add(new FlowGraph.FunctionParameter(parameter.getName(), parameter.getType()));
        }
        return copied;
    }

    private static List<FlowGraph.EditorPassthrough> copyEditorPassthroughs(List<FlowGraph.EditorPassthrough> passthroughs) {
        List<FlowGraph.EditorPassthrough> copied = new ArrayList<>();
        if (passthroughs == null) {
            return copied;
        }
        for (FlowGraph.EditorPassthrough passthrough : passthroughs) {
            if (passthrough == null) {
                continue;
            }
            copied.add(new FlowGraph.EditorPassthrough(passthrough.getNodeId(), passthrough.getInputPin()));
        }
        return copied;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (studioTabsManager != null && studioTabsManager.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        if (nodeItemSelector != null && nodeItemSelector.visible && nodeItemSelector.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && studioContentBrowser.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            return activeView.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (paletteSidePanel != null && paletteSidePanel.isVisible() && paletteSidePanel.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        if (studioResourcePanel != null && studioResourcePanel.isVisible() && studioResourcePanel.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
        int wx = (int) worldMouse[0];
        int wy = (int) worldMouse[1];
        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);
            if (widget.mouseScrolled(wx, wy, verticalAmount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (headerBackground != null) {
            layoutHeaderButtons();
        }
        layoutStudioHeaderButtons();
        for (StudioDocument document : studioDocuments) {
            if (document.view() != null) {
                document.view().resize(width, studioMode ? studioEditorHeight() : height);
            }
        }
        updatePositions();
    }

    @Override
    public <T extends Widget> T addDrawableChild(T widget) {
        super.addDrawableChild(widget);
        return widget;
    }

    @Override
    public int getInitialWidth() {
        return initialWidth;
    }

    @Override
    public int getInitialHeight() {
        return initialHeight;
    }


    @Override
    public Screen asScreen() {
        return this;
    }

    @Override public void updateRenderOrder(List<AnimatedWidget> list) {}
    @Override public void setHitBottom(boolean hitBottom) {}
}

