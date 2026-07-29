package redxax.oxy.remotely.data.flow;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.collaboration.CollaborationService;
import restudio.resync.flow.workspace.LiveDocumentChannel;
import restudio.resync.flow.workspace.WorkspacePatch;
import restudio.resync.flow.workspace.WorkspaceTarget;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ReSyncWorkspaceClient {
    private final Gson gson;
    private final LiveDocumentChannel<JsonObject, List<WorkspacePatch<JsonElement>>, JsonObject, CollaborationService.Identity> documents =
        new LiveDocumentChannel<>();
    private final Map<Listener, LiveDocumentChannel.Listener<JsonObject, List<WorkspacePatch<JsonElement>>, JsonObject, CollaborationService.Identity>> adapters =
        new ConcurrentHashMap<>();
    private final Map<Listener, Set<String>> listenerTargets = new ConcurrentHashMap<>();

    public ReSyncWorkspaceClient(Gson gson) {
        this.gson = gson;
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
        listenerTargets.computeIfAbsent(listener, ignored -> ConcurrentHashMap.newKeySet()).add(target.key());
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
        Snapshot snapshot = parse(json, Snapshot.class);
        if (snapshot == null || snapshot.document() == null) {
            return;
        }
        List<LiveDocumentChannel.Awareness<JsonObject, CollaborationService.Identity>> awareness = snapshot.awareness() == null
            ? List.of() : snapshot.awareness().stream().map(this::toGeneric).toList();
        documents.acceptSnapshot(new LiveDocumentChannel.Snapshot<>(
            target(snapshot.type(), snapshot.resourceId()), snapshot.sequence(), snapshot.document(), awareness));
    }

    public void applyOperation(String json) {
        Operation operation = parse(json, Operation.class);
        if (operation == null || operation.patches() == null) {
            return;
        }
        documents.acceptOperation(new LiveDocumentChannel.Operation<>(
            target(operation.type(), operation.resourceId()), operation.sequence(), operation.operationId(),
            operation.authorSessionId(), operation.author(), operation.patches()));
    }

    public void applyAwareness(String json) {
        Awareness awareness = parse(json, Awareness.class);
        if (awareness != null) {
            documents.acceptAwareness(toGeneric(awareness));
        }
    }

    public void applyResync(String json) {
        Resync resync = parse(json, Resync.class);
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
                    operation.operation()), own);
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

    private <T> T parse(String json, Class<T> type) {
        try {
            return gson.fromJson(json, type);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public interface Listener {
        void onSnapshot(Snapshot snapshot);

        void onOperation(Operation operation, boolean own);

        void onAwareness(Awareness awareness);

        void onResync(String reason);
    }

    public record Snapshot(String type, String resourceId, long sequence, JsonObject document, List<Awareness> awareness) {
    }

    public record Operation(String type, String resourceId, long sequence, String operationId, String authorSessionId,
                            ReSyncCollaborationClient.Identity author, List<WorkspacePatch<JsonElement>> patches) {
    }

    public record Awareness(String type, String resourceId, String authorSessionId,
                            ReSyncCollaborationClient.Identity author, JsonObject state, long updatedAt) {
    }

    private record Resync(String type, String resourceId, String reason) {
    }
}
