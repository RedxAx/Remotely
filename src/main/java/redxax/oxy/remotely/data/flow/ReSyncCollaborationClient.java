package redxax.oxy.remotely.data.flow;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.collaboration.CollaborationService;
import redxax.oxy.remotely.flow.data.FlowJson;

import java.util.ArrayList;
import java.util.List;

public final class ReSyncCollaborationClient extends CollaborationService {
    public ReSyncCollaborationClient(Gson ignored, String clientId) {
        this(clientId);
    }

    public ReSyncCollaborationClient(String clientId) {
        super(clientId);
    }

    public boolean applySnapshot(String json) {
        Snapshot snapshot;
        try {
            JsonObject root = FlowJson.parse(json).getAsJsonObject();
            List<Presence> collaborators = new ArrayList<>();
            FlowJson.array(root, "collaborators").forEach(value -> { if (value.isJsonObject()) collaborators.add(presence(value.getAsJsonObject())); });
            snapshot = new Snapshot(FlowJson.string(root, "selfSessionId", ""), identity(FlowJson.object(root, "selfIdentity")),
                strings(root, "selfSessionIds"), collaborators);
        } catch (RuntimeException exception) {
            return false;
        }
        return acceptSnapshot(snapshot != null ? snapshot.selfSessionId() : "",
            snapshot != null ? snapshot.selfIdentity() : null,
            snapshot != null ? snapshot.selfSessionIds() : List.of(),
            snapshot != null ? snapshot.collaborators() : List.of());
    }

    public void applyResourceChange(ResourceChange change) {
        acceptResourceChange(change);
    }

    public boolean applyMessage(String json) {
        Message message;
        try {
            JsonObject root = FlowJson.parse(json).getAsJsonObject();
            message = new Message(FlowJson.string(root, "id", ""), FlowJson.string(root, "authorSessionId", ""),
                identity(FlowJson.object(root, "author")), FlowJson.string(root, "resourceType", ""),
                FlowJson.string(root, "resourceId", ""), FlowJson.integer(root, "color", 0),
                FlowJson.string(root, "message", ""), FlowJson.longValue(root, "sentAt", 0));
        } catch (RuntimeException exception) {
            return false;
        }
        return acceptMessage(message);
    }

    private record Snapshot(String selfSessionId, Identity selfIdentity, List<String> selfSessionIds, List<Presence> collaborators) {
    }

    private static Presence presence(JsonObject json) {
        return new Presence(FlowJson.string(json, "sessionId", ""), FlowJson.string(json, "clientId", ""), identity(FlowJson.object(json, "identity")),
            FlowJson.string(json, "resourceType", ""), FlowJson.string(json, "resourceId", ""), FlowJson.string(json, "viewId", ""),
            FlowJson.decimal(json, "x", 0), FlowJson.decimal(json, "y", 0), FlowJson.bool(json, "active", false),
            FlowJson.bool(json, "typing", false), FlowJson.integer(json, "color", 0), FlowJson.bool(json, "customColor", false),
            FlowJson.longValue(json, "updatedAt", 0));
    }

    private static Identity identity(JsonObject json) {
        return json == null ? null : new Identity(FlowJson.string(json, "subjectId", ""), FlowJson.string(json, "displayName", "Collaborator"),
            FlowJson.string(json, "avatar", ""), FlowJson.string(json, "source", ""));
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> values = new ArrayList<>();
        for (JsonElement value : FlowJson.array(json, key)) if (!value.isJsonNull()) values.add(value.getAsString());
        return values;
    }
}
