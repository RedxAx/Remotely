package redxax.oxy.remotely.data.flow;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.data.WorldGenSerializer;
import redxax.oxy.remotely.worldgen.registry.WorldGenNodeDefinition;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class WorldGenProtocolHandler {
    private final String serverId;
    private final Gson gson;
    private final Consumer<com.google.gson.JsonObject> jobConsumer;

    WorldGenProtocolHandler(String serverId, Gson gson, Consumer<com.google.gson.JsonObject> jobConsumer) {
        this.serverId = serverId;
        this.gson = gson;
        this.jobConsumer = jobConsumer;
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
                default -> System.out.println("[ReSyncFlow] Unknown worldgen packet: 0x" + String.format("%02X", packetId));
            }
        } catch (Exception e) {
            System.err.println("[ReSyncFlow] Failed to process worldgen packet: " + e.getMessage());
        }
    }

    private void handlePreviewStatus(String json) {
        Map<?, ?> status = gson.fromJson(json, Map.class);
        String previewId = status != null && status.get("previewId") != null ? String.valueOf(status.get("previewId")) : "";
        String state = status != null && status.get("status") != null ? String.valueOf(status.get("status")) : "error";
        String message = status != null && status.get("message") != null ? String.valueOf(status.get("message")) : state;
        WorldGenManager.getInstance().handlePreviewStatus(serverId, previewId, state, message);
    }

    private void handleRegistrySnapshot(String json) {
        Type type = TypeToken.getParameterized(List.class, WorldGenNodeDefinition.class).getType();
        List<WorldGenNodeDefinition> definitions;
        Map<?, ?> snapshot = null;
        try {
            snapshot = gson.fromJson(json, Map.class);
        } catch (Exception ignored) {
        }
        if (snapshot != null && snapshot.get("nodes") != null) {
            definitions = gson.fromJson(gson.toJson(snapshot.get("nodes")), type);
        } else {
            definitions = gson.fromJson(json, type);
        }
        WorldGenManager.getInstance().applyRegistrySnapshot(serverId, definitions, snapshot != null ? snapshot.get("capabilities") : null);
    }

    private void handleProjectData(String json) {
        WorldGenProject project = WorldGenSerializer.deserializeProject(json);
        if (project != null) {
            WorldGenManager.getInstance().handleProjectData(serverId, project);
        }
    }

    private void handleProjectList(String json) {
        Type type = TypeToken.getParameterized(List.class, String.class).getType();
        List<String> ids = gson.fromJson(json, type);
        WorldGenManager.getInstance().handleProjectList(serverId, ids != null ? ids : List.of());
    }

    private void handleProjectSaveAck(String json) {
        WorldGenManager.getInstance().handleProjectSaved(serverId, json);
    }

    private void handleCompileDiagnostics(String json) {
        WorldGenManager.getInstance().handleCompileDiagnostics(serverId, json);
    }

    private void handleJob(String json) {
        if (jobConsumer != null) {
            jobConsumer.accept(gson.fromJson(json, com.google.gson.JsonObject.class));
        }
    }
}
