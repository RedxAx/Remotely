package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import org.lwjgl.glfw.GLFW;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;

import java.util.ArrayList;
import java.util.List;

public class LootTableDesignerScreen extends FocusedJsonResourceDesignerScreen {
    private static final String ENTITY_TYPE_OPTIONS_SOURCE = "server:minecraft:entity_type";
    private static final List<String> FALLBACK_ENTITY_TYPE_OPTIONS = List.of(
        "zombie", "skeleton", "creeper", "spider", "enderman", "witch", "slime", "villager", "iron_golem", "cow", "pig", "sheep", "chicken", "player"
    );

    protected int selectedLootEntryIndex;
    protected int lootPreviewX;
    protected int lootPreviewY;
    protected int lootPreviewScale = 1;
    protected int lootPreviewColumns = 9;
    protected int lootPreviewRows = 3;
    protected int lootPreviewEntryOffset;
    protected int lootHighlightNonce;
    protected final int lootHighlightAnimationScope = SlotInteractionGrid.animationScope();

    protected record LootSlot(int slotIndex, int entryIndex, SlotInteractionGrid.SlotRect rect) {
    }

    public LootTableDesignerScreen(StudioScreen owner, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, ReSyncResourceDragPayload.LOOT_TABLE, resourceId, resource, serverId, parent);
        ensureTriggerObject();
        ensureRecipeItemCatalogLoaded();
    }

    @Override
    protected boolean hasResourceHistory() {
        return true;
    }

    @Override
    protected void onResourceSnapshotRestored() {
        selectedLootEntryIndex = 0;
    }

    @Override
    protected void renderResourcePreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int mouseX, int mouseY, int text, int muted) {
        renderLootGrid(context, previewX, previewY, previewWidth, previewHeight, mouseX, mouseY);
    }

    @Override
    protected List<String> editorFields() {
        return lootTableFields();
    }

    @Override
    protected List<ResourcePanelSection> editorSections(List<String> fields) {
        return appendRemainingSections(List.of(
            new ResourcePanelSection("Trigger", fields.stream().filter(field -> field.startsWith("trigger.")).toList()),
            new ResourcePanelSection("Pool", fields.stream().filter(field -> field.startsWith("pools.")).toList()),
            new ResourcePanelSection("Hooks", fields.stream().filter(field -> field.startsWith("hooks.")).toList())
        ), fields);
    }

    @Override
    protected List<String> customSelectorOptions(String field) {
        if ("trigger.event".equals(field)) {
            return List.of("none", "block_break", "block_place", "entity_death", "item_use", "item_hit_entity");
        }
        if ("trigger.entity".equals(field)) {
            return entityTargetOptions();
        }
        if ("trigger.target".equals(field) && "entity_death".equalsIgnoreCase(triggerEvent())) {
            return entityTargetOptions();
        }
        if ("trigger.target".equals(field) || "trigger.tool".equals(field)) {
            return recipeItemOptions();
        }
        return null;
    }

    @Override
    protected boolean customDropdownField(String field) {
        return "trigger.event".equals(field);
    }

    @Override
    protected boolean toggleField(String field) {
        return super.toggleField(field) || "trigger.overrideDrops".equals(field);
    }

    @Override
    protected boolean defaultToggleValue(String field) {
        return "trigger.overrideDrops".equals(field) || super.defaultToggleValue(field);
    }

    @Override
    protected boolean customRecipeItemSelectorField(String field) {
        return field.matches("pools\\.\\d+\\.entries\\.\\d+\\.item")
            || "trigger.tool".equals(field)
            || "trigger.target".equals(field) && !"entity_death".equalsIgnoreCase(triggerEvent());
    }

    @Override
    protected boolean remountPanelOnFieldReload() {
        return true;
    }

    @Override
    protected boolean customRebuildOnSelection(String field) {
        return "trigger.event".equals(field);
    }

    @Override
    protected boolean handleResourceMouseClicked(int mouseX, int mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return handleLootGridClick(mouseX, mouseY);
        }
        return button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && handleLootGridRightClick(mouseX, mouseY);
    }

    @Override
    protected boolean handleResourceMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        return changeLootGridAmount(mouseX, mouseY, verticalAmount);
    }

    @Override
    protected boolean handleResourceKeyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) && !isStudioKeyboardInputFocused() && lootEntryCount() > 0) {
            deleteLootEntry(selectedLootEntryIndex());
            return true;
        }
        return false;
    }

    @Override
    protected String fieldLabel(String field) {
        return switch (field) {
            case "trigger.event" -> "Event";
            case "trigger.target" -> "Target";
            case "trigger.entity" -> "Entity";
            case "trigger.tool" -> "Tool";
            case "trigger.overrideDrops" -> "Override";
            default -> {
                if (field.matches("pools\\.\\d+\\.entries\\.\\d+\\.item")) {
                    yield "Item";
                }
                if (field.matches("pools\\.\\d+\\.entries\\.\\d+\\.minAmount")) {
                    yield "Min Amount";
                }
                if (field.matches("pools\\.\\d+\\.entries\\.\\d+\\.maxAmount")) {
                    yield "Max Amount";
                }
                if (field.matches("pools\\.\\d+\\.entries\\.\\d+\\.weight")) {
                    yield "Weight";
                }
                if (field.matches("pools\\.\\d+\\.entries\\.\\d+\\.chance")) {
                    yield "Chance";
                }
                if (field.matches("pools\\.\\d+\\.entries\\.\\d+\\.conditions")) {
                    yield "Conditions";
                }
                if (field.matches("pools\\.\\d+\\.entries\\.\\d+\\.components")) {
                    yield "Components";
                }
                yield super.fieldLabel(field);
            }
        };
    }

    @Override
    protected String resourceSummary() {
        return firstFilled(jsonText("displayName"), "Loot Table");
    }

    @Override
    protected String resourceDisplayName() {
        return "Loot Table";
    }

    protected void renderLootGrid(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int mouseX, int mouseY) {
        int scale = previewWidth >= 420 && previewHeight >= 180 ? 2 : 1;
        int slotSize = 18 * scale;
        int rows = Math.clamp((previewHeight - 24) / Math.max(1, slotSize), 3, 6);
        lootPreviewRows = rows;
        int gridWidth = lootPreviewColumns * slotSize;
        int gridHeight = lootPreviewRows * slotSize;
        lootPreviewX = previewX + Math.max(0, (previewWidth - gridWidth) / 2);
        lootPreviewY = previewY + Math.max(0, (previewHeight - gridHeight) / 2);
        lootPreviewScale = scale;
        int visibleSlots = lootPreviewColumns * lootPreviewRows;
        lootPreviewEntryOffset = Math.clamp(lootPreviewEntryOffset, 0, Math.max(0, lootEntryCount() - visibleSlots));
        List<JsonObject> entries = firstLootEntries();
        for (int slot = 0; slot < visibleSlots; slot++) {
            int x = lootPreviewX + (slot % lootPreviewColumns) * slotSize;
            int y = lootPreviewY + (slot / lootPreviewColumns) * slotSize;
            drawLootSlot(context, x, y, slotSize, inside(mouseX, mouseY, x, y, slotSize, slotSize));
            int entryIndex = lootPreviewEntryOffset + slot;
            if (entryIndex < entries.size()) {
                JsonObject entry = entries.get(entryIndex);
                drawRecipeItem(context, jsonText(entry, "item"), parseInt(jsonText(entry, "maxAmount"), 1, 1, 64), x + scale, y + scale, scale);
            }
        }
        drawSelectedLootSlot(context);
    }

    protected void drawLootSlot(IDrawContext context, int x, int y, int size, boolean hovered) {
        context.fill(x, y, x + size, y + size, 0xFF545454);
        context.fill(x + 1, y + 1, x + size - 1, y + size - 1, hovered ? 0xFF777777 : 0xFF686868);
        context.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0xFF3E3E3E);
    }

    protected void drawSelectedLootSlot(IDrawContext context) {
        if (lootEntryCount() <= 0) {
            return;
        }
        int selected = selectedLootEntryIndex();
        List<SlotInteractionGrid.SlotRect> rects = lootVisibleSlots().stream()
            .filter(slot -> slot.entryIndex() == selected)
            .map(LootSlot::rect)
            .toList();
        SlotInteractionGrid.drawHighlights(context, rects, ThemeManager.getDefaultAccent().getAccentColor(), true, SlotInteractionGrid.animationKey("loot_slot_selected", lootHighlightAnimationScope, lootHighlightNonce), lootPreviewX, lootPreviewY, SlotInteractionGrid.HighlightReveal.GROUP);
    }

    protected boolean handleLootGridClick(int mouseX, int mouseY) {
        LootSlot slot = lootSlotAt(mouseX, mouseY);
        if (slot == null) {
            return false;
        }
        selectedLootEntryIndex = slot.entryIndex();
        ensureLootEntry(slot.entryIndex());
        lootHighlightNonce++;
        showRecipeMaterialSelector(lootEntryField(slot.entryIndex(), "item"), mouseX, mouseY);
        reloadFields();
        return true;
    }

    protected boolean handleLootGridRightClick(int mouseX, int mouseY) {
        LootSlot slot = lootSlotAt(mouseX, mouseY);
        if (slot == null || slot.entryIndex() >= lootEntryCount()) {
            return false;
        }
        deleteLootEntry(slot.entryIndex());
        return true;
    }

    protected boolean changeLootGridAmount(int mouseX, int mouseY, double verticalAmount) {
        LootSlot slot = lootSlotAt(mouseX, mouseY);
        if (slot == null) {
            return false;
        }
        if (slot.entryIndex() >= lootEntryCount()) {
            return scrollLootGrid(verticalAmount);
        }
        String amountField = lootEntryField(slot.entryIndex(), "maxAmount");
        int amount = parseInt(jsonPathText(amountField), 1, 1, 64);
        int next = Math.clamp(amount + (verticalAmount > 0 ? 1 : -1), 1, 64);
        if (next == amount) {
            return false;
        }
        captureResourceSnapshot();
        putJsonText(amountField, String.valueOf(next));
        selectedLootEntryIndex = slot.entryIndex();
        return true;
    }

    protected boolean scrollLootGrid(double verticalAmount) {
        int maxOffset = Math.max(0, lootEntryCount() - lootPreviewColumns * lootPreviewRows);
        if (maxOffset <= 0) {
            return false;
        }
        int next = Math.clamp(lootPreviewEntryOffset + (verticalAmount > 0 ? -lootPreviewColumns : lootPreviewColumns), 0, maxOffset);
        if (next == lootPreviewEntryOffset) {
            return false;
        }
        lootPreviewEntryOffset = next;
        return true;
    }

    protected LootSlot lootSlotAt(int mouseX, int mouseY) {
        for (LootSlot slot : lootVisibleSlots()) {
            if (slot.rect().contains(mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    protected List<LootSlot> lootVisibleSlots() {
        List<LootSlot> slots = new ArrayList<>();
        int slotSize = 18 * lootPreviewScale;
        int inset = Math.max(1, lootPreviewScale);
        int visibleSlots = lootPreviewColumns * lootPreviewRows;
        for (int slot = 0; slot < visibleSlots; slot++) {
            int x = lootPreviewX + (slot % lootPreviewColumns) * slotSize - inset;
            int y = lootPreviewY + (slot / lootPreviewColumns) * slotSize - inset;
            slots.add(new LootSlot(slot, lootPreviewEntryOffset + slot, new SlotInteractionGrid.SlotRect(slot, x, y, slotSize + inset * 2)));
        }
        return slots;
    }

    protected int selectedLootEntryIndex() {
        int count = lootEntryCount();
        if (count <= 0) {
            selectedLootEntryIndex = 0;
            return 0;
        }
        selectedLootEntryIndex = Math.clamp(selectedLootEntryIndex, 0, count - 1);
        return selectedLootEntryIndex;
    }

    protected int lootEntryCount() {
        return firstLootEntries().size();
    }

    protected void ensureLootEntry(int index) {
        if (index < 0) {
            return;
        }
        captureResourceSnapshot();
        JsonArray entries = firstLootEntryArray(true);
        while (entries.size() <= index) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", "minecraft:stone");
            entry.addProperty("minAmount", 1);
            entry.addProperty("maxAmount", 1);
            entry.addProperty("weight", 1);
            entry.addProperty("chance", 100);
            entries.add(entry);
        }
    }

    protected void deleteLootEntry(int index) {
        JsonArray entries = firstLootEntryArray(false);
        if (entries == null || index < 0 || index >= entries.size()) {
            return;
        }
        captureResourceSnapshot();
        entries.remove(index);
        selectedLootEntryIndex = Math.clamp(index, 0, Math.max(0, entries.size() - 1));
        lootPreviewEntryOffset = Math.clamp(lootPreviewEntryOffset, 0, Math.max(0, entries.size() - lootPreviewColumns * lootPreviewRows));
        reloadFields();
    }

    protected List<String> entityTargetOptions() {
        List<String> values = catalogOptions(ENTITY_TYPE_OPTIONS_SOURCE);
        List<String> options = new ArrayList<>();
        options.add("none");
        if (values.stream().anyMatch(this::isRealOption)) {
            options.addAll(values.stream().filter(this::isRealOption).distinct().toList());
        } else {
            options.addAll(FALLBACK_ENTITY_TYPE_OPTIONS);
        }
        return options;
    }

    protected String triggerEvent() {
        return jsonPathText("trigger.event");
    }

    protected void ensureTriggerObject() {
        if (resource.has("trigger") && resource.get("trigger").isJsonObject()) {
            resource.remove("links");
            return;
        }
        JsonObject trigger = new JsonObject();
        if (resource.has("links") && resource.get("links").isJsonArray() && !resource.getAsJsonArray("links").isEmpty() && resource.getAsJsonArray("links").get(0).isJsonObject()) {
            trigger = resource.getAsJsonArray("links").get(0).getAsJsonObject().deepCopy();
        }
        if (!trigger.has("event")) {
            trigger.addProperty("event", "none");
        }
        if (!trigger.has("target")) {
            trigger.addProperty("target", "");
        }
        if (!trigger.has("entity")) {
            trigger.addProperty("entity", "");
        }
        if (!trigger.has("tool")) {
            trigger.addProperty("tool", "");
        }
        if (!trigger.has("overrideDrops")) {
            trigger.addProperty("overrideDrops", true);
        }
        resource.add("trigger", trigger);
        resource.remove("links");
    }

    protected List<JsonObject> firstLootEntries() {
        JsonArray entries = firstLootEntryArray(false);
        if (entries == null) {
            return List.of();
        }
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement entry : entries) {
            if (entry != null && entry.isJsonObject()) {
                result.add(entry.getAsJsonObject());
            }
        }
        return result;
    }

    protected JsonArray firstLootEntryArray(boolean create) {
        JsonArray pools = resource.has("pools") && resource.get("pools").isJsonArray() ? resource.getAsJsonArray("pools") : null;
        if (pools == null) {
            if (!create) {
                return null;
            }
            pools = new JsonArray();
            resource.add("pools", pools);
        }
        while (create && pools.isEmpty()) {
            JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);
            pool.add("entries", new JsonArray());
            pools.add(pool);
        }
        if (pools.isEmpty() || !pools.get(0).isJsonObject()) {
            return null;
        }
        JsonObject pool = pools.get(0).getAsJsonObject();
        if (!pool.has("entries") || !pool.get("entries").isJsonArray()) {
            if (!create) {
                return null;
            }
            pool.add("entries", new JsonArray());
        }
        return pool.getAsJsonArray("entries");
    }

    protected String lootEntryField(int index, String field) {
        return "pools.0.entries." + index + "." + field;
    }

    protected List<String> lootTableFields() {
        String event = triggerEvent();
        List<String> fields = new ArrayList<>(List.of("displayName", "enabled", "trigger.event", "trigger.target"));
        if (List.of("block_break", "block_place", "entity_death").contains(event)) {
            fields.add("trigger.tool");
        }
        if ("item_hit_entity".equalsIgnoreCase(event)) {
            fields.add("trigger.entity");
        }
        if (List.of("block_break", "entity_death").contains(event)) {
            fields.add("trigger.overrideDrops");
        }
        fields.add("pools.0.rolls");
        if (lootEntryCount() > 0) {
            int index = selectedLootEntryIndex();
            fields.addAll(List.of(
                lootEntryField(index, "item"),
                lootEntryField(index, "minAmount"),
                lootEntryField(index, "maxAmount"),
                lootEntryField(index, "weight"),
                lootEntryField(index, "chance"),
                lootEntryField(index, "conditions"),
                lootEntryField(index, "components")
            ));
        }
        fields.addAll(List.of("hooks.beforeRollFlow", "hooks.afterRollFlow", "hooks.deniedRollFlow"));
        return fields;
    }
}
