package redxax.oxy.remotely.data.flow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowSerializer;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;

import java.util.Locale;
import java.util.function.BiConsumer;

public enum ReSyncResourceType {

    FLOW(
            0x01, 0x09, 0x02, 0x0A, 0x03, 0x08, 0x07,
            "Flow", item -> FlowSerializer.serialize((FlowGraph) item), FlowSerializer::deserializeFlow,
            (item, newId) -> ((FlowGraph) item).setId(newId),
            item -> ((FlowGraph) item).getId(),
            item -> ((FlowGraph) item).getId()
    ),

    GUI(
            0x11, 0x14, 0x12, 0x15, 0x13, 0x16, 0x17,
            "GUI", item -> FlowSerializer.serializeGui((GuiDefinition) item), FlowSerializer::deserializeGui,
            (item, newId) -> {
                GuiDefinition gui = (GuiDefinition) item;
                String oldId = gui.getId();
                gui.setId(newId);
                if (gui.getTitle() == null || gui.getTitle().isBlank() || gui.getTitle().equals(oldId)) {
                    gui.setTitle(newId);
                }
            },
            item -> ((GuiDefinition) item).getId(),
            item -> {
                GuiDefinition gui = (GuiDefinition) item;
                return gui.getTitle() != null ? gui.getTitle() : gui.getId();
            }
    ),

    SCOREBOARD(
            0x18, 0x1A, 0x1C, 0x1D, 0x19, 0x1B, 0x1E,
            "Scoreboard", item -> FlowSerializer.serializeScoreboard((ScoreboardDefinition) item), FlowSerializer::deserializeScoreboard,
            (item, newId) -> {
                ScoreboardDefinition sb = (ScoreboardDefinition) item;
                String oldId = sb.getId();
                sb.setId(newId);
                if (sb.getObjectiveId() == null || sb.getObjectiveId().isBlank() || sb.getObjectiveId().equals(oldId)) {
                    sb.setObjectiveId(newId);
                }
                if (sb.getTitle() == null || sb.getTitle().isBlank() || sb.getTitle().equals(oldId)) {
                    sb.setTitle(newId);
                }
            },
            item -> ((ScoreboardDefinition) item).getId(),
            item -> {
                ScoreboardDefinition sb = (ScoreboardDefinition) item;
                return sb.getTitle() != null ? sb.getTitle() : sb.getId();
            }
    ),

    TAB(
            0x20, 0x22, 0x24, 0x25, 0x21, 0x23, 0x26,
            "Tab", item -> FlowSerializer.serializeTab((TabDefinition) item), FlowSerializer::deserializeTab,
            (item, newId) -> ((TabDefinition) item).setId(newId),
            item -> ((TabDefinition) item).getId(),
            item -> ((TabDefinition) item).getId()
    ),

    CUSTOM_CONTENT(
            0x30, 0x36, 0x32, 0x31, 0x33, 0x34, 0x35,
            "Custom Content", item -> FlowSerializer.serializeCustomContent((CustomContentDefinition) item), FlowSerializer::deserializeCustomContent,
            (item, newId) -> ((CustomContentDefinition) item).setId(newId),
            item -> ((CustomContentDefinition) item).getId(),
            item -> {
                CustomContentDefinition content = (CustomContentDefinition) item;
                return content.getDisplayName() != null ? content.getDisplayName() : content.getId();
            }
    ),

    PROJECT_METADATA(
            0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56,
            "Project Metadata", item -> new Gson().toJson(item), json -> new Gson().fromJson(json, ReSyncProjectMetadata.class),
            (item, newId) -> ((ReSyncProjectMetadata) item).setServerId(newId),
            item -> ((ReSyncProjectMetadata) item).getServerId() == null || ((ReSyncProjectMetadata) item).getServerId().isBlank() ? "project" : ((ReSyncProjectMetadata) item).getServerId(),
            item -> "Project"
    ),

    CHAT_CHANNEL(
            0x67, 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D,
            "Chat Channel", ReSyncResourceType::serializeJsonObject, ReSyncResourceType::deserializeJsonObject,
            ReSyncResourceType::renameJsonObject, ReSyncResourceType::jsonObjectId, ReSyncResourceType::jsonObjectName
    ),

    CHAT_FORMAT(
            0x6E, 0x6F, 0x70, 0x71, 0x72, 0x73, 0x74,
            "Chat Format", ReSyncResourceType::serializeJsonObject, ReSyncResourceType::deserializeJsonObject,
            ReSyncResourceType::renameJsonObject, ReSyncResourceType::jsonObjectId, ReSyncResourceType::jsonObjectName
    ),

