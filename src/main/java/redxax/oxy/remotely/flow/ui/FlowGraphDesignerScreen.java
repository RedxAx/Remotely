package redxax.oxy.remotely.flow.ui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.FlowDebugController;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.data.flow.world.WorldDashboardEntry;
import redxax.oxy.remotely.data.flow.world.WorldGeneratorDescriptor;
import redxax.oxy.remotely.data.flow.world.WorldInventoryGroup;
import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import redxax.oxy.remotely.data.flow.world.WorldProfileSettings;
import redxax.oxy.remotely.data.flow.world.WorldRegistryEntry;
import redxax.oxy.remotely.data.flow.world.WorldSnapshot;
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
import redxax.oxy.remotely.flow.ui.studio.ReSyncResourceCreator;
import redxax.oxy.remotely.flow.ui.studio.RecipeSlotTarget;
import redxax.oxy.remotely.flow.ui.studio.RecipeStationLayout;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.flow.ui.studio.ScreenBackedStudioView;
import redxax.oxy.remotely.flow.ui.studio.GuiStudioPreviewView;
import redxax.oxy.remotely.flow.ui.studio.ScoreboardStudioPreviewView;
import redxax.oxy.remotely.flow.ui.studio.StudioCatalogRefreshView;
import redxax.oxy.remotely.flow.ui.studio.StudioInfiniteScreen;
import redxax.oxy.remotely.flow.ui.studio.StudioDocument;
import redxax.oxy.remotely.flow.ui.studio.StudioOverlayView;
import redxax.oxy.remotely.flow.ui.studio.StudioPanel;
import redxax.oxy.remotely.flow.ui.studio.StudioPriorityInputView;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import redxax.oxy.remotely.flow.ui.studio.StudioSelectorView;
import redxax.oxy.remotely.flow.ui.studio.StudioHeaderProvider;
import redxax.oxy.remotely.flow.ui.studio.StudioViewportState;
import redxax.oxy.remotely.flow.ui.studio.TabStudioPreviewView;
import redxax.oxy.remotely.flow.ui.studio.WorldStudioDocumentView;
import redxax.oxy.remotely.flow.ui.marketplace.ReSyncMarketplaceScreen;
import redxax.oxy.remotely.packcontent.PackContentRegistry;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.ui.WorldGenEditorScreen;
import org.lwjgl.glfw.GLFW;
import restudio.rebase.Rebase;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.backend.feature.NetworkTransferFeature;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.resource.InstanceResource;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rebase.ui.screens.editor.WorkspaceTreeExplorer;
import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rescreen.game.MinecraftAssetReference;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.UiHost;
import restudio.rescreen.platform.lwjgl.MinecraftRenderItem;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;
import restudio.rescreen.ui.desktop.DesktopIconWidget;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.rescreen.layout.DesktopLayout;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.*;
import restudio.rescreen.ui.rescreen.ReScreen.HeaderBuilder.Position;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.FileUtils;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static restudio.rescreen.config.Config.animationsEnabled;
import static restudio.rescreen.config.Config.deltaTime;
import static restudio.rescreen.config.Config.desktopMode;
import static restudio.rescreen.config.Config.globalExpandSpeed;

public class FlowGraphDesignerScreen extends StudioInfiniteScreen implements UiHost, StudioHeaderProvider, DesktopWindowBehaviorProvider {
    private static final String CUSTOM_FUNCTION_NODE_PREFIX = "custom_function:";
    private static final int RESYNC_PORT = 12441;
    private static final String RESYNC_RELEASE_URL = "https://restudiomc.net/api/releases/resync/latest/download";
    protected static final Set<FlowGraphDesignerScreen> OPEN_SCREENS = new CopyOnWriteArraySet<>();
    private static final String MATERIAL_OPTIONS_SOURCE = "server:minecraft:material";
    private static final String RECIPE_ITEM_OPTIONS_SOURCE = "server:custom_content:recipe_item";
    private static final Map<String, BufferedImage> MOTD_ICON_CACHE = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > 48;
        }
    };
    private static final List<String> FALLBACK_MATERIAL_OPTIONS = List.of(
        "STONE", "COBBLESTONE", "OAK_PLANKS", "OAK_LOG", "GLASS", "GLASS_PANE",
        "GRAY_STAINED_GLASS_PANE", "WHITE_STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE",
        "RED_STAINED_GLASS_PANE", "GREEN_STAINED_GLASS_PANE", "BLUE_STAINED_GLASS_PANE",
        "BARRIER", "CHEST", "ENDER_CHEST", "ANVIL", "BOOK", "PAPER", "MAP",
        "COMPASS", "CLOCK", "DIAMOND", "EMERALD", "GOLD_INGOT", "IRON_INGOT",
        "NETHERITE_INGOT", "REDSTONE", "AMETHYST_SHARD", "ENDER_PEARL",
        "TOTEM_OF_UNDYING", "PLAYER_HEAD", "NAME_TAG"
    );
    protected FlowGraph graph;
    protected final String serverId;
    private static Screen parent;
    private final Screen ownerScreen;
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
    protected StudioPanel paletteStudioPanel;
    private final Map<NodeDefinition.NodeCategory, PopupWidget> categoryPopups = new HashMap<>();
    private List<NodeDefinition.NodeCategory> categoryOrder = List.of();

    private IconButton headerBackground;
    protected final List<AnimatedWidget> headerButtons = new ArrayList<>();
    private final List<AnimatedWidget> activeViewHeaderButtons = new ArrayList<>();
    private AnimatedWidget debugToggleButton;
    private AnimatedWidget debugResumeButton;
    private AnimatedWidget debugStepButton;
    private AnimatedWidget debugStopButton;
    private boolean initialized;
    private boolean debugMode;
    private static final float INITIAL_VIEWPORT_MAX_ZOOM = 1.0F;
    private static final float INITIAL_VIEWPORT_START_ZOOM = 3.0F;
    private static final int INITIAL_VIEWPORT_PADDING = 80;
    private static final int INITIAL_VIEWPORT_STABLE_FRAMES = 2;
    private boolean initialViewportFitPending = true;
    private int initialViewportStableFrames;
    private int initialViewportSignature;
    private final Set<String> initialViewportFittedKeys = new HashSet<>();

    private int initialWidth;
    private int initialHeight;
    protected boolean studioMode;
    private TabsManager studioTabsManager;
    private ReSyncContentBrowserWidget studioContentBrowser;
    private SidePanel studioResourcePanel;
    private StudioPanel studioResourceStudioPanel;
    private final ReSyncStudioPanelState studioPanelState = new ReSyncStudioPanelState();
    private final List<AnimatedWidget> studioResourcePanelWidgets = new ArrayList<>();
    private String studioResourcePanelKey = "";
    private final Map<String, TextInputWidget> studioResourcePanelInputs = new HashMap<>();
    private final Map<String, ToggleWidget> studioResourcePanelToggles = new HashMap<>();
    private IconMessage studioEmptyMessage;
    private final List<StudioDocument> studioDocuments = new ArrayList<>();
    private StudioDocument activeStudioDocument;
    private final FlowGraph studioEmptyGraph = new FlowGraph();
    private boolean syncingStudioTabSelection;
    private String activeNodeRegistryServerId;
    private final Gson gson = new Gson();
    private TextInputWidget commandLabelInput;
    private final List<TextInputWidget> commandPathInputs = new ArrayList<>();
    private ToggleWidget commandStructuredToggle;
    private static final float STUDIO_CONTENT_BROWSER_DEFAULT_RATIO = 0.22F;
    private static final int STUDIO_CONTENT_BROWSER_MIN_HEIGHT = 96;
    private static final int STUDIO_CONTENT_BROWSER_MAX_HEIGHT = 260;
    private static final int STUDIO_CONTENT_BROWSER_TITLE_HEIGHT = 16;
    private static final int STUDIO_CONTENT_BROWSER_COLLAPSED_HEIGHT = STUDIO_CONTENT_BROWSER_TITLE_HEIGHT;
    private static final int STUDIO_CONTENT_BROWSER_RESIZE_GRIP = 5;
    private int studioContentBrowserHeight = -1;
    private boolean studioContentBrowserCollapsed;
    private boolean studioContentBrowserResizing;
    private int studioContentBrowserResizeStartY;
    private int studioContentBrowserResizeStartHeight;
    private float studioContentBrowserAnimatedHeight = -1;
    private final ClientServerView startupServer;
    private final String loaderHint;
    private final String serverTitle;
    private final SecureRandom secureRandom = new SecureRandom();
    private StudioStartupState startupState = StudioStartupState.READY;
    private IconMessage startupIcon;
    private IconButton startupCloseButton;
    private IconButton setupReSyncButton;
    private IconButton welcomeServerButton;
    private boolean startupProbeRunning;
    private boolean setupRunning;
    private boolean studioChromeBuilt;
    private long lastStartupProbeAt;

    private enum StudioStartupState {
        LOADING,
        NOT_SUPPORTED,
        SETUP,
        INSTALLING,
        INSTALLED,
        SERVER_STOPPED,
        READY
    }

    private record AssetBrowserSnapshot(List<String> folders, List<String> resources) {
    }

    private static class CommandBindingContext {
        private String command;
        private List<String> subcommands;
        private Boolean structured;
    }

    private class ReSyncContentBrowserWidget extends AnimatedWidget {
        private final Path projectRoot = Path.of("ReSync");
        private String currentFolder = "";
        private final Deque<String> backHistory = new ArrayDeque<>();
        private final Deque<String> forwardHistory = new ArrayDeque<>();
        private ReSyncProjectMetadata.ResourceEntry selectedResource;
        private ReSyncProjectMetadata.FolderEntry selectedFolder;
        private AnimatedWidget lastGridReleasedWidget;
        private long lastGridReleaseTime;
        private int lastMouseX;
        private int lastMouseY;
        private final Container treeContainer;
        private final Container gridContainer;
        private final TextInputWidget nameInput;
        private final IconMessage emptyFolderMessage;
        private final ReSyncProjectTreeProvider treeProvider;
        private final WorkspaceTreeExplorer treeExplorer;
        private final SquareButtonWidget createButton;
        private final SquareButtonWidget marketplaceButton;
        private final AnimatedButton closeButton;
        private final BufferedImage folderIcon;
        private final BufferedImage flowIcon;
        private final BufferedImage functionIcon;
        private final BufferedImage commandIcon;
        private final BufferedImage itemIcon;
        private final BufferedImage armorIcon;
        private final BufferedImage blockIcon;
        private final BufferedImage guiIcon;
        private final BufferedImage scoreboardIcon;
        private final BufferedImage tabIcon;
        private final BufferedImage chatIcon;
        private final BufferedImage motdIcon;
        private final BufferedImage messageRuleIcon;
        private final BufferedImage recipeIcon;
        private final BufferedImage textIcon;
        private final BufferedImage advancementIcon;
        private final BufferedImage worldGenIcon;
        private final BufferedImage worldIcon;
        private ItemSelectorWidget createContentSelector;
        private AssetBrowserSnapshot lastAssetBrowserSnapshot;

        private ReSyncContentBrowserWidget(int x, int y, int width, int height) {
            super(x, y, width, height, "");
            animateElevation = false;
            entranceAnimationEnabled = false;
            enableHoverColors = false;
            nameInput = new TextInputWidget.Builder()
                .placeholder("Selected")
                .size(110, 18)
                .build();
            treeContainer = new Container("studio-content-tree", x + 4, y + 1, 210, height - 2);
            treeContainer.layout(new ManagedLayout()).columns(1).padding(2).scrolling(true).backgroundDrawing(false);
            treeContainer.setRelativeScissor(1, 1, 1, 1);
            gridContainer = new Container("studio-content-grid", x + 220, y + 1, width - 224, height - 2);
            gridContainer.layout(new DesktopLayout()).backgroundDrawing(false).enableSelecting(true).enableDoubleClick(false);
            gridContainer.setRelativeScissor(1, 1, 1, 1);
            emptyFolderMessage = new IconMessage(0, 0, 64, 64, "No Assets In This Folder", "emptyFolder.png");
            emptyFolderMessage.entranceAnimationEnabled = false;
            treeProvider = new ReSyncProjectTreeProvider();
            treeExplorer = new WorkspaceTreeExplorer(FlowGraphDesignerScreen.this, treeContainer, this::openTreeFile, false);
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
            marketplaceButton = new SquareButtonWidget.Builder()
                .imagePath("market.png")
                .size(18, 18)
                .hint("Marketplace")
                .onClick(FlowGraphDesignerScreen.this::openReSyncMarketplace)
                .build();
            closeButton = new AnimatedButton.Builder()
                .onClick(FlowGraphDesignerScreen.this::toggleStudioContentBrowser)
                .accentType(ThemeManager.getAccent("danger"))
                .animateElevation(false)
                .size(12, 8)
                .hint("Collapse Assets")
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
            functionIcon = resources.getImage(Identifier.icon("snippets.png"));
            commandIcon = resources.getImage(Identifier.icon("terminal.png"));
            itemIcon = resources.getImage(Identifier.icon("item.png"));
            armorIcon = resources.getImage(Identifier.icon("armor.png"));
            blockIcon = resources.getImage(Identifier.icon("block.png"));
            guiIcon = resources.getImage(Identifier.icon("fullPanel.png"));
            scoreboardIcon = resources.getImage(Identifier.icon("panel.png"));
            tabIcon = resources.getImage(Identifier.icon("topPanel.png"));
            chatIcon = resources.getImage(Identifier.icon("chat.png"));
            motdIcon = resources.getImage(Identifier.icon("hi.png"));
            messageRuleIcon = resources.getImage(Identifier.icon("edit.png"));
            recipeIcon = resources.getImage(Identifier.icon("crafting.png"));
            textIcon = resources.getImage(Identifier.icon("text.png"));
            advancementIcon = resources.getImage(Identifier.icon("advancement.png"));
            worldGenIcon = resources.getImage(Identifier.icon("map.png"));
            worldIcon = resources.getImage(Identifier.icon("earth.png"));
            updateContainers();
            rebuild();
        }

        @Override
        protected void drawContent(IDrawContext context, int mouseX, int mouseY) {
            int border = ThemeManager.getColor(ThemeColor.innerBorder);
            renderBrowserTitle(context, mouseX, mouseY, border);
            if (studioContentBrowserContentHidden()) {
                return;
            }
            int dividerX = getX() + currentFolderWidth() + 4;
            context.fill(dividerX, contentTop(), dividerX + 1, getY() + getHeight() - 1, border);
            renderClippedContainer(context, treeContainer, mouseX, mouseY);
            renderClippedContainer(context, gridContainer, mouseX, mouseY);
            renderEmptyFolderMessage(context, mouseX, mouseY);
            marketplaceButton.render(context, mouseX, mouseY, 0);
            createButton.render(context, mouseX, mouseY, 0);
        }

        private void renderBrowserTitle(IDrawContext context, int mouseX, int mouseY, int border) {
            context.fill(getX(), getY(), getX() + getWidth(), getY() + STUDIO_CONTENT_BROWSER_TITLE_HEIGHT, ThemeManager.getColor(ThemeColor.inClickableBackground));
            context.fillBorder(getX(), getY(), getX() + getWidth(), getY() + STUDIO_CONTENT_BROWSER_TITLE_HEIGHT, 1, border);
            context.drawText("Assets", getX() + 4, getY() + 4, ThemeManager.getColor(ThemeColor.text), true);
            closeButton.render(context, mouseX, mouseY, 0);
        }

        private void renderClippedContainer(IDrawContext context, Container container, int mouseX, int mouseY) {
            context.pushScissorState();
            context.enableScissor(container.getX() + 1, container.getY() + 1, container.getX() + container.getWidth() - 1, container.getY() + container.getHeight() - 1);
            container.render(context, mouseX, mouseY, 0);
            context.disableScissor();
            context.popScissorState();
        }

        private void renderEmptyFolderMessage(IDrawContext context, int mouseX, int mouseY) {
            if (!gridContainer.getWidgets().isEmpty()) {
                return;
            }
            int centerX = gridContainer.getX() + gridContainer.getWidth() / 2;
            int centerY = gridContainer.getY() + gridContainer.getHeight() / 2;
            emptyFolderMessage.setPosition(centerX - emptyFolderMessage.getWidth() / 2, centerY - emptyFolderMessage.getHeight() / 2);
            emptyFolderMessage.setScissorRegion(gridContainer.getX() + 1, gridContainer.getY() + 1, gridContainer.getX() + gridContainer.getWidth() - 1, gridContainer.getY() + gridContainer.getHeight() - 1);
            emptyFolderMessage.render(context, mouseX, mouseY, 0);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver(mouseX, mouseY)) {
                return false;
            }
            lastMouseX = (int) mouseX;
            lastMouseY = (int) mouseY;
            if (handleHistoryMouseButton(button)) {
                return true;
            }
            if (closeButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (marketplaceButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isResizeGrip(mouseX, mouseY)) {
                studioContentBrowserResizing = true;
                studioContentBrowserResizeStartY = (int) mouseY;
                studioContentBrowserResizeStartHeight = studioContentBrowserHeight();
                return true;
            }
            if (studioContentBrowserCollapsed) {
                toggleStudioContentBrowser();
                return true;
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

        private boolean handleHistoryMouseButton(int button) {
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
            if (studioContentBrowserResizing) {
                studioContentBrowserResizing = false;
                updatePositions();
                return true;
            }
            if (!isMouseOver(mouseX, mouseY)) {
                return false;
            }
            if (studioContentBrowserCollapsed) {
                return true;
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
            if (studioContentBrowserResizing) {
                resizeStudioContentBrowser(studioContentBrowserResizeStartHeight + studioContentBrowserResizeStartY - (int) mouseY);
                return true;
            }
            if (!isMouseOver(mouseX, mouseY)) {
                return false;
            }
            if (studioContentBrowserCollapsed) {
                return true;
            }
            return treeContainer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) || gridContainer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
            if (!isMouseOver(mouseX, mouseY)) {
                return false;
            }
            if (studioContentBrowserCollapsed) {
                return true;
            }
            return treeContainer.mouseScrolled(mouseX, mouseY, amount) || gridContainer.mouseScrolled(mouseX, mouseY, amount);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (studioContentBrowserCollapsed) {
                return false;
            }
            return treeContainer.keyPressed(keyCode, scanCode, modifiers) || gridContainer.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (studioContentBrowserCollapsed) {
                return false;
            }
            return treeContainer.charTyped(chr, modifiers) || gridContainer.charTyped(chr, modifiers);
        }

        @Override
        public void tick() {
            super.tick();
            if (advanceStudioContentBrowserHeightAnimation()) {
                updateStudioLayout();
            }
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
            AssetBrowserSnapshot snapshot = assetBrowserSnapshot();
            if (snapshot.equals(lastAssetBrowserSnapshot)) {
                return;
            }
            lastAssetBrowserSnapshot = snapshot;
            rebuildTree();
            rebuildGrid();
        }

        private AssetBrowserSnapshot assetBrowserSnapshot() {
            List<String> folders = new ArrayList<>();
            for (ReSyncProjectMetadata.FolderEntry folder : studioAllFolders()) {
                folders.add(String.join("\u0001",
                    folder.getPath(),
                    folder.getParentPath(),
                    folder.getName(),
                    String.valueOf(folder.getSortOrder()),
                    String.valueOf(folder.isCollapsed())));
            }
            Collections.sort(folders);
            List<String> resources = new ArrayList<>();
            for (ReSyncProjectMetadata.ResourceEntry resource : studioAllResources()) {
                resources.add(String.join("\u0001",
                    resource.getType(),
                    resource.getId(),
                    resource.getDisplayName(),
                    resource.getPath(),
                    String.valueOf(resource.getSortOrder()),
                    iconPathFor(resource)));
            }
            Collections.sort(resources);
            return new AssetBrowserSnapshot(folders, resources);
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
            rebuildTree();
            rebuildGrid();
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
            showExplorerMenu(lastMouseX, lastMouseY);
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
            ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(FlowGraphDesignerScreen.this)
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
            ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(FlowGraphDesignerScreen.this)
                .addIconItem("New Folder", "folder.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FOLDER), "Create Folder")
                .addIconItem("New Flow", "graph.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FLOW), "Create Flow")
                .addIconItem("New Function", "snippets.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.FUNCTION), "Create Function")
                .addIconItem("New Command", "terminal.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.COMMAND), "Create Command")
                .addIconItem("New Content", "resources.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.CUSTOM_CONTENT), "Create Content")
                .addIconItem("New GUI", "fullPanel.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.GUI), "Create GUI")
                .addIconItem("New Scoreboard", "panel.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.SCOREBOARD), "Create Scoreboard")
                .addIconItem("New Tab", "topPanel.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.TAB), "Create Tab")
                .addIconItem("New Chat", "chat.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.CHAT_CHANNEL), "Create Chat")
                .addIconItem("New MOTD", "hi.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.MOTD_PROFILE), "Create MOTD")
                .addIconItem("New Message Rule", "edit.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.MESSAGE_RULE), "Create Message Rule")
                .addIconItem("New Recipe", "crafting.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.RECIPE_DEFINITION), "Create Recipe")
                .addIconItem("New Advancement", "advancement.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.ADVANCEMENT_TREE), "Create Advancement")
                .addIconItem("New Dialog", "chat.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.DIALOG), "Create Dialog")
                .addIconItem("New Text", "text.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.TEXT_TEMPLATE), "Create Text")
                .addIconItem("New WorldGen", "map.png", () -> showCreateResourcePopup(ReSyncResourceDragPayload.WORLDGEN), "Create WorldGen");
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
            if (ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(type)) {
                showCreateContentPopup();
                return;
            }
            ReSyncResourceCreator.showCreatePopup(FlowGraphDesignerScreen.this, serverId, type, createTargetFolder(), null, this::openCreatedResource);
        }

        private void showCreateContentPopup() {
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
            AnimatedButton typeButton = createContentSelectorButton(selectedType[0]);
            AnimatedButton providerButton = createContentSelectorButton(selectedProvider[0]);
            AnimatedButton assetButton = new AnimatedButton.Builder()
                .label(selectedAsset[0])
                .size(220, 20)
                .entranceAnimation(false)
                .build();
            typeButton.setAction(() -> showCreateContentSearchSelector(List.of("item", "armor", "block"), selectedType[0], value -> {
                if (!isRealContentOption(value)) {
                    return;
                }
                selectedType[0] = value;
                selectedAsset[0] = "vanilla".equalsIgnoreCase(selectedProvider[0]) ? defaultContentMaterial(value) : "";
                typeButton.setMessage(contentOptionLabel(value));
                assetButton.setMessage(assetButtonLabel(selectedAsset[0], selectedProvider[0]));
            }, typeButton.getX(), typeButton.getY() + typeButton.getHeight()));
            providerButton.setAction(() -> showCreateContentSearchSelector(providerOptions(), selectedProvider[0], value -> {
                if (!isRealContentOption(value)) {
                    return;
                }
                selectedProvider[0] = value;
                selectedAsset[0] = "vanilla".equalsIgnoreCase(value) ? defaultContentMaterial(selectedType[0]) : "";
                providerButton.setMessage(contentOptionLabel(value));
                assetButton.setMessage(assetButtonLabel(selectedAsset[0], value));
            }, providerButton.getX(), providerButton.getY() + providerButton.getHeight()));
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
            builder.addRow("Type", true, 22, typeButton);
            builder.addRow("Asset", true, 22, providerButton, assetButton);

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
                    if (createContentResource(id, name, selectedType[0], selectedProvider[0], selectedAsset[0])) {
                        closeCreateContentSearchSelector();
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

        private AnimatedButton createContentSelectorButton(String selected) {
            return new AnimatedButton.Builder()
                .label(contentOptionLabel(selected))
                .size(110, 20)
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
            int selectorX = Math.clamp(x, 8, Math.max(8, FlowGraphDesignerScreen.this.width - selector.getWidth() - 8));
            int selectorY = Math.clamp(y, 32, Math.max(32, FlowGraphDesignerScreen.this.height - selector.getHeight() - 20));
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
            setFocusedWidget(null);
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
            List<String> values = OptionCatalogCache.getInstance().getValues(serverId, source);
            if (!values.isEmpty()) {
                return values;
            }
            if (!OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
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
            if (manager != null && source != null && !OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
                manager.ensureFlowClient(serverId).requestOptionCatalog(source);
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

        private void openCreatedResource(ReSyncResourceCreator.Result result) {
            if (result == null) {
                return;
            }
            String type = result.type();
            String id = result.id();
            Object resource = result.resource();
            rebuild();
            switch (type) {
                case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION, ReSyncResourceDragPayload.COMMAND -> {
                    if (resource instanceof FlowGraph graph) {
                        openStudioGraphDocument(type, id, id, graph);
                    }
                }
                case ReSyncResourceDragPayload.GUI, ReSyncResourceDragPayload.SCOREBOARD, ReSyncResourceDragPayload.TAB, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                     ReSyncResourceDragPayload.DIALOG -> openStudioDesigner(type, id);
                case ReSyncResourceDragPayload.CHAT_CHANNEL, ReSyncResourceDragPayload.CHAT_FORMAT,
                     ReSyncResourceDragPayload.CHAT_RULE, ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT, ReSyncResourceDragPayload.MENTION_STYLE,
                     ReSyncResourceDragPayload.IGNORE_LIST, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                     ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE -> {
                    if (resource instanceof JsonObject json) {
                        openJsonResourceDocument(type, id, id, json);
                    }
                }
                case ReSyncResourceDragPayload.WORLDGEN -> {
                    if (resource instanceof WorldGenProject project) {
                        openStudioWorldGenDocument(id, id, project);
                    }
                }
                default -> {
                }
            }
        }

        private boolean createContentResource(String id, String name, String contentType, String provider, String asset) {
            FlowManager manager = FlowManager.getInstance();
            if (manager == null) {
                return false;
            }
            String targetFolder = createTargetFolder();
            if (manager.getProjectMetadata(serverId).findResource(ReSyncResourceDragPayload.CUSTOM_CONTENT, id) != null || resourceExists(manager, ReSyncResourceDragPayload.CUSTOM_CONTENT, id)) {
                new Notification("Error", "Content ID already exists", Notification.Type.ERROR);
                return false;
            }
            String requestedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
            String normalizedType = switch (requestedType) {
                case "block", "armor" -> requestedType;
                default -> "item";
            };
            String selectedProvider = provider == null || provider.isBlank() ? "vanilla" : provider;
            FlowGraph contentGraph = manager.createContentFlow(serverId, id, normalizedType, name);
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
            ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
            ReSyncProjectMetadata.ResourceEntry entry = metadata.ensureResource(ReSyncResourceDragPayload.CUSTOM_CONTENT, id, name, targetFolder);
            entry.setPath(targetFolder);
            manager.saveProjectMetadata(serverId, metadata);
            rebuild();
            openStudioViewDocument(ReSyncResourceDragPayload.CUSTOM_CONTENT, id, name, contentGraph, new ScreenBackedStudioView(FlowGraphDesignerScreen.this, new ContentStudioScreen(serverId, null, contentGraph.getId(), FlowGraphDesignerScreen.this)));
            return true;
        }

        private String createTargetFolder() {
            if (selectedFolder != null) {
                return selectedFolder.getPath();
            }
            return ReSyncProjectMetadata.normalizePath(currentFolder);
        }

        private boolean resourceExists(FlowManager manager, String type, String id) {
            return ReSyncResourceCreator.exists(manager, serverId, type, id);
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
                case ReSyncResourceDragPayload.CHAT_CHANNEL, ReSyncResourceDragPayload.CHAT_FORMAT,
                     ReSyncResourceDragPayload.CHAT_RULE, ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT, ReSyncResourceDragPayload.MENTION_STYLE,
                     ReSyncResourceDragPayload.IGNORE_LIST, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                     ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                     ReSyncResourceDragPayload.DIALOG -> {
                    ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(selectedResource.getType());
                    yield resourceType != null && manager.renameJsonResource(serverId, resourceType, selectedResource.getId(), newId);
                }
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
                    studioDocuments.set(i, new StudioDocument(document.type(), newId, newId, document.graph(), document.view(), document.viewport()));
                    break;
                }
            }
            rebuild();
            syncStudioDocumentTabs();
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
                case ReSyncResourceDragPayload.CHAT_CHANNEL, ReSyncResourceDragPayload.CHAT_FORMAT,
                     ReSyncResourceDragPayload.CHAT_RULE, ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT, ReSyncResourceDragPayload.MENTION_STYLE,
                     ReSyncResourceDragPayload.IGNORE_LIST, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                     ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                     ReSyncResourceDragPayload.DIALOG -> {
                    ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(selectedResource.getType());
                    if (resourceType == null) {
                        return;
                    }
                    manager.deleteJsonResource(serverId, resourceType, selectedResource.getId());
                }
                case ReSyncResourceDragPayload.WORLDGEN -> WorldGenManager.getInstance().deleteProject(serverId, selectedResource.getId());
                default -> {
                    return;
                }
            }
            ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
            metadata.getResources().removeIf(entry -> entry.key().equals(selectedResource.key()));
            manager.saveProjectMetadata(serverId, metadata);
            studioDocuments.removeIf(document -> document.key().equals(selectedResource.key()));
            syncStudioDocumentTabs();
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

        private BufferedImage iconFor(ReSyncProjectMetadata.ResourceEntry resource) {
            return switch (iconPathFor(resource)) {
                case "folder.png" -> folderIcon;
                case "snippets.png" -> functionIcon;
                case "terminal.png" -> commandIcon;
                case "item.png" -> itemIcon;
                case "armor.png" -> armorIcon;
                case "block.png" -> blockIcon;
                case "fullPanel.png" -> guiIcon;
                case "panel.png" -> scoreboardIcon;
                case "topPanel.png" -> tabIcon;
                case "chat.png" -> chatIcon;
                case "hi.png" -> motdIcon;
                case "edit.png" -> messageRuleIcon;
                case "crafting.png" -> recipeIcon;
                case "text.png" -> textIcon;
                case "advancement.png" -> advancementIcon;
                case "map.png" -> worldGenIcon;
                case "earth.png" -> worldIcon;
                default -> flowIcon;
            };
        }

        private String iconPathFor(ReSyncProjectMetadata.ResourceEntry resource) {
            return studioResourceIconPath(resource.getType(), resource.getId());
        }

        private void updateContainers() {
            int folderWidth = currentFolderWidth();
            int contentTop = contentTop();
            treeContainer.setPosition(getX() + 4, contentTop);
            treeContainer.setSize(folderWidth - 8, Math.max(0, getHeight() - contentOffset() - 1));
            gridContainer.setPosition(getX() + folderWidth + 8, contentTop);
            gridContainer.setSize(getWidth() - folderWidth - 12, Math.max(0, getHeight() - contentOffset() - 1));
            createButton.setPosition(getX() + getWidth() - 24, getY() + STUDIO_CONTENT_BROWSER_TITLE_HEIGHT + 4);
            marketplaceButton.setPosition(getX() + getWidth() - 46, getY() + STUDIO_CONTENT_BROWSER_TITLE_HEIGHT + 4);
            closeButton.setPosition(getX() + getWidth() - 16, getY() + 3);
            closeButton.accentType = ThemeManager.getAccent(studioContentBrowserCollapsed ? "nice" : "danger");
            closeButton.setHint(studioContentBrowserCollapsed ? "Show Assets" : "Collapse Assets");
            treeContainer.setRelativeScissor(1, 1, 1, 1);
            gridContainer.setRelativeScissor(1, 1, 1, 1);
            treeContainer.updateWidgetPositions();
            gridContainer.updateWidgetPositions();
        }

        private int contentOffset() {
            return studioContentBrowserContentHidden() ? getHeight() : STUDIO_CONTENT_BROWSER_TITLE_HEIGHT;
        }

        private int contentTop() {
            return getY() + contentOffset();
        }

        private boolean isResizeGrip(double mouseX, double mouseY) {
            return !studioContentBrowserCollapsed
                && mouseX >= getX()
                && mouseX <= getX() + getWidth()
                && mouseY >= getY()
                && mouseY <= getY() + STUDIO_CONTENT_BROWSER_RESIZE_GRIP;
        }

        private boolean studioContentBrowserContentHidden() {
            return studioContentBrowserCollapsed && getHeight() <= STUDIO_CONTENT_BROWSER_COLLAPSED_HEIGHT + 1;
        }

        private int currentFolderWidth() {
            return Math.clamp(getWidth() / 5, 160, 216);
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

    private final StudioScreen.History<GraphSnapshot> graphHistory = history(() -> new GraphSnapshot(graph, selectedNodeIds), this::restoreSnapshot);

    @Override
    protected StudioScreen.History<?> activeHistory() {
        ReSyncStudioView view = activeStudioView();
        if (view instanceof JsonResourceStudioView resourceView && resourceView.hasResourceHistory()) {
            return resourceView.resourceHistory;
        }
        return graphHistory;
    }

    public FlowGraphDesignerScreen(FlowGraph graph, String serverId, Screen parent) {
        this(graph, serverId, parent, null, "", "");
    }

    public FlowGraphDesignerScreen(FlowGraph graph, String serverId, Screen parent, ClientServerView startupServer, String loaderHint) {
        this(graph, serverId, parent, startupServer, loaderHint, startupServer != null ? startupServer.name : "");
    }

    public FlowGraphDesignerScreen(FlowGraph graph, String serverId, Screen parent, ClientServerView startupServer, String loaderHint, String serverTitle) {
        super();
        this.zoomLevel = INITIAL_VIEWPORT_MAX_ZOOM;
        this.targetZoomLevel = INITIAL_VIEWPORT_START_ZOOM;
        this.graph = graph;
        this.serverId = serverId;
        this.ownerScreen = parent;
        this.startupServer = startupServer;
        this.loaderHint = safeText(loaderHint);
        this.serverTitle = safeText(serverTitle);
        if (!(parent instanceof FlowGraphDesignerScreen)) {
            FlowGraphDesignerScreen.parent = parent;
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

    public FlowGraphDesignerScreen enableStudioMode() {
        this.studioMode = true;
        this.startupState = StudioStartupState.LOADING;
        return this;
    }

    public void openStudioFlow(FlowGraph targetGraph, String title) {
        if (targetGraph == null) {
            return;
        }
        openStudioGraphDocument(targetGraph.isFunction() ? ReSyncResourceDragPayload.FUNCTION : ReSyncResourceDragPayload.FLOW,
            targetGraph.getId(), title == null || title.isBlank() ? targetGraph.getId() : title, targetGraph);
    }

    public void openWorkspaceFlowEditor(String flowId, String branchPin) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || flowId == null) {
            return;
        }
        FlowGraph targetGraph = manager.getFlowsForServer(serverId).get(flowId);
        if (targetGraph == null) {
            manager.openFlowEditor(serverId, null, flowId, branchPin);
            return;
        }
        openStudioFlow(targetGraph, manager.getFlowName(serverId, flowId));
        if (branchPin != null) {
            focusContentBranch(branchPin);
        }
    }

    public void openWorkspaceFlowEditor(String flowId) {
        openWorkspaceFlowEditor(flowId, null);
    }

    public void openWorkspaceGuiDesigner(String guiId) {
        openStudioDesigner(ReSyncResourceDragPayload.GUI, guiId);
    }

    public void openWorkspaceScoreboardDesigner(String scoreboardId) {
        openStudioDesigner(ReSyncResourceDragPayload.SCOREBOARD, scoreboardId);
    }

    public void openWorkspaceTabDesigner(String tabId) {
        openStudioDesigner(ReSyncResourceDragPayload.TAB, tabId);
    }

    public void openWorkspaceDialogDesigner(String dialogId) {
        openStudioDesigner(ReSyncResourceDragPayload.DIALOG, dialogId);
    }

    public void refreshStudioWorkspace() {
        refreshStudioWorkspace(true);
    }

    public void refreshStudioWorkspace(boolean rebuildContentBrowser) {
        if (studioContentBrowser != null) {
            if (rebuildContentBrowser) {
                studioContentBrowser.rebuild();
            }
        }
        refreshStudioResourcePanel();
    }

    private void openStudioGraphDocument(String type, String id, String title, FlowGraph targetGraph) {
        if (targetGraph == null) {
            return;
        }
        addStudioDocument(type, id, title == null || title.isBlank() ? id : title, targetGraph, null);
        syncStudioDocumentTabs();
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
            } else {
                manager.openGuiDesigner(serverId, null, id, this);
            }
            return;
        }
        if (ReSyncResourceDragPayload.SCOREBOARD.equals(type)) {
            ScoreboardDefinition scoreboard = manager.getScoreboardsForServer(serverId).get(id);
            if (scoreboard != null) {
                openStudioViewDocument(type, id, manager.getScoreboardName(serverId, id), new ScreenBackedStudioView(this, new ScoreboardDesignerScreen(scoreboard, serverId, this)));
            } else {
                manager.openScoreboardDesigner(serverId, null, id, this);
            }
            return;
        }
        if (ReSyncResourceDragPayload.TAB.equals(type)) {
            TabDefinition tab = manager.getTabsForServer(serverId).get(id);
            if (tab != null) {
                openStudioViewDocument(type, id, manager.getTabName(serverId, id), new ScreenBackedStudioView(this, new TabDesignerScreen(tab, serverId, this)));
            } else {
                manager.openTabDesigner(serverId, null, id, this);
            }
            return;
        }
        if (ReSyncResourceDragPayload.ADVANCEMENT_TREE.equals(type)) {
            JsonObject tree = manager.getJsonResourcesForServer(serverId, ReSyncResourceType.ADVANCEMENT_TREE).get(id);
            if (tree != null) {
                openStudioViewDocument(type, id, ReSyncResourceType.ADVANCEMENT_TREE.extractName(tree), new ScreenBackedStudioView(this, new AdvancementDesignerScreen(tree, serverId, this)));
            }
        }
        if (ReSyncResourceDragPayload.DIALOG.equals(type)) {
            JsonObject dialog = manager.getJsonResourcesForServer(serverId, ReSyncResourceType.DIALOG).get(id);
            if (dialog != null) {
                openStudioViewDocument(type, id, ReSyncResourceType.DIALOG.extractName(dialog), new ScreenBackedStudioView(this, new DialogDesignerScreen(dialog, serverId, this)));
            } else {
                manager.openDialogDesigner(serverId, id, this);
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
        syncStudioDocumentTabs();
        selectStudioDocument(ReSyncProjectMetadata.resourceKey(type, id));
    }

    private void addStudioDocument(String type, String id, String title, FlowGraph targetGraph, ReSyncStudioView view) {
        String key = ReSyncProjectMetadata.resourceKey(type, id);
        for (StudioDocument document : studioDocuments) {
            if (document.key().equals(key)) {
                FlowManager manager = FlowManager.getInstance();
                if (manager != null) {
                    ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
                    metadata.addOpenDocument(type, id, title);
                    manager.saveProjectMetadata(serverId, metadata, false);
                }
                return;
            }
        }
        ReSyncStudioView documentView = view != null ? view : createStudioDocumentView(type, id, targetGraph);
        StudioDocument document = new StudioDocument(type, id, title, targetGraph, documentView, new StudioViewportState());
        studioDocuments.add(document);
        FlowManager manager = FlowManager.getInstance();
        if (manager != null) {
            ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
            metadata.addOpenDocument(type, id, title);
            manager.saveProjectMetadata(serverId, metadata, false);
        }
    }

    private ReSyncStudioView createStudioDocumentView(String type, String id, FlowGraph targetGraph) {
        if (targetGraph != null) {
            return null;
        }
        if (ReSyncResourceDragPayload.GUI.equals(type)) {
            return new GuiStudioPreviewView(serverId, id);
        }
        if (ReSyncResourceDragPayload.SCOREBOARD.equals(type)) {
            return new ScoreboardStudioPreviewView(serverId, id);
        }
        if (ReSyncResourceDragPayload.TAB.equals(type)) {
            return new TabStudioPreviewView(serverId, id);
        }
        return null;
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
        for (FlowGraphDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.getServerId())) {
                screen.onOptionCatalogRefreshed();
            }
        }
    }

    public static FlowGraphDesignerScreen getStudioScreen(String serverId) {
        for (FlowGraphDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null
                && screen.studioMode
                && screen.startupState == StudioStartupState.READY
                && screen.studioChromeBuilt
                && serverId != null
                && serverId.equals(screen.getServerId())) {
                return screen;
            }
        }
        return null;
    }

    public static void refreshWorldsForServer(String serverId) {
        for (FlowGraphDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.getServerId())) {
                screen.onWorldSnapshotRefreshed();
            }
        }
    }

    public static void handleWorldOperationResultForServer(String serverId, WorldOperationResult result) {
        for (FlowGraphDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.getServerId())) {
                screen.handleWorldOperationResult(result);
            }
        }
    }

    public static void handleWorldAuditSnapshotForServer(String serverId, JsonElement data) {
        for (FlowGraphDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.getServerId())) {
                screen.showWorldAuditSnapshot(data);
            }
        }
    }

    protected void onOptionCatalogRefreshed() {
        closeNodeItemSelector();
        refreshNodeRegistry();
        for (StudioDocument document : studioDocuments) {
            if (document.view() instanceof StudioCatalogRefreshView refreshView) {
                refreshView.onStudioCatalogRefreshed();
            }
        }
    }

    private void onWorldSnapshotRefreshed() {
        for (StudioDocument document : studioDocuments) {
            if (document.view() instanceof WorldStudioDocumentView worldView) {
                worldView.refreshWorlds();
            }
        }
    }

    private void handleWorldOperationResult(WorldOperationResult result) {
        if (result == null || !result.isSuccess()) {
            return;
        }
        String action = safeText(result.getAction()).trim().toLowerCase(Locale.ROOT);
        for (StudioDocument document : studioDocuments) {
            if (document.view() instanceof WorldStudioDocumentView worldView) {
                worldView.handleWorldOperationResult(result);
            }
        }
        if ("whoworld".equals(action)) {
            showWorldWhoPopup(result);
        }
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

    private void applyInitialViewportFitIfReady() {
        String viewportFitKey = initialViewportFitKey();
        if (!initialViewportFitPending || initialViewportFittedKeys.contains(viewportFitKey) || width <= 0 || height <= 0) {
            return;
        }
        if (studioMode && (startupState != StudioStartupState.READY || activeStudioDocument == null)) {
            return;
        }
        if (worldWidgets.isEmpty()) {
            initialViewportFitPending = false;
            initialViewportFittedKeys.add(viewportFitKey);
            zoomLevel = INITIAL_VIEWPORT_MAX_ZOOM;
            targetZoomLevel = INITIAL_VIEWPORT_MAX_ZOOM;
            panX = 0.0F;
            panY = 0.0F;
            targetPanX = 0.0F;
            targetPanY = 0.0F;
            return;
        }
        for (Widget widget : worldWidgets) {
            if (widget instanceof FlowNodeWidget flowNodeWidget && !flowNodeWidget.hasLoadedDefinition()) {
                initialViewportStableFrames = 0;
                initialViewportSignature = 0;
                return;
            }
        }
        int signature = initialViewportSignature();
        if (signature == initialViewportSignature) {
            initialViewportStableFrames++;
        } else {
            initialViewportSignature = signature;
            initialViewportStableFrames = 1;
        }
        if (initialViewportStableFrames < INITIAL_VIEWPORT_STABLE_FRAMES) {
            return;
        }
        fitViewportToWorldWidgets();
        initialViewportFitPending = false;
        initialViewportFittedKeys.add(viewportFitKey);
    }

    private String initialViewportFitKey() {
        if (studioMode && activeStudioDocument != null) {
            return activeStudioDocument.key();
        }
        return graph != null && graph.getId() != null ? graph.getId() : "screen";
    }

    private int initialViewportSignature() {
        int signature = worldWidgets.size();
        for (Widget widget : worldWidgets) {
            signature = 31 * signature + widget.getX();
            signature = 31 * signature + widget.getY();
            signature = 31 * signature + widget.getWidth();
            signature = 31 * signature + widget.getHeight();
        }
        return signature;
    }

    private void fitViewportToWorldWidgets() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Widget widget : worldWidgets) {
            minX = Math.min(minX, widget.getX());
            minY = Math.min(minY, widget.getY());
            maxX = Math.max(maxX, widget.getX() + widget.getWidth());
            maxY = Math.max(maxY, widget.getY() + widget.getHeight());
        }
        if (minX == Integer.MAX_VALUE || minY == Integer.MAX_VALUE || maxX == Integer.MIN_VALUE || maxY == Integer.MIN_VALUE) {
            return;
        }
        int viewportWidth = Math.max(1, viewportFitWidth() - INITIAL_VIEWPORT_PADDING * 2);
        int viewportHeight = Math.max(1, viewportFitHeight() - INITIAL_VIEWPORT_PADDING * 2);
        int contentWidth = Math.max(1, maxX - minX);
        int contentHeight = Math.max(1, maxY - minY);
        float fitZoom = Math.min(viewportWidth / (float) contentWidth, viewportHeight / (float) contentHeight);
        fitZoom = Math.clamp(fitZoom, minZoom, Math.min(maxZoom, INITIAL_VIEWPORT_MAX_ZOOM));
        float centerX = (minX + maxX) / 2.0F;
        float centerY = (minY + maxY) / 2.0F;
        float viewportCenterY = viewportFitTop() + viewportFitHeight() / 2.0F;
        targetZoomLevel = fitZoom;
        targetPanX = viewportFitLeft() + viewportFitWidth() / 2.0F - centerX;
        targetPanY = viewportCenterY - centerY;
        isZoomingToMouse = false;
    }

    private int viewportFitTop() {
        return studioMode ? 30 : 0;
    }

    protected int viewportFitLeft() {
        int left = 0;
        if (paletteSidePanel != null && paletteSidePanel.isVisible() && paletteSidePanel.isLeftAnchored()) {
            left += paletteSidePanel.getDesiredWidth() + 8;
        }
        if (studioResourcePanel != null && studioResourcePanel.isVisible() && studioResourcePanel.isLeftAnchored()) {
            left += studioResourcePanel.getDesiredWidth() + 8;
        }
        return left;
    }

    protected int viewportFitWidth() {
        int right = 0;
        if (paletteSidePanel != null && paletteSidePanel.isVisible() && !paletteSidePanel.isLeftAnchored()) {
            right += paletteSidePanel.getDesiredWidth() + 8;
        }
        if (studioResourcePanel != null && studioResourcePanel.isVisible() && !studioResourcePanel.isLeftAnchored()) {
            right += studioResourcePanel.getDesiredWidth() + 8;
        }
        return Math.max(1, width - viewportFitLeft() - right);
    }

    private int viewportFitHeight() {
        if (!studioMode) {
            return height;
        }
        return Math.max(1, studioEditorHeight() - viewportFitTop());
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
        graphHistory.clear();
        initialViewportFittedKeys.remove(initialViewportFitKey());
        refreshNodeRegistry();
    }

    public String getDesktopAppId() {
        if (studioMode) {
            return "resync-studio:" + serverId;
        }
        return "flow-editor";
    }

    public String getDesktopAppTitle() {
        if (studioMode) {
            String title = !serverTitle.isBlank() ? serverTitle : safeText(serverId);
            return title.isBlank() ? "ReSync Studio" : "ReSync Studio - " + title;
        }
        return "Flow Editor";
    }

    public String getDesktopAppIconPath() {
        return "flow.png";
    }

    @Override
    public DesktopWindowBehavior getDesktopWindowBehavior() {
        return studioMode ? DesktopWindowBehavior.SINGLETON : DesktopWindowBehavior.DEFAULT_REPLACE;
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
        scheduleInitialViewportFit();
        refreshPalette();
    }

    private void scheduleInitialViewportFit() {
        if (initialViewportFittedKeys.contains(initialViewportFitKey())) {
            return;
        }
        initialViewportFitPending = true;
        initialViewportStableFrames = 0;
        initialViewportSignature = 0;
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
            header().position(Position.TOP).size(30).visible(true);

            if (studioMode) {
                studioEmptyMessage = new IconMessage(0, 0, 180, 96, "Open Or Create An Asset", "remotely.png");
                studioEmptyMessage.entranceAnimationEnabled = false;
                ensureStartupWidgets();
                setStartupState(StudioStartupState.LOADING, "Loading...\nDetecting ReSync", "remotely.png", false);
                beginStartupProbe(true);
            } else {
                createPaletteSidePanel();
                createHeaderButtons();
            }
            initialized = true;
        }

        if (!studioMode) {
            syncDebugHeaderVisibility();
            layoutHeaderButtons();
        }
        if (studioMode) {
            if (startupState == StudioStartupState.READY) {
                updateStudioLayout();
            } else {
                updateStartupWidgets();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!studioMode || startupState == StudioStartupState.READY) {
            return;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && manager.isFlowClientConnected(serverId)) {
            enterStudioReadyState();
            return;
        }
        if ((startupState == StudioStartupState.LOADING || startupState == StudioStartupState.SERVER_STOPPED || startupState == StudioStartupState.INSTALLED) && !startupProbeRunning) {
            beginStartupProbe(false);
        }
    }

    private void ensureStartupWidgets() {
        if (startupIcon == null) {
            startupIcon = new IconMessage(0, 0, width, 120, "Loading", "remotely.png");
            startupIcon.entranceAnimationEnabled = false;
        }
        if (startupCloseButton == null) {
            startupCloseButton = new IconButton.Builder()
                .imagePath("close.png")
                .size(18, 18)
                .hint("Back")
                .entranceAnimation(false)
                .onClick(this::close)
                .build();
        }
        if (setupReSyncButton == null) {
            setupReSyncButton = new IconButton.Builder()
                .label("Setup ReSync")
                .imagePath("ReSync.png")
                .accentType(ThemeManager.getAccent("nice"))
                .size(180, 20)
                .autoWidthOnTextChange(true)
                .hint("Setup ReSync")
                .entranceAnimation(false)
                .onClick(this::runSetupFlow)
                .build();
            setupReSyncButton.setVisible(false);
        }
        if (welcomeServerButton == null) {
            welcomeServerButton = new IconButton.Builder()
                .label("Open Server")
                .imagePath("server.png")
                .accentType(ThemeManager.getAccent("nice"))
                .size(180, 20)
                .autoWidthOnTextChange(true)
                .hint("Open Server")
                .entranceAnimation(false)
                .onClick(this::openServerScreen)
                .build();
            welcomeServerButton.setVisible(false);
        }
        updateStartupWidgets();
    }

    private void updateStartupWidgets() {
        int headerHeight = 35;
        int availableHeight = height - headerHeight;
        if (startupIcon != null) {
            int iconY = headerHeight + (availableHeight - startupIcon.getHeight()) / 2;
            startupIcon.setPosition(0, iconY);
            startupIcon.setSize(width, startupIcon.getHeight());
        }
        if (startupCloseButton != null) {
            startupCloseButton.setPosition(width - 25, 8);
        }
        int buttonY = startupIcon != null ? startupIcon.getY() + startupIcon.getHeight() + 10 : Math.max(100, height / 2 + 70);
        if (setupReSyncButton != null) {
            setupReSyncButton.setPosition((width - setupReSyncButton.getWidth()) / 2, buttonY);
        }
        if (welcomeServerButton != null) {
            int offset = setupReSyncButton != null && setupReSyncButton.isVisible() ? 28 : 0;
            welcomeServerButton.setPosition((width - welcomeServerButton.getWidth()) / 2, buttonY + offset);
        }
    }

    private void setStartupState(StudioStartupState state, String message, String iconPath, boolean showSetupButton) {
        startupState = state;
        ensureStartupWidgets();
        if (startupIcon != null) {
            startupIcon.setMessage(message);
            startupIcon.setIcon(iconPath);
            startupIcon.setVisible(true);
        }
        if (setupReSyncButton != null) {
            setupReSyncButton.setVisible(showSetupButton && !setupRunning);
        }
        if (welcomeServerButton != null) {
            welcomeServerButton.setVisible(state == StudioStartupState.INSTALLED);
        }
        updateStartupWidgets();
    }

    private void beginStartupProbe(boolean force) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            setStartupState(StudioStartupState.NOT_SUPPORTED, "Not Supported\nReSync Is Missing", "stop.png", false);
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && startupProbeRunning) {
            return;
        }
        if (!force && now - lastStartupProbeAt < 800) {
            return;
        }
        startupProbeRunning = true;
        lastStartupProbeAt = now;
        manager.ensureFlowClientForStartup(serverId, startupServer, false);
        if (manager.isFlowClientConnected(serverId)) {
            startupProbeRunning = false;
            enterStudioReadyState();
            return;
        }
        if (force && startupState != StudioStartupState.INSTALLING && startupState != StudioStartupState.INSTALLED) {
            setStartupState(StudioStartupState.LOADING, "Loading...\nDetecting ReSync", "remotely.png", false);
        }
        CompletableFuture.runAsync(this::probeStartupStateAsync);
    }

    private void probeStartupStateAsync() {
        StudioStartupState targetState;
        try {
            targetState = computeStartupState();
        } catch (Exception ignored) {
            targetState = StudioStartupState.SETUP;
        }
        StudioStartupState resolvedState = targetState;
        ScreenManager.getInstance().execute(() -> {
            startupProbeRunning = false;
            FlowManager manager = FlowManager.getInstance();
            if (manager != null && manager.isFlowClientConnected(serverId)) {
                enterStudioReadyState();
                return;
            }
            if (startupState == StudioStartupState.INSTALLING) {
                return;
            }
            if (startupState == StudioStartupState.INSTALLED && resolvedState != StudioStartupState.READY && resolvedState != StudioStartupState.LOADING) {
                return;
            }
            switch (resolvedState) {
                case READY -> enterStudioReadyState();
                case NOT_SUPPORTED -> setStartupState(StudioStartupState.NOT_SUPPORTED, "ReSync Is Not On This Server\nBukkit-Based Server Required", "close.png", false);
                case SERVER_STOPPED -> setStartupState(StudioStartupState.SERVER_STOPPED, "Server Is Offline\nStart The Server To Use ReSync", "stop.png", false);
                case SETUP -> setStartupState(StudioStartupState.SETUP, "Setup ReSync\nInstall And Configure", "ReSync.png", true);
                default -> setStartupState(StudioStartupState.LOADING, "Loading...\nDetecting ReSync", "remotely.png", false);
            }
        });
    }

    private StudioStartupState computeStartupState() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return StudioStartupState.NOT_SUPPORTED;
        }
        if (manager.isFlowClientConnected(serverId)) {
            return StudioStartupState.READY;
        }
        Boolean pluginCompatible = isPluginCompatible();
        if (Boolean.FALSE.equals(pluginCompatible)) {
            return StudioStartupState.NOT_SUPPORTED;
        }
        if (startupServer != null) {
            try {
                Boolean pluginPresent = manager.isReSyncPluginInstalled(serverId).get(5, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(pluginPresent)) {
                    Instance instance = manager.findInstanceByServerId(serverId, startupServer);
                    if (instance != null && instance.getState() != InstanceState.RUNNING) {
                        return StudioStartupState.SERVER_STOPPED;
                    }
                    return StudioStartupState.LOADING;
                }
            } catch (Exception ignored) {
            }
            return StudioStartupState.SETUP;
        }
        Instance instance = manager.getInstanceByServerId(serverId);
        boolean isRunning = instance != null && instance.getState() == InstanceState.RUNNING;
        if (instance != null && isReSyncResourcePresent(instance)) {
            if (!isRunning) {
                return StudioStartupState.SERVER_STOPPED;
            }
            if (manager.getFlowAvailabilityIssue(serverId, null) != null) {
                return StudioStartupState.SETUP;
            }
            return StudioStartupState.LOADING;
        }
        if (manager.isFlowClientConnected(serverId)) {
            return StudioStartupState.READY;
        }
        return StudioStartupState.SETUP;
    }

    private Boolean isPluginCompatible() {
        FlowManager manager = FlowManager.getInstance();
        Instance instance = manager == null ? null : manager.getInstanceByServerId(serverId);
        if (instance != null) {
            String backendType = resolveBackendType(instance);
            if (!instance.isServer()) {
                if ("SSH".equalsIgnoreCase(backendType) || "RESTUDIO".equalsIgnoreCase(backendType)) {
                    return null;
                }
                return false;
            }
            if (instance.supportsPlugins()) {
                return true;
            }
            if (instance.getModLoader() != null) {
                String loaderName = instance.getModLoader().name();
                if (!"VANILLA".equalsIgnoreCase(loaderName)) {
                    return isPluginCompatibleFromLoader(loaderName);
                }
            }
            if (!loaderHint.isBlank()) {
                return isPluginCompatibleFromLoader(loaderHint);
            }
            if ("SSH".equalsIgnoreCase(backendType)) {
                return null;
            }
            return null;
        }
        if (startupServer != null) {
            if (startupServer.loader == null || startupServer.loader.isBlank()) {
                if (!loaderHint.isBlank()) {
                    return isPluginCompatibleFromLoader(loaderHint);
                }
                return null;
            }
            return isPluginCompatibleFromLoader(startupServer.loader);
        }
        if (!loaderHint.isBlank()) {
            return isPluginCompatibleFromLoader(loaderHint);
        }
        return null;
    }

    private String resolveBackendType(Instance instance) {
        if (instance == null || instance.getBackendConfig() == null || instance.getBackendConfig().type == null) {
            return "";
        }
        return instance.getBackendConfig().type.trim();
    }

    private boolean isPluginCompatibleFromLoader(String loader) {
        String normalized = safeText(loader).trim().toUpperCase(Locale.ROOT);
        return normalized.equals("PAPER")
            || normalized.equals("FOLIA")
            || normalized.equals("SPIGOT")
            || normalized.equals("BUKKIT")
            || normalized.equals("PURPUR")
            || normalized.equals("LEAF")
            || normalized.equals("VELOCITY")
            || normalized.equals("WATERFALL")
            || normalized.equals("BUNGEECORD");
    }

    private boolean isReSyncResourcePresent(Instance instance) {
        try {
            List<InstanceResource> resources = Rebase.get().getResourceManager().getResources(instance).get(15, TimeUnit.SECONDS);
            for (InstanceResource resource : resources) {
                if (resource == null) {
                    continue;
                }
                String fileName = safeText(resource.getFileName()).toLowerCase(Locale.ROOT);
                String name = safeText(resource.getName()).toLowerCase(Locale.ROOT);
                if (fileName.contains("resync") || name.contains("resync")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void enterStudioReadyState() {
        startupState = StudioStartupState.READY;
        startupIcon = null;
        startupCloseButton = null;
        setupReSyncButton = null;
        welcomeServerButton = null;
        if (!studioChromeBuilt) {
            createPaletteSidePanel();
            createHeaderButtons();
            createStudioWorkspaceChrome();
            studioChromeBuilt = true;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager != null) {
            manager.ensureFlowClientForStartup(serverId, startupServer, true);
            manager.requestInitialFlowData(serverId);
            WorldGenManager.getInstance().requestProjectList(serverId);
        }
        updateStudioLayout();
    }

    private void runSetupFlow() {
        if (setupRunning) {
            return;
        }
        setupRunning = true;
        setStartupState(StudioStartupState.INSTALLING, "Installing ReSync...", "remotely.png", false);
        CompletableFuture.runAsync(this::setupReSyncAsync);
    }

    private void setupReSyncAsync() {
        boolean success;
        boolean needsRestart = false;
        try {
            FlowManager manager = FlowManager.getInstance();
            if (manager != null && manager.isFlowClientConnected(serverId)) {
                success = true;
            } else if (isReStudioTarget()) {
                success = setupForReStudio();
                needsRestart = true;
            } else {
                success = setupForNonReStudio();
                needsRestart = true;
            }
        } catch (Exception error) {
            success = false;
            String reason = error.getMessage() == null || error.getMessage().isBlank() ? "Setup Failed" : error.getMessage();
            ScreenManager.getInstance().execute(() -> new Notification("ReSync", reason, Notification.Type.ERROR));
        }
        boolean completed = success;
        boolean shouldRestart = needsRestart;
        ScreenManager.getInstance().execute(() -> {
            setupRunning = false;
            if (completed) {
                if (shouldRestart) {
                    showInstalledState();
                } else {
                    beginStartupProbe(true);
                }
                return;
            }
            setStartupState(StudioStartupState.SETUP, "Setup ReSync\nInstall And Configure", "ReSync.png", true);
        });
    }

    private void showInstalledState() {
        FlowManager manager = FlowManager.getInstance();
        Instance instance = manager != null ? manager.findInstanceByServerId(serverId, startupServer) : null;
        boolean isRunning = instance != null && instance.getState() == InstanceState.RUNNING;
        boolean isSSH = instance != null && instance.getBackendConfig() != null && "SSH".equalsIgnoreCase(instance.getBackendConfig().type);
        StringBuilder message = new StringBuilder();
        message.append("ReSync Installed!");
        if (isRunning) {
            message.append("\nRestart Your Server To Activate");
        } else {
            message.append("\nStart Your Server To Activate");
        }
        if (isSSH) {
            message.append("\nOpen Port ").append(RESYNC_PORT).append(" On Your Host");
        }
        setStartupState(StudioStartupState.INSTALLED, message.toString(), "ReSync.png", false);
        new Notification("ReSync", isRunning ? "Installed! Restart Server To Activate" : "Installed! Start Server To Activate", Notification.Type.SUCCESS);
    }

    private void openServerScreen() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        Instance instance = manager.findInstanceByServerId(serverId, startupServer);
        if (instance == null && startupServer != null) {
            instance = buildTemporaryInstance(startupServer);
        }
        if (instance == null) {
            return;
        }
        RemotelyClient.INSTANCE.openInstanceInTerminal(this, instance);
    }

    private Instance buildTemporaryInstance(ClientServerView server) {
        Map<String, String> creds = new HashMap<>();
        creds.put("identifier", server.identifier);
        creds.put("host", server.sftpIp);
        creds.put("port", String.valueOf(server.sftpPort));
        creds.put("user", server.sftpUser);
        creds.put("password", "");
        creds.put("installing", String.valueOf(server.isInstalling));
        creds.put("suspended", String.valueOf(server.isSuspended));
        Instance instance = new Instance(server.name, "unknown", "");
        instance.setBackendConfig(new BackendConfig("RESTUDIO", creds));
        instance.setServer(true);
        if (server.loader != null) {
            try {
                instance.setModLoader(ModLoader.valueOf(server.loader));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return instance;
    }

    private boolean setupForReStudio() throws Exception {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null || serverId.isBlank()) {
            return false;
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        manager.provisionReSyncForReStudioServer(serverId, future::complete);
        Boolean result = future.get(90, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    private boolean setupForNonReStudio() throws Exception {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return false;
        }
        Instance instance = manager.getInstanceByServerId(serverId);
        if (instance == null) {
            ScreenManager.getInstance().execute(() -> new Notification("ReSync", "Server Not Found", Notification.Type.ERROR));
            return false;
        }
        ServerBackend backend = instance.getBackend();
        if (backend == null) {
            return false;
        }
        NetworkTransferFeature transfer = backend.getFeature(NetworkTransferFeature.class).orElse(null);
        if (transfer == null) {
            ScreenManager.getInstance().execute(() -> new Notification("ReSync", "Network Transfer Missing", Notification.Type.ERROR));
            return false;
        }
        FileSystemProvider fileSystem = backend.getFileSystem();
        if (fileSystem == null) {
            return false;
        }

        Path serverPath = Path.of(instance.getPath());
        Path pluginsPath = serverPath.resolve(resolvePluginsDirectory(instance));
        Path reSyncJarPath = pluginsPath.resolve("ReSync.jar");
        ensureDirectory(fileSystem, pluginsPath);
        transfer.downloadFile(RESYNC_RELEASE_URL, reSyncJarPath, null).get(90, TimeUnit.SECONDS);
        registerReSyncResource(instance, reSyncJarPath);

        Path configDir = pluginsPath.resolve("ReSync");
        ensureDirectory(fileSystem, configDir);
        String apiKey = generateApiKey();
        boolean localBackend = instance.getBackendConfig() != null && "LOCAL".equalsIgnoreCase(instance.getBackendConfig().type);
        String bindHost = localBackend ? "127.0.0.1" : "0.0.0.0";
        String publicBindEnabled = Boolean.toString(!localBackend);
        String configText = "port=" + RESYNC_PORT + "\n"
            + "api-key=" + apiKey + "\n"
            + "bind-host=" + bindHost + "\n"
            + "public-bind-enabled=" + publicBindEnabled + "\n";
        fileSystem.write(configDir.resolve("config.properties"), configText).get(30, TimeUnit.SECONDS);

        BackendConfig backendConfig = instance.getBackendConfig();
        if (backendConfig != null) {
            if (backendConfig.credentials == null) {
                backendConfig.credentials = new HashMap<>();
            }
            backendConfig.credentials.put("resyncEnabled", "true");
            instance.save();
        }
        return true;
    }

    private void registerReSyncResource(Instance instance, Path reSyncJarPath) {
        if (instance == null || reSyncJarPath == null) {
            return;
        }
        try {
            boolean remoteBackend = instance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(instance.getBackendConfig().type);
            if (remoteBackend) {
                Rebase.get().getResourceManager().invalidateCache(instance);
                Rebase.get().getResourceManager().getResources(instance).get(30, TimeUnit.SECONDS);
                return;
            }
            Rebase.get().getResourceManager().loadResource(instance, reSyncJarPath).get(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            Rebase.get().getResourceManager().invalidateCache(instance);
        }
    }

    private String generateApiKey() {
        byte[] key = new byte[32];
        secureRandom.nextBytes(key);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
    }

    private String resolvePluginsDirectory(Instance instance) {
        if (instance == null) {
            return "plugins";
        }
        if (instance.supportsPlugins() || instance.shouldInstallModsAsPlugins()) {
            return "plugins";
        }
        return "plugins";
    }

    private void ensureDirectory(FileSystemProvider fileSystem, Path path) throws Exception {
        Boolean exists = fileSystem.exists(path).get(20, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        fileSystem.createDirectory(path).get(30, TimeUnit.SECONDS);
    }

    private boolean isReStudioTarget() {
        if (startupServer != null) {
            return true;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return false;
        }
        Instance instance = manager.getInstanceByServerId(serverId);
        if (instance == null || instance.getBackendConfig() == null) {
            return false;
        }
        return "RESTUDIO".equalsIgnoreCase(instance.getBackendConfig().type);
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
        paletteStudioPanel = rightStudioPanel("palettePanel")
            .show();
        paletteSidePanel = paletteStudioPanel.sidePanel();
        paletteStudioPanel.padding(studioPanelState.padding());

        categoryPopups.clear();
        categoryOrder = resolveCategoryOrder();
        for (NodeDefinition.NodeCategory category : categoryOrder) {
            PopupWidget popup = new PopupWidget.Builder(getCategoryLabel(category)).enableCollapseOnClose(true).build();
            ReSyncStudioPanelState.disableEntrance(popup);
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
                    if (studioDocuments.isEmpty()) {
                        clearActiveStudioDocument();
                    } else if (activeStudioDocument != null && activeStudioDocument.key().equals(key)) {
                        selectStudioDocument(studioDocuments.getFirst().key());
                    } else {
                        refreshActiveViewHeaderButtons();
                        refreshStudioResourcePanel();
                    }
                }
            })
            .onTabSelected(tab -> {
                if (syncingStudioTabSelection) {
                    return;
                }
                Object data = tab.getData();
                if (data instanceof String key) {
                    selectStudioDocument(key);
                }
            })
            .build();
        studioContentBrowserHeight = studioContentBrowserDefaultHeight();
        studioContentBrowser = new ReSyncContentBrowserWidget(8, height - studioContentBrowserHeight() - studioContentBrowserBottomMargin(), width - 16, studioContentBrowserHeight());
        addHudWidget(studioContentBrowser);
        studioResourceStudioPanel = rightStudioPanel("studioResourcePanel")
            .show();
        studioResourcePanel = studioResourceStudioPanel.sidePanel();
        studioResourceStudioPanel.padding(studioPanelState.padding());
        studioResourcePanel.hide();
        syncStudioDocumentTabs();
        clearActiveStudioDocument();
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
            } else {
                manager.openFlowEditor(serverId, null, resource.getId());
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
        ReSyncResourceType jsonType = ReSyncResourceType.byTypeId(resource.getType());
        if (jsonType != null) {
            JsonObject json = manager.getJsonResourcesForServer(serverId, jsonType).get(resource.getId());
            if (json == null) {
                manager.ensureFlowClient(serverId).requestResource(jsonType, resource.getId(), false);
                json = manager.createJsonResource(serverId, jsonType, resource.getId(), resource.getPath());
            }
            if (ReSyncResourceDragPayload.ADVANCEMENT_TREE.equals(resource.getType()) || ReSyncResourceDragPayload.DIALOG.equals(resource.getType())) {
                openStudioDesigner(resource.getType(), resource.getId());
            } else {
                openJsonResourceDocument(resource.getType(), resource.getId(), resource.getDisplayName(), json);
            }
            return;
        }
        if (ReSyncResourceDragPayload.WORLD.equals(resource.getType())) {
            openStudioWorldDocument(resource.getId(), resource.getDisplayName());
        }
    }

    private void openJsonResourceDocument(String type, String id, String title, JsonObject resource) {
        openStudioViewDocument(type, id, title == null || title.isBlank() ? id : title, new JsonResourceStudioView(type, id, resource));
    }

    private void openStudioWorldDocument(String id, String title) {
        openStudioViewDocument(ReSyncResourceDragPayload.WORLD, id, title == null || title.isBlank() ? id : title, new WorldStudioView(id));
    }

    private List<ReSyncProjectMetadata.FolderEntry> studioFolders(String parentPath) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return List.of();
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        return metadata.getFolders().stream()
            .filter(this::isVisibleStudioFolder)
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
        return metadata.getFolders().stream()
            .filter(this::isVisibleStudioFolder)
            .toList();
    }

    private List<ReSyncProjectMetadata.ResourceEntry> studioResources(String folderPath) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return List.of();
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        return metadata.getResources().stream()
            .filter(this::isVisibleStudioResource)
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
        return metadata.getResources().stream()
            .filter(this::isVisibleStudioResource)
            .toList();
    }

    private boolean isVisibleStudioResource(ReSyncProjectMetadata.ResourceEntry resource) {
        return resource != null;
    }

    private boolean isVisibleStudioFolder(ReSyncProjectMetadata.FolderEntry folder) {
        return folder != null;
    }

    private void syncStudioDocumentTabs() {
        if (studioTabsManager == null) {
            return;
        }
        Set<String> documentKeys = new HashSet<>();
        for (StudioDocument document : studioDocuments) {
            documentKeys.add(document.key());
        }
        List<TabsManager.Tab> tabs = studioTabsManager.getTabs();
        for (int i = tabs.size() - 1; i >= 0; i--) {
            Object data = tabs.get(i).getData();
            if (!(data instanceof String key) || !documentKeys.contains(key)) {
                studioTabsManager.removeTabRaw(i);
            }
        }
        tabs = studioTabsManager.getTabs();
        for (StudioDocument document : studioDocuments) {
            TabsManager.Tab tab = findStudioTab(document.key(), tabs);
            if (tab == null) {
                Container container = new Container("document-" + document.key(), 0, 0, 1, 1);
                tab = studioTabsManager.addTab(document.title(), container, studioResourceIconPath(document.type(), document.id()));
                tab.setData(document.key());
                tabs.add(tab);
            } else {
                tab.setName(document.title());
                tab.setIconPath(studioResourceIconPath(document.type(), document.id()));
            }
        }
        if (activeStudioDocument != null) {
            TabsManager.Tab activeTab = findStudioTab(activeStudioDocument.key(), studioTabsManager.getTabs());
            if (activeTab != null && studioTabsManager.getActiveTab() != activeTab) {
                setStudioTabsManagerActiveTab(activeTab);
            } else if (activeTab == null) {
                clearActiveStudioDocument();
            }
        }
        studioTabsManager.updateLayout();
    }

    private TabsManager.Tab findStudioTab(String key, List<TabsManager.Tab> tabs) {
        for (TabsManager.Tab tab : tabs) {
            if (key.equals(tab.getData())) {
                return tab;
            }
        }
        return null;
    }

    private void setStudioTabsManagerActiveTab(TabsManager.Tab tab) {
        if (studioTabsManager == null || tab == null || studioTabsManager.getActiveTab() == tab) {
            return;
        }
        syncingStudioTabSelection = true;
        try {
            studioTabsManager.setActiveTab(tab.getContainer());
        } finally {
            syncingStudioTabSelection = false;
        }
    }

    private String studioResourceIconPath(String type, String id) {
        return switch (type) {
            case ReSyncResourceDragPayload.FUNCTION -> "snippets.png";
            case ReSyncResourceDragPayload.COMMAND -> "terminal.png";
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> customContentIconPath(id);
            case ReSyncResourceDragPayload.GUI -> "fullPanel.png";
            case ReSyncResourceDragPayload.SCOREBOARD -> "panel.png";
            case ReSyncResourceDragPayload.TAB -> "topPanel.png";
            case ReSyncResourceDragPayload.CHAT_CHANNEL, ReSyncResourceDragPayload.CHAT_FORMAT, ReSyncResourceDragPayload.CHAT_RULE,
                 ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT, ReSyncResourceDragPayload.MENTION_STYLE, ReSyncResourceDragPayload.IGNORE_LIST -> "chat.png";
            case ReSyncResourceDragPayload.MOTD_PROFILE -> "hi.png";
            case ReSyncResourceDragPayload.MESSAGE_RULE -> "edit.png";
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> "crafting.png";
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> "text.png";
            case ReSyncResourceDragPayload.ADVANCEMENT_TREE -> "advancement.png";
            case ReSyncResourceDragPayload.DIALOG -> "chat.png";
            case ReSyncResourceDragPayload.WORLDGEN -> "map.png";
            case ReSyncResourceDragPayload.WORLD -> "earth.png";
            default -> "graph.png";
        };
    }

    private String customContentIconPath(String id) {
        FlowManager manager = FlowManager.getInstance();
        CustomContentDefinition content = manager != null ? manager.getCustomContentForServer(serverId).get(id) : null;
        return switch (content != null ? safeText(content.getType()).toLowerCase(Locale.ROOT) : "") {
            case "armor" -> "armor.png";
            case "block" -> "block.png";
            case "item" -> "item.png";
            default -> "item.png";
        };
    }

    private void refreshStudioResourcePanel() {
        if (studioResourcePanel == null) {
            return;
        }
        commandLabelInput = null;
        commandPathInputs.clear();
        commandStructuredToggle = null;
        if (activeStudioDocument == null) {
            clearStudioResourcePanelWidgets();
            studioResourcePanel.hide();
            return;
        }
        if (ReSyncResourceDragPayload.FLOW.equals(activeStudioDocument.type())
            || ReSyncResourceDragPayload.FUNCTION.equals(activeStudioDocument.type())
            || ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(activeStudioDocument.type())) {
            clearStudioResourcePanelWidgets();
            studioResourcePanel.hide();
            return;
        }
        ReSyncStudioView view = activeStudioView();
        if (view != null && view.hasPanel()) {
            if (view.preferredPanelPlacement() == StudioPanel.Placement.LEFT) {
                studioResourcePanel.left();
            } else {
                studioResourcePanel.right();
            }
        } else if (ReSyncResourceDragPayload.COMMAND.equals(activeStudioDocument.type())) {
            studioResourcePanel.left();
        } else {
            studioResourcePanel.right();
        }
        studioResourcePanel.show();
        if (view != null && view.hasPanel()) {
            view.configurePanel(studioResourceStudioPanel);
        } else if (ReSyncResourceDragPayload.GUI.equals(activeStudioDocument.type())) {
            buildGuiResourcePanel();
        } else if (ReSyncResourceDragPayload.COMMAND.equals(activeStudioDocument.type())) {
            buildCommandResourcePanel();
        } else if (ReSyncResourceDragPayload.SCOREBOARD.equals(activeStudioDocument.type())) {
            buildScoreboardResourcePanel();
        } else if (ReSyncResourceDragPayload.TAB.equals(activeStudioDocument.type())) {
            buildTabResourcePanel();
        } else if (ReSyncResourceDragPayload.DIALOG.equals(activeStudioDocument.type())) {
            buildDialogResourcePanel();
        } else if (ReSyncResourceDragPayload.WORLDGEN.equals(activeStudioDocument.type())) {
            buildWorldGenResourcePanel();
        } else if (ReSyncResourceDragPayload.WORLD.equals(activeStudioDocument.type())) {
            clearStudioResourcePanelWidgets();
            studioResourcePanel.hide();
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
        buildCommandResourcePanel(command);
    }

    private void buildCommandResourcePanel(CommandBindingContext command) {
        String panelKey = activeStudioDocument.key();
        commandPathInputs.clear();
        if (reuseStudioResourcePanel(panelKey)) {
            clearStudioResourcePanelWidgets();
        }
        setStudioResourcePanelKey(panelKey);
        commandLabelInput = panelInput("Command", command.command != null && !command.command.isBlank() ? command.command : activeStudioDocument.id());
        commandStructuredToggle = new ToggleWidget.Builder()
            .label("Structured")
            .toggled(command.structured != null && command.structured)
            .size(studioPanelState.rowWidth(studioResourcePanel), 18)
            .entranceAnimation(false)
            .build();
        rememberStudioPanelInput("command", commandLabelInput);
        rememberStudioPanelToggle("structured", commandStructuredToggle);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        List<AnimatedWidget> widgets = new ArrayList<>();
        widgets.add(commandSummaryRow(rowWidth));
        widgets.add(studioPanelState.row("Command", commandLabelInput, rowWidth, studioResourceDescription("Command")));
        widgets.add(studioPanelState.row("Structured", commandStructuredToggle, rowWidth, studioResourceDescription("Structured")));
        List<String> paths = command.subcommands != null ? new ArrayList<>(command.subcommands) : new ArrayList<>();
        if (paths.isEmpty()) {
            paths.add("");
        }
        for (int i = 0; i < paths.size(); i++) {
            widgets.add(commandPathRow(paths, i, rowWidth));
        }
        widgets.add(new AnimatedButton.Builder()
            .label("Add Path")
            .size(rowWidth, 18)
            .entranceAnimation(false)
            .onClick(() -> {
                CommandBindingContext draft = currentCommandDraft();
                draft.subcommands.add("");
                rebuildCommandResourcePanel(draft);
            })
            .build());
        setStudioResourcePanelWidgets(widgets.toArray(new AnimatedWidget[0]));
    }

    private MountableButtonWidget commandSummaryRow(int rowWidth) {
        String command = commandLabelInput != null && commandLabelInput.getText() != null && !commandLabelInput.getText().isBlank()
            ? "/" + normalizeCommandLabel(commandLabelInput.getText())
            : "/" + activeStudioDocument.id();
        MountableButtonWidget row = new MountableButtonWidget.Builder("Command")
            .description(command)
            .iconPath("terminal.png")
            .build();
        row.setSize(rowWidth, 30);
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    private TitledRowWidget commandPathRow(List<String> paths, int index, int rowWidth) {
        String value = paths.get(index);
        String[] pathExamples = {
            "pvp duel <online_player>",
            "report hacker <offline_player>",
            "database getPlayers <player_with_perm:my.permission.node>",
            "trade <online_player>"
        };
        int buttonCount = 1;
        if (index > 0) {
            buttonCount++;
        }
        if (index < paths.size() - 1) {
            buttonCount++;
        }
        int inputWidth = Math.max(120, rowWidth - buttonCount * 18 - 8);
        TextInputWidget pathInput = new TextInputWidget.Builder()
            .text(value)
            .placeholder(pathExamples[index % pathExamples.length])
            .forcePlaceholder(false)
            .size(inputWidth, 18)
            .build();
        ReSyncStudioPanelState.disableEntrance(pathInput);
        commandPathInputs.add(pathInput);
        RowWidget.Builder rowBuilder = new RowWidget.Builder()
            .size(rowWidth, 18)
            .padding(2)
            .addWidget(pathInput);
        if (index > 0) {
            rowBuilder.addWidget(commandPathButton("goforward.png", -90, () -> moveCommandPath(index, -1)));
        }
        if (index < paths.size() - 1) {
            rowBuilder.addWidget(commandPathButton("goforward.png", 90, () -> moveCommandPath(index, 1)));
        }
        rowBuilder.addWidget(new SquareButtonWidget.Builder()
            .imagePath("delete.png")
            .size(18, 18)
            .hint("Remove Path")
            .accentType(ThemeManager.getAccent("danger"))
            .entranceAnimation(false)
            .onClick(() -> removeCommandPath(index))
            .build());
        RowWidget row = rowBuilder.build();
        ReSyncStudioPanelState.disableEntrance(row);
        return studioPanelState.row("Path " + (index + 1), row, rowWidth, studioResourceDescription("Path"));
    }

    private SquareButtonWidget commandPathButton(String imagePath, int rotation, Runnable action) {
        return new SquareButtonWidget.Builder()
            .imagePath(imagePath)
            .size(18, 18)
            .rotate(rotation)
            .hint(rotation < 0 ? "Move Up" : "Move Down")
            .entranceAnimation(false)
            .onClick(action)
            .build();
    }

    private void moveCommandPath(int index, int direction) {
        CommandBindingContext draft = currentCommandDraft();
        int target = index + direction;
        if (index >= 0 && target >= 0 && index < draft.subcommands.size() && target < draft.subcommands.size()) {
            String value = draft.subcommands.get(index);
            draft.subcommands.set(index, draft.subcommands.get(target));
            draft.subcommands.set(target, value);
        }
        rebuildCommandResourcePanel(draft);
    }

    private void removeCommandPath(int index) {
        CommandBindingContext draft = currentCommandDraft();
        if (index >= 0 && index < draft.subcommands.size()) {
            draft.subcommands.remove(index);
        }
        if (draft.subcommands.isEmpty()) {
            draft.subcommands.add("");
        }
        rebuildCommandResourcePanel(draft);
    }

    private void rebuildCommandResourcePanel(CommandBindingContext draft) {
        studioResourcePanelKey = "";
        buildCommandResourcePanel(draft);
        studioResourcePanel.container().updateWidgetPositions();
    }

    private void buildGuiResourcePanel() {
        FlowManager manager = FlowManager.getInstance();
        GuiDefinition gui = manager != null ? manager.getGuisForServer(serverId).get(activeStudioDocument.id()) : null;
        if (gui == null) {
            return;
        }
        String panelKey = activeStudioDocument.key();
        if (reuseStudioResourcePanel(panelKey)) {
            updateStudioPanelInput("title", gui.getTitle());
            updateStudioPanelInput("rows", String.valueOf(gui.getRows()));
            updateStudioPanelToggle("inventory", gui.isExtendToPlayerInventory());
            return;
        }
        setStudioResourcePanelKey(panelKey);
        TextInputWidget title = panelInput("Title", gui.getTitle());
        TextInputWidget rows = panelInput("Rows", String.valueOf(gui.getRows()));
        ToggleWidget playerInventory = new ToggleWidget.Builder()
            .label("Inventory")
            .toggled(gui.isExtendToPlayerInventory())
            .size(studioPanelState.rowWidth(studioResourcePanel), 18)
            .entranceAnimation(false)
            .build();
        rememberStudioPanelInput("title", title);
        rememberStudioPanelInput("rows", rows);
        rememberStudioPanelToggle("inventory", playerInventory);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        setStudioResourcePanelWidgets(studioPanelState.row("Title", title, rowWidth, studioResourceDescription("Gui Title")),
            studioPanelState.row("Rows", rows, rowWidth, studioResourceDescription("Gui Rows")),
            studioPanelState.row("Inventory", playerInventory, rowWidth, studioResourceDescription("Gui Inventory")), panelSaveButton(() -> {
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
        String panelKey = activeStudioDocument.key();
        if (reuseStudioResourcePanel(panelKey)) {
            updateStudioPanelInput("title", scoreboard.getTitle());
            updateStudioPanelInput("objective", scoreboard.getObjectiveId());
            updateStudioPanelInput("lines", String.join("|", scoreboard.getLines()));
            return;
        }
        setStudioResourcePanelKey(panelKey);
        TextInputWidget title = panelInput("Title", scoreboard.getTitle());
        TextInputWidget objective = panelInput("Objective", scoreboard.getObjectiveId());
        TextInputWidget lines = panelInput("Lines", String.join("|", scoreboard.getLines()));
        rememberStudioPanelInput("title", title);
        rememberStudioPanelInput("objective", objective);
        rememberStudioPanelInput("lines", lines);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        setStudioResourcePanelWidgets(studioPanelState.row("Title", title, rowWidth, studioResourceDescription("Scoreboard Title")),
            studioPanelState.row("Objective", objective, rowWidth, studioResourceDescription("Objective")),
            studioPanelState.row("Lines", lines, rowWidth, studioResourceDescription("Scoreboard Lines")), panelSaveButton(() -> {
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
        String panelKey = activeStudioDocument.key();
        if (reuseStudioResourcePanel(panelKey)) {
            updateStudioPanelInput("header", tab.getHeader());
            updateStudioPanelInput("entry", tab.getEntryFormat());
            updateStudioPanelInput("footer", tab.getFooter());
            return;
        }
        setStudioResourcePanelKey(panelKey);
        TextInputWidget header = panelInput("Header", tab.getHeader());
        TextInputWidget entry = panelInput("Entry", tab.getEntryFormat());
        TextInputWidget footer = panelInput("Footer", tab.getFooter());
        rememberStudioPanelInput("header", header);
        rememberStudioPanelInput("entry", entry);
        rememberStudioPanelInput("footer", footer);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        setStudioResourcePanelWidgets(studioPanelState.row("Header", header, rowWidth, studioResourceDescription("Tab Header")),
            studioPanelState.row("Entry", entry, rowWidth, studioResourceDescription("Tab Entry")),
            studioPanelState.row("Footer", footer, rowWidth, studioResourceDescription("Tab Footer")), panelSaveButton(() -> {
            tab.setHeader(header.getText());
            tab.setEntryFormat(entry.getText());
            tab.setFooter(footer.getText());
            manager.saveTab(serverId, tab);
        }));
    }

    private void buildDialogResourcePanel() {
        FlowManager manager = FlowManager.getInstance();
        JsonObject dialog = manager != null ? manager.getJsonResourcesForServer(serverId, ReSyncResourceType.DIALOG).get(activeStudioDocument.id()) : null;
        if (dialog == null) {
            return;
        }
        String panelKey = activeStudioDocument.key();
        if (reuseStudioResourcePanel(panelKey)) {
            updateStudioPanelInput("name", jsonText(dialog, "displayName"));
            updateStudioPanelInput("title", jsonText(dialog, "title"));
            updateStudioPanelInput("type", jsonText(dialog, "type"));
            updateStudioPanelToggle("enabled", jsonBool(dialog, "enabled", true));
            return;
        }
        setStudioResourcePanelKey(panelKey);
        TextInputWidget name = panelInput("Name", jsonText(dialog, "displayName"));
        TextInputWidget title = panelInput("Title", jsonText(dialog, "title"));
        TextInputWidget type = panelInput("Type", jsonText(dialog, "type"));
        ToggleWidget enabled = new ToggleWidget.Builder()
            .label("Enabled")
            .toggled(jsonBool(dialog, "enabled", true))
            .size(studioPanelState.rowWidth(studioResourcePanel), 18)
            .entranceAnimation(false)
            .build();
        rememberStudioPanelInput("name", name);
        rememberStudioPanelInput("title", title);
        rememberStudioPanelInput("type", type);
        rememberStudioPanelToggle("enabled", enabled);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        setStudioResourcePanelWidgets(studioPanelState.row("Name", name, rowWidth, studioResourceDescription("Dialog Name")),
            studioPanelState.row("Title", title, rowWidth, studioResourceDescription("Dialog Title")),
            studioPanelState.row("Type", type, rowWidth, studioResourceDescription("Dialog Type")),
            studioPanelState.row("Enabled", enabled, rowWidth, studioResourceDescription("Dialog Enabled")), panelSaveButton(() -> {
            dialog.addProperty("displayName", name.getText());
            dialog.addProperty("title", title.getText());
            dialog.addProperty("type", type.getText());
            dialog.addProperty("enabled", enabled.getValue());
            dialog.remove("pause");
            dialog.remove("external_title");
            manager.saveJsonResource(serverId, ReSyncResourceType.DIALOG, dialog);
        }));
    }

    private void buildWorldGenResourcePanel() {
        String panelKey = activeStudioDocument.key();
        if (reuseStudioResourcePanel(panelKey)) {
            return;
        }
        setStudioResourcePanelKey(panelKey);
        setStudioResourcePanelWidgets(panelSaveButton(() -> {
            WorldGenProject project = WorldGenManager.getInstance().createProjectTemplate("Continental", activeStudioDocument.id());
            WorldGenManager.getInstance().saveWorldGen(serverId, project);
        }));
    }

    private void clearStudioResourcePanelWidgets() {
        if (studioResourcePanel == null || studioResourcePanelWidgets.isEmpty()) {
            studioResourcePanelKey = "";
            studioResourcePanelInputs.clear();
            studioResourcePanelToggles.clear();
            return;
        }
        Container container = studioResourcePanel.container();
        for (AnimatedWidget widget : new ArrayList<>(studioResourcePanelWidgets)) {
            container.removeWidget(widget);
        }
        studioResourcePanelWidgets.clear();
        studioResourcePanelKey = "";
        studioResourcePanelInputs.clear();
        studioResourcePanelToggles.clear();
    }

    private void setStudioResourcePanelWidgets(AnimatedWidget... widgets) {
        if (studioResourcePanel == null) {
            return;
        }
        Container container = studioResourcePanel.container();
        for (AnimatedWidget widget : widgets) {
            ReSyncStudioPanelState.disableEntrance(widget);
            container.addWidget(widget);
            studioResourcePanelWidgets.add(widget);
        }
    }

    private boolean reuseStudioResourcePanel(String key) {
        return key != null && key.equals(studioResourcePanelKey);
    }

    private void setStudioResourcePanelKey(String key) {
        if (!Objects.equals(studioResourcePanelKey, key)) {
            clearStudioResourcePanelWidgets();
            studioResourcePanelKey = key;
        }
    }

    private void rememberStudioPanelInput(String key, TextInputWidget input) {
        studioResourcePanelInputs.put(key, input);
    }

    private void rememberStudioPanelToggle(String key, ToggleWidget toggle) {
        studioResourcePanelToggles.put(key, toggle);
    }

    private void updateStudioPanelInput(String key, String value) {
        TextInputWidget input = studioResourcePanelInputs.get(key);
        if (input != null && !input.isFocused() && !Objects.equals(input.getText(), safeText(value))) {
            input.setText(safeText(value));
        }
    }

    private void updateStudioPanelToggle(String key, boolean value) {
        ToggleWidget toggle = studioResourcePanelToggles.get(key);
        if (toggle != null && toggle.getValue() != value) {
            toggle.setValue(value);
        }
    }

    private TextInputWidget panelInput(String placeholder, String value) {
        TextInputWidget input = new TextInputWidget.Builder()
            .placeholder(placeholder)
            .forcePlaceholder(false)
            .text(value != null ? value : "")
            .size(studioPanelState.rowWidth(studioResourcePanel), ReSyncStudioPanelState.FIELD_HEIGHT)
            .build();
        ReSyncStudioPanelState.disableEntrance(input);
        return input;
    }

    private AnimatedButton panelSaveButton(Runnable action) {
        return studioPanelState.action("Save", studioPanelState.rowWidth(studioResourcePanel), action);
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

    private List<String> collectCommandPathDraft() {
        List<String> paths = new ArrayList<>();
        for (TextInputWidget input : commandPathInputs) {
            paths.add(input != null && input.getText() != null ? input.getText().trim() : "");
        }
        return paths;
    }

    private List<String> collectCommandPaths() {
        List<String> paths = new ArrayList<>();
        for (String path : collectCommandPathDraft()) {
            if (!path.isBlank()) {
                paths.add(path);
            }
        }
        return paths;
    }

    private CommandBindingContext currentCommandDraft() {
        CommandBindingContext draft = new CommandBindingContext();
        draft.command = commandLabelInput != null ? commandLabelInput.getText() : activeStudioDocument.id();
        draft.subcommands = collectCommandPathDraft();
        draft.structured = commandStructuredToggle != null && commandStructuredToggle.getValue();
        return draft;
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
                saveActiveStudioViewport();
                activeStudioDocument = document;
                setStudioTabsManagerActiveTab(findStudioTab(document.key(), studioTabsManager != null ? studioTabsManager.getTabs() : List.of()));
                restoreStudioViewport(document.viewport());
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
                graphHistory.clear();
                refreshNodeRegistry();
                if (paletteSidePanel != null) {
                    if (document.view() != null || document.graph() == null || ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(document.type())) {
                        paletteSidePanel.hide();
                    } else {
                        paletteSidePanel.show();
                    }
                }
                if (studioResourcePanel != null) {
                    if (document.view() != null && !document.view().hasPanel()) {
                        studioResourcePanel.hide();
                    } else {
                        studioResourcePanel.show();
                    }
                }
                if (document.view() == null || document.view().hasPanel()) {
                    refreshStudioResourcePanel();
                }
                updatePositions();
                return;
            }
        }
        clearActiveStudioDocument();
    }

    private void saveActiveStudioViewport() {
        if (!studioMode || activeStudioDocument == null || activeStudioDocument.viewport() == null) {
            return;
        }
        StudioViewportState viewport = activeStudioDocument.viewport();
        viewport.zoomLevel = zoomLevel;
        viewport.targetZoomLevel = targetZoomLevel;
        viewport.panX = panX;
        viewport.panY = panY;
        viewport.targetPanX = targetPanX;
        viewport.targetPanY = targetPanY;
    }

    private void restoreStudioViewport(StudioViewportState viewport) {
        if (viewport == null) {
            return;
        }
        zoomLevel = viewport.zoomLevel;
        targetZoomLevel = viewport.targetZoomLevel;
        panX = viewport.panX;
        panY = viewport.panY;
        targetPanX = viewport.targetPanX;
        targetPanY = viewport.targetPanY;
        isZoomingToMouse = false;
    }

    private void clearActiveStudioDocument() {
        saveActiveStudioViewport();
        activeStudioDocument = null;
        graph = studioEmptyGraph;
        selectedNodeIds.clear();
        selectionBase.clear();
        selectedDragStartPositions.clear();
        focusedNode = null;
        dragState.isDragging = false;
        pendingSourceNodeId = null;
        pendingSourcePin = null;
        graphHistory.clear();
        activeNodeRegistryServerId = serverId;
        if (paletteSidePanel != null) {
            paletteSidePanel.hide();
        }
        if (studioResourcePanel != null) {
            clearStudioResourcePanelWidgets();
            studioResourcePanel.hide();
        }
        refreshActiveViewHeaderButtons();
        updatePositions();
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
                        .entranceAnimation(false)
                        .onClick(() -> addNodeAtCenter(def.getId()))
                        .build();
                ReSyncStudioPanelState.disableEntrance(btn);
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
        if (!studioMode) {
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
        if (shouldShowBackButton()) {
            SquareButtonWidget backButton = headerButton("close.png", "Back", () -> {
                if (parent != null) {
                    client.setScreen(parent);
                } else {
                    close();
                }
            });
            addHeaderButton(backButton);
        }

        addHeaderButton(headerButton("save.png", "Save", this::onSave));

        addHeaderButton(headerButton("layout.png", "Layout", this::organizeGraph));

        debugToggleButton = headerButton("report.png", "Debug", this::toggleDebugMode);
        addHeaderButton(debugToggleButton);

        debugResumeButton = debugHeaderButton("Continue", "start.png", () -> {
            FlowDebugController debug = debugController();
            if (debug != null) {
                FlowDebugController.DebugSession session = debug.getActiveSession();
                debug.resume(serverId, session != null ? session.sessionId() : "");
            }
        });
        debugStepButton = debugHeaderButton("Step", "goforward.png", this::stepDebug);
        debugStopButton = debugHeaderButton("Stop", "stop.png", () -> {
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
            addHeaderButton(headerButton("copy.png", "Extract Function", this::showExtractFunctionPopup));
        }
        addCustomHeaderButtons();
        syncDebugHeaderVisibility();
    }

    protected boolean shouldShowBackButton() {
        return !desktopMode || shouldForceSuperScreen();
    }

    private SquareButtonWidget debugHeaderButton(String label, String icon, Runnable action) {
        return headerButton(icon, label, action);
    }

    protected SquareButtonWidget headerButton(String icon, String hint, Runnable action) {
        return new SquareButtonWidget.Builder()
                .size(18, 18)
                .identifier(Identifier.icon(icon))
                .hint(hint)
                .onClick(action)
                .entranceAnimation(false)
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
        notifyStudioHeaderButtonsChanged();
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

    private void stepDebug() {
        FlowDebugController debug = debugController();
        if (debug == null) {
            return;
        }
        FlowDebugController.DebugSession session = debug.getActiveSession();
        String sessionId = session != null ? session.sessionId() : "";
        debug.stepInto(serverId, sessionId);
    }

    protected void addHeaderButton(AnimatedWidget button) {
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
        List<AnimatedWidget> source = headerButtons;
        if (ownerScreen instanceof FlowGraphDesignerScreen && !headerButtons.isEmpty() && "Back".equals(safeText(headerButtons.getFirst().hint))) {
            source = headerButtons.subList(1, headerButtons.size());
        }
        return source.stream()
            .filter(button -> button != null && shouldExposeStudioHeaderButton(button))
            .map(button -> (AnimatedWidget) button)
            .toList();
    }

    private boolean shouldExposeStudioHeaderButton(AnimatedWidget button) {
        if (button == debugResumeButton || button == debugStepButton || button == debugStopButton) {
            return debugMode;
        }
        return true;
    }

    protected boolean showExtractButton() {
        return true;
    }

    protected void addCustomHeaderButtons() {
    }

    private void openReSyncMarketplace() {
        ScreenManager.getInstance().setScreen(new ReSyncMarketplaceScreen(this, serverId));
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
                    builder.input(functionParameterPin(param, NodeDefinition.PinDirection.INPUT));
                }
            }
        }
        if (functionGraph.getFunctionOutputs() != null) {
            for (FlowGraph.FunctionParameter param : functionGraph.getFunctionOutputs()) {
                if (param != null && param.getName() != null && !param.getName().isBlank()) {
                    builder.output(functionParameterPin(param, NodeDefinition.PinDirection.OUTPUT));
                }
            }
        }
        builder.priority(220).color(NodeDefinition.NodeCategory.FUNCTION);
        return builder.build();
    }

    private NodeDefinition.PinDefinition functionParameterPin(FlowGraph.FunctionParameter parameter, NodeDefinition.PinDirection direction) {
        NodeDefinition.PinBuilder builder = new NodeDefinition.PinBuilder(parameter.getName(), NodeDefinition.PinType.DATA, direction, parameter.getType() != null ? parameter.getType() : FlowDataType.ANY);
        NodeDefinition.WidgetType widget = functionParameterWidget(parameter);
        if (widget != null) {
            builder.widget(widget);
        }
        if (parameter.getOptionsSource() != null && !parameter.getOptionsSource().isBlank()) {
            builder.optionsSource(parameter.getOptionsSource());
        }
        if (parameter.getDefaultValue() != null && !parameter.getDefaultValue().isBlank()) {
            builder.defaultValue(parameter.getDefaultValue());
        }
        return builder.build();
    }

    private NodeDefinition.WidgetType functionParameterWidget(FlowGraph.FunctionParameter parameter) {
        String widget = parameter.getWidget();
        if (widget != null && !widget.isBlank()) {
            try {
                return NodeDefinition.WidgetType.valueOf(widget.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return parameter.getOptionsSource() != null && !parameter.getOptionsSource().isBlank() ? NodeDefinition.WidgetType.SEARCHABLE_LIST : null;
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

        for (AnimatedWidget button : headerButtons) {
            if (button.visible) {
                totalWidth += button.getWidth();
            }
        }
        long visibleButtons = headerButtons.stream().filter(button -> button.visible).count();
        totalWidth += Math.max(0, (int) visibleButtons - 1) * padding;

        int startY = 16;
        int currentX = width - 10;
        int headerHeight = 18;
        if (headerBackground != null) {
            headerBackground.setWidth(totalWidth + (padding * 2));
            headerBackground.setHeight(30);
            headerBackground.setPosition(width - headerBackground.getWidth() - 10, 10);
            startY = headerBackground.getY();
            currentX = headerBackground.getX() + headerBackground.getWidth() - padding;
            headerHeight = headerBackground.getHeight();
        }
        for (AnimatedWidget button : headerButtons) {
            if (!button.visible) {
                continue;
            }
            currentX -= button.getWidth();
            button.setPosition(currentX, startY + (headerHeight - button.getHeight()) / 2);
            currentX -= padding;
        }
    }

    private void notifyStudioHeaderButtonsChanged() {
        if (ownerScreen instanceof FlowGraphDesignerScreen studioParent) {
            studioParent.refreshActiveViewHeaderButtons();
        }
        if (studioMode) {
            refreshActiveViewHeaderButtons();
        }
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        if (paletteStudioPanel != null && paletteSidePanel != null && paletteSidePanel.isVisible()) {
            paletteStudioPanel.layout();
        }
        if (studioResourceStudioPanel != null && studioResourcePanel != null && studioResourcePanel.isVisible()) {
            studioResourceStudioPanel.layout();
        }
        if (!studioMode) {
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
        clampStudioContentBrowserHeight();
        layoutStudioContentBrowser();
        if (paletteStudioPanel != null && paletteSidePanel != null && paletteSidePanel.isVisible()) {
            paletteStudioPanel.layout();
        }
        if (studioResourcePanel != null) {
            int previousRowWidth = studioResourceStudioPanel != null ? studioResourceStudioPanel.rowWidth() : studioPanelState.rowWidth(studioResourcePanel);
            CommandBindingContext commandDraft = activeStudioDocument != null
                && ReSyncResourceDragPayload.COMMAND.equals(activeStudioDocument.type())
                && !commandPathInputs.isEmpty()
                ? currentCommandDraft()
                : null;
            if (studioResourceStudioPanel != null) {
                studioResourceStudioPanel.layout();
            }
            int currentRowWidth = studioResourceStudioPanel != null ? studioResourceStudioPanel.rowWidth() : studioPanelState.rowWidth(studioResourcePanel);
            if (commandDraft != null && previousRowWidth != currentRowWidth) {
                studioResourcePanelKey = "";
                buildCommandResourcePanel(commandDraft);
                studioResourcePanel.container().updateWidgetPositions();
            } else if (previousRowWidth != currentRowWidth && activeStudioView() != null && activeStudioView().hasPanel()) {
                studioResourcePanelKey = "";
                activeStudioView().configurePanel(studioResourceStudioPanel);
                studioResourcePanel.container().updateWidgetPositions();
            }
        }
        for (StudioDocument document : studioDocuments) {
            if (document.view() != null) {
                document.view().resize(width, studioEditorHeight());
            }
        }
    }

    private int studioContentBrowserHeight() {
        return Math.round(studioContentBrowserAnimatedHeight());
    }

    private int studioContentBrowserTargetHeight() {
        return studioContentBrowserCollapsed ? STUDIO_CONTENT_BROWSER_COLLAPSED_HEIGHT : clampStudioContentBrowserHeightValue(studioContentBrowserHeight < 0 ? studioContentBrowserDefaultHeight() : studioContentBrowserHeight);
    }

    private float studioContentBrowserAnimatedHeight() {
        if (studioContentBrowserAnimatedHeight < 0 || studioContentBrowserResizing) {
            studioContentBrowserAnimatedHeight = studioContentBrowserTargetHeight();
        }
        return studioContentBrowserAnimatedHeight;
    }

    private int studioContentBrowserDefaultHeight() {
        return clampStudioContentBrowserHeightValue((int) (height * STUDIO_CONTENT_BROWSER_DEFAULT_RATIO));
    }

    private int studioContentBrowserBottomMargin() {
        return 4;
    }

    private void clampStudioContentBrowserHeight() {
        if (studioContentBrowserHeight < 0) {
            studioContentBrowserHeight = studioContentBrowserDefaultHeight();
            return;
        }
        studioContentBrowserHeight = clampStudioContentBrowserHeightValue(studioContentBrowserHeight);
    }

    private boolean advanceStudioContentBrowserHeightAnimation() {
        int targetHeight = studioContentBrowserTargetHeight();
        if (studioContentBrowserAnimatedHeight < 0 || studioContentBrowserResizing) {
            boolean changed = Math.round(studioContentBrowserAnimatedHeight) != targetHeight;
            studioContentBrowserAnimatedHeight = targetHeight;
            return changed;
        }
        float previousHeight = studioContentBrowserAnimatedHeight;
        studioContentBrowserAnimatedHeight = animationsEnabled
            ? studioContentBrowserAnimatedHeight + (targetHeight - studioContentBrowserAnimatedHeight) * globalExpandSpeed * deltaTime
            : targetHeight;
        return Math.round(previousHeight) != Math.round(studioContentBrowserAnimatedHeight);
    }

    private void layoutStudioContentBrowser() {
        if (studioContentBrowser == null) {
            return;
        }
        int browserHeight = studioContentBrowserHeight();
        studioContentBrowser.setPosition(8, height - browserHeight - studioContentBrowserBottomMargin());
        studioContentBrowser.setSize(width - 16, browserHeight);
    }

    private int clampStudioContentBrowserHeightValue(int value) {
        int available = Math.max(STUDIO_CONTENT_BROWSER_MIN_HEIGHT, height - 150);
        int maxHeight = Math.min(STUDIO_CONTENT_BROWSER_MAX_HEIGHT, Math.max(STUDIO_CONTENT_BROWSER_MIN_HEIGHT, available));
        return Math.clamp(value, STUDIO_CONTENT_BROWSER_MIN_HEIGHT, maxHeight);
    }

    private void resizeStudioContentBrowser(int requestedHeight) {
        studioContentBrowserCollapsed = false;
        studioContentBrowserHeight = clampStudioContentBrowserHeightValue(requestedHeight);
        studioContentBrowserAnimatedHeight = studioContentBrowserHeight;
        updatePositions();
    }

    private void toggleStudioContentBrowser() {
        studioContentBrowserCollapsed = !studioContentBrowserCollapsed;
        studioContentBrowserResizing = false;
        updatePositions();
    }

    private int studioEditorHeight() {
        int bottom = studioContentBrowser != null ? studioContentBrowser.getY() - 8 : height;
        return Math.max(80, bottom);
    }

    private String studioResourceDescription(String label) {
        return switch (label) {
            case "Command" -> "Root command label.\nDo not include the leading slash.\nExample: trade creates /trade.";
            case "Structured" -> "Structured command mode.\nOn: ReSync stores paths and argument tokens.\nOff: the command is treated as one flat trigger label.";
            case "Path" -> "Subcommand path matched after the root command.\nTokens are separated by spaces.\nPlaceholders:\n<online_player> Online Bukkit player name.\n<offline_player> Known offline player name.\n<player_with_perm:permission.node> Online player with that permission.\n<any> Any single argument token.\nAny other <name> also matches one token.";
            case "Gui Title" -> "Inventory title shown at the top of the GUI.\nMinecraft displays it in the menu header, so keep it short.";
            case "Gui Rows" -> "Chest row count.\nValid range: 1 to 6.\nEach row adds 9 custom slots.";
            case "Gui Inventory" -> "Player inventory visibility.\nOn: show the player's inventory under the custom menu.\nOff: show only the custom menu slots.";
            case "Scoreboard Title" -> "Sidebar display title.\nMinecraft renders this above all scoreboard lines.";
            case "Objective" -> "Scoreboard objective id.\nKeep it stable because updates target this id.";
            case "Scoreboard Lines" -> "Sidebar rows under the title.\nMinecraft shows up to 15 lines.\nEarlier lines appear higher.";
            case "Tab Header" -> "Text above the player list in the tab overlay.\nSupports multiple lines.";
            case "Tab Entry" -> "Per-player tab row format.\n%player% is replaced in the preview.\nRuntime placeholders depend on the synced tab resource.";
            case "Tab Footer" -> "Text below the player list in the tab overlay.\nSupports multiple lines.";
            default -> "";
        };
    }

    @Override
    protected int studioPanelBottomReserve() {
        if (studioMode && studioContentBrowser != null) {
            return Math.max(8, height - studioContentBrowser.getY() + 18);
        }
        return super.studioPanelBottomReserve();
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
        if (type.startsWith("event:") || isFunctionStartType(type)) {
            return 0;
        }
        if (isFunctionEndType(type)) {
            return 90;
        }
        return 50;
    }

    private boolean isFunctionStartType(String type) {
        return "function_start".equals(type) || "function.start".equals(type) || "function.function_start".equals(type);
    }

    private boolean isFunctionEndType(String type) {
        return "function_end".equals(type) || "function.end".equals(type) || "function.function_end".equals(type);
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
        saveActiveStudioViewport();
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

        if (studioMode && startupState != StudioStartupState.READY) {
            renderStartupSurface(context, mouseX, mouseY, delta);
            return;
        }

        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            activeView.resize(width, studioEditorHeight());
            activeView.render(context, mouseX, mouseY, delta);
            renderStudioOverlays(context, mouseX, mouseY, delta);
            return;
        }

        if (studioMode && activeStudioDocument == null) {
            renderStudioEmptyMessage(context, mouseX, mouseY);
            renderStudioOverlays(context, mouseX, mouseY, delta);
            return;
        }

        applyInitialViewportFitIfReady();

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

    private void renderStartupSurface(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateStartupWidgets();
        if (startupIcon != null) {
            startupIcon.render(context, mouseX, mouseY, delta);
        }
        if (startupCloseButton != null && shouldShowBackButton()) {
            startupCloseButton.render(context, mouseX, mouseY, delta);
        }
        if (setupReSyncButton != null && setupReSyncButton.isVisible()) {
            setupReSyncButton.render(context, mouseX, mouseY, delta);
        }
        if (welcomeServerButton != null && welcomeServerButton.isVisible()) {
            welcomeServerButton.render(context, mouseX, mouseY, delta);
        }
        renderStartupHintOverlays(context);
    }

    private void renderStartupHintOverlays(IDrawContext context) {
        if (startupCloseButton != null && shouldShowBackButton()) {
            startupCloseButton.renderHintOverlay(context);
        }
        if (setupReSyncButton != null && setupReSyncButton.isVisible()) {
            setupReSyncButton.renderHintOverlay(context);
        }
        if (welcomeServerButton != null && welcomeServerButton.isVisible()) {
            welcomeServerButton.renderHintOverlay(context);
        }
    }

    private ReSyncStudioView activeStudioView() {
        return studioMode && activeStudioDocument != null ? activeStudioDocument.view() : null;
    }

    private boolean activeStudioViewUsesResourcePanel() {
        ReSyncStudioView view = activeStudioView();
        return view != null && view.hasPanel();
    }

    private void refreshActiveViewHeaderButtons() {
        List<AnimatedWidget> nextButtons = new ArrayList<>();
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            nextButtons.addAll(view.headerButtons());
        }
        for (AnimatedWidget button : activeViewHeaderButtons) {
            if (button != null) {
                button.visible = false;
            }
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
        if (activeStudioDocument == null) {
            return buttons;
        }
        if (activeStudioView() == null) {
            buttons.addAll(headerButtons);
        } else {
            buttons.addAll(activeViewHeaderButtons);
        }
        return buttons;
    }

    private void renderStudioEmptyMessage(IDrawContext context, int mouseX, int mouseY) {
        if (studioEmptyMessage == null) {
            return;
        }
        int top = 42;
        int bottom = studioContentBrowser != null ? studioContentBrowser.getY() - 8 : height - 8;
        int centerX = width / 2;
        int centerY = top + Math.max(0, bottom - top) / 2;
        studioEmptyMessage.setPosition(centerX - studioEmptyMessage.getWidth() / 2, centerY - studioEmptyMessage.getHeight() / 2);
        studioEmptyMessage.render(context, mouseX, mouseY, 0);
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
            context.pushScissorState();
            context.clearScissor();
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

            if (studioTabsManager != null) {
                studioTabsManager.render(context, mouseX, mouseY, delta);
            }
            context.popScissorState();
        }

        for (Widget widget : hudWidgets) {
            widget.render(context, mouseX, mouseY, delta);
        }

        if (activeStudioDocument != null && activeStudioView() == null && paletteStudioPanel != null) {
            renderStudioPanel(paletteStudioPanel, context, mouseX, mouseY, delta);
        }
        if (activeStudioDocument != null && (activeStudioView() == null || activeStudioViewUsesResourcePanel()) && studioResourceStudioPanel != null) {
            renderStudioPanel(studioResourceStudioPanel, context, mouseX, mouseY, delta);
        }
        renderActiveResourceSelectorOverlay(context, mouseX, mouseY, delta);

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
        renderHeaderHintOverlays(context);
        for (Widget widget : widgets) {
            if (widget instanceof ItemSelectorWidget selector && selector.visible) {
                selector.renderHintOverlay(context);
            } else if (widget instanceof ContextMenuWidget menu && menu.isVisible()) {
                menu.renderHintOverlay(context);
            }
        }
    }

    private void renderHeaderHintOverlays(IDrawContext context) {
        if (!studioMode) {
            return;
        }
        for (AnimatedWidget button : header().leftButtons) {
            if (button != null && button.visible) {
                button.renderHintOverlay(context);
            }
        }
        for (AnimatedWidget button : header().rightButtons) {
            if (button != null && button.visible) {
                button.renderHintOverlay(context);
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
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            view.renderPreview(context, left, top, areaWidth, areaHeight);
        } else if (ReSyncResourceDragPayload.WORLD.equals(activeStudioDocument.type())) {
            int text = ThemeManager.getColor(ThemeColor.text);
            context.drawText(activeStudioDocument.title(), left + 12, top + 12, text, false);
        }
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
                drawWire(context, (float)sourcePinWorld[0], (float)sourcePinWorld[1], (float)mouseWorld[0], (float)mouseWorld[1], dragWireColor);
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
        if (handleActiveStudioSelectorMouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (handlePopupWidgetMouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        ReSyncStudioView earlyActiveView = activeStudioView();
        if (earlyActiveView instanceof StudioPriorityInputView && earlyActiveView.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && studioContentBrowser.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && studioResourcePanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
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
        drawWire(context, (float) start.x(), (float) start.y(), (float) end.x(), (float) end.y(), color);
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
        drawWire(context, (float) output.x(), (float) output.y(), (float) end.x(), (float) end.y(), color);
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
            return Math.round(source.x() + Math.clamp((targetX - source.x()) * 0.45, WIRE_OUT_OFFSET, 220));
        }
        return Math.round(source.x() + WIRE_OUT_OFFSET);
    }

    private void drawWire(IDrawContext context, float startX, float startY, float endX, float endY, int color) {
        drawWireSegments(context, wireSegments(startX, startY, endX, endY), color);
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

    private List<WireSegment> wireSegments(double x1, double y1, double x2, double y2) {
        int startX = Math.round((float) x1);
        int startY = Math.round((float) y1);
        int endX = Math.round((float) x2);
        int endY = Math.round((float) y2);
        int outX = startX + WIRE_OUT_OFFSET;
        int inX = endX - WIRE_OUT_OFFSET;
        int midY = Math.round((startY + endY) / 2f);
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
        if (studioMode && startupState != StudioStartupState.READY) {
            return handleStartupMouseClicked(mouseX, mouseY, button);
        }
        if (handleContextMenuMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (handleNodeItemSelectorMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (handleActiveStudioSelectorMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (handlePopupWidgetMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        ReSyncStudioView earlyActiveView = activeStudioView();
        if (earlyActiveView instanceof StudioPriorityInputView && earlyActiveView.mouseClicked(mouseX, mouseY, button)) {
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
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && studioResourcePanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return activeView.mouseClicked(mouseX, mouseY, button);
        }
        if (paletteSidePanel != null && paletteSidePanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (studioResourcePanel != null && studioResourcePanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }


        double[] worldMouse = screenToWorld(headerCoords[0], headerCoords[1]);
        int wx = (int)worldMouse[0];
        int wy = (int)worldMouse[1];

        if (handleHeaderButtonsClick((int) headerCoords[0], (int) headerCoords[1], button)) {
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
                setFocusedWidget(null);
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
                        dragMouseX = headerCoords[0];
                        dragMouseY = headerCoords[1];
                        startWireDrag(widget, pinName, true);
                        setFocusedWidget(null);
                        return true;
                    }

                    double[] outputBounds = widget.getPinBounds(pinName, false);
                    if (isInside(wx, wy, outputBounds)) {
                        dragMouseX = headerCoords[0];
                        dragMouseY = headerCoords[1];
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
                if (inputWidget instanceof TextInputWidget) {
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
                widget.setLastScreenMouse((int) headerCoords[0], (int) headerCoords[1]);
                widget.mouseClicked(wx, wy, button);
                return true;
            }
        }

        focusedNode = null;
        setFocusedWidget(null);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (hasShiftDown() || hasControlDown()) {
                startSelection(headerCoords[0], headerCoords[1]);
                return true;
            }
            clearSelection();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleContextMenuMouseClicked(double mouseX, double mouseY, int button) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (!(widget instanceof ContextMenuWidget menu) || !menu.isVisible()) {
                continue;
            }
            boolean overMenu = menu.isMouseOver(mouseX, mouseY);
            boolean handled = menu.mouseClicked(mouseX, mouseY, button);
            hideContextMenu();
            if (handled || overMenu) {
                return true;
            }
            return button != GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        }
        return false;
    }

    private boolean handlePopupWidgetMouseClicked(double mouseX, double mouseY, int button) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && popup.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    private boolean handlePopupWidgetMouseReleased(double mouseX, double mouseY, int button) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && popup.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    private boolean handlePopupWidgetMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && popup.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }

    private boolean handlePopupWidgetMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && popup.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
                return true;
            }
        }
        return false;
    }

    private boolean handlePopupWidgetKeyPressed(int keyCode, int scanCode, int modifiers) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && popup.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private boolean handlePopupWidgetCharTyped(char chr, int modifiers) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && popup.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleActiveStudioSelectorMouseClicked(double mouseX, double mouseY, int button) {
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    private boolean handleActiveStudioSelectorMouseReleased(double mouseX, double mouseY, int button) {
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    private boolean handleActiveStudioSelectorMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    private boolean handleActiveStudioSelectorMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return false;
    }

    private boolean handleActiveStudioSelectorKeyPressed(int keyCode, int scanCode, int modifiers) {
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    private boolean handleActiveStudioSelectorCharTyped(char chr, int modifiers) {
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.charTyped(chr, modifiers);
        }
        return false;
    }

    private boolean handleStartupMouseClicked(double mouseX, double mouseY, int button) {
        if (startupCloseButton != null && shouldShowBackButton() && startupCloseButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (setupReSyncButton != null && setupReSyncButton.isVisible() && setupReSyncButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return welcomeServerButton != null && welcomeServerButton.isVisible() && welcomeServerButton.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleStudioHudMouseClicked(double mouseX, double mouseY, int button) {
        if (!studioMode) {
            return false;
        }
        if (studioContentBrowser != null && studioContentBrowser.handleHistoryMouseButton(button)) {
            return true;
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
        if (handleActiveStudioSelectorMouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (handlePopupWidgetMouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        ReSyncStudioView earlyActiveView = activeStudioView();
        if (earlyActiveView instanceof StudioPriorityInputView && earlyActiveView.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && studioContentBrowser.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && studioResourcePanel.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
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
        if (handleActiveStudioSelectorKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (handlePopupWidgetKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        ReSyncStudioView earlyActiveView = activeStudioView();
        if (earlyActiveView instanceof StudioPriorityInputView && earlyActiveView.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && studioContentBrowser.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && studioResourcePanel.isVisible() && studioResourcePanel.container().keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return activeView.keyPressed(keyCode, scanCode, modifiers);
        }
        if (studioResourcePanel != null && studioResourcePanel.isVisible() && studioResourcePanel.container().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (isKeyboardInputFocused() && super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)
                && !isKeyboardInputFocused()) {
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

        if (hasControl && !isKeyboardInputFocused()) {
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
            if (handleStudioHistoryShortcut(keyCode, modifiers)) {
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected boolean isKeyboardInputFocused() {
        Widget focusedWidget = getFocusedWidget();
        return focusedWidget instanceof TextInputWidget
                || focusedWidget instanceof ItemSelectorWidget;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (studioTabsManager != null && studioTabsManager.charTyped(chr, modifiers)) {
            return true;
        }
        if (nodeItemSelector != null && nodeItemSelector.visible && nodeItemSelector.charTyped(chr, modifiers)) {
            return true;
        }
        if (handleActiveStudioSelectorCharTyped(chr, modifiers)) {
            return true;
        }
        if (handlePopupWidgetCharTyped(chr, modifiers)) {
            return true;
        }
        ReSyncStudioView earlyActiveView = activeStudioView();
        if (earlyActiveView instanceof StudioPriorityInputView && earlyActiveView.charTyped(chr, modifiers)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && studioContentBrowser.charTyped(chr, modifiers)) {
            return true;
        }
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && studioResourcePanel.isVisible() && studioResourcePanel.container().charTyped(chr, modifiers)) {
                return true;
            }
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
        builder.addHeaderButton("add.png", widget::showAddFunctionParameterPopup, "Add Parameter", ThemeManager.getAccent("nice"));
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
            if (sourceType != null) {
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
                addSelectorItem(builder, selectorLabel(def), selectorHint(def), selectorSearchTerms(def), () -> {
                    captureSnapshot();
                    addNode(worldX, worldY, def.getId(), pinName);
                });
                addSelectorVariantItems(builder, def, worldX, worldY, pinName);
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
        next.subcommands = collectCommandPaths();
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
                studioDocuments.set(i, new StudioDocument(document.type(), newId, newId, graph, document.view(), document.viewport()));
                activeStudioDocument = studioDocuments.get(i);
                break;
            }
        }
        graph.setId(newId);
        syncStudioDocumentTabs();
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
            return end != null ? wireSegments(output.x(), output.y(), end.x(), end.y()) : List.of();
        }
        List<FlowConnection> fanout = fanoutConnections(connection);
        if (fanout.size() > 1) {
            return fanoutSegments(connection, fanout, false);
        }
        PinPoint start = sourceOutputPoint(connection);
        return start != null && end != null ? wireSegments(start.x(), start.y(), end.x(), end.y()) : List.of();
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
        t = Math.clamp(t, 0, 1);
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

        selectedNodeIds.addAll(newSelectedIds);
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
        graphHistory.capture();
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
            copied.add(new FlowGraph.FunctionParameter(parameter.getName(), parameter.getType(), parameter.getWidget(), parameter.getOptionsSource(), parameter.getDefaultValue()));
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

    private class WorldStudioView implements ReSyncStudioView, StudioSelectorView, WorldStudioDocumentView {
        private final Container worldsList;
        private final Container detailPane;
        private final Map<String, WorldEntryRow> entries = new HashMap<>();
        private final List<AnimatedWidget> detailWidgets = new ArrayList<>();
        private final List<AnimatedWidget> headerActions = new ArrayList<>();
        private ItemSelectorWidget activePlayerSelector;
        private ItemSelectorWidget activeOptionSelector;
        private WorldDetailForm detailForm;
        private String selectedWorldName;
        private boolean initialized;
        private int x;
        private int y;
        private int width;
        private int height;

        private WorldStudioView(String initialWorldName) {
            this.selectedWorldName = safeText(initialWorldName);
            this.worldsList = new Container("worlds-list", 0, 0, 260, 100);
            this.worldsList.layout(new ManagedLayout()).columns(1).padding(4).scrolling(true).backgroundDrawing(true);
            this.detailPane = new Container("world-detail", 0, 0, 300, 100);
            this.detailPane.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true).backgroundDrawing(true);
            createHeaderActions();
        }

        @Override
        public void init() {
            if (initialized) {
                return;
            }
            initialized = true;
            refreshWorlds();
        }

        @Override
        public void selected() {
            init();
            refreshDetails();
        }

        @Override
        public List<AnimatedWidget> headerButtons() {
            return headerActions;
        }

        @Override
        public void resize(int width, int height) {
            this.x = 10;
            this.y = 36;
            this.width = Math.max(80, width - 20);
            this.height = Math.max(80, height - 44);
            updateLayout();
        }

        @Override
        public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
            init();
            updateLayout();
            worldsList.render(context, mouseX, mouseY, delta);
            detailPane.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            init();
            if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return detailPane.mouseClicked(mouseX, mouseY, button) || worldsList.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
            if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
            return detailPane.mouseReleased(mouseX, mouseY, button) || worldsList.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
            if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
            return detailPane.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) || worldsList.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
                return true;
            }
            if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
                return true;
            }
            return detailPane.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount) || worldsList.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return detailPane.keyPressed(keyCode, scanCode, modifiers) || worldsList.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.charTyped(chr, modifiers)) {
                return true;
            }
            if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.charTyped(chr, modifiers)) {
                return true;
            }
            return detailPane.charTyped(chr, modifiers) || worldsList.charTyped(chr, modifiers);
        }

        @Override
        public boolean hasActiveStudioSelector() {
            return activeOptionSelector != null && activeOptionSelector.visible
                || activePlayerSelector != null && activePlayerSelector.visible;
        }

        private void createHeaderActions() {
            headerActions.add(studioHeaderButton("New World", "create.png", this::showCreateWorldPopup, ThemeManager.getAccent("nice")));
            headerActions.add(studioHeaderButton("Import", "download.png", () -> worldManager().importWorlds(serverId), null));
            headerActions.add(studioHeaderButton("Scan", "search.png", () -> worldManager().scanWorlds(serverId), null));
            headerActions.add(studioHeaderButton("Refresh", "reload.png", () -> worldManager().refreshWorldsFromServer(serverId), null));
            headerActions.add(studioHeaderButton("Groups", "resources.png", this::showInventoryGroupsPopup, null));
            headerActions.add(studioHeaderButton("History", "history.png", () -> worldManager().requestWorldAuditSnapshot(serverId), null));
        }

        private IconButton studioHeaderButton(String label, String icon, Runnable action, Accent accent) {
            IconButton.Builder builder = new IconButton.Builder()
                .label(label)
                .imagePath(icon)
                .size(0, 18)
                .autoWidthOnTextChange(true)
                .hint(label)
                .entranceAnimation(false)
                .onClick(action);
            if (accent != null) {
                builder.accentType(accent);
            }
            return builder.build();
        }

        private FlowManager worldManager() {
            return FlowManager.getInstance();
        }

        private void updateLayout() {
            int gap = 8;
            int listWidth = Math.clamp(width / 3, 250, 370);
            worldsList.setPosition(x, y);
            worldsList.setSize(listWidth, height);
            detailPane.setPosition(x + listWidth + gap, y);
            detailPane.setSize(Math.max(80, width - listWidth - gap), height);
            updateEntryWidths();
        }

        private void updateEntryWidths() {
            int entryWidth = Math.max(180, worldsList.getWidth() - 12);
            for (WorldEntryRow entry : entries.values()) {
                entry.widget.setSize(entryWidth, 34);
            }
        }

        @Override
        public void refreshWorlds() {
            FlowManager manager = worldManager();
            if (manager == null) {
                return;
            }
            Map<String, WorldRegistryEntry> worlds = manager.getWorldsForServer(serverId);
            Set<String> nextWorlds = new HashSet<>(worlds.keySet());
            for (String existing : new ArrayList<>(entries.keySet())) {
                if (!containsIgnoreCase(nextWorlds, existing)) {
                    WorldEntryRow row = entries.remove(existing);
                    if (row != null) {
                        worldsList.removeWidget(row.widget);
                    }
                }
            }
            List<String> worldNames = new ArrayList<>(worlds.keySet());
            worldNames.sort(String.CASE_INSENSITIVE_ORDER);
            if (selectedWorldName.isBlank() || !containsIgnoreCase(nextWorlds, selectedWorldName)) {
                selectedWorldName = worldNames.isEmpty() ? "" : worldNames.getFirst();
            }
            for (String worldName : worldNames) {
                upsertWorldEntry(worldName);
            }
            refreshDetails();
            worldsList.updateWidgetPositions();
        }

        private boolean containsIgnoreCase(Collection<String> values, String value) {
            if (values == null || value == null) {
                return false;
            }
            for (String entry : values) {
                if (value.equalsIgnoreCase(safeText(entry))) {
                    return true;
                }
            }
            return false;
        }

        private void upsertWorldEntry(String worldName) {
            FlowManager manager = worldManager();
            WorldRegistryEntry world = manager != null ? manager.getWorld(serverId, worldName) : null;
            if (world == null) {
                return;
            }
            String existingKey = findEntryKeyIgnoreCase(entries, worldName);
            WorldDashboardEntry dashboard = findWorldDashboard(worldName);
            String status = dashboard != null ? safeText(dashboard.getStatus()) : world.isLoaded() ? "Loaded" : "Unloaded";
            int players = dashboard != null ? dashboard.getPlayerCount() : 0;
            String environment = dashboard != null && dashboard.getEnvironment() != null ? dashboard.getEnvironment() : world.getEnvironment();
            String difficulty = dashboard != null && dashboard.getDifficulty() != null ? dashboard.getDifficulty() : world.getDifficulty();
            String alias = dashboard != null ? safeText(dashboard.getAlias()) : safeText(world.getProfileSettings().getAlias());
            String description = status + " | " + safeText(environment) + " | " + players + " Players | " + safeText(difficulty);
            String hidden = worldHiddenSummary(world, alias);
            WorldEntryRow row = existingKey == null ? null : entries.get(existingKey);
            if (row != null) {
                if (!Objects.equals(existingKey, worldName)) {
                    entries.remove(existingKey);
                    entries.put(worldName, row);
                }
                row.update(worldName, description, hidden, world.isLoaded(), worldName.equalsIgnoreCase(selectedWorldName));
                return;
            }
            row = new WorldEntryRow(worldName, description, hidden, world.isLoaded(), worldName.equalsIgnoreCase(selectedWorldName));
            row.widget.setSize(Math.max(180, worldsList.getWidth() - 12), 34);
            worldsList.addWidget(row.widget);
            entries.put(worldName, row);
        }

        private class WorldEntryRow {
            private final MountableButtonWidget widget;
            private final SquareButtonWidget stateButton;
            private final SquareButtonWidget actionsButton;
            private String worldName;

            private WorldEntryRow(String worldName, String description, String hiddenText, boolean loaded, boolean selected) {
                this.worldName = worldName;
                SquareButtonWidget mapButton = new SquareButtonWidget.Builder()
                    .imagePath("map.png")
                    .hint("Open Map")
                    .onClick(() -> worldManager().openWorldMap(serverId, null, this.worldName))
                    .build();
                stateButton = new SquareButtonWidget.Builder()
                    .imagePath(loaded ? "hide.png" : "add.png")
                    .hint(loaded ? "Unload World" : "Load World")
                    .onClick(() -> {
                        WorldRegistryEntry world = worldManager().getWorld(serverId, this.worldName);
                        if (world == null) {
                            return;
                        }
                        if (world.isLoaded()) {
                            showWorldUnloadPopup(this.worldName);
                        } else {
                            worldManager().loadWorld(serverId, this.worldName);
                        }
                    })
                    .build();
                SquareButtonWidget builtActionsButton = new SquareButtonWidget.Builder()
                    .imagePath("ContextMenu.png")
                    .hint("More Actions")
                    .build();
                builtActionsButton.action = () -> showWorldActionsMenu(this.worldName, builtActionsButton);
                actionsButton = builtActionsButton;
                widget = new MountableButtonWidget.Builder(worldName)
                    .description(description)
                    .hiddenText(hiddenText)
                    .onClick(() -> selectWorld(this.worldName))
                    .addButton(mapButton)
                    .addButton(stateButton)
                    .addButton(actionsButton)
                    .build();
                update(worldName, description, hiddenText, loaded, selected);
            }

            private void update(String worldName, String description, String hiddenText, boolean loaded, boolean selected) {
                this.worldName = worldName;
                widget.setName(worldName);
                widget.setDescription(description);
                widget.setHiddenText(hiddenText);
                widget.titleColor = selected ? ThemeManager.getAccent("default").getAccentColor() : ThemeManager.getColor(ThemeColor.text);
                stateButton.setIcon(loaded ? "hide.png" : "add.png");
                stateButton.hint = loaded ? "Unload World" : "Load World";
            }
        }

        private String worldHiddenSummary(WorldRegistryEntry world, String alias) {
            StringBuilder hidden = new StringBuilder();
            if (!alias.isBlank()) {
                hidden.append("Alias: ").append(alias);
            }
            if (!safeText(world.getGenerator()).isBlank()) {
                appendHidden(hidden, "Generator: " + world.getGenerator());
            }
            WorldProfileSettings profile = world.getProfileSettings();
            if (!safeText(profile.getInventoryGroupId()).isBlank()) {
                appendHidden(hidden, "Group: " + profile.getInventoryGroupId());
            }
            if (!profile.isPvpEnabled()) {
                appendHidden(hidden, "PVP Off");
            }
            if (!profile.isAutoSaveEnabled()) {
                appendHidden(hidden, "Auto Save Off");
            }
            if (world.isTimeLockEnabled()) {
                appendHidden(hidden, "Time Locked");
            }
            if (world.isWeatherLockEnabled()) {
                appendHidden(hidden, "Weather Locked");
            }
            return hidden.toString();
        }

        private void appendHidden(StringBuilder hidden, String value) {
            if (!hidden.isEmpty()) {
                hidden.append(" | ");
            }
            hidden.append(value);
        }

        private void selectWorld(String worldName) {
            selectedWorldName = safeText(worldName);
            for (String entryName : new ArrayList<>(entries.keySet())) {
                upsertWorldEntry(entryName);
            }
            refreshDetails();
        }

        private void refreshDetails() {
            FlowManager manager = worldManager();
            WorldRegistryEntry world = manager != null ? manager.getWorld(serverId, selectedWorldName) : null;
            if (world == null) {
                clearDetailWidgets();
                addDetailWidget(emptyDetailButton());
                detailPane.updateWidgetPositions();
                detailForm = null;
                return;
            }
            if (detailForm != null && selectedWorldName.equalsIgnoreCase(detailForm.worldName)) {
                detailForm.update(world);
                return;
            }
            clearDetailWidgets();
            WorldProfileSettings profile = world.getProfileSettings();
            int rowWidth = Math.max(220, detailPane.getWidth() - 18);
            TextInputWidget alias = detailInput("Alias", profile.getAlias(), rowWidth / 2 - 5);
            OptionField difficulty = optionField(WorldUiSupport.mergeOptions(List.of("PEACEFUL", "EASY", "NORMAL", "HARD"), safeText(world.getDifficulty()).toUpperCase(Locale.ROOT)),
                safeText(world.getDifficulty()).isBlank() ? "NORMAL" : safeText(world.getDifficulty()).toUpperCase(Locale.ROOT), rowWidth / 2 - 5);
            ToggleWidget hidden = detailToggle("Hidden", profile.isHidden(), 92);
            ToggleWidget forceGameMode = detailToggle("Force Game Mode", profile.isForceGameMode(), 132);
            OptionField gameMode = optionField(WorldUiSupport.mergeOptions(List.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"), safeText(profile.getGameMode()).toUpperCase(Locale.ROOT)),
                safeText(profile.getGameMode()).isBlank() ? "SURVIVAL" : safeText(profile.getGameMode()).toUpperCase(Locale.ROOT), rowWidth / 2 - 5);
            ToggleWidget pvp = detailToggle("PVP", profile.isPvpEnabled(), 70);
            ToggleWidget autoSave = detailToggle("Auto Save", profile.isAutoSaveEnabled(), 105);
            ToggleWidget keepSpawn = detailToggle("Keep Spawn", profile.isKeepSpawnLoaded(), 112);
            ToggleWidget animals = detailToggle("Animals", profile.isAnimalSpawnsEnabled(), 92);
            ToggleWidget monsters = detailToggle("Monsters", profile.isMonsterSpawnsEnabled(), 100);
            ToggleWidget hunger = detailToggle("Hunger", profile.isHungerEnabled(), 90);
            ToggleWidget autoHeal = detailToggle("Auto Heal", profile.isAutoHealEnabled(), 100);
            ToggleWidget bedRespawn = detailToggle("Bed Respawn", profile.isBedRespawnEnabled(), 118);
            ToggleWidget anchorRespawn = detailToggle("Anchor Respawn", profile.isAnchorRespawnEnabled(), 132);
            ToggleWidget miscSpawns = detailToggle("Misc Spawns", profile.isNonLivingEntitySpawnsEnabled(), 115);
            TextInputWidget accessPermission = detailInput("Access Permission", profile.getAccessPermission(), rowWidth / 2 - 5);
            TextInputWidget bypassPermission = detailInput("Bypass Permission", profile.getBypassPermission(), rowWidth / 2 - 5);
            TextInputWidget arrivalMessage = detailInput("Arrival Message", profile.getArrivalMessage(), rowWidth / 2 - 5);
            TextInputWidget denyMessage = detailInput("Deny Message", profile.getDenyMessage(), rowWidth / 2 - 5);
            TextInputWidget respawnWorld = detailInput("Respawn World", profile.getRespawnWorld(), rowWidth / 2 - 5);
            TextInputWidget inventoryGroup = detailInput("Inventory Group", profile.getInventoryGroupId(), rowWidth / 2 - 5);
            RowWidget respawnWorldPicker = detailPicker(respawnWorld, rowWidth / 2 - 5, fallbackWorldOptions(world.getWorldName()), value -> respawnWorld.setText(value));
            RowWidget inventoryGroupPicker = detailPicker(inventoryGroup, rowWidth / 2 - 5, inventoryGroupOptions(), value -> inventoryGroup.setText("No Group".equals(value) ? "" : value));
            ToggleWidget customSpawn = detailToggle("Custom Spawn", profile.isCustomSpawnEnabled(), 120);
            TextInputWidget spawnX = detailInput("X", formatDecimal(profile.getSpawnX()), 82);
            TextInputWidget spawnY = detailInput("Y", formatDecimal(profile.getSpawnY()), 82);
            TextInputWidget spawnZ = detailInput("Z", formatDecimal(profile.getSpawnZ()), 82);
            TextInputWidget spawnYaw = detailInput("Yaw", formatDecimal(profile.getSpawnYaw()), 82);
            TextInputWidget spawnPitch = detailInput("Pitch", formatDecimal(profile.getSpawnPitch()), 82);
            TextInputWidget netherWorld = detailInput("Nether World", profile.getLinkedNetherWorld(), rowWidth / 3 - 5);
            TextInputWidget endWorld = detailInput("End World", profile.getLinkedEndWorld(), rowWidth / 3 - 5);
            TextInputWidget overworld = detailInput("Overworld", profile.getLinkedOverworld(), rowWidth / 3 - 5);
            TextInputWidget netherScale = detailInput("Nether Scale", formatDecimal(profile.getNetherScale()), rowWidth / 3 - 5);
            TextInputWidget endScale = detailInput("End Scale", formatDecimal(profile.getEndScale()), rowWidth / 3 - 5);
            ToggleWidget autoNether = detailToggle("Auto Nether", profile.isAutoLinkNetherPortal(), 112);
            ToggleWidget autoEnd = detailToggle("Auto End", profile.isAutoLinkEndPortal(), 92);
            ToggleWidget isolated = detailToggle("Isolated State", world.isIsolatedPlayerState(), 125);
            ToggleWidget timeLock = detailToggle("Time Lock", world.isTimeLockEnabled(), 100);
            TextInputWidget lockedTime = detailInput("Locked Time", String.valueOf(world.getLockedTime()), 120);
            ToggleWidget weatherLock = detailToggle("Weather Lock", world.isWeatherLockEnabled(), 118);
            ToggleWidget storm = detailToggle("Storm", world.isLockedStorm(), 78);
            ToggleWidget thundering = detailToggle("Thunder", world.isLockedThundering(), 92);

            addDetailWidget(metricRow(rowWidth, world));
            addDetailWidget(row("Identity", rowWidth, alias, difficulty.button()));
            addDetailWidget(row("Access", rowWidth, hidden, forceGameMode, gameMode.button()));
            addDetailWidget(row("Permissions", rowWidth, accessPermission, bypassPermission));
            addDetailWidget(row("Messages", rowWidth, arrivalMessage, denyMessage));
            addDetailWidget(row("Rules", rowWidth, pvp, autoSave, keepSpawn, animals, monsters));
            addDetailWidget(row("Player State", rowWidth, hunger, autoHeal, bedRespawn, anchorRespawn, miscSpawns));
            addDetailWidget(row("Travel", rowWidth, respawnWorldPicker, inventoryGroupPicker));
            addDetailWidget(row("Spawn", rowWidth, customSpawn, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch));
            addDetailWidget(row("Links", rowWidth, netherWorld, endWorld, overworld));
            addDetailWidget(row("Portal Scale", rowWidth, netherScale, endScale, autoNether, autoEnd));
            addDetailWidget(row("Runtime", rowWidth, isolated, timeLock, lockedTime, weatherLock, storm, thundering));
            detailForm = new WorldDetailForm(world.getWorldName(), alias, difficulty, hidden, forceGameMode, gameMode, pvp, autoSave,
                keepSpawn, animals, monsters, hunger, autoHeal, bedRespawn, anchorRespawn, miscSpawns, accessPermission,
                bypassPermission, arrivalMessage, denyMessage, respawnWorld, inventoryGroup, customSpawn, spawnX, spawnY, spawnZ,
                spawnYaw, spawnPitch, netherWorld, endWorld, overworld, netherScale, endScale, autoNether, autoEnd, isolated,
                timeLock, lockedTime, weatherLock, storm, thundering);
            addDetailWidget(saveWorldButton(rowWidth, world, alias, difficulty, hidden, forceGameMode, gameMode, pvp, autoSave, keepSpawn,
                animals, monsters, hunger, autoHeal, bedRespawn, anchorRespawn, miscSpawns, accessPermission, bypassPermission,
                arrivalMessage, denyMessage, respawnWorld, inventoryGroup, customSpawn, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch,
                netherWorld, endWorld, overworld, netherScale, endScale, autoNether, autoEnd, isolated, timeLock, lockedTime,
                weatherLock, storm, thundering));
            detailPane.updateWidgetPositions();
        }

        private void clearDetailWidgets() {
            closeOptionSelector();
            for (AnimatedWidget widget : new ArrayList<>(detailWidgets)) {
                detailPane.removeWidget(widget);
            }
            detailWidgets.clear();
        }

        private class WorldDetailForm {
            private final String worldName;
            private final TextInputWidget alias;
            private final OptionField difficulty;
            private final ToggleWidget hidden;
            private final ToggleWidget forceGameMode;
            private final OptionField gameMode;
            private final ToggleWidget pvp;
            private final ToggleWidget autoSave;
            private final ToggleWidget keepSpawn;
            private final ToggleWidget animals;
            private final ToggleWidget monsters;
            private final ToggleWidget hunger;
            private final ToggleWidget autoHeal;
            private final ToggleWidget bedRespawn;
            private final ToggleWidget anchorRespawn;
            private final ToggleWidget miscSpawns;
            private final TextInputWidget accessPermission;
            private final TextInputWidget bypassPermission;
            private final TextInputWidget arrivalMessage;
            private final TextInputWidget denyMessage;
            private final TextInputWidget respawnWorld;
            private final TextInputWidget inventoryGroup;
            private final ToggleWidget customSpawn;
            private final TextInputWidget spawnX;
            private final TextInputWidget spawnY;
            private final TextInputWidget spawnZ;
            private final TextInputWidget spawnYaw;
            private final TextInputWidget spawnPitch;
            private final TextInputWidget netherWorld;
            private final TextInputWidget endWorld;
            private final TextInputWidget overworld;
            private final TextInputWidget netherScale;
            private final TextInputWidget endScale;
            private final ToggleWidget autoNether;
            private final ToggleWidget autoEnd;
            private final ToggleWidget isolated;
            private final ToggleWidget timeLock;
            private final TextInputWidget lockedTime;
            private final ToggleWidget weatherLock;
            private final ToggleWidget storm;
            private final ToggleWidget thundering;

            private WorldDetailForm(String worldName, TextInputWidget alias, OptionField difficulty, ToggleWidget hidden,
                                    ToggleWidget forceGameMode, OptionField gameMode, ToggleWidget pvp, ToggleWidget autoSave,
                                    ToggleWidget keepSpawn, ToggleWidget animals, ToggleWidget monsters, ToggleWidget hunger,
                                    ToggleWidget autoHeal, ToggleWidget bedRespawn, ToggleWidget anchorRespawn, ToggleWidget miscSpawns,
                                    TextInputWidget accessPermission, TextInputWidget bypassPermission, TextInputWidget arrivalMessage,
                                    TextInputWidget denyMessage, TextInputWidget respawnWorld, TextInputWidget inventoryGroup,
                                    ToggleWidget customSpawn, TextInputWidget spawnX, TextInputWidget spawnY, TextInputWidget spawnZ,
                                    TextInputWidget spawnYaw, TextInputWidget spawnPitch, TextInputWidget netherWorld,
                                    TextInputWidget endWorld, TextInputWidget overworld, TextInputWidget netherScale,
                                    TextInputWidget endScale, ToggleWidget autoNether, ToggleWidget autoEnd, ToggleWidget isolated,
                                    ToggleWidget timeLock, TextInputWidget lockedTime, ToggleWidget weatherLock, ToggleWidget storm,
                                    ToggleWidget thundering) {
                this.worldName = safeText(worldName);
                this.alias = alias;
                this.difficulty = difficulty;
                this.hidden = hidden;
                this.forceGameMode = forceGameMode;
                this.gameMode = gameMode;
                this.pvp = pvp;
                this.autoSave = autoSave;
                this.keepSpawn = keepSpawn;
                this.animals = animals;
                this.monsters = monsters;
                this.hunger = hunger;
                this.autoHeal = autoHeal;
                this.bedRespawn = bedRespawn;
                this.anchorRespawn = anchorRespawn;
                this.miscSpawns = miscSpawns;
                this.accessPermission = accessPermission;
                this.bypassPermission = bypassPermission;
                this.arrivalMessage = arrivalMessage;
                this.denyMessage = denyMessage;
                this.respawnWorld = respawnWorld;
                this.inventoryGroup = inventoryGroup;
                this.customSpawn = customSpawn;
                this.spawnX = spawnX;
                this.spawnY = spawnY;
                this.spawnZ = spawnZ;
                this.spawnYaw = spawnYaw;
                this.spawnPitch = spawnPitch;
                this.netherWorld = netherWorld;
                this.endWorld = endWorld;
                this.overworld = overworld;
                this.netherScale = netherScale;
                this.endScale = endScale;
                this.autoNether = autoNether;
                this.autoEnd = autoEnd;
                this.isolated = isolated;
                this.timeLock = timeLock;
                this.lockedTime = lockedTime;
                this.weatherLock = weatherLock;
                this.storm = storm;
                this.thundering = thundering;
            }

            private void update(WorldRegistryEntry world) {
                WorldProfileSettings profile = world.getProfileSettings();
                updateInput(alias, profile.getAlias());
                difficulty.setValue(safeText(world.getDifficulty()).isBlank() ? "NORMAL" : safeText(world.getDifficulty()).toUpperCase(Locale.ROOT));
                hidden.setValue(profile.isHidden());
                forceGameMode.setValue(profile.isForceGameMode());
                gameMode.setValue(safeText(profile.getGameMode()).isBlank() ? "SURVIVAL" : safeText(profile.getGameMode()).toUpperCase(Locale.ROOT));
                pvp.setValue(profile.isPvpEnabled());
                autoSave.setValue(profile.isAutoSaveEnabled());
                keepSpawn.setValue(profile.isKeepSpawnLoaded());
                animals.setValue(profile.isAnimalSpawnsEnabled());
                monsters.setValue(profile.isMonsterSpawnsEnabled());
                hunger.setValue(profile.isHungerEnabled());
                autoHeal.setValue(profile.isAutoHealEnabled());
                bedRespawn.setValue(profile.isBedRespawnEnabled());
                anchorRespawn.setValue(profile.isAnchorRespawnEnabled());
                miscSpawns.setValue(profile.isNonLivingEntitySpawnsEnabled());
                updateInput(accessPermission, profile.getAccessPermission());
                updateInput(bypassPermission, profile.getBypassPermission());
                updateInput(arrivalMessage, profile.getArrivalMessage());
                updateInput(denyMessage, profile.getDenyMessage());
                updateInput(respawnWorld, profile.getRespawnWorld());
                updateInput(inventoryGroup, profile.getInventoryGroupId());
                customSpawn.setValue(profile.isCustomSpawnEnabled());
                updateInput(spawnX, formatDecimal(profile.getSpawnX()));
                updateInput(spawnY, formatDecimal(profile.getSpawnY()));
                updateInput(spawnZ, formatDecimal(profile.getSpawnZ()));
                updateInput(spawnYaw, formatDecimal(profile.getSpawnYaw()));
                updateInput(spawnPitch, formatDecimal(profile.getSpawnPitch()));
                updateInput(netherWorld, profile.getLinkedNetherWorld());
                updateInput(endWorld, profile.getLinkedEndWorld());
                updateInput(overworld, profile.getLinkedOverworld());
                updateInput(netherScale, formatDecimal(profile.getNetherScale()));
                updateInput(endScale, formatDecimal(profile.getEndScale()));
                autoNether.setValue(profile.isAutoLinkNetherPortal());
                autoEnd.setValue(profile.isAutoLinkEndPortal());
                isolated.setValue(world.isIsolatedPlayerState());
                timeLock.setValue(world.isTimeLockEnabled());
                updateInput(lockedTime, String.valueOf(world.getLockedTime()));
                weatherLock.setValue(world.isWeatherLockEnabled());
                storm.setValue(world.isLockedStorm());
                thundering.setValue(world.isLockedThundering());
            }

            private void updateInput(TextInputWidget input, String value) {
                if (input != null && !input.isFocused() && !Objects.equals(input.getText(), safeText(value))) {
                    input.setText(safeText(value));
                }
            }

        }

        private AnimatedWidget metricRow(int rowWidth, WorldRegistryEntry world) {
            WorldDashboardEntry dashboard = findWorldDashboard(world.getWorldName());
            String status = dashboard != null ? safeText(dashboard.getStatus()) : world.isLoaded() ? "Loaded" : "Unloaded";
            int players = dashboard != null ? dashboard.getPlayerCount() : 0;
            IconButton statusButton = metricButton(status, world.isLoaded() ? "play.png" : "hide.png", 120);
            IconButton playersButton = metricButton(players + " Players", "steve.png", 120);
            IconButton environmentButton = metricButton(safeText(world.getEnvironment()).isBlank() ? "Unknown" : world.getEnvironment(), "earth.png", 145);
            IconButton generatorButton = metricButton(safeText(world.getGenerator()).isBlank() ? "Default" : world.getGenerator(), "resources.png", 160);
            return new RowWidget.Builder().size(rowWidth, 20).addWidget(statusButton, playersButton, environmentButton, generatorButton).build();
        }

        private IconButton metricButton(String label, String icon, int width) {
            IconButton button = new IconButton.Builder()
                .label(label)
                .imagePath(icon)
                .size(width, 18)
                .entranceAnimation(false)
                .build();
            button.active = false;
            return button;
        }

        private TitledRowWidget row(String title, int rowWidth, AnimatedWidget... widgets) {
            TitledRowWidget.Builder builder = new TitledRowWidget.Builder()
                .title(title)
                .description(worldPanelDescription(title))
                .size(rowWidth, 30)
                .padding(4);
            for (AnimatedWidget widget : widgets) {
                builder.addWidget(widget);
            }
            return builder.build();
        }

        private String worldPanelDescription(String title) {
            return switch (title) {
                case "Identity" -> "World identity fields.\nIncludes display alias, environment, seed, generator, and difficulty.";
                case "Access" -> "Join and play-state controls.\nCovers visibility, forced game mode, and access behavior.";
                case "Permissions" -> "Permission nodes for entering, bypassing, or managing this world.\nLeave empty when no permission check applies.";
                case "Messages" -> "Player-facing messages.\nShown on join, denial, fallback, or world-specific transitions.";
                case "Rules" -> "Core gameplay toggles.\nControls PvP, saving, spawn loading, and entity spawning.";
                case "Player State" -> "World-specific player state rules.\nControls hunger, healing, respawn behavior, and related state resets.";
                case "Travel" -> "World routing settings.\nControls fallback world and inventory-group selection during transfers.";
                case "Spawn" -> "Custom spawn override.\nIncludes world, X, Y, Z, yaw, and pitch.";
                case "Links" -> "Dimension-style links.\nConnects overworld, nether, and end destinations for travel logic.";
                case "Portal Scale" -> "Portal coordinate scaling.\nUsed when linked dimensions convert travel positions.";
                case "Runtime" -> "Live world locks and isolation.\nControls time, weather, storms, thunder, and runtime isolation.";
                case "Groups" -> "Inventory group editor.\nGroups define which player state is shared across selected worlds.";
                case "Saved Groups" -> "Saved inventory groups for this server.\nSelect one to edit its worlds and preserved state.";
                case "Inventory" -> "Inventory state preserved by this group.\nIncludes main inventory, armor, offhand, and ender chest.";
                case "Location" -> "Location state preserved by this group.\nIncludes effects, last location, and bed spawn.";
                case "Actions" -> "Editor actions for the current world or group.\nSave applies changes; cancel discards the draft.";
                case "Worlds" -> "Worlds included by this selector or inventory group.\nEmpty selections depend on the surrounding field behavior.";
                default -> "";
            };
        }

        private void addDetailWidget(AnimatedWidget widget) {
            detailPane.addWidget(widget);
            detailWidgets.add(widget);
        }

        private TextInputWidget detailInput(String placeholder, String value, int width) {
            TextInputWidget input = new TextInputWidget.Builder()
                .placeholder(placeholder)
                .forcePlaceholder(false)
                .text(safeText(value))
                .size(Math.max(60, width), 20)
                .build();
            input.entranceAnimationEnabled = false;
            return input;
        }

        private RowWidget detailPicker(TextInputWidget input, int width, List<String> options, Consumer<String> onSelected) {
            input.setWidth(Math.max(60, width - 24));
            SquareButtonWidget picker = new SquareButtonWidget.Builder()
                .imagePath("search.png")
                .size(18, 18)
                .hint("Select")
                .entranceAnimation(false)
                .onClick(() -> showOptionSelector(input, options, onSelected))
                .build();
            RowWidget row = new RowWidget.Builder()
                .size(Math.max(80, width), 20)
                .padding(2)
                .addWidget(input)
                .addWidget(picker)
                .build();
            row.entranceAnimationEnabled = false;
            return row;
        }

        private OptionField optionField(List<String> options, String selected, int width) {
            return optionField(options, selected, width, this::worldOptionLabel);
        }

        private OptionField optionField(List<String> options, String selected, int width, Function<String, String> labeler) {
            return new OptionField(options, selected, width, labeler);
        }

        private final class OptionField {
            private final List<String> options;
            private final AnimatedButton button;
            private final Function<String, String> labeler;
            private String value;

            private OptionField(List<String> options, String selected, int width, Function<String, String> labeler) {
                this.options = options == null ? List.of() : options.stream()
                    .filter(option -> option != null && !option.isBlank())
                    .distinct()
                    .toList();
                this.labeler = labeler == null ? value -> value : labeler;
                value = resolveOptionValue(this.options, selected);
                button = new AnimatedButton.Builder()
                    .label(optionLabel(value))
                    .size(Math.max(80, width), 20)
                    .entranceAnimation(false)
                    .build();
                button.setAction(() -> showOptionSelector(button, this.options, this::setValue));
            }

            private AnimatedButton button() {
                return button;
            }

            private String value() {
                return value;
            }

            private void setValue(String value) {
                String resolved = resolveOptionValue(options, value);
                if (!Objects.equals(this.value, resolved)) {
                    this.value = resolved;
                    button.setMessage(optionLabel(resolved));
                }
            }

            private String optionLabel(String value) {
                String label = labeler.apply(safeText(value));
                return label == null || label.isBlank() ? "None" : label;
            }
        }

        private String resolveOptionValue(List<String> options, String value) {
            if (options == null || options.isEmpty()) {
                return safeText(value);
            }
            String text = safeText(value);
            for (String option : options) {
                if (safeText(option).equalsIgnoreCase(text)) {
                    return option;
                }
            }
            return options.getFirst();
        }

        private String worldOptionLabel(String value) {
            if (value == null || value.isBlank()) {
                return "None";
            }
            String cleaned = value.trim().replace('_', ' ').replace('-', ' ');
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

        private void showOptionSelector(AnimatedWidget anchor, List<String> options, Consumer<String> onSelected) {
            if (anchor == null || onSelected == null) {
                return;
            }
            List<String> choices = options == null ? List.of() : options.stream()
                .filter(option -> option != null && !option.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
            if (choices.isEmpty()) {
                return;
            }
            closeOptionSelector();
            var overlay = ScreenManager.getInstance().getPopupOverlay();
            ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
            ItemSelectorWidget selector = new ItemSelectorWidget.Builder(overlay)
                .size(220, 240)
                .dismissOnSelect(true)
                .onClose(() -> closeOptionSelector(selectorRef[0]))
                .build();
            selector.setLayer(900);
            selector.setPriority(30);
            selectorRef[0] = selector;
            for (String option : choices) {
                selector.addItem(option, () -> onSelected.accept(option));
            }
            activeOptionSelector = selector;
            overlay.addDrawableChild(selector);
            selector.show(anchor.getX(), anchor.getY() + anchor.getHeight());
        }

        private void closeOptionSelector() {
            closeOptionSelector(activeOptionSelector);
        }

        private void closeOptionSelector(ItemSelectorWidget selector) {
            if (selector == null) {
                return;
            }
            selector.onClose = null;
            selector.hide();
            ScreenManager.getInstance().getPopupOverlay().remove(selector);
            if (selector == activeOptionSelector) {
                activeOptionSelector = null;
            }
        }

        private ToggleWidget detailToggle(String label, boolean value, int width) {
            return new ToggleWidget.Builder()
                .label(label)
                .toggled(value)
                .size(width, 20)
                .entranceAnimation(false)
                .build();
        }

        private IconButton saveWorldButton(int rowWidth, WorldRegistryEntry world, TextInputWidget alias, OptionField difficulty,
                                           ToggleWidget hidden, ToggleWidget forceGameMode, OptionField gameMode, ToggleWidget pvp,
                                           ToggleWidget autoSave, ToggleWidget keepSpawn, ToggleWidget animals, ToggleWidget monsters,
                                           ToggleWidget hunger, ToggleWidget autoHeal, ToggleWidget bedRespawn, ToggleWidget anchorRespawn,
                                           ToggleWidget miscSpawns, TextInputWidget accessPermission, TextInputWidget bypassPermission,
                                           TextInputWidget arrivalMessage, TextInputWidget denyMessage, TextInputWidget respawnWorld,
                                           TextInputWidget inventoryGroup, ToggleWidget customSpawn, TextInputWidget spawnX, TextInputWidget spawnY,
                                           TextInputWidget spawnZ, TextInputWidget spawnYaw, TextInputWidget spawnPitch, TextInputWidget netherWorld,
                                           TextInputWidget endWorld, TextInputWidget overworld, TextInputWidget netherScale, TextInputWidget endScale,
                                           ToggleWidget autoNether, ToggleWidget autoEnd, ToggleWidget isolated, ToggleWidget timeLock,
                                           TextInputWidget lockedTime, ToggleWidget weatherLock, ToggleWidget storm, ToggleWidget thundering) {
            return new IconButton.Builder()
                .label("Save World")
                .imagePath("save.png")
                .accentType(ThemeManager.getAccent("nice"))
                .size(rowWidth, 22)
                .entranceAnimation(false)
                .onClick(() -> saveWorld(world, alias, difficulty, hidden, forceGameMode, gameMode, pvp, autoSave, keepSpawn, animals,
                    monsters, hunger, autoHeal, bedRespawn, anchorRespawn, miscSpawns, accessPermission, bypassPermission, arrivalMessage,
                    denyMessage, respawnWorld, inventoryGroup, customSpawn, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, netherWorld,
                    endWorld, overworld, netherScale, endScale, autoNether, autoEnd, isolated, timeLock, lockedTime, weatherLock, storm,
                    thundering))
                .build();
        }

        private void saveWorld(WorldRegistryEntry world, TextInputWidget alias, OptionField difficulty, ToggleWidget hidden,
                               ToggleWidget forceGameMode, OptionField gameMode, ToggleWidget pvp, ToggleWidget autoSave,
                               ToggleWidget keepSpawn, ToggleWidget animals, ToggleWidget monsters, ToggleWidget hunger, ToggleWidget autoHeal,
                               ToggleWidget bedRespawn, ToggleWidget anchorRespawn, ToggleWidget miscSpawns, TextInputWidget accessPermission,
                               TextInputWidget bypassPermission, TextInputWidget arrivalMessage, TextInputWidget denyMessage,
                               TextInputWidget respawnWorld, TextInputWidget inventoryGroup, ToggleWidget customSpawn, TextInputWidget spawnX,
                               TextInputWidget spawnY, TextInputWidget spawnZ, TextInputWidget spawnYaw, TextInputWidget spawnPitch,
                               TextInputWidget netherWorld, TextInputWidget endWorld, TextInputWidget overworld, TextInputWidget netherScale,
                               TextInputWidget endScale, ToggleWidget autoNether, ToggleWidget autoEnd, ToggleWidget isolated,
                               ToggleWidget timeLock, TextInputWidget lockedTime, ToggleWidget weatherLock, ToggleWidget storm,
                               ToggleWidget thundering) {
            FlowManager manager = worldManager();
            if (manager == null || world == null) {
                return;
            }
            Double parsedSpawnX = parseNullableDouble(spawnX.getText());
            Double parsedSpawnY = parseNullableDouble(spawnY.getText());
            Double parsedSpawnZ = parseNullableDouble(spawnZ.getText());
            Float parsedSpawnYaw = parseNullableFloat(spawnYaw.getText());
            Float parsedSpawnPitch = parseNullableFloat(spawnPitch.getText());
            Double parsedNetherScale = parseNullableDouble(netherScale.getText());
            Double parsedEndScale = parseNullableDouble(endScale.getText());
            Long parsedLockedTime = parseNullableLong(lockedTime.getText());
            if (parsedSpawnX == null || parsedSpawnY == null || parsedSpawnZ == null || parsedSpawnYaw == null || parsedSpawnPitch == null) {
                new Notification("World", "Invalid Spawn", Notification.Type.ERROR);
                return;
            }
            if (parsedNetherScale == null || parsedNetherScale <= 0.0 || parsedEndScale == null || parsedEndScale <= 0.0) {
                new Notification("World", "Invalid Portal Scale", Notification.Type.ERROR);
                return;
            }
            if (parsedLockedTime == null || parsedLockedTime < 0L) {
                new Notification("World", "Invalid Time", Notification.Type.ERROR);
                return;
            }
            WorldProfileSettings profile = new WorldProfileSettings();
            profile.setAlias(alias.getText());
            profile.setHidden(hidden.getValue());
            profile.setAccessPermission(accessPermission.getText());
            profile.setBypassPermission(bypassPermission.getText());
            profile.setRespawnWorld(respawnWorld.getText());
            profile.setForceGameMode(forceGameMode.getValue());
            profile.setGameMode(safeText(gameMode.value()));
            profile.setCustomSpawnEnabled(customSpawn.getValue());
            profile.setSpawnX(parsedSpawnX);
            profile.setSpawnY(parsedSpawnY);
            profile.setSpawnZ(parsedSpawnZ);
            profile.setSpawnYaw(parsedSpawnYaw);
            profile.setSpawnPitch(parsedSpawnPitch);
            profile.setPvpEnabled(pvp.getValue());
            profile.setKeepSpawnLoaded(keepSpawn.getValue());
            profile.setAutoSaveEnabled(autoSave.getValue());
            profile.setAnimalSpawnsEnabled(animals.getValue());
            profile.setMonsterSpawnsEnabled(monsters.getValue());
            profile.setHungerEnabled(hunger.getValue());
            profile.setAutoHealEnabled(autoHeal.getValue());
            profile.setBedRespawnEnabled(bedRespawn.getValue());
            profile.setAnchorRespawnEnabled(anchorRespawn.getValue());
            profile.setNonLivingEntitySpawnsEnabled(miscSpawns.getValue());
            profile.setArrivalMessage(arrivalMessage.getText());
            profile.setDenyMessage(denyMessage.getText());
            profile.setInventoryGroupId(inventoryGroup.getText());
            profile.setLinkedNetherWorld(netherWorld.getText());
            profile.setLinkedEndWorld(endWorld.getText());
            profile.setLinkedOverworld(overworld.getText());
            profile.setNetherScale(parsedNetherScale);
            profile.setEndScale(parsedEndScale);
            profile.setAutoLinkNetherPortal(autoNether.getValue());
            profile.setAutoLinkEndPortal(autoEnd.getValue());
            String worldName = world.getWorldName();
            if (!safeText(difficulty.value()).equalsIgnoreCase(safeText(world.getDifficulty()))) {
                manager.suppressNextWorldSuccessNotification(serverId, "setDifficulty");
                manager.setWorldDifficulty(serverId, worldName, safeText(difficulty.value()));
            }
            manager.suppressNextWorldSuccessNotification(serverId, "setWorldProfile");
            manager.setWorldProfile(serverId, worldName, profile);
            if (isolated.getValue() != world.isIsolatedPlayerState()) {
                manager.suppressNextWorldSuccessNotification(serverId, "setIsolatedPlayerState");
                manager.setWorldIsolatedState(serverId, worldName, isolated.getValue());
            }
            if (timeLock.getValue() != world.isTimeLockEnabled() || parsedLockedTime != world.getLockedTime()) {
                manager.suppressNextWorldSuccessNotification(serverId, "setTimeLock");
                manager.setWorldTimeLock(serverId, worldName, timeLock.getValue(), parsedLockedTime);
            }
            if (weatherLock.getValue() != world.isWeatherLockEnabled() || storm.getValue() != world.isLockedStorm() || thundering.getValue() != world.isLockedThundering()) {
                manager.suppressNextWorldSuccessNotification(serverId, "setWeatherLock");
                manager.setWorldWeatherLock(serverId, worldName, weatherLock.getValue(), storm.getValue(), thundering.getValue());
            }
            new Notification("ReSync", "World Saved", Notification.Type.SUCCESS);
        }

        private IconButton emptyDetailButton() {
            IconButton button = new IconButton.Builder()
                .label("No Worlds")
                .imagePath("earth.png")
                .size(Math.max(160, detailPane.getWidth() - 18), 22)
                .entranceAnimation(false)
                .build();
            button.active = false;
            return button;
        }

        private void showWorldActionsMenu(String worldName, SquareButtonWidget anchor) {
            FlowManager manager = worldManager();
            if (manager == null || anchor == null || worldName == null || worldName.isBlank()) {
                return;
            }
            ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(FlowGraphDesignerScreen.this)
                .addHeaderButton("map.png", () -> manager.openWorldMap(serverId, null, worldName), "Open Map")
                .addHeaderButton("steve.png", () -> showWorldTeleportPopup(worldName), "Teleport Player")
                .addHeaderButton("search.png", () -> manager.whoWorld(serverId, worldName), "View Players")
                .addIconItem("Clone World", "copy.png", () -> showCloneWorldPopup(worldName), "")
                .addIconItem("Purge Entities", "delete.png", () -> showWorldPurgePopup(worldName), "", ThemeManager.getAccent("calm"))
                .addIconItem("Delete World", "delete.png", () -> showWorldDeletePopup(worldName), "", ThemeManager.getAccent("danger"));
            showContextMenu(anchor.getX() + anchor.getWidth() + 4, anchor.getY() + anchor.getHeight(), builder);
        }

        private void showCreateWorldPopup() {
            FlowManager manager = worldManager();
            if (manager == null) {
                return;
            }
            WorldGenManager.getInstance().requestProjectList(serverId);
            WorldSnapshot snapshot = manager.getWorldSnapshot(serverId);
            List<WorldGeneratorDescriptor> generatorDescriptors = snapshot == null ? List.of() : snapshot.getGeneratorDescriptors();
            TextInputWidget worldInput = new TextInputWidget.Builder().placeholder("World Name").size(220, 18).build();
            TextInputWidget seedInput = new TextInputWidget.Builder().placeholder("Seed").size(220, 18).build();
            OptionField environmentSelect = optionField(List.of("NORMAL", "NETHER", "THE_END", "CUSTOM"), "NORMAL", 220);
            List<GeneratorOption> generatorOptions = createGeneratorOptions(generatorDescriptors);
            List<String> generatorLabels = generatorOptions.stream().map(GeneratorOption::label).toList();
            OptionField generatorSelect = optionField(generatorLabels, generatorOptions.getFirst().label(), 220, value -> value);
            TextInputWidget generatorConfig = new TextInputWidget.Builder().placeholder("Generator Config").size(220, 18).build();
            GeneratorOption[] selectedGenerator = new GeneratorOption[] {generatorOptionByLabel(generatorOptions, generatorSelect.value())};
            applyGeneratorOption(selectedGenerator[0], generatorConfig);
            generatorSelect.button().setAction(() -> showOptionSelector(generatorSelect.button(), generatorLabels, value -> {
                generatorSelect.setValue(value);
                selectedGenerator[0] = generatorOptionByLabel(generatorOptions, value);
                applyGeneratorOption(selectedGenerator[0], generatorConfig);
            }));
            PopupWidget.Builder builder = new PopupWidget.Builder("Create World")
                .setResizable(false)
                .setAntiOutOfBound(true)
                .setBoundOffset(desktopMode ? 35 : 0)
                .size(420, 220);
            builder.addRow("World", true, 18, worldInput);
            builder.addRow("Seed", true, 18, seedInput);
            builder.addRow("Environment", true, 18, environmentSelect.button());
            builder.addRow("Generator", true, 18, generatorSelect.button());
            builder.addRow("Config", true, 18, generatorConfig);
            PopupWidget[] popupRef = new PopupWidget[1];
            IconButton createButton = new IconButton.Builder()
                .label("Create")
                .imagePath("create.png")
                .accentType(ThemeManager.getAccent("nice"))
                .size(110, 20)
                .onClick(() -> {
                    String worldName = safeText(worldInput.getText()).trim();
                    if (!WorldUiSupport.isValidSimpleId(worldName)) {
                        new Notification("World", "Invalid World Name", Notification.Type.ERROR);
                        return;
                    }
                    if (WorldUiSupport.containsIgnoreCase(worldNameOptions(), worldName)) {
                        new Notification("World", "World Exists", Notification.Type.ERROR);
                        return;
                    }
                    GeneratorOption generatorOption = selectedGenerator[0];
                    manager.createWorld(serverId, worldName, seedInput.getText(), safeText(environmentSelect.value()),
                        generatorOption == null ? "" : generatorOption.generator(), generatorConfig.getText());
                    selectedWorldName = worldName;
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                })
                .build();
            builder.addRow("", true, 20, createButton);
            popupRef[0] = builder.build();
            addDrawableChild(popupRef[0]);
            popupRef[0].show();
        }

        private List<GeneratorOption> createGeneratorOptions(List<WorldGeneratorDescriptor> descriptors) {
            List<GeneratorOption> options = new ArrayList<>();
            options.add(new GeneratorOption("Default", "", "", false));
            for (WorldGeneratorDescriptor descriptor : descriptors) {
                if (descriptor == null || safeText(descriptor.getId()).isBlank()) {
                    continue;
                }
                String label = safeText(descriptor.getDisplayName()).isBlank() ? descriptor.getId() : descriptor.getDisplayName();
                addGeneratorOption(options, new GeneratorOption(label, descriptor.getId(), descriptor.getDefaultConfig(), descriptor.isConfigurable()));
            }
            Set<String> projectIds = new LinkedHashSet<>(WorldGenManager.getInstance().getProjectIds(serverId));
            ReSyncProjectMetadata metadata = worldManager().getProjectMetadata(serverId);
            for (ReSyncProjectMetadata.ResourceEntry resource : metadata.getResources()) {
                if (resource != null && ReSyncResourceDragPayload.WORLDGEN.equals(resource.getType()) && !safeText(resource.getId()).isBlank()) {
                    projectIds.add(resource.getId());
                }
            }
            List<String> sortedProjectIds = new ArrayList<>(projectIds);
            sortedProjectIds.sort(String.CASE_INSENSITIVE_ORDER);
            for (String projectId : sortedProjectIds) {
                if (!safeText(projectId).isBlank()) {
                    addGeneratorOption(options, new GeneratorOption("WorldGen: " + projectId, "worldgen_project", projectId, true));
                }
            }
            return options;
        }

        private void addGeneratorOption(List<GeneratorOption> options, GeneratorOption option) {
            for (GeneratorOption existing : options) {
                if (safeText(existing.label()).equalsIgnoreCase(safeText(option.label()))) {
                    return;
                }
            }
            options.add(option);
        }

        private GeneratorOption generatorOptionByLabel(List<GeneratorOption> options, String label) {
            if (options == null || options.isEmpty()) {
                return null;
            }
            for (GeneratorOption option : options) {
                if (safeText(option.label()).equalsIgnoreCase(safeText(label))) {
                    return option;
                }
            }
            return options.getFirst();
        }

        private void applyGeneratorOption(GeneratorOption option, TextInputWidget generatorConfig) {
            if (generatorConfig == null) {
                return;
            }
            boolean configurable = option != null && option.configurable();
            generatorConfig.active = configurable;
            if (!generatorConfig.isFocused() || !configurable) {
                generatorConfig.setText(configurable ? safeText(option.config()) : "");
            }
        }

        private final class GeneratorOption {
            private final String label;
            private final String generator;
            private final String config;
            private final boolean configurable;

            private GeneratorOption(String label, String generator, String config, boolean configurable) {
                this.label = label;
                this.generator = generator;
                this.config = config;
                this.configurable = configurable;
            }

            private String label() {
                return label;
            }

            private String generator() {
                return generator;
            }

            private String config() {
                return config;
            }

            private boolean configurable() {
                return configurable;
            }
        }

        private void showCloneWorldPopup(String sourceWorld) {
            PopupWidget.Builder builder = new PopupWidget.Builder("Clone World")
                .setResizable(false)
                .setAntiOutOfBound(true)
                .setBoundOffset(desktopMode ? 35 : 0)
                .size(400, 135);
            TextInputWidget worldInput = new TextInputWidget.Builder().placeholder("Target World").size(220, 18).build();
            ToggleWidget loadAfter = detailToggle("Load After", true, 100);
            builder.addRow("Target", true, 18, worldInput);
            builder.addRow("", true, 18, loadAfter);
            PopupWidget[] popupRef = new PopupWidget[1];
            IconButton cloneButton = new IconButton.Builder()
                .label("Clone")
                .imagePath("copy.png")
                .accentType(ThemeManager.getAccent("nice"))
                .size(110, 20)
                .onClick(() -> {
                    String targetWorld = safeText(worldInput.getText()).trim();
                    if (!WorldUiSupport.isValidSimpleId(targetWorld)) {
                        new Notification("World", "Invalid World Name", Notification.Type.ERROR);
                        return;
                    }
                    if (WorldUiSupport.containsIgnoreCase(worldNameOptions(), targetWorld)) {
                        new Notification("World", "World Exists", Notification.Type.ERROR);
                        return;
                    }
                    worldManager().cloneWorld(serverId, sourceWorld, targetWorld, loadAfter.getValue());
                    selectedWorldName = targetWorld;
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                })
                .build();
            builder.addRow("", true, 20, cloneButton);
            popupRef[0] = builder.build();
            addDrawableChild(popupRef[0]);
            popupRef[0].show();
        }

        private void showWorldUnloadPopup(String worldName) {
            showFallbackPopup(worldName, fallbackWorld -> worldManager().unloadWorld(serverId, worldName, fallbackWorld));
        }

        private void showWorldDeletePopup(String worldName) {
            FlowManager manager = worldManager();
            if (manager == null) {
                return;
            }
            List<String> fallbackOptions = fallbackWorldOptions(worldName);
            PopupWidget.Builder builder = fallbackBuilder("Delete World", worldName, 170);
            ToggleWidget deleteFiles = detailToggle("Delete Files", false, 115);
            TextInputWidget fallbackInput = new TextInputWidget.Builder()
                .text(selectDefaultFallbackWorld(worldName, fallbackOptions))
                .placeholder("Fallback World")
                .size(220, 18)
                .build();
            builder.addRow("Files", true, 18, deleteFiles);
            builder.addRow("Fallback", true, 18, fallbackInput);
            PopupWidget[] popupRef = new PopupWidget[1];
            IconButton deleteButton = new IconButton.Builder()
                .label("Delete")
                .imagePath("delete.png")
                .accentType(ThemeManager.getAccent("danger"))
                .size(110, 20)
                .onClick(() -> {
                    String fallbackWorld = safeText(fallbackInput.getText()).trim();
                    if (!WorldUiSupport.containsIgnoreCase(fallbackOptions, fallbackWorld)) {
                        new Notification("World", "Unknown Fallback World", Notification.Type.ERROR);
                        return;
                    }
                    manager.deleteWorld(serverId, worldName, deleteFiles.getValue(), fallbackWorld);
                    if (worldName.equalsIgnoreCase(selectedWorldName)) {
                        selectedWorldName = fallbackWorld;
                    }
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                })
                .build();
            builder.addRow("", true, 20, deleteButton);
            popupRef[0] = builder.build();
            addDrawableChild(popupRef[0]);
            popupRef[0].show();
        }

        private void showFallbackPopup(String worldName, Consumer<String> action) {
            List<String> fallbackOptions = fallbackWorldOptions(worldName);
            PopupWidget.Builder builder = fallbackBuilder("Unload World", worldName, 130);
            TextInputWidget fallbackInput = new TextInputWidget.Builder()
                .text(selectDefaultFallbackWorld(worldName, fallbackOptions))
                .placeholder("Fallback World")
                .size(220, 18)
                .build();
            builder.addRow("Fallback", true, 18, fallbackInput);
            PopupWidget[] popupRef = new PopupWidget[1];
            IconButton actionButton = new IconButton.Builder()
                .label("Unload")
                .imagePath("hide.png")
                .accentType(ThemeManager.getAccent("danger"))
                .size(110, 20)
                .onClick(() -> {
                    String fallbackWorld = safeText(fallbackInput.getText()).trim();
                    if (!WorldUiSupport.containsIgnoreCase(fallbackOptions, fallbackWorld)) {
                        new Notification("World", "Unknown Fallback World", Notification.Type.ERROR);
                        return;
                    }
                    action.accept(fallbackWorld);
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                })
                .build();
            builder.addRow("", true, 20, actionButton);
            popupRef[0] = builder.build();
            addDrawableChild(popupRef[0]);
            popupRef[0].show();
        }

        private PopupWidget.Builder fallbackBuilder(String title, String worldName, int height) {
            return new PopupWidget.Builder(title + " | " + worldName)
                .setResizable(false)
                .setAntiOutOfBound(true)
                .setBoundOffset(desktopMode ? 35 : 0)
                .size(430, height);
        }

        private void showWorldPurgePopup(String worldName) {
            PopupWidget.Builder builder = new PopupWidget.Builder("Purge Entities")
                .setResizable(false)
                .setAntiOutOfBound(true)
                .setBoundOffset(desktopMode ? 35 : 0)
                .size(420, 190);
            ToggleWidget monsters = detailToggle("Monsters", true, 96);
            ToggleWidget animals = detailToggle("Animals", false, 90);
            ToggleWidget ambient = detailToggle("Ambient", false, 90);
            ToggleWidget misc = detailToggle("Misc", false, 80);
            ToggleWidget vehicles = detailToggle("Vehicles", false, 92);
            ToggleWidget items = detailToggle("Items", false, 80);
            builder.addRow("Types", true, 18, monsters, animals, ambient);
            builder.addRow("More", true, 18, misc, vehicles, items);
            PopupWidget[] popupRef = new PopupWidget[1];
            IconButton purgeButton = new IconButton.Builder()
                .label("Purge")
                .imagePath("delete.png")
                .accentType(ThemeManager.getAccent("danger"))
                .size(110, 20)
                .onClick(() -> {
                    if (!monsters.getValue() && !animals.getValue() && !ambient.getValue() && !misc.getValue() && !vehicles.getValue() && !items.getValue()) {
                        new Notification("World", "Select Purge Types", Notification.Type.ERROR);
                        return;
                    }
                    worldManager().purgeWorld(serverId, worldName, monsters.getValue(), animals.getValue(), ambient.getValue(), misc.getValue(), vehicles.getValue(), items.getValue());
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                })
                .build();
            builder.addRow("", true, 20, purgeButton);
            popupRef[0] = builder.build();
            addDrawableChild(popupRef[0]);
            popupRef[0].show();
        }

        private void showWorldTeleportPopup(String worldName) {
            PopupWidget.Builder builder = new PopupWidget.Builder("Teleport Player")
                .onClose(this::closePlayerSelector)
                .setResizable(false)
                .setAntiOutOfBound(true)
                .setBoundOffset(desktopMode ? 35 : 0)
                .size(440, 205);
            String[] selectedPlayer = {""};
            AnimatedButton playerButton = new AnimatedButton.Builder()
                .label("Select Player")
                .size(220, 18)
                .entranceAnimation(false)
                .build();
            playerButton.setAction(() -> showPlayerSelector(playerButton, selectedPlayer[0], player -> {
                selectedPlayer[0] = player;
                playerButton.setMessage(player.isBlank() ? "Select Player" : player);
            }));
            TextInputWidget xInput = new TextInputWidget.Builder().placeholder("X").size(82, 18).build();
            TextInputWidget yInput = new TextInputWidget.Builder().placeholder("Y").size(82, 18).build();
            TextInputWidget zInput = new TextInputWidget.Builder().placeholder("Z").size(82, 18).build();
            TextInputWidget yawInput = new TextInputWidget.Builder().placeholder("Yaw").size(82, 18).build();
            TextInputWidget pitchInput = new TextInputWidget.Builder().placeholder("Pitch").size(82, 18).build();
            builder.addRow("Player", true, 18, playerButton);
            builder.addRow("Position", true, 18, xInput, yInput, zInput);
            builder.addRow("Rotation", true, 18, yawInput, pitchInput);
            PopupWidget[] popupRef = new PopupWidget[1];
            IconButton spawnButton = new IconButton.Builder()
                .label("Spawn")
                .imagePath("earth.png")
                .size(105, 20)
                .onClick(() -> {
                    String playerName = safeText(selectedPlayer[0]).trim();
                    if (playerName.isBlank()) {
                        new Notification("World", "Player Required", Notification.Type.ERROR);
                        return;
                    }
                    worldManager().teleportPlayerToWorldSpawn(serverId, playerName, worldName);
                    closePlayerSelector();
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                })
                .build();
            IconButton teleportButton = new IconButton.Builder()
                .label("Teleport")
                .imagePath("steve.png")
                .accentType(ThemeManager.getAccent("nice"))
                .size(120, 20)
                .onClick(() -> {
                    String playerName = safeText(selectedPlayer[0]).trim();
                    if (playerName.isBlank()) {
                        new Notification("World", "Player Required", Notification.Type.ERROR);
                        return;
                    }
                    if (!WorldUiSupport.isCompleteOrBlank(xInput.getText(), yInput.getText(), zInput.getText())
                        || !WorldUiSupport.isCompleteOrBlank(yawInput.getText(), pitchInput.getText())) {
                        new Notification("World", "Complete Coordinates", Notification.Type.ERROR);
                        return;
                    }
                    worldManager().teleportPlayerToWorld(serverId, playerName, worldName, parseNullableDouble(xInput.getText()),
                        parseNullableDouble(yInput.getText()), parseNullableDouble(zInput.getText()), parseNullableFloat(yawInput.getText()),
                        parseNullableFloat(pitchInput.getText()));
                    closePlayerSelector();
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                })
                .build();
            builder.addRow("", true, 20, spawnButton, teleportButton);
            popupRef[0] = builder.build();
            addDrawableChild(popupRef[0]);
            popupRef[0].show();
        }

        private void showPlayerSelector(AnimatedWidget anchor, String selected, Consumer<String> onSelected) {
            FlowManager manager = worldManager();
            if (manager == null || anchor == null || onSelected == null) {
                return;
            }
            closePlayerSelector();
            manager.requestPlayerTrackingSnapshot(serverId);
            List<PlayerDossier> players = manager.getOnlinePlayersForServer(serverId);
            var overlay = ScreenManager.getInstance().getPopupOverlay();
            ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
            ItemSelectorWidget selector = new ItemSelectorWidget.Builder(overlay)
                .size(220, 240)
                .dismissOnSelect(true)
                .emptyMessage("No Players")
                .onClose(() -> closePlayerSelector(selectorRef[0]))
                .build();
            selector.setLayer(900);
            selector.setPriority(30);
            selectorRef[0] = selector;
            activePlayerSelector = selector;
            for (PlayerDossier player : players) {
                if (player == null || safeText(player.getPlayerName()).isBlank()) {
                    continue;
                }
                String name = player.getPlayerName();
                String uuid = safeText(player.getPlayerId());
                selector.addItem(name, uuid, name + " " + uuid, () -> onSelected.accept(name));
            }
            selector.setSelectedItem(selected);
            overlay.addDrawableChild(selector);
            selector.show(anchor.getX(), anchor.getY() + anchor.getHeight());
        }

        private void closePlayerSelector() {
            closePlayerSelector(activePlayerSelector);
        }

        private void closePlayerSelector(ItemSelectorWidget selector) {
            if (selector == null) {
                return;
            }
            selector.onClose = null;
            selector.hide();
            ScreenManager.getInstance().getPopupOverlay().remove(selector);
            if (selector == activePlayerSelector) {
                activePlayerSelector = null;
            }
        }

        private void showInventoryGroupsPopup() {
            FlowManager manager = worldManager();
            if (manager == null) {
                return;
            }
            clearDetailWidgets();
            detailForm = null;
            List<WorldInventoryGroup> groups = new ArrayList<>(manager.getWorldInventoryGroupsForServer(serverId));
            groups.sort(Comparator.comparing(group -> safeText(group == null ? "" : group.getGroupId()), String.CASE_INSENSITIVE_ORDER));
            int rowWidth = Math.max(220, detailPane.getWidth() - 18);
            IconButton createButton = new IconButton.Builder()
                .label("Create")
                .imagePath("create.png")
                .accentType(ThemeManager.getAccent("nice"))
                .size(96, 18)
                .entranceAnimation(false)
                .onClick(() -> showInventoryGroupPopup(null))
                .build();
            IconButton closeButton = new IconButton.Builder()
                .label("Close")
                .imagePath("close.png")
                .size(86, 18)
                .entranceAnimation(false)
                .onClick(this::refreshDetails)
                .build();
            addDetailWidget(row("Groups", rowWidth, createButton, closeButton));
            if (groups.isEmpty()) {
                addDetailWidget(row("Saved Groups", rowWidth, readOnlyButton("No Groups")));
            } else {
                for (WorldInventoryGroup group : groups) {
                    if (group == null || safeText(group.getGroupId()).isBlank()) {
                        continue;
                    }
                    IconButton label = readOnlyButton(inventoryGroupSummary(group));
                    SquareButtonWidget edit = new SquareButtonWidget.Builder()
                        .imagePath("edit.png")
                        .hint("Edit Group")
                        .onClick(() -> showInventoryGroupPopup(group))
                        .build();
                    SquareButtonWidget delete = new SquareButtonWidget.Builder()
                        .imagePath("delete.png")
                        .hint("Delete Group")
                        .accentType(ThemeManager.getAccent("danger"))
                        .onClick(() -> {
                            manager.deleteInventoryGroup(serverId, group.getGroupId());
                            showInventoryGroupsPopup();
                        })
                        .build();
                    addDetailWidget(row(group.getGroupId(), rowWidth, label, edit, delete));
                }
            }
            detailPane.updateWidgetPositions();
        }

        private String inventoryGroupSummary(WorldInventoryGroup group) {
            String display = safeText(group.getDisplayName()).isBlank() ? group.getGroupId() : group.getDisplayName();
            List<String> worlds = group.getWorlds() == null ? List.of() : group.getWorlds();
            return display + " | " + worlds.size() + " Worlds | " + summarizeInventoryGroupShares(group);
        }

        private String summarizeInventoryGroupShares(WorldInventoryGroup group) {
            List<String> parts = new ArrayList<>();
            if (group.isShareInventory()) parts.add("Inventory");
            if (group.isShareArmor()) parts.add("Armor");
            if (group.isShareOffhand()) parts.add("Offhand");
            if (group.isShareEnderChest()) parts.add("Ender Chest");
            if (group.isShareHealth()) parts.add("Health");
            if (group.isShareHunger()) parts.add("Hunger");
            if (group.isShareExperience()) parts.add("Experience");
            if (group.isShareGameMode()) parts.add("Game Mode");
            if (group.isSharePotionEffects()) parts.add("Potions");
            if (group.isShareLastLocation()) parts.add("Last Location");
            if (group.isShareBedSpawn()) parts.add("Bed Spawn");
            return parts.isEmpty() ? "No Shared State" : String.join(", ", parts);
        }

        private void showInventoryGroupPopup(WorldInventoryGroup existingGroup) {
            FlowManager manager = worldManager();
            if (manager == null) {
                return;
            }
            boolean editing = existingGroup != null;
            clearDetailWidgets();
            detailForm = null;
            int rowWidth = Math.max(220, detailPane.getWidth() - 18);
            TextInputWidget displayName = new TextInputWidget.Builder()
                .text(editing ? safeText(existingGroup.getDisplayName()) : "")
                .placeholder("Group Name")
                .forcePlaceholder(false)
                .size(Math.max(220, rowWidth - 12), 20)
                .build();
            displayName.entranceAnimationEnabled = false;
            Set<String> selectedWorlds = new LinkedHashSet<>(editing ? existingGroup.getWorlds() : defaultGroupWorldSelection());
            ToggleWidget inventory = detailToggle("Inventory", !editing || existingGroup.isShareInventory(), 92);
            ToggleWidget armor = detailToggle("Armor", !editing || existingGroup.isShareArmor(), 80);
            ToggleWidget offhand = detailToggle("Offhand", !editing || existingGroup.isShareOffhand(), 88);
            ToggleWidget enderChest = detailToggle("Ender Chest", !editing || existingGroup.isShareEnderChest(), 110);
            ToggleWidget health = detailToggle("Health", !editing || existingGroup.isShareHealth(), 82);
            ToggleWidget hunger = detailToggle("Hunger", !editing || existingGroup.isShareHunger(), 86);
            ToggleWidget experience = detailToggle("Experience", !editing || existingGroup.isShareExperience(), 105);
            ToggleWidget gameMode = detailToggle("Game Mode", !editing || existingGroup.isShareGameMode(), 105);
            ToggleWidget potions = detailToggle("Potions", !editing || existingGroup.isSharePotionEffects(), 90);
            ToggleWidget lastLocation = detailToggle("Last Location", !editing || existingGroup.isShareLastLocation(), 115);
            ToggleWidget bedSpawn = detailToggle("Bed Spawn", !editing || existingGroup.isShareBedSpawn(), 100);
            addDetailWidget(row(editing ? "Edit Group" : "Create Group", rowWidth, displayName));
            addInventoryGroupWorldRow(rowWidth, selectedWorlds);
            addDetailWidget(row("Inventory", rowWidth, inventory, armor, offhand, enderChest));
            addDetailWidget(row("Player State", rowWidth, health, hunger, experience, gameMode));
            addDetailWidget(row("Location", rowWidth, potions, lastLocation, bedSpawn));
            IconButton save = new IconButton.Builder()
                .label(editing ? "Save" : "Create")
                .imagePath("save.png")
                .accentType(ThemeManager.getAccent("nice"))
                .size(90, 18)
                .entranceAnimation(false)
                .onClick(() -> {
                    String name = safeText(displayName.getText()).trim();
                    String id = editing ? safeText(existingGroup.getGroupId()).trim() : inventoryGroupIdFromName(name);
                    if (!editing && name.isBlank()) {
                        new Notification("World", "Group Name Required", Notification.Type.ERROR);
                        return;
                    }
                    if (!WorldUiSupport.isValidSimpleId(id)) {
                        new Notification("World", "Invalid Group Name", Notification.Type.ERROR);
                        return;
                    }
                    if (!editing && manager.getWorldInventoryGroup(serverId, id) != null) {
                        new Notification("World", "Group Exists", Notification.Type.ERROR);
                        return;
                    }
                    WorldInventoryGroup group = new WorldInventoryGroup();
                    group.setGroupId(id);
                    group.setDisplayName(name);
                    group.setWorlds(new ArrayList<>(selectedWorlds));
                    group.setShareInventory(inventory.getValue());
                    group.setShareArmor(armor.getValue());
                    group.setShareOffhand(offhand.getValue());
                    group.setShareEnderChest(enderChest.getValue());
                    group.setShareHealth(health.getValue());
                    group.setShareHunger(hunger.getValue());
                    group.setShareExperience(experience.getValue());
                    group.setShareGameMode(gameMode.getValue());
                    group.setSharePotionEffects(potions.getValue());
                    group.setShareLastLocation(lastLocation.getValue());
                    group.setShareBedSpawn(bedSpawn.getValue());
                    if (editing) {
                        manager.updateInventoryGroup(serverId, group);
                    } else {
                        manager.createInventoryGroup(serverId, group);
                    }
                    showInventoryGroupsPopup();
                })
                .build();
            IconButton cancel = new IconButton.Builder()
                .label("Back")
                .imagePath("close.png")
                .size(84, 18)
                .entranceAnimation(false)
                .onClick(this::showInventoryGroupsPopup)
                .build();
            addDetailWidget(row("Actions", rowWidth, save, cancel));
            detailPane.updateWidgetPositions();
        }

        private void addInventoryGroupWorldRow(int rowWidth, Set<String> selectedWorlds) {
            List<String> worlds = worldNameOptions();
            if (worlds.isEmpty()) {
                addDetailWidget(row("Worlds", rowWidth, readOnlyButton("No Worlds")));
                return;
            }
            int buttonWidth = Math.max(110, (rowWidth - 24) / 3);
            List<ToggleWidget> rowToggles = new ArrayList<>();
            for (String world : worlds) {
                ToggleWidget toggle = new ToggleWidget.Builder()
                    .label(world)
                    .toggled(containsIgnoreCase(selectedWorlds, world))
                    .size(buttonWidth, 20)
                    .entranceAnimation(false)
                    .onChange(value -> {
                        if (value) {
                            selectedWorlds.add(world);
                        } else {
                            selectedWorlds.removeIf(selected -> selected.equalsIgnoreCase(world));
                        }
                    })
                    .build();
                rowToggles.add(toggle);
                if (rowToggles.size() == 3) {
                    addDetailWidget(row("Worlds", rowWidth, rowToggles.toArray(new AnimatedWidget[0])));
                    rowToggles.clear();
                }
            }
            if (!rowToggles.isEmpty()) {
                addDetailWidget(row("Worlds", rowWidth, rowToggles.toArray(new AnimatedWidget[0])));
            }
        }

        private String inventoryGroupIdFromName(String name) {
            String id = safeText(name).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
            return id.isBlank() ? "group" : id;
        }

        private List<String> defaultGroupWorldSelection() {
            return safeText(selectedWorldName).isBlank() ? List.of() : List.of(selectedWorldName);
        }

        @Override
        public void handleWorldOperationResult(WorldOperationResult result) {
            String action = safeText(result.getAction()).trim().toLowerCase(Locale.ROOT);
            if ("deleteworld".equals(action)) {
                String worldName = resultWorldName(result);
                String key = findEntryKeyIgnoreCase(entries, worldName);
                if (key != null) {
                    WorldEntryRow row = entries.remove(key);
                    if (row != null) {
                        worldsList.removeWidget(row.widget);
                    }
                }
            }
            refreshWorlds();
        }

        private List<String> onlinePlayerNames() {
            FlowManager manager = worldManager();
            return manager == null ? List.of() : WorldUiSupport.normalizeUniqueEntries(manager.getOnlinePlayerNamesForServer(serverId));
        }

        private List<String> worldNameOptions() {
            FlowManager manager = worldManager();
            if (manager == null) {
                return List.of();
            }
            List<String> names = new ArrayList<>(manager.getWorldsForServer(serverId).keySet());
            names = WorldUiSupport.normalizeUniqueEntries(names);
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return names;
        }

        private List<String> inventoryGroupOptions() {
            FlowManager manager = worldManager();
            if (manager == null) {
                return List.of("No Group");
            }
            List<String> groups = new ArrayList<>();
            groups.add("No Group");
            for (WorldInventoryGroup group : manager.getWorldInventoryGroupsForServer(serverId)) {
                if (group != null && !safeText(group.getGroupId()).isBlank()) {
                    groups.add(group.getGroupId());
                }
            }
            return WorldUiSupport.normalizeUniqueEntries(groups);
        }

        private List<String> fallbackWorldOptions(String worldName) {
            List<String> options = new ArrayList<>(worldNameOptions());
            options.removeIf(option -> option != null && option.equalsIgnoreCase(worldName));
            return options;
        }

        private String selectDefaultFallbackWorld(String worldName, List<String> fallbackOptions) {
            if (fallbackOptions == null || fallbackOptions.isEmpty()) {
                return "";
            }
            WorldRegistryEntry world = worldManager() != null ? worldManager().getWorld(serverId, worldName) : null;
            WorldProfileSettings profile = world == null ? null : world.getProfileSettings();
            List<String> preferred = new ArrayList<>();
            if (profile != null) {
                preferred.add(profile.getRespawnWorld());
                preferred.add(profile.getLinkedOverworld());
            }
            preferred.add("world");
            preferred.add("overworld");
            for (String candidate : preferred) {
                for (String option : fallbackOptions) {
                    if (option != null && option.equalsIgnoreCase(safeText(candidate))) {
                        return option;
                    }
                }
            }
            return fallbackOptions.getFirst();
        }
    }

    private WorldDashboardEntry findWorldDashboard(String worldName) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || worldName == null) {
            return null;
        }
        for (WorldDashboardEntry entry : manager.getWorldDashboardForServer(serverId)) {
            if (entry != null && entry.getWorldName() != null && entry.getWorldName().equalsIgnoreCase(worldName)) {
                return entry;
            }
        }
        return null;
    }

    private String findEntryKeyIgnoreCase(Map<String, ?> entries, String target) {
        if (entries == null || target == null || target.isBlank()) {
            return null;
        }
        for (String key : entries.keySet()) {
            if (key != null && key.equalsIgnoreCase(target)) {
                return key;
            }
        }
        return null;
    }

    private String resultWorldName(WorldOperationResult result) {
        return resultDataText(result);
    }

    private String resultDataText(WorldOperationResult result) {
        if (result == null) {
            return "";
        }
        if (result.getData() != null) {
            for (String key : List.of("worldName", "world")) {
                Object raw = result.getData().get(key);
                String value = raw == null ? "" : String.valueOf(raw).trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return safeText(result.getWorldName()).trim();
    }

    private void showWorldAuditSnapshot(JsonElement data) {
        JsonArray records = data != null && data.isJsonArray() ? data.getAsJsonArray() : new JsonArray();
        PopupWidget.Builder builder = new PopupWidget.Builder("World History")
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(560, Math.min(330, 120 + Math.min(records.size(), 9) * 22));
        if (records.isEmpty()) {
            builder.addRow("Status", true, 18, readOnlyButton("No Operations"));
        } else {
            int shown = 0;
            for (JsonElement element : records) {
                if (shown >= 9) {
                    break;
                }
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                boolean success = object.has("success") && !object.get("success").isJsonNull() && object.get("success").getAsBoolean();
                String status = success ? "Ok" : "Failed";
                String action = formatTitleWords(jsonText(object, "action").isBlank() ? "Operation" : jsonText(object, "action"));
                String world = jsonText(object, "targetWorld");
                String message = success ? jsonText(object, "message") : jsonText(object, "failureReason");
                long duration = object.has("durationMillis") && !object.get("durationMillis").isJsonNull() ? object.get("durationMillis").getAsLong() : 0L;
                String text = action + (world.isBlank() ? "" : " | " + world) + " | " + duration + "ms" + (message.isBlank() ? "" : " | " + message);
                builder.addRow(status, true, 18, readOnlyButton(text.length() > 72 ? text.substring(0, 69) + "..." : text));
                shown++;
            }
        }
        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    private void showWorldWhoPopup(WorldOperationResult result) {
        String worldName = resultWorldName(result);
        PopupWidget.Builder builder = new PopupWidget.Builder(worldName.isBlank() ? "World Players" : "Players In " + worldName)
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(520, 220);
        Object rawPlayers = result != null && result.getData() != null ? result.getData().get("players") : null;
        List<?> players = rawPlayers instanceof List<?> list ? list : List.of();
        if (players.isEmpty()) {
            builder.addRow("Players", true, 18, readOnlyButton("No Players"));
        } else {
            int shown = 0;
            for (Object player : players) {
                if (shown >= 8) {
                    break;
                }
                builder.addRow("Player", true, 18, readOnlyButton(String.valueOf(player)));
                shown++;
            }
        }
        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    private IconButton readOnlyButton(String text) {
        IconButton button = new IconButton.Builder()
            .label(text)
            .autoWidthOnTextChange(true)
            .entranceAnimation(false)
            .build();
        button.active = false;
        return button;
    }

    private String jsonText(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return safeText(object.get(key).getAsString());
    }

    private boolean jsonBool(JsonObject object, String key, boolean fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<String> parseCommaSeparatedList(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        List<String> values = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return WorldUiSupport.normalizeUniqueEntries(values);
    }

    private String formatTitleWords(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('_', ' ').replace('-', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
        StringBuilder builder = new StringBuilder();
        for (String part : normalized.trim().split("\\s+")) {
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
        return builder.toString();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private int textWidth(String value) {
        String clean = safeText(value).replaceAll("(?i)[&§][0-9a-fk-or]", "").replaceAll("<[^>]+>", "");
        if (RemotelyClient.tr != null) {
            return RemotelyClient.tr.getWidth(clean);
        }
        return clean.length() * 6;
    }

    private MinecraftGameAssets getGameAssets() {
        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
            MinecraftGameAssets gameAssets = RemotelyClient.INSTANCE.getHost().getGameAssets();
            if (gameAssets != null) {
                return gameAssets;
            }
        }
        return MinecraftGameAssets.EMPTY;
    }

    private void drawMinecraftTexture(IDrawContext context, MinecraftGameAssets gameAssets, MinecraftAssetReference reference, BufferedImage fallback, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        Object nativeIdentifier = gameAssets.getNativeIdentifier(reference);
        if (nativeIdentifier != null && context.drawNativeTexture(nativeIdentifier, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight)) {
            return;
        }
        if (fallback != null && fallback != ResourceManager.getInstance().getMissingTexture()) {
            context.drawPixelArt(fallback, x, y, width, height);
        }
    }

    private void renderActiveResourceSelectorOverlay(IDrawContext context, int mouseX, int mouseY, float delta) {
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioOverlayView overlayView) {
            overlayView.renderStudioOverlay(context, mouseX, mouseY, delta);
        }
    }

    private Long parseNullableLong(String value) {
        String text = safeText(value).trim();
        if (text.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double parseNullableDouble(String value) {
        String text = safeText(value).trim();
        if (text.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Float parseNullableFloat(String value) {
        String text = safeText(value).trim();
        if (text.isBlank()) {
            return 0.0F;
        }
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (studioTabsManager != null && studioTabsManager.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        if (nodeItemSelector != null && nodeItemSelector.visible && nodeItemSelector.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        if (handleActiveStudioSelectorMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (handlePopupWidgetMouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        ReSyncStudioView earlyActiveView = activeStudioView();
        if (earlyActiveView instanceof StudioPriorityInputView && earlyActiveView.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && studioContentBrowser.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && studioResourcePanel.isVisible() && studioResourcePanel.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
                return true;
            }
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
        if (!studioMode) {
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

    private class JsonResourceStudioView implements ReSyncStudioView, StudioSelectorView, StudioOverlayView, StudioCatalogRefreshView, StudioPriorityInputView {
        private final String type;
        private final String id;
        private final JsonObject resource;
        private final List<AnimatedWidget> headerActions = new ArrayList<>();
        private final Map<String, TextInputWidget> fieldInputs = new LinkedHashMap<>();
        private final Map<String, CodeEditorWidget> codeFieldInputs = new LinkedHashMap<>();
        private ItemSelectorWidget activeResourceSelector;
        private String pendingRecipeSelectorField;
        private int pendingRecipeSelectorX;
        private int pendingRecipeSelectorY;
        private int recipePreviewX;
        private int recipePreviewY;
        private int recipePreviewScale = 1;
        private RecipeStationLayout recipePreviewLayout;
        private String selectedRecipeField;
        private String pressedRecipeField;
        private String dragRecipeTargetField;
        private final Set<String> dragRecipeTargetFields = new HashSet<>();
        private SlotInteractionGrid.Stroke recipeStroke;
        private RecipeStrokeMode recipeStrokeMode = RecipeStrokeMode.NONE;
        private String recipeStrokeValue = "";
        private String recipeBrushValue = "";
        private List<String> pendingRecipeSelectionFields = new ArrayList<>();
        private boolean draggingRecipeField;
        private boolean resourceHistoryBatch;
        private int x;
        private int y;
        private int width;
        private int height;
        private final StudioScreen.History<String> resourceHistory = history(this::resourceSnapshot, this::restoreResourceSnapshot);

        private enum RecipeStrokeMode {
            NONE,
            PAINT,
            ERASE,
            SELECT
        }

        private JsonResourceStudioView(String type, String id, JsonObject resource) {
            this.type = type;
            this.id = id;
            this.resource = resource != null ? resource : new JsonObject();
            headerActions.add(headerButton("save.png", "Save", this::save));
            if (ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type)) {
                ensureRecipeItemCatalogLoaded();
            }
        }

        private boolean hasResourceHistory() {
            return ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type);
        }

        private String resourceSnapshot() {
            return gson.toJson(resource);
        }

        private void captureResourceSnapshot() {
            if (hasResourceHistory() && !resourceHistoryBatch && !resourceHistory.isRestoring()) {
                resourceHistory.capture();
            }
        }

        private void restoreResourceSnapshot(String snapshot) {
            JsonObject restored = gson.fromJson(snapshot, JsonObject.class);
            List<String> keys = resource.entrySet().stream().map(Map.Entry::getKey).toList();
            for (String key : keys) {
                resource.remove(key);
            }
            if (restored != null) {
                for (Map.Entry<String, JsonElement> entry : restored.entrySet()) {
                    resource.add(entry.getKey(), entry.getValue().deepCopy());
                }
            }
            selectedRecipeField = null;
            pressedRecipeField = null;
            dragRecipeTargetField = null;
            dragRecipeTargetFields.clear();
            recipeStroke = null;
            recipeStrokeMode = RecipeStrokeMode.NONE;
            recipeStrokeValue = "";
            pendingRecipeSelectionFields = new ArrayList<>();
            draggingRecipeField = false;
            reloadFields();
        }

        @Override
        public List<AnimatedWidget> headerButtons() {
            return headerActions;
        }

        @Override
        public boolean hasPanel() {
            return true;
        }

        @Override
        public StudioPanel.Placement preferredPanelPlacement() {
            return StudioPanel.Placement.RIGHT;
        }

        @Override
        public void configurePanel(StudioPanel panel) {
            buildResourcePanel();
        }

        @Override
        public void resize(int width, int height) {
            this.x = 18;
            this.y = 44;
            this.width = Math.max(120, width - 36);
            this.height = Math.max(80, height - 62);
        }

        @Override
        public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
            int text = ThemeManager.getColor(ThemeColor.text);
            int muted = ThemeManager.getColor(ThemeColor.textDark);
            renderPreviewCanvas(context, text, muted);
        }

        private void renderPreviewCanvas(IDrawContext context, int text, int muted) {
            int previewX = x + 12;
            int previewY = y + 12;
            int rightReserve = studioResourcePanel != null && studioResourcePanel.isVisible() && !studioResourcePanel.isLeftAnchored() ? studioResourcePanel.getDesiredWidth() + 10 : 0;
            int previewWidth = Math.max(160, x + width - rightReserve - previewX - 14);
            int previewHeight = Math.max(80, height - 24);
            switch (type) {
                case ReSyncResourceDragPayload.MOTD_PROFILE -> renderMotdRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
                case ReSyncResourceDragPayload.RECIPE_DEFINITION -> renderRecipeRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
                case ReSyncResourceDragPayload.TEXT_TEMPLATE -> renderTextRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
                case ReSyncResourceDragPayload.MESSAGE_RULE -> renderMessageRuleRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
                case ReSyncResourceDragPayload.IGNORE_LIST -> renderIgnoreListRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
                case ReSyncResourceDragPayload.CHAT_CHANNEL, ReSyncResourceDragPayload.CHAT_FORMAT, ReSyncResourceDragPayload.CHAT_RULE,
                     ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT, ReSyncResourceDragPayload.MENTION_STYLE -> renderChatRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
                default -> renderGenericRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
            }
        }

        private void renderMotdRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
            int top = previewY + Math.max(20, previewHeight / 2 - 38);
            BufferedImage icon = motdPreviewIcon();
            String[] lines = motdPreviewLines();
            context.drawPixelArt(icon, previewX + 12, top + 16, 32, 32);
            drawFormattedLine(context, "Cool Server", previewX + 48, top + 16, text, true);
            drawFormattedLine(context, lines[0], previewX + 48, top + 28, text, true);
            drawFormattedLine(context, lines[1], previewX + 48, top + 37, muted, true);
            context.drawText(sampleCountText(), previewX + previewWidth - 74, top + 16, 0xFFFFFFFF, false);
        }

        private BufferedImage motdPreviewIcon() {
            String hash = jsonText("iconHash");
            if (!hash.isBlank()) {
                BufferedImage cached;
                synchronized (MOTD_ICON_CACHE) {
                    cached = MOTD_ICON_CACHE.get(hash);
                }
                if (cached != null) {
                    return cached;
                }
            }
            String data = jsonText("iconData");
            if (!data.isBlank()) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(stripImageDataPrefix(data));
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (image != null) {
                        String key = hash.isBlank() ? sha256(bytes) : hash;
                        synchronized (MOTD_ICON_CACHE) {
                            MOTD_ICON_CACHE.put(key, image);
                        }
                        return image;
                    }
                } catch (Exception ignored) {
                }
            }
            return ResourceManager.getInstance().getImage(Identifier.icon("fullPanel.png"));
        }

        private void renderRecipeRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
            String recipeType = normalizedRecipeType();
            RecipeStationLayout layout = recipeStationLayout(recipeType);
            MinecraftGameAssets gameAssets = getGameAssets();
            MinecraftAssetReference reference = layout.hasTexture() ? gameAssets.containerTexture(layout.texture()) : null;
            BufferedImage texture = reference != null ? gameAssets.getImage(reference) : null;
            boolean hasTexture = texture != null && texture != ResourceManager.getInstance().getMissingTexture();
            int textureWidth = hasTexture ? texture.getWidth() : layout.fallbackWidth();
            int textureHeight = hasTexture ? texture.getHeight() : layout.fallbackHeight();
            int scale = Math.max(1, Math.min(previewWidth / textureWidth, previewHeight / textureHeight));
            scale = Math.min(scale, 3);
            int viewWidth = textureWidth * scale;
            int viewHeight = textureHeight * scale;
            int viewX = previewX + Math.max(0, (previewWidth - viewWidth) / 2);
            int viewY = previewY + Math.max(0, (previewHeight - viewHeight) / 2);
            recipePreviewX = viewX;
            recipePreviewY = viewY;
            recipePreviewScale = scale;
            recipePreviewLayout = layout;
            if (hasTexture) {
                drawMinecraftTexture(context, gameAssets, reference, texture, viewX, viewY, viewWidth, viewHeight, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
            } else {
                drawRecipeFallbackPanel(context, layout, viewX, viewY, viewWidth, viewHeight, muted, scale);
            }
            drawRecipeSlotHighlights(context, recipeType, layout, viewX, viewY, scale);
            drawRecipeStationItems(context, recipeType, layout, viewX, viewY, scale);
        }

        private void drawRecipeFallbackPanel(IDrawContext context, RecipeStationLayout layout, int viewX, int viewY, int viewWidth, int viewHeight, int muted, int scale) {
            int border = ThemeManager.getColor(ThemeColor.innerBorder);
            for (RecipeSlotTarget target : recipeSlotTargets(normalizedRecipeType(), layout)) {
                int[] point = target.point();
                if (point == null || point.length < 2) {
                    continue;
                }
                int left = viewX + point[0] * scale;
                int top = viewY + point[1] * scale;
                int size = 16 * scale;
                context.fill(left, top, left + size, top + size, ThemeManager.getColor(ThemeColor.elementBackground));
                context.fillBorder(left, top, left + size, top + size, 1, border);
            }
            if (layout.ingredients().length > 0 && layout.output() != null && layout.output().length >= 2) {
                int[] input = layout.ingredients()[0];
                int[] output = layout.output();
                int centerY = viewY + (input[1] * scale) + 8 * scale;
                int lineHeight = Math.max(1, scale);
                int startX = viewX + (input[0] + 24) * scale;
                int endX = viewX + (output[0] - 8) * scale;
                context.fill(startX, centerY, endX, centerY + lineHeight, muted);
            }
        }

        private void renderTextRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
            int centerY = previewY + previewHeight / 2;
            List<String> lines = previewTextLines();
            int startY = centerY - Math.min(3, lines.size()) * 12;
            for (int i = 0; i < Math.min(5, lines.size()); i++) {
                drawFormattedLine(context, lines.get(i), previewX + 28, startY + i * 24, i == 0 ? text : muted, true);
            }
        }

        private void renderMessageRuleRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
            int centerY = previewY + previewHeight / 2 - 46;
            String source = jsonText("source");
            String find = jsonText("contains");
            String original = sampleMessageSource(source);
            String action = jsonText("action").toLowerCase(Locale.ROOT);
            context.drawText(messageSourceLabel(source) + "  " + messageActionLabel(action), previewX + 34, centerY - 6, muted, false);
            drawFormattedLine(context, original, previewX + 34, centerY + 12, muted, true);
            String replacement = jsonText("replacement");
            String rendered = messagePreviewResult(original, find, replacement, action);
            context.drawText(messageMatchLabel(original, find), previewX + 34, centerY + 40, muted, true);
            drawFormattedLine(context, rendered, previewX + 34, centerY + 58, text, true);
            if ("flow".equals(action) && !jsonText("flowId").isBlank()) {
                context.drawText("Runs " + jsonText("flowId"), previewX + 34, centerY + 84, muted, true);
            }
        }

        private String messagePreviewResult(String original, String find, String replacement, String action) {
            boolean matches = find == null || find.isBlank() || original.contains(find);
            if (!matches) {
                return "<dark_gray>No Match</dark_gray>";
            }
            String renderedReplacement = messageReplacementText(original, replacement);
            return switch (action) {
                case "remove", "clear", "hide" -> "<dark_gray>Hidden</dark_gray>";
                case "append" -> original + renderedReplacement;
                case "prepend" -> renderedReplacement + original;
                case "replace_section", "section" -> find == null || find.isBlank() ? renderedReplacement : original.replace(find, renderedReplacement);
                case "flow" -> renderedReplacement;
                default -> renderedReplacement;
            };
        }

        private String messageReplacementText(String original, String replacement) {
            String template = replacement == null || replacement.isBlank() ? "{message}" : replacement;
            return template.replace("{player}", "Steve").replace("{message}", original);
        }

        private String messageMatchLabel(String original, String find) {
            if (find == null || find.isBlank()) {
                return "Applies To All";
            }
            return original.contains(find) ? "Matches " + find : "Missing " + find;
        }

        private String messageSourceLabel(String source) {
            return switch (source == null ? "" : source.toLowerCase(Locale.ROOT)) {
                case "quit" -> "Quit";
                case "kick" -> "Kick";
                case "death" -> "Death";
                case "title" -> "Title";
                case "actionbar" -> "Actionbar";
                case "bossbar" -> "Bossbar";
                case "openscreen" -> "Open Screen";
                case "packettext" -> "Packet Text";
                case "system" -> "System";
                default -> "Join";
            };
        }

        private String messageActionLabel(String action) {
            return switch (action == null ? "" : action.toLowerCase(Locale.ROOT)) {
                case "remove", "clear", "hide" -> "Hide";
                case "append" -> "Append";
                case "prepend" -> "Prepend";
                case "flow" -> "Flow";
                case "replace_section", "section" -> "Replace Part";
                default -> "Replace";
            };
        }

        private String sampleMessageSource(String source) {
            return switch (source == null ? "" : source.toLowerCase(Locale.ROOT)) {
                case "quit" -> "Steve left the game";
                case "kick" -> "Steve was kicked";
                case "death" -> "Steve fell from a high place";
                case "title" -> "Welcome Steve";
                case "actionbar" -> "Objective Updated";
                case "bossbar" -> "Dragon Health";
                case "openscreen" -> "Chest";
                case "packettext" -> "Server Notice";
                case "system" -> "Server restarting soon";
                default -> "Steve joined the game";
            };
        }

        private void renderChatRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
            int chatX = previewX + Math.max(14, previewWidth / 2 - 150);
            int chatY = previewY + Math.max(18, previewHeight / 2 - 48);
            String prefix = jsonText("prefix");
            String template = jsonText("template");
            if (template.isBlank()) {
                template = "{prefix}{sender}: {message}";
            }
            String line = template.replace("{prefix}", prefix).replace("{sender}", "Steve").replace("{receiver}", "Alex").replace("{message}", "Hello @Alex");
            drawFormattedLine(context, applyMentionPreview(line), chatX + 12, chatY + 16, text, true);
            drawFormattedLine(context, "<gray>Alex: Looks good", chatX + 12, chatY + 36, muted, true);
            drawFormattedLine(context, "<yellow>@Steve</yellow> synced", chatX + 12, chatY + 56, text, true);
        }

        private void renderIgnoreListRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
            int listX = previewX + Math.max(18, previewWidth / 2 - 120);
            int listY = previewY + Math.max(18, previewHeight / 2 - 70);
            context.drawText("Ignored Players", listX + 12, listY + 12, text, false);
            JsonArray players = resource.has("players") && resource.get("players").isJsonArray() ? resource.getAsJsonArray("players") : new JsonArray();
            int row = 0;
            for (int i = 0; i < players.size() && row < 5; i++) {
                String player = players.get(i).isJsonNull() ? "" : players.get(i).getAsString();
                if (player.isBlank()) {
                    continue;
                }
                context.drawText(player, listX + 18, listY + 37 + row * 17, row == 0 ? text : muted, false);
                row++;
            }
            if (row == 0) {
                context.drawText("None", listX + 18, listY + 38, muted, false);
            }
        }

        private void renderGenericRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
            int centerY = previewY + previewHeight / 2;
            context.drawText(resourceDisplayName(), previewX + 24, centerY - 10, text, false);
            context.drawText("Ready", previewX + 24, centerY + 8, muted, false);
        }

        private void save() {
            ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(type);
            FlowManager manager = FlowManager.getInstance();
            if (resourceType != null && manager != null) {
                sanitizeLegacyResourceFields();
                manager.saveJsonResource(serverId, resourceType, resource);
            }
        }

        private void sanitizeLegacyResourceFields() {
            if (ReSyncResourceDragPayload.TEXT_TEMPLATE.equals(type)) {
                resource.remove("colorsText");
                if (jsonText("mode").isBlank()) {
                    resource.addProperty("mode", "frames");
                }
                JsonArray frames = resource.has("frames") && resource.get("frames").isJsonArray() ? resource.getAsJsonArray("frames") : new JsonArray();
                if (frames.isEmpty()) {
                    String text = jsonText("text");
                    if (text.isBlank()) {
                        text = id;
                    }
                    if (text != null && !text.isBlank()) {
                        frames.add(text);
                        resource.add("frames", frames);
                    }
                }
            } else if (ReSyncResourceDragPayload.MOTD_PROFILE.equals(type)) {
                resource.remove("mode");
                resource.remove("frames");
                resource.remove("frameMillis");
                resource.remove("rotationMillis");
                resource.remove("line1Frames");
                resource.remove("line2Frames");
                resource.remove("versionText");
                resource.remove("protocolVersion");
                resource.remove("protocolText");
                resource.remove("match");
                resource.remove("samplePlayers");
                resource.remove("countMode");
                resource.remove("fakePlayers");
                resource.remove("overrideMaxPlayers");
                if (!"fixed".equalsIgnoreCase(jsonText("playerCountMode"))) {
                    resource.remove("onlinePlayers");
                    resource.remove("maxPlayers");
                }
            } else if (ReSyncResourceDragPayload.MESSAGE_RULE.equals(type)) {
                resource.remove("regex");
                resource.remove("componentPath");
                resource.remove("componentValue");
            }
        }

        private void reloadFields() {
            fieldInputs.clear();
            codeFieldInputs.clear();
            studioResourcePanelKey = "";
            buildResourcePanel();
        }

        private void buildResourcePanel() {
            if (studioResourcePanel == null) {
                return;
            }
            String panelKey = activeStudioDocument != null ? activeStudioDocument.key() : ReSyncProjectMetadata.resourceKey(type, id);
            if (reuseStudioResourcePanel(panelKey)) {
                refreshResourcePanelFields();
                return;
            }
            setStudioResourcePanelKey(panelKey);
            fieldInputs.clear();
            codeFieldInputs.clear();
            int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
            List<AnimatedWidget> widgets = new ArrayList<>();
            MountableButtonWidget summary = new MountableButtonWidget.Builder(resourceDisplayName())
                .description(resourceSummary())
                .iconPath(studioResourceIconPath(type, id))
                .build();
            summary.setSize(rowWidth, 30);
            widgets.add(summary);
            for (String field : editorFields()) {
                widgets.add(fieldRow(field, rowWidth));
            }
            if (ReSyncResourceDragPayload.MOTD_PROFILE.equals(type)) {
                widgets.add(motdIconUploadRow(rowWidth));
            }
            widgets.add(panelSaveButton(this::save));
            setStudioResourcePanelWidgets(widgets.toArray(new AnimatedWidget[0]));
        }

        private void refreshResourcePanelFields() {
            for (Map.Entry<String, TextInputWidget> entry : fieldInputs.entrySet()) {
                TextInputWidget input = entry.getValue();
                String value = jsonPathText(entry.getKey());
                if (input != null && !input.isFocused() && !Objects.equals(input.getText(), value)) {
                    input.setText(value);
                }
            }
            for (Map.Entry<String, CodeEditorWidget> entry : codeFieldInputs.entrySet()) {
                CodeEditorWidget input = entry.getValue();
                String value = jsonPathText(entry.getKey());
                if (input != null && !input.isFocused() && !Objects.equals(input.getText(), value)) {
                    input.setText(value);
                }
            }
        }

        private AnimatedWidget fieldRow(String field, int rowWidth) {
            String label = fieldLabel(field);
            if (recipeBindingField(field)) {
                return recipeBindingRow(field, label, rowWidth);
            }
            if (flowBindingField(field)) {
                return flowBindingRow(field, label, rowWidth);
            }
            if ("conditions.world".equals(field)) {
                return worldConditionFieldRow(label, rowWidth);
            }
            List<String> selectorOptions = selectorOptions(field);
            if (!selectorOptions.isEmpty()) {
                return searchableFieldRow(field, label, selectorOptions, rowWidth);
            }
            if (isCodeField(field)) {
                int editorHeight = codeFieldHeight(field);
                CodeEditorWidget input = new CodeEditorWidget(0, 0, rowWidth, editorHeight);
                input.setText(jsonPathText(field));
                input.onChange = value -> putJsonText(field, input.getText());
                ReSyncStudioPanelState.disableEntrance(input);
                codeFieldInputs.put(field, input);
                return studioPanelState.codeRow(label, input, rowWidth, editorHeight + 18, jsonResourceDescription(field, label));
            }
            TextInputWidget input = new TextInputWidget.Builder()
                .text(jsonPathText(field))
                .placeholder(label)
                .forcePlaceholder(false)
                .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
                .onChange(value -> putJsonText(field, value))
                .build();
            ReSyncStudioPanelState.disableEntrance(input);
            fieldInputs.put(field, input);
            return studioPanelState.row(label, input, rowWidth, jsonResourceDescription(field, label));
        }

        private boolean recipeBindingField(String field) {
            return ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type) && switch (field) {
                case "craftedBinding", "cookedBinding", "conditionBinding", "deniedBinding" -> true;
                default -> false;
            };
        }

        private boolean flowBindingField(String field) {
            if (ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type)) {
                return false;
            }
            return field != null && ("flowId".equals(field) || "flowPredicate".equals(field) || field.endsWith("Flow") || field.contains("Flow"));
        }

        private AnimatedWidget flowBindingRow(String field, String label, int rowWidth) {
            CompactBindingWidget widget = new CompactBindingWidget.Builder(
                FlowGraphDesignerScreen.this,
                List.of("None", "Flow"),
                () -> jsonPathHas(field) ? "Flow" : "None",
                mode -> {
                    if ("Flow".equals(mode)) {
                        ensureJsonPathText(field, "");
                    } else {
                        putJsonText(field, "");
                    }
                    refreshResourcePanelFields();
                },
                this::flowOptions,
                () -> jsonPathTextRaw(field),
                value -> {
                    putJsonText(field, value);
                    refreshResourcePanelFields();
                },
                () -> List.of(),
                () -> {
                    String flowId = jsonPathTextRaw(field);
                    if (!flowId.isBlank()) {
                        openWorkspaceFlowEditor(flowId);
                    }
                }
            )
                .createAction("Create New", () -> jsonPathHas(field), () -> createFlowBindingTarget(field))
                .size(rowWidth, 18)
                .entranceAnimation(false)
                .build();
            ReSyncStudioPanelState.disableEntrance(widget);
            return studioPanelState.row(label, widget, rowWidth, jsonResourceDescription(field, label));
        }

        private AnimatedWidget recipeBindingRow(String field, String label, int rowWidth) {
            String flowField = recipeBindingFlowField(field);
            String functionBase = recipeBindingFunctionBase(field);
            CompactBindingWidget widget = new CompactBindingWidget.Builder(
                FlowGraphDesignerScreen.this,
                flowField.isBlank() ? List.of("None", "Function") : List.of("None", "Flow", "Function"),
                () -> recipeBindingMode(flowField, functionBase),
                mode -> {
                    if ("None".equals(mode)) {
                        if (!flowField.isBlank()) {
                            putJsonText(flowField, "");
                        }
                        putJsonText(functionBase + ".functionId", "");
                    } else if ("Flow".equals(mode)) {
                        putJsonText(functionBase + ".functionId", "");
                        if (!flowField.isBlank() && !jsonPathHas(flowField)) {
                            ensureJsonPathText(flowField, "");
                        }
                    } else if ("Function".equals(mode)) {
                        if (!flowField.isBlank()) {
                            putJsonText(flowField, "");
                        }
                        if (!jsonPathHas(functionBase + ".functionId")) {
                            ensureFunctionCall(functionBase);
                        }
                    }
                    refreshResourcePanelFields();
                },
                () -> "Function".equals(recipeBindingMode(flowField, functionBase)) ? functionOptions() : "Flow".equals(recipeBindingMode(flowField, functionBase)) ? flowOptions() : List.of("none"),
                () -> "Function".equals(recipeBindingMode(flowField, functionBase)) ? jsonPathTextRaw(functionBase + ".functionId") : "Flow".equals(recipeBindingMode(flowField, functionBase)) ? jsonPathTextRaw(flowField) : "",
                value -> {
                    if ("Function".equals(recipeBindingMode(flowField, functionBase))) {
                        putJsonText(functionBase + ".functionId", value);
                    } else if ("Flow".equals(recipeBindingMode(flowField, functionBase)) && !flowField.isBlank()) {
                        putJsonText(flowField, value);
                    }
                    refreshResourcePanelFields();
                },
                () -> compactFunctionInputs(functionBase),
                () -> openRecipeBinding(functionBase, flowField)
            )
                .createAction("Create New", () -> "Function".equals(recipeBindingMode(flowField, functionBase)) || "Flow".equals(recipeBindingMode(flowField, functionBase)), () -> createRecipeBindingTarget(flowField, functionBase))
                .size(rowWidth, 18)
                .entranceAnimation(false)
                .build();
            ReSyncStudioPanelState.disableEntrance(widget);
            return studioPanelState.row(label, widget, rowWidth, jsonResourceDescription(field, label));
        }

        private String recipeBindingFlowField(String field) {
            return switch (field) {
                case "craftedBinding" -> "craftedFlow";
                case "cookedBinding" -> "cookedFlow";
                case "deniedBinding" -> "deniedFlow";
                default -> "";
            };
        }

        private String recipeBindingFunctionBase(String field) {
            return switch (field) {
                case "craftedBinding" -> "craftedAction";
                case "cookedBinding" -> "cookedAction";
                case "conditionBinding" -> "conditions.predicate";
                case "deniedBinding" -> "deniedAction";
                default -> "";
            };
        }

        private String recipeBindingMode(String flowField, String functionBase) {
            if (jsonPathHas(functionBase + ".functionId")) {
                return "Function";
            }
            if (!flowField.isBlank() && jsonPathHas(flowField)) {
                return "Flow";
            }
            return "None";
        }

        private List<CompactBindingWidget.BindingInput> compactFunctionInputs(String functionBase) {
            FlowGraph function = selectedFunction(functionBase + ".functionId", recipeFunctionShape(functionBase));
            if (function == null || function.getFunctionInputs() == null || function.getFunctionInputs().isEmpty()) {
                return List.of();
            }
            List<CompactBindingWidget.BindingInput> inputs = new ArrayList<>();
            for (FlowGraph.FunctionParameter input : function.getFunctionInputs()) {
                if (input == null || input.getName() == null || input.getName().isBlank()) {
                    continue;
                }
                String field = functionBase + ".inputs." + input.getName();
                inputs.add(new CompactBindingWidget.BindingInput(
                    input.getName(),
                    functionInputLabel(input.getName()),
                    jsonPathText(field),
                    input.getType() != null ? input.getType().getColor() : FlowDataType.ANY.getColor(),
                    () -> functionInputOptions(field, input),
                    value -> putJsonText(field, value),
                    input.getType() != null && FlowDataType.BOOLEAN.isAssignableFrom(input.getType()) ? CompactBindingWidget.InputKind.BOOLEAN : CompactBindingWidget.InputKind.TEXT
                ));
            }
            return inputs;
        }

        private List<String> functionInputOptions(String field, FlowGraph.FunctionParameter input) {
            if (input == null) {
                return List.of();
            }
            List<String> options = new ArrayList<>();
            String contextDefault = functionInputContextDefault(field, input);
            if (!contextDefault.isBlank()) {
                options.add(contextDefault);
            }
            FlowDataType type = input.getType();
            if (type != null && FlowDataType.PLAYER.isAssignableFrom(type) && !options.contains("$player")) {
                options.add("$player");
            }
            if (type != null && (FlowDataType.ITEM.isAssignableFrom(type) || FlowDataType.MATERIAL.isAssignableFrom(type))) {
                if (!options.contains("$event.output")) {
                    options.add("$event.output");
                }
                if (!options.contains("$event.source")) {
                    options.add("$event.source");
                }
            }
            if (type != null && FlowDataType.BOOLEAN.isAssignableFrom(type)) {
                if (!options.contains("true")) {
                    options.add("true");
                }
                if (!options.contains("false")) {
                    options.add("false");
                }
            }
            String source = input.getOptionsSource();
            if (source != null && !source.isBlank()) {
                for (String option : catalogOptions(source)) {
                    if (option != null && !option.isBlank() && !options.contains(option)) {
                        options.add(option);
                    }
                }
            }
            return options;
        }

        private void openRecipeBinding(String functionBase, String flowField) {
            String mode = recipeBindingMode(flowField, functionBase);
            String id = "Function".equals(mode) ? jsonPathTextRaw(functionBase + ".functionId") : "Flow".equals(mode) ? jsonPathTextRaw(flowField) : "";
            if (!id.isBlank()) {
                openWorkspaceFlowEditor(id);
            }
        }

        private void createFlowBindingTarget(String field) {
            createBindingResource(ReSyncResourceDragPayload.FLOW, id -> {
                putJsonText(field, id);
                refreshResourcePanelFields();
            });
        }

        private void createRecipeBindingTarget(String flowField, String functionBase) {
            String mode = recipeBindingMode(flowField, functionBase);
            if ("Function".equals(mode)) {
                createBindingResource(ReSyncResourceDragPayload.FUNCTION, id -> {
                    normalizeBindingFunction(id, recipeFunctionShape(functionBase));
                    putJsonText(functionBase + ".functionId", id);
                    refreshResourcePanelFields();
                });
            } else if ("Flow".equals(mode) && !flowField.isBlank()) {
                createBindingResource(ReSyncResourceDragPayload.FLOW, id -> {
                    putJsonText(flowField, id);
                    refreshResourcePanelFields();
                });
            }
        }

        private void createBindingResource(String type, Consumer<String> onCreated) {
            ReSyncResourceCreator.showCreatePopup(FlowGraphDesignerScreen.this, serverId, type, bindingCreateFolder(), null, result -> {
                if (onCreated != null) {
                    onCreated.accept(result.id());
                }
                refreshStudioWorkspace(true);
                openWorkspaceFlowEditor(result.id());
            });
        }

        private CompactBindingSupport.FunctionShape recipeFunctionShape(String functionBase) {
            return functionBase != null && functionBase.contains("predicate")
                ? CompactBindingSupport.playerPredicateShape()
                : CompactBindingSupport.playerActionShape();
        }

        private void normalizeBindingFunction(String functionId, CompactBindingSupport.FunctionShape shape) {
            FlowManager manager = FlowManager.getInstance();
            FlowGraph function = manager != null ? manager.getFlowsForServer(serverId).get(functionId) : null;
            CompactBindingSupport.normalizeFunction(serverId, function, shape);
        }

        private String bindingCreateFolder() {
            if (activeStudioDocument == null) {
                return "";
            }
            FlowManager manager = FlowManager.getInstance();
            ReSyncProjectMetadata.ResourceEntry entry = manager != null ? manager.getProjectMetadata(serverId).findResource(activeStudioDocument.type(), activeStudioDocument.id()) : null;
            return entry != null ? entry.getPath() : "";
        }

        private void ensureJsonPathText(String field, String value) {
            if (field == null || field.isBlank()) {
                return;
            }
            if (!field.contains(".")) {
                resource.addProperty(field, value != null ? value : "");
                return;
            }
            putJsonPathText(field, value != null ? value : "");
        }

        private void ensureFunctionCall(String basePath) {
            JsonObject call = ensureJsonPathObject(basePath);
            call.addProperty("type", "functionRef");
            if (!call.has("functionId")) {
                call.addProperty("functionId", "");
            }
        }

        private JsonObject ensureJsonPathObject(String field) {
            if (field == null || field.isBlank()) {
                return resource;
            }
            String[] parts = field.split("\\.");
            JsonObject current = resource;
            for (String part : parts) {
                if (part.isBlank()) {
                    continue;
                }
                if (!current.has(part) || !current.get(part).isJsonObject()) {
                    current.add(part, new JsonObject());
                }
                current = current.getAsJsonObject(part);
            }
            return current;
        }

        private boolean jsonPathHas(String field) {
            if (field == null || field.isBlank()) {
                return false;
            }
            return jsonPathElement(field) != null;
        }

        private String functionInputLabel(String name) {
            String cleaned = name == null ? "" : name.replace('_', ' ').trim();
            return cleaned.isBlank() ? "Input" : Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        }

        private AnimatedWidget motdIconUploadRow(int rowWidth) {
            MountableButtonWidget button = new MountableButtonWidget.Builder(jsonText("iconHash").isBlank() ? "Upload Icon" : "Replace Icon")
                .description(jsonText("iconHash").isBlank() ? "No Icon" : "Icon Ready")
                .onClick(this::pickMotdIcon)
                .build();
            button.setSize(rowWidth, 30);
            BufferedImage icon = motdPreviewIcon();
            if (icon != null) {
                button.setIcon(icon);
            }
            ReSyncStudioPanelState.disableEntrance(button);
            return button;
        }

        private void pickMotdIcon() {
            FileUtils.pickImageFileAsync("Select Server Icon", path -> {
                if (path == null) {
                    return;
                }
                CompletableFuture.runAsync(() -> loadMotdIcon(path));
            });
        }

        private void loadMotdIcon(Path path) {
            try {
                BufferedImage source = ImageIO.read(path.toFile());
                if (source == null) {
                    throw new IOException("Invalid Image");
                }
                BufferedImage normalized = normalizeMotdIcon(source);
                if (normalized.getWidth() != 64 || normalized.getHeight() != 64) {
                    throw new IOException("Icon Must Be 64x64");
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(normalized, "png", output);
                byte[] bytes = output.toByteArray();
                String hash = sha256(bytes);
                String encoded = Base64.getEncoder().encodeToString(bytes);
                synchronized (MOTD_ICON_CACHE) {
                    MOTD_ICON_CACHE.put(hash, normalized);
                }
                ScreenManager.getInstance().execute(() -> {
                    resource.addProperty("iconHash", hash);
                    resource.addProperty("iconData", encoded);
                    resource.addProperty("icon", "motd-icons/" + hash + ".png");
                    reloadFields();
                    save();
                });
            } catch (Exception e) {
                ScreenManager.getInstance().execute(() -> new Notification("Icon Upload Failed", e.getMessage(), Notification.Type.ERROR));
            }
        }

        private BufferedImage normalizeMotdIcon(BufferedImage source) {
            BufferedImage normalized = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = normalized.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            graphics.drawImage(source, 0, 0, 64, 64, null);
            graphics.dispose();
            return normalized;
        }

        private String stripImageDataPrefix(String data) {
            int comma = data.indexOf(',');
            return data.startsWith("data:image/") && comma >= 0 ? data.substring(comma + 1) : data;
        }

        private String sha256(byte[] bytes) throws NoSuchAlgorithmException {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        }

        private AnimatedWidget worldConditionFieldRow(String label, int rowWidth) {
            List<String> selected = worldConditionValues();
            List<String> options = normalizedWorldOptions(catalogOptions("server:minecraft:world"), selected);
            DropDownWidget<String> dropdown = new DropDownWidget.Builder<>(options)
                .multiSelect(true)
                .size(174, 18)
                .maxVisibleItems(8)
                .onMultiSelectionChanged(widget -> putWorldConditionValues(widget.getSelectedItems()))
                .build();
            dropdown.setSelectedItems(selected, List.of());
            ReSyncStudioPanelState.disableEntrance(dropdown);
            return studioPanelState.row(label, dropdown, rowWidth, jsonResourceDescription("conditions.world", label));
        }

        private String jsonResourceDescription(String field, String label) {
            String key = field == null ? label : field;
            return switch (key) {
                case "sender" -> "Private-message sender format.\nPlaceholders:\n{sender} Sender name.\n{receiver} Receiver name.\n{message} Message text after mention parsing.";
                case "receiver" -> "Private-message receiver format.\nPlaceholders:\n{sender} Sender name.\n{receiver} Receiver name.\n{message} Message text after mention parsing.";
                case "spy" -> "Social-spy message format.\nShown to enabled spies who can see the sender/receiver pair.\nPlaceholders: {sender}, {receiver}, {message}.";
                case "player1", "player2", "player3", "player4", "player5" -> "Ignored player name entry.\nUsed by the ignore-list resource to seed blocked private/chat targets.";
                case "name", "displayName", "title" -> "Human-readable name.\nShown in previews, menus, or generated Minecraft text.";
                case "description" -> "Long description for players or editors.\nExplain what the resource does and when it applies.";
                case "enabled" -> "Export state.\nOn: synchronized and active.\nOff: saved but not used live.";
                case "priority" -> "Match ordering weight.\nHigher values are reserved for more specific rules.";
                case "permission" -> "Required permission node.\nLeave empty when no permission check is needed.";
                case "conditions.permission" -> "Recipe permission condition.\nOnly players with this permission can craft or take the recipe result.\nEmpty means no permission gate.";
                case "conditions.world" -> "Recipe world condition.\nOnly players in this world can craft or take the recipe result.\nEmpty means every world.";
                case "worlds" -> "World filter.\nEmpty means every world.\nSelected worlds scope this resource.";
                case "line1", "line2", "motd", "serverName" -> "Minecraft server-list MOTD text.\nRendered in the multiplayer server list.\nKeep both lines readable at small size.";
                case "motdText" -> "Server-list MOTD editor text.\nFirst line maps to line1.\nSecond line maps to line2.";
                case "icon", "iconHash", "iconData" -> "Server-list icon data.\nBest source image size: 64x64.\nMinecraft displays it beside the MOTD.";
                case "playerCountMode" -> "Server-list player-count mode.\nreal: show actual online/max values.\nhidden: hide player counts on Paper.\nfixed: override online and max values.";
                case "onlinePlayers" -> "Fixed online-player count.\nUsed only when Player Count is fixed.\nPaper applies it to the server-list ping response.";
                case "maxPlayers" -> "Fixed max-player count.\nUsed only when Player Count is fixed.\nBukkit applies it to the server-list ping response.";
                case "type", "recipeType" -> "Recipe type.\nOptions:\nshaped: 3x3 pattern.\nshapeless: ingredients in any order.\nfurnace, blasting, smoking, campfire: one input plus cook settings.\nstonecutting: one input to one output.\nsmithing_transform: template/base/addition to output.\nsmithing_trim: template/base/addition trim recipe.";
                case "group", "category" -> "Organization key.\nUsed by browsers, filters, and grouping views.";
                case "material", "output.material", "template.material", "base.material", "addition.material" -> "Item material id.\nAccepts a Minecraft material id or supported custom content id.";
                case "amount", "output.amount", "template.amount", "base.amount", "addition.amount" -> "Item stack amount.\nMost Minecraft item stacks use 1 to 64.";
                case "ingredients" -> "Recipe ingredient list.\nShapeless: all required inputs.\nCooking/stonecutting: first ingredient is the input.\nSmithing: template, base, and addition are separate fields.";
                case "shape" -> "Shaped recipe pattern.\nUp to 3 rows.\nEach character maps to an entry in recipe keys/ingredients.";
                case "slots" -> "Recipe preview slot bindings.\nUsed by the editor to map materials to visible recipe slots.";
                case "experience" -> "Cooking recipe experience reward.\nPassed to Bukkit cooking recipes as the XP dropped when the result is taken.";
                case "cookingTime", "cookTime" -> "Cooking duration in ticks.\n20 ticks = 1 second.\nMinimum runtime value is 1 tick.";
                case "craftedBinding" -> "Action run after a crafting, stonecutting, or shapeless result is taken.\nUse a flow or function with recipe/player context.";
                case "cookedBinding" -> "Action run after a cooking recipe result is taken.\nUse a flow or function with recipe/player context.";
                case "conditionBinding" -> "Predicate function checked before recipe use.\nUse declared inputs with recipe/player context.";
                case "deniedBinding" -> "Action run when recipe conditions or ingredient checks deny the craft.\nUse a flow or function with recipe/player context.";
                case "craftedFlow" -> "Flow run after a crafting, stonecutting, or shapeless result is taken.\nReceives recipe/player event context.";
                case "cookedFlow" -> "Flow run after a furnace, blast furnace, smoker, or campfire result is taken.\nReceives recipe/player event context.";
                case "deniedFlow" -> "Flow run when recipe conditions or ingredient checks deny the craft.\nReceives recipe/player event context.";
                case "channel", "channelId" -> "Chat channel id.\nKeep it stable because formats, permissions, aliases, and rules can reference it.";
                case "range" -> "Chat hearing range in blocks.\nNegative values mean unlimited range.\nUsed with the sender and each viewer location.";
                case "speakPermission" -> "Permission required to send messages in this channel.\nEmpty means every player can speak.";
                case "readPermission" -> "Permission required to receive this channel.\nEmpty means every player can read it.";
                case "allowMiniMessage" -> "MiniMessage formatting gate.\nOn: channel formatting can parse MiniMessage tags.";
                case "miniMessagePermission" -> "Permission required for player-authored MiniMessage formatting.\nEmpty means no extra MiniMessage permission gate.";
                case "format", "messageFormat", "entryFormat" -> "Chat/message format template.\nCommon placeholders:\n{player} Sender name.\n{displayName} Sender display name.\n{message} Message text.\n{prefix} Channel prefix.\n{channel} Channel id.";
                case "prefix" -> "Text before the message or player name.\nTypical content: channel labels, ranks, or status markers.";
                case "suffix" -> "Text after the message or player name.\nKeep it short so chat and tab rows stay readable.";
                case "color" -> "Primary display color.\nUse enough contrast for Minecraft chat and UI backgrounds.";
                case "hover", "hoverText" -> "Interactive chat hover text.\nShown only on clients that support hover events.";
                case "click", "clickAction", "clickValue" -> "Interactive chat click behavior.\nTypical actions are suggest command, run command, or open URL.";
                case "source" -> "Message-rule source event.\nOptions: join, quit, kick, death, title, actionbar, bossbar, openScreen, packetText, system.";
                case "sources" -> "Message-rule source list.\nArray form of Source when the rule should match several event types.";
                case "contains" -> "Match text.\nThe rule applies when the source message contains this value.\nEmpty values match broadly and should be avoided.";
                case "replacement" -> "Replacement or inserted text.\nAction decides how this is used.\n{message} keeps the original message.";
                case "action" -> "Rule action.\nChat rules: block, replace, flow, channel.\nMessage rules: replace_section, replace, append, prepend, remove, flow.";
                case "players" -> "Player filter list.\nWhen present, the rule applies only to matching player names.";
                case "template" -> "Chat format template.\nRendered by the chat/text service for the resource that owns it.";
                case "text" -> ReSyncResourceDragPayload.TEXT_TEMPLATE.equals(type)
                    ? "Base text for text-template modes.\nUsed by typing, scroll, gradient, blink, random, conditional, and frame fallback."
                    : "Reusable text body.\nRendered by the resource that owns this field.";
                case "mode" -> ReSyncResourceDragPayload.TEXT_TEMPLATE.equals(type)
                    ? "Text-template animation mode.\nframes: cycle each frame.\ntyping: reveal text over time.\nscroll: moving text window.\ngradient: rotating two-color gradient.\nblink: alternate visible/blank.\nrandom: stable random frame per subject.\nconditional: first frame for self, second for others."
                    : "Resource mode.\nAvailable values depend on the current resource type.";
                case "framesText" -> "Animation frames.\nOne frame per line.\nUsed by frames, random, blink, and conditional modes.";
                case "frameMillis" -> "Animation frame duration in milliseconds.\nMinimum runtime value is 1 ms.\nDefault is 250 ms.";
                case "width" -> "Scroll window width in characters.\nUsed by scroll mode.\nMinimum runtime value is 1.";
                case "visibleCharacters" -> "Typing character cap.\n0 means no cap.\nPositive values limit the maximum visible typed characters.";
                case "colorsText" -> "Gradient color list.\nOne color per line.\nGradient mode rotates through adjacent color pairs.";
                case "command", "commands" -> "Executable command text.\nStore commands only, without explanation around them.";
                case "flowId" -> "Flow run by this rule or action.\nReceives the current player/event context.";
                case "flowPredicate", "predicateFlowId" -> "Predicate flow reference.\nThe resource continues only when this flow passes for the current context.";
                case "privateMessageFlow" -> "Flow run after a private message is sent.\nReceives event.message and event.receiver.";
                case "mentionFlow" -> "Flow run when a mention style is applied.\nReceives mention/player context from chat handling.";
                default -> switch (label) {
                    case "Worlds" -> "World filter.\nEmpty means every world.\nSelected worlds scope this resource.";
                    case "Icon" -> "Preview icon or uploaded image.\nUsed by browser and editor previews.";
                    case "Provider" -> "Value resolver.\nDifferent providers map ids to different assets or runtime behavior.";
                    default -> "JSON resource field.\nValue must match the selected resource type and runtime schema.";
                };
            };
        }

        private List<String> normalizedWorldOptions(List<String> choices, List<String> selected) {
            List<String> options = new ArrayList<>();
            if (choices != null) {
                for (String choice : choices) {
                    if (choice != null && !choice.isBlank() && !"Loading".equals(choice) && !options.contains(choice)) {
                        options.add(choice);
                    }
                }
            }
            if (selected != null) {
                for (String value : selected) {
                    if (value != null && !value.isBlank() && !options.contains(value)) {
                        options.addFirst(value);
                    }
                }
            }
            if (options.isEmpty()) {
                options.add("Loading");
            }
            return options;
        }

        private AnimatedWidget searchableFieldRow(String field, String label, List<String> options, int rowWidth) {
            List<String> normalized = normalizedSelectorOptions(options, jsonPathText(field));
            AnimatedButton button = new AnimatedButton.Builder()
                .label(selectorLabel(field, resolveSelectedOption(normalized, jsonPathText(field))))
                .size(174, 18)
                .entranceAnimation(false)
                .build();
            button.setAction(() -> {
                if (normalized.size() == 1 && "Loading".equals(normalized.getFirst())) {
                    return;
                }
                showResourceSelector(field, normalized, jsonPathText(field), value -> {
                    if (!isRealOption(value)) {
                        return;
                    }
                    button.setMessage(selectorLabel(field, value));
                    putJsonText(field, value);
                    if (rebuildOnSelection(field)) {
                        reloadFields();
                    }
                }, button.getX(), button.getY() + button.getHeight());
            });
            return studioPanelState.row(label, button, rowWidth, jsonResourceDescription(field, label));
        }

        private void showResourceSelector(String field, List<String> options, String selected, Consumer<String> onSelected, int selectorX, int selectorY) {
            closeResourceSelector();
            ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
            ItemSelectorWidget selector = new ItemSelectorWidget.Builder(FlowGraphDesignerScreen.this)
                .size(220, 240)
                .dismissOnSelect(true)
                .onClose(() -> closeResourceSelector(selectorRef[0]))
                .build();
            selector.setLayer(900);
            selector.setPriority(30);
            selectorRef[0] = selector;
            String createType = selectorCreateResourceType(field);
            if (createType != null) {
                selector.addItem("Create New", () -> createBindingResource(createType, onSelected));
            }
            for (String option : options.stream().filter(this::isRealOption).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
                selector.addItem(selectorLabel(field, option), () -> onSelected.accept(option));
            }
            selector.setSelectedItem(selectorLabel(field, selected));
            activeResourceSelector = selector;
            FlowGraphDesignerScreen.this.addDrawableChild(selector);
            int left = Math.clamp(selectorX, 8, Math.max(8, width - selector.getWidth() - 8));
            int top = Math.clamp(selectorY, 32, Math.max(32, height - selector.getHeight() - 20));
            selector.show(left, top);
        }

        private String selectorCreateResourceType(String field) {
            if (field == null || field.isBlank()) {
                return null;
            }
            if (field.endsWith(".functionId")) {
                return ReSyncResourceDragPayload.FUNCTION;
            }
            if ("flowId".equals(field) || "flowPredicate".equals(field) || field.endsWith("Flow") || field.contains("Flow")) {
                return ReSyncResourceDragPayload.FLOW;
            }
            return null;
        }

        private void closeResourceSelector() {
            closeResourceSelector(activeResourceSelector);
        }

        private void closeResourceSelector(ItemSelectorWidget selector) {
            if (selector != null) {
                selector.onClose = null;
                selector.hide();
                FlowGraphDesignerScreen.this.remove(selector);
            }
            if (selector == activeResourceSelector) {
                activeResourceSelector = null;
                pendingRecipeSelectionFields = new ArrayList<>();
            }
            setFocusedWidget(null);
        }

        @Override
        public void renderStudioOverlay(IDrawContext context, int mouseX, int mouseY, float delta) {
        }

        private boolean searchableSelectorField(String field) {
            return "output.material".equals(field) || "template.material".equals(field) || "base.material".equals(field) || "addition.material".equals(field)
                || field.endsWith("Flow") || "flowId".equals(field) || "flowPredicate".equals(field) || field.contains("Flow")
                || field.endsWith(".functionId") || functionInputCatalogSource(field) != null || functionInputBooleanField(field)
                || recipeSlotIndex(field) >= 0 || recipeIngredientIndex(field) >= 0;
        }

        private boolean functionInputBooleanField(String field) {
            FlowGraph.FunctionParameter parameter = functionInputParameter(field);
            return parameter != null && parameter.getType() != null && FlowDataType.BOOLEAN.isAssignableFrom(parameter.getType());
        }

        private List<String> normalizedSelectorOptions(List<String> choices, String selected) {
            List<String> options = new ArrayList<>();
            if (choices != null) {
                for (String choice : choices) {
                    if (choice != null && !choice.isBlank() && !options.contains(choice)) {
                        options.add(choice);
                    }
                }
            }
            if (selected != null && !selected.isBlank() && !"Loading".equals(selected) && !options.contains(selected)) {
                options.addFirst(selected);
            }
            if (options.isEmpty()) {
                options.add("No Options");
            }
            return options;
        }

        private String resolveSelectedOption(List<String> options, String selected) {
            if (selected != null && options.contains(selected)) {
                return selected;
            }
            if (selected != null) {
                String normalized = selected.toUpperCase(Locale.ROOT);
                if (options.contains(normalized)) {
                    return normalized;
                }
            }
            return options.isEmpty() ? null : options.getFirst();
        }

        private List<String> selectorOptions(String field) {
            return switch (field) {
                case "enabled", "allowMiniMessage" -> List.of("true", "false");
                case "type" -> recipeTypeOptions();
                case "output.material", "template.material", "base.material", "addition.material" -> recipeItemOptions();
                case "playerCountMode" -> List.of("real", "hidden", "fixed");
                case "mode" -> ReSyncResourceDragPayload.TEXT_TEMPLATE.equals(type)
                    ? List.of("frames", "typing", "scroll", "gradient", "blink", "random", "conditional")
                    : List.of();
                case "action" -> switch (type) {
                    case ReSyncResourceDragPayload.CHAT_RULE -> List.of("block", "replace", "flow", "channel");
                    case ReSyncResourceDragPayload.MESSAGE_RULE -> List.of("replace_section", "replace", "append", "prepend", "remove", "flow");
                    default -> List.of();
                };
                case "source" -> List.of("join", "quit", "kick", "death", "title", "actionbar", "bossbar", "openScreen", "packetText", "system");
                case "flowId", "flowPredicate", "craftedFlow", "deniedFlow", "cookedFlow", "privateMessageFlow", "mentionFlow" -> flowOptions();
                default -> {
                    String catalog = functionInputCatalogSource(field);
                    if (field.endsWith(".functionId")) {
                        yield functionOptions();
                    }
                    if (functionInputBooleanField(field)) {
                        yield List.of("true", "false");
                    }
                    if (catalog != null) {
                        yield catalogOptions(catalog);
                    }
                    yield recipeSlotIndex(field) >= 0 || recipeIngredientIndex(field) >= 0 ? recipeItemOptions() : List.of();
                }
            };
        }

        private boolean rebuildOnSelection(String field) {
            return "type".equals(field) || "playerCountMode".equals(field) || "action".equals(field) || field.endsWith(".functionId");
        }

        private String selectorLabel(String field, String value) {
            if (value == null || value.isBlank()) {
                return "none";
            }
            if (isRecipeItemSelectorField(field)) {
                return recipeItemSelectorLabel(value);
            }
            if (field.endsWith("Flow") || "flowId".equals(field) || "flowPredicate".equals(field) || field.contains("Flow")) {
                FlowManager manager = FlowManager.getInstance();
                return manager != null && !"none".equals(value) ? manager.getFlowName(serverId, value) : value;
            }
            if (field.endsWith(".functionId")) {
                FlowManager manager = FlowManager.getInstance();
                return manager != null && !"none".equals(value) ? manager.getFlowName(serverId, value) : value;
            }
            return formatOptionLabel(value);
        }

        private String formatOptionLabel(String value) {
            if (value == null || value.isBlank()) {
                return "none";
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

        private List<String> recipeTypeOptions() {
            return List.of("shaped", "shapeless", "furnace", "blasting", "smoking", "campfire", "stonecutting", "smithing_transform", "smithing_trim");
        }

        private List<String> materialOptions() {
            List<String> values = OptionCatalogCache.getInstance().getValues(serverId, MATERIAL_OPTIONS_SOURCE);
            if (!values.isEmpty()) {
                return values;
            }
            FlowManager manager = FlowManager.getInstance();
            if (manager != null && serverId != null) {
                manager.ensureFlowClient(serverId).requestOptionCatalog(MATERIAL_OPTIONS_SOURCE);
            }
            return FALLBACK_MATERIAL_OPTIONS;
        }

        private void ensureRecipeItemCatalogLoaded() {
            FlowManager manager = FlowManager.getInstance();
            if (manager == null || serverId == null) {
                return;
            }
            if (!OptionCatalogCache.getInstance().hasCatalog(serverId, RECIPE_ITEM_OPTIONS_SOURCE)) {
                manager.ensureFlowClient(serverId).requestOptionCatalog(RECIPE_ITEM_OPTIONS_SOURCE);
            }
            if (!OptionCatalogCache.getInstance().hasCatalog(serverId, "server:custom_content:provider")) {
                manager.ensureFlowClient(serverId).requestOptionCatalog("server:custom_content:provider");
            }
            for (String source : List.of(
                "server:custom_content:nexo_item",
                "server:custom_content:nexo_armor",
                "server:custom_content:nexo_block",
                "server:custom_content:nexo_furniture"
            )) {
                if (!OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
                    manager.ensureFlowClient(serverId).requestOptionCatalog(source);
                }
            }
        }

        private boolean isRecipeItemCatalogReady() {
            return OptionCatalogCache.getInstance().hasCatalog(serverId, RECIPE_ITEM_OPTIONS_SOURCE);
        }

        private List<String> recipeItemOptions() {
            ensureRecipeItemCatalogLoaded();
            if (!isRecipeItemCatalogReady()) {
                return List.of("Loading");
            }
            return mergedRecipeItemValues();
        }

        private List<String> mergedRecipeItemValues() {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            List<String> serverValues = OptionCatalogCache.getInstance().getValues(serverId, RECIPE_ITEM_OPTIONS_SOURCE);
            boolean hasReSync = false;
            for (String value : serverValues) {
                if (value != null && value.startsWith("content:")) {
                    values.add(value);
                    hasReSync = true;
                }
            }
            if (!hasReSync) {
                appendLocalReSyncRecipeValues(values);
            }
            appendProviderRecipeValues(values);
            for (String value : serverValues) {
                if (value != null && value.startsWith("provider:")) {
                    values.add(value);
                }
            }
            for (String material : materialOptions()) {
                if (material != null && !material.isBlank()) {
                    values.add(material);
                }
            }
            for (String value : serverValues) {
                if (value != null && !value.isBlank() && !value.contains(":")) {
                    values.add(value);
                }
            }
            return new ArrayList<>(values);
        }

        private void appendLocalReSyncRecipeValues(Set<String> values) {
            FlowManager manager = FlowManager.getInstance();
            if (manager == null || serverId == null) {
                return;
            }
            manager.getCustomContentForServer(serverId).values().stream()
                .filter(content -> content != null && content.getId() != null && !content.getId().isBlank())
                .filter(content -> {
                    String contentType = content.getType() != null ? content.getType().toLowerCase(Locale.ROOT) : "";
                    return Set.of("item", "armor", "block").contains(contentType);
                })
                .map(content -> "content:" + content.getId())
                .forEach(values::add);
        }

        private void appendProviderRecipeValues(Set<String> values) {
            for (String provider : providerOptions()) {
                if (provider == null || provider.isBlank() || "Loading".equals(provider) || "vanilla".equalsIgnoreCase(provider)) {
                    continue;
                }
                String providerKey = provider.toLowerCase(Locale.ROOT);
                LinkedHashSet<String> externalIds = new LinkedHashSet<>();
                for (String type : List.of("item", "armor", "block")) {
                    List<String> catalogAssets = providerCatalogAssets(type, provider);
                    if (!catalogAssets.isEmpty() && !catalogAssets.equals(List.of("Loading"))) {
                        externalIds.addAll(catalogAssets);
                    }
                }
                if (externalIds.isEmpty()) {
                    for (PackContentRegistry.PackAssetOption option : PackContentRegistry.get().assetOptions(provider)) {
                        if (option.id() != null && !option.id().isBlank()) {
                            externalIds.add(option.id());
                        }
                    }
                }
                for (String externalId : externalIds) {
                    values.add("provider:" + providerKey + ":" + externalId);
                }
            }
        }

        private String recipeItemGroupForValue(String value, OptionCatalogItem item) {
            if (item != null && !item.getGroup().isBlank()) {
                return item.getGroup();
            }
            if (value.startsWith("content:")) {
                return "ReSync";
            }
            if (value.startsWith("provider:")) {
                return "Providers";
            }
            return "Vanilla";
        }

        private Map<String, OptionCatalogItem> recipeItemCatalogByValue() {
            Map<String, OptionCatalogItem> byValue = new LinkedHashMap<>();
            for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, RECIPE_ITEM_OPTIONS_SOURCE)) {
                if (item == null) {
                    continue;
                }
                String value = item.getValue();
                if (value == null || value.isBlank()) {
                    continue;
                }
                byValue.putIfAbsent(value, item);
            }
            return byValue;
        }

        @Override
        public void onStudioCatalogRefreshed() {
            flushPendingRecipeItemSelector();
        }

        private void flushPendingRecipeItemSelector() {
            if (pendingRecipeSelectorField == null || !isRecipeItemCatalogReady()) {
                return;
            }
            openRecipeItemSelector(pendingRecipeSelectorField, pendingRecipeSelectorX, pendingRecipeSelectorY);
        }

        private boolean isRecipeItemSelectorField(String field) {
            if (field == null || field.isBlank()) {
                return false;
            }
            return field.endsWith(".material")
                || recipeSlotIndex(field) >= 0
                || recipeIngredientIndex(field) >= 0;
        }

        private String recipeItemSelectorLabel(String value) {
            if (value == null || value.isBlank()) {
                return "none";
            }
            for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, RECIPE_ITEM_OPTIONS_SOURCE)) {
                if (value.equals(item.getValue())) {
                    return item.getLabel();
                }
            }
            if (value.startsWith("provider:")) {
                int split = value.lastIndexOf(':');
                if (split > 0 && split < value.length() - 1) {
                    return value.substring(split + 1);
                }
            }
            return formatOptionLabel(value);
        }

        private String encodeRecipeItemValue(JsonObject object) {
            if (object == null) {
                return "";
            }
            String contentId = jsonText(object, "contentId");
            if (contentId.isBlank()) {
                contentId = jsonText(object, "customContentId");
            }
            if (contentId.isBlank()) {
                contentId = jsonText(object, "customContent");
            }
            if (!contentId.isBlank()) {
                return "content:" + contentId;
            }
            String provider = jsonText(object, "provider");
            String externalId = jsonText(object, "externalId");
            if (externalId.isBlank()) {
                externalId = jsonText(object, "nexo");
            }
            if (provider.isBlank() && !externalId.isBlank()) {
                provider = "nexo";
            }
            if (!provider.isBlank() && !externalId.isBlank()) {
                return "provider:" + provider.toLowerCase(Locale.ROOT) + ":" + externalId;
            }
            return jsonText(object, "material");
        }

        private void applyRecipeItemValue(JsonObject object, String value) {
            if (object == null) {
                return;
            }
            object.remove("contentId");
            object.remove("customContentId");
            object.remove("customContent");
            object.remove("provider");
            object.remove("externalId");
            object.remove("nexo");
            object.remove("material");
            object.remove("item");
            String selection = value == null ? "" : value.trim();
            if (selection.isBlank() || "none".equalsIgnoreCase(selection)) {
                return;
            }
            if (selection.startsWith("content:")) {
                object.addProperty("contentId", selection.substring("content:".length()));
                return;
            }
            if (selection.startsWith("provider:")) {
                String rest = selection.substring("provider:".length());
                int split = rest.indexOf(':');
                if (split > 0 && split < rest.length() - 1) {
                    object.addProperty("provider", rest.substring(0, split));
                    object.addProperty("externalId", rest.substring(split + 1));
                }
                return;
            }
            object.addProperty("material", selection);
        }

        private JsonObject recipeItemObject(String value) {
            JsonObject object = new JsonObject();
            applyRecipeItemValue(object, value);
            return object;
        }

        private boolean isRecipeItemPathField(String field) {
            return field != null && field.endsWith(".material");
        }

        private String recipeItemPathText(String field) {
            String[] parts = field.split("\\.", 2);
            JsonObject parent = jsonObject(parts[0]);
            return encodeRecipeItemValue(parent);
        }

        private void putRecipeItemPathText(String field, String value) {
            String[] parts = field.split("\\.", 2);
            JsonObject parent = jsonObject(parts[0]);
            if (!resource.has(parts[0]) || !resource.get(parts[0]).isJsonObject()) {
                resource.add(parts[0], parent);
            }
            applyRecipeItemValue(parent, value);
            if ("output".equals(parts[0]) && value != null && !value.isBlank() && jsonText(parent, "amount").isBlank()) {
                parent.addProperty("amount", 1);
            }
            resource.add(parts[0], parent);
        }

        private List<String> flowOptions() {
            FlowManager manager = FlowManager.getInstance();
            if (manager == null) {
                return List.of("none");
            }
            return CompactBindingSupport.flowOptions(serverId);
        }

        private List<String> functionOptions() {
            FlowManager manager = FlowManager.getInstance();
            if (manager == null) {
                return List.of("none");
            }
            return CompactBindingSupport.functionOptions(serverId);
        }

        private String functionInputCatalogSource(String field) {
            FlowGraph.FunctionParameter parameter = functionInputParameter(field);
            if (parameter == null) {
                return null;
            }
            String source = parameter.getOptionsSource();
            return source != null && !source.isBlank() ? source : null;
        }

        private FlowGraph.FunctionParameter functionInputParameter(String field) {
            String marker = ".inputs.";
            int index = field.indexOf(marker);
            if (index < 0) {
                return null;
            }
            String basePath = field.substring(0, index);
            String inputName = field.substring(index + marker.length());
            FlowGraph function = selectedFunction(basePath + ".functionId");
            if (function == null || function.getFunctionInputs() == null || inputName.isBlank()) {
                return null;
            }
            for (FlowGraph.FunctionParameter input : function.getFunctionInputs()) {
                if (input != null && inputName.equals(input.getName())) {
                    return input;
                }
            }
            return null;
        }

        private List<String> catalogOptions(String source) {
            List<String> values = OptionCatalogCache.getInstance().getValues(serverId, source);
            if (!values.isEmpty()) {
                return values;
            }
            if (!OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
                FlowManager manager = FlowManager.getInstance();
                if (manager != null) {
                    manager.ensureFlowClient(serverId).requestOptionCatalog(source);
                }
                return List.of("Loading");
            }
            return List.of();
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

        private boolean isRealOption(String value) {
            return value != null && !"Loading".equals(value) && !"No Options".equals(value);
        }

        private boolean isCodeField(String field) {
            return switch (field) {
                case "template", "sender", "receiver", "spy", "replacement", "text", "motdText", "format", "framesText", "colorsText" -> true;
                default -> false;
            };
        }

        private int codeFieldHeight(String field) {
            return switch (field) {
                case "motdText" -> 46;
                case "text", "template", "format", "replacement", "framesText", "colorsText" -> dynamicCodeFieldHeight(field);
                default -> 82;
            };
        }

        private int dynamicCodeFieldHeight(String field) {
            String value = jsonPathText(field);
            int lines = value == null || value.isBlank() ? 4 : Math.clamp(value.split("\\R", -1).length, 4, 10);
            return Math.clamp(lines * 18 + 28, 100, 208);
        }

        private String resourceSummary() {
            return switch (type) {
                case ReSyncResourceDragPayload.MOTD_PROFILE -> firstFilled(jsonPathText("motdText"), "Server List");
                case ReSyncResourceDragPayload.RECIPE_DEFINITION -> firstFilled(jsonText(jsonObject("output"), "material"), "Crafting");
                case ReSyncResourceDragPayload.TEXT_TEMPLATE -> firstFilled(jsonText("text"), "Template");
                case ReSyncResourceDragPayload.MESSAGE_RULE -> firstFilled(jsonText("source"), "Rewrite");
                case ReSyncResourceDragPayload.IGNORE_LIST -> jsonArraySize("players") + " Players";
                case ReSyncResourceDragPayload.CHAT_CHANNEL -> firstFilled(jsonText("displayName"), jsonText("prefix"), "Channel");
                case ReSyncResourceDragPayload.CHAT_FORMAT -> "Chat Layout";
                case ReSyncResourceDragPayload.CHAT_RULE -> firstFilled(jsonText("contains"), "Rule");
                case ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT -> "Private Messages";
                case ReSyncResourceDragPayload.MENTION_STYLE -> "Mention Style";
                default -> id;
            };
        }

        private String firstFilled(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return "";
        }

        private boolean handleRecipePreviewClick(int mouseX, int mouseY, int button) {
            if (recipePreviewLayout == null || recipePreviewScale <= 0) {
                return false;
            }
            String field = recipeFieldAt(mouseX, mouseY);
            if (field.isBlank()) {
                return false;
            }
            selectedRecipeField = field;
            recipeStroke = SlotInteractionGrid.beginStroke(recipeSlotRects(), mouseX, mouseY);
            updateRecipeStrokeTargets();
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                recipeStrokeMode = RecipeStrokeMode.ERASE;
                pressedRecipeField = field;
                dragRecipeTargetField = field;
                draggingRecipeField = false;
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                pressedRecipeField = field;
                dragRecipeTargetField = field;
                draggingRecipeField = false;
                String fieldValue = jsonPathText(field);
                if (!fieldValue.isBlank()) {
                    recipeBrushValue = fieldValue;
                }
                recipeStrokeValue = !fieldValue.isBlank() ? fieldValue : recipeBrushValue;
                recipeStrokeMode = recipeStrokeValue.isBlank() ? RecipeStrokeMode.SELECT : RecipeStrokeMode.PAINT;
                return true;
            }
            return false;
        }

        private boolean handleRecipePreviewDrag(int mouseX, int mouseY) {
            if (pressedRecipeField == null || pressedRecipeField.isBlank()) {
                return false;
            }
            if (recipeStroke != null) {
                recipeStroke.moveTo(mouseX, mouseY);
                updateRecipeStrokeTargets();
            }
            String field = recipeFieldAt(mouseX, mouseY);
            if (field.isBlank()) {
                dragRecipeTargetField = null;
                return true;
            }
            dragRecipeTargetField = field;
            if (!Objects.equals(field, pressedRecipeField) || (recipeStroke != null && recipeStroke.slots().size() > 1)) {
                draggingRecipeField = true;
            }
            return true;
        }

        private boolean handleRecipePreviewRelease(int mouseX, int mouseY) {
            if (pressedRecipeField == null || pressedRecipeField.isBlank()) {
                return false;
            }
            String source = pressedRecipeField;
            String target = recipeFieldAt(mouseX, mouseY);
            boolean wasDragging = draggingRecipeField;
            SlotInteractionGrid.Stroke finishedStroke = recipeStroke;
            RecipeStrokeMode finishedMode = recipeStrokeMode;
            String finishedValue = recipeStrokeValue;
            pressedRecipeField = null;
            dragRecipeTargetField = null;
            dragRecipeTargetFields.clear();
            recipeStroke = null;
            recipeStrokeMode = RecipeStrokeMode.NONE;
            recipeStrokeValue = "";
            draggingRecipeField = false;
            if (finishedMode == RecipeStrokeMode.ERASE) {
                commitRecipeStroke(finishedStroke, "", true);
                selectedRecipeField = null;
                reloadFields();
                return true;
            }
            if (finishedMode == RecipeStrokeMode.SELECT) {
                pendingRecipeSelectionFields = recipeStrokeFields(finishedStroke);
                showRecipeMaterialSelector(source, mouseX, mouseY);
                return true;
            }
            if (wasDragging && target.isBlank()) {
                return true;
            }
            if (wasDragging && finishedMode == RecipeStrokeMode.PAINT && !finishedValue.isBlank()) {
                commitRecipeStroke(finishedStroke, finishedValue, false);
                recipeBrushValue = finishedValue;
                selectedRecipeField = target.isBlank() ? source : target;
                reloadFields();
                return true;
            }
            showRecipeMaterialSelector(source, mouseX, mouseY);
            return true;
        }

        private void updateRecipeStrokeTargets() {
            dragRecipeTargetFields.clear();
            if (recipeStroke == null || recipePreviewLayout == null) {
                return;
            }
            List<RecipeSlotTarget> targets = recipeSlotTargets(normalizedRecipeType(), recipePreviewLayout);
            for (int slot : recipeStroke.slots()) {
                if (slot >= 0 && slot < targets.size()) {
                    dragRecipeTargetFields.add(targets.get(slot).field());
                }
            }
        }

        private void commitRecipeStroke(SlotInteractionGrid.Stroke stroke, String value, boolean erase) {
            List<String> fields = recipeStrokeFields(stroke);
            if (fields.isEmpty()) {
                return;
            }
            captureResourceSnapshot();
            resourceHistoryBatch = true;
            try {
                for (String field : fields) {
                    if (erase) {
                        deleteRecipeField(field);
                    } else if (value != null && !value.isBlank()) {
                        putJsonText(field, value);
                    }
                }
            } finally {
                resourceHistoryBatch = false;
            }
        }

        private List<String> recipeStrokeFields(SlotInteractionGrid.Stroke stroke) {
            if (stroke == null || stroke.isEmpty() || recipePreviewLayout == null) {
                return List.of();
            }
            List<RecipeSlotTarget> targets = recipeSlotTargets(normalizedRecipeType(), recipePreviewLayout);
            List<String> fields = new ArrayList<>();
            for (int slot : stroke.slots()) {
                if (slot >= 0 && slot < targets.size()) {
                    String field = targets.get(slot).field();
                    if (!fields.contains(field)) {
                        fields.add(field);
                    }
                }
            }
            return fields;
        }

        private String recipeFieldAt(int mouseX, int mouseY) {
            String recipeType = normalizedRecipeType();
            List<RecipeSlotTarget> targets = recipeSlotTargets(recipeType, recipePreviewLayout);
            List<SlotInteractionGrid.SlotRect> slots = new ArrayList<>();
            for (int i = 0; i < targets.size(); i++) {
                int[] point = targets.get(i).point();
                if (point == null || point.length < 2) {
                    continue;
                }
                int size = Math.max(16, 16 * recipePreviewScale);
                slots.add(new SlotInteractionGrid.SlotRect(i, recipePreviewX + point[0] * recipePreviewScale, recipePreviewY + point[1] * recipePreviewScale, size));
            }
            int slot = SlotInteractionGrid.hitSlot(slots, mouseX, mouseY);
            return slot >= 0 && slot < targets.size() ? targets.get(slot).field() : "";
        }

        private List<SlotInteractionGrid.SlotRect> recipeSlotRects() {
            String recipeType = normalizedRecipeType();
            List<RecipeSlotTarget> targets = recipeSlotTargets(recipeType, recipePreviewLayout);
            List<SlotInteractionGrid.SlotRect> slots = new ArrayList<>();
            for (int i = 0; i < targets.size(); i++) {
                int[] point = targets.get(i).point();
                if (point == null || point.length < 2) {
                    continue;
                }
                int size = Math.max(16, 16 * recipePreviewScale);
                slots.add(new SlotInteractionGrid.SlotRect(i, recipePreviewX + point[0] * recipePreviewScale, recipePreviewY + point[1] * recipePreviewScale, size));
            }
            return slots;
        }

        private List<RecipeSlotTarget> recipeSlotTargets(String recipeType, RecipeStationLayout layout) {
            if (layout == null) {
                return List.of();
            }
            List<RecipeSlotTarget> targets = new ArrayList<>();
            if (isSmithingRecipe(recipeType)) {
                String[] fields = new String[]{"template.material", "base.material", "addition.material"};
                for (int i = 0; i < layout.templates().length && i < fields.length; i++) {
                    targets.add(new RecipeSlotTarget(fields[i], layout.templates()[i]));
                }
                targets.add(new RecipeSlotTarget("output.material", layout.output()));
                return targets;
            }
            int[][] points = layout.ingredients();
            for (int i = 0; i < points.length; i++) {
                String field;
                if (isCookingRecipe(recipeType) || "stonecutting".equals(recipeType) || "shapeless".equals(recipeType)) {
                    field = "ingredient" + (i + 1);
                } else {
                    field = "slot" + (i + 1);
                }
                targets.add(new RecipeSlotTarget(field, points[i]));
            }
            targets.add(new RecipeSlotTarget("output.material", layout.output()));
            return targets;
        }

        private void drawRecipeSlotHighlights(IDrawContext context, String recipeType, RecipeStationLayout layout, int viewX, int viewY, int scale) {
            int selectedColor = ThemeManager.getAnimatedColor("recipe_slot_selected".hashCode(), (ThemeManager.getAccent("nice").getAccentColor() & 0x00FFFFFF) | 0x99000000);
            for (RecipeSlotTarget target : recipeSlotTargets(recipeType, layout)) {
                boolean selected = Objects.equals(target.field(), selectedRecipeField);
                boolean dragTarget = Objects.equals(target.field(), dragRecipeTargetField) || dragRecipeTargetFields.contains(target.field());
                if ((!selected && !dragTarget) || target.point() == null || target.point().length < 2) {
                    continue;
                }
                int size = Math.max(16, 16 * scale);
                SlotInteractionGrid.drawHighlight(context, viewX + target.point()[0] * scale, viewY + target.point()[1] * scale, size, size, selectedColor, selected);
            }
        }

        private void deleteRecipeField(String field) {
            if (resourceHistoryBatch) {
                deleteRecipeFieldRaw(field);
                return;
            }
            captureResourceSnapshot();
            resourceHistoryBatch = true;
            try {
                deleteRecipeFieldRaw(field);
            } finally {
                resourceHistoryBatch = false;
            }
        }

        private void deleteRecipeFieldRaw(String field) {
            if ("output.material".equals(field)) {
                JsonObject output = jsonObject("output");
                output.remove("material");
                output.remove("amount");
            } else if ("template.material".equals(field) || "base.material".equals(field) || "addition.material".equals(field)) {
                putJsonPathText(field, "");
            } else if (recipeSlotIndex(field) >= 0) {
                putRecipeSlotText(recipeSlotIndex(field), "");
            } else if (recipeIngredientIndex(field) >= 0) {
                putRecipeIngredientText(recipeIngredientIndex(field), "");
            }
        }

        private void moveRecipeField(String source, String target) {
            String material = jsonPathText(source);
            if (material.isBlank()) {
                return;
            }
            captureResourceSnapshot();
            resourceHistoryBatch = true;
            try {
                putJsonText(target, material);
                putJsonText(source, "");
                if ("output.material".equals(source)) {
                    jsonObject("output").remove("amount");
                }
                if ("output.material".equals(target) && jsonText(jsonObject("output"), "amount").isBlank()) {
                    JsonObject output = jsonObject("output");
                    output.addProperty("amount", 1);
                    resource.add("output", output);
                }
            } finally {
                resourceHistoryBatch = false;
            }
        }

        private boolean changeRecipeItemAmount(int mouseX, int mouseY, double verticalAmount) {
            if (recipePreviewLayout == null || recipePreviewScale <= 0) {
                return false;
            }
            String field = recipeFieldAt(mouseX, mouseY);
            if (field.isBlank() || jsonPathText(field).isBlank()) {
                return false;
            }
            int amount = recipeFieldAmount(field);
            int nextAmount = Math.clamp(amount + (verticalAmount > 0 ? 1 : -1), 1, 64);
            if (amount == nextAmount) {
                return false;
            }
            captureResourceSnapshot();
            putRecipeFieldAmount(field, nextAmount);
            return true;
        }

        private void showRecipeMaterialSelector(String field, int mouseX, int mouseY) {
            ensureRecipeItemCatalogLoaded();
            if (!isRecipeItemCatalogReady()) {
                pendingRecipeSelectorField = field;
                pendingRecipeSelectorX = mouseX;
                pendingRecipeSelectorY = mouseY;
                return;
            }
            openRecipeItemSelector(field, mouseX, mouseY);
        }

        private void openRecipeItemSelector(String field, int mouseX, int mouseY) {
            pendingRecipeSelectorField = null;
            String selected = jsonPathText(field);
            List<String> values = mergedRecipeItemValues();
            Map<String, OptionCatalogItem> catalogByValue = recipeItemCatalogByValue();
            closeResourceSelector();
            ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
            ItemSelectorWidget.Builder builder = new ItemSelectorWidget.Builder(FlowGraphDesignerScreen.this)
                .size(220, 240)
                .dismissOnSelect(true)
                .emptyMessage("No Items")
                .onClose(() -> closeResourceSelector(selectorRef[0]));
            builder.beginBatch();
            String lastGroup = null;
            boolean hasSelected = false;
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                OptionCatalogItem item = catalogByValue.get(value);
                String group = recipeItemGroupForValue(value, item);
                if (!group.isBlank() && !group.equals(lastGroup)) {
                    builder.addSectionHeader(group);
                    lastGroup = group;
                }
                String label = item != null ? item.getLabel() : recipeItemSelectorLabel(value);
                if (value.equals(selected)) {
                    hasSelected = true;
                }
                String description = item != null ? item.getDescription() : "";
                String searchTerms = value + " " + group + " " + description;
                builder.addItem(label, description, searchTerms, () -> applyRecipeItemSelection(field, value));
            }
            if (!selected.isBlank() && !hasSelected) {
                builder.addItem(recipeItemSelectorLabel(selected), "", selected, () -> applyRecipeItemSelection(field, selected));
            }
            ItemSelectorWidget selector = builder.endBatch().build();
            selectorRef[0] = selector;
            selector.setLayer(900);
            selector.setPriority(30);
            selector.setSelectedItem(recipeItemSelectorLabel(selected));
            activeResourceSelector = selector;
            FlowGraphDesignerScreen.this.addDrawableChild(selector);
            int left = Math.clamp(mouseX, 8, Math.max(8, width - selector.getWidth() - 8));
            int top = Math.clamp(mouseY, 32, Math.max(32, height - selector.getHeight() - 20));
            selector.show(left, top);
        }

        private void applyRecipeItemSelection(String field, String value) {
            if (!isRealOption(value)) {
                return;
            }
            recipeBrushValue = value;
            if (!pendingRecipeSelectionFields.isEmpty()) {
                captureResourceSnapshot();
                resourceHistoryBatch = true;
                try {
                    for (String pendingField : pendingRecipeSelectionFields) {
                        putJsonText(pendingField, value);
                    }
                } finally {
                    resourceHistoryBatch = false;
                    pendingRecipeSelectionFields = new ArrayList<>();
                }
            } else {
                putJsonText(field, value);
            }
            reloadFields();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (activeResourceSelector != null && activeResourceSelector.visible && activeResourceSelector.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                && ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type)
                && handleRecipePreviewClick((int) mouseX, (int) mouseY, button);
        }

        @Override
        public boolean hasActiveStudioSelector() {
            return activeResourceSelector != null && activeResourceSelector.visible;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (activeResourceSelector != null && activeResourceSelector.visible && activeResourceSelector.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
            return (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                && ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type)
                && handleRecipePreviewRelease((int) mouseX, (int) mouseY);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (activeResourceSelector != null && activeResourceSelector.visible && activeResourceSelector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
            return (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                && ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type)
                && handleRecipePreviewDrag((int) mouseX, (int) mouseY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (activeResourceSelector != null && activeResourceSelector.visible && activeResourceSelector.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
                return true;
            }
            return ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type) && changeRecipeItemAmount((int) mouseX, (int) mouseY, verticalAmount);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (activeResourceSelector != null && activeResourceSelector.visible) {
                return activeResourceSelector.keyPressed(keyCode, scanCode, modifiers);
            }
            return ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type) && handleStudioHistoryShortcut(keyCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            return activeResourceSelector != null && activeResourceSelector.visible && activeResourceSelector.charTyped(chr, modifiers);
        }

        private List<String> editorFields() {
            return switch (type) {
                case ReSyncResourceDragPayload.CHAT_CHANNEL -> List.of("displayName", "prefix", "format", "range", "speakPermission", "readPermission", "allowMiniMessage", "miniMessagePermission");
                case ReSyncResourceDragPayload.CHAT_FORMAT -> List.of("template");
                case ReSyncResourceDragPayload.CHAT_RULE -> chatRuleFields();
                case ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT -> List.of("sender", "receiver", "spy", "privateMessageFlow");
                case ReSyncResourceDragPayload.MENTION_STYLE -> List.of("template", "mentionFlow");
                case ReSyncResourceDragPayload.IGNORE_LIST -> List.of("player1", "player2", "player3", "player4", "player5");
                case ReSyncResourceDragPayload.MOTD_PROFILE -> motdFields();
                case ReSyncResourceDragPayload.MESSAGE_RULE -> messageRuleFields();
                case ReSyncResourceDragPayload.RECIPE_DEFINITION -> recipeFields();
                case ReSyncResourceDragPayload.TEXT_TEMPLATE -> textTemplateFields();
                default -> List.of("displayName");
            };
        }

        private List<String> chatRuleFields() {
            List<String> fields = new ArrayList<>(List.of("contains", "action"));
            String action = jsonText("action");
            if ("replace".equalsIgnoreCase(action)) {
                fields.add("replacement");
            } else if ("channel".equalsIgnoreCase(action)) {
                fields.add("channel");
            } else if ("flow".equalsIgnoreCase(action)) {
                fields.add("flowId");
            }
            return fields;
        }

        private List<String> messageRuleFields() {
            List<String> fields = new ArrayList<>(List.of("source", "contains", "replacement", "action"));
            String action = jsonText("action");
            if ("flow".equalsIgnoreCase(action)) {
                fields.add("flowPredicate");
                fields.add("flowId");
            }
            fields.add("priority");
            fields.add("enabled");
            return fields;
        }

        private List<String> recipeFields() {
            String recipeType = normalizedRecipeType();
            List<String> fields = new ArrayList<>(List.of("type"));
            if (isCookingRecipe(recipeType)) {
                fields.add("experience");
                fields.add("cookingTime");
                fields.add("cookedBinding");
            } else if ("stonecutting".equals(recipeType)) {
                fields.add("craftedBinding");
            } else if ("shapeless".equals(recipeType)) {
                fields.add("craftedBinding");
            } else if (!isSmithingRecipe(recipeType)) {
                fields.add("craftedBinding");
            }
            fields.add("conditions.permission");
            fields.add("conditions.world");
            fields.add("conditionBinding");
            fields.add("deniedBinding");
            return fields;
        }

        private List<String> functionInputFields(String basePath) {
            FlowGraph function = selectedFunction(basePath + ".functionId");
            if (function == null || function.getFunctionInputs() == null || function.getFunctionInputs().isEmpty()) {
                return List.of();
            }
            List<String> fields = new ArrayList<>();
            for (FlowGraph.FunctionParameter input : function.getFunctionInputs()) {
                if (input != null && input.getName() != null && !input.getName().isBlank()) {
                    fields.add(basePath + ".inputs." + input.getName());
                }
            }
            return fields;
        }

        private FlowGraph selectedFunction(String field) {
            return selectedFunction(field, null);
        }

        private FlowGraph selectedFunction(String field, CompactBindingSupport.FunctionShape shape) {
            String functionId = jsonPathText(field);
            if (functionId.isBlank()) {
                return null;
            }
            return CompactBindingSupport.selectedFunction(serverId, functionId, shape);
        }

        private FlowGraph selectedFunctionById(String functionId) {
            if (functionId == null || functionId.isBlank() || "none".equalsIgnoreCase(functionId)) {
                return null;
            }
            return CompactBindingSupport.selectedFunction(serverId, functionId, null);
        }

        private List<String> motdFields() {
            List<String> fields = new ArrayList<>(List.of("motdText", "priority", "playerCountMode"));
            if ("fixed".equalsIgnoreCase(jsonText("playerCountMode"))) {
                fields.add("onlinePlayers");
                fields.add("maxPlayers");
            }
            return fields;
        }

        private List<String> textTemplateFields() {
            return List.of("mode", "text", "framesText", "frameMillis", "width", "visibleCharacters", "colorsText");
        }

        private String fieldLabel(String field) {
            if (field.contains(".inputs.")) {
                String name = field.substring(field.lastIndexOf('.') + 1).replace('_', ' ');
                return name.isBlank() ? "Input" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
            String knownLabel = switch (field) {
                case "displayName" -> "Name";
                case "source" -> "Source";
                case "contains" -> "Find";
                case "replacement" -> "Replace With";
                case "action" -> "Action";
                case "motdText" -> "MOTD";
                case "speakPermission" -> "Speak";
                case "readPermission" -> "Read";
                case "allowMiniMessage" -> "Allow MiniMessage";
                case "miniMessagePermission" -> "MiniMessage Permission";
                case "privateMessageFlow" -> "Message Flow";
                case "mentionFlow" -> "Mention Flow";
                case "playerCountMode" -> "Player Count";
                case "onlinePlayers" -> "Online";
                case "maxPlayers" -> "Max Players";
                case "framesText" -> "Frames";
                case "colorsText" -> "Colors";
                case "flowPredicate" -> "Condition";
                case "conditions.predicate.functionId" -> "Condition Function";
                case "conditionBinding" -> "Condition";
                case "flowId" -> "Flow";
                case "output.material" -> "Output";
                case "output.amount" -> "Amount";
                case "conditions.permission" -> "Permission";
                case "conditions.world" -> "World";
                case "craftedBinding" -> "Craft Action";
                case "craftedFlow" -> "Craft Flow";
                case "craftedAction.functionId" -> "Craft Function";
                case "deniedBinding" -> "Deny Action";
                case "deniedFlow" -> "Deny Flow";
                case "deniedAction.functionId" -> "Deny Function";
                case "cookedBinding" -> "Cook Action";
                case "cookedFlow" -> "Cook Flow";
                case "cookedAction.functionId" -> "Cook Function";
                default -> null;
            };
            if (knownLabel != null) {
                return knownLabel;
            }
            StringBuilder label = new StringBuilder();
            for (int i = 0; i < field.length(); i++) {
                char c = field.charAt(i);
                if (i == 0) {
                    label.append(Character.toUpperCase(c));
                } else if (Character.isDigit(c) && Character.isLetter(field.charAt(i - 1))) {
                    label.append(' ').append(c);
                } else if (Character.isUpperCase(c)) {
                    label.append(' ').append(c);
                } else {
                    label.append(c);
                }
            }
            return label.toString();
        }

        private void putJsonText(String field, String value) {
            if (ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type)) {
                captureResourceSnapshot();
            }
            if ("motdText".equals(field)) {
                putMotdText(value);
                return;
            }
            if (ReSyncResourceDragPayload.MOTD_PROFILE.equals(type) && "playerCountMode".equals(field) && !"fixed".equalsIgnoreCase(value)) {
                resource.remove("onlinePlayers");
                resource.remove("maxPlayers");
            }
            if (ReSyncResourceDragPayload.TEXT_TEMPLATE.equals(type) && "text".equals(field)) {
                putTemplateText(value);
                return;
            }
            if ("framesText".equals(field)) {
                putJsonArrayLines("frames", value);
                return;
            }
            if ("colorsText".equals(field)) {
                putJsonArrayLines("colors", value);
                return;
            }
            if (playerIndex(field) >= 0) {
                putPlayerText(field, value);
                return;
            }
            if (recipeSlotIndex(field) >= 0) {
                putRecipeSlotText(recipeSlotIndex(field), value);
                return;
            }
            if (recipeIngredientIndex(field) >= 0) {
                putRecipeIngredientText(recipeIngredientIndex(field), value);
                return;
            }
            if (ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type) && isRecipeItemPathField(field)) {
                putRecipeItemPathText(field, value);
                return;
            }
            if (field.endsWith(".functionId")) {
                putFunctionIdPathText(field, value);
                return;
            }
            if (field.contains(".")) {
                putJsonPathText(field, value);
                return;
            }
            if (value == null || value.isBlank()) {
                resource.remove(field);
                return;
            }
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                resource.addProperty(field, Boolean.parseBoolean(value));
                return;
            }
            try {
                resource.addProperty(field, Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                resource.addProperty(field, value);
            }
        }

        private String jsonPathText(String field) {
            if ("motdText".equals(field)) {
                return motdText();
            }
            if ("framesText".equals(field)) {
                return jsonArrayLines("frames");
            }
            if ("colorsText".equals(field)) {
                return jsonArrayLines("colors");
            }
            if (playerIndex(field) >= 0) {
                return playerText(field);
            }
            if (recipeSlotIndex(field) >= 0) {
                return recipeSlotText(recipeSlotIndex(field));
            }
            if (recipeIngredientIndex(field) >= 0) {
                return recipeIngredientText(recipeIngredientIndex(field));
            }
            if (ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type) && isRecipeItemPathField(field)) {
                return recipeItemPathText(field);
            }
            FlowGraph.FunctionParameter functionInput = functionInputParameter(field);
            if (functionInput != null) {
                String configured = jsonPathTextRaw(field);
                if (!configured.isBlank()) {
                    return configured;
                }
                if (functionInput.getDefaultValue() != null && !functionInput.getDefaultValue().isBlank()) {
                    return functionInput.getDefaultValue();
                }
                return functionInputContextDefault(field, functionInput);
            }
            if (!field.contains(".")) {
                JsonElement element = resource.get(field);
                return element != null && element.isJsonArray() ? "" : jsonText(field);
            }
            return jsonPathTextRaw(field);
        }

        private String functionInputContextDefault(String field, FlowGraph.FunctionParameter input) {
            if (input == null || input.getType() == null) {
                return "";
            }
            FlowDataType type = input.getType();
            if (FlowDataType.BOOLEAN.isAssignableFrom(type)) {
                return "false";
            }
            if (FlowDataType.PLAYER.isAssignableFrom(type)) {
                return "$player";
            }
            if (FlowDataType.ITEM.isAssignableFrom(type) || FlowDataType.MATERIAL.isAssignableFrom(type)) {
                if (field.startsWith("craftedAction.inputs.") || field.startsWith("cookedAction.inputs.")) {
                    return "$event.output";
                }
                if (field.startsWith("deniedAction.inputs.")) {
                    return "$event.source";
                }
            }
            return "";
        }

        private String jsonPathTextRaw(String field) {
            JsonElement element = jsonPathElement(field);
            return element != null && !element.isJsonNull() && element.isJsonPrimitive() ? element.getAsString() : "";
        }

        private List<String> worldConditionValues() {
            JsonObject conditions = jsonObject("conditions");
            List<String> worlds = new ArrayList<>();
            JsonArray array = conditions.has("worlds") && conditions.get("worlds").isJsonArray() ? conditions.getAsJsonArray("worlds") : new JsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonNull() && !element.getAsString().isBlank() && !worlds.contains(element.getAsString())) {
                    worlds.add(element.getAsString());
                }
            }
            String legacyWorld = jsonText(conditions, "world");
            if (!legacyWorld.isBlank() && !worlds.contains(legacyWorld)) {
                worlds.addFirst(legacyWorld);
            }
            return worlds;
        }

        private void putWorldConditionValues(List<String> values) {
            JsonObject conditions = jsonObject("conditions");
            conditions.remove("world");
            JsonArray array = new JsonArray();
            if (values != null) {
                for (String value : values) {
                    if (value != null && !value.isBlank() && !"Loading".equals(value) && !"Any".equalsIgnoreCase(value)) {
                        array.add(value);
                    }
                }
            }
            if (array.isEmpty()) {
                conditions.remove("worlds");
            } else {
                conditions.add("worlds", array);
            }
            if (conditions.size() == 0) {
                resource.remove("conditions");
            } else {
                resource.add("conditions", conditions);
            }
        }

        private String motdText() {
            String line1 = jsonText("line1");
            String line2 = jsonText("line2");
            if (line1.isBlank() && line2.isBlank()) {
                return "";
            }
            return firstMotdLine(line1) + "\n" + firstMotdLine(line2);
        }

        private void putMotdText(String value) {
            String[] lines = (value == null ? "" : value).split("\\R", -1);
            String line1 = lines.length > 0 ? lines[0] : "";
            String line2 = lines.length > 1 ? lines[1] : "";
            if (line1.isBlank()) {
                resource.remove("line1");
            } else {
                resource.addProperty("line1", line1);
            }
            if (line2.isBlank()) {
                resource.remove("line2");
            } else {
                resource.addProperty("line2", line2);
            }
            resource.remove("mode");
            resource.remove("frames");
            resource.remove("frameMillis");
            resource.remove("rotationMillis");
            resource.remove("line1Frames");
            resource.remove("line2Frames");
            resource.remove("match");
            resource.remove("versionText");
            resource.remove("protocolVersion");
            resource.remove("protocolText");
            resource.remove("samplePlayers");
            resource.remove("countMode");
            resource.remove("fakePlayers");
            resource.remove("overrideMaxPlayers");
        }

        private String firstMotdLine(String value) {
            String[] lines = (value == null ? "" : value).split("\\R", -1);
            return lines.length > 0 ? lines[0] : "";
        }

        private void putTemplateText(String value) {
            if (value == null || value.isBlank()) {
                resource.remove("text");
            } else {
                resource.addProperty("text", value);
            }
        }

        private String jsonArrayLines(String key) {
            JsonArray array = resource.has(key) && resource.get(key).isJsonArray() ? resource.getAsJsonArray(key) : new JsonArray();
            List<String> values = new ArrayList<>();
            for (JsonElement element : array) {
                if (!element.isJsonNull()) {
                    values.add(element.getAsString());
                }
            }
            return String.join("\n", values);
        }

        private void putJsonArrayLines(String key, String value) {
            JsonArray array = new JsonArray();
            if (value != null) {
                for (String line : value.split("\\R", -1)) {
                    String trimmed = line.trim();
                    if (!trimmed.isBlank()) {
                        array.add(trimmed);
                    }
                }
            }
            if (array.isEmpty()) {
                resource.remove(key);
                return;
            }
            resource.add(key, array);
        }

        private String playerText(String field) {
            int index = playerIndex(field);
            if (index < 0) {
                return "";
            }
            JsonArray players = resource.has("players") && resource.get("players").isJsonArray() ? resource.getAsJsonArray("players") : new JsonArray();
            return index < players.size() && !players.get(index).isJsonNull() ? players.get(index).getAsString() : "";
        }

        private void putPlayerText(String field, String value) {
            int index = playerIndex(field);
            if (index < 0) {
                return;
            }
            JsonArray players = resource.has("players") && resource.get("players").isJsonArray() ? resource.getAsJsonArray("players") : new JsonArray();
            resource.add("players", players);
            while (players.size() <= index) {
                players.add("");
            }
            players.set(index, new JsonPrimitive(value == null ? "" : value.trim()));
        }

        private int playerIndex(String field) {
            if (field == null || !field.startsWith("player") || field.length() <= "player".length()) {
                return -1;
            }
            try {
                return Math.max(0, Integer.parseInt(field.substring("player".length())) - 1);
            } catch (NumberFormatException exception) {
                return -1;
            }
        }

        private int recipeSlotIndex(String field) {
            if (field == null || !field.startsWith("slot") || field.length() <= "slot".length()) {
                return -1;
            }
            try {
                int index = Integer.parseInt(field.substring("slot".length())) - 1;
                return index >= 0 && index <= 8 ? index : -1;
            } catch (NumberFormatException exception) {
                return -1;
            }
        }

        private int recipeIngredientIndex(String field) {
            if (field == null || !field.startsWith("ingredient") || field.length() <= "ingredient".length()) {
                return -1;
            }
            try {
                int index = Integer.parseInt(field.substring("ingredient".length())) - 1;
                return index >= 0 && index <= 8 ? index : -1;
            } catch (NumberFormatException exception) {
                return -1;
            }
        }

        private String recipeIngredientText(int index) {
            if (index < 0) {
                return "";
            }
            JsonArray ingredients = resource.has("ingredients") && resource.get("ingredients").isJsonArray() ? resource.getAsJsonArray("ingredients") : new JsonArray();
            if (index >= ingredients.size()) {
                if (index == 0 && resource.has("ingredient")) {
                    return ingredientLabel(resource.get("ingredient"));
                }
                return "";
            }
            return ingredientLabel(ingredients.get(index));
        }

        private String recipeSlotText(int index) {
            if (index < 0) {
                return "";
            }
            JsonObject keys = jsonObject("keys");
            JsonElement ingredient = keys.get(recipeSlotSymbol(index));
            if (ingredient != null) {
                return ingredientLabel(ingredient);
            }
            if (!"shapeless".equals(normalizedRecipeType())) {
                return "";
            }
            return recipeIngredientText(index);
        }

        private void putRecipeSlotText(int index, String value) {
            if (index < 0) {
                return;
            }
            JsonArray shape = resource.has("shape") && resource.get("shape").isJsonArray() ? resource.getAsJsonArray("shape") : new JsonArray();
            resource.add("shape", shape);
            while (shape.size() < 3) {
                shape.add("   ");
            }
            JsonObject keys = jsonObject("keys");
            resource.add("keys", keys);
            String symbol = recipeSlotSymbol(index);
            String material = value == null ? "" : value.trim();
            char shapeSymbol = material.isBlank() || "none".equalsIgnoreCase(material) ? ' ' : symbol.charAt(0);
            for (int row = 0; row < 3; row++) {
                String existing = row < shape.size() && !shape.get(row).isJsonNull() ? shape.get(row).getAsString() : "";
                shape.set(row, new JsonPrimitive(paddedShapeRow(existing, row, index, shapeSymbol)));
            }
            if (material.isBlank() || "none".equalsIgnoreCase(material)) {
                keys.remove(symbol);
                removeRecipeIngredientIndex(index);
                return;
            }
            keys.add(symbol, recipeItemObject(material));
        }

        private void removeRecipeIngredientIndex(int index) {
            JsonArray ingredients = resource.has("ingredients") && resource.get("ingredients").isJsonArray() ? resource.getAsJsonArray("ingredients") : null;
            if (ingredients == null || index < 0 || index >= ingredients.size()) {
                return;
            }
            ingredients.set(index, new JsonPrimitive(""));
            while (!ingredients.isEmpty()) {
                JsonElement last = ingredients.get(ingredients.size() - 1);
                if (!last.isJsonNull() && !(last.isJsonPrimitive() && last.getAsString().isBlank())) {
                    break;
                }
                ingredients.remove(ingredients.size() - 1);
            }
            if (ingredients.isEmpty()) {
                resource.remove("ingredients");
            }
        }

        private String paddedShapeRow(String existing, int row, int index, char shapeSymbol) {
            StringBuilder builder = new StringBuilder(existing == null ? "" : existing);
            while (builder.length() < 3) {
                builder.append(' ');
            }
            int column = index % 3;
            if (row == index / 3) {
                builder.setCharAt(column, shapeSymbol);
            }
            return builder.substring(0, 3);
        }

        private String recipeSlotSymbol(int index) {
            return String.valueOf((char) ('A' + Math.clamp(index, 0, 8)));
        }

        private void putRecipeIngredientText(int index, String value) {
            if (index < 0) {
                return;
            }
            JsonArray ingredients = resource.has("ingredients") && resource.get("ingredients").isJsonArray() ? resource.getAsJsonArray("ingredients") : new JsonArray();
            resource.add("ingredients", ingredients);
            while (ingredients.size() <= index) {
                ingredients.add("");
            }
            String material = value == null ? "" : value.trim();
            if (material.isBlank() || "none".equalsIgnoreCase(material)) {
                ingredients.set(index, new JsonPrimitive(""));
                if (index == 0 && (isCookingRecipe(normalizedRecipeType()) || "stonecutting".equals(normalizedRecipeType()))) {
                    resource.remove("ingredient");
                }
            } else {
                JsonObject ingredient = recipeItemObject(material);
                ingredients.set(index, ingredient);
                if (index == 0 && (isCookingRecipe(normalizedRecipeType()) || "stonecutting".equals(normalizedRecipeType()))) {
                    resource.add("ingredient", ingredient);
                }
            }
        }

        private void putJsonPathText(String field, String value) {
            String[] parts = field.split("\\.");
            JsonObject parent = resource;
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                if (!parent.has(part) || !parent.get(part).isJsonObject()) {
                    parent.add(part, new JsonObject());
                }
                parent = parent.getAsJsonObject(part);
            }
            String key = parts[parts.length - 1];
            if (value == null || value.isBlank()) {
                parent.remove(key);
                pruneEmptyPath(parts);
                return;
            }
            String trimmed = value.trim();
            if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
                parent.addProperty(key, Boolean.parseBoolean(trimmed));
                return;
            }
            try {
                parent.addProperty(key, Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                parent.addProperty(key, trimmed);
            }
        }

        private void putFunctionIdPathText(String field, String value) {
            String basePath = field.substring(0, field.length() - ".functionId".length());
            if (value == null || value.isBlank() || "none".equalsIgnoreCase(value) || "No Function".equals(value)) {
                putJsonPathText(field, "");
                putJsonPathText(basePath + ".inputs", "");
                return;
            }
            putJsonPathText(field, value);
            pruneFunctionInputs(basePath, value);
        }

        private void pruneFunctionInputs(String basePath, String functionId) {
            JsonObject call = jsonPathObject(basePath);
            JsonObject inputs = call != null && call.has("inputs") && call.get("inputs").isJsonObject() ? call.getAsJsonObject("inputs") : null;
            FlowGraph function = selectedFunctionById(functionId);
            if (inputs == null || function == null || function.getFunctionInputs() == null) {
                return;
            }
            List<String> allowed = new ArrayList<>();
            for (FlowGraph.FunctionParameter input : function.getFunctionInputs()) {
                if (input != null && input.getName() != null && !input.getName().isBlank()) {
                    allowed.add(input.getName());
                }
            }
            for (String key : new ArrayList<>(inputs.keySet())) {
                if (!allowed.contains(key)) {
                    inputs.remove(key);
                }
            }
            if (inputs.isEmpty()) {
                call.remove("inputs");
            }
        }

        private JsonElement jsonPathElement(String field) {
            if (field == null || field.isBlank()) {
                return null;
            }
            String[] parts = field.split("\\.");
            JsonObject current = resource;
            for (int i = 0; i < parts.length; i++) {
                if (current == null || !current.has(parts[i]) || current.get(parts[i]).isJsonNull()) {
                    return null;
                }
                JsonElement element = current.get(parts[i]);
                if (i == parts.length - 1) {
                    return element;
                }
                if (!element.isJsonObject()) {
                    return null;
                }
                current = element.getAsJsonObject();
            }
            return null;
        }

        private JsonObject jsonPathObject(String field) {
            JsonElement element = jsonPathElement(field);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        }

        private void pruneEmptyPath(String[] parts) {
            for (int length = parts.length - 1; length > 0; length--) {
                JsonObject parent = resource;
                for (int i = 0; i < length - 1; i++) {
                    if (!parent.has(parts[i]) || !parent.get(parts[i]).isJsonObject()) {
                        parent = null;
                        break;
                    }
                    parent = parent.getAsJsonObject(parts[i]);
                }
                if (parent == null || !parent.has(parts[length - 1]) || !parent.get(parts[length - 1]).isJsonObject()) {
                    continue;
                }
                JsonObject child = parent.getAsJsonObject(parts[length - 1]);
                if (!child.isEmpty()) {
                    break;
                }
                parent.remove(parts[length - 1]);
            }
        }

        private String jsonText(String key) {
            return jsonText(resource, key);
        }

        private String jsonText(JsonObject object, String key) {
            if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
                return "";
            }
            return object.get(key).getAsString();
        }

        private int jsonArraySize(String key) {
            return resource.has(key) && resource.get(key).isJsonArray() ? resource.getAsJsonArray(key).size() : 0;
        }

        private int jsonObjectSize(String key) {
            return resource.has(key) && resource.get(key).isJsonObject() ? resource.getAsJsonObject(key).size() : 0;
        }

        private JsonObject jsonObject(String key) {
            return resource.has(key) && resource.get(key).isJsonObject() ? resource.getAsJsonObject(key) : new JsonObject();
        }

        private String resourceDisplayName() {
            return switch (type) {
                case ReSyncResourceDragPayload.CHAT_CHANNEL -> "Chat";
                case ReSyncResourceDragPayload.CHAT_FORMAT -> "Chat Format";
                case ReSyncResourceDragPayload.CHAT_RULE -> "Chat Rule";
                case ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT -> "PM Format";
                case ReSyncResourceDragPayload.MENTION_STYLE -> "Mention";
                case ReSyncResourceDragPayload.IGNORE_LIST -> "Ignore List";
                case ReSyncResourceDragPayload.MOTD_PROFILE -> "MOTD";
                case ReSyncResourceDragPayload.MESSAGE_RULE -> "Message Rule";
                case ReSyncResourceDragPayload.RECIPE_DEFINITION -> "Recipe";
                case ReSyncResourceDragPayload.TEXT_TEMPLATE -> "Text";
                default -> "Resource";
            };
        }

        private void drawFormattedLine(IDrawContext context, String value, int startX, int y, int fallbackColor, boolean shadow) {
            context.drawRichText(value, startX, y, fallbackColor, shadow);
        }

        private String applyMentionPreview(String line) {
            String template = ReSyncResourceDragPayload.MENTION_STYLE.equals(type) ? jsonText("template") : "<yellow>@{player}</yellow>";
            return line.replace("@Alex", template.replace("{player}", "Alex"));
        }

        private String[] motdPreviewLines() {
            String line1 = jsonText("line1").isBlank() ? "<green>ReSync Server" : firstMotdLine(jsonText("line1"));
            String line2 = jsonText("line2").isBlank() ? "<gray>Flow Powered" : firstMotdLine(jsonText("line2"));
            return new String[]{line1, line2};
        }

        private String sampleCountText() {
            String mode = jsonText("playerCountMode");
            if ("hidden".equalsIgnoreCase(mode)) {
                return "§8???";
            }
            String online = jsonText("onlinePlayers");
            String max = jsonText("maxPlayers");
            return ("§7" + (online.isBlank() ? "12" : online)) + "§8/§7" + (max.isBlank() ? "80" : max);
        }

        private String recipeSlotLabel(int row, int column) {
            JsonArray shape = resource.has("shape") && resource.get("shape").isJsonArray() ? resource.getAsJsonArray("shape") : new JsonArray();
            JsonObject keys = jsonObject("keys");
            String recipeType = normalizedRecipeType();
            if (!shape.isEmpty() && row < shape.size()) {
                String line = shape.get(row).getAsString();
                if (column < line.length()) {
                    JsonElement ingredient = keys.get(String.valueOf(line.charAt(column)));
                    String shaped = ingredientLabel(ingredient);
                    if (!shaped.isBlank()) {
                        return shaped;
                    }
                }
            }
            if (!"shapeless".equals(recipeType)) {
                return "";
            }
            JsonArray ingredients = resource.has("ingredients") && resource.get("ingredients").isJsonArray() ? resource.getAsJsonArray("ingredients") : new JsonArray();
            int index = row * 3 + column;
            return index < ingredients.size() ? ingredientLabel(ingredients.get(index)) : "";
        }

        private String normalizedRecipeType() {
            String value = jsonText("type").trim().toLowerCase(Locale.ROOT);
            if (value.startsWith("minecraft:")) {
                value = value.substring("minecraft:".length());
            }
            value = switch (value) {
                case "smelting" -> "furnace";
                case "blast" -> "blasting";
                case "smoker" -> "smoking";
                case "campfire_cooking" -> "campfire";
                case "stonecutter" -> "stonecutting";
                default -> value;
            };
            return value.isBlank() ? "shaped" : value;
        }

        private boolean isCookingRecipe(String recipeType) {
            return "furnace".equals(recipeType) || "blasting".equals(recipeType) || "smoking".equals(recipeType) || "campfire".equals(recipeType);
        }

        private boolean isSmithingRecipe(String recipeType) {
            return "smithing".equals(recipeType) || "smithing_transform".equals(recipeType) || "smithing_trim".equals(recipeType) || "trim".equals(recipeType);
        }

        private String ingredientLabel(JsonElement ingredient) {
            if (ingredient == null || ingredient.isJsonNull()) {
                return "";
            }
            if (ingredient.isJsonArray() && !ingredient.getAsJsonArray().isEmpty()) {
                return ingredientLabel(ingredient.getAsJsonArray().get(0));
            }
            if (!ingredient.isJsonObject()) {
                return ingredient.getAsString();
            }
            return encodeRecipeItemValue(ingredient.getAsJsonObject());
        }

        private int ingredientAmount(JsonElement ingredient) {
            if (ingredient == null || ingredient.isJsonNull()) {
                return 1;
            }
            if (ingredient.isJsonArray() && !ingredient.getAsJsonArray().isEmpty()) {
                return ingredientAmount(ingredient.getAsJsonArray().get(0));
            }
            if (!ingredient.isJsonObject()) {
                return 1;
            }
            return parseInt(jsonText(ingredient.getAsJsonObject(), "amount"), 1, 1, 64);
        }

        private JsonElement firstIngredient() {
            JsonArray ingredients = resource.has("ingredients") && resource.get("ingredients").isJsonArray() ? resource.getAsJsonArray("ingredients") : new JsonArray();
            return ingredients.isEmpty() ? null : ingredients.get(0);
        }

        private String cookingIngredientLabel() {
            JsonElement ingredient = resource.has("ingredient") ? resource.get("ingredient") : firstIngredient();
            return ingredientLabel(ingredient);
        }

        private int cookingIngredientAmount() {
            JsonElement ingredient = resource.has("ingredient") ? resource.get("ingredient") : firstIngredient();
            return ingredientAmount(ingredient);
        }

        private JsonElement recipeSlotIngredient(int row, int column) {
            JsonArray shape = resource.has("shape") && resource.get("shape").isJsonArray() ? resource.getAsJsonArray("shape") : new JsonArray();
            JsonObject keys = jsonObject("keys");
            if (!shape.isEmpty() && row < shape.size()) {
                String line = shape.get(row).getAsString();
                if (column < line.length()) {
                    JsonElement ingredient = keys.get(String.valueOf(line.charAt(column)));
                    if (ingredient != null) {
                        return ingredient;
                    }
                }
            }
            JsonArray ingredients = resource.has("ingredients") && resource.get("ingredients").isJsonArray() ? resource.getAsJsonArray("ingredients") : new JsonArray();
            int index = row * 3 + column;
            return index < ingredients.size() ? ingredients.get(index) : null;
        }

        private int recipeSlotAmount(int row, int column) {
            return ingredientAmount(recipeSlotIngredient(row, column));
        }

        private int recipeFieldAmount(String field) {
            if ("output.material".equals(field)) {
                return parseInt(jsonText(jsonObject("output"), "amount"), 1, 1, 64);
            }
            if ("template.material".equals(field)) {
                return ingredientAmount(jsonObject("template"));
            }
            if ("base.material".equals(field)) {
                return ingredientAmount(jsonObject("base"));
            }
            if ("addition.material".equals(field)) {
                return ingredientAmount(jsonObject("addition"));
            }
            int slotIndex = recipeSlotIndex(field);
            if (slotIndex >= 0) {
                return recipeSlotAmount(slotIndex / 3, slotIndex % 3);
            }
            int ingredientIndex = recipeIngredientIndex(field);
            if (ingredientIndex >= 0) {
                JsonArray ingredients = resource.has("ingredients") && resource.get("ingredients").isJsonArray() ? resource.getAsJsonArray("ingredients") : new JsonArray();
                if (ingredientIndex < ingredients.size()) {
                    return ingredientAmount(ingredients.get(ingredientIndex));
                }
                return ingredientIndex == 0 && resource.has("ingredient") ? ingredientAmount(resource.get("ingredient")) : 1;
            }
            return 1;
        }

        private void putRecipeFieldAmount(String field, int amount) {
            if ("output.material".equals(field) || "template.material".equals(field) || "base.material".equals(field) || "addition.material".equals(field)) {
                String[] parts = field.split("\\.", 2);
                JsonObject object = jsonObject(parts[0]);
                object.addProperty("amount", amount);
                resource.add(parts[0], object);
                return;
            }
            int slotIndex = recipeSlotIndex(field);
            if (slotIndex >= 0) {
                putRecipeSlotAmount(slotIndex, amount);
                return;
            }
            int ingredientIndex = recipeIngredientIndex(field);
            if (ingredientIndex >= 0) {
                putRecipeIngredientAmount(ingredientIndex, amount);
            }
        }

        private void putRecipeSlotAmount(int index, int amount) {
            JsonObject keys = jsonObject("keys");
            String symbol = recipeSlotSymbol(index);
            JsonElement ingredient = keys.get(symbol);
            if (ingredient != null) {
                JsonObject object = recipeIngredientObject(ingredient);
                object.addProperty("amount", amount);
                keys.add(symbol, object);
                resource.add("keys", keys);
                return;
            }
            putRecipeIngredientAmount(index, amount);
        }

        private void putRecipeIngredientAmount(int index, int amount) {
            JsonArray ingredients = resource.has("ingredients") && resource.get("ingredients").isJsonArray() ? resource.getAsJsonArray("ingredients") : new JsonArray();
            resource.add("ingredients", ingredients);
            while (ingredients.size() <= index) {
                ingredients.add("");
            }
            JsonObject object = recipeIngredientObject(ingredients.get(index));
            object.addProperty("amount", amount);
            ingredients.set(index, object);
            if (index == 0 && (isCookingRecipe(normalizedRecipeType()) || "stonecutting".equals(normalizedRecipeType()))) {
                resource.add("ingredient", object);
            }
        }

        private JsonObject recipeIngredientObject(JsonElement ingredient) {
            if (ingredient != null && ingredient.isJsonArray() && !ingredient.getAsJsonArray().isEmpty()) {
                return recipeIngredientObject(ingredient.getAsJsonArray().get(0));
            }
            if (ingredient != null && ingredient.isJsonObject()) {
                return ingredient.getAsJsonObject();
            }
            JsonObject object = new JsonObject();
            if (ingredient != null && ingredient.isJsonPrimitive() && !ingredient.getAsString().isBlank()) {
                object.addProperty("material", ingredient.getAsString());
            }
            return object;
        }

        private String recipePreviewMaterial(String encoded) {
            if (encoded == null || encoded.isBlank()) {
                return "";
            }
            if (!encoded.contains(":")) {
                return encoded;
            }
            if (encoded.startsWith("content:")) {
                String contentId = encoded.substring("content:".length());
                FlowManager manager = FlowManager.getInstance();
                if (manager != null && serverId != null) {
                    CustomContentDefinition content = manager.getCustomContentForServer(serverId).get(contentId);
                    if (content != null && content.getMaterial() != null && !content.getMaterial().isBlank()) {
                        return content.getMaterial();
                    }
                }
                return "BARRIER";
            }
            if (encoded.startsWith("provider:")) {
                return "PAPER";
            }
            return encoded;
        }

        private void drawRecipeItem(IDrawContext context, String material, int amount, int x, int y, int scale) {
            String previewMaterial = material == null ? "" : material.trim();
            if (previewMaterial.isBlank()) {
                return;
            }
            int iconSize = Math.max(16, 16 * scale);
            MinecraftRenderItem item = ItemIconPreview.resolve(serverId, previewMaterial).toRenderItem(recipeItemSelectorLabel(previewMaterial));
            if (item == null) {
                return;
            }
            if (scale <= 1) {
                context.drawItem(item, x, y, 0);
            } else {
                context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(scale, scale, 1);
                context.drawItem(item, 0, 0, 0);
                context.getMatrices().pop();
            }
            drawRecipeItemAmount(context, Math.clamp(amount, 1, 64), x, y, iconSize);
        }

        private void drawRecipeItemAmount(IDrawContext context, int amount, int x, int y, int iconSize) {
            String text = String.valueOf(amount);
            int textX = x + iconSize - textWidth(text);
            int textY = y + iconSize - 8;
            context.drawText(text, textX + 1, textY + 1, 0xFF000000, false);
            context.drawText(text, textX, textY, 0xFFFFFFFF, false);
        }

        private void drawRecipeStationItems(IDrawContext context, String recipeType, RecipeStationLayout layout, int viewX, int viewY, int scale) {
            if (isSmithingRecipe(recipeType)) {
                drawRecipeLayoutItem(context, ingredientLabel(jsonObject("template")), ingredientAmount(jsonObject("template")), layout.templates()[0], viewX, viewY, scale);
                drawRecipeLayoutItem(context, ingredientLabel(jsonObject("base")), ingredientAmount(jsonObject("base")), layout.templates()[1], viewX, viewY, scale);
                drawRecipeLayoutItem(context, ingredientLabel(jsonObject("addition")), ingredientAmount(jsonObject("addition")), layout.templates()[2], viewX, viewY, scale);
            } else if (isCookingRecipe(recipeType)) {
                drawRecipeLayoutItem(context, cookingIngredientLabel(), cookingIngredientAmount(), layout.ingredients()[0], viewX, viewY, scale);
            } else if ("stonecutting".equals(recipeType)) {
                drawRecipeLayoutItem(context, ingredientLabel(firstIngredient()), ingredientAmount(firstIngredient()), layout.ingredients()[0], viewX, viewY, scale);
            } else {
                int[][] points = layout.ingredients();
                for (int i = 0; i < points.length; i++) {
                    drawRecipeLayoutItem(context, recipeSlotLabel(i / 3, i % 3), recipeSlotAmount(i / 3, i % 3), points[i], viewX, viewY, scale);
                }
            }
            drawRecipeLayoutItem(context, ingredientLabel(jsonObject("output")), parseInt(jsonText(jsonObject("output"), "amount"), 1, 1, 64), layout.output(), viewX, viewY, scale);
        }

        private void drawRecipeLayoutItem(IDrawContext context, String material, int amount, int[] point, int viewX, int viewY, int scale) {
            if (point == null || point.length < 2) {
                return;
            }
            drawRecipeItem(context, material, amount, viewX + point[0] * scale, viewY + point[1] * scale, scale);
        }

        private RecipeStationLayout recipeStationLayout(String recipeType) {
            int[][] craftingSlots = new int[][]{
                {30, 17}, {48, 17}, {66, 17},
                {30, 35}, {48, 35}, {66, 35},
                {30, 53}, {48, 53}, {66, 53}
            };
            return switch (recipeType) {
                case "furnace" -> new RecipeStationLayout("furnace.png", 176, 166, new int[][]{{56, 17}}, new int[0][0], new int[]{116, 35});
                case "blasting" -> new RecipeStationLayout("blast_furnace.png", 176, 166, new int[][]{{56, 17}}, new int[0][0], new int[]{116, 35});
                case "smoking" -> new RecipeStationLayout("smoker.png", 176, 166, new int[][]{{56, 17}}, new int[0][0], new int[]{116, 35});
                case "campfire" -> new RecipeStationLayout("", 88, 16, new int[][]{{8, 0}}, new int[0][0], new int[]{64, 0});
                case "stonecutting" -> new RecipeStationLayout("stonecutter.png", 176, 166, new int[][]{{20, 33}}, new int[0][0], new int[]{143, 33});
                case "smithing", "smithing_transform", "smithing_trim", "trim" -> new RecipeStationLayout("smithing.png", 176, 166, new int[0][0], new int[][]{{8, 48}, {26, 48}, {44, 48}}, new int[]{98, 48});
                default -> new RecipeStationLayout("crafting_table.png", 176, 166, craftingSlots, new int[0][0], new int[]{124, 35});
            };
        }

        private List<String> previewTextLines() {
            List<String> lines = new ArrayList<>();
            JsonArray frames = resource.has("frames") && resource.get("frames").isJsonArray() ? resource.getAsJsonArray("frames") : new JsonArray();
            for (JsonElement frame : frames) {
                if (!frame.isJsonNull() && !frame.getAsString().isBlank()) {
                    lines.add(frame.getAsString());
                }
            }
            if (!lines.isEmpty()) {
                return lines;
            }
            String text = jsonText("text");
            for (String line : text.split("\\R")) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return lines.isEmpty() ? List.of("Text") : lines;
        }
    }

    @Override
    public Screen asScreen() {
        return this;
    }

    @Override public void updateRenderOrder(List<AnimatedWidget> list) {}
    @Override public void setHitBottom(boolean hitBottom) {}
}

