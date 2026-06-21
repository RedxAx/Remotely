package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.ReSyncResourceCreator;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioView;
import redxax.oxy.remotely.flow.ui.studio.RecipeSlotTarget;
import redxax.oxy.remotely.flow.ui.studio.RecipeStationLayout;
import redxax.oxy.remotely.flow.ui.studio.StudioCatalogRefreshView;
import redxax.oxy.remotely.flow.ui.studio.StudioHeaderProvider;
import redxax.oxy.remotely.flow.ui.studio.StudioOverlayView;
import redxax.oxy.remotely.flow.ui.studio.StudioPanel;
import redxax.oxy.remotely.flow.ui.studio.StudioPriorityInputView;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import redxax.oxy.remotely.flow.ui.studio.StudioSelectorView;
import redxax.oxy.remotely.packcontent.PackContentRegistry;
import org.lwjgl.glfw.GLFW;
import restudio.rescreen.game.MinecraftAssetReference;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.game.MinecraftGameEntities;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.lwjgl.MinecraftRenderItem;
import restudio.rescreen.render.Render;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.DoubleSliderWidget;
import restudio.rescreen.ui.widgets.IconMessage;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;
import restudio.rescreen.ui.widgets.CompactBindingWidget;
import restudio.rescreen.util.FileUtils;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

import static redxax.oxy.remotely.flow.ui.GuiEditOverlayState.snapshot;
import static restudio.rescreen.config.Config.desktopMode;

