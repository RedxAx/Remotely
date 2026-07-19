package redxax.oxy.remotely.network;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.backend.impl.LocalBackend;
import restudio.rebase.backend.impl.SshBackend;
import restudio.rebase.instance.Instance;
import restudio.rebase.util.ssh.SSHManager;
import restudio.resync.network.NetworkChannels;
import restudio.resync.network.NetworkEvent;
import restudio.resync.network.NetworkEventCodec;
import restudio.resync.network.NetworkFrame;
import restudio.resync.network.NetworkFrameCodec;
import restudio.resync.network.NetworkFrameType;
import restudio.resync.network.NetworkNodePresence;
import restudio.resync.network.NetworkNodePresenceCodec;
import restudio.resync.network.NetworkNodeMode;
import restudio.resync.network.NetworkNodeModeCodec;
import restudio.resync.network.NetworkNodeStatus;
import restudio.resync.network.NetworkProxyAction;
import restudio.resync.network.NetworkProxyActionCodec;
import restudio.resync.network.NetworkProxyActionType;
import restudio.resync.network.NetworkRequestContext;
import restudio.resync.network.NetworkRoute;
import restudio.resync.network.NetworkRouteSet;
import restudio.resync.network.NetworkRouteSetCodec;
import restudio.resync.network.NetworkRoutingGroup;
import restudio.resync.network.NetworkRoutingStrategy;
import restudio.resync.network.NetworkSnapshotAdminCodec;
import restudio.resync.network.NetworkSnapshotMetadata;
import restudio.resync.network.NetworkSnapshotPin;
import restudio.resync.network.NetworkSnapshotQuery;
import restudio.resync.network.NetworkSnapshotRestore;
import restudio.resync.network.NetworkStateReconciliationCodec;
import restudio.resync.network.NetworkStateReconciliationRequest;
import restudio.resync.network.NetworkTransferCodec;
import restudio.resync.network.PlayerTransfer;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

