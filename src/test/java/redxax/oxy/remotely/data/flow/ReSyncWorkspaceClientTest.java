package redxax.oxy.remotely.data.flow;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.collaboration.CollaborationService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReSyncWorkspaceClientTest {
    @Test
    void ordersOperationsAndRecognizesOptimisticEchoes() {
        ReSyncWorkspaceClient client = new ReSyncWorkspaceClient(new Gson());
        RecordingListener listener = new RecordingListener();
        client.join("flow", "main", listener);
        client.applySnapshot("""
            {"type":"flow","resourceId":"main","sequence":2,"document":{"nodes":{},"connections":[]},"awareness":[]}
            """);
        client.sent("flow", "main", "local-operation");
        client.applyOperation("""
            {
              "type":"flow",
              "resourceId":"main",
              "sequence":3,
              "operationId":"local-operation",
              "authorSessionId":"one",
              "patches":[{"op":"set","path":"/nodes/first","value":{"type":"log","x":10,"y":20}}]
            }
            """);

        assertEquals(3L, client.sequence("flow", "main"));
        assertEquals(List.of(true), listener.ownOperations);
        assertEquals(1, listener.operations.size());
    }

    @Test
    void requestsAResyncWhenAnOrderedOperationIsMissing() {
        ReSyncWorkspaceClient client = new ReSyncWorkspaceClient(new Gson());
        RecordingListener listener = new RecordingListener();
        client.join("flow", "main", listener);
        client.applySnapshot("""
            {"type":"flow","resourceId":"main","sequence":4,"document":{"nodes":{},"connections":[]},"awareness":[]}
            """);
        client.applyOperation("""
            {"type":"flow","resourceId":"main","sequence":6,"operationId":"remote","authorSessionId":"two","patches":[]}
            """);

        assertEquals(List.of("Operation Gap"), listener.resyncReasons);
        assertTrue(listener.operations.isEmpty());
    }

    @Test
    void ignoresOperationsAlreadyCoveredByAResyncSnapshot() {
        ReSyncWorkspaceClient client = new ReSyncWorkspaceClient(new Gson());
        RecordingListener listener = new RecordingListener();
        client.join("flow", "main", listener);
        client.sent("flow", "main", "local-operation");
        client.applySnapshot("""
            {"type":"flow","resourceId":"main","sequence":5,"document":{"nodes":{},"connections":[]},"awareness":[]}
            """);
        client.applyOperation("""
            {"type":"flow","resourceId":"main","sequence":5,"operationId":"local-operation","authorSessionId":"one","patches":[]}
            """);

        assertEquals(5L, client.sequence("flow", "main"));
        assertTrue(listener.operations.isEmpty());
        assertTrue(listener.resyncReasons.isEmpty());
    }

    @Test
    void requestsOneResyncWhenOperationsArriveBeforeTheSnapshot() {
        ReSyncWorkspaceClient client = new ReSyncWorkspaceClient(new Gson());
        RecordingListener listener = new RecordingListener();
        client.join("flow", "main", listener);
        String operation = """
            {"type":"flow","resourceId":"main","sequence":1,"operationId":"remote","authorSessionId":"two","patches":[]}
            """;

        client.applyOperation(operation);
        client.applyOperation(operation);

        assertEquals(List.of("Operation Before Snapshot"), listener.resyncReasons);
        assertTrue(listener.operations.isEmpty());
    }

    @Test
    void treatsOperationsFromAnotherSessionAsRemote() {
        CollaborationService collaboration = new CollaborationService("direct");
        collaboration.identify(new CollaborationService.Identity("user", "Alex", "", "restudio"));
        ReSyncWorkspaceClient client = new ReSyncWorkspaceClient(new Gson(), collaboration);
        RecordingListener listener = new RecordingListener();
        client.join("flow", "main", listener);
        client.applySnapshot("""
            {"type":"flow","resourceId":"main","sequence":0,"document":{"nodes":{},"connections":[]},"awareness":[]}
            """);
        client.applyOperation("""
            {
              "type":"flow",
              "resourceId":"main",
              "sequence":1,
              "operationId":"bridge-operation",
              "authorSessionId":"bridge",
              "author":{"subjectId":"user","displayName":"Alex","avatar":"","source":"minecraft"},
              "patches":[{"op":"set","path":"/nodes/first/x","value":10}]
            }
            """);

        assertEquals(List.of(false), listener.ownOperations);
    }

    @Test
    void keepsAwarenessFromAnotherSessionVisible() {
        CollaborationService collaboration = new CollaborationService("direct");
        collaboration.identify(new CollaborationService.Identity("user", "Alex", "", "restudio"));
        ReSyncWorkspaceClient client = new ReSyncWorkspaceClient(new Gson(), collaboration);
        RecordingListener listener = new RecordingListener();
        client.join("flow", "main", listener);
        client.applySnapshot("""
            {
              "type":"flow",
              "resourceId":"main",
              "sequence":0,
              "document":{"nodes":{},"connections":[]},
              "awareness":[
                {"type":"flow","resourceId":"main","authorSessionId":"bridge","author":{"subjectId":"user","displayName":"Alex","avatar":"","source":"minecraft"},"state":{},"updatedAt":1},
                {"type":"flow","resourceId":"main","authorSessionId":"other","author":{"subjectId":"other","displayName":"Sam","avatar":"","source":"restudio"},"state":{},"updatedAt":1}
              ]
            }
            """);
        client.applyAwareness("""
            {"type":"flow","resourceId":"main","authorSessionId":"bridge","author":{"subjectId":"user","displayName":"Alex","avatar":"","source":"minecraft"},"state":{},"updatedAt":2}
            """);

        assertEquals(List.of("bridge", "other"), listener.snapshotAwareness);
        assertEquals(List.of("bridge", "other", "bridge"), listener.awareness);
    }

    private static final class RecordingListener implements ReSyncWorkspaceClient.Listener {
        private final List<ReSyncWorkspaceClient.Operation> operations = new ArrayList<>();
        private final List<Boolean> ownOperations = new ArrayList<>();
        private final List<String> resyncReasons = new ArrayList<>();
        private final List<String> snapshotAwareness = new ArrayList<>();
        private final List<String> awareness = new ArrayList<>();

        @Override
        public void onSnapshot(ReSyncWorkspaceClient.Snapshot snapshot) {
            snapshot.awareness().stream().map(ReSyncWorkspaceClient.Awareness::authorSessionId).forEach(snapshotAwareness::add);
        }

        @Override
        public void onOperation(ReSyncWorkspaceClient.Operation operation, boolean own) {
            operations.add(operation);
            ownOperations.add(own);
        }

        @Override
        public void onAwareness(ReSyncWorkspaceClient.Awareness awareness) {
            this.awareness.add(awareness.authorSessionId());
        }

        @Override
        public void onResync(String reason) {
            resyncReasons.add(reason);
        }
    }
}
