package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.util.BrowserSafeState;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.collaboration.CollaborationService;
import redxax.oxy.remotely.flow.data.FlowJson;
import restudio.resync.flow.workspace.LiveDocumentChannel;
import restudio.resync.flow.workspace.WorkspacePatch;
import restudio.resync.flow.workspace.WorkspaceTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReSyncWorkspaceClient {
    private final CollaborationService collaboration;
    private final LiveDocumentChannel<JsonObject, List<WorkspacePatch<JsonElement>>, JsonObject, CollaborationService.Identity> documents =
        new LiveDocumentChannel<>();
    private final Map<Listener, LiveDocumentChannel.Listener<JsonObject, List<WorkspacePatch<JsonElement>>, JsonObject, CollaborationService.Identity>> adapters =
        BrowserSafeState.map();
    private final Map<Listener, Set<String>> listenerTargets = BrowserSafeState.map();

    public ReSyncWorkspaceClient() {
        this(null, null);
    }

    public ReSyncWorkspaceClient(Object ignored) {
        this(ignored, ignored instanceof CollaborationService service ? service : null);
    }

    public ReSyncWorkspaceClient(Object ignored, CollaborationService collaboration) {
        this.collaboration = collaboration != null ? collaboration : ignored instanceof CollaborationService service ? service : null;
    }

    public void bind(LiveDocumentChannel.Transport<List<WorkspacePatch<JsonElement>>, JsonObject> transport) {
        documents.bind(transport);
    }

    public boolean connect() {
        return documents.connect();
    }

    public boolean disconnect(String reason) {
        return documents.disconnect(reason);
    }

    public boolean join(String type, String resourceId, Listener listener) {
        if (listener == null) {
            return false;
        }
        WorkspaceTarget target = target(type, resourceId);
        boolean first = documents.join(target, adapters.computeIfAbsent(listener, this::adapt));
        listenerTargets.computeIfAbsent(listener, ignored -> BrowserSafeState.set()).add(target.key());
        return first;
    }

    public boolean leave(String type, String resourceId, Listener listener) {
        if (listener == null) {
            return false;
        }
        WorkspaceTarget target = target(type, resourceId);
        LiveDocumentChannel.Listener<JsonObject, List<WorkspacePatch<JsonElement>>, JsonObject, CollaborationService.Identity> adapter =
            adapters.get(listener);
        if (adapter == null) {
            return false;
        }
        boolean last = documents.leave(target, adapter);
        Set<String> targets = listenerTargets.get(listener);
        if (targets != null) {
            targets.remove(target.key());
            if (targets.isEmpty()) {
                listenerTargets.remove(listener, targets);
                adapters.remove(listener, adapter);
            }
        }
        return last;
    }

    public void sent(String type, String resourceId, String operationId) {
        documents.sent(target(type, resourceId), operationId);
    }

    public void discard(String operationId) {
        documents.discard(operationId);
    }

    public long sequence(String type, String resourceId) {
        return documents.sequence(target(type, resourceId));
    }

    public String publishOperation(String type, String resourceId, List<WorkspacePatch<JsonElement>> patches) {
        return patches == null || patches.isEmpty()
            ? "" : documents.publishOperation(target(type, resourceId), patches);
    }

    public boolean publishAwareness(String type, String resourceId, JsonObject state) {
        return documents.publishAwareness(target(type, resourceId), state != null ? state : new JsonObject());
    }

    public void applySnapshot(String json) {
        Snapshot snapshot = snapshot(json);
        if (snapshot == null || snapshot.document() == null) {
            return;
        }
        List<LiveDocumentChannel.Awareness<JsonObject, CollaborationService.Identity>> awareness = snapshot.awareness() == null
            ? List.of() : snapshot.awareness().stream().filter(value -> !isOwn(value.authorSessionId())).map(this::toGeneric).toList();
        documents.acceptSnapshot(new LiveDocumentChannel.Snapshot<>(
            target(snapshot.type(), snapshot.resourceId()), snapshot.sequence(), snapshot.document(), awareness));
    }

    public void applyOperation(String json) {
        Operation operation = operation(json);
        if (operation == null || operation.patches() == null) {
            return;
        }
        documents.acceptOperation(new LiveDocumentChannel.Operation<>(
            target(operation.type(), operation.resourceId()), operation.sequence(), operation.operationId(),
            operation.authorSessionId(), operation.author(), operation.patches()));
    }

    public void applyAwareness(String json) {
        Awareness awareness = awareness(json);
        if (awareness != null && !isOwn(awareness.authorSessionId())) {
            documents.acceptAwareness(toGeneric(awareness));
        }
    }

    public void applyResync(String json) {
        Resync resync = resync(json);
        if (resync != null) {
            documents.acceptResync(target(resync.type(), resync.resourceId()), resync.reason());
        }
    }

    public void clear() {
        documents.disconnect("Disconnected");
    }

    private LiveDocumentChannel.Listener<JsonObject, List<WorkspacePatch<JsonElement>>, JsonObject, CollaborationService.Identity> adapt(
        Listener listener) {
        return new LiveDocumentChannel.Listener<>() {
            @Override
            public void onSnapshot(LiveDocumentChannel.Snapshot<JsonObject, JsonObject, CollaborationService.Identity> snapshot) {
                listener.onSnapshot(new Snapshot(snapshot.target().resourceType(), snapshot.target().resourceId(),
                    snapshot.sequence(), snapshot.document(),
                    snapshot.awareness().stream().map(ReSyncWorkspaceClient.this::fromGeneric).toList()));
            }

            @Override
            public void onOperation(LiveDocumentChannel.Operation<List<WorkspacePatch<JsonElement>>, CollaborationService.Identity> operation,
                                    boolean own) {
                listener.onOperation(new Operation(operation.target().resourceType(), operation.target().resourceId(),
                    operation.sequence(), operation.operationId(), operation.authorSessionId(), operation.author(),
                    operation.operation()), own || isOwn(operation.authorSessionId()));
            }

            @Override
            public void onAwareness(LiveDocumentChannel.Awareness<JsonObject, CollaborationService.Identity> awareness) {
                listener.onAwareness(fromGeneric(awareness));
            }

            @Override
            public void onResync(String reason) {
                listener.onResync(reason);
            }
        };
    }

    private boolean isOwn(String sessionId) {
        return collaboration != null && collaboration.isOwnSession(sessionId);
    }

    private LiveDocumentChannel.Awareness<JsonObject, CollaborationService.Identity> toGeneric(Awareness awareness) {
        return new LiveDocumentChannel.Awareness<>(target(awareness.type(), awareness.resourceId()),
            awareness.authorSessionId(), awareness.author(), awareness.state(), awareness.updatedAt());
    }

    private Awareness fromGeneric(LiveDocumentChannel.Awareness<JsonObject, CollaborationService.Identity> awareness) {
        return new Awareness(awareness.target().resourceType(), awareness.target().resourceId(),
            awareness.authorSessionId(), awareness.author(), awareness.state(), awareness.updatedAt());
    }

    private WorkspaceTarget target(String type, String resourceId) {
        return new WorkspaceTarget(type, resourceId);
    }

    private Snapshot snapshot(String json) {
        try {
            JsonObject root = root(json);
            if (root == null) {
                return null;
            }
            JsonObject document = FlowJson.object(root, "document");
            return new Snapshot(FlowJson.string(root, "type", null), FlowJson.string(root, "resourceId", null),
                FlowJson.longValue(root, "sequence", 0), document == null ? null : document.deepCopy(),
                awarenessList(root.get("awareness")));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Operation operation(String json) {
        try {
            JsonObject root = root(json);
            if (root == null) {
                return null;
            }
            return new Operation(FlowJson.string(root, "type", null), FlowJson.string(root, "resourceId", null),
                FlowJson.longValue(root, "sequence", 0), FlowJson.string(root, "operationId", null),
                FlowJson.string(root, "authorSessionId", null), identity(FlowJson.object(root, "author")), patches(root.get("patches")));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Awareness awareness(String json) {
        try {
            JsonObject root = root(json);
            return root == null ? null : awareness(root);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Resync resync(String json) {
        try {
            JsonObject root = root(json);
            return root == null ? null : new Resync(FlowJson.string(root, "type", null), FlowJson.string(root, "resourceId", null),
                FlowJson.string(root, "reason", null));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Awareness awareness(JsonObject root) {
        JsonObject state = FlowJson.object(root, "state");
        return new Awareness(FlowJson.string(root, "type", null), FlowJson.string(root, "resourceId", null),
            FlowJson.string(root, "authorSessionId", null), identity(FlowJson.object(root, "author")),
            state == null ? null : state.deepCopy(), FlowJson.longValue(root, "updatedAt", 0));
    }

    private List<Awareness> awarenessList(JsonElement value) {
        if (value == null || !value.isJsonArray()) {
            return List.of();
        }
        List<Awareness> result = new ArrayList<>();
        for (JsonElement item : value.getAsJsonArray()) {
            if (item.isJsonObject()) {
                result.add(awareness(item.getAsJsonObject()));
            }
        }
        return result;
    }

    private List<WorkspacePatch<JsonElement>> patches(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonArray()) {
            return null;
        }
        List<WorkspacePatch<JsonElement>> result = new ArrayList<>();
        for (JsonElement item : value.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject patch = item.getAsJsonObject();
            JsonElement patchValue = patch.has("value") && patch.get("value") != null ? patch.get("value").deepCopy() : null;
            result.add(new WorkspacePatch<>(FlowJson.string(patch, "op", null), FlowJson.string(patch, "path", null), patchValue));
        }
        return result;
    }

    private ReSyncCollaborationClient.Identity identity(JsonObject json) {
        return json == null ? null : new ReSyncCollaborationClient.Identity(FlowJson.string(json, "subjectId", ""),
            FlowJson.string(json, "displayName", "Collaborator"), FlowJson.string(json, "avatar", ""),
            FlowJson.string(json, "source", ""));
    }

    private JsonObject root(String json) {
        JsonElement parsed = FlowJson.parse(json);
        return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    }

    public interface Listener {
        void onSnapshot(Snapshot snapshot);

        void onOperation(Operation operation, boolean own);

        void onAwareness(Awareness awareness);

        void onResync(String reason);
    }

    public record Snapshot(String type, String resourceId, long sequence, JsonObject document, List<Awareness> awareness) {
        @Override
        public String toString() {
            return "Snapshot[type=" + type + ", resourceId=" + resourceId + ", sequence=" + sequence
                + ", document=" + FlowJson.write(document) + ", awarenessCount=" + (awareness == null ? 0 : awareness.size()) + "]";
        }
    }

    public record Operation(String type, String resourceId, long sequence, String operationId, String authorSessionId,
                            ReSyncCollaborationClient.Identity author, List<WorkspacePatch<JsonElement>> patches) {
        @Override
        public String toString() {
            return "Operation[type=" + type + ", resourceId=" + resourceId + ", sequence=" + sequence
                + ", operationId=" + operationId + ", authorSessionId=" + authorSessionId + ", patchCount="
                + (patches == null ? 0 : patches.size()) + "]";
        }
    }

    public record Awareness(String type, String resourceId, String authorSessionId,
                            ReSyncCollaborationClient.Identity author, JsonObject state, long updatedAt) {
        @Override
        public String toString() {
            return "Awareness[type=" + type + ", resourceId=" + resourceId + ", authorSessionId=" + authorSessionId
                + ", state=" + FlowJson.write(state) + ", updatedAt=" + updatedAt + "]";
        }
    }

    private record Resync(String type, String resourceId, String reason) {
    }
}
