package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.data.playerdata.PlayerItem;
import redxax.oxy.remotely.flow.ui.MinecraftUiPreviewRenderer;
import restudio.rescreen.game.MinecraftAssetReference;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.game.MinecraftGameItems;
import restudio.rescreen.game.tooltip.MinecraftTextComponents;
import restudio.rescreen.game.tooltip.MinecraftTooltip;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReMouseButton;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.lwjgl.MinecraftRenderItem;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.SearchUtils;
import restudio.rescreen.util.Notification;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

public class InventoryWidget extends AnimatedWidget {
    private static final int INV_TEXTURE_WIDTH = 256;
    private static final int INV_TEXTURE_HEIGHT = 256;
    private static final int SLOT_SIZE = 18;
    private static final int ICON_SIZE = 16;
    private static final int INV_OFFSET_X = 7;
    private static final int INV_OFFSET_Y = 83;
    private static final int HOTBAR_OFFSET_X = 7;
    private static final int HOTBAR_OFFSET_Y = 141;
    private static final int ARMOR_OFFSET_X = 7;
    private static final int ARMOR_OFFSET_Y = 7;
    private static final int OFFHAND_OFFSET_X = 77;
    private static final int OFFHAND_OFFSET_Y = 61;
    private static final int CRAFT_OFFSET_X = 97;
    private static final int CRAFT_OFFSET_Y = 17;
    private static final int CRAFT_SLOT_0 = 80;
    private static final int CRAFT_SLOT_1 = 81;
    private static final int CRAFT_SLOT_2 = 82;
    private static final int CRAFT_SLOT_3 = 83;
    private static final int INV_TRIM_RIGHT = 80;
    private static final int INV_TRIM_BOTTOM = 90;
    private static final int CHEST_GUI_TEXTURE_WIDTH = 176;
    private static final int CHEST_GUI_TOP_MARGIN = 16;
    private static final int CHEST_GUI_SIDE_MARGIN = 7;
    private static final int CHEST_GUI_PLAYER_INV_OFFSET = 13;
    private static final int CHEST_GUI_HOTBAR_OFFSET = 71;
    private static final int CHEST_GUI_PLAYER_INV_HEIGHT = 96;
    private static final int CHEST_GUI_BOTTOM_TEXTURE_Y = 126;
    private static final int SEARCH_DIM_COLOR = 0x88000000;

    public enum Mode {
        INVENTORY,
        ENDER_CHEST
    }

    private final UnifiedPlayer player;
    private final PlayerManagerController controller;
    private final Mode mode;
    private final BooleanSupplier editableSupplier;
    private final Runnable interactionCallback;
    private final LongConsumer refreshScheduler;

    private final Map<Integer, PlayerItem> inventory = new HashMap<>();
    private final Map<Integer, PlayerItem> enderItems = new HashMap<>();
    private final PlayerItem[] armor = new PlayerItem[4];
    private PlayerItem offhand;

    private final Map<String, Integer> slotPositions = new LinkedHashMap<>();

    private int lastBaseX;
    private int lastBaseY;
    private float lastScale = 1f;
    private int lastVisibleW;
    private int lastVisibleH;
    private int lastGuiWidth;
    private int lastGuiHeight;

    private PlayerItem heldItem;
    private String lastDragSlot;
    private final Map<String, PlayerItem> pendingUpdates = new LinkedHashMap<>();
    private long lastClickAtMs;
    private String lastClickKey;
    private long syncBlockUntilMs;
    private String searchQuery = "";
    private int lastMouseX;
    private int lastMouseY;

    public InventoryWidget(int x, int y, int width, int height, UnifiedPlayer player, PlayerManagerController controller, PlayerData data, Mode mode, BooleanSupplier editableSupplier, Runnable interactionCallback, LongConsumer refreshScheduler) {
        super(x, y, width, height, "");
        setCursorHoverReactive(true);
        this.player = player;
        this.controller = controller;
        this.mode = mode;
        this.editableSupplier = editableSupplier;
        this.interactionCallback = interactionCallback;
        this.refreshScheduler = refreshScheduler;
        if (data != null) {
            if (data.inventory() != null) {
                for (PlayerItem item : data.inventory()) {
                    if (item != null) {
                        inventory.put(item.slot(), item);
                    }
                }
            }
            if (data.armor() != null) {
                mapArmor(data.armor());
            }
            if (data.enderChest() != null && data.enderChest().items() != null) {
                for (PlayerItem item : data.enderChest().items()) {
                    if (item != null) {
                        enderItems.put(item.slot(), item);
                    }
                }
            }
        }

        this.offhand = data != null && data.offhand() != null && !data.offhand().isEmpty() ? data.offhand().getFirst() : null;
        initSlotPositions();
        animateElevation = false;
        flat = true;
        transparent = true;
        active = false;
        enableHoverColors = false;
        entranceAnimationEnabled = false;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null ? "" : searchQuery.trim();
    }

