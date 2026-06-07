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
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.lwjgl.MinecraftRenderItem;
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
    private static final List<String> FALLBACK_MATERIAL_OPTIONS = List.of(
        "STONE", "COBBLESTONE", "OAK_PLANKS", "OAK_LOG", "GLASS", "GLASS_PANE",
        "GRAY_STAINED_GLASS_PANE", "WHITE_STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE",
        "RED_STAINED_GLASS_PANE", "GREEN_STAINED_GLASS_PANE", "BLUE_STAINED_GLASS_PANE",
        "BARRIER", "CHEST", "ENDER_CHEST", "ANVIL", "BOOK", "PAPER", "MAP",
        "COMPASS", "CLOCK", "DIAMOND", "EMERALD", "GOLD_INGOT", "IRON_INGOT",
        "NETHERITE_INGOT", "REDSTONE", "AMETHYST_SHARD", "ENDER_PEARL",
        "TOTEM_OF_UNDYING", "PLAYER_HEAD", "NAME_TAG"
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
    private RecipeStrokeMode recipeStrokeMode = RecipeStrokeMode.NONE;
    private String recipeStrokeValue = "";
    private String recipeBrushValue = "";
    private List<String> pendingRecipeSelectionFields = new ArrayList<>();
    private boolean draggingRecipeField;
    private boolean resourceEditHistoryBatch;
    private int x;
    private int y;
    private int width;
    private int height;
    protected final StudioScreen.History<String> resourceEditHistory = history(this::resourceSnapshot, this::restoreResourceSnapshot);

    private record ResourcePanelSection(String title, List<String> fields) {
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
        super.close();
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
            case ReSyncResourceDragPayload.CHAT -> renderChatRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
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
        String original = messagePreviewSource(source);
        String action = jsonText("action").toLowerCase(Locale.ROOT);
        context.drawText(messageSourceLabel(source) + "  " + messageActionLabel(action), previewX + 34, centerY - 6, muted, false);
        previewMessageText = original;
        previewMessageX = previewX + 34;
        previewMessageY = centerY + 12;
        renderMessageSelection(context, previewMessageText, previewMessageX, previewMessageY);
        drawFormattedLine(context, original, previewMessageX, previewMessageY, muted, true);
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
        int chatX = previewX + Math.max(14, previewWidth / 2 - 150);
        int chatY = previewY + Math.max(18, previewHeight / 2 - 48);
        String prefix = jsonPathText("channel.prefix");
        String template = jsonPathText("format.template");
        if (template.isBlank()) {
            template = "{prefix}{sender}: {message}";
        }
        String line = template.replace("{prefix}", prefix).replace("{sender}", "Steve").replace("{receiver}", "Alex").replace("{message}", "Hello @Alex");
        drawFormattedLine(context, applyMentionPreview(line), chatX + 12, chatY + 16, text, true);
        drawFormattedLine(context, "<gray>Alex: Looks good", chatX + 12, chatY + 36, muted, true);
        drawFormattedLine(context, "<yellow>@Steve</yellow> synced", chatX + 12, chatY + 56, text, true);
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
        if (!ReSyncResourceDragPayload.RECIPE_DEFINITION.equals(type) && !ReSyncResourceDragPayload.MOTD_PROFILE.equals(type)) {
            refreshResourcePanelFields();
            return;
        }
        mountResourcePanel();
    }

    private void mountResourcePanel() {
        if (studioResourcePanel == null) {
            return;
        }
        studioResourcePanel.container().clearWidgets();
        resourceFieldInputs.clear();
        resourceCodeFieldInputs.clear();
        resourceToggleFieldInputs.clear();
        resourceDropdownFieldInputs.clear();
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
        List<String> fields = editorFields();
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
        setStudioResourcePanelWidgets(widgets.toArray(new AnimatedWidget[0]));
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
        context.fill(x1 - 1, y - 1, x2 + 1, y + 10, 0x553B82F6);
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
        if (toggleField(field)) {
            return toggleFieldRow(field, label, rowWidth);
        }
        if (dropdownField(field)) {
            return dropdownFieldRow(field, label, rowWidth);
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

    private boolean toggleField(String field) {
        return "enabled".equals(field) || "allowMiniMessage".equals(field) || "channel.allowMiniMessage".equals(field);
    }

    private AnimatedWidget toggleFieldRow(String field, String label, int rowWidth) {
        String configured = jsonPathText(field);
        boolean value = configured.isBlank() ? "enabled".equals(field) : Boolean.parseBoolean(configured);
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

    private boolean dropdownField(String field) {
        return "source".equals(field) || "action".equals(field) || "rule.action".equals(field);
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

    private AnimatedWidget flowBindingRow(String field, String label, int rowWidth) {
        CompactBindingWidget widget = new CompactBindingWidget.Builder(
            hostScreen(),
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
                    if (host != null) {
                        host.openWorkspaceFlowEditor(flowId);
                    }
                }
            }
        )
            .createAction("Create New", () -> jsonPathHas(field), () -> createFlowBindingTarget(field))
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
        if (jsonPathHas(functionBase + ".functionId")) {
            return flowField.isBlank() && commandField.isBlank() ? "Function" : "Run Function";
        }
        if (!flowField.isBlank() && jsonPathHas(flowField)) {
            return "Run Flow";
        }
        if (!commandField.isBlank() && resource.has(commandField)) {
            return "Run Command";
        }
        return "None";
    }

    private List<CompactBindingWidget.BindingInput> compactRecipeBindingInputs(String functionBase, String commandField) {
        if (!commandField.isBlank() && resource.has(commandField)) {
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
            if (createType != null) {
                selector.addItem("Create New", () -> createBindingResource(createType, onSelected));
            }
            for (String option : options.stream().filter(this::isRealOption).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
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
            case "type" -> recipeTypeOptions();
            case "output.material", "template.material", "base.material", "addition.material" -> recipeItemOptions();
            case "playerCountMode" -> List.of("real", "hidden", "fixed");
            case "mode" -> ReSyncResourceDragPayload.TEXT_TEMPLATE.equals(type)
                ? List.of("frames", "typing", "scroll", "gradient", "blink", "random", "conditional")
                : List.of();
            case "action", "rule.action" -> switch (type) {
                case ReSyncResourceDragPayload.CHAT -> List.of("block", "replace", "flow", "channel");
                case ReSyncResourceDragPayload.MESSAGE_RULE -> List.of("replace_section", "replace", "append", "prepend", "remove", "flow");
                default -> List.of();
            };
            case "source" -> List.of("chat", "join", "quit", "kick", "death", "title", "actionbar", "bossbar", "openScreen", "packetText", "system");
            case "flowId", "flowPredicate", "craftedFlow", "deniedFlow", "cookedFlow", "privateMessageFlow", "mentionFlow", "rule.flowId", "privateMessages.privateMessageFlow", "mention.mentionFlow" -> flowOptions();
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
        return "type".equals(field) || "playerCountMode".equals(field) || field.endsWith(".functionId");
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
        reloadFields();
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

    private void applyRecipeItemSelection(String field, String value) {
        if (!isRealOption(value)) {
            return;
        }
        recipeBrushValue = value;
        if (!pendingRecipeSelectionFields.isEmpty()) {
            captureResourceSnapshot();
            resourceEditHistoryBatch = true;
            try {
                for (String pendingField : pendingRecipeSelectionFields) {
                    putJsonText(pendingField, value);
                }
            } finally {
                resourceEditHistoryBatch = false;
                pendingRecipeSelectionFields = new ArrayList<>();
            }
        } else {
            putJsonText(field, value);
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
        return List.of("mode", "text", "framesText", "frameMillis", "width", "visibleCharacters", "colorsText");
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

    private void putJsonPathElement(String field, JsonElement value) {
        String[] parts = field.split("\\.");
        JsonObject parent = resource;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!parent.has(part) || !parent.get(part).isJsonObject()) {
                parent.add(part, new JsonObject());
            }
            parent = parent.getAsJsonObject(part);
        }
        parent.add(parts[parts.length - 1], value);
    }

    private void removeJsonPath(String field) {
        String[] parts = field.split("\\.");
        JsonObject parent = resource;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parent.has(parts[i]) || !parent.get(parts[i]).isJsonObject()) {
                return;
            }
            parent = parent.getAsJsonObject(parts[i]);
        }
        parent.remove(parts[parts.length - 1]);
        pruneEmptyPath(parts);
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
            default -> "Resource";
        };
    }

    private void drawFormattedLine(IDrawContext context, String value, int startX, int y, int fallbackColor, boolean shadow) {
        context.drawRichText(value, startX, y, fallbackColor, shadow);
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
            default -> "edit.png";
        };
    }

}

