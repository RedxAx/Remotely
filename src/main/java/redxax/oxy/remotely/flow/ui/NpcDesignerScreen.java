package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import redxax.restudio.Remodel.util.SkinFetcher;
import org.lwjgl.glfw.GLFW;
import restudio.rescreen.game.MinecraftGameEntities;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.lwjgl.MinecraftRenderItem;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.DoubleSliderWidget;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class NpcDesignerScreen extends FocusedJsonResourceDesignerScreen {
    private static final String ENTITY_TYPE_OPTIONS_SOURCE = "server:minecraft:entity_type";
    private static final long SKIN_RETRY_DELAY_MS = 60000L;
    private static final Map<String, BufferedImage> SKIN_PREVIEW_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> SKIN_PREVIEW_FAILURES = new ConcurrentHashMap<>();
    private static final Set<String> SKIN_PREVIEW_FETCHING = ConcurrentHashMap.newKeySet();
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

    protected int npcPreviewX;
    protected int npcPreviewY;

    protected record NpcEquipmentSlot(String field, int x, int y) {
    }

    public NpcDesignerScreen(StudioScreen owner, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, ReSyncResourceDragPayload.NPC_DEFINITION, resourceId, resource, serverId, parent);
        ensureEntityTypeCatalogLoaded();
    }

    @Override
    protected void renderResourcePreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int mouseX, int mouseY, int text, int muted) {
        renderNpcRealPreview(context, previewX, previewY, previewWidth, previewHeight, mouseX, mouseY, text, muted);
    }

    @Override
    protected List<String> editorFields() {
        return npcFields();
    }

    @Override
    protected List<ResourcePanelSection> editorSections(List<String> fields) {
        return appendRemainingSections(List.of(
            new ResourcePanelSection("NPC", fields.stream().filter(field -> List.of("displayName", "entityType", "ai", "gravity", "invulnerable", "followPlayer", "followRange").contains(field)).toList()),
            new ResourcePanelSection("Skin", fields.stream().filter(field -> field.startsWith("skin.")).toList()),
            new ResourcePanelSection("Trade", fields.stream().filter(field -> List.of("tradeProfile", "lootTable").contains(field)).toList()),
            new ResourcePanelSection("Equipment", fields.stream().filter(field -> field.startsWith("equipment.")).toList()),
            new ResourcePanelSection("Hooks", fields.stream().filter(field -> field.startsWith("hooks.")).toList())
        ), fields);
    }

    @Override
    protected AnimatedWidget customFieldRow(String field, String label, int rowWidth) {
        if ("followRange".equals(field)) {
            return npcFollowRangeSliderRow(label, rowWidth);
        }
        if ("entityType".equals(field)) {
            return entityTypeFieldRow(field, label, rowWidth);
        }
        return null;
    }

    @Override
    protected boolean remountPanelOnFieldReload() {
        return true;
    }

    @Override
    protected List<String> customSelectorOptions(String field) {
        return switch (field) {
            case "entityType" -> entityTypeOptions();
            case "spawnMode" -> List.of("manual");
            case "location.world" -> normalizedSelectorOptions(catalogOptions("server:minecraft:world"), jsonPathText(field));
            default -> null;
        };
    }

    @Override
    protected boolean customRebuildOnSelection(String field) {
        return "entityType".equals(field);
    }

    @Override
    protected boolean customDropdownField(String field) {
        return "location.world".equals(field) || "spawnMode".equals(field);
    }

    @Override
    protected boolean customRecipeItemSelectorField(String field) {
        return field.startsWith("equipment.");
    }

    @Override
    protected boolean handleResourceMouseClicked(int mouseX, int mouseY, int button) {
        return (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) && handleNpcPreviewClick(mouseX, mouseY, button);
    }

    @Override
    protected String defaultFunctionInputContext() {
        return "npc";
    }

    @Override
    protected String resourceSummary() {
        return firstFilled(jsonPathText("entityType"), "NPC");
    }

    @Override
    protected String resourceDisplayName() {
        return "NPC";
    }

    protected AnimatedWidget entityTypeFieldRow(String field, String label, int rowWidth) {
        ensureEntityTypeCatalogLoaded();
        String selected = jsonPathText(field);
        AnimatedButton button = new AnimatedButton.Builder()
            .label(selectorLabel(field, selected))
            .size(174, 18)
            .entranceAnimation(false)
            .build();
        registerResourceSelectorButton(field, button);
        button.setAction(() -> openEntityTypeSelector(field, button.getX(), button.getY() + button.getHeight()));
        return studioPanelState.row(label, button, rowWidth, jsonResourceDescription(field, label));
    }

    protected AnimatedWidget npcFollowRangeSliderRow(String label, int rowWidth) {
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

    protected List<String> entityTypeOptions() {
        ensureEntityTypeCatalogLoaded();
        List<String> values = OptionCatalogCache.getInstance().getValues(serverId, ENTITY_TYPE_OPTIONS_SOURCE);
        if (!values.isEmpty()) {
            LinkedHashSet<String> options = new LinkedHashSet<>(values);
            options.add("player");
            return new ArrayList<>(options);
        }
        return FALLBACK_ENTITY_TYPE_OPTIONS;
    }

    protected void ensureEntityTypeCatalogLoaded() {
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null && !OptionCatalogCache.getInstance().hasCatalog(serverId, ENTITY_TYPE_OPTIONS_SOURCE)) {
            manager.ensureFlowClient(serverId).requestOptionCatalog(ENTITY_TYPE_OPTIONS_SOURCE);
        }
    }

    protected Map<String, OptionCatalogItem> entityTypeCatalogByValue() {
        Map<String, OptionCatalogItem> byValue = new LinkedHashMap<>();
        for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, ENTITY_TYPE_OPTIONS_SOURCE)) {
            if (item != null && item.getValue() != null && !item.getValue().isBlank()) {
                byValue.put(item.getValue(), item);
            }
        }
        return byValue;
    }

    protected void openEntityTypeSelector(String field, int mouseX, int mouseY) {
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

    protected void applyEntityTypeSelection(String field, String value) {
        if (!isRealOption(value)) {
            return;
        }
        putJsonText(field, value);
        reloadFields();
    }

    protected void renderNpcRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int mouseX, int mouseY, int text, int muted) {
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
        if (npcPlayerEntityType()) {
            context.drawText("Skin " + compactState(jsonPathText("skin.username")), cardX + 74, cardY + 102, muted, false);
        }
        context.drawText("Trade " + compactState(resourceLinkText("links.tradeProfile", "tradeProfile")), cardX + 148, cardY + 58, muted, false);
        context.drawText("Loot " + compactState(resourceLinkText("links.lootTable", "lootTable")), cardX + 148, cardY + 74, muted, false);
    }

    protected void drawNpcEntityPreview(IDrawContext context, int x, int y, int size, int mouseX, int mouseY) {
        String entityType = normalizedNpcEntityType();
        String displayName = firstFilled(jsonPathText("displayName"), id);
        boolean baby = npcBaby();
        Map<String, Object> tag = npcEntityPreviewTag();
        float relativeMouseX = mouseX - (x + size / 2.0f);
        float relativeMouseY = mouseY - (y + size / 2.0f);
        if ("minecraft:player".equals(entityType)) {
            context.drawPlayerRelativeMousePreview(MinecraftGameEntities.of("minecraft:player", displayName, null, npcSkinPreview(), false, baby, tag), x, y, 20, size, relativeMouseX, relativeMouseY, false);
        } else {
            context.drawEntityRelativeMousePreview(MinecraftGameEntities.of(entityType, displayName, null, null, false, baby, tag), x, y, 20, size, relativeMouseX, relativeMouseY, false);
        }
    }

    protected boolean npcBaby() {
        String value = firstFilled(jsonPathText("baby"), jsonPathText("isBaby"), jsonPathText("IsBaby"));
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }

    protected BufferedImage npcSkinPreview() {
        String username = jsonPathText("skin.username").trim();
        if (username.isBlank()) {
            return null;
        }
        String key = username.toLowerCase(Locale.ROOT);
        BufferedImage cached = SKIN_PREVIEW_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        long lastFailure = SKIN_PREVIEW_FAILURES.getOrDefault(key, 0L);
        if (lastFailure > 0L && System.currentTimeMillis() - lastFailure < SKIN_RETRY_DELAY_MS) {
            return null;
        }
        if (SKIN_PREVIEW_FETCHING.add(key)) {
            String fetchUsername = username;
            CompletableFuture.supplyAsync(() -> SkinFetcher.getSkin(fetchUsername))
                .thenAccept(skin -> {
                    if (skin != null) {
                        SKIN_PREVIEW_CACHE.put(key, skin);
                        SKIN_PREVIEW_FAILURES.remove(key);
                    } else {
                        SKIN_PREVIEW_FAILURES.put(key, System.currentTimeMillis());
                    }
                    SKIN_PREVIEW_FETCHING.remove(key);
                })
                .exceptionally(error -> {
                    SKIN_PREVIEW_FAILURES.put(key, System.currentTimeMillis());
                    SKIN_PREVIEW_FETCHING.remove(key);
                    return null;
                });
        }
        return null;
    }

    protected Map<String, Object> npcEntityPreviewTag() {
        LinkedHashMap<String, Object> tag = new LinkedHashMap<>();
        LinkedHashMap<String, Object> equipment = new LinkedHashMap<>();
        putNpcEquipment(equipment, "mainHand", "equipment.mainHand");
        putNpcEquipment(equipment, "offHand", "equipment.offHand");
        putNpcEquipment(equipment, "helmet", "equipment.helmet");
        putNpcEquipment(equipment, "chestplate", "equipment.chestplate");
        putNpcEquipment(equipment, "leggings", "equipment.leggings");
        putNpcEquipment(equipment, "boots", "equipment.boots");
        if (!equipment.isEmpty()) {
            tag.put("equipment", Map.copyOf(equipment));
        }
        String skinUsername = jsonPathText("skin.username").trim();
        if (!skinUsername.isBlank()) {
            tag.put("skin", Map.of("username", skinUsername));
            tag.put("skinUsername", skinUsername);
        }
        return tag.isEmpty() ? Map.of() : Map.copyOf(tag);
    }

    protected void putNpcEquipment(Map<String, Object> equipment, String key, String field) {
        String item = jsonPathText(field).trim();
        if (!item.isBlank()) {
            MinecraftRenderItem preview = ItemIconPreview.resolve(serverId, item).toRenderItem(recipeItemSelectorLabel(item));
            equipment.put(key, preview != null ? preview : item);
        }
    }

    protected String normalizedNpcEntityType() {
        String entityType = jsonPathText("entityType").trim();
        if (entityType.isBlank()) {
            entityType = "villager";
        }
        entityType = entityType.toLowerCase(Locale.ROOT).replace(' ', '_');
        return entityType.contains(":") ? entityType : "minecraft:" + entityType;
    }

    protected boolean npcPlayerEntityType() {
        return "minecraft:player".equals(normalizedNpcEntityType());
    }

    protected boolean handleNpcPreviewClick(int mouseX, int mouseY, int button) {
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

    protected String npcPreviewEquipmentFieldAt(int mouseX, int mouseY) {
        for (NpcEquipmentSlot slot : npcEquipmentSlots()) {
            if (inside(mouseX, mouseY, npcPreviewX + slot.x(), npcPreviewY + slot.y(), 16, 16)) {
                return slot.field();
            }
        }
        return "";
    }

    protected List<NpcEquipmentSlot> npcEquipmentSlots() {
        return List.of(
            new NpcEquipmentSlot("equipment.mainHand", 74, 54),
            new NpcEquipmentSlot("equipment.offHand", 98, 54),
            new NpcEquipmentSlot("equipment.helmet", 74, 78),
            new NpcEquipmentSlot("equipment.chestplate", 98, 78),
            new NpcEquipmentSlot("equipment.leggings", 122, 78),
            new NpcEquipmentSlot("equipment.boots", 122, 54)
        );
    }

    protected List<String> npcFields() {
        List<String> fields = new ArrayList<>(List.of(
            "displayName", "entityType", "ai", "gravity", "invulnerable", "followPlayer", "followRange", "tradeProfile", "lootTable",
            "hooks.spawnAction", "hooks.rightClickAction", "hooks.leftClickAction", "hooks.despawnAction"
        ));
        if (npcPlayerEntityType()) {
            fields.add(2, "skin.username");
        }
        fields.addAll(9, List.of("equipment.mainHand", "equipment.offHand", "equipment.helmet", "equipment.chestplate", "equipment.leggings", "equipment.boots"));
        return fields;
    }
}