public class NetworkRuntimeMonitor implements AutoCloseable {
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAXIMUM_FRAME_BYTES = 1_048_576;
    private static final int MAXIMUM_PAYLOAD_BYTES = 524_288;
    private static final long RETRY_DELAY_MILLIS = 5_000;
    private static final long HEARTBEAT_INTERVAL_MILLIS = 5_000;
    private static final long CONNECT_TIMEOUT_MILLIS = 15_000;
    private final NetworkSecretStore secretStore;
    private final NetworkFrameCodec codec = new NetworkFrameCodec(MAXIMUM_FRAME_BYTES, MAXIMUM_PAYLOAD_BYTES);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "remotely-network-runtime");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Target> targets = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, NetworkRuntimeSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, Long> nextAttempts = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<NetworkRuntimeSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<NetworkEvent>> eventListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public NetworkRuntimeMonitor(NetworkSecretStore secretStore) {
        this.secretStore = secretStore;
        executor.scheduleWithFixedDelay(this::tick, 1, 2, TimeUnit.SECONDS);
    }

    public void refresh(Collection<NetworkDefinition> networks, Collection<Instance> instances) {
        if (closed.get()) {
            return;
        }
        ListSnapshot snapshot = new ListSnapshot(networks, instances);
        executor.execute(() -> reconcile(snapshot));
    }

    public NetworkRuntimeSnapshot snapshot(String networkId) {
        String normalized = networkId == null ? "" : networkId.trim();
        return normalized.isBlank() ? NetworkRuntimeSnapshot.disabled("") : snapshots.getOrDefault(normalized, NetworkRuntimeSnapshot.disabled(normalized));
    }

    public void addListener(Consumer<NetworkRuntimeSnapshot> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Consumer<NetworkRuntimeSnapshot> listener) {
        listeners.remove(listener);
    }

    public void addEventListener(Consumer<NetworkEvent> listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    public void removeEventListener(Consumer<NetworkEvent> listener) {
        eventListeners.remove(listener);
    }

    public CompletableFuture<Void> setNodeMode(String networkId, String nodeId, NetworkNodeStatus status) {
        String normalizedNetworkId = networkId == null ? "" : networkId.trim();
        if (closed.get() || normalizedNetworkId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("ReSync Runtime Is Not Available"));
        }
        NetworkNodeMode mode;
        try {
            mode = new NetworkNodeMode(nodeId, status);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                Session session = sessions.get(normalizedNetworkId);
                if (session == null || !session.authorized()) {
                    result.completeExceptionally(new IllegalStateException("ReSync Runtime Is Not Connected"));
                    return;
                }
                session.setNodeMode(mode, result);
            });
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    public CompletableFuture<Void> executeProxyCommand(String networkId, String command) {
        try {
            return proxyAction(networkId, new NetworkProxyAction(NetworkProxyActionType.COMMAND, command));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Void> broadcast(String networkId, String message) {
        try {
            return proxyAction(networkId, new NetworkProxyAction(NetworkProxyActionType.BROADCAST, message));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<List<NetworkSnapshotMetadata>> listSnapshots(String networkId, UUID playerId, int limit) {
        return listSnapshots(networkId, playerId, 0, limit);
    }

    public CompletableFuture<List<NetworkSnapshotMetadata>> listSnapshots(String networkId, UUID playerId, int offset, int limit) {
        try {
            NetworkSnapshotQuery query = new NetworkSnapshotQuery(playerId, offset, limit);
            return runtimeRequest(networkId, session -> session.request(NetworkFrameType.SNAPSHOT_LIST, NetworkChannels.STATE, NetworkSnapshotAdminCodec.encodeQuery(query), Set.of("state.inspect"), 10).thenApply(frame -> NetworkSnapshotAdminCodec.decodeList(frame.payload())));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<NetworkSnapshotMetadata> readSnapshot(String networkId, String snapshotId) {
        try {
            return runtimeRequest(networkId, session -> session.request(NetworkFrameType.SNAPSHOT_READ, NetworkChannels.STATE, NetworkSnapshotAdminCodec.encodeReference(snapshotId), Set.of("state.inspect"), 10).thenApply(frame -> NetworkSnapshotAdminCodec.decodeMetadata(frame.payload())));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<NetworkSnapshotMetadata> pinSnapshot(String networkId, String snapshotId, boolean pinned) {
        try {
            NetworkSnapshotPin pin = new NetworkSnapshotPin(snapshotId, pinned);
            return runtimeRequest(networkId, session -> session.request(NetworkFrameType.SNAPSHOT_PIN, NetworkChannels.STATE, NetworkSnapshotAdminCodec.encodePin(pin), Set.of("state.restore"), 10).thenApply(frame -> NetworkSnapshotAdminCodec.decodeMetadata(frame.payload())));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<PlayerTransfer> restoreSnapshot(String networkId, String snapshotId, String targetNodeId) {
        try {
            NetworkSnapshotRestore restore = new NetworkSnapshotRestore(snapshotId, targetNodeId, Instant.now().plusSeconds(600).toEpochMilli());
            return runtimeRequest(networkId, session -> session.request(NetworkFrameType.SNAPSHOT_RESTORE, NetworkChannels.STATE, NetworkSnapshotAdminCodec.encodeRestore(restore), Set.of("state.restore"), 30).thenApply(frame -> NetworkTransferCodec.decodeTransfer(frame.payload())));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Void> reconcilePlayerState(String networkId, NetworkStateReconciliationRequest request) {
        try {
            return runtimeRequest(networkId, session -> session.request(NetworkFrameType.STATE_RECONCILE, NetworkChannels.STATE, NetworkStateReconciliationCodec.encodeRequest(request), Set.of("state.restore"), 610).thenApply(frame -> null));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private <T> CompletableFuture<T> runtimeRequest(String networkId, Function<Session, CompletableFuture<T>> operation) {
        String normalizedNetworkId = networkId == null ? "" : networkId.trim();
        if (closed.get() || normalizedNetworkId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("ReSync Runtime Is Not Available"));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                Session session = sessions.get(normalizedNetworkId);
                if (session == null || !session.authorized()) {
                    result.completeExceptionally(new IllegalStateException("ReSync Runtime Is Not Connected"));
                    return;
                }
                try {
                    operation.apply(session).whenComplete((value, throwable) -> {
                        if (throwable != null) {
                            result.completeExceptionally(throwable);
                        } else {
                            result.complete(value);
                        }
                    });
                } catch (RuntimeException exception) {
                    result.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    private CompletableFuture<Void> proxyAction(String networkId, NetworkProxyAction action) {
        String normalizedNetworkId = networkId == null ? "" : networkId.trim();
        if (closed.get() || normalizedNetworkId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("ReSync Runtime Is Not Available"));
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                Session session = sessions.get(normalizedNetworkId);
                if (session == null || !session.authorized()) {
                    result.completeExceptionally(new IllegalStateException("ReSync Runtime Is Not Connected"));
                    return;
                }
                session.proxyAction(action, result);
            });
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    private void reconcile(ListSnapshot source) {
        if (closed.get()) {
            return;
        }
        Map<String, Instance> instancesById = new LinkedHashMap<>();
        source.instances().forEach(instance -> instancesById.put(instance.getInstanceId(), instance));
        Map<String, Target> nextTargets = new LinkedHashMap<>();
        for (NetworkDefinition network : source.networks()) {
            NetworkRuntimePolicy runtime = network.runtime();
            if (!runtime.enabled()) {
                publish(NetworkRuntimeSnapshot.disabled(network.networkId()));
                continue;
            }
            if (!runtime.transportReady()) {
                publish(state(network.networkId(), NetworkRuntimeConnectionState.UNAVAILABLE, "Secure Runtime Transport Pending", false));
                continue;
            }
            NetworkMember proxy = network.proxyMember();
            Instance proxyInstance = proxy == null ? null : instancesById.get(proxy.instanceId());
            List<NetworkRoute> routes = network.members().stream().filter(member -> !member.isProxy()).map(member -> new NetworkRoute(member.nodeId(), member.routeName(), member.address(), member.port())).toList();
            List<NetworkRoutingGroup> routingGroups = network.routingGroups().stream().map(group -> new NetworkRoutingGroup(group.id(), group.name(), NetworkRoutingStrategy.valueOf(group.strategy().name()), group.nodeIds(), group.weights(), group.fallbackGroupId(), group.forcedHosts(), group.permission())).toList();
            String maintenanceRoute = network.routingGroups().stream().filter(group -> group.id().equals("fallback")).flatMap(group -> group.nodeIds().stream()).map(nodeId -> routes.stream().filter(route -> route.nodeId().equals(nodeId)).map(NetworkRoute::routeName).findFirst().orElse("")).filter(route -> !route.isBlank()).findFirst().orElse(routes.isEmpty() ? "" : routes.getFirst().routeName());
            Target target = new Target(network.networkId(), network.revision(), proxy == null ? "" : proxy.nodeId(), NetworkRuntimeIdentity.operatorNodeId(network.networkId()), runtime, proxyInstance, maintenanceRoute, routes, routingGroups);
            nextTargets.put(network.networkId(), target);
        }
        targets.clear();
        targets.putAll(nextTargets);
        sessions.forEach((networkId, session) -> {
            Target target = nextTargets.get(networkId);
            if (target == null || !session.matches(target)) {
                if (sessions.remove(networkId, session)) {
                    session.close();
                }
            }
        });
        snapshots.keySet().removeIf(networkId -> source.networks().stream().noneMatch(network -> network.networkId().equals(networkId)));
        nextAttempts.keySet().removeIf(networkId -> !nextTargets.containsKey(networkId));
        nextTargets.values().forEach(this::ensureConnected);
    }

    private void tick() {
        if (closed.get()) {
            return;
        }
        targets.values().forEach(this::ensureConnected);
        sessions.values().forEach(Session::heartbeat);
    }

    private void ensureConnected(Target target) {
        if (closed.get() || System.currentTimeMillis() < nextAttempts.getOrDefault(target.networkId(), 0L)) {
            return;
        }
        Session current = sessions.get(target.networkId());
        if (current != null && current.matches(target) && !current.closed()) {
            if (!current.connectionTimedOut()) {
                return;
            }
            if (sessions.remove(target.networkId(), current)) {
                current.close();
            }
            nextAttempts.put(target.networkId(), System.currentTimeMillis() + RETRY_DELAY_MILLIS);
            publish(state(target.networkId(), NetworkRuntimeConnectionState.RECONNECTING, "Runtime Connection Timed Out", false));
            return;
        }
        if (current != null && sessions.remove(target.networkId(), current)) {
            current.close();
        }
        publish(state(target.networkId(), NetworkRuntimeConnectionState.CONNECTING, "Connecting ReSync Runtime", false));
        Session session = null;
        try {
            session = open(target);
            sessions.put(target.networkId(), session);
            session.connect();
        } catch (Exception exception) {
            if (session != null && sessions.remove(target.networkId(), session)) {
                session.close();
            }
            nextAttempts.put(target.networkId(), System.currentTimeMillis() + RETRY_DELAY_MILLIS);
            publish(state(target.networkId(), NetworkRuntimeConnectionState.UNAVAILABLE, rootMessage(exception), false));
        }
    }

    private Session open(Target target) throws IOException {
        if (target.proxyInstance() == null) {
            throw new IllegalStateException("Proxy Instance Unavailable");
        }
        SSHManager.ManagedLocalForward forward = null;
        URI endpoint;
        if (target.runtime().security() == NetworkTransportSecurity.LOOPBACK) {
            ServerBackend backend = target.proxyInstance().getBackend();
            if (backend instanceof SshBackend sshBackend) {
                forward = sshBackend.getSshManager().openLocalForward(target.runtime().hubAddress(), target.runtime().hubPort());
                endpoint = URI.create("ws://127.0.0.1:" + forward.localPort());
            } else if (backend instanceof LocalBackend) {
                endpoint = URI.create(target.runtime().hubUrl());
            } else {
                throw new IllegalStateException("Proxy Runtime Tunnel Is Unavailable");
            }
        } else {
            endpoint = URI.create(target.runtime().hubUrl());
        }
        try {
            return new Session(target, endpoint, forward);
        } catch (RuntimeException exception) {
            closeForward(forward);
            throw exception;
        }
    }

    private NetworkRuntimeSnapshot state(String networkId, NetworkRuntimeConnectionState state, String message, boolean clearNodes) {
        return snapshots.getOrDefault(networkId, NetworkRuntimeSnapshot.disabled(networkId)).connection(state, message, clearNodes);
    }

    private void publish(NetworkRuntimeSnapshot snapshot) {
        snapshots.put(snapshot.networkId(), snapshot);
        for (Consumer<NetworkRuntimeSnapshot> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void disconnected(Session session, String reason) {
        if (!sessions.remove(session.target().networkId(), session) || session.closed() || closed.get()) {
            return;
        }
        session.finishDisconnect();
        if ("Network Credential Rejected".equals(reason)) {
            secretStore.deleteRuntimeCredential(session.target().networkId(), session.target().operatorNodeId());
        }
        nextAttempts.put(session.target().networkId(), System.currentTimeMillis() + RETRY_DELAY_MILLIS);
        publish(state(session.target().networkId(), NetworkRuntimeConnectionState.RECONNECTING, reason == null || reason.isBlank() ? "Reconnecting ReSync Runtime" : reason, false));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void closeForward(SSHManager.ManagedLocalForward forward) {
        if (forward == null) {
            return;
        }
        try {
            forward.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.shutdownNow();
        sessions.values().forEach(Session::close);
        sessions.clear();
        targets.clear();
        nextAttempts.clear();
    }

    private final class Session implements AutoCloseable {
        private final Target target;
        private final SSHManager.ManagedLocalForward forward;
        private final Client client;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicLong requestIds = new AtomicLong();
        private final Map<String, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<NetworkFrame>> responses = new ConcurrentHashMap<>();
        private final long createdAt = System.currentTimeMillis();
        private volatile boolean authorized;
        private volatile long lastHeartbeat;

        private Session(Target target, URI endpoint, SSHManager.ManagedLocalForward forward) {
            this.target = target;
            this.forward = forward;
            String credential = secretStore.resolveRuntimeCredential(target.networkId(), target.operatorNodeId());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-ReSync-Network", target.networkId());
            headers.put("X-ReSync-Node", target.operatorNodeId());
            if (credential.isBlank()) {
                headers.put("X-ReSync-Enrollment", secretStore.getOrCreateEnrollmentToken(target.networkId(), target.operatorNodeId()));
            } else {
                headers.put("X-ReSync-Credential", credential);
            }
            this.client = new Client(endpoint, headers);
            this.client.setConnectionLostTimeout(15);
        }

        private void connect() {
            client.connect();
        }

        private boolean matches(Target candidate) {
            return target.networkId().equals(candidate.networkId()) && target.revision() == candidate.revision() && target.runtime().equals(candidate.runtime()) && target.proxyInstance() == candidate.proxyInstance();
        }

        private boolean closed() {
            return closed.get();
        }

        private boolean authorized() {
            return authorized && !closed.get() && client.isOpen();
        }

        private boolean connectionTimedOut() {
            return !authorized && System.currentTimeMillis() - createdAt >= CONNECT_TIMEOUT_MILLIS;
        }

        private Target target() {
            return target;
        }

        private void handle(NetworkFrame frame) {
            if (!frame.context().networkId().equals(target.networkId()) || !frame.context().nodeId().equals(target.hubNodeId())) {
                throw new SecurityException("Network Hub Identity Does Not Match");
            }
            if (frame.type() == NetworkFrameType.ENROLL_ACK) {
                String credential = new String(frame.payload(), StandardCharsets.UTF_8).trim();
                secretStore.saveRuntimeCredential(target.networkId(), target.operatorNodeId(), credential);
                authorize();
                return;
            }
            if (frame.type() == NetworkFrameType.RESPONSE && frame.context().requestId().equals("session")) {
                authorize();
                return;
            }
            CompletableFuture<NetworkFrame> response = frame.type() == NetworkFrameType.ERROR ? null : responses.remove(frame.context().requestId());
            if (response != null) {
                response.complete(frame);
                return;
            }
            if (frame.type() == NetworkFrameType.RESPONSE) {
                CompletableFuture<Void> request = pending.remove(frame.context().requestId());
                if (request != null) {
                    request.complete(null);
                    return;
                }
            }
            if (frame.type() == NetworkFrameType.RESPONSE && frame.context().requestId().equals("routes-" + target.revision())) {
                publish(state(target.networkId(), NetworkRuntimeConnectionState.CONNECTED, "Runtime Routes Ready", false));
                return;
            }
            if ((frame.type() == NetworkFrameType.PRESENCE_SNAPSHOT || frame.type() == NetworkFrameType.PRESENCE_DELTA) && frame.channel().equals(NetworkChannels.PRESENCE)) {
                NetworkNodePresence presence = NetworkNodePresenceCodec.decode(target.networkId(), frame.payload());
                NetworkRuntimeSnapshot current = snapshots.getOrDefault(target.networkId(), NetworkRuntimeSnapshot.disabled(target.networkId()));
                publish(current.presence(presence));
                return;
            }
            if (frame.type() == NetworkFrameType.EVENT_DELIVERY && frame.channel().equals(NetworkChannels.EVENTS)) {
                NetworkEvent event = NetworkEventCodec.decodeEvent(frame.payload());
                if (!event.networkId().equals(target.networkId())) {
                    throw new SecurityException("Network Event Identity Does Not Match");
                }
                for (Consumer<NetworkEvent> listener : eventListeners) {
                    listener.accept(event);
                }
                acknowledgeEvent(event.eventId());
                return;
            }
            if (frame.type() == NetworkFrameType.ERROR) {
                String message = new String(frame.payload(), StandardCharsets.UTF_8);
                CompletableFuture<Void> request = pending.remove(frame.context().requestId());
                if (request != null) {
                    request.completeExceptionally(new IllegalStateException(message));
                }
                CompletableFuture<NetworkFrame> typed = responses.remove(frame.context().requestId());
                if (typed != null) {
                    typed.completeExceptionally(new IllegalStateException(message));
                }
                publish(state(target.networkId(), NetworkRuntimeConnectionState.CONNECTED, "Runtime Operation Failed • " + message, false));
            }
        }

        private void authorize() {
            authorized = true;
            lastHeartbeat = 0;
            publish(state(target.networkId(), NetworkRuntimeConnectionState.CONNECTED, "ReSync Runtime Connected", true));
            reconcileRoutes();
            heartbeat();
        }

        private void reconcileRoutes() {
            String requestId = "routes-" + target.revision();
            NetworkRequestContext context = new NetworkRequestContext(PROTOCOL_VERSION, target.networkId(), target.operatorNodeId(), requestId, Instant.now().plusSeconds(10).toEpochMilli(), Set.of("routes.write"));
            byte[] payload = NetworkRouteSetCodec.encode(new NetworkRouteSet(target.revision(), target.maintenanceRoute(), target.routes(), target.routingGroups()));
            client.send(codec.encode(new NetworkFrame(context, NetworkChannels.ROUTING, NetworkFrameType.ROUTE_RECONCILE, payload)));
        }

        private void setNodeMode(NetworkNodeMode mode, CompletableFuture<Void> result) {
            String requestId = "mode-" + requestIds.incrementAndGet();
            NetworkRequestContext context = new NetworkRequestContext(PROTOCOL_VERSION, target.networkId(), target.operatorNodeId(), requestId, Instant.now().plusSeconds(10).toEpochMilli(), Set.of("nodes.manage"));
            pending.put(requestId, result);
            try {
                client.send(codec.encode(new NetworkFrame(context, NetworkChannels.CONTROL, NetworkFrameType.NODE_MODE_SET, NetworkNodeModeCodec.encode(mode))));
                executor.schedule(() -> timeout(requestId), 10, TimeUnit.SECONDS);
            } catch (RuntimeException exception) {
                pending.remove(requestId, result);
                result.completeExceptionally(exception);
            }
        }

        private void proxyAction(NetworkProxyAction action, CompletableFuture<Void> result) {
            String requestId = "action-" + requestIds.incrementAndGet();
            String scope = action.type() == NetworkProxyActionType.COMMAND ? "proxy.command" : "proxy.broadcast";
            NetworkRequestContext context = new NetworkRequestContext(PROTOCOL_VERSION, target.networkId(), target.operatorNodeId(), requestId, Instant.now().plusSeconds(10).toEpochMilli(), Set.of(scope));
            pending.put(requestId, result);
            try {
                client.send(codec.encode(new NetworkFrame(context, NetworkChannels.CONTROL, NetworkFrameType.PROXY_ACTION, NetworkProxyActionCodec.encode(action))));
                executor.schedule(() -> timeout(requestId), 10, TimeUnit.SECONDS);
            } catch (RuntimeException exception) {
                pending.remove(requestId, result);
                result.completeExceptionally(exception);
            }
        }

        private CompletableFuture<NetworkFrame> request(NetworkFrameType type, String channel, byte[] payload, Set<String> scopes, int timeoutSeconds) {
            String requestId = "request-" + requestIds.incrementAndGet();
            NetworkRequestContext context = new NetworkRequestContext(PROTOCOL_VERSION, target.networkId(), target.operatorNodeId(), requestId, Instant.now().plusSeconds(timeoutSeconds).toEpochMilli(), scopes);
            CompletableFuture<NetworkFrame> result = new CompletableFuture<>();
            responses.put(requestId, result);
            try {
                client.send(codec.encode(new NetworkFrame(context, channel, type, payload)));
                executor.schedule(() -> timeout(requestId), timeoutSeconds, TimeUnit.SECONDS);
            } catch (RuntimeException exception) {
                responses.remove(requestId, result);
                result.completeExceptionally(exception);
            }
            return result;
        }

        private void acknowledgeEvent(String eventId) {
            String requestId = "event-ack-" + eventId;
            NetworkRequestContext context = new NetworkRequestContext(PROTOCOL_VERSION, target.networkId(), target.operatorNodeId(), requestId, Instant.now().plusSeconds(10).toEpochMilli(), Set.of("events.consume"));
            client.send(codec.encode(new NetworkFrame(context, NetworkChannels.EVENTS, NetworkFrameType.EVENT_ACK, NetworkEventCodec.encodeAcknowledgement(eventId))));
        }

        private void timeout(String requestId) {
            CompletableFuture<Void> request = pending.remove(requestId);
            if (request != null) {
                request.completeExceptionally(new IllegalStateException("Runtime Operation Timed Out"));
            }
            CompletableFuture<NetworkFrame> response = responses.remove(requestId);
            if (response != null) {
                response.completeExceptionally(new IllegalStateException("Runtime Operation Timed Out"));
            }
        }

        private void heartbeat() {
            long now = System.currentTimeMillis();
            if (!authorized || closed.get() || !client.isOpen() || now - lastHeartbeat < HEARTBEAT_INTERVAL_MILLIS) {
                return;
            }
            lastHeartbeat = now;
            String requestId = target.operatorNodeId() + "-" + requestIds.incrementAndGet();
            NetworkRequestContext context = new NetworkRequestContext(PROTOCOL_VERSION, target.networkId(), target.operatorNodeId(), requestId, Instant.now().plusSeconds(10).toEpochMilli(), Set.of("node.heartbeat", "presence.read"));
            client.send(codec.encode(new NetworkFrame(context, NetworkChannels.CONTROL, NetworkFrameType.HEARTBEAT, new byte[0])));
        }

        private void closeForward() {
            NetworkRuntimeMonitor.this.closeForward(forward);
        }

        private void finishDisconnect() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            authorized = false;
            failPending("ReSync Runtime Disconnected");
            if (client.isOpen()) {
                client.close();
            }
            closeForward();
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            authorized = false;
            failPending("ReSync Runtime Closed");
            client.close();
            closeForward();
        }

        private void failPending(String message) {
            pending.values().forEach(request -> request.completeExceptionally(new IllegalStateException(message)));
            pending.clear();
            responses.values().forEach(request -> request.completeExceptionally(new IllegalStateException(message)));
            responses.clear();
        }

        private final class Client extends WebSocketClient {
            private Client(URI endpoint, Map<String, String> headers) {
                super(endpoint, headers);
            }

            @Override
            public void onOpen(ServerHandshake handshake) {
                authorized = false;
                publish(state(target.networkId(), NetworkRuntimeConnectionState.CONNECTING, "Authenticating ReSync Runtime", false));
            }

            @Override
            public void onMessage(String message) {
                close(1003, "Binary Network Frames Required");
            }

            @Override
            public void onMessage(ByteBuffer message) {
                byte[] encoded = new byte[message.remaining()];
                message.get(encoded);
                try {
                    handle(codec.decode(encoded));
                } catch (RuntimeException exception) {
                    close(1008, rootMessage(exception));
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                authorized = false;
                disconnected(Session.this, reason);
            }

            @Override
            public void onError(Exception exception) {
                if (!Session.this.closed.get() && !isOpen()) {
                    disconnected(Session.this, rootMessage(exception));
                }
            }
        }
    }

    private record Target(String networkId, long revision, String hubNodeId, String operatorNodeId, NetworkRuntimePolicy runtime, Instance proxyInstance, String maintenanceRoute, List<NetworkRoute> routes, List<NetworkRoutingGroup> routingGroups) {
        private Target {
            maintenanceRoute = maintenanceRoute == null ? "" : maintenanceRoute;
            routes = List.copyOf(routes);
            routingGroups = List.copyOf(routingGroups);
        }
    }

    private record ListSnapshot(List<NetworkDefinition> networks, List<Instance> instances) {
        private ListSnapshot(Collection<NetworkDefinition> networks, Collection<Instance> instances) {
            this(networks == null ? List.of() : networks.stream().toList(), instances == null ? List.of() : instances.stream().toList());
        }
    }
}
