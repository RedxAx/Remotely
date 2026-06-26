package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import org.lwjgl.glfw.GLFW;
import restudio.rescreen.game.MinecraftAssetReference;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.DoubleSliderWidget;
import restudio.rescreen.util.ResourceManager;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.flow.ui.GuiEditOverlayState.snapshot;

public class VillageDesignerScreen extends FocusedJsonResourceDesignerScreen {
    public VillageDesignerScreen(StudioScreen owner, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, ReSyncResourceDragPayload.VILLAGE_PROFILE, resourceId, resource, serverId, parent);
    }

    @Override
    protected void onResourceSnapshotRestored() {
        selectedVillageOfferIndex = 0;
    }

    @Override
    protected void renderResourcePreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int mouseX, int mouseY, int text, int muted) {
        renderVillageRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
    }

    @Override
    protected List<String> editorFields() {
        return villageFields();
    }

    @Override
    protected List<ResourcePanelSection> editorSections(List<String> fields) {
        return appendRemainingSections(List.of(
            new ResourcePanelSection("Profile", fields.stream().filter(field -> List.of("displayName", "profession", "villagerType", "level").contains(field)).toList()),
            new ResourcePanelSection("Trade", fields.stream().filter(field -> field.startsWith("offers.") || List.of("maxUses", "restockTicks", "lootTable").contains(field)).toList()),
            new ResourcePanelSection("Hooks", fields.stream().filter(field -> field.startsWith("hooks.")).toList())
        ), fields);
    }

    @Override
    protected AnimatedWidget customFieldRow(String field, String label, int rowWidth) {
        return "level".equals(field) ? villageLevelSliderRow(label, rowWidth) : null;
    }

    @Override
    protected List<String> customSelectorOptions(String field) {
        return switch (field) {
            case "profession" -> villagerProfessionOptions();
            case "villagerType" -> villagerTypeOptions();
            default -> null;
        };
    }

    @Override
    protected boolean customDropdownField(String field) {
        return "villagerType".equals(field);
    }

    @Override
    protected boolean customRecipeItemSelectorField(String field) {
        return field.matches("offers\\.\\d+\\.(cost|cost2|result)");
    }

    protected List<String> villagerProfessionOptions() {
        return List.of("none", "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher", "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");
    }

    protected List<String> villagerTypeOptions() {
        return List.of("plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga");
    }

    @Override
    protected boolean handleResourceMouseClicked(int mouseX, int mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && handleVillagePreviewClick(mouseX, mouseY)) {
            return true;
        }
        return button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && handleVillagePreviewRightClick(mouseX, mouseY);
    }

    @Override
    protected boolean handleResourceMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        return changeVillagePreviewItemAmount(mouseX, mouseY, verticalAmount);
    }

    @Override
    protected CompactBindingSupport.FunctionShape runtimeFunctionShape(String functionBase) {
        return CompactBindingSupport.villageActionShape();
    }

    @Override
    protected String defaultFunctionInputContext() {
        return "village";
    }

    @Override
    protected String resourceSummary() {
        return firstFilled(jsonPathText("profession"), "Village");
    }

    @Override
    protected String resourceDisplayName() {
        return "Village";
    }

    protected int selectedVillageOfferIndex;
    protected int villagePreviewX;
    protected int villagePreviewY;
    protected int villagePreviewScale = 1;
    protected int villagePreviewOfferOffset;
    protected int villagePreviewVisibleOffers;
    protected int villagePreviewOfferScroll;
    protected int villagePreviewAddX;
    protected int villagePreviewAddY;
    protected int villagePreviewAddWidth;
    protected int villagePreviewAddHeight;
    protected int villagePreviewDeleteX;
    protected int villagePreviewDeleteY;
    protected int villagePreviewDeleteWidth;
    protected int villagePreviewDeleteHeight;

    protected AnimatedWidget villageLevelSliderRow(String label, int rowWidth) {
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

    protected void renderVillageRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
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

    protected void drawMerchantPreviewTrades(IDrawContext context, MinecraftGameAssets gameAssets, int viewX, int viewY, int scale, int text, int muted) {
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

    protected void drawMerchantPreviewSummary(IDrawContext context, int viewX, int viewY, int scale, int text, int muted) {
        String profession = formatOptionLabel(firstFilled(jsonPathText("profession"), "none"));
        String title = profession.equals("none") ? resourceDisplayName() : profession + " - " + villageLevelName(parseInt(jsonPathText("level"), 1, 1, 5));
        int titleX = viewX + (49 + 138) * scale - textWidth(title) / 2;
        int tradesX = viewX + (5 + 48) * scale - textWidth("Trades") / 2;
        context.drawText(title, titleX, viewY + 6 * scale, 0xFF404040, false);
        context.drawText("Trades", tradesX, viewY + 6 * scale, 0xFF404040, false);
    }

    protected String villageLevelName(int level) {
        return switch (Math.clamp(level, 1, 5)) {
            case 2 -> "Apprentice";
            case 3 -> "Journeyman";
            case 4 -> "Expert";
            case 5 -> "Master";
            default -> "Novice";
        };
    }

    protected List<JsonObject> villageOffers() {
        JsonArray offers = resource.has("offers") && resource.get("offers").isJsonArray() ? resource.getAsJsonArray("offers") : new JsonArray();
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement element : offers) {
            if (element != null && element.isJsonObject()) {
                result.add(element.getAsJsonObject());
            }
        }
        return result;
    }

    protected List<JsonObject> editableVillageOffers() {
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

    protected int villageOfferCount() {
        return editableVillageOffers().size();
    }

    protected int selectedVillageOfferIndex() {
        int count = villageOfferCount();
        if (count <= 0) {
            selectedVillageOfferIndex = 0;
            return 0;
        }
        selectedVillageOfferIndex = Math.clamp(selectedVillageOfferIndex, 0, count - 1);
        return selectedVillageOfferIndex;
    }

    protected JsonArray villageOfferArray(boolean create) {
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

    protected void addVillageOffer() {
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

    protected void deleteSelectedVillageOffer() {
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

    protected String villageOfferSummary(JsonObject offer, int index) {
        String cost = recipeItemSelectorLabel(firstFilled(jsonText(offer, "cost"), "minecraft:emerald"));
        String result = recipeItemSelectorLabel(firstFilled(jsonText(offer, "result"), "minecraft:book"));
        return "Trade " + (index + 1) + "  " + cost + " > " + result;
    }

    protected boolean handleVillagePreviewClick(int mouseX, int mouseY) {
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

    protected boolean handleVillagePreviewRightClick(int mouseX, int mouseY) {
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

    protected boolean changeVillagePreviewItemAmount(int mouseX, int mouseY, double verticalAmount) {
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

    protected boolean changeVillagePreviewTradeScroll(int mouseX, int mouseY, double verticalAmount) {
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

    protected String villagePreviewItemFieldAt(int mouseX, int mouseY) {
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

    protected int villageOfferIndex(String field) {
        if (field == null || !field.startsWith("offers.")) {
            return selectedVillageOfferIndex();
        }
        String[] parts = field.split("\\.");
        return parts.length > 1 && isIndex(parts[1]) ? Integer.parseInt(parts[1]) : selectedVillageOfferIndex();
    }

    protected String villagePreviewAmountField(String field) {
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

    protected List<String> villageFields() {
        List<String> fields = new ArrayList<>(List.of("displayName", "profession", "villagerType", "level", "maxUses", "restockTicks", "lootTable"));
        if (villageOfferCount() > 0) {
            int index = selectedVillageOfferIndex();
            fields.add("offers." + index + ".weight");
        }
        fields.addAll(List.of("hooks.openAction", "hooks.completeAction", "hooks.deniedAction"));
        return fields;
    }
}