    public boolean isInteractionActive() {
        return heldItem != null || !pendingUpdates.isEmpty() || System.currentTimeMillis() < syncBlockUntilMs;
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (mode == Mode.ENDER_CHEST) {
            drawEnderChest(ctx, mouseX, mouseY);
            return;
        }
        drawInventory(ctx, mouseX, mouseY);
    }

    @Override
    public void renderHintOverlay(IDrawContext context) {
        super.renderHintOverlay(context);
        drawHoveredItemTooltip(context);
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        markInteraction();
        if (!canManipulateInventory()) {
            return false;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        ReMouseButton button = event.button();
        SlotSelection selection = findSelection(mouseX, mouseY);
        if (selection == null) {
            if (heldItem != null && (button == ReMouseButton.LEFT || button == ReMouseButton.RIGHT)) {
                if (isWithinWidgetBounds(mouseX, mouseY)) {
                    return false;
                }
                heldItem = null;
                flushPendingUpdatesIfReady();
                return true;
            }
            return false;
        }
        if (selection.commandSlot == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean doubleLeftClick = button == ReMouseButton.LEFT && Objects.equals(lastClickKey, selection.key) && now - lastClickAtMs <= 250L;
        lastClickAtMs = now;
        lastClickKey = selection.key;
        if (doubleLeftClick) {
            boolean handled = handleDoubleClick(selection);
            flushPendingUpdatesIfReady();
            return handled;
        }
        if (button == ReMouseButton.MIDDLE) {
            if (selection.item != null) {
                heldItem = copyItem(selection.item, 64);
                return true;
            }
            return false;
        }
        if (button == ReMouseButton.LEFT) {
            boolean handled = handleLeftClick(selection);
            flushPendingUpdatesIfReady();
            return handled;
        }
        if (button == ReMouseButton.RIGHT) {
            boolean handled = handleRightClick(selection);
            flushPendingUpdatesIfReady();
            return handled;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(ReMouseEvent event) {
        markInteraction();
        if (!canManipulateInventory() || (event.button() != ReMouseButton.LEFT && event.button() != ReMouseButton.RIGHT) || heldItem == null || heldItem.count() <= 0) {
            return false;
        }
        SlotSelection selection = findSelection(event.x(), event.y());
        if (selection == null || selection.commandSlot == null) {
            return false;
        }
        if (selection.key.equals(lastDragSlot)) {
            return false;
        }
        if (placeOne(selection)) {
            lastDragSlot = selection.key;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        if (event.button() == ReMouseButton.LEFT) {
            lastDragSlot = null;
            flushPendingUpdatesIfReady();
        }
        if (event.button() == ReMouseButton.RIGHT) {
            flushPendingUpdatesIfReady();
        }
        return false;
    }

    private void drawInventory(IDrawContext ctx, int mouseX, int mouseY) {
        MinecraftGameAssets gameAssets = getGameAssets();
        MinecraftAssetReference backgroundReference = gameAssets.containerTexture("inventory.png");
        int availableW = Math.max(1, getWidth());
        int availableH = Math.max(1, getHeight());
        int bgW = INV_TEXTURE_WIDTH;
        int bgH = INV_TEXTURE_HEIGHT;
        int visibleW = Math.max(1, Math.min(bgW, INV_TEXTURE_WIDTH - INV_TRIM_RIGHT));
        int visibleH = Math.max(1, Math.min(bgH, INV_TEXTURE_HEIGHT - INV_TRIM_BOTTOM));
        int centerX = getX() + availableW / 2;
        int centerY = getY() + availableH / 2;
        int baseX = centerX - visibleW / 2;
        int baseY = centerY - visibleH / 2;
        lastBaseX = baseX;
        lastBaseY = baseY;
        lastScale = 1f;
        lastVisibleW = visibleW;
        lastVisibleH = visibleH;

        if (gameAssets.exists(backgroundReference)) {
            ctx.enableScissor(baseX, baseY, baseX + visibleW, baseY + visibleH);
            drawMinecraftTexture(ctx, gameAssets, backgroundReference, gameAssets.getImageId(backgroundReference), baseX, baseY, bgW, bgH, 0, 0, bgW, bgH);
            ctx.disableScissor();
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                int packed = slotPositions.getOrDefault("inv_" + slotIndex, pack(0, 0));
                drawItem(ctx, inventory.get(slotIndex), baseX, baseY, unpackX(packed), unpackY(packed), 1f);
            }
        }

        for (int col = 0; col < 9; col++) {
            int packed = slotPositions.getOrDefault("hotbar_" + col, pack(0, 0));
            drawItem(ctx, inventory.get(col), baseX, baseY, unpackX(packed), unpackY(packed), 1f);
        }

        int helmet = slotPositions.getOrDefault("helmet", pack(0, 0));
        int chest = slotPositions.getOrDefault("chest", pack(0, 0));
        int legs = slotPositions.getOrDefault("legs", pack(0, 0));
        int boots = slotPositions.getOrDefault("boots", pack(0, 0));
        drawItem(ctx, armor[3], baseX, baseY, unpackX(helmet), unpackY(helmet), 1f);
        drawItem(ctx, armor[2], baseX, baseY, unpackX(chest), unpackY(chest), 1f);
        drawItem(ctx, armor[1], baseX, baseY, unpackX(legs), unpackY(legs), 1f);
        drawItem(ctx, armor[0], baseX, baseY, unpackX(boots), unpackY(boots), 1f);

        int offhandPacked = slotPositions.getOrDefault("offhand", pack(0, 0));
        drawItem(ctx, offhand, baseX, baseY, unpackX(offhandPacked), unpackY(offhandPacked), 1f);

        int craft0 = slotPositions.getOrDefault("craft_0", pack(0, 0));
        int craft1 = slotPositions.getOrDefault("craft_1", pack(0, 0));
        int craft2 = slotPositions.getOrDefault("craft_2", pack(0, 0));
        int craft3 = slotPositions.getOrDefault("craft_3", pack(0, 0));
        drawItem(ctx, inventory.get(CRAFT_SLOT_0), baseX, baseY, unpackX(craft0), unpackY(craft0), 1f);
        drawItem(ctx, inventory.get(CRAFT_SLOT_1), baseX, baseY, unpackX(craft1), unpackY(craft1), 1f);
        drawItem(ctx, inventory.get(CRAFT_SLOT_2), baseX, baseY, unpackX(craft2), unpackY(craft2), 1f);
        drawItem(ctx, inventory.get(CRAFT_SLOT_3), baseX, baseY, unpackX(craft3), unpackY(craft3), 1f);

        drawHeldItem(ctx, mouseX, mouseY);
    }

    private void drawEnderChest(IDrawContext ctx, int mouseX, int mouseY) {
        MinecraftGameAssets gameAssets = getGameAssets();
        MinecraftAssetReference backgroundReference = gameAssets.containerTexture("generic_54.png");
        int availableW = Math.max(1, getWidth());
        int availableH = Math.max(1, getHeight());
        int rows = 3;
        int topHeight = CHEST_GUI_TOP_MARGIN + rows * SLOT_SIZE;
        int centerX = getX() + availableW / 2;
        int centerY = getY() + availableH / 2;
        int baseX = centerX - CHEST_GUI_TEXTURE_WIDTH / 2;
        int baseY = centerY - (topHeight + CHEST_GUI_PLAYER_INV_HEIGHT) / 2;
        lastBaseX = baseX;
        lastBaseY = baseY;
        lastGuiWidth = CHEST_GUI_TEXTURE_WIDTH;
        lastGuiHeight = topHeight + CHEST_GUI_PLAYER_INV_HEIGHT;

        if (gameAssets.exists(backgroundReference)) {
            Identifier backgroundId = gameAssets.getImageId(backgroundReference);
            drawMinecraftTexture(ctx, gameAssets, backgroundReference, backgroundId, baseX, baseY, CHEST_GUI_TEXTURE_WIDTH, topHeight, 0, 0, CHEST_GUI_TEXTURE_WIDTH, topHeight);
            drawMinecraftTexture(ctx, gameAssets, backgroundReference, backgroundId, baseX, baseY + topHeight, CHEST_GUI_TEXTURE_WIDTH, CHEST_GUI_PLAYER_INV_HEIGHT, 0, CHEST_GUI_BOTTOM_TEXTURE_Y, CHEST_GUI_TEXTURE_WIDTH, CHEST_GUI_PLAYER_INV_HEIGHT);
        }

        int gridX = CHEST_GUI_SIDE_MARGIN;
        int gridY = CHEST_GUI_TOP_MARGIN;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                drawItem(ctx, enderItems.get(slotIndex), baseX, baseY, gridX + col * SLOT_SIZE, gridY + row * SLOT_SIZE, 1f);
            }
        }

        int playerInvY = CHEST_GUI_TOP_MARGIN + rows * SLOT_SIZE + CHEST_GUI_PLAYER_INV_OFFSET;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                drawItem(ctx, inventory.get(slotIndex), baseX, baseY, gridX + col * SLOT_SIZE, playerInvY + row * SLOT_SIZE, 1f);
            }
        }

        int hotbarY = CHEST_GUI_TOP_MARGIN + rows * SLOT_SIZE + CHEST_GUI_HOTBAR_OFFSET;
        for (int col = 0; col < 9; col++) {
            drawItem(ctx, inventory.get(col), baseX, baseY, gridX + col * SLOT_SIZE, hotbarY, 1f);
        }

        drawHeldItem(ctx, mouseX, mouseY);
    }