public abstract class FocusedJsonResourceDesignerScreen extends StudioScreen implements DesktopWindowBehaviorProvider, ReSyncStudioView, StudioSelectorView, StudioOverlayView, StudioCatalogRefreshView, StudioPriorityInputView, StudioHeaderProvider {
    private static final CopyOnWriteArraySet<FocusedJsonResourceDesignerScreen> OPEN_SCREENS = new CopyOnWriteArraySet<>();
    protected final String type;
    protected final String id;
    protected final JsonObject resource;
    protected final String serverId;
    protected final Object parent;
    protected final StudioScreen host;
    private static final String MATERIAL_OPTIONS_SOURCE = "server:minecraft:material";
    private static final String RECIPE_ITEM_OPTIONS_SOURCE = "server:custom_content:recipe_item";
    private static final String ENTITY_TYPE_OPTIONS_SOURCE = "server:minecraft:entity_type";
    private static final List<String> FALLBACK_MATERIAL_OPTIONS = List.of(
        "STONE", "COBBLESTONE", "OAK_PLANKS", "OAK_LOG", "GLASS", "GLASS_PANE",
        "GRAY_STAINED_GLASS_PANE", "WHITE_STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE",
        "RED_STAINED_GLASS_PANE", "GREEN_STAINED_GLASS_PANE", "BLUE_STAINED_GLASS_PANE",
        "BARRIER", "CHEST", "ENDER_CHEST", "ANVIL", "BOOK", "PAPER", "MAP",
        "COMPASS", "CLOCK", "DIAMOND", "EMERALD", "GOLD_INGOT", "IRON_INGOT",
        "NETHERITE_INGOT", "REDSTONE", "AMETHYST_SHARD", "ENDER_PEARL",
        "TOTEM_OF_UNDYING", "PLAYER_HEAD", "NAME_TAG"
    );
    private static final List<String> FALLBACK_ENTITY_TYPE_OPTIONS = List.of(
        "allay", "armadillo", "armor_stand", "axolotl", "bat", "bee",
        "blaze", "bogged", "breeze", "camel", "cat", "cave_spider",
        "chicken", "cod", "copper_golem", "cow", "creaking", "creeper",
        "dolphin", "donkey", "drowned", "elder_guardian", "ender_dragon", "enderman",
        "endermite", "evoker", "fox", "frog", "ghast", "giant",
        "glow_squid", "goat", "guardian", "happy_ghast", "hoglin", "horse",
        "husk", "illusioner", "iron_golem", "llama", "magma_cube", "mooshroom",
        "mule", "ocelot", "panda", "parrot", "phantom", "pig",
        "piglin", "piglin_brute", "pillager", "player", "polar_bear", "pufferfish",
        "rabbit", "ravager", "salmon", "sheep", "shulker", "silverfish",
        "skeleton", "skeleton_horse", "slime", "sniffer", "snow_golem", "spider",
        "squid", "stray", "strider", "tadpole", "trader_llama", "tropical_fish",
        "turtle", "vex", "villager", "vindicator", "wandering_trader", "warden",
        "witch", "wither", "wither_skeleton", "wolf", "zoglin", "zombie",
        "zombie_horse", "zombie_villager", "zombified_piglin"
    );
    private static final Map<String, BufferedImage> MOTD_ICON_CACHE = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > 48;
        }
    };
    private final List<AnimatedWidget> resourceHeaderActions = new ArrayList<>();
    private final Map<String, TextInputWidget> resourceFieldInputs = new LinkedHashMap<>();
    private final Map<String, CodeEditorWidget> resourceCodeFieldInputs = new LinkedHashMap<>();
    private final Map<String, ToggleWidget> resourceToggleFieldInputs = new LinkedHashMap<>();
    private final Map<String, DropDownWidget<String>> resourceDropdownFieldInputs = new LinkedHashMap<>();
    private final Map<String, AnimatedButton> resourceSelectorButtons = new LinkedHashMap<>();
    private final List<CompactBindingWidget> resourceBindingWidgets = new ArrayList<>();
    private boolean resourcePanelMounted;
    private PopupWidget messageLogPopup;
    private int messageLogPage;
    private int messageLogPageSize = 8;
    private String selectedMessageText = "";
    private String selectedMessageSource = "";
    private String previewMessageText = "";
    private int previewMessageX;
    private int previewMessageY;
    private int previewMessageSelectionAnchor = -1;
    private int previewMessageSelectionFocus = -1;
    private boolean previewMessageSelecting;
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
    private int recipeHighlightOriginX;
    private int recipeHighlightOriginY;
    private final int recipeHighlightAnimationScope = SlotInteractionGrid.animationScope();
    private int recipeHighlightPreviewNonce;
    private int recipeHighlightSelectionNonce;
    private RecipeStrokeMode recipeStrokeMode = RecipeStrokeMode.NONE;
    private String recipeStrokeValue = "";
    private String recipeBrushValue = "";
    private List<String> pendingRecipeSelectionFields = new ArrayList<>();
    private boolean draggingRecipeField;
    private int selectedVillageOfferIndex;
    private int villagePreviewX;
    private int villagePreviewY;
    private int villagePreviewScale = 1;
    private int villagePreviewOfferOffset;
    private int villagePreviewVisibleOffers;
    private int villagePreviewOfferScroll;
    private int villagePreviewAddX;
    private int villagePreviewAddY;
    private int villagePreviewAddWidth;
    private int villagePreviewAddHeight;
    private int villagePreviewDeleteX;
    private int villagePreviewDeleteY;
    private int villagePreviewDeleteWidth;
    private int villagePreviewDeleteHeight;
    private int npcPreviewX;
    private int npcPreviewY;
    private String resourceLinkDraftMode = "";
    private boolean resourceEditHistoryBatch;
    private int x;
    private int y;
    private int width;
    private int height;
    protected final StudioScreen.History<String> resourceEditHistory = history(this::resourceSnapshot, this::restoreResourceSnapshot);

    private record ResourcePanelSection(String title, List<String> fields) {
    }

    private record NpcEquipmentSlot(String field, int x, int y) {
    }

    private enum RecipeStrokeMode {
        NONE,
        PAINT,
        ERASE,
        SELECT
    }

    protected FocusedJsonResourceDesignerScreen(StudioScreen owner, String type, String id, JsonObject resource, String serverId, Object parent) {
        this.type = type;
        this.id = id;
        this.resource = resource != null ? resource : new JsonObject();
        this.serverId = serverId;
        this.parent = parent;
        this.host = owner != null ? owner : parent instanceof StudioScreen screen ? screen : null;
        OPEN_SCREENS.add(this);
        resourceHeaderActions.add(headerButton("save.png", "Save", this::save));
        if (ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type)) {
            ensureRecipeItemCatalogLoaded();
        }
        if (ReSyncResourceDragPayload.NPC_DEFINITION.equals(type)) {
            ensureEntityTypeCatalogLoaded();
        }
        if (ReSyncResourceDragPayload.MESSAGE_RULE.equals(type)) {
            requestMessageLogPage(0);
        }
    }

    protected boolean hasResourceHistory() {
        return ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type);
    }

    public static void refreshCatalogForServer(String serverId) {
        for (FocusedJsonResourceDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.serverId)) {
                screen.onStudioCatalogRefreshed();
            }
        }
    }

    public static void refreshFlowBindingsForServer(String serverId) {
        refreshFlowBindingsForServer(serverId, null);
    }

    public static boolean hasOpenScreenForServer(String serverId) {
        for (FocusedJsonResourceDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.serverId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasFlowBindingForServer(String serverId, String flowId) {
        for (FocusedJsonResourceDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.serverId) && screen.hasFlowBinding(flowId)) {
                return true;
            }
        }
        return false;
    }

    public static void refreshFlowBindingsForServer(String serverId, String flowId) {
        for (FocusedJsonResourceDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.serverId)) {
                screen.refreshBindingWidgets(flowId);
            }
        }
    }

    public static void refreshMessageLogForServer(String serverId) {
        for (FocusedJsonResourceDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.serverId) && ReSyncResourceDragPayload.MESSAGE_RULE.equals(screen.type)) {
                screen.onMessageLogRefreshed();
            }
        }
    }

    @Override
    public void close() {
        OPEN_SCREENS.remove(this);
        clearFocusedResourcePanelState();
        super.close();
    }

    @Override
    public void closed() {
        OPEN_SCREENS.remove(this);
        clearFocusedResourcePanelState();
    }

    public StudioScreen.History<String> resourceHistory() {
        return resourceEditHistory;
    }

    private void onMessageLogRefreshed() {
        if (messageLogPopup != null && messageLogPopup.isVisible()) {
            messageLogPopup.hide();
            showMessageLogPopup();
        }
    }

    private String resourceSnapshot() {
        return gson.toJson(resource);
    }

    private void captureResourceSnapshot() {
        if (hasResourceHistory() && !resourceEditHistoryBatch && !resourceEditHistory.isRestoring()) {
            resourceEditHistory.capture();
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
        selectedVillageOfferIndex = 0;
        reloadFields();
    }

    @Override
    public List<AnimatedWidget> headerButtons() {
        return resourceHeaderActions;
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
        useStudioResourcePanel(panel);
        if (!resourcePanelMounted || !resourcePanelWidgetsMounted()) {
            mountResourcePanel();
            return;
        }
        refreshResourcePanelFields();
    }

    @Override
    protected void clearStudioResourcePanelWidgets() {
        super.clearStudioResourcePanelWidgets();
        clearFocusedResourcePanelState();
    }

    private void clearFocusedResourcePanelState() {
        resourcePanelMounted = false;
        resourceFieldInputs.clear();
        resourceCodeFieldInputs.clear();
        resourceToggleFieldInputs.clear();
        resourceDropdownFieldInputs.clear();
        resourceSelectorButtons.clear();
        resourceBindingWidgets.clear();
        studioResourcePanelWidgets.clear();
        studioResourcePanelKey = "";
        studioResourcePanelInputs.clear();
        studioResourcePanelToggles.clear();
        studioResourceStudioPanel = null;
        studioResourcePanel = null;
    }

    @Override
    public void resize(int width, int height) {
        int leftReserve = previewLeftReserve();
        this.x = 18 + leftReserve;
        this.y = 44;
        this.width = Math.max(120, width - 36 - leftReserve);
        this.height = Math.max(80, height - 62);
    }

    protected int previewLeftReserve() {
        return 0;
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        int text = ThemeManager.getColor(ThemeColor.text);
        int muted = ThemeManager.getColor(ThemeColor.textDark);
        renderPreviewCanvas(context, mouseX, mouseY, text, muted);
    }

    private void renderPreviewCanvas(IDrawContext context, int mouseX, int mouseY, int text, int muted) {
        int previewX = x + 12;
        int previewY = y + 12;
        int rightReserve = studioResourcePanel != null && studioResourcePanel.isVisible() && !studioResourcePanel.isLeftAnchored() ? studioResourcePanel.getDesiredWidth() + 10 : 0;
        int previewRightReserve = centeredTextPreview() ? 0 : rightReserve;
        int previewWidth = Math.max(160, x + width - previewRightReserve - previewX - 14);
        int previewHeight = Math.max(80, height - 24);
        switch (type) {
            case ReSyncResourceDragPayload.MOTD_PROFILE -> renderMotdRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> renderRecipeRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
            case ReSyncResourceDragPayload.VILLAGE_PROFILE -> renderVillageRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
            case ReSyncResourceDragPayload.NPC_DEFINITION -> renderNpcRealPreview(context, previewX, previewY, previewWidth, previewHeight, mouseX, mouseY, text, muted);
            case ReSyncResourceDragPayload.LOOT_TABLE -> renderLootRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> renderTextRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
            case ReSyncResourceDragPayload.MESSAGE_RULE -> renderMessageRuleRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
            case ReSyncResourceDragPayload.CHAT -> renderChatRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
            default -> renderGenericRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
        }
    }

    private boolean centeredTextPreview() {
        return ReSyncResourceDragPayload.CHAT.equals(type) || ReSyncResourceDragPayload.MESSAGE_RULE.equals(type);
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
        if (MinecraftUiPreviewRenderer.drawAssetRegion(context, gameAssets, reference, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight)) {
            return;
        }
        if (fallback != null && fallback != ResourceManager.getInstance().getMissingTexture()) {
            context.drawPixelArt(fallback, x, y, width, height);
        }
    }

    private boolean drawMinecraftSprite(IDrawContext context, MinecraftGameAssets gameAssets, String sprite, int x, int y, int width, int height) {
        return MinecraftUiPreviewRenderer.drawSprite(context, gameAssets, sprite, x, y, width, height);
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
            Render.drawLayeredInnerBorder(context, left, top, size, size, ThemeManager.getColor(ThemeColor.elementBackground), border);
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
        int centerY = previewY + previewHeight / 2;
        int centerX = previewX + previewWidth / 2;
        String source = jsonText("source");
        String find = jsonText("contains");
        String original = messagePreviewSource(source);
        String action = jsonText("action").toLowerCase(Locale.ROOT);
        previewMessageText = original;
        previewMessageX = centeredTextX(previewMessageText, centerX);
        previewMessageY = centerY - 14;
        renderMessageSelection(context, previewMessageText, previewMessageX, previewMessageY);
        drawFormattedLine(context, original, previewMessageX, previewMessageY, muted, true);
        String replacement = jsonText("replacement");
        String rendered = messagePreviewResult(original, find, replacement, action);
        drawFormattedLine(context, rendered, centeredTextX(rendered, centerX), centerY + 10, text, true);
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

    private String messageSourceLabel(String source) {
        return switch (source == null ? "" : source.toLowerCase(Locale.ROOT)) {
            case "chat" -> "Chat";
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

    private String messagePreviewSource(String source) {
        if (!selectedMessageText.isBlank() && (source == null || source.isBlank() || selectedMessageSource.isBlank() || selectedMessageSource.equalsIgnoreCase(source))) {
            return selectedMessageText;
        }
        JsonObject entry = firstMessageLogEntry(source);
        if (entry != null) {
            return jsonText(entry, "plainText");
        }
        return sampleMessageSource(source);
    }

    private JsonObject firstMessageLogEntry(String source) {
        JsonObject page = messageLogPage();
        JsonArray entries = page != null && page.has("entries") && page.get("entries").isJsonArray() ? page.getAsJsonArray("entries") : new JsonArray();
        for (JsonElement element : entries) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String entrySource = jsonText(entry, "source");
            if (source == null || source.isBlank() || entrySource.isBlank() || entrySource.equalsIgnoreCase(source)) {
                return entry;
            }
        }
        return null;
    }

    private String sampleMessageSource(String source) {
        return switch (source == null ? "" : source.toLowerCase(Locale.ROOT)) {
            case "chat" -> "Steve: Hello server";
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
        int centerX = previewX + previewWidth / 2;
        int chatY = previewY + Math.max(18, previewHeight / 2 - 48);
        String prefix = jsonPathText("channel.prefix");
        String template = jsonPathText("format.template");
        if (template.isBlank()) {
            template = "{prefix}{sender}: {message}";
        }
        String line = template.replace("{prefix}", prefix).replace("{sender}", "Steve").replace("{receiver}", "Alex").replace("{message}", "Hello @Alex");
        String firstLine = applyMentionPreview(line);
        String secondLine = "<gray>Alex: Looks good";
        String thirdLine = "<yellow>@Steve</yellow> synced";
        drawFormattedLine(context, firstLine, centeredTextX(firstLine, centerX), chatY + 16, text, true);
        drawFormattedLine(context, secondLine, centeredTextX(secondLine, centerX), chatY + 36, muted, true);
        drawFormattedLine(context, thirdLine, centeredTextX(thirdLine, centerX), chatY + 56, text, true);
    }

    private void renderGenericRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
        int centerY = previewY + previewHeight / 2;
        context.drawText(resourceDisplayName(), previewX + 24, centerY - 10, text, false);
        context.drawText("Ready", previewX + 24, centerY + 8, muted, false);
    }

    private void renderVillageRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
        MinecraftGameAssets gameAssets = getGameAssets();
        MinecraftAssetReference reference = gameAssets.asset("minecraft", "textures/gui/container/villager.png");
        BufferedImage texture = gameAssets.getImage(reference);
        boolean hasTexture = texture != null && texture != ResourceManager.getInstance().getMissingTexture();
        int atlasWidth = 512;
        int atlasHeight = 256;
        int viewTextureWidth = 276;
        int viewTextureHeight = 166;
        int scale = 1;
        int viewWidth = viewTextureWidth * scale;
        int viewHeight = viewTextureHeight * scale;
        int viewX = previewX + Math.max(0, (previewWidth - viewWidth) / 2);
        int viewY = previewY + Math.max(0, (previewHeight - viewHeight) / 2);
        villagePreviewX = viewX;
        villagePreviewY = viewY;
        villagePreviewScale = scale;
        if (hasTexture) {
            drawMinecraftTexture(context, gameAssets, reference, texture, viewX, viewY, viewWidth, viewHeight, 0, 0, viewTextureWidth, viewTextureHeight, atlasWidth, atlasHeight);
        } else {
            context.fill(viewX, viewY, viewX + viewWidth, viewY + viewHeight, 0xFFE8CFA6);
            context.fill(viewX + 136 * scale, viewY, viewX + viewWidth, viewY + viewHeight, 0xFFE3D8C3);
        }
        drawMerchantPreviewTrades(context, gameAssets, viewX, viewY, scale, text, muted);
        drawMerchantPreviewSummary(context, viewX, viewY, scale, text, muted);
    }

    private void renderNpcRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int mouseX, int mouseY, int text, int muted) {
        int cardWidth = Math.min(260, previewWidth - 24);
        int cardHeight = 124;
        int cardX = previewX + Math.max(12, (previewWidth - cardWidth) / 2);
        int cardY = previewY + Math.max(12, (previewHeight - cardHeight) / 2);
        npcPreviewX = cardX;
        npcPreviewY = cardY;
        context.drawText(firstFilled(jsonPathText("displayName"), id), cardX + 14, cardY + 12, text, false);
        context.drawText(formatOptionLabel(jsonPathText("entityType")), cardX + 14, cardY + 28, muted, false);
        drawNpcEntityPreview(context, cardX + 10, cardY + 44, 58, mouseX, mouseY);
        for (NpcEquipmentSlot slot : npcEquipmentSlots()) {
            context.fill(cardX + slot.x() - 2, cardY + slot.y() - 2, cardX + slot.x() + 18, cardY + slot.y() + 18, 0x66101010);
            drawRecipeItem(context, jsonPathText(slot.field()), 1, cardX + slot.x(), cardY + slot.y(), 1);
        }
        context.drawText("Trade " + compactState(resourceLinkText("links.tradeProfile", "tradeProfile")), cardX + 148, cardY + 58, muted, false);
        context.drawText("Loot " + compactState(resourceLinkText("links.lootTable", "lootTable")), cardX + 148, cardY + 74, muted, false);
    }

    private void drawNpcEntityPreview(IDrawContext context, int x, int y, int size, int mouseX, int mouseY) {
        String entityType = normalizedNpcEntityType();
        String displayName = firstFilled(jsonPathText("displayName"), id);
        boolean baby = npcBaby();
        Map<String, Object> tag = npcEntityPreviewTag();
        float relativeMouseX = mouseX - (x + size / 2.0f);
        float relativeMouseY = mouseY - (y + size / 2.0f);
        if ("minecraft:player".equals(entityType)) {
            context.drawPlayerRelativeMousePreview(MinecraftGameEntities.of("minecraft:player", displayName, null, null, false, baby, tag), x, y, 20, size, relativeMouseX, relativeMouseY, false);
        } else {
            context.drawEntityRelativeMousePreview(MinecraftGameEntities.of(entityType, displayName, null, null, false, baby, tag), x, y, 20, size, relativeMouseX, relativeMouseY, false);
        }
    }

    private boolean npcBaby() {
        String value = firstFilled(jsonPathText("baby"), jsonPathText("isBaby"), jsonPathText("IsBaby"));
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }

    private Map<String, Object> npcEntityPreviewTag() {
        LinkedHashMap<String, Object> equipment = new LinkedHashMap<>();
        putNpcEquipment(equipment, "mainHand", "equipment.mainHand");
        putNpcEquipment(equipment, "offHand", "equipment.offHand");
        putNpcEquipment(equipment, "helmet", "equipment.helmet");
        putNpcEquipment(equipment, "chestplate", "equipment.chestplate");
        putNpcEquipment(equipment, "leggings", "equipment.leggings");
        putNpcEquipment(equipment, "boots", "equipment.boots");
        return equipment.isEmpty() ? Map.of() : Map.of("equipment", Map.copyOf(equipment));
    }

    private void putNpcEquipment(Map<String, Object> equipment, String key, String field) {
        String item = jsonPathText(field).trim();
        if (!item.isBlank()) {
            MinecraftRenderItem preview = ItemIconPreview.resolve(serverId, item).toRenderItem(recipeItemSelectorLabel(item));
            equipment.put(key, preview != null ? preview : item);
        }
    }

    private String normalizedNpcEntityType() {
        String entityType = jsonPathText("entityType").trim();
        if (entityType.isBlank()) {
            entityType = "villager";
        }
        entityType = entityType.toLowerCase(Locale.ROOT).replace(' ', '_');
        return entityType.contains(":") ? entityType : "minecraft:" + entityType;
    }

    private void drawMerchantPreviewTrades(IDrawContext context, MinecraftGameAssets gameAssets, int viewX, int viewY, int scale, int text, int muted) {
        List<JsonObject> offers = villageOffers();
        int selected = selectedVillageOfferIndex();
        int offerSlots = 6;
        int maxOffset = Math.max(0, offers.size() - offerSlots);
        int offset = Math.clamp(villagePreviewOfferScroll, 0, maxOffset);
        int visibleOffers = Math.min(offerSlots, offers.size() - offset);
        villagePreviewOfferScroll = offset;
        villagePreviewOfferOffset = offset;
        villagePreviewVisibleOffers = Math.max(0, visibleOffers);
        for (int i = 0; i < visibleOffers; i++) {
            int offerIndex = offset + i;
            JsonObject offer = offers.get(offerIndex);
            int buttonY = viewY + (18 + i * 20) * scale;
            int rowY = buttonY + scale;
            int rowX = viewX + 5 * scale;
            MinecraftUiPreviewRenderer.drawButton(context, gameAssets, rowX, buttonY, 88 * scale, 20 * scale, "", offerIndex == selected);
            boolean disabled = !jsonText(offer, "enabled").isBlank() && !Boolean.parseBoolean(jsonText(offer, "enabled"));
            drawRecipeItem(context, firstFilled(jsonText(offer, "cost"), "minecraft:emerald"), parseInt(jsonText(offer, "costAmount"), 1, 1, 64), viewX + 10 * scale, rowY + 2 * scale, scale);
            String cost2 = jsonText(offer, "cost2");
            if (!cost2.isBlank()) {
                drawRecipeItem(context, cost2, parseInt(jsonText(offer, "cost2Amount"), 1, 1, 64), viewX + 40 * scale, rowY + 2 * scale, scale);
            }
            if (!drawMinecraftSprite(context, gameAssets, disabled ? "container/villager/trade_arrow_out_of_stock" : "container/villager/trade_arrow", viewX + 60 * scale, rowY + 3 * scale, 10 * scale, 9 * scale)) {
                context.drawText(">", viewX + 61 * scale, rowY + 5 * scale, disabled ? muted : text, false);
            }
            drawRecipeItem(context, firstFilled(jsonText(offer, "result"), "minecraft:book"), parseInt(jsonText(offer, "resultAmount"), 1, 1, 64), viewX + 73 * scale, rowY + 2 * scale, scale);
            if (disabled) {
                if (!drawMinecraftSprite(context, gameAssets, "container/villager/out_of_stock", viewX + 187 * scale, viewY + 35 * scale, 28 * scale, 21 * scale)) {
                    context.drawText("X", viewX + 196 * scale, viewY + 41 * scale, 0xFFFF5555, false);
                }
            }
        }
        int addSlot = visibleOffers;
        villagePreviewAddX = viewX + 5 * scale;
        villagePreviewAddY = viewY + (18 + addSlot * 20) * scale;
        villagePreviewAddWidth = 88 * scale;
        villagePreviewAddHeight = 20 * scale;
        MinecraftUiPreviewRenderer.drawButton(context, gameAssets, villagePreviewAddX, villagePreviewAddY, villagePreviewAddWidth, villagePreviewAddHeight, "Add Trade", false);
        villagePreviewDeleteX = viewX + 136 * scale;
        villagePreviewDeleteY = viewY + 138 * scale;
        villagePreviewDeleteWidth = 70 * scale;
        villagePreviewDeleteHeight = 20 * scale;
        MinecraftUiPreviewRenderer.drawButton(context, gameAssets, villagePreviewDeleteX, villagePreviewDeleteY, villagePreviewDeleteWidth, villagePreviewDeleteHeight, "Delete Trade", false);
        if (offers.size() > offerSlots) {
            int scrollerY = viewY + (18 + (maxOffset > 0 ? Math.round((float) offset / maxOffset * 92) : 0)) * scale;
            drawMinecraftSprite(context, gameAssets, "container/villager/scroller", viewX + 94 * scale, scrollerY, 6 * scale, 27 * scale);
        } else {
            drawMinecraftSprite(context, gameAssets, "container/villager/scroller_disabled", viewX + 94 * scale, viewY + 18 * scale, 6 * scale, 27 * scale);
        }
        drawMinecraftSprite(context, gameAssets, "container/villager/experience_bar_background", viewX + 136 * scale, viewY + 16 * scale, 102 * scale, 5 * scale);
        drawMinecraftSprite(context, gameAssets, "container/villager/experience_bar_current", viewX + 136 * scale, viewY + 16 * scale, Math.clamp(parseInt(jsonPathText("level"), 1, 1, 5) * 20, 20, 100) * scale, 5 * scale);
    }

    private void drawMerchantPreviewSummary(IDrawContext context, int viewX, int viewY, int scale, int text, int muted) {
        String profession = formatOptionLabel(firstFilled(jsonPathText("profession"), "none"));
        String title = profession.equals("none") ? resourceDisplayName() : profession + " - " + villageLevelName(parseInt(jsonPathText("level"), 1, 1, 5));
        int titleX = viewX + (49 + 138) * scale - textWidth(title) / 2;
        int tradesX = viewX + (5 + 48) * scale - textWidth("Trades") / 2;
        context.drawText(title, titleX, viewY + 6 * scale, 0xFF404040, false);
        context.drawText("Trades", tradesX, viewY + 6 * scale, 0xFF404040, false);
    }

    private String villageLevelName(int level) {
        return switch (Math.clamp(level, 1, 5)) {
            case 2 -> "Apprentice";
            case 3 -> "Journeyman";
            case 4 -> "Expert";
            case 5 -> "Master";
            default -> "Novice";
        };
    }

    private List<JsonObject> villageOffers() {
        JsonArray offers = resource.has("offers") && resource.get("offers").isJsonArray() ? resource.getAsJsonArray("offers") : new JsonArray();
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement element : offers) {
            if (element != null && element.isJsonObject()) {
                result.add(element.getAsJsonObject());
            }
        }
        return result;
    }

    private List<JsonObject> editableVillageOffers() {
        JsonArray offers = villageOfferArray(false);
        List<JsonObject> result = new ArrayList<>();
        if (offers == null) {
            return result;
        }
        for (JsonElement element : offers) {
            if (element != null && element.isJsonObject()) {
                result.add(element.getAsJsonObject());
            }
        }
        return result;
    }

    private int villageOfferCount() {
        return editableVillageOffers().size();
    }

    private int selectedVillageOfferIndex() {
        int count = villageOfferCount();
        if (count <= 0) {
            selectedVillageOfferIndex = 0;
            return 0;
        }
        selectedVillageOfferIndex = Math.clamp(selectedVillageOfferIndex, 0, count - 1);
        return selectedVillageOfferIndex;
    }

    private JsonArray villageOfferArray(boolean create) {
        if (resource.has("offers") && resource.get("offers").isJsonArray()) {
            return resource.getAsJsonArray("offers");
        }
        if (!create) {
            return null;
        }
        JsonArray offers = new JsonArray();
        resource.add("offers", offers);
        return offers;
    }

    private void addVillageOffer() {
        snapshot();
        JsonArray offers = villageOfferArray(true);
        JsonObject offer = new JsonObject();
        offer.addProperty("cost", "minecraft:emerald");
        offer.addProperty("costAmount", 1);
        offer.addProperty("result", "minecraft:book");
        offer.addProperty("resultAmount", 1);
        offer.addProperty("weight", 1);
        offers.add(offer);
        selectedVillageOfferIndex = offers.size() - 1;
        villagePreviewOfferScroll = Math.max(0, offers.size() - 6);
        mountResourcePanel();
    }

    private void deleteSelectedVillageOffer() {
        JsonArray offers = villageOfferArray(false);
        if (offers == null || offers.size() == 0) {
            return;
        }
        snapshot();
        int index = selectedVillageOfferIndex();
        offers.remove(index);
        selectedVillageOfferIndex = Math.clamp(index, 0, Math.max(0, offers.size() - 1));
        villagePreviewOfferScroll = Math.clamp(villagePreviewOfferScroll, 0, Math.max(0, offers.size() - 6));
        mountResourcePanel();
    }

    private String villageOfferSummary(JsonObject offer, int index) {
        String cost = recipeItemSelectorLabel(firstFilled(jsonText(offer, "cost"), "minecraft:emerald"));
        String result = recipeItemSelectorLabel(firstFilled(jsonText(offer, "result"), "minecraft:book"));
        return "Trade " + (index + 1) + "  " + cost + " > " + result;
    }

    private boolean handleVillagePreviewClick(int mouseX, int mouseY) {
        String itemField = villagePreviewItemFieldAt(mouseX, mouseY);
        if (!itemField.isBlank()) {
            selectedVillageOfferIndex = villageOfferIndex(itemField);
            showRecipeMaterialSelector(itemField, mouseX, mouseY);
            return true;
        }
        if (inside(mouseX, mouseY, villagePreviewAddX, villagePreviewAddY, villagePreviewAddWidth, villagePreviewAddHeight)) {
            addVillageOffer();
            return true;
        }
        if (inside(mouseX, mouseY, villagePreviewDeleteX, villagePreviewDeleteY, villagePreviewDeleteWidth, villagePreviewDeleteHeight)) {
            deleteSelectedVillageOffer();
            return true;
        }
        for (int i = 0; i < villagePreviewVisibleOffers; i++) {
            int rowX = villagePreviewX + 5 * villagePreviewScale;
            int rowY = villagePreviewY + (18 + i * 20) * villagePreviewScale;
            if (inside(mouseX, mouseY, rowX, rowY, 88 * villagePreviewScale, 20 * villagePreviewScale)) {
                selectedVillageOfferIndex = villagePreviewOfferOffset + i;
                mountResourcePanel();
                return true;
            }
        }
        return false;
    }

    private boolean handleVillagePreviewRightClick(int mouseX, int mouseY) {
        String itemField = villagePreviewItemFieldAt(mouseX, mouseY);
        if (itemField.isBlank()) {
            return false;
        }
        captureResourceSnapshot();
        putJsonText(itemField, "");
        String amountField = villagePreviewAmountField(itemField);
        if (!amountField.isBlank()) {
            removeJsonPath(amountField);
        }
        selectedVillageOfferIndex = villageOfferIndex(itemField);
        reloadFields();
        return true;
    }

    private boolean changeVillagePreviewItemAmount(int mouseX, int mouseY, double verticalAmount) {
        String itemField = villagePreviewItemFieldAt(mouseX, mouseY);
        if (itemField.isBlank()) {
            return changeVillagePreviewTradeScroll(mouseX, mouseY, verticalAmount);
        }
        if (jsonPathText(itemField).isBlank()) {
            return false;
        }
        String amountField = villagePreviewAmountField(itemField);
        if (amountField.isBlank()) {
            return false;
        }
        int amount = parseInt(jsonPathText(amountField), 1, 1, 64);
        int next = Math.clamp(amount + (verticalAmount > 0 ? 1 : -1), 1, 64);
        if (next == amount) {
            return false;
        }
        captureResourceSnapshot();
        putJsonText(amountField, String.valueOf(next));
        selectedVillageOfferIndex = villageOfferIndex(itemField);
        return true;
    }

    private boolean changeVillagePreviewTradeScroll(int mouseX, int mouseY, double verticalAmount) {
        int maxOffset = Math.max(0, villageOfferCount() - 6);
        if (maxOffset <= 0 || !inside(mouseX, mouseY, villagePreviewX + 5 * villagePreviewScale, villagePreviewY + 18 * villagePreviewScale, 96 * villagePreviewScale, 122 * villagePreviewScale)) {
            return false;
        }
        int next = Math.clamp(villagePreviewOfferScroll + (verticalAmount > 0 ? -1 : 1), 0, maxOffset);
        if (next == villagePreviewOfferScroll) {
            return false;
        }
        villagePreviewOfferScroll = next;
        return true;
    }

    private String villagePreviewItemFieldAt(int mouseX, int mouseY) {
        for (int i = 0; i < villagePreviewVisibleOffers; i++) {
            int offerIndex = villagePreviewOfferOffset + i;
            int rowY = villagePreviewY + (18 + i * 20) * villagePreviewScale + villagePreviewScale;
            int size = 16 * villagePreviewScale;
            int costX = villagePreviewX + 10 * villagePreviewScale;
            if (inside(mouseX, mouseY, costX, rowY + 2 * villagePreviewScale, size, size)) {
                return "offers." + offerIndex + ".cost";
            }
            int cost2X = villagePreviewX + 40 * villagePreviewScale;
            if (inside(mouseX, mouseY, cost2X, rowY + 2 * villagePreviewScale, size, size)) {
                return "offers." + offerIndex + ".cost2";
            }
            int resultX = villagePreviewX + 73 * villagePreviewScale;
            if (inside(mouseX, mouseY, resultX, rowY + 2 * villagePreviewScale, size, size)) {
                return "offers." + offerIndex + ".result";
            }
        }
        return "";
    }

    private int villageOfferIndex(String field) {
        if (field == null || !field.startsWith("offers.")) {
            return selectedVillageOfferIndex();
        }
        String[] parts = field.split("\\.");
        return parts.length > 1 && isIndex(parts[1]) ? Integer.parseInt(parts[1]) : selectedVillageOfferIndex();
    }

    private String villagePreviewAmountField(String field) {
        if (field == null) {
            return "";
        }
        if (field.endsWith(".cost")) {
            return field + "Amount";
        }
        if (field.endsWith(".cost2")) {
            return field + "Amount";
        }
        if (field.endsWith(".result")) {
            return field + "Amount";
        }
        return "";
    }

    private boolean handleNpcPreviewClick(int mouseX, int mouseY, int button) {
        String field = npcPreviewEquipmentFieldAt(mouseX, mouseY);
        if (field.isBlank()) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            captureResourceSnapshot();
            putJsonText(field, "");
            reloadFields();
            return true;
        }
        showRecipeMaterialSelector(field, mouseX, mouseY);
        return true;
    }

    private String npcPreviewEquipmentFieldAt(int mouseX, int mouseY) {
        for (NpcEquipmentSlot slot : npcEquipmentSlots()) {
            if (inside(mouseX, mouseY, npcPreviewX + slot.x(), npcPreviewY + slot.y(), 16, 16)) {
                return slot.field();
            }
        }
        return "";
    }

    private List<NpcEquipmentSlot> npcEquipmentSlots() {
        return List.of(
            new NpcEquipmentSlot("equipment.mainHand", 74, 54),
            new NpcEquipmentSlot("equipment.offHand", 98, 54),
            new NpcEquipmentSlot("equipment.helmet", 74, 78),
            new NpcEquipmentSlot("equipment.chestplate", 98, 78),
            new NpcEquipmentSlot("equipment.leggings", 122, 78),
            new NpcEquipmentSlot("equipment.boots", 122, 54)
        );
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return width > 0 && height > 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private void renderLootRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
        int panelWidth = Math.min(300, previewWidth - 24);
        int panelHeight = 112;
        int panelX = previewX + Math.max(12, (previewWidth - panelWidth) / 2);
        int panelY = previewY + Math.max(12, (previewHeight - panelHeight) / 2);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xCC202018);
        context.drawText("Loot Table", panelX + 12, panelY + 10, text, false);
        List<JsonObject> entries = firstLootEntries();
        int x = panelX + 20;
        int y = panelY + 42;
        for (int i = 0; i < Math.min(6, entries.size()); i++) {
            JsonObject entry = entries.get(i);
            drawRecipeItem(context, firstFilled(jsonText(entry, "item"), "minecraft:stone"), parseInt(jsonText(entry, "maxAmount"), 1, 1, 64), x + i * 38, y, 1);
            context.drawText(firstFilled(jsonText(entry, "chance"), "100") + "%", x + i * 38 - 2, y + 22, muted, false);
        }
        if (entries.isEmpty()) {
            context.drawText("No Drops", panelX + 20, panelY + 50, muted, false);
        }
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
        if (!ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type)
            && !ReSyncResourceDragPayload.MOTD_PROFILE.equals(type)) {
            refreshResourcePanelFields();
            return;
        }
        mountResourcePanel();
    }

    private void mountResourcePanel() {
        if (studioResourcePanel == null) {
            return;
        }
        resourceFieldInputs.clear();
        resourceCodeFieldInputs.clear();
        resourceToggleFieldInputs.clear();
        resourceDropdownFieldInputs.clear();
        resourceSelectorButtons.clear();
        resourceBindingWidgets.clear();
        studioResourcePanelWidgets.clear();
        studioResourcePanelKey = ReSyncProjectMetadata.resourceKey(type, id);
        buildResourcePanel();
        resourcePanelMounted = true;
    }

    private void buildResourcePanel() {
        if (studioResourcePanel == null) {
            return;
        }
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        List<String> fields = editorFields().stream().filter(field -> !"id".equals(field)).toList();
        List<AnimatedWidget> widgets = new ArrayList<>();
        MountableButtonWidget summary = new MountableButtonWidget.Builder(resourceDisplayName())
            .description(resourceSummary())
            .iconPath(studioResourceIconPath(type, id))
            .build();
        summary.setSize(rowWidth, 30);
        widgets.add(summary);
        for (ResourcePanelSection section : editorSections(fields)) {
            if (!section.title().isBlank()) {
                widgets.add(studioPanelState.hint(section.title(), rowWidth));
            }
            for (String field : section.fields()) {
                widgets.add(fieldRow(field, rowWidth));
            }
        }
        if (ReSyncResourceDragPayload.MOTD_PROFILE.equals(type)) {
            widgets.add(motdIconUploadRow(rowWidth));
        }
        if (ReSyncResourceDragPayload.MESSAGE_RULE.equals(type)) {
            widgets.addAll(messageLogPanelWidgets(rowWidth));
        }
        widgets.add(panelSaveButton(this::save));
        for (AnimatedWidget widget : widgets) {
            ReSyncStudioPanelState.disableEntrance(widget);
        }
        studioResourcePanel.container().replaceWidgets(widgets);
        studioResourcePanelWidgets.clear();
        studioResourcePanelWidgets.addAll(widgets);
    }

    private boolean resourcePanelWidgetsMounted() {
        return studioResourcePanel != null
            && !studioResourcePanelWidgets.isEmpty()
            && studioResourcePanel.container().getWidgets().containsAll(studioResourcePanelWidgets);
    }

    private List<AnimatedWidget> messageLogPanelWidgets(int rowWidth) {
        List<AnimatedWidget> widgets = new ArrayList<>();
        AnimatedButton button = new AnimatedButton.Builder()
            .label("Messages")
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .entranceAnimation(false)
            .onClick(() -> {
                requestMessageLogPage(messageLogPage);
                showMessageLogPopup();
            })
            .build();
        ReSyncStudioPanelState.disableEntrance(button);
        widgets.add(studioPanelState.row("Samples", button, rowWidth, "Open Logged Messages"));
        return widgets;
    }

    private RowWidget messageLogControls(int width) {
        AnimatedButton refresh = new AnimatedButton.Builder()
            .label("Refresh")
            .size(70, 18)
            .entranceAnimation(false)
            .onClick(() -> requestMessageLogPage(messageLogPage))
            .build();
        AnimatedButton previous = new AnimatedButton.Builder()
            .label("Prev")
            .size(52, 18)
            .entranceAnimation(false)
            .onClick(() -> requestMessageLogPage(Math.max(0, messageLogPage - 1)))
            .build();
        AnimatedButton next = new AnimatedButton.Builder()
            .label("Next")
            .size(52, 18)
            .entranceAnimation(false)
            .onClick(() -> requestMessageLogPage(messageLogPage + 1))
            .build();
        RowWidget controls = new RowWidget.Builder()
            .size(width, 18)
            .padding(4)
            .addWidget(refresh, previous, next)
            .build();
        ReSyncStudioPanelState.disableEntrance(controls);
        return controls;
    }

    private void showMessageLogPopup() {
        if (messageLogPopup != null && messageLogPopup.isVisible()) {
            messageLogPopup.hide();
        }
        PopupWidget.Builder builder = new PopupWidget.Builder("Messages")
            .size(520, 360)
            .setResizable(true);
        builder.addRow("", true, 22, messageLogControls(480));
        List<JsonObject> entries = messageLogEntries();
        if (entries.isEmpty()) {
            builder.addRow("", true, 24, new MessageLogEntryWidget(null, 480, 22));
        } else {
            for (JsonObject entry : entries) {
                builder.addRow("", true, 24, new MessageLogEntryWidget(entry, 480, 22));
            }
        }
        messageLogPopup = builder.build();
        hostScreen().addDrawableChild(messageLogPopup);
        messageLogPopup.show();
    }

    private List<JsonObject> messageLogEntries() {
        JsonObject page = messageLogPage();
        JsonArray entries = page != null && page.has("entries") && page.get("entries").isJsonArray() ? page.getAsJsonArray("entries") : new JsonArray();
        List<JsonObject> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonElement element : entries) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String key = jsonText(entry, "source") + "\u0000" + jsonText(entry, "plainText");
            if (seen.add(key)) {
                result.add(entry);
            }
        }
        return result;
    }

    private class MessageLogEntryWidget extends AnimatedWidget {
        private final JsonObject entry;

        private MessageLogEntryWidget(JsonObject entry, int width, int height) {
            super(0, 0, width, height, "");
            this.entry = entry;
            setCursorHoverReactive(entry != null);
            entranceAnimationEnabled = false;
        }

        @Override
        protected void drawContent(IDrawContext context, int mouseX, int mouseY) {
            int bg = isMouseOver(mouseX, mouseY) && entry != null ? ThemeManager.getColor(ThemeColor.elementHoverBackground) : ThemeManager.getColor(ThemeColor.elementBackground);
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
            if (entry == null) {
                context.drawText("No Messages", getX() + 6, getY() + 6, ThemeManager.getColor(ThemeColor.textDark), false);
                return;
            }
            String source = messageSourceLabel(jsonText(entry, "source"));
            String text = clippedPlainText(jsonText(entry, "plainText"), Math.max(24, getWidth() / 6));
            context.drawText(source, getX() + 6, getY() + 6, ThemeManager.getColor(ThemeColor.textDark), false);
            context.drawRichText(text, getX() + 82, getY() + 6, ThemeManager.getColor(ThemeColor.text), true);
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            if (button != 0 || entry == null) {
                return;
            }
            selectMessageLogEntry(entry);
            if (messageLogPopup != null) {
                messageLogPopup.hide();
            }
        }
    }

    private JsonObject messageLogPage() {
        FlowManager manager = FlowManager.getInstance();
        return manager != null ? manager.getMessageLogPage(serverId) : null;
    }

    private void requestMessageLogPage(int page) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        messageLogPage = Math.max(0, page);
        manager.requestMessageLog(serverId, messageLogPage, messageLogPageSize, "", jsonText("source"));
    }

    private void selectMessageLogEntry(JsonObject entry) {
        selectedMessageText = jsonText(entry, "plainText");
        selectedMessageSource = jsonText(entry, "source");
        previewMessageSelectionAnchor = -1;
        previewMessageSelectionFocus = -1;
        if (!selectedMessageSource.isBlank()) {
            putJsonText("source", selectedMessageSource);
        }
        reloadFields();
    }

    private String clippedPlainText(String value, int maxLength) {
        String clean = value != null ? value.replace('\n', ' ').replace('\r', ' ').strip() : "";
        if (clean.length() <= maxLength) {
            return clean.isBlank() ? "Message" : clean;
        }
        return clean.substring(0, Math.max(1, maxLength - 1)) + "...";
    }

    private void renderMessageSelection(IDrawContext context, String text, int startX, int y) {
        if (text == null || text.isBlank() || previewMessageSelectionAnchor < 0 || previewMessageSelectionFocus < 0) {
            return;
        }
        int start = Math.min(previewMessageSelectionAnchor, previewMessageSelectionFocus);
        int end = Math.max(previewMessageSelectionAnchor, previewMessageSelectionFocus);
        if (start == end) {
            return;
        }
        String prefix = text.substring(0, Math.min(start, text.length()));
        String selection = text.substring(Math.min(start, text.length()), Math.min(end, text.length()));
        int x1 = startX + textWidth(prefix);
        int x2 = x1 + Math.max(2, textWidth(selection));
        context.drawInvertedRect(x1, y - 1, x2, y + 10);
    }

    private List<ResourcePanelSection> editorSections(List<String> fields) {
        if (ReSyncResourceDragPayload.CHAT.equals(type)) {
            return appendRemainingSections(List.of(
                new ResourcePanelSection("Channel", fields.stream().filter(field -> field.equals("displayName") || field.startsWith("channel.")).toList()),
                new ResourcePanelSection("Format", fields.stream().filter(field -> field.startsWith("format.")).toList()),
                new ResourcePanelSection("Rule", fields.stream().filter(field -> field.startsWith("rule.")).toList()),
                new ResourcePanelSection("Private Messages", fields.stream().filter(field -> field.startsWith("privateMessages.")).toList()),
                new ResourcePanelSection("Mentions", fields.stream().filter(field -> field.startsWith("mention.")).toList()),
                new ResourcePanelSection("Ignore", fields.stream().filter(field -> field.equals("ignorePlayersText")).toList())
            ), fields);
        }
        if (ReSyncResourceDragPayload.MESSAGE_RULE.equals(type)) {
            return appendRemainingSections(List.of(
                new ResourcePanelSection("Match", fields.stream().filter(field -> List.of("source", "contains", "players", "permission").contains(field)).toList()),
                new ResourcePanelSection("Action", fields.stream().filter(field -> List.of("action", "replacement", "flowPredicate", "flowId").contains(field)).toList()),
                new ResourcePanelSection("State", fields.stream().filter(field -> List.of("priority", "enabled").contains(field)).toList())
            ), fields);
        }
        if (ReSyncResourceDragPayload.VILLAGE_PROFILE.equals(type)) {
            return appendRemainingSections(List.of(
                new ResourcePanelSection("Profile", fields.stream().filter(field -> List.of("displayName", "profession", "villagerType", "level").contains(field)).toList()),
                new ResourcePanelSection("Trade", fields.stream().filter(field -> field.startsWith("offers.") || List.of("maxUses", "restockTicks", "lootTable").contains(field)).toList()),
                new ResourcePanelSection("Hooks", fields.stream().filter(field -> field.startsWith("hooks.")).toList())
            ), fields);
        }
        if (ReSyncResourceDragPayload.NPC_DEFINITION.equals(type)) {
            return appendRemainingSections(List.of(
                new ResourcePanelSection("NPC", fields.stream().filter(field -> List.of("displayName", "entityType", "ai", "gravity", "invulnerable", "followPlayer", "followRange").contains(field)).toList()),
                new ResourcePanelSection("Trade", fields.stream().filter(field -> List.of("tradeProfile", "lootTable").contains(field)).toList()),
                new ResourcePanelSection("Equipment", fields.stream().filter(field -> field.startsWith("equipment.")).toList()),
                new ResourcePanelSection("Hooks", fields.stream().filter(field -> field.startsWith("hooks.")).toList())
            ), fields);
        }
        if (ReSyncResourceDragPayload.LOOT_TABLE.equals(type)) {
            return appendRemainingSections(List.of(
                new ResourcePanelSection("Pools", fields.stream().filter(field -> field.startsWith("pools.")).toList()),
                new ResourcePanelSection("Hooks", fields.stream().filter(field -> field.startsWith("hooks.")).toList())
            ), fields);
        }
        return List.of(new ResourcePanelSection("", fields));
    }

    private List<ResourcePanelSection> appendRemainingSections(List<ResourcePanelSection> sections, List<String> fields) {
        Set<String> used = new LinkedHashSet<>();
        List<ResourcePanelSection> result = new ArrayList<>();
        for (ResourcePanelSection section : sections) {
            if (!section.fields().isEmpty()) {
                result.add(section);
                used.addAll(section.fields());
            }
        }
        List<String> remaining = fields.stream().filter(field -> !used.contains(field)).toList();
        if (!remaining.isEmpty()) {
            result.add(new ResourcePanelSection("Advanced", remaining));
        }
        return result;
    }

    private void refreshResourcePanelFields() {
        for (Map.Entry<String, TextInputWidget> entry : resourceFieldInputs.entrySet()) {
            TextInputWidget input = entry.getValue();
            String value = jsonPathText(entry.getKey());
            if (input != null && !input.isFocused() && !Objects.equals(input.getText(), value)) {
                input.setText(value);
            }
        }
        for (Map.Entry<String, CodeEditorWidget> entry : resourceCodeFieldInputs.entrySet()) {
            CodeEditorWidget input = entry.getValue();
            String value = jsonPathText(entry.getKey());
            if (input != null && !input.isFocused() && !Objects.equals(input.getText(), value)) {
                input.setText(value);
            }
        }
        for (Map.Entry<String, ToggleWidget> entry : resourceToggleFieldInputs.entrySet()) {
            ToggleWidget toggle = entry.getValue();
            String configured = jsonPathText(entry.getKey());
            boolean value = configured.isBlank() ? "enabled".equals(entry.getKey()) : Boolean.parseBoolean(configured);
            if (toggle != null && toggle.getValue() != value) {
                toggle.setValue(value);
            }
        }
        for (Map.Entry<String, DropDownWidget<String>> entry : resourceDropdownFieldInputs.entrySet()) {
            DropDownWidget<String> dropdown = entry.getValue();
            List<String> options = selectorOptions(entry.getKey());
            String value = resolveSelectedOption(options, jsonPathText(entry.getKey()));
            if (dropdown != null && !Objects.equals(dropdown.getSelectedItem(), value)) {
                dropdown.setSelectedItem(value);
            }
        }
        for (Map.Entry<String, AnimatedButton> entry : resourceSelectorButtons.entrySet()) {
            AnimatedButton button = entry.getValue();
            String selected;
            String label;
            if (isRecipeItemSelectorField(entry.getKey())) {
                selected = jsonPathText(entry.getKey());
                label = recipeItemSelectorLabel(selected);
            } else {
                List<String> options = selectorOptions(entry.getKey());
                selected = resolveSelectedOption(normalizedSelectorOptions(options, jsonPathText(entry.getKey())), jsonPathText(entry.getKey()));
                label = selectorLabel(entry.getKey(), selected);
            }
            if (button != null && !Objects.equals(button.getMessage(), label)) {
                button.setMessage(label);
            }
        }
    }

    private AnimatedWidget fieldRow(String field, int rowWidth) {
        String label = fieldLabel(field);
        if (recipeBindingField(field)) {
            return recipeBindingRow(field, label, rowWidth);
        }
        if (ReSyncResourceDragPayload.VILLAGE_PROFILE.equals(type) && "level".equals(field)) {
            return villageLevelSliderRow(label, rowWidth);
        }
        if (ReSyncResourceDragPayload.NPC_DEFINITION.equals(type) && "followRange".equals(field)) {
            return npcFollowRangeSliderRow(label, rowWidth);
        }
        if (runtimeFunctionBindingField(field)) {
            return runtimeFunctionBindingRow(field, label, rowWidth);
        }
        if (resourceLinkBindingField(field)) {
            return resourceLinkBindingRow(field, label, rowWidth);
        }
        if (flowBindingField(field)) {
            return flowBindingRow(field, label, rowWidth);
        }
        if ("conditions.world".equals(field)) {
            return worldConditionFieldRow(label, rowWidth);
        }
        if (toggleField(field)) {
            return toggleFieldRow(field, label, rowWidth);
        }
        if (dropdownField(field)) {
            return dropdownFieldRow(field, label, rowWidth);
        }
        if (ReSyncResourceDragPayload.NPC_DEFINITION.equals(type) && "entityType".equals(field)) {
            return entityTypeFieldRow(field, label, rowWidth);
        }
        if (isRecipeItemSelectorField(field)) {
            return recipeItemFieldRow(field, label, rowWidth);
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
            resourceCodeFieldInputs.put(field, input);
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
        resourceFieldInputs.put(field, input);
        return studioPanelState.row(label, input, rowWidth, jsonResourceDescription(field, label));
    }

    private AnimatedWidget recipeItemFieldRow(String field, String label, int rowWidth) {
        ensureRecipeItemCatalogLoaded();
        String selected = jsonPathText(field);
        AnimatedButton button = new AnimatedButton.Builder()
            .label(recipeItemSelectorLabel(selected))
            .size(174, 18)
            .entranceAnimation(false)
            .build();
        resourceSelectorButtons.put(field, button);
        button.setAction(() -> showRecipeMaterialSelector(field, button.getX(), button.getY() + button.getHeight()));
        return studioPanelState.row(label, button, rowWidth, jsonResourceDescription(field, label));
    }

    private AnimatedWidget entityTypeFieldRow(String field, String label, int rowWidth) {
        ensureEntityTypeCatalogLoaded();
        String selected = jsonPathText(field);
        AnimatedButton button = new AnimatedButton.Builder()
            .label(selectorLabel(field, selected))
            .size(174, 18)
            .entranceAnimation(false)
            .build();
        resourceSelectorButtons.put(field, button);
        button.setAction(() -> openEntityTypeSelector(field, button.getX(), button.getY() + button.getHeight()));
        return studioPanelState.row(label, button, rowWidth, jsonResourceDescription(field, label));
    }

    private boolean toggleField(String field) {
        return "enabled".equals(field) || "allowMiniMessage".equals(field) || "channel.allowMiniMessage".equals(field)
            || "ai".equals(field) || "gravity".equals(field) || "invulnerable".equals(field) || "followPlayer".equals(field);
    }

    private AnimatedWidget toggleFieldRow(String field, String label, int rowWidth) {
        String configured = jsonPathText(field);
        boolean value = configured.isBlank() ? defaultToggleValue(field) : Boolean.parseBoolean(configured);
        ToggleWidget toggle = new ToggleWidget.Builder()
            .label("")
            .toggled(value)
            .size(46, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onChange(val -> putJsonText(field, String.valueOf(val)))
            .build();
        ReSyncStudioPanelState.disableEntrance(toggle);
        resourceToggleFieldInputs.put(field, toggle);
        return studioPanelState.row(label, toggle, rowWidth, jsonResourceDescription(field, label));
    }

    private boolean defaultToggleValue(String field) {
        return "enabled".equals(field) || "gravity".equals(field) || "invulnerable".equals(field);
    }

    private AnimatedWidget villageLevelSliderRow(String label, int rowWidth) {
        int level = parseInt(jsonPathText("level"), 1, 1, 5);
        DoubleSliderWidget[] ref = new DoubleSliderWidget[1];
        DoubleSliderWidget slider = new DoubleSliderWidget.Builder()
            .label("Level " + level)
            .value((level - 1) / 4.0)
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onChange(() -> {
                DoubleSliderWidget widget = ref[0];
                if (widget == null) {
                    return;
                }
                int next = Math.clamp(1 + (int) Math.round(widget.getValue() * 4.0), 1, 5);
                widget.label = "Level " + next;
                putJsonText("level", String.valueOf(next));
            })
            .build();
        ref[0] = slider;
        ReSyncStudioPanelState.disableEntrance(slider);
        return studioPanelState.row(label, slider, rowWidth, jsonResourceDescription("level", label));
    }

    private AnimatedWidget npcFollowRangeSliderRow(String label, int rowWidth) {
        int range = parseInt(jsonPathText("followRange"), 12, 1, 64);
        DoubleSliderWidget[] ref = new DoubleSliderWidget[1];
        DoubleSliderWidget slider = new DoubleSliderWidget.Builder()
            .label("Range " + range)
            .value((range - 1) / 63.0)
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onChange(() -> {
                DoubleSliderWidget widget = ref[0];
                if (widget == null) {
                    return;
                }
                int next = Math.clamp(1 + (int) Math.round(widget.getValue() * 63.0), 1, 64);
                widget.label = "Range " + next;
                putJsonText("followRange", String.valueOf(next));
            })
            .build();
        ref[0] = slider;
        ReSyncStudioPanelState.disableEntrance(slider);
        return studioPanelState.row(label, slider, rowWidth, jsonResourceDescription("followRange", label));
    }

    private boolean dropdownField(String field) {
        return "source".equals(field) || "action".equals(field) || "rule.action".equals(field)
            || "playerCountMode".equals(field) || "villagerType".equals(field) || "location.world".equals(field)
            || "spawnMode".equals(field)
            || (ReSyncResourceDragPayload.TEXT_TEMPLATE.equals(type) && "mode".equals(field));
    }

    private AnimatedWidget dropdownFieldRow(String field, String label, int rowWidth) {
        List<String> options = selectorOptions(field);
        String selected = resolveSelectedOption(options, jsonPathText(field));
        DropDownWidget<String> dropdown = new DropDownWidget.Builder<>(options)
            .displayFunction(this::formatOptionLabel)
            .selectedItem(selected)
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onSelectionChanged(value -> {
                putJsonText(field, value);
                if (ReSyncResourceDragPayload.MESSAGE_RULE.equals(type) && "source".equals(field)) {
                    requestMessageLogPage(0);
                }
                if (rebuildOnSelection(field)) {
                    if (ReSyncResourceDragPayload.TEXT_TEMPLATE.equals(type) && "mode".equals(field)) {
                        mountResourcePanel();
                    } else {
                        reloadFields();
                    }
                }
            })
            .build();
        ReSyncStudioPanelState.disableEntrance(dropdown);
        resourceDropdownFieldInputs.put(field, dropdown);
        return studioPanelState.row(label, dropdown, rowWidth, jsonResourceDescription(field, label));
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
        return field != null && ("flowId".equals(field) || field.endsWith(".flowId") || "flowPredicate".equals(field) || field.endsWith("Flow") || field.contains("Flow"));
    }

    private boolean runtimeFunctionBindingField(String field) {
        return field != null && field.startsWith("hooks.") && field.endsWith("Action");
    }

    private AnimatedWidget runtimeFunctionBindingRow(String field, String label, int rowWidth) {
        CompactBindingWidget widget = new CompactBindingWidget.Builder(
            hostScreen(),
            List.of("None", "Function"),
            () -> jsonPathHas(field) ? "Function" : "None",
            mode -> {
                if ("Function".equals(mode)) {
                    ensureFunctionCall(field);
                } else {
                    removeJsonPath(field);
                }
                refreshResourcePanelFields();
            },
            () -> jsonPathHas(field) ? functionOptions() : List.of("none"),
            () -> jsonPathTextRaw(field + ".functionId"),
            value -> {
                ensureFunctionCall(field);
                putFunctionIdPathText(field + ".functionId", value);
                refreshResourcePanelFields();
            },
            () -> compactFunctionBindingInputs(field, runtimeFunctionShape(field)),
            () -> openFunctionBinding(field)
        )
            .createAction("Create New", () -> jsonPathHas(field), () -> createFunctionBindingTarget(field, runtimeFunctionShape(field)))
            .animationKey("json." + type + "." + field)
            .size(rowWidth, 18)
            .entranceAnimation(false)
            .build();
        resourceBindingWidgets.add(widget);
        ReSyncStudioPanelState.disableEntrance(widget);
        return studioPanelState.row(label, widget, rowWidth, jsonResourceDescription(field, label));
    }

    private boolean resourceLinkBindingField(String field) {
        return "links".equals(field);
    }

    private AnimatedWidget resourceLinkBindingRow(String field, String label, int rowWidth) {
        CompactBindingWidget widget = new CompactBindingWidget.Builder(
            hostScreen(),
            List.of("None", "Trade", "Loot Table"),
            this::resourceLinkMode,
            mode -> {
                if ("None".equals(mode)) {
                    resourceLinkDraftMode = "";
                    removeJsonPath("links");
                    removeJsonPath("dialog");
                    removeJsonPath("tradeProfile");
                    removeJsonPath("lootTable");
                } else {
                    resourceLinkDraftMode = mode;
                    ensureJsonPathText(resourceLinkField(mode), "");
                }
                refreshResourcePanelFields();
            },
            () -> {
                String mode = resourceLinkMode();
                return "None".equals(mode) ? List.of("none") : selectorOptions(resourceLinkField(mode));
            },
            () -> {
                String mode = resourceLinkMode();
                return "None".equals(mode) ? "" : resourceLinkText(resourceLinkField(mode), legacyResourceLinkField(mode));
            },
            value -> {
                String mode = resourceLinkMode();
                if (!"None".equals(mode)) {
                    putJsonText(resourceLinkField(mode), value);
                }
                refreshResourcePanelFields();
            },
            () -> List.of(),
            this::openLinkedResource
        )
            .animationKey("json." + type + "." + field)
            .size(rowWidth, 18)
            .entranceAnimation(false)
            .build();
        resourceBindingWidgets.add(widget);
        ReSyncStudioPanelState.disableEntrance(widget);
        return studioPanelState.row(label, widget, rowWidth, jsonResourceDescription(field, label));
    }

    private AnimatedWidget flowBindingRow(String field, String label, int rowWidth) {
        CompactBindingWidget widget = new CompactBindingWidget.Builder(
            hostScreen(),
            List.of("None", "Flow"),
            () -> hasConfiguredJsonText(field) ? "Flow" : "None",
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
                    if (host != null) {
                        host.openWorkspaceFlowEditor(flowId);
                    }
                }
            }
        )
            .createAction("Create New", () -> hasConfiguredJsonText(field), () -> createFlowBindingTarget(field))
            .animationKey("json." + type + "." + field)
            .size(rowWidth, 18)
            .entranceAnimation(false)
            .build();
        resourceBindingWidgets.add(widget);
        ReSyncStudioPanelState.disableEntrance(widget);
        return studioPanelState.row(label, widget, rowWidth, jsonResourceDescription(field, label));
    }

    private AnimatedWidget recipeBindingRow(String field, String label, int rowWidth) {
        String flowField = recipeBindingFlowField(field);
        String functionBase = recipeBindingFunctionBase(field);
        String commandField = recipeBindingCommandField(field);
        CompactBindingWidget widget = new CompactBindingWidget.Builder(
            hostScreen(),
            recipeBindingModes(flowField, commandField),
            () -> recipeBindingMode(flowField, functionBase, commandField),
            mode -> {
                if ("None".equals(mode)) {
                    if (!flowField.isBlank()) {
                        putJsonText(flowField, "");
                    }
                    if (!commandField.isBlank()) {
                        putJsonArrayLines(commandField, "");
                    }
                    putJsonText(functionBase + ".functionId", "");
                } else if ("Run Flow".equals(mode)) {
                    putJsonText(functionBase + ".functionId", "");
                    if (!commandField.isBlank()) {
                        putJsonArrayLines(commandField, "");
                    }
                    if (!flowField.isBlank() && !jsonPathHas(flowField)) {
                        ensureJsonPathText(flowField, "");
                    }
                } else if ("Run Function".equals(mode) || "Function".equals(mode)) {
                    if (!flowField.isBlank()) {
                        putJsonText(flowField, "");
                    }
                    if (!commandField.isBlank()) {
                        putJsonArrayLines(commandField, "");
                    }
                    if (!jsonPathHas(functionBase + ".functionId")) {
                        ensureFunctionCall(functionBase);
                    }
                } else if ("Run Command".equals(mode)) {
                    if (!flowField.isBlank()) {
                        putJsonText(flowField, "");
                    }
                    putJsonText(functionBase + ".functionId", "");
                    if (!commandField.isBlank() && !resource.has(commandField)) {
                        resource.add(commandField, new JsonArray());
                    }
                }
                refreshResourcePanelFields();
            },
            () -> "Run Function".equals(recipeBindingMode(flowField, functionBase, commandField)) || "Function".equals(recipeBindingMode(flowField, functionBase, commandField)) ? functionOptions() : "Run Flow".equals(recipeBindingMode(flowField, functionBase, commandField)) ? flowOptions() : List.of("none"),
            () -> {
                String mode = recipeBindingMode(flowField, functionBase, commandField);
                return "Run Function".equals(mode) || "Function".equals(mode) ? jsonPathTextRaw(functionBase + ".functionId") : "Run Flow".equals(mode) ? jsonPathTextRaw(flowField) : "Run Command".equals(mode) ? "Command" : "";
            },
            value -> {
                String mode = recipeBindingMode(flowField, functionBase, commandField);
                if ("Run Function".equals(mode) || "Function".equals(mode)) {
                    putJsonText(functionBase + ".functionId", value);
                } else if ("Run Flow".equals(mode) && !flowField.isBlank()) {
                    putJsonText(flowField, value);
                }
                refreshResourcePanelFields();
            },
            () -> compactRecipeBindingInputs(functionBase, commandField),
            () -> openRecipeBinding(functionBase, flowField, commandField)
        )
            .createAction("Create New", () -> "Run Function".equals(recipeBindingMode(flowField, functionBase, commandField)) || "Function".equals(recipeBindingMode(flowField, functionBase, commandField)) || "Run Flow".equals(recipeBindingMode(flowField, functionBase, commandField)), () -> createRecipeBindingTarget(flowField, functionBase, commandField))
            .animationKey("json." + type + "." + field)
            .size(rowWidth, 18)
            .entranceAnimation(false)
            .build();
        resourceBindingWidgets.add(widget);
        ReSyncStudioPanelState.disableEntrance(widget);
        return studioPanelState.row(label, widget, rowWidth, jsonResourceDescription(field, label));
    }

    private void refreshBindingWidgets() {
        refreshBindingWidgets(null);
    }

    private void refreshBindingWidgets(String flowId) {
        for (CompactBindingWidget widget : resourceBindingWidgets) {
            if (widget != null && (flowId == null || widget.referencesTarget(flowId))) {
                widget.refresh();
            }
        }
    }

    private boolean hasFlowBinding(String flowId) {
        for (CompactBindingWidget widget : resourceBindingWidgets) {
            if (widget != null && widget.referencesTarget(flowId)) {
                return true;
            }
        }
        return false;
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

    private String recipeBindingCommandField(String field) {
        return switch (field) {
            case "craftedBinding" -> "craftedCommands";
            case "cookedBinding" -> "cookedCommands";
            case "deniedBinding" -> "deniedCommands";
            default -> "";
        };
    }

    private List<String> recipeBindingModes(String flowField, String commandField) {
        if (flowField.isBlank() && commandField.isBlank()) {
            return CompactBindingSupport.PREDICATE_MODES;
        }
        return CompactBindingSupport.ACTION_MODES;
    }

    private String recipeBindingMode(String flowField, String functionBase, String commandField) {
        if (hasConfiguredJsonText(functionBase + ".functionId")) {
            return flowField.isBlank() && commandField.isBlank() ? "Function" : "Run Function";
        }
        if (!flowField.isBlank() && hasConfiguredJsonText(flowField)) {
            return "Run Flow";
        }
        if (!commandField.isBlank() && hasConfiguredJsonArray(commandField)) {
            return "Run Command";
        }
        return "None";
    }

    private boolean hasConfiguredJsonText(String field) {
        String value = jsonPathTextRaw(field);
        return value != null && !value.isBlank() && !"none".equalsIgnoreCase(value);
    }

    private boolean hasConfiguredJsonArray(String field) {
        JsonElement element = jsonPathElement(field);
        return element != null && element.isJsonArray() && element.getAsJsonArray().size() > 0;
    }

    private List<CompactBindingWidget.BindingInput> compactRecipeBindingInputs(String functionBase, String commandField) {
        if (!commandField.isBlank() && hasConfiguredJsonArray(commandField)) {
            return List.of(new CompactBindingWidget.BindingInput(
                "commands",
                "Commands",
                jsonArrayLines(commandField),
                FlowDataType.STRING.getColor(),
                null,
                value -> putJsonArrayLines(commandField, value),
                CompactBindingWidget.InputKind.COMMAND
            ));
        }
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

    private List<CompactBindingWidget.BindingInput> compactFunctionBindingInputs(String functionBase, CompactBindingSupport.FunctionShape shape) {
        FlowGraph function = selectedFunction(functionBase + ".functionId", shape);
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
        List<String> options = new ArrayList<>(CompactBindingSupport.functionInputOptions(input, functionInputContext(field)));
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

    private void openRecipeBinding(String functionBase, String flowField, String commandField) {
        String mode = recipeBindingMode(flowField, functionBase, commandField);
        String id = "Run Function".equals(mode) || "Function".equals(mode) ? jsonPathTextRaw(functionBase + ".functionId") : "Run Flow".equals(mode) ? jsonPathTextRaw(flowField) : "";
        if (!id.isBlank()) {
            if (host != null) {
                host.openWorkspaceFlowEditor(id);
            }
        }
    }

    private void openFunctionBinding(String functionBase) {
        String id = jsonPathTextRaw(functionBase + ".functionId");
        if (!id.isBlank() && host != null) {
            host.openWorkspaceFlowEditor(id);
        }
    }

    private void openLinkedResource(String field) {
        openLinkedResource();
    }

    private void openLinkedResource() {
        String mode = resourceLinkMode();
        if ("None".equals(mode) || host == null) {
            return;
        }
        String field = resourceLinkField(mode);
        String id = resourceLinkText(field, legacyResourceLinkField(mode));
        if (id.isBlank()) {
            return;
        }
        host.openWorkspaceResource(linkedResourceType(field), id);
    }

    private void createFlowBindingTarget(String field) {
        createBindingResource(ReSyncResourceDragPayload.FLOW, id -> {
            putJsonText(field, id);
            refreshResourcePanelFields();
        });
    }

    private void createRecipeBindingTarget(String flowField, String functionBase, String commandField) {
        String mode = recipeBindingMode(flowField, functionBase, commandField);
        if ("Run Function".equals(mode) || "Function".equals(mode)) {
            createBindingResource(ReSyncResourceDragPayload.FUNCTION, id -> {
                normalizeBindingFunction(id, recipeFunctionShape(functionBase));
                putJsonText(functionBase + ".functionId", id);
                refreshResourcePanelFields();
            });
        } else if ("Run Flow".equals(mode) && !flowField.isBlank()) {
            createBindingResource(ReSyncResourceDragPayload.FLOW, id -> {
                putJsonText(flowField, id);
                refreshResourcePanelFields();
            });
        }
    }

    private void createFunctionBindingTarget(String functionBase, CompactBindingSupport.FunctionShape shape) {
        createBindingResource(ReSyncResourceDragPayload.FUNCTION, id -> {
            normalizeBindingFunction(id, shape);
            putJsonText(functionBase + ".functionId", id);
            refreshResourcePanelFields();
        });
    }

    private void createBindingResource(String type, Consumer<String> onCreated) {
        ReSyncResourceCreator.showCreatePopup(hostScreen(), serverId, type, bindingCreateFolder(), null, result -> {
            if (onCreated != null) {
                onCreated.accept(result.id());
            }
            if (host != null) {
                host.refreshStudioWorkspace(true);
                host.openWorkspaceFlowEditor(result.id());
            }
        });
    }

    private CompactBindingSupport.FunctionShape recipeFunctionShape(String functionBase) {
        return functionBase != null && functionBase.contains("predicate")
            ? CompactBindingSupport.recipePredicateShape()
            : CompactBindingSupport.recipeActionShape();
    }

    private CompactBindingSupport.FunctionShape runtimeFunctionShape(String functionBase) {
        return ReSyncResourceDragPayload.VILLAGE_PROFILE.equals(type) || functionBase != null && functionBase.contains("complete")
            ? CompactBindingSupport.villageActionShape()
            : CompactBindingSupport.npcActionShape();
    }

    private String linkedResourceType(String field) {
        return switch (field) {
            case "links.lootTable", "lootTable" -> ReSyncResourceDragPayload.LOOT_TABLE;
            case "links.tradeProfile", "tradeProfile" -> ReSyncResourceDragPayload.VILLAGE_PROFILE;
            default -> ReSyncResourceDragPayload.FLOW;
        };
    }

    private String resourceLinkMode() {
        if (!resourceLinkDraftMode.isBlank()) {
            return resourceLinkDraftMode;
        }
        if (hasConfiguredJsonText("links.tradeProfile") || hasConfiguredJsonText("tradeProfile")) {
            return "Trade";
        }
        if (hasConfiguredJsonText("links.lootTable") || hasConfiguredJsonText("lootTable")) {
            return "Loot Table";
        }
        return "None";
    }

    private String resourceLinkField(String mode) {
        return switch (mode) {
            case "Trade" -> "links.tradeProfile";
            case "Loot Table" -> "links.lootTable";
            default -> "";
        };
    }

    private String legacyResourceLinkField(String mode) {
        return switch (mode) {
            case "Trade" -> "tradeProfile";
            case "Loot Table" -> "lootTable";
            default -> "";
        };
    }

    private void normalizeBindingFunction(String functionId, CompactBindingSupport.FunctionShape shape) {
        FlowManager manager = FlowManager.getInstance();
        FlowGraph function = manager != null ? manager.getFlowsForServer(serverId).get(functionId) : null;
        CompactBindingSupport.normalizeFunction(serverId, function, shape);
    }

    private String bindingCreateFolder() {
        FlowManager manager = FlowManager.getInstance();
        ReSyncProjectMetadata.ResourceEntry entry = manager != null ? manager.getProjectMetadata(serverId).findResource(type, id) : null;
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
            case "channel.prefix" -> "Channel prefix.\nShown before sender/message text in this chat profile.\nUse it for labels, ranks, or routing hints.";
            case "format.template" -> "Public chat layout.\nPlaceholders: {prefix}, {sender}, {message}.\nKeep the sender and message clearly readable.";
            case "channel.range" -> "Hearing range in blocks.\nNegative values mean global chat.\nPositive values only reach nearby viewers.";
            case "channel.speakPermission" -> "Permission required to send in this chat profile.\nEmpty means every player can speak.";
            case "channel.readPermission" -> "Permission required to receive this chat profile.\nEmpty means every player can read it.";
            case "channel.allowMiniMessage" -> "Allows MiniMessage parsing for player chat.\nKeep off unless players should be able to style messages.";
            case "channel.miniMessagePermission" -> "Permission required for player-authored MiniMessage.\nUsed only when Allow MiniMessage is on.";
            case "rule.contains" -> "Chat match text.\nWhen present, this chat rule runs only if the message contains it.\nLeave empty to apply the rule broadly.";
            case "rule.action" -> "Chat rule action.\nblock stops the message.\nreplace rewrites it.\nflow runs logic.\nchannel redirects it.";
            case "rule.replacement" -> "Replacement chat text.\nUse {message} to keep the original message inside the rewritten output.";
            case "rule.channel" -> "Redirect channel id.\nUsed when Action is channel.\nMust match another chat profile id.";
            case "rule.flowId" -> "Flow run by this chat rule.\nReceives the current sender, message, and channel context.";
            case "privateMessages.sender" -> "Private-message sender format.\nShown to the player sending the message.\nPlaceholders: {sender}, {receiver}, {message}.";
            case "privateMessages.receiver" -> "Private-message receiver format.\nShown to the player receiving the message.\nPlaceholders: {sender}, {receiver}, {message}.";
            case "privateMessages.spy" -> "Private-message spy format.\nShown to enabled spies who can see this sender/receiver pair.";
            case "privateMessages.privateMessageFlow" -> "Flow run after a private message is sent.\nReceives event.message and event.receiver.";
            case "mention.template" -> "Mention style.\nUse {player} where the mentioned player name should appear.";
            case "mention.mentionFlow" -> "Flow run when a player mention is applied.\nReceives mention/player context from chat handling.";
            case "ignorePlayersText" -> "Globally ignored players for this chat profile.\nOne name or UUID per line.\nMatching senders are hidden from receivers.";
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
                ? "Base text for text-template modes.\nSupports MiniMessage tags."
                : "Reusable text body.\nRendered by the resource that owns this field.";
            case "mode" -> ReSyncResourceDragPayload.TEXT_TEMPLATE.equals(type)
                ? "Text-template animation mode.\nframes: cycle each frame.\ntyping: reveal text over time.\nscroll: moving text window.\nbounce: moving text window that reverses at edges.\nblink: alternate visible and blank.\npulse, rainbow, wave, wipe, sparkle: cosmetic animated text."
                : "Resource mode.\nAvailable values depend on the current resource type.";
            case "framesText" -> "Animation frames.\nOne frame per line.\nUsed by frames mode.";
            case "frameMillis" -> "Animation frame duration in milliseconds.\nMinimum runtime value is 1 ms.\nDefault is 250 ms.";
            case "width" -> "Visible window width in characters.\nUsed by scroll and bounce modes.";
            case "visibleCharacters" -> "Visible character cap.\n0 means no cap.\nUsed by typing and wipe modes.";
            case "colorsText" -> "Color list.\nOne MiniMessage color or tag per line.\nUsed by wave mode.";
            case "secondaryColor" -> "Secondary MiniMessage color or tag.\nUsed by pulse and sparkle modes.";
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
        resourceSelectorButtons.put(field, button);
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
                if (ReSyncResourceDragPayload.MESSAGE_RULE.equals(type) && "source".equals(field)) {
                    requestMessageLogPage(0);
                }
                if (rebuildOnSelection(field)) {
                    reloadFields();
                }
            }, button.getX(), button.getY() + button.getHeight());
        });
        return studioPanelState.row(label, button, rowWidth, jsonResourceDescription(field, label));
    }

    private void showResourceSelector(String field, List<String> options, String selected, Consumer<String> onSelected, int selectorX, int selectorY) {
        String createType = selectorCreateResourceType(field);
        showStudioSelector(List.of(), selectorLabel(field, selected), selectorX, selectorY, selector -> {
            List<String> realOptions = options.stream().filter(this::isRealOption).distinct().toList();
            if (realOptions.stream().anyMatch(option -> "none".equalsIgnoreCase(option))) {
                selector.addItem(selectorLabel(field, "none"), () -> onSelected.accept("none"));
            }
            if (createType != null) {
                selector.addItem("Create New", () -> createBindingResource(createType, onSelected));
            }
            for (String option : realOptions.stream().filter(option -> !"none".equalsIgnoreCase(option)).sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
                selector.addItem(selectorLabel(field, option), () -> onSelected.accept(option));
            }
        });
    }

    private String selectorCreateResourceType(String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        if (field.endsWith(".functionId")) {
            return ReSyncResourceDragPayload.FUNCTION;
        }
        if ("flowId".equals(field) || field.endsWith(".flowId") || "flowPredicate".equals(field) || field.endsWith("Flow") || field.contains("Flow")) {
            return ReSyncResourceDragPayload.FLOW;
        }
        return null;
    }

    @Override
    protected void onStudioSelectorClosed() {
        pendingRecipeSelectionFields = new ArrayList<>();
    }

    private boolean searchableSelectorField(String field) {
        return "output.material".equals(field) || "template.material".equals(field) || "base.material".equals(field) || "addition.material".equals(field)
            || "profession".equals(field) || "villagerType".equals(field) || "entityType".equals(field) || "spawnMode".equals(field)
            || "lootTable".equals(field) || "tradeProfile".equals(field)
            || field.startsWith("equipment.") || field.matches("offers\\.\\d+\\.(cost|cost2|result)") || field.matches("pools\\.\\d+\\.entries\\.\\d+\\.item")
            || field.endsWith("Flow") || "flowId".equals(field) || field.endsWith(".flowId") || "flowPredicate".equals(field) || field.contains("Flow")
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
            case "enabled", "allowMiniMessage", "channel.allowMiniMessage" -> List.of("true", "false");
            case "ai", "gravity", "invulnerable", "followPlayer" -> List.of("true", "false");
            case "type" -> recipeTypeOptions();
            case "output.material", "template.material", "base.material", "addition.material" -> recipeItemOptions();
            case "profession" -> villagerProfessionOptions();
            case "villagerType" -> villagerTypeOptions();
            case "entityType" -> entityTypeOptions();
            case "spawnMode" -> List.of("manual");
            case "location.world" -> normalizedSelectorOptions(catalogOptions("server:minecraft:world"), jsonPathText(field));
            case "lootTable", "links.lootTable" -> jsonResourceOptions(ReSyncResourceType.LOOT_TABLE);
            case "tradeProfile", "links.tradeProfile" -> jsonResourceOptions(ReSyncResourceType.VILLAGE_PROFILE);
            case "playerCountMode" -> List.of("real", "hidden", "fixed");
            case "mode" -> ReSyncResourceDragPayload.TEXT_TEMPLATE.equals(type)
                ? List.of("frames", "typing", "scroll", "bounce", "blink", "pulse", "rainbow", "wave", "wipe", "sparkle")
                : List.of();
            case "action", "rule.action" -> switch (type) {
                case ReSyncResourceDragPayload.CHAT -> List.of("block", "replace", "flow", "channel");
                case ReSyncResourceDragPayload.MESSAGE_RULE -> List.of("replace_section", "replace", "append", "prepend", "remove", "flow");
                default -> List.of();
            };
            case "source" -> List.of("chat", "join", "quit", "kick", "death", "title", "actionbar", "bossbar", "openScreen", "packetText", "system");
            case "flowId", "flowPredicate", "craftedFlow", "deniedFlow", "cookedFlow", "privateMessageFlow", "mentionFlow", "rule.flowId", "privateMessages.privateMessageFlow", "mention.mentionFlow",
                 "hooks.openFlow", "hooks.completeFlow", "hooks.deniedFlow", "hooks.spawnFlow", "hooks.rightClickFlow", "hooks.leftClickFlow", "hooks.interactFlow", "hooks.damageFlow", "hooks.deathFlow", "hooks.despawnFlow",
                 "hooks.beforeRollFlow", "hooks.afterRollFlow", "hooks.deniedRollFlow" -> flowOptions();
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
                yield recipeSlotIndex(field) >= 0 || recipeIngredientIndex(field) >= 0 || field.startsWith("equipment.")
                    || field.matches("offers\\.\\d+\\.(cost|cost2|result)") || field.matches("pools\\.\\d+\\.entries\\.\\d+\\.item")
                    ? recipeItemOptions() : List.of();
            }
        };
    }

    private boolean rebuildOnSelection(String field) {
        return "type".equals(field)
            || "entityType".equals(field)
            || "playerCountMode".equals(field)
            || ("mode".equals(field) && ReSyncResourceDragPayload.TEXT_TEMPLATE.equals(type))
            || field.endsWith(".functionId");
    }

    private String selectorLabel(String field, String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        if (isRecipeItemSelectorField(field)) {
            return recipeItemSelectorLabel(value);
        }
        if (field.endsWith("Flow") || "flowId".equals(field) || field.endsWith(".flowId") || "flowPredicate".equals(field) || field.contains("Flow")) {
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

    private List<String> entityTypeOptions() {
        ensureEntityTypeCatalogLoaded();
        List<String> values = OptionCatalogCache.getInstance().getValues(serverId, ENTITY_TYPE_OPTIONS_SOURCE);
        if (!values.isEmpty()) {
            LinkedHashSet<String> options = new LinkedHashSet<>(values);
            options.add("player");
            return new ArrayList<>(options);
        }
        return FALLBACK_ENTITY_TYPE_OPTIONS;
    }

    private void ensureEntityTypeCatalogLoaded() {
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null && !OptionCatalogCache.getInstance().hasCatalog(serverId, ENTITY_TYPE_OPTIONS_SOURCE)) {
            manager.ensureFlowClient(serverId).requestOptionCatalog(ENTITY_TYPE_OPTIONS_SOURCE);
        }
    }

    private Map<String, OptionCatalogItem> entityTypeCatalogByValue() {
        Map<String, OptionCatalogItem> byValue = new LinkedHashMap<>();
        for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, ENTITY_TYPE_OPTIONS_SOURCE)) {
            if (item != null && item.getValue() != null && !item.getValue().isBlank()) {
                byValue.put(item.getValue(), item);
            }
        }
        return byValue;
    }

    private List<String> villagerProfessionOptions() {
        return List.of("none", "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher", "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");
    }

    private List<String> villagerTypeOptions() {
        return List.of("plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga");
    }

    private List<String> jsonResourceOptions(ReSyncResourceType type) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null || type == null) {
            return List.of("none");
        }
        List<String> values = new ArrayList<>(manager.getJsonResourcesForServer(serverId, type).keySet());
        values.sort(String.CASE_INSENSITIVE_ORDER);
        values.addFirst("none");
        return values;
    }

    private List<String> typedResourceOptions(String type) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null || type == null) {
            return List.of("none");
        }
        List<String> values = switch (type) {
            case ReSyncResourceDragPayload.GUI -> new ArrayList<>(manager.getGuisForServer(serverId).keySet());
            case ReSyncResourceDragPayload.SCOREBOARD -> new ArrayList<>(manager.getScoreboardsForServer(serverId).keySet());
            case ReSyncResourceDragPayload.TAB -> new ArrayList<>(manager.getTabsForServer(serverId).keySet());
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> new ArrayList<>(manager.getCustomContentForServer(serverId).keySet());
            default -> new ArrayList<>();
        };
        values.sort(String.CASE_INSENSITIVE_ORDER);
        values.addFirst("none");
        return values;
    }

    private void ensureRecipeItemCatalogLoaded() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null) {
            return;
        }
        if (!OptionCatalogCache.getInstance().hasCatalog(serverId, RECIPE_ITEM_OPTIONS_SOURCE)) {
            manager.ensureFlowClient(serverId).requestOptionCatalog(RECIPE_ITEM_OPTIONS_SOURCE);
        }
        if (!OptionCatalogCache.getInstance().hasCatalog(serverId, MATERIAL_OPTIONS_SOURCE)) {
            manager.ensureFlowClient(serverId).requestOptionCatalog(MATERIAL_OPTIONS_SOURCE);
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
        return OptionCatalogCache.getInstance().hasCatalog(serverId, RECIPE_ITEM_OPTIONS_SOURCE)
            || OptionCatalogCache.getInstance().hasCatalog(serverId, MATERIAL_OPTIONS_SOURCE);
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
        for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, MATERIAL_OPTIONS_SOURCE)) {
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
        if (!resourcePanelMounted || !resourcePanelWidgetsMounted()) {
            mountResourcePanel();
        } else {
            refreshResourcePanelFields();
            refreshBindingWidgets();
        }
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
            || field.startsWith("equipment.")
            || field.matches("offers\\.\\d+\\.(cost|cost2|result)")
            || field.matches("pools\\.\\d+\\.entries\\.\\d+\\.item")
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
        for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, MATERIAL_OPTIONS_SOURCE)) {
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
        if ("ignorePlayersText".equals(field) || field.endsWith(".template") || field.endsWith(".sender") || field.endsWith(".receiver") || field.endsWith(".spy") || field.endsWith(".replacement")) {
            return true;
        }
        return switch (field) {
            case "template", "sender", "receiver", "spy", "replacement", "text", "motdText", "format", "framesText", "colorsText" -> true;
            default -> false;
        };
    }

    private int codeFieldHeight(String field) {
        return switch (field) {
            case "motdText" -> 46;
            case "text", "template", "format", "replacement", "framesText", "colorsText", "ignorePlayersText" -> dynamicCodeFieldHeight(field);
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
            case ReSyncResourceDragPayload.CHAT -> firstFilled(jsonText("displayName"), jsonPathText("channel.prefix"), "Chat");
            case ReSyncResourceDragPayload.VILLAGE_PROFILE -> firstFilled(jsonPathText("profession"), "Village");
            case ReSyncResourceDragPayload.NPC_DEFINITION -> firstFilled(jsonPathText("entityType"), "NPC");
            case ReSyncResourceDragPayload.LOOT_TABLE -> firstFilled(jsonText("displayName"), "Loot Table");
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

    private String compactState(String value) {
        return value == null || value.isBlank() || "none".equalsIgnoreCase(value) ? "None" : "Set";
    }

    private String resourceLinkText(String field, String legacyField) {
        String value = jsonPathText(field);
        if (!value.isBlank() && !"none".equalsIgnoreCase(value)) {
            return value;
        }
        return jsonPathText(legacyField);
    }

    private String firstOfferText(String key) {
        JsonArray offers = resource.has("offers") && resource.get("offers").isJsonArray() ? resource.getAsJsonArray("offers") : new JsonArray();
        if (offers.isEmpty() || !offers.get(0).isJsonObject()) {
            return "";
        }
        return jsonText(offers.get(0).getAsJsonObject(), key);
    }

    private List<JsonObject> firstLootEntries() {
        JsonArray pools = resource.has("pools") && resource.get("pools").isJsonArray() ? resource.getAsJsonArray("pools") : new JsonArray();
        if (pools.isEmpty() || !pools.get(0).isJsonObject()) {
            return List.of();
        }
        JsonObject pool = pools.get(0).getAsJsonObject();
        JsonArray entries = pool.has("entries") && pool.get("entries").isJsonArray() ? pool.getAsJsonArray("entries") : new JsonArray();
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement entry : entries) {
            if (entry != null && entry.isJsonObject()) {
                result.add(entry.getAsJsonObject());
            }
        }
        return result;
    }

    private boolean handleRecipePreviewClick(int mouseX, int mouseY, int button) {
        if (recipePreviewLayout == null || recipePreviewScale <= 0) {
            return false;
        }
        String field = recipeFieldAt(mouseX, mouseY);
        if (field.isBlank()) {
            return false;
        }
        recipeHighlightOriginX = mouseX;
        recipeHighlightOriginY = mouseY;
        recipeHighlightPreviewNonce++;
        recipeHighlightSelectionNonce++;
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
            recipeHighlightSelectionNonce++;
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
            recipeHighlightSelectionNonce++;
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
        resourceEditHistoryBatch = true;
        try {
            for (String field : fields) {
                if (erase) {
                    deleteRecipeField(field);
                } else if (value != null && !value.isBlank()) {
                    putJsonText(field, value);
                }
            }
        } finally {
            resourceEditHistoryBatch = false;
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
        int selectedColor = ThemeManager.getDefaultAccent().getAccentColor();
        List<SlotInteractionGrid.SlotRect> selectedRects = new ArrayList<>();
        List<SlotInteractionGrid.SlotRect> dragRects = new ArrayList<>();
        for (RecipeSlotTarget target : recipeSlotTargets(recipeType, layout)) {
            boolean selected = Objects.equals(target.field(), selectedRecipeField);
            boolean dragTarget = Objects.equals(target.field(), dragRecipeTargetField) || dragRecipeTargetFields.contains(target.field());
            if ((!selected && !dragTarget) || target.point() == null || target.point().length < 2) {
                continue;
            }
            int size = Math.max(16, 16 * scale);
            SlotInteractionGrid.SlotRect rect = new SlotInteractionGrid.SlotRect(target.field().hashCode(), viewX + target.point()[0] * scale, viewY + target.point()[1] * scale, size);
            if (selected) {
                selectedRects.add(rect);
            }
            if (dragTarget) {
                dragRects.add(rect);
            }
        }
        SlotInteractionGrid.drawHighlights(context, dragRects, selectedColor, false, SlotInteractionGrid.animationKey("recipe_slot_drag", recipeHighlightAnimationScope, recipeHighlightPreviewNonce), recipeHighlightOriginX, recipeHighlightOriginY, SlotInteractionGrid.HighlightReveal.RIPPLE);
        SlotInteractionGrid.drawHighlights(context, selectedRects, selectedColor, true, SlotInteractionGrid.animationKey("recipe_slot_selected", recipeHighlightAnimationScope, recipeHighlightSelectionNonce), recipeHighlightOriginX, recipeHighlightOriginY, SlotInteractionGrid.HighlightReveal.GROUP);
    }

    private void deleteRecipeField(String field) {
        if (resourceEditHistoryBatch) {
            deleteRecipeFieldRaw(field);
            return;
        }
        captureResourceSnapshot();
        resourceEditHistoryBatch = true;
        try {
            deleteRecipeFieldRaw(field);
        } finally {
            resourceEditHistoryBatch = false;
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
        resourceEditHistoryBatch = true;
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
            resourceEditHistoryBatch = false;
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
        ItemSelectorWidget.Builder builder = new ItemSelectorWidget.Builder(this)
            .size(220, 240)
            .dismissOnSelect(true)
            .emptyMessage("No Items");
        builder.beginBatch();
        builder.addItem("none", "", "none empty clear", () -> applyRecipeItemSelection(field, "none"));
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
        showStudioSelector(builder.endBatch().build(), recipeItemSelectorLabel(selected), mouseX, mouseY);
    }

    private void openEntityTypeSelector(String field, int mouseX, int mouseY) {
        ensureEntityTypeCatalogLoaded();
        String selected = jsonPathText(field);
        List<String> values = entityTypeOptions();
        Map<String, OptionCatalogItem> catalogByValue = entityTypeCatalogByValue();
        ItemSelectorWidget.Builder builder = new ItemSelectorWidget.Builder(this)
            .size(220, 240)
            .dismissOnSelect(true)
            .emptyMessage("No Entities");
        builder.beginBatch();
        String lastGroup = null;
        boolean hasSelected = false;
        for (String value : values) {
            if (value == null || value.isBlank() || "Loading".equals(value) || "No Options".equals(value)) {
                continue;
            }
            OptionCatalogItem item = catalogByValue.get(value);
            String group = item != null && !item.getGroup().isBlank() ? item.getGroup() : "Entities";
            if (!group.equals(lastGroup)) {
                builder.addSectionHeader(group);
                lastGroup = group;
            }
            String label = item != null ? item.getLabel() : selectorLabel(field, value);
            String description = item != null ? item.getDescription() : "";
            String searchTerms = value + " " + group + " " + description;
            if (value.equals(selected)) {
                hasSelected = true;
            }
            builder.addItem(label, description, searchTerms, () -> applyEntityTypeSelection(field, value));
        }
        if (!selected.isBlank() && !hasSelected) {
            builder.addItem(selectorLabel(field, selected), "", selected, () -> applyEntityTypeSelection(field, selected));
        }
        showStudioSelector(builder.endBatch().build(), selectorLabel(field, selected), mouseX, mouseY);
    }

    private void applyEntityTypeSelection(String field, String value) {
        if (!isRealOption(value)) {
            return;
        }
        putJsonText(field, value);
        reloadFields();
    }

    private void applyRecipeItemSelection(String field, String value) {
        if (!isRealOption(value)) {
            return;
        }
        String selection = value == null || "none".equalsIgnoreCase(value) ? "" : value;
        recipeBrushValue = selection;
        if (!pendingRecipeSelectionFields.isEmpty()) {
            captureResourceSnapshot();
            resourceEditHistoryBatch = true;
            try {
                for (String pendingField : pendingRecipeSelectionFields) {
                    putJsonText(pendingField, selection);
                }
            } finally {
                resourceEditHistoryBatch = false;
                pendingRecipeSelectionFields = new ArrayList<>();
            }
        } else {
            putJsonText(field, selection);
        }
        reloadFields();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleActiveStudioSelectorMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
            && ReSyncResourceDragPayload.MESSAGE_RULE.equals(type)
            && handleMessagePreviewSelectionStart((int) mouseX, (int) mouseY)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
            && ReSyncResourceDragPayload.VILLAGE_PROFILE.equals(type)
            && handleVillagePreviewClick((int) mouseX, (int) mouseY)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
            && ReSyncResourceDragPayload.VILLAGE_PROFILE.equals(type)
            && handleVillagePreviewRightClick((int) mouseX, (int) mouseY)) {
            return true;
        }
        if ((button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            && ReSyncResourceDragPayload.NPC_DEFINITION.equals(type)
            && handleNpcPreviewClick((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        return (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            && ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type)
            && handleRecipePreviewClick((int) mouseX, (int) mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (handleActiveStudioSelectorMouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
            && ReSyncResourceDragPayload.MESSAGE_RULE.equals(type)
            && handleMessagePreviewSelectionRelease((int) mouseX, (int) mouseY)) {
            return true;
        }
        return (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            && ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type)
            && handleRecipePreviewRelease((int) mouseX, (int) mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (handleActiveStudioSelectorMouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
            && ReSyncResourceDragPayload.MESSAGE_RULE.equals(type)
            && handleMessagePreviewSelectionDrag((int) mouseX, (int) mouseY)) {
            return true;
        }
        return (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            && ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type)
            && handleRecipePreviewDrag((int) mouseX, (int) mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (handleActiveStudioSelectorMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (ReSyncResourceDragPayload.VILLAGE_PROFILE.equals(type) && changeVillagePreviewItemAmount((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        return ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type) && changeRecipeItemAmount((int) mouseX, (int) mouseY, verticalAmount);
    }

    private boolean handleMessagePreviewSelectionStart(int mouseX, int mouseY) {
        if (!messagePreviewHit(mouseX, mouseY)) {
            return false;
        }
        previewMessageSelecting = true;
        previewMessageSelectionAnchor = messagePreviewIndex(mouseX);
        previewMessageSelectionFocus = previewMessageSelectionAnchor;
        return true;
    }

    private boolean handleMessagePreviewSelectionDrag(int mouseX, int mouseY) {
        if (!previewMessageSelecting) {
            return false;
        }
        previewMessageSelectionFocus = messagePreviewIndex(mouseX);
        return true;
    }

    private boolean handleMessagePreviewSelectionRelease(int mouseX, int mouseY) {
        if (!previewMessageSelecting) {
            return false;
        }
        previewMessageSelecting = false;
        previewMessageSelectionFocus = messagePreviewIndex(mouseX);
        int start = Math.min(previewMessageSelectionAnchor, previewMessageSelectionFocus);
        int end = Math.max(previewMessageSelectionAnchor, previewMessageSelectionFocus);
        if (previewMessageText != null && start >= 0 && end > start && end <= previewMessageText.length()) {
            putJsonText("contains", previewMessageText.substring(start, end));
            refreshResourcePanelFields();
        }
        return true;
    }

    private boolean messagePreviewHit(int mouseX, int mouseY) {
        if (previewMessageText == null || previewMessageText.isBlank()) {
            return false;
        }
        int width = Math.max(12, textWidth(previewMessageText));
        return mouseX >= previewMessageX - 2 && mouseX <= previewMessageX + width + 2 && mouseY >= previewMessageY - 3 && mouseY <= previewMessageY + 12;
    }

    private int messagePreviewIndex(int mouseX) {
        if (previewMessageText == null || previewMessageText.isBlank()) {
            return 0;
        }
        int relative = Math.max(0, mouseX - previewMessageX);
        for (int index = 0; index <= previewMessageText.length(); index++) {
            String prefix = previewMessageText.substring(0, index);
            if (textWidth(prefix) >= relative) {
                return index;
            }
        }
        return previewMessageText.length();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleActiveStudioSelectorKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type) && handleStudioHistoryShortcut(keyCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return handleActiveStudioSelectorCharTyped(chr, modifiers);
    }

    private List<String> editorFields() {
        return switch (type) {
            case ReSyncResourceDragPayload.CHAT -> chatFields();
            case ReSyncResourceDragPayload.MOTD_PROFILE -> motdFields();
            case ReSyncResourceDragPayload.MESSAGE_RULE -> messageRuleFields();
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> recipeFields();
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> textTemplateFields();
            case ReSyncResourceDragPayload.VILLAGE_PROFILE -> villageFields();
            case ReSyncResourceDragPayload.NPC_DEFINITION -> npcFields();
            case ReSyncResourceDragPayload.LOOT_TABLE -> lootTableFields();
            default -> List.of("displayName");
        };
    }

    private List<String> chatFields() {
        List<String> fields = new ArrayList<>(List.of(
            "displayName",
            "channel.prefix",
            "format.template",
            "channel.range",
            "channel.speakPermission",
            "channel.readPermission",
            "channel.allowMiniMessage",
            "channel.miniMessagePermission",
            "rule.contains",
            "rule.action",
            "rule.replacement",
            "rule.channel",
            "rule.flowId"
        ));
        fields.addAll(List.of(
            "privateMessages.sender",
            "privateMessages.receiver",
            "privateMessages.spy",
            "privateMessages.privateMessageFlow",
            "mention.template",
            "mention.mentionFlow",
            "ignorePlayersText"
        ));
        return fields;
    }

    private List<String> messageRuleFields() {
        return new ArrayList<>(List.of("source", "contains", "replacement", "action", "flowPredicate", "flowId", "priority", "enabled"));
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
        List<String> fields = new ArrayList<>(List.of("mode"));
        switch (jsonText("mode").toLowerCase(Locale.ROOT)) {
            case "typing" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("visibleCharacters");
            }
            case "scroll", "scrolling" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("width");
            }
            case "bounce" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("width");
            }
            case "blink" -> {
                fields.add("text");
                fields.add("frameMillis");
            }
            case "pulse", "sparkle" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("color");
                fields.add("secondaryColor");
            }
            case "rainbow" -> {
                fields.add("text");
                fields.add("frameMillis");
            }
            case "wave" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("colorsText");
            }
            case "wipe" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("visibleCharacters");
            }
            default -> {
                fields.add("framesText");
                fields.add("frameMillis");
            }
        }
        return fields;
    }

    private List<String> villageFields() {
        List<String> fields = new ArrayList<>(List.of("displayName", "profession", "villagerType", "level", "maxUses", "restockTicks", "lootTable"));
        if (villageOfferCount() > 0) {
            int index = selectedVillageOfferIndex();
            fields.add("offers." + index + ".weight");
        }
        fields.addAll(List.of("hooks.openAction", "hooks.completeAction", "hooks.deniedAction"));
        return fields;
    }

    private List<String> npcFields() {
        return new ArrayList<>(List.of(
            "displayName", "entityType", "ai", "gravity", "invulnerable", "followPlayer", "followRange", "tradeProfile", "lootTable",
            "hooks.spawnAction", "hooks.rightClickAction", "hooks.leftClickAction", "hooks.despawnAction"
        ));
    }

    private List<String> lootTableFields() {
        return new ArrayList<>(List.of(
            "pools.0.rolls", "pools.0.entries.0.item", "pools.0.entries.0.minAmount", "pools.0.entries.0.maxAmount", "pools.0.entries.0.weight", "pools.0.entries.0.chance",
            "pools.0.entries.0.conditions", "pools.0.entries.0.components", "hooks.beforeRollFlow", "hooks.afterRollFlow", "hooks.deniedRollFlow"
        ));
    }

    private String fieldLabel(String field) {
        if (field.contains(".inputs.")) {
            String name = field.substring(field.lastIndexOf('.') + 1).replace('_', ' ');
            return name.isBlank() ? "Input" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        String knownLabel = switch (field) {
            case "displayName" -> "Name";
            case "channel.prefix" -> "Prefix";
            case "format.template" -> "Format";
            case "channel.range" -> "Range";
            case "channel.speakPermission" -> "Speak";
            case "channel.readPermission" -> "Read";
            case "channel.allowMiniMessage" -> "Allow MiniMessage";
            case "channel.miniMessagePermission" -> "MiniMessage Permission";
            case "rule.contains" -> "Find";
            case "rule.action" -> "Action";
            case "rule.replacement" -> "Replace With";
            case "rule.channel" -> "Channel";
            case "rule.flowId" -> "Rule Flow";
            case "privateMessages.sender" -> "Sender";
            case "privateMessages.receiver" -> "Receiver";
            case "privateMessages.spy" -> "Spy";
            case "privateMessages.privateMessageFlow" -> "Message Flow";
            case "mention.template" -> "Mention";
            case "mention.mentionFlow" -> "Mention Flow";
            case "ignorePlayersText" -> "Ignored Players";
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
            case "profession" -> "Profession";
            case "villagerType" -> "Type";
            case "level" -> "Level";
            case "maxUses" -> "Max Uses";
            case "restockTicks" -> "Restock";
            case "lootTable" -> "Loot Table";
            case "tradeProfile" -> "Trade";
            case "dialog" -> "Dialog";
            case "entityType" -> "Entity";
            case "spawnMode" -> "Spawn";
            case "location.world" -> "World";
            case "location.x" -> "X";
            case "location.y" -> "Y";
            case "location.z" -> "Z";
            case "location.yaw" -> "Yaw";
            case "location.pitch" -> "Pitch";
            case "invulnerable" -> "Invulnerable";
            case "ai" -> "AI";
            case "gravity" -> "Gravity";
            case "followPlayer" -> "Follow Player";
            case "followRange" -> "Follow Range";
            case "equipment.mainHand" -> "Main Hand";
            case "equipment.offHand" -> "Off Hand";
            case "equipment.helmet" -> "Helmet";
            case "equipment.chestplate" -> "Chestplate";
            case "equipment.leggings" -> "Leggings";
            case "equipment.boots" -> "Boots";
            case "offers.0.cost" -> "Cost";
            case "offers.0.costAmount" -> "Cost Amount";
            case "offers.0.cost2" -> "Cost 2";
            case "offers.0.cost2Amount" -> "Cost 2 Amount";
            case "offers.0.result" -> "Result";
            case "offers.0.resultAmount" -> "Result Amount";
            case "offers.0.weight" -> "Weight";
            case "pools.0.rolls" -> "Rolls";
            case "pools.0.entries.0.item" -> "Item";
            case "pools.0.entries.0.minAmount" -> "Min Amount";
            case "pools.0.entries.0.maxAmount" -> "Max Amount";
            case "pools.0.entries.0.weight" -> "Weight";
            case "pools.0.entries.0.chance" -> "Chance";
            case "pools.0.entries.0.conditions" -> "Conditions";
            case "pools.0.entries.0.components" -> "Components";
            case "hooks.openFlow" -> "Open";
            case "hooks.completeFlow" -> "Complete";
            case "hooks.deniedFlow" -> "Denied";
            case "hooks.spawnFlow" -> "Spawn";
            case "hooks.interactFlow" -> "Interact";
            case "hooks.damageFlow" -> "Damage";
            case "hooks.deathFlow" -> "Death";
            case "hooks.despawnFlow" -> "Despawn";
            case "hooks.openAction" -> "Open";
            case "hooks.completeAction" -> "Complete";
            case "hooks.deniedAction" -> "Denied";
            case "hooks.spawnAction" -> "Spawn";
            case "hooks.rightClickAction" -> "Right Click";
            case "hooks.leftClickAction" -> "Left Click";
            case "hooks.despawnAction" -> "Despawn";
            case "hooks.beforeRollFlow" -> "Before Roll";
            case "hooks.afterRollFlow" -> "After Roll";
            case "hooks.deniedRollFlow" -> "Denied Roll";
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
        if (field.matches("offers\\.\\d+\\.cost")) {
            return "Cost";
        }
        if (field.matches("offers\\.\\d+\\.costAmount")) {
            return "Cost Amount";
        }
        if (field.matches("offers\\.\\d+\\.cost2")) {
            return "Cost 2";
        }
        if (field.matches("offers\\.\\d+\\.cost2Amount")) {
            return "Cost 2 Amount";
        }
        if (field.matches("offers\\.\\d+\\.result")) {
            return "Result";
        }
        if (field.matches("offers\\.\\d+\\.resultAmount")) {
            return "Result Amount";
        }
        if (field.matches("offers\\.\\d+\\.weight")) {
            return "Weight";
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
        if ("id".equals(field)) {
            return;
        }
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
        if ("ignorePlayersText".equals(field)) {
            putJsonArrayLines("ignore.players", value);
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
        if ("ignorePlayersText".equals(field)) {
            return jsonArrayLines("ignore.players");
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
        return CompactBindingSupport.functionInputContextDefault(input, functionInputContext(field));
    }

    private String functionInputContext(String field) {
        if (field == null) {
            return "recipe";
        }
        if (field.startsWith("cookedAction.inputs.")) {
            return "recipe:cooked";
        }
        if (field.startsWith("craftedAction.inputs.")) {
            return "recipe:crafted";
        }
        if (field.startsWith("deniedAction.inputs.")) {
            return "recipe:denied";
        }
        if (field.startsWith("conditions.predicate.inputs.")) {
            return "recipe:predicate";
        }
        if (ReSyncResourceDragPayload.VILLAGE_PROFILE.equals(type) && field.startsWith("hooks.")) {
            return "village";
        }
        if (ReSyncResourceDragPayload.NPC_DEFINITION.equals(type) && field.startsWith("hooks.")) {
            return "npc";
        }
        return "recipe";
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
        JsonElement element = key != null && key.contains(".") ? jsonPathElement(key) : resource.get(key);
        JsonArray array = element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
        List<String> values = new ArrayList<>();
        for (JsonElement e : array) {
            if (!e.isJsonNull()) {
                values.add(e.getAsString());
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
            if (key != null && key.contains(".")) {
                removeJsonPath(key);
            } else {
                resource.remove(key);
            }
            return;
        }
        if (key != null && key.contains(".")) {
            putJsonPathElement(key, array);
        } else {
            resource.add(key, array);
        }
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
        JsonPathParent parent = jsonPathParent(field, true);
        if (parent == null) {
            return;
        }
        if (value == null || value.isBlank()) {
            removeJsonPath(field);
            return;
        }
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            jsonPathSet(parent, new JsonPrimitive(Boolean.parseBoolean(trimmed)));
            return;
        }
        try {
            jsonPathSet(parent, new JsonPrimitive(Integer.parseInt(trimmed)));
        } catch (NumberFormatException ignored) {
            jsonPathSet(parent, new JsonPrimitive(trimmed));
        }
    }

    private void putJsonPathElement(String field, JsonElement value) {
        JsonPathParent parent = jsonPathParent(field, true);
        if (parent != null) {
            jsonPathSet(parent, value);
        }
    }

    private void removeJsonPath(String field) {
        JsonPathParent parent = jsonPathParent(field, false);
        if (parent == null) {
            return;
        }
        if (parent.object() != null) {
            parent.object().remove(parent.key());
        } else if (parent.array() != null && isIndex(parent.key())) {
            int index = Integer.parseInt(parent.key());
            if (index >= 0 && index < parent.array().size()) {
                parent.array().remove(index);
            }
        }
        pruneEmptyPath(field.split("\\."));
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
        JsonElement current = resource;
        for (int i = 0; i < parts.length; i++) {
            if (current == null || current.isJsonNull()) {
                return null;
            }
            JsonElement element;
            if (current.isJsonObject()) {
                JsonObject object = current.getAsJsonObject();
                if (!object.has(parts[i]) || object.get(parts[i]).isJsonNull()) {
                    return null;
                }
                element = object.get(parts[i]);
            } else if (current.isJsonArray() && isIndex(parts[i])) {
                JsonArray array = current.getAsJsonArray();
                int index = Integer.parseInt(parts[i]);
                if (index < 0 || index >= array.size() || array.get(index).isJsonNull()) {
                    return null;
                }
                element = array.get(index);
            } else {
                return null;
            }
            if (i == parts.length - 1) {
                return element;
            }
            if (!element.isJsonObject() && !element.isJsonArray()) {
                return null;
            }
            current = element;
        }
        return null;
    }

    private JsonPathParent jsonPathParent(String field, boolean create) {
        if (field == null || field.isBlank()) {
            return null;
        }
        String[] parts = field.split("\\.");
        JsonElement current = resource;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            String next = parts[i + 1];
            boolean nextArray = isIndex(next);
            if (current.isJsonObject()) {
                JsonObject object = current.getAsJsonObject();
                if (!object.has(part) || object.get(part).isJsonNull() || (!object.get(part).isJsonObject() && !object.get(part).isJsonArray())) {
                    if (!create) {
                        return null;
                    }
                    object.add(part, nextArray ? new JsonArray() : new JsonObject());
                }
                current = object.get(part);
            } else if (current.isJsonArray() && isIndex(part)) {
                JsonArray array = current.getAsJsonArray();
                int index = Integer.parseInt(part);
                if (index < 0) {
                    return null;
                }
                while (create && array.size() <= index) {
                    array.add(new JsonObject());
                }
                if (index >= array.size()) {
                    return null;
                }
                JsonElement child = array.get(index);
                if (child == null || child.isJsonNull() || (!child.isJsonObject() && !child.isJsonArray())) {
                    if (!create) {
                        return null;
                    }
                    child = nextArray ? new JsonArray() : new JsonObject();
                    array.set(index, child);
                }
                current = child;
            } else {
                return null;
            }
        }
        JsonElement parent = current;
        String key = parts[parts.length - 1];
        return parent.isJsonObject() ? new JsonPathParent(parent.getAsJsonObject(), null, key)
            : parent.isJsonArray() ? new JsonPathParent(null, parent.getAsJsonArray(), key)
            : null;
    }

    private void jsonPathSet(JsonPathParent parent, JsonElement value) {
        JsonElement safeValue = value != null ? value : new JsonPrimitive("");
        if (parent.object() != null) {
            parent.object().add(parent.key(), safeValue);
        } else if (parent.array() != null && isIndex(parent.key())) {
            int index = Integer.parseInt(parent.key());
            while (parent.array().size() <= index) {
                parent.array().add(new JsonObject());
            }
            parent.array().set(index, safeValue);
        }
    }

    private boolean isIndex(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private record JsonPathParent(JsonObject object, JsonArray array, String key) {
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

    @Override
    protected String jsonText(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    @Override
    public void renderStudioOverlay(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (activeStudioSelector != null && activeStudioSelector.visible) {
            activeStudioSelector.render(context, mouseX, mouseY, delta);
            activeStudioSelector.renderHintOverlay(context);
        }
    }

    private int jsonObjectSize(String key) {
        return resource.has(key) && resource.get(key).isJsonObject() ? resource.getAsJsonObject(key).size() : 0;
    }

    private JsonObject jsonObject(String key) {
        return resource.has(key) && resource.get(key).isJsonObject() ? resource.getAsJsonObject(key) : new JsonObject();
    }

    private String resourceDisplayName() {
        return switch (type) {
            case ReSyncResourceDragPayload.CHAT -> "Chat";
            case ReSyncResourceDragPayload.MOTD_PROFILE -> "MOTD";
            case ReSyncResourceDragPayload.MESSAGE_RULE -> "Message Rule";
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> "Recipe";
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> "Text";
            case ReSyncResourceDragPayload.VILLAGE_PROFILE -> "Village";
            case ReSyncResourceDragPayload.NPC_DEFINITION -> "NPC";
            case ReSyncResourceDragPayload.LOOT_TABLE -> "Loot Table";
            default -> "Resource";
        };
    }

    private void drawFormattedLine(IDrawContext context, String value, int startX, int y, int fallbackColor, boolean shadow) {
        context.drawRichText(value, startX, y, fallbackColor, shadow);
    }

    private int centeredTextX(String value, int centerX) {
        return centerX - textWidth(value) / 2;
    }

    private String applyMentionPreview(String line) {
        String template = ReSyncResourceDragPayload.CHAT.equals(type) ? jsonPathText("mention.template") : "<yellow>@{player}</yellow>";
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
        if (previewMaterial.isBlank() || "none".equalsIgnoreCase(previewMaterial)) {
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
        if (amount > 1) {
            drawRecipeItemAmount(context, Math.clamp(amount, 1, 64), x, y, iconSize);
        }
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

    protected SquareButtonWidget headerButton(String icon, String hint, Runnable action) {
        return new SquareButtonWidget.Builder()
            .size(18, 18)
            .identifier(Identifier.icon(icon))
            .hint(hint)
            .onClick(action)
            .entranceAnimation(false)
            .build();
    }

    private Screen hostScreen() {
        return host != null ? host : this;
    }

    private int parseInt(String value, int fallback, int min, int max) {
        try {
            return Math.clamp(Integer.parseInt(value), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private int textWidth(String value) {
        String clean = safeText(value).replaceAll("(?i)[&�][0-9a-fk-or]", "").replaceAll("<[^>]+>", "");
        if (RemotelyClient.tr != null) {
            return RemotelyClient.tr.getWidth(clean);
        }
        return clean.length() * 6;
    }

    @Override
    public String getDesktopAppId() {
        return type + "-designer";
    }

    @Override
    public String getDesktopAppTitle() {
        return titleForType(type);
    }

    @Override
    public String getDesktopAppIconPath() {
        return iconForType(type);
    }

    @Override
    public DesktopWindowBehavior getDesktopWindowBehavior() {
        return DesktopWindowBehavior.SINGLETON;
    }

    @Override
    public boolean shouldForceSuperScreen() {
        return desktopMode && !(parent instanceof Screen);
    }

    @Override
    public List<AnimatedWidget> getStudioHeaderButtons() {
        return headerButtons();
    }

    protected static String titleForType(String type) {
        return switch (type) {
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> "Recipe Designer";
            case ReSyncResourceDragPayload.MOTD_PROFILE -> "MOTD Designer";
            case ReSyncResourceDragPayload.MESSAGE_RULE -> "Message Rule Designer";
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> "Text Designer";
            case ReSyncResourceDragPayload.CHAT -> "Chat Designer";
            case ReSyncResourceDragPayload.VILLAGE_PROFILE -> "Village Designer";
            case ReSyncResourceDragPayload.NPC_DEFINITION -> "NPC Designer";
            case ReSyncResourceDragPayload.LOOT_TABLE -> "Loot Table Designer";
            default -> "Resource Designer";
        };
    }

    protected static String iconForType(String type) {
        return switch (type) {
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> "crafting.png";
            case ReSyncResourceDragPayload.MOTD_PROFILE -> "hi.png";
            case ReSyncResourceDragPayload.MESSAGE_RULE -> "edit.png";
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> "text.png";
            case ReSyncResourceDragPayload.CHAT -> "chat.png";
            case ReSyncResourceDragPayload.VILLAGE_PROFILE -> "crafting.png";
            case ReSyncResourceDragPayload.NPC_DEFINITION -> "entity.png";
            case ReSyncResourceDragPayload.LOOT_TABLE -> "resources.png";
            default -> "edit.png";
        };
    }

}

