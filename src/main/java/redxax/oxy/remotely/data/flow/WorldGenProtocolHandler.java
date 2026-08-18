package redxax.oxy.remotely.data.flow;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.FlowJson;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.data.WorldGenSerializer;
import redxax.oxy.remotely.worldgen.registry.WorldGenNodeDefinition;
import redxax.oxy.remotely.worldgen.registry.WorldGenNodeDefinitionJson;
import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class WorldGenProtocolHandler {
    private final String serverId;
    private final Consumer<JsonObject> jobConsumer;
    private final ReSyncWorldGenerationState state;

    WorldGenProtocolHandler(String serverId, Consumer<JsonObject> jobConsumer, ReSyncWorldGenerationState state) {
        this.serverId = serverId;
        this.jobConsumer = jobConsumer;
        this.state = state == null ? ReSyncWorldGenerationState.noop() : state;
    }

    WorldGenProtocolHandler(String serverId, Object ignored, Consumer<JsonObject> jobConsumer, ReSyncWorldGenerationState state) {
        this(serverId, jobConsumer, state);
    }

    void handle(byte[] data) {
        if (data.length < 1) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte packetId = buffer.get();
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        try {
            switch (packetId) {
                case 0x23 -> handlePreviewStatus(json);
                case 0x25 -> handleRegistrySnapshot(json);
                case 0x35 -> handleProjectData(json);
                case 0x36 -> handleProjectList(json);
                case 0x37 -> handleProjectSaveAck(json);
                case 0x38 -> handleCompileDiagnostics(json);
                case 0x39 -> handleJob(json);
                default -> ReLog.logger(LogTypes.FLOW).source(LogSource.server(serverId, serverId)).component(WorldGenProtocolHandler.class).with("packetId", String.format("0x%02X", packetId)).warn("Unknown world generation packet");
            }
        } catch (Exception e) {
            ReLog.logger(LogTypes.FLOW).source(LogSource.server(serverId, serverId)).component(WorldGenProtocolHandler.class).with("packetId", String.format("0x%02X", packetId)).error("Could not process world generation packet", e);
        }
    }

    private void handlePreviewStatus(String json) {
        JsonElement root = FlowJson.parse(json);
        JsonObject status = root.isJsonNull() ? null : root.getAsJsonObject();
        String previewId = FlowJson.string(status, "previewId", "");
        String state = FlowJson.string(status, "status", "error");
        String message = FlowJson.string(status, "message", state);
        this.state.onPreviewStatus(serverId, previewId, state, message);
    }

    private void handleRegistrySnapshot(String json) {
        JsonElement root = FlowJson.parse(json);
        if (root.isJsonNull()) {
            state.onRegistrySnapshot(serverId, null, null);
            return;
        }
        JsonObject snapshot = root.isJsonObject() ? root.getAsJsonObject() : null;
        List<WorldGenNodeDefinition> definitions;
        Object capabilities = null;
        if (snapshot != null && snapshot.get("nodes") != null && !snapshot.get("nodes").isJsonNull()) {
            if (!snapshot.get("nodes").isJsonArray()) {
                throw new IllegalArgumentException("World generation registry nodes must be an array");
            }
            definitions = WorldGenNodeDefinitionJson.readList(snapshot.get("nodes"));
            if (snapshot.has("capabilities")) {
                capabilities = FlowJson.value(snapshot.get("capabilities"));
            }
        } else {
            if (!root.isJsonArray()) {
                throw new IllegalArgumentException("World generation registry must be an array or object envelope");
            }
            definitions = WorldGenNodeDefinitionJson.readList(root);
        }
        this.state.onRegistrySnapshot(serverId, definitions, capabilities);
    }

    private void handleProjectData(String json) {
        WorldGenProject project = WorldGenSerializer.deserializeProject(json);
        if (project != null) {
            state.onProjectData(serverId, project);
        }
    }

    private void handleProjectList(String json) {
        JsonElement root = FlowJson.parse(json);
        if (root.isJsonNull()) {
            state.onProjectList(serverId, List.of());
            return;
        }
        if (!root.isJsonArray()) {
            throw new IllegalArgumentException("World generation project list must be an array");
        }
        JsonArray encoded = root.getAsJsonArray();
        List<String> ids = new ArrayList<>();
        for (JsonElement value : encoded) {
            ids.add(value.isJsonNull() ? null : value.getAsString());
        }
        state.onProjectList(serverId, ids);
    }

    private void handleProjectSaveAck(String json) {
        state.onProjectSaveAcknowledged(serverId, json);
    }

    private void handleCompileDiagnostics(String json) {
        state.onCompileDiagnostics(serverId, json);
    }

    private void handleJob(String json) {
        if (jobConsumer != null) {
            JsonElement root = FlowJson.parse(json);
            jobConsumer.accept(root.isJsonNull() ? null : root.getAsJsonObject());
        }
    }
}
