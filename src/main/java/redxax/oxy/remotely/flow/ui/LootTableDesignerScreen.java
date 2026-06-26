package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import restudio.rescreen.platform.IDrawContext;

import java.util.ArrayList;
import java.util.List;

public class LootTableDesignerScreen extends FocusedJsonResourceDesignerScreen {
    public LootTableDesignerScreen(StudioScreen owner, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, ReSyncResourceDragPayload.LOOT_TABLE, resourceId, resource, serverId, parent);
    }

    @Override
    protected void renderResourcePreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int mouseX, int mouseY, int text, int muted) {
        renderLootRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
    }

    @Override
    protected List<String> editorFields() {
        return lootTableFields();
    }

    @Override
    protected List<ResourcePanelSection> editorSections(List<String> fields) {
        return appendRemainingSections(List.of(
            new ResourcePanelSection("Pools", fields.stream().filter(field -> field.startsWith("pools.")).toList()),
            new ResourcePanelSection("Hooks", fields.stream().filter(field -> field.startsWith("hooks.")).toList())
        ), fields);
    }

    @Override
    protected boolean customRecipeItemSelectorField(String field) {
        return field.matches("pools\\.\\d+\\.entries\\.\\d+\\.item");
    }

    @Override
    protected String resourceSummary() {
        return firstFilled(jsonText("displayName"), "Loot Table");
    }

    @Override
    protected String resourceDisplayName() {
        return "Loot Table";
    }

    protected void renderLootRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
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

    protected List<JsonObject> firstLootEntries() {
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

    protected List<String> lootTableFields() {
        return new ArrayList<>(List.of(
            "pools.0.rolls", "pools.0.entries.0.item", "pools.0.entries.0.minAmount", "pools.0.entries.0.maxAmount", "pools.0.entries.0.weight", "pools.0.entries.0.chance",
            "pools.0.entries.0.conditions", "pools.0.entries.0.components", "hooks.beforeRollFlow", "hooks.afterRollFlow", "hooks.deniedRollFlow"
        ));
    }
}
