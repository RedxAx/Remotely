package redxax.oxy.remotely.flow.ui.studio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.FlowSerializer;
import restudio.rescreen.util.JsonTreeParser;

import java.util.Map;

public final class ReSyncCollaborationDocuments {
    private ReSyncCollaborationDocuments() {
    }

    public static JsonObject from(GuiDefinition value) {
        return encode(value == null ? null : FlowSerializer.serializeGui(value));
    }

    public static JsonObject from(ScoreboardDefinition value) {
        return encode(value == null ? null : FlowSerializer.serializeScoreboard(value));
    }

    public static JsonObject from(TabDefinition value) {
        return encode(value == null ? null : FlowSerializer.serializeTab(value));
    }

    public static JsonObject from(Object value) {
        return switch (value) {
            case GuiDefinition gui -> from(gui);
            case ScoreboardDefinition scoreboard -> from(scoreboard);
            case TabDefinition tab -> from(tab);
            case null -> null;
            default -> throw new IllegalArgumentException("Unsupported Collaboration Document");
        };
    }

    public static GuiDefinition toGui(JsonObject document) {
        return document == null ? null : FlowSerializer.deserializeGui(JsonTreeParser.write(document));
    }

    public static ScoreboardDefinition toScoreboard(JsonObject document) {
        return document == null ? null : FlowSerializer.deserializeScoreboard(JsonTreeParser.write(document));
    }

    public static TabDefinition toTab(JsonObject document) {
        return document == null ? null : FlowSerializer.deserializeTab(JsonTreeParser.write(document));
    }

    private static JsonObject encode(String json) {
        return json == null ? null : JsonTreeParser.parse(json).getAsJsonObject();
    }

    public static void copy(JsonObject target, JsonObject source) {
        if (target == null || source == null) {
            return;
        }
        target.keySet().clear();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            target.add(entry.getKey(), entry.getValue().deepCopy());
        }
    }

    public static void copy(GuiDefinition target, GuiDefinition source) {
        if (target == null || source == null) {
            return;
        }
        target.setId(source.getId());
        target.setEnabled(source.isEnabled());
        target.setTitle(source.getTitle());
        target.setRows(source.getRows());
        target.setExtendToPlayerInventory(source.isExtendToPlayerInventory());
        target.setElements(source.getElements());
    }

    public static void copy(ScoreboardDefinition target, ScoreboardDefinition source) {
        if (target == null || source == null) {
            return;
        }
        target.setId(source.getId());
        target.setEnabled(source.isEnabled());
        target.setTitle(source.getTitle());
        target.setObjectiveId(source.getObjectiveId());
        target.setDisplaySlot(source.getDisplaySlot());
        target.setLines(source.getLines());
    }

    public static void copy(TabDefinition target, TabDefinition source) {
        if (target == null || source == null) {
            return;
        }
        target.setId(source.getId());
        target.setEnabled(source.isEnabled());
        target.setHeader(source.getHeader());
        target.setEntryFormat(source.getEntryFormat());
        target.setFooter(source.getFooter());
    }
}