    private void drawHeldItem(IDrawContext ctx, int mouseX, int mouseY) {
        if (heldItem == null) {
            return;
        }
        MinecraftRenderItem renderItem = toRenderItem(heldItem);
        if (renderItem != null) {
            ctx.drawItem(renderItem, mouseX - 8, mouseY - 8, 0);
        }
        if (heldItem.count() > 1) {
            String text = String.valueOf(heldItem.count());
            int textW = TextRenderer.tr.getWidth(text);
            ctx.drawText(text, mouseX + 8 - textW, mouseY + 7, 0xFFFFFFFF, true);
        }
    }

    private void drawItem(IDrawContext ctx, PlayerItem item, int baseX, int baseY, int slotX, int slotY, float scale) {
        if (item == null || item.id() == null || item.id().isBlank() || "minecraft:air".equalsIgnoreCase(item.id())) {
            return;
        }
        int slotSize = Math.round(SLOT_SIZE * scale);
        int iconSize = Math.max(8, Math.round(ICON_SIZE * scale));
        int left = baseX + Math.round(slotX * scale);
        int top = baseY + Math.round(slotY * scale);
        int iconX = left + (slotSize - iconSize) / 2;
        int iconY = top + (slotSize - iconSize) / 2;
        MinecraftRenderItem renderItem = toRenderItem(item);
        if (renderItem != null) {
            ctx.drawItem(renderItem, iconX, iconY, 0);
        }
        if (item.count() > 1) {
            String text = String.valueOf(item.count());
            int textW = TextRenderer.tr.getWidth(text);
            int textX = left + slotSize - textW - 1;
            int textY = top + slotSize - 8;
            ctx.drawText(text, textX, textY, 0xFFFFFFFF, true);
        }
        drawSearchOverlay(ctx, item, left, top, slotSize);
    }

