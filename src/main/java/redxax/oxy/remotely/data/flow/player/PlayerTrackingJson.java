package redxax.oxy.remotely.data.flow.player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.FlowJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlayerTrackingJson {
    public static PlayerTrackingUpdate read(JsonObject json) {
        List<PlayerDossier> dossiers = new ArrayList<>(); FlowJson.array(json, "dossiers").forEach(value -> { if (value.isJsonObject()) dossiers.add(dossier(value.getAsJsonObject())); });
        JsonObject encodedDossier = FlowJson.object(json, "dossier");
        return new PlayerTrackingUpdate(FlowJson.string(json, "type", ""), FlowJson.string(json, "reason", ""), FlowJson.string(json, "playerId", ""), encodedDossier == null ? null : dossier(encodedDossier), dossiers);
    }

    private static PlayerDossier dossier(JsonObject json) {
        JsonObject active = FlowJson.object(json, "activeSession"); List<PlayerSessionRecord> sessions = new ArrayList<>(); FlowJson.array(json, "sessions").forEach(value -> { if (value.isJsonObject()) sessions.add(session(value.getAsJsonObject())); });
        List<PlayerEventRecord> events = new ArrayList<>(); FlowJson.array(json, "recentEvents").forEach(value -> { if (value.isJsonObject()) events.add(event(value.getAsJsonObject())); });
        Map<String, PlayerFacetState> facets = new LinkedHashMap<>(); JsonObject encodedFacets = FlowJson.object(json, "facets"); if (encodedFacets != null) encodedFacets.entrySet().forEach(entry -> { if (entry.getValue().isJsonObject()) facets.put(entry.getKey(), facet(entry.getValue().getAsJsonObject())); });
        return new PlayerDossier(FlowJson.string(json, "playerId", ""), FlowJson.string(json, "playerName", ""), FlowJson.bool(json, "online", false), FlowJson.longValue(json, "firstSeenAt", 0), FlowJson.longValue(json, "lastSeenAt", 0), FlowJson.longValue(json, "totalPlayTimeMs", 0), active == null ? null : session(active), sessions, events, facets);
    }

    private static PlayerSessionRecord session(JsonObject json) { return new PlayerSessionRecord(FlowJson.string(json, "sessionId", ""), FlowJson.string(json, "source", ""), FlowJson.longValue(json, "startedAt", 0), FlowJson.longValue(json, "endedAt", 0), FlowJson.longValue(json, "durationMs", 0)); }
    private static PlayerEventRecord event(JsonObject json) { return new PlayerEventRecord(FlowJson.string(json, "eventId", ""), FlowJson.longValue(json, "timestamp", 0), FlowJson.string(json, "moduleId", ""), FlowJson.string(json, "category", ""), FlowJson.string(json, "type", ""), map(json.get("data"))); }
    private static PlayerFacetState facet(JsonObject json) { JsonObject metadata = FlowJson.object(json, "metadata"); return new PlayerFacetState(FlowJson.string(json, "facetId", ""), FlowJson.string(json, "moduleId", ""), FlowJson.longValue(json, "updatedAt", 0), metadata == null ? null : new PlayerFacetMetadata(FlowJson.string(metadata, "title", ""), FlowJson.string(metadata, "tabName", ""), FlowJson.integer(metadata, "priority", 0), FlowJson.bool(metadata, "tab", false)), map(json.get("data"))); }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(JsonElement value) { Object decoded = FlowJson.value(value); return decoded instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }

    private PlayerTrackingJson() {
    }
}
