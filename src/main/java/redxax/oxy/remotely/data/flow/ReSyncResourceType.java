package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowSerializer;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;

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
    private final String displayName;
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
        this.displayName = displayName;
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
    public String displayName() { return displayName; }

    public String serialize(Object item) { return serializer.serialize(item); }
    public Object deserialize(String json) { return deserializer.deserialize(json); }
    public void applyRename(Object item, String newId) { renameApplier.accept(item, newId); }
    public String extractId(Object item) { return idExtractor.extract(item); }
    public String extractName(Object item) { return nameExtractor.extract(item); }

    public static ReSyncResourceType byDataResponse(byte packetId) {
        for (ReSyncResourceType rt : values()) {
            if (rt.dataResponseByte == packetId) return rt;
        }
        return null;
    }

    public static ReSyncResourceType byListResponse(byte packetId) {
        for (ReSyncResourceType rt : values()) {
            if (rt.listResponseByte == packetId) return rt;
        }
        return null;
    }

    public static ReSyncResourceType bySaveAck(byte packetId) {
        for (ReSyncResourceType rt : values()) {
            if (rt.saveAckByte == packetId) return rt;
        }
        return null;
    }
}
