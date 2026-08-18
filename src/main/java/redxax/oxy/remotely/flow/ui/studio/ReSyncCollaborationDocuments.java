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

    public static JsonObject from(Object value) {
        String json = switch (value) {
            case GuiDefinition gui -> FlowSerializer.serializeGui(gui);
            case ScoreboardDefinition scoreboard -> FlowSerializer.serializeScoreboard(scoreboard);
            case TabDefinition tab -> FlowSerializer.serializeTab(tab);
            case null -> null;
            default -> throw new IllegalArgumentException("Unsupported Collaboration Document " + value.getClass().getName());
        };
        return json == null ? null : JsonTreeParser.parse(json).getAsJsonObject();
    }

    public static <T> T to(JsonObject document, Class<T> type) {
        if (document == null || type == null) return null;
        Object value;
        if (type == GuiDefinition.class) value = FlowSerializer.deserializeGui(JsonTreeParser.write(document));
        else if (type == ScoreboardDefinition.class) value = FlowSerializer.deserializeScoreboard(JsonTreeParser.write(document));
        else if (type == TabDefinition.class) value = FlowSerializer.deserializeTab(JsonTreeParser.write(document));
        else throw new IllegalArgumentException("Unsupported Collaboration Document " + type.getName());
        return type.cast(value);
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