    private void drawSearchOverlay(IDrawContext ctx, PlayerItem item, int left, int top, int slotSize) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return;
        }
        if (matchesSearch(item)) {
            Accent accent = ThemeManager.getAccent("nice");
            int color = accent != null ? accent.getAccentColor() : 0xFF7ED957;
            ctx.fill(left, top, left + slotSize, top + 1, color);
            ctx.fill(left, top + slotSize - 1, left + slotSize, top + slotSize, color);
            ctx.fill(left, top, left + 1, top + slotSize, color);
            ctx.fill(left + slotSize - 1, top, left + slotSize, top + slotSize, color);
            return;
        }
        ctx.fill(left + 1, top + 1, left + slotSize - 1, top + slotSize - 1, SEARCH_DIM_COLOR);
    }

    private void drawHoveredItemTooltip(IDrawContext context) {
        if (heldItem != null) {
            return;
        }
        SlotSelection selection = findSelection(lastMouseX, lastMouseY);
        if (selection == null || selection.item == null) {
            return;
        }
        MinecraftRenderItem renderItem = toRenderItem(selection.item);
        MinecraftTooltip fallback = buildItemFallbackTooltip(selection.item);
        int screenWidth = getWidth();
        int screenHeight = getHeight();
        Screen screen = ScreenManager.getInstance().getCurrentScreen();
        if (screen != null) {
            screenWidth = screen.getWidth();
            screenHeight = screen.getHeight();
        }
        if (renderItem != null) {
            context.pushScissorState();
            context.clearScissor();
            context.drawMinecraftItemTooltip(renderItem, fallback, lastMouseX, lastMouseY, screenWidth, screenHeight);
            context.popScissorState();
            return;
        }
        context.pushScissorState();
        context.clearScissor();
        context.drawMinecraftTooltip(fallback, lastMouseX, lastMouseY, screenWidth, screenHeight);
        context.popScissorState();
    }

    private MinecraftTooltip buildItemFallbackTooltip(PlayerItem item) {
        String title = formatLabel(item != null ? item.id() : null);
        if (title.isBlank()) {
            title = "Item";
        }
        return MinecraftTooltip.of(MinecraftTextComponents.fromValue(title));
    }

    private boolean matchesSearch(PlayerItem item) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return true;
        }
        if (item == null || item.id() == null || item.id().isBlank() || "minecraft:air".equalsIgnoreCase(item.id())) {
            return false;
        }
        String query = searchQuery.trim();
        String lowered = query.toLowerCase(Locale.ROOT);
        String raw = item.id().toLowerCase(Locale.ROOT);
        String label = formatLabel(item.id()).toLowerCase(Locale.ROOT);
        return raw.contains(lowered) || label.contains(lowered) || SearchUtils.isFuzzyMatch(label, query) || SearchUtils.isFuzzyMatch(raw, query);
    }

    private void initSlotPositions() {
        slotPositions.put("helmet", pack(ARMOR_OFFSET_X, ARMOR_OFFSET_Y));
        slotPositions.put("chest", pack(ARMOR_OFFSET_X, ARMOR_OFFSET_Y + SLOT_SIZE));
        slotPositions.put("legs", pack(ARMOR_OFFSET_X, ARMOR_OFFSET_Y + SLOT_SIZE * 2));
        slotPositions.put("boots", pack(ARMOR_OFFSET_X, ARMOR_OFFSET_Y + SLOT_SIZE * 3));
        slotPositions.put("offhand", pack(OFFHAND_OFFSET_X, OFFHAND_OFFSET_Y));
        slotPositions.put("craft_0", pack(CRAFT_OFFSET_X, CRAFT_OFFSET_Y));
        slotPositions.put("craft_1", pack(CRAFT_OFFSET_X + SLOT_SIZE, CRAFT_OFFSET_Y));
        slotPositions.put("craft_2", pack(CRAFT_OFFSET_X, CRAFT_OFFSET_Y + SLOT_SIZE));
        slotPositions.put("craft_3", pack(CRAFT_OFFSET_X + SLOT_SIZE, CRAFT_OFFSET_Y + SLOT_SIZE));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                slotPositions.put("inv_" + slotIndex, pack(INV_OFFSET_X + col * SLOT_SIZE, INV_OFFSET_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            slotPositions.put("hotbar_" + col, pack(HOTBAR_OFFSET_X + col * SLOT_SIZE, HOTBAR_OFFSET_Y));
        }
    }

    private void mapArmor(Iterable<PlayerItem> items) {
        int nextIndex = 0;
        for (PlayerItem item : items) {
            if (item == null) {
                continue;
            }
            int slot = item.slot();
            int index = -1;
            if (slot >= 0 && slot <= 3) {
                index = slot;
            } else if (slot >= 100 && slot <= 103) {
                index = slot - 100;
            }
            if (index < 0) {
                while (nextIndex < armor.length && armor[nextIndex] != null) {
                    nextIndex++;
                }
                index = nextIndex < armor.length ? nextIndex : -1;
            }
            if (index >= 0 && index < armor.length) {
                armor[index] = item;
            }
        }
    }

    private int pack(int x, int y) {
        return (x << 16) | (y & 0xFFFF);
    }

    private int unpackX(int packed) {
        return (packed >> 16) & 0xFFFF;
    }

    private int unpackY(int packed) {
        return packed & 0xFFFF;
    }

    private boolean handleLeftClick(SlotSelection selection) {
        PlayerItem slotItem = selection.item;
        if (heldItem == null) {
            if (slotItem == null) {
                return false;
            }
            heldItem = copyItem(slotItem, slotItem.count());
            setSlotItem(selection.key, null);
            sendSlotUpdate(selection.commandSlot, null);
            return true;
        }
        if (slotItem == null) {
            setSlotItem(selection.key, copyItem(heldItem, heldItem.count()));
            sendSlotUpdate(selection.commandSlot, heldItem);
            heldItem = null;
            return true;
        }
        if (canStack(slotItem, heldItem) && slotItem.count() < 64) {
            int transfer = Math.min(64 - slotItem.count(), heldItem.count());
            if (transfer <= 0) {
                return false;
            }
            PlayerItem updatedSlot = copyItem(slotItem, slotItem.count() + transfer);
            setSlotItem(selection.key, updatedSlot);
            sendSlotUpdate(selection.commandSlot, updatedSlot);
            int remain = heldItem.count() - transfer;
            heldItem = remain > 0 ? copyItem(heldItem, remain) : null;
            return true;
        }
        PlayerItem oldSlot = copyItem(slotItem, slotItem.count());
        setSlotItem(selection.key, copyItem(heldItem, heldItem.count()));
        sendSlotUpdate(selection.commandSlot, heldItem);
        heldItem = oldSlot;
        return true;
    }

    private boolean handleRightClick(SlotSelection selection) {
        PlayerItem slotItem = selection.item;
        if (heldItem == null) {
            if (slotItem == null) {
                return false;
            }
            int take = (slotItem.count() + 1) / 2;
            int remain = slotItem.count() - take;
            heldItem = copyItem(slotItem, take);
            PlayerItem updated = remain > 0 ? copyItem(slotItem, remain) : null;
            setSlotItem(selection.key, updated);
            sendSlotUpdate(selection.commandSlot, updated);
            return true;
        }
        return placeOne(selection);
    }

    private boolean placeOne(SlotSelection selection) {
        if (heldItem == null || heldItem.count() <= 0) {
            return false;
        }
        PlayerItem slotItem = selection.item;
        if (slotItem == null) {
            PlayerItem placed = copyItem(heldItem, 1);
            setSlotItem(selection.key, placed);
            sendSlotUpdate(selection.commandSlot, placed);
            heldItem = heldItem.count() > 1 ? copyItem(heldItem, heldItem.count() - 1) : null;
            return true;
        }
        if (!canStack(slotItem, heldItem) || slotItem.count() >= 64) {
            return false;
        }
        PlayerItem updatedSlot = copyItem(slotItem, slotItem.count() + 1);
        setSlotItem(selection.key, updatedSlot);
        sendSlotUpdate(selection.commandSlot, updatedSlot);
        heldItem = heldItem.count() > 1 ? copyItem(heldItem, heldItem.count() - 1) : null;
        return true;
    }

    private boolean handleDoubleClick(SlotSelection selection) {
        PlayerItem template = heldItem != null ? heldItem : selection.item;
        if (template == null) {
            return false;
        }
        int total = heldItem != null ? heldItem.count() : 0;
        for (String key : getGatherOrder()) {
            String commandSlot = resolveCommandSlotByKey(key);
            if (commandSlot == null) {
                continue;
            }
            PlayerItem slotItem = resolveItemByKey(key);
            if (!canStack(slotItem, template)) {
                continue;
            }
            if (total >= 64) {
                break;
            }
            int take = Math.min(slotItem.count(), 64 - total);
            if (take <= 0) {
                continue;
            }
            total += take;
            heldItem = copyItem(template, total);
            int remain = slotItem.count() - take;
            PlayerItem updated = remain > 0 ? copyItem(slotItem, remain) : null;
            setSlotItem(key, updated);
            sendSlotUpdate(commandSlot, updated);
        }
        return heldItem != null;
    }

    private Iterable<String> getGatherOrder() {
        if (mode == Mode.ENDER_CHEST) {
            LinkedHashMap<String, Boolean> keys = new LinkedHashMap<>();
            for (int slotIndex = 0; slotIndex < 27; slotIndex++) {
                keys.put("ender_" + slotIndex, Boolean.TRUE);
            }
            for (int slotIndex = 9; slotIndex < 36; slotIndex++) {
                keys.put("inv_" + slotIndex, Boolean.TRUE);
            }
            for (int slotIndex = 0; slotIndex < 9; slotIndex++) {
                keys.put("hotbar_" + slotIndex, Boolean.TRUE);
            }
            return keys.keySet();
        }
        return slotPositions.keySet();
    }

    private void setSlotItem(String key, PlayerItem item) {
        if (key.startsWith("ender_")) {
            int slot = parseIntSafe(key.substring("ender_".length()), -1);
            if (slot >= 0) {
                if (item == null) {
                    enderItems.remove(slot);
                } else {
                    enderItems.put(slot, item);
                }
            }
            return;
        }
        if (key.startsWith("inv_")) {
            int slot = parseIntSafe(key.substring("inv_".length()), -1);
            if (slot >= 0) {
                if (item == null) {
                    inventory.remove(slot);
                } else {
                    inventory.put(slot, item);
                }
            }
            return;
        }
        if (key.startsWith("hotbar_")) {
            int slot = parseIntSafe(key.substring("hotbar_".length()), -1);
            if (slot >= 0) {
                if (item == null) {
                    inventory.remove(slot);
                } else {
                    inventory.put(slot, item);
                }
            }
            return;
        }
        switch (key) {
            case "helmet" -> armor[3] = item;
            case "chest" -> armor[2] = item;
            case "legs" -> armor[1] = item;
            case "boots" -> armor[0] = item;
            case "offhand" -> offhand = item;
        }
    }

    private void sendSlotUpdate(String commandSlot, PlayerItem item) {
        if (commandSlot == null || player == null || player.getName() == null || player.getName().isBlank()) {
            return;
        }
        pendingUpdates.put(commandSlot, item == null ? null : copyItem(item, item.count()));
    }

    private boolean canStack(PlayerItem first, PlayerItem second) {
        return first != null && second != null && Objects.equals(first.id(), second.id()) && Objects.equals(first.tag(), second.tag());
    }

    private PlayerItem copyItem(PlayerItem item, int count) {
        return item == null ? null : new PlayerItem(item.id(), Math.max(1, count), item.slot(), item.tag());
    }

    private void flushPendingUpdatesIfReady() {
        if (heldItem != null || pendingUpdates.isEmpty()) {
            return;
        }
        syncBlockUntilMs = System.currentTimeMillis() + 550L;
        Map<String, PlayerItem> edits = new LinkedHashMap<>(pendingUpdates);
        pendingUpdates.clear();
        controller.editPlayerInventory(player, edits, controller.getInventoryRevision(player.getUuid())).whenComplete((success, error) -> {
            if (error != null || !Boolean.TRUE.equals(success)) {
                ScreenManager.getInstance().execute(() -> new Notification("Inventory", "Inventory changed before the edit completed.", Notification.Type.ERROR));
            }
        });
        if (refreshScheduler != null) {
            refreshScheduler.accept(180L);
        }
    }

    private SlotSelection findSelection(double mouseX, double mouseY) {
        if (mode == Mode.ENDER_CHEST) {
            return findEnderSelection(mouseX, mouseY);
        }
        for (Map.Entry<String, Integer> entry : slotPositions.entrySet()) {
            String key = entry.getKey();
            int packed = entry.getValue();
            int slotX = lastBaseX + Math.round(unpackX(packed) * lastScale);
            int slotY = lastBaseY + Math.round(unpackY(packed) * lastScale);
            int slotSize = Math.round(SLOT_SIZE * lastScale);
            if (mouseX < slotX || mouseX > slotX + slotSize || mouseY < slotY || mouseY > slotY + slotSize) {
                continue;
            }
            return new SlotSelection(key, resolveItemByKey(key), resolveCommandSlotByKey(key));
        }
        return null;
    }

    private SlotSelection findEnderSelection(double mouseX, double mouseY) {
        int gridX = CHEST_GUI_SIDE_MARGIN;
        int gridY = CHEST_GUI_TOP_MARGIN;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                int slotX = lastBaseX + gridX + col * SLOT_SIZE;
                int slotY = lastBaseY + gridY + row * SLOT_SIZE;
                if (mouseX < slotX || mouseX > slotX + SLOT_SIZE || mouseY < slotY || mouseY > slotY + SLOT_SIZE) {
                    continue;
                }
                return new SlotSelection("ender_" + slotIndex, enderItems.get(slotIndex), "enderchest." + slotIndex);
            }
        }
        int playerInvY = CHEST_GUI_TOP_MARGIN + 3 * SLOT_SIZE + CHEST_GUI_PLAYER_INV_OFFSET;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                int slotX = lastBaseX + gridX + col * SLOT_SIZE;
                int slotY = lastBaseY + playerInvY + row * SLOT_SIZE;
                if (mouseX < slotX || mouseX > slotX + SLOT_SIZE || mouseY < slotY || mouseY > slotY + SLOT_SIZE) {
                    continue;
                }
                return new SlotSelection("inv_" + slotIndex, inventory.get(slotIndex), "inventory." + (slotIndex - 9));
            }
        }
        int hotbarY = CHEST_GUI_TOP_MARGIN + 3 * SLOT_SIZE + CHEST_GUI_HOTBAR_OFFSET;
        for (int col = 0; col < 9; col++) {
            int slotX = lastBaseX + gridX + col * SLOT_SIZE;
            int slotY = lastBaseY + hotbarY;
            if (mouseX < slotX || mouseX > slotX + SLOT_SIZE || mouseY < slotY || mouseY > slotY + SLOT_SIZE) {
                continue;
            }
            return new SlotSelection("hotbar_" + col, inventory.get(col), "hotbar." + col);
        }
        return null;
    }

    private boolean isWithinWidgetBounds(double mouseX, double mouseY) {
        if (mode == Mode.ENDER_CHEST) {
            return mouseX >= lastBaseX && mouseX <= lastBaseX + lastGuiWidth && mouseY >= lastBaseY && mouseY <= lastBaseY + lastGuiHeight;
        }
        return mouseX >= lastBaseX && mouseX <= lastBaseX + lastVisibleW && mouseY >= lastBaseY && mouseY <= lastBaseY + lastVisibleH;
    }

    private PlayerItem resolveItemByKey(String key) {
        if (key.startsWith("ender_")) {
            int slot = parseIntSafe(key.substring("ender_".length()), -1);
            return slot >= 0 ? enderItems.get(slot) : null;
        }
        if (key.startsWith("inv_")) {
            int slot = parseIntSafe(key.substring("inv_".length()), -1);
            return slot >= 0 ? inventory.get(slot) : null;
        }
        if (key.startsWith("hotbar_")) {
            int slot = parseIntSafe(key.substring("hotbar_".length()), -1);
            return slot >= 0 ? inventory.get(slot) : null;
        }
        return switch (key) {
            case "helmet" -> armor[3];
            case "chest" -> armor[2];
            case "legs" -> armor[1];
            case "boots" -> armor[0];
            case "offhand" -> offhand;
            default -> null;
        };
    }

    private String resolveCommandSlotByKey(String key) {
        if (key.startsWith("ender_")) {
            int slot = parseIntSafe(key.substring("ender_".length()), -1);
            return slot >= 0 ? "enderchest." + slot : null;
        }
        if (key.startsWith("inv_")) {
            int slot = parseIntSafe(key.substring("inv_".length()), -1);
            return slot >= 9 ? "inventory." + (slot - 9) : null;
        }
        if (key.startsWith("hotbar_")) {
            int slot = parseIntSafe(key.substring("hotbar_".length()), -1);
            return slot >= 0 ? "hotbar." + slot : null;
        }
        return switch (key) {
            case "helmet" -> "armor.head";
            case "chest" -> "armor.chest";
            case "legs" -> "armor.legs";
            case "boots" -> "armor.feet";
            case "offhand" -> "weapon.offhand";
            default -> null;
        };
    }

    private int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void markInteraction() {
        if (interactionCallback != null) {
            interactionCallback.run();
        }
    }

    private boolean canManipulateInventory() {
        return editableSupplier != null && editableSupplier.getAsBoolean() && controller != null && controller.canEditPlayerInventory(player);
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

    private void drawMinecraftTexture(IDrawContext ctx, MinecraftGameAssets gameAssets, MinecraftAssetReference reference, Identifier fallbackId, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
        if (MinecraftUiPreviewRenderer.drawAssetRegion(ctx, gameAssets, reference, x, y, width, height, u, v, regionWidth, regionHeight, INV_TEXTURE_WIDTH, INV_TEXTURE_HEIGHT)) {
            return;
        }
        MinecraftUiPreviewRenderer.drawImage(ctx, fallbackId, x, y, width, height);
    }

    private MinecraftRenderItem toRenderItem(PlayerItem item) {
        return item == null ? null : MinecraftGameItems.fromTag(item.id(), item.count(), item.tag());
    }

    private String formatLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace("minecraft:", "").replace('_', ' ');
        if (cleaned.isEmpty()) {
            return cleaned;
        }
        String[] parts = cleaned.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return builder.toString();
    }

    private record SlotSelection(String key, PlayerItem item, String commandSlot) {
    }
}