    CHAT_RULE(
            0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x7B,
            "Chat Rule", ReSyncResourceType::serializeJsonObject, ReSyncResourceType::deserializeJsonObject,
            ReSyncResourceType::renameJsonObject, ReSyncResourceType::jsonObjectId, ReSyncResourceType::jsonObjectName
    ),

    PRIVATE_MESSAGE_FORMAT(
            0x7C, 0x7D, 0x7E, 0x7F, 0x80, 0x81, 0x82,
            "Private Message Format", ReSyncResourceType::serializeJsonObject, ReSyncResourceType::deserializeJsonObject,
            ReSyncResourceType::renameJsonObject, ReSyncResourceType::jsonObjectId, ReSyncResourceType::jsonObjectName
    ),

    MENTION_STYLE(
            0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
            "Mention Style", ReSyncResourceType::serializeJsonObject, ReSyncResourceType::deserializeJsonObject,
            ReSyncResourceType::renameJsonObject, ReSyncResourceType::jsonObjectId, ReSyncResourceType::jsonObjectName
    ),

    IGNORE_LIST(
            0x8A, 0x8B, 0x8C, 0x8D, 0x8E, 0x8F, 0x90,
            "Ignore List", ReSyncResourceType::serializeJsonObject, ReSyncResourceType::deserializeJsonObject,
            ReSyncResourceType::renameJsonObject, ReSyncResourceType::jsonObjectId, ReSyncResourceType::jsonObjectName
    ),

    MOTD_PROFILE(
            0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97,
            "MOTD Profile", ReSyncResourceType::serializeJsonObject, ReSyncResourceType::deserializeJsonObject,
            ReSyncResourceType::renameJsonObject, ReSyncResourceType::jsonObjectId, ReSyncResourceType::jsonObjectName
    ),

    MESSAGE_RULE(
            0x98, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E,
            "Message Rule", ReSyncResourceType::serializeJsonObject, ReSyncResourceType::deserializeJsonObject,
            ReSyncResourceType::renameJsonObject, ReSyncResourceType::jsonObjectId, ReSyncResourceType::jsonObjectName
    ),

    RECIPE_DEFINITION(
            0x9F, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5,
            "Recipe Definition", ReSyncResourceType::serializeJsonObject, ReSyncResourceType::deserializeJsonObject,
            ReSyncResourceType::renameJsonObject, ReSyncResourceType::jsonObjectId, ReSyncResourceType::jsonObjectName
    ),

    TEXT_TEMPLATE(
            0xA6, 0xA7, 0xA8, 0xA9, 0xAA, 0xAB, 0xAC,
            "Text Template", ReSyncResourceType::serializeJsonObject, ReSyncResourceType::deserializeJsonObject,
            ReSyncResourceType::renameJsonObject, ReSyncResourceType::jsonObjectId, ReSyncResourceType::jsonObjectName
    ),

    ADVANCEMENT_TREE(
            0xAD, 0xAE, 0xAF, 0xB0, 0xB1, 0xB2, 0xB3,
            "Advancement Tree", ReSyncResourceType::serializeJsonObject, ReSyncResourceType::deserializeJsonObject,
            ReSyncResourceType::renameJsonObject, ReSyncResourceType::jsonObjectId, ReSyncResourceType::jsonObjectName
    );

    @FunctionalInterface
    public interface Serializer {
        String serialize(Object item);
    }

    @FunctionalInterface
    public interface Deserializer {
        Object deserialize(String json);
    }

    @FunctionalInterface
    public interface IdExtractor {
        String extract(Object item);
    }

    private final byte requestByte;
    private final byte listRequestByte;
    private final byte dataResponseByte;
    private final byte listResponseByte;
    private final byte saveByte;
    private final byte deleteByte;
    private final byte saveAckByte;
    private final String typeId;
    private final String displayName;
    private final String defaultFolder;
    private final boolean enabled;
    private final Serializer serializer;
    private final Deserializer deserializer;
    private final BiConsumer<Object, String> renameApplier;
    private final IdExtractor idExtractor;
    private final IdExtractor nameExtractor;

    ReSyncResourceType(int requestByte, int listRequestByte, int dataResponseByte, int listResponseByte,
                       int saveByte, int deleteByte, int saveAckByte,
                       String displayName,
                       Serializer serializer, Deserializer deserializer,
                       BiConsumer<Object, String> renameApplier,
                       IdExtractor idExtractor, IdExtractor nameExtractor) {
        this.requestByte = (byte) requestByte;
        this.listRequestByte = (byte) listRequestByte;
        this.dataResponseByte = (byte) dataResponseByte;
        this.listResponseByte = (byte) listResponseByte;
        this.saveByte = (byte) saveByte;
        this.deleteByte = (byte) deleteByte;
        this.saveAckByte = (byte) saveAckByte;
        this.typeId = name().toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.defaultFolder = defaultFolderFor(typeId);
        this.enabled = true;
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.renameApplier = renameApplier;
        this.idExtractor = idExtractor;
        this.nameExtractor = nameExtractor;
    }

    public byte requestByte() { return requestByte; }
    public byte listRequestByte() { return listRequestByte; }
    public byte dataResponseByte() { return dataResponseByte; }
    public byte listResponseByte() { return listResponseByte; }
    public byte saveByte() { return saveByte; }
    public byte deleteByte() { return deleteByte; }
    public byte saveAckByte() { return saveAckByte; }
    public String typeId() { return typeId; }
    public String displayName() { return displayName; }
    public String defaultFolder() { return defaultFolder; }
    public boolean enabled() { return enabled; }

    public String serialize(Object item) { return serializer.serialize(item); }
    public Object deserialize(String json) { return deserializer.deserialize(json); }
    public void applyRename(Object item, String newId) { renameApplier.accept(item, newId); }
    public String extractId(Object item) { return idExtractor.extract(item); }
    public String extractName(Object item) { return nameExtractor.extract(item); }

    public static ReSyncResourceType byDataResponse(byte packetId) {
        for (ReSyncResourceType rt : values()) {
            if (rt.enabled && rt.dataResponseByte == packetId) return rt;
        }
        return null;
    }

    public static ReSyncResourceType byListResponse(byte packetId) {
        for (ReSyncResourceType rt : values()) {
            if (rt.enabled && rt.listResponseByte == packetId) return rt;
        }
        return null;
    }

    public static ReSyncResourceType bySaveAck(byte packetId) {
        for (ReSyncResourceType rt : values()) {
            if (rt.enabled && rt.saveAckByte == packetId) return rt;
        }
        return null;
    }

    public static ReSyncResourceType byTypeId(String typeId) {
        for (ReSyncResourceType rt : values()) {
            if (rt.typeId.equals(typeId)) {
                return rt;
            }
        }
        return null;
    }

    public static String defaultFolderFor(String typeId) {
        return switch (typeId) {
            case "function" -> "Blueprints/Functions";
            case "command" -> "Blueprints/Commands";
            case "gui" -> "GUIs";
            case "scoreboard" -> "Customization/Scoreboards";
            case "tab" -> "Customization/Tabs";
            case "custom_content" -> "Content/Items";
            case "project_metadata" -> "";
            case "chat_channel", "chat_format", "chat_rule", "private_message_format", "mention_style", "ignore_list" -> "Customization/Chat";
            case "motd_profile" -> "Customization/MOTDs";
            case "message_rule" -> "Customization/Messages";
            case "recipe_definition" -> "Content/Recipes";
            case "text_template" -> "Text/Templates";
            case "advancement_tree" -> "Content/Advancements";
            case "worldgen" -> "WorldGen";
            case "world" -> "Worlds";
            default -> "Blueprints/Flows";
        };
    }

    private static String serializeJsonObject(Object item) {
        return new Gson().toJson(item);
    }

    private static Object deserializeJsonObject(String json) {
        return new Gson().fromJson(json, JsonObject.class);
    }

    private static void renameJsonObject(Object item, String newId) {
        if (item instanceof JsonObject json) {
            json.addProperty("id", newId);
        }
    }

    private static String jsonObjectId(Object item) {
        if (item instanceof JsonObject json && json.has("id") && !json.get("id").isJsonNull()) {
            return json.get("id").getAsString();
        }
        return "";
    }

    private static String jsonObjectName(Object item) {
        if (item instanceof JsonObject json) {
            if (json.has("displayName") && !json.get("displayName").isJsonNull()) {
                return json.get("displayName").getAsString();
            }
            if (json.has("name") && !json.get("name").isJsonNull()) {
                return json.get("name").getAsString();
            }
            if (json.has("id") && !json.get("id").isJsonNull()) {
                return json.get("id").getAsString();
            }
        }
        return "";
    }
}
