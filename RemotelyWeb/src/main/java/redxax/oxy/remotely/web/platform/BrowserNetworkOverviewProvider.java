package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.ForwardingMode;
import redxax.oxy.remotely.network.NetworkConfigDocumentKey;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkDesiredState;
import redxax.oxy.remotely.network.NetworkDiscoveryResult;
import redxax.oxy.remotely.network.NetworkEntryPoint;
import redxax.oxy.remotely.network.NetworkForwardingPolicy;
import redxax.oxy.remotely.network.NetworkIncident;
import redxax.oxy.remotely.network.NetworkIncidentSeverity;
import redxax.oxy.remotely.network.NetworkIncidentSource;
import redxax.oxy.remotely.network.NetworkIncidentStatus;
import redxax.oxy.remotely.network.NetworkJob;
import redxax.oxy.remotely.network.NetworkJobDocument;
import redxax.oxy.remotely.network.NetworkJobDocumentState;
import redxax.oxy.remotely.network.NetworkJobStatus;
import redxax.oxy.remotely.network.NetworkJobType;
import redxax.oxy.remotely.network.NetworkLifecycleAction;
import redxax.oxy.remotely.network.NetworkLifecycleJob;
import redxax.oxy.remotely.network.NetworkLifecycleOperation;
import redxax.oxy.remotely.network.NetworkLifecycleStatus;
import redxax.oxy.remotely.network.NetworkLifecycleStep;
import redxax.oxy.remotely.network.NetworkLifecycleStepStatus;
import redxax.oxy.remotely.network.NetworkMember;
import redxax.oxy.remotely.network.NetworkMemberManagement;
import redxax.oxy.remotely.network.NetworkMemberRole;
import redxax.oxy.remotely.network.NetworkObservationState;
import redxax.oxy.remotely.network.NetworkPathSync;
import redxax.oxy.remotely.network.NetworkPreflightReport;
import redxax.oxy.remotely.network.NetworkPreflightCheck;
import redxax.oxy.remotely.network.NetworkPreflightCheckStatus;
import redxax.oxy.remotely.network.NetworkPreflightStatus;
import redxax.oxy.remotely.network.NetworkRuntimeConnectionState;
import redxax.oxy.remotely.network.NetworkRuntimeNodePresence;
import redxax.oxy.remotely.network.NetworkRuntimeNodeStatus;
import redxax.oxy.remotely.network.NetworkRuntimePolicy;
import redxax.oxy.remotely.network.NetworkRuntimeSnapshot;
import redxax.oxy.remotely.network.NetworkSharedDataPolicy;
import redxax.oxy.remotely.network.NetworkTransportSecurity;
import redxax.oxy.remotely.network.NetworkValidationIssue;
import redxax.oxy.remotely.network.NetworkMemberObservation;
import redxax.oxy.remotely.network.PortReservation;
import redxax.oxy.remotely.network.RoutingGroup;
import redxax.oxy.remotely.network.RoutingStrategy;
import redxax.oxy.remotely.network.SyncDataFamily;
import redxax.oxy.remotely.network.SyncLocationPolicy;
import redxax.oxy.remotely.network.SyncRealm;
import redxax.oxy.remotely.ui.server.NetworkOverviewProvider;
import redxax.oxy.remotely.ui.server.ServerScreenHost;
import redxax.oxy.remotely.util.TaskSchedulers;
import restudio.rescreen.platform.Async;
import restudio.rescreen.platform.TaskScheduler;
import restudio.rescreen.ui.core.Screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class BrowserNetworkOverviewProvider implements NetworkOverviewProvider {
    private static final Duration POLL_INITIAL_DELAY = Duration.ofSeconds(1);
    private static final Duration POLL_PERIOD = Duration.ofSeconds(2);
    private static final Map<Enum<?>, Map<String, ? extends Enum<?>>> ENUM_VALUES = Map.ofEntries(
            Map.entry(ForwardingMode.MODERN, enumValues(ForwardingMode.MODERN, ForwardingMode.BUNGEEGUARD, ForwardingMode.LEGACY, ForwardingMode.NONE)),
            Map.entry(NetworkDesiredState.STOPPED, enumValues(NetworkDesiredState.STOPPED, NetworkDesiredState.RUNNING, NetworkDesiredState.MAINTENANCE)),
            Map.entry(NetworkMemberRole.CUSTOM, enumValues(NetworkMemberRole.PROXY, NetworkMemberRole.LOBBY, NetworkMemberRole.FALLBACK,
                    NetworkMemberRole.GAMEPLAY, NetworkMemberRole.RESTRICTED, NetworkMemberRole.MAINTENANCE, NetworkMemberRole.CUSTOM)),
            Map.entry(NetworkMemberManagement.MANAGED, enumValues(NetworkMemberManagement.MANAGED, NetworkMemberManagement.EXTERNAL)),
            Map.entry(RoutingStrategy.ORDERED, enumValues(RoutingStrategy.ORDERED, RoutingStrategy.LEAST_PLAYERS, RoutingStrategy.WEIGHTED)),
            Map.entry(SyncDataFamily.PRESENCE, enumValues(SyncDataFamily.PRESENCE, SyncDataFamily.INVENTORY, SyncDataFamily.ENDER_CHEST,
                    SyncDataFamily.EXPERIENCE, SyncDataFamily.VITALS, SyncDataFamily.EFFECTS, SyncDataFamily.PLAYER_STATE,
                    SyncDataFamily.ADVANCEMENTS, SyncDataFamily.RECIPES, SyncDataFamily.STATISTICS, SyncDataFamily.LOCATION,
                    SyncDataFamily.PERSISTENT_DATA)),
            Map.entry(SyncLocationPolicy.NEVER, enumValues(SyncLocationPolicy.NEVER, SyncLocationPolicy.SAME_SERVER_ONLY,
                    SyncLocationPolicy.REALM_RETURN_POINT, SyncLocationPolicy.EXACT_COMPATIBLE_WORLD)),
            Map.entry(NetworkTransportSecurity.LOOPBACK, enumValues(NetworkTransportSecurity.LOOPBACK, NetworkTransportSecurity.WSS)),
            Map.entry(NetworkSharedDataPolicy.SelectionMode.ALL, enumValues(NetworkSharedDataPolicy.SelectionMode.ALL,
                    NetworkSharedDataPolicy.SelectionMode.ALLOW_LIST, NetworkSharedDataPolicy.SelectionMode.DENY_LIST)),
            Map.entry(NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS, enumValues(NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS,
                    NetworkSharedDataPolicy.ConflictPolicy.LOCAL_WINS)),
            Map.entry(NetworkObservationState.UNKNOWN, enumValues(NetworkObservationState.UNKNOWN, NetworkObservationState.HEALTHY,
                    NetworkObservationState.DEGRADED, NetworkObservationState.INSECURE, NetworkObservationState.UNREACHABLE,
                    NetworkObservationState.DRIFTED)),
            Map.entry(NetworkValidationIssue.Severity.INFO, enumValues(NetworkValidationIssue.Severity.INFO,
                    NetworkValidationIssue.Severity.WARNING, NetworkValidationIssue.Severity.ERROR)),
            Map.entry(NetworkRuntimeNodeStatus.OFFLINE, enumValues(NetworkRuntimeNodeStatus.ONLINE, NetworkRuntimeNodeStatus.DRAINING,
                    NetworkRuntimeNodeStatus.MAINTENANCE, NetworkRuntimeNodeStatus.OFFLINE, NetworkRuntimeNodeStatus.REVOKED)),
            Map.entry(NetworkRuntimeConnectionState.DISABLED, enumValues(NetworkRuntimeConnectionState.DISABLED,
                    NetworkRuntimeConnectionState.CONNECTING, NetworkRuntimeConnectionState.CONNECTED,
                    NetworkRuntimeConnectionState.RECONNECTING, NetworkRuntimeConnectionState.UNAVAILABLE)),
            Map.entry(NetworkIncidentStatus.OPEN, enumValues(NetworkIncidentStatus.OPEN, NetworkIncidentStatus.RESOLVED)),
            Map.entry(NetworkIncidentSource.RUNTIME, enumValues(NetworkIncidentSource.RUNTIME, NetworkIncidentSource.DISCOVERY,
                    NetworkIncidentSource.EVENT)),
            Map.entry(NetworkIncidentSeverity.WARNING, enumValues(NetworkIncidentSeverity.INFO, NetworkIncidentSeverity.WARNING,
                    NetworkIncidentSeverity.CRITICAL)),
            Map.entry(NetworkLifecycleAction.START, enumValues(NetworkLifecycleAction.START, NetworkLifecycleAction.STOP,
                    NetworkLifecycleAction.DRAIN, NetworkLifecycleAction.CAPACITY_GATE, NetworkLifecycleAction.MAINTENANCE,
                    NetworkLifecycleAction.HEALTH_GATE, NetworkLifecycleAction.RESUME)),
            Map.entry(NetworkLifecycleStepStatus.PENDING, enumValues(NetworkLifecycleStepStatus.PENDING, NetworkLifecycleStepStatus.RUNNING,
                    NetworkLifecycleStepStatus.SUCCEEDED, NetworkLifecycleStepStatus.SKIPPED, NetworkLifecycleStepStatus.FAILED)),
            Map.entry(NetworkLifecycleOperation.START, enumValues(NetworkLifecycleOperation.START, NetworkLifecycleOperation.STOP,
                    NetworkLifecycleOperation.RESTART, NetworkLifecycleOperation.ROLLING_RESTART, NetworkLifecycleOperation.DRAIN)),
            Map.entry(NetworkLifecycleStatus.READY, enumValues(NetworkLifecycleStatus.READY, NetworkLifecycleStatus.RUNNING,
                    NetworkLifecycleStatus.INTERRUPTED, NetworkLifecycleStatus.SUCCEEDED, NetworkLifecycleStatus.FAILED)),
            Map.entry(NetworkJobDocumentState.PENDING, enumValues(NetworkJobDocumentState.PENDING, NetworkJobDocumentState.UNCHANGED,
                    NetworkJobDocumentState.APPLIED, NetworkJobDocumentState.ROLLED_BACK)),
            Map.entry(NetworkJobType.RECONCILE, enumValues(NetworkJobType.QUICK_CREATE, NetworkJobType.RECONCILE, NetworkJobType.ATTACH,
                    NetworkJobType.ROUTING, NetworkJobType.REALMS, NetworkJobType.ROTATE_SECRET, NetworkJobType.DETACH,
                    NetworkJobType.DELETE, NetworkJobType.ADOPT, NetworkJobType.LIFECYCLE)),
            Map.entry(NetworkJobStatus.PLANNING, enumValues(NetworkJobStatus.PLANNING, NetworkJobStatus.READY, NetworkJobStatus.RUNNING,
                    NetworkJobStatus.INTERRUPTED, NetworkJobStatus.ROLLING_BACK, NetworkJobStatus.SUCCEEDED,
                    NetworkJobStatus.ROLLED_BACK, NetworkJobStatus.FAILED, NetworkJobStatus.BLOCKED)),
            Map.entry(NetworkPreflightCheckStatus.FAILED, enumValues(NetworkPreflightCheckStatus.PASSED, NetworkPreflightCheckStatus.WARNING,
                    NetworkPreflightCheckStatus.FAILED)),
            Map.entry(NetworkPreflightStatus.RUNNING, enumValues(NetworkPreflightStatus.RUNNING, NetworkPreflightStatus.SUCCEEDED,
                    NetworkPreflightStatus.FAILED)));
    private final BrowserRemotelyServerApi api;
    private final RemotelyClient client;
    private final List<Consumer<OverviewState>> listeners = new ArrayList<>();
    private final List<Consumer<NetworkRuntimeSnapshot>> runtimeListeners = new ArrayList<>();
    private volatile String loadedNetworkId = "";
    private volatile OverviewState loadedState;
    private TaskScheduler.ScheduledTask pollingTask;
    private boolean refreshInFlight;

    BrowserNetworkOverviewProvider(BrowserRemotelyServerApi api, RemotelyClient client) {
        this.api = api;
        this.client = client;
    }

    @Override
    public Async<OverviewState> load(String networkId) {
        return api.networkRequest("GET", "/networks/" + BrowserRemotelyServerApi.pathValue(networkId), null)
                .thenApply(value -> {
                    OverviewState result = state(value);
                    loadedNetworkId = result.network() == null || result.network().networkId().isBlank()
                            ? networkId : result.network().networkId();
                    loadedState = result;
                    return result;
                });
    }

    @Override
    public void addListener(Consumer<OverviewState> listener) {
        if (listener == null || listeners.contains(listener)) {
            return;
        }
        listeners.add(listener);
        ensurePolling();
    }

    @Override
    public void removeListener(Consumer<OverviewState> listener) {
        listeners.remove(listener);
        stopPollingIfUnused();
    }

    @Override
    public void addRuntimeListener(Consumer<NetworkRuntimeSnapshot> listener) {
        if (listener == null || runtimeListeners.contains(listener)) {
            return;
        }
        runtimeListeners.add(listener);
        ensurePolling();
    }

    @Override
    public void removeRuntimeListener(Consumer<NetworkRuntimeSnapshot> listener) {
        runtimeListeners.remove(listener);
        stopPollingIfUnused();
    }

    private void ensurePolling() {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            return;
        }
        pollingTask = TaskSchedulers.current().scheduleAtFixedRate(this::poll,
                POLL_INITIAL_DELAY, POLL_PERIOD);
    }

    private void stopPollingIfUnused() {
        if (!listeners.isEmpty() || !runtimeListeners.isEmpty()) {
            return;
        }
        TaskScheduler.ScheduledTask task = pollingTask;
        pollingTask = null;
        if (task != null) {
            task.cancel();
        }
        refreshInFlight = false;
    }

    private void poll() {
        if (refreshInFlight || (listeners.isEmpty() && runtimeListeners.isEmpty())) {
            return;
        }
        String networkId = loadedNetworkId;
        if (networkId == null || networkId.isBlank()) {
            return;
        }
        refreshInFlight = true;
        OverviewState previous = loadedState;
        load(networkId).whenComplete((current, failure) -> {
            refreshInFlight = false;
            if (failure != null || current == null || !networkId.equals(loadedNetworkId)) {
                return;
            }
            if (networkChanged(previous, current)) {
                for (Consumer<OverviewState> listener : List.copyOf(listeners)) {
                    listener.accept(current);
                }
            }
            if (runtimeChanged(previous, current) && current.runtime() != null) {
                for (Consumer<NetworkRuntimeSnapshot> listener : List.copyOf(runtimeListeners)) {
                    listener.accept(current.runtime());
                }
            }
        });
    }

    private boolean networkChanged(OverviewState previous, OverviewState current) {
        return previous == null
                || !Objects.equals(previous.network(), current.network())
                || !Objects.equals(previous.servers(), current.servers())
                || !Objects.equals(previous.discovery(), current.discovery())
                || !Objects.equals(previous.incidents(), current.incidents())
                || !Objects.equals(previous.lifecycleJobs(), current.lifecycleJobs())
                || !Objects.equals(previous.jobs(), current.jobs())
                || !Objects.equals(previous.transferFailureHeat(), current.transferFailureHeat())
                || !Objects.equals(previous.reSyncInstalled(), current.reSyncInstalled())
                || !Objects.equals(previous.capabilities(), current.capabilities());
    }

    private boolean runtimeChanged(OverviewState previous, OverviewState current) {
        return previous == null || !Objects.equals(previous.runtime(), current.runtime());
    }

    @Override
    public Async<OverviewState> loadForServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return Async.failed(new IllegalArgumentException("Server Identifier Is Unavailable"));
        }
        return api.getNetworks().thenCompose(networks -> networks == null ? Async.failed(new UnsupportedOperationException("Network Is Unavailable"))
                : networks.stream().filter(network -> network != null && network.members().contains(serverId)).findFirst()
                .map(network -> load(network.id()))
                .orElseGet(() -> Async.failed(new UnsupportedOperationException("Network Is Unavailable"))));
    }

    @Override
    public Async<NetworkDefinition> save(String networkId, SaveRequest request) {
        return load(networkId).thenCompose(state -> requireCapability(state, "save")
                .thenCompose(ignored -> api.networkRequest("PUT", "/networks/" + BrowserRemotelyServerApi.pathValue(networkId),
                        withRevision(BrowserNetworkJson.save(request), state.network().revision()))))
                .thenApply(value -> definition(object(value, "network")));
    }

    @Override
    public Async<NetworkLifecycleJob> lifecycle(String networkId, NetworkLifecycleOperation operation) {
        return lifecycleRequest(networkId, "/lifecycle", Map.of("operation", enumName(operation, NetworkLifecycleOperation.START)))
                .thenApply(BrowserNetworkOverviewProvider::lifecycleJob);
    }

    @Override
    public Async<NetworkLifecycleJob> memberLifecycle(String networkId, String memberId,
                                                       NetworkLifecycleOperation operation) {
        return load(networkId).thenCompose(state -> requireCapability(state, "memberLifecycle")
                .thenCompose(ignored -> {
                    String instanceId = instanceId(state, memberId);
                    if (instanceId.isBlank()) return Async.failed(new IllegalArgumentException("Network Member Is Unavailable"));
                    return api.networkRequest("POST", networkPath(networkId, "/members/" + BrowserRemotelyServerApi.pathValue(instanceId) + "/lifecycle"),
                            withRevision(Map.of("operation", enumName(operation, NetworkLifecycleOperation.START)), state.network().revision()));
                })).thenApply(value -> lifecycleJob(object(value, "job")));
    }

    @Override
    public Async<NetworkJob> attach(String networkId, AttachRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serverId", request == null ? "" : request.serverId());
        body.put("joinRule", request == null ? "" : request.joinRule());
        body.put("installReSync", request != null && request.installReSync());
        return jobRequest(networkId, "/members", body);
    }

    @Override
    public Async<NetworkJob> attachExternal(String networkId, ExternalAttachRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (request != null) {
            body.put("name", request.name());
            body.put("address", request.address());
            body.put("port", request.port());
            body.put("capacity", request.capacity());
            body.put("joinRule", request.joinRule());
        }
        body.putIfAbsent("name", "");
        body.putIfAbsent("address", "");
        body.putIfAbsent("port", 0);
        body.putIfAbsent("capacity", 0);
        body.putIfAbsent("joinRule", "");
        return jobRequest(networkId, "/members/external", body);
    }

    @Override
    public Async<NetworkJob> detach(String networkId, String memberId) {
        return load(networkId).thenCompose(state -> requireCapability(state, "membership")
                .thenCompose(ignored -> {
                    String instanceId = instanceId(state, memberId);
                    if (instanceId.isBlank()) return Async.failed(new IllegalArgumentException("Network Member Is Unavailable"));
                    return api.networkRequest("POST", networkPath(networkId, "/members/" + BrowserRemotelyServerApi.pathValue(instanceId) + "/detach"),
                            withRevision(Map.of(), state.network().revision()));
                })).thenApply(value -> networkJob(object(value, "job")));
    }

    @Override
    public Async<NetworkJob> dissolve(String networkId) {
        return jobRequest(networkId, "/dissolve", Map.of());
    }

    @Override
    public Async<NetworkPreflightReport> preflight(String networkId) {
        return load(networkId).thenCompose(state -> requireCapability(state, "preflight")
                .thenCompose(ignored -> api.networkRequest("POST", networkPath(networkId, "/preflight"),
                        withRevision(Map.of(), state.network().revision()))))
                .thenApply(value -> preflight(object(value, "preflight")));
    }

    @Override
    public Async<NetworkJob> rotateSecret(String networkId) {
        return jobRequest(networkId, "/rotate-secret", Map.of());
    }

    @Override
    public Async<NetworkJob> reconcile(String networkId) {
        return jobRequest(networkId, "/reconcile", Map.of());
    }

    @Override
    public Async<NetworkJob> resumeJob(String networkId, String jobId) {
        return jobRequest(networkId, "/jobs/" + BrowserRemotelyServerApi.pathValue(jobId) + "/resume", Map.of());
    }

    @Override
    public Async<NetworkJob> rollbackJob(String networkId, String jobId) {
        return jobRequest(networkId, "/jobs/" + BrowserRemotelyServerApi.pathValue(jobId) + "/rollback", Map.of());
    }

    @Override
    public Async<NetworkLifecycleJob> resumeLifecycle(String networkId, String jobId) {
        return lifecycleRequest(networkId, "/lifecycle-jobs/" + BrowserRemotelyServerApi.pathValue(jobId) + "/resume", Map.of())
                .thenApply(BrowserNetworkOverviewProvider::lifecycleJob);
    }

    @Override
    public Async<Void> runtimeNodeMode(String networkId, String nodeId, NetworkRuntimeNodeStatus status) {
        return load(networkId).thenCompose(state -> requireCapability(state, "runtimeControl")
                .thenCompose(ignored -> api.networkRequest("POST", networkPath(networkId, "/runtime/nodes/" + BrowserRemotelyServerApi.pathValue(nodeId)),
                        withRevision(Map.of("status", enumName(status, NetworkRuntimeNodeStatus.OFFLINE)), state.network().revision())))).thenApply(ignored -> null);
    }

    @Override
    public Async<NetworkJob> installReSync(String networkId) {
        return jobRequest(networkId, "/resync", Map.of());
    }

    @Override
    public Async<Void> executeProxyCommand(String networkId, String command) {
        return load(networkId).thenCompose(state -> requireCapability(state, "command")
                .thenCompose(ignored -> api.networkRequest("POST", networkPath(networkId, "/command"),
                        withRevision(Map.of("command", command == null ? "" : command), state.network().revision()))))
                .thenApply(ignored -> null);
    }

    @Override
    public Async<Void> broadcastMessage(String networkId, String message) {
        return load(networkId).thenCompose(state -> requireCapability(state, "broadcast")
                .thenCompose(ignored -> api.networkRequest("POST", networkPath(networkId, "/broadcast"),
                        withRevision(Map.of("message", message == null ? "" : message), state.network().revision()))))
                .thenApply(ignored -> null);
    }

    @Override
    public Async<List<ServerView>> availableServers(String networkId) {
        return load(networkId).thenApply(value -> {
            Set<String> attached = value.network().members().stream().map(NetworkMember::instanceId).collect(Collectors.toSet());
            return value.servers().stream().filter(server -> !server.proxy() && !attached.contains(server.id())).toList();
        });
    }

    @Override
    public boolean available() {
        return BrowserLaunchSession.authenticated();
    }

    @Override
    public void openServer(Screen current, String serverId) {
        if (client == null || client.getHost() == null) throw new UnsupportedOperationException("Server Is Unavailable");
        ServerScreenHost host = client.getHost().serverScreenHost(client);
        if (host instanceof BrowserServerScreenHost browserHost) {
            browserHost.openNetworkServer(current, serverId);
            return;
        }
        throw new UnsupportedOperationException("Server Details Are Unavailable");
    }

    private Async<JsonObject> lifecycleRequest(String networkId, String suffix, Object body) {
        return load(networkId).thenCompose(state -> requireCapability(state, lifecycleCapability(suffix))
                .thenCompose(ignored -> api.networkRequest("POST", networkPath(networkId, suffix),
                        withRevision(body, state.network().revision()))))
                .thenApply(value -> object(value, "job"));
    }

    private Async<NetworkJob> jobRequest(String networkId, String suffix, Object body) {
        return load(networkId).thenCompose(state -> requireCapability(state, jobCapability(suffix))
                .thenCompose(ignored -> api.networkRequest("POST", networkPath(networkId, suffix),
                        withRevision(body, state.network().revision()))))
                .thenApply(value -> networkJob(object(value, "job")));
    }

    private static Async<Void> requireCapability(OverviewState state, String operation) {
        if (state == null || operation == null || operation.isBlank()) {
            return Async.completed(null);
        }
        NetworkCapability capability = state.capabilities().get(operation);
        if (capability != null && capability.supported()) {
            return Async.completed(null);
        }
        String reason = capability == null || capability.reason().isBlank() ? "Network Operation Is Unavailable" : capability.reason();
        return Async.failed(new UnsupportedOperationException(reason));
    }

    private static String instanceId(OverviewState state, String memberId) {
        if (state == null || state.network() == null || memberId == null || memberId.isBlank()) return "";
        return state.network().members().stream()
                .filter(member -> memberId.equals(member.instanceId()) || memberId.equals(member.nodeId()))
                .map(NetworkMember::instanceId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private static String lifecycleCapability(String suffix) {
        if (suffix != null && suffix.startsWith("/lifecycle-jobs/")) return "lifecycleRecovery";
        return suffix != null && suffix.startsWith("/members/") ? "memberLifecycle" : "lifecycle";
    }

    private static String jobCapability(String suffix) {
        if (suffix == null) return "job";
        if (suffix.equals("/members")) return "membership";
        if (suffix.equals("/members/external")) return "externalMembership";
        if (suffix.endsWith("/detach")) return "membership";
        if (suffix.equals("/dissolve")) return "dissolve";
        if (suffix.equals("/rotate-secret")) return "secretRotation";
        if (suffix.equals("/reconcile")) return "reconcile";
        if (suffix.endsWith("/resume") || suffix.endsWith("/rollback")) return "jobRecovery";
        if (suffix.equals("/resync")) return "resync";
        return "job";
    }

    private static Object withRevision(Object body, long revision) {
        if (body instanceof JsonObject value) {
            JsonObject result = value.deepCopy();
            result.addProperty("expectedRevision", revision);
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (body instanceof Map<?, ?> values) {
            values.forEach((key, value) -> {
                if (key instanceof String name) result.put(name, value);
            });
        }
        result.put("expectedRevision", revision);
        return result;
    }

    private String networkPath(String networkId, String suffix) {
        return "/networks/" + BrowserRemotelyServerApi.pathValue(networkId) + suffix;
    }

    private static OverviewState state(JsonObject value) {
        NetworkDefinition network = definition(object(value, "network"));
        List<ServerView> servers = BrowserNetworkJson.servers(value);
        NetworkDiscoveryResult discovery = discovery(object(value, "discovery"), network);
        NetworkRuntimeSnapshot runtime = runtime(object(value, "runtime"), network.networkId());
        List<NetworkIncident> incidents = BrowserNetworkJson.objects(value, "incidents").stream().map(item -> incident(item, network.networkId())).toList();
        List<NetworkLifecycleJob> lifecycleJobs = BrowserNetworkJson.objects(value, "lifecycleJobs").stream().map(BrowserNetworkOverviewProvider::lifecycleJob).toList();
        List<NetworkJob> jobs = BrowserNetworkJson.objects(value, "jobs").stream().map(BrowserNetworkOverviewProvider::networkJob).toList();
        return new OverviewState(network, servers, discovery, runtime, incidents, lifecycleJobs, jobs,
                BrowserNetworkJson.integerMap(value, "transferFailureHeat"), BrowserNetworkJson.booleanMap(value, "reSyncInstalled"),
                BrowserNetworkJson.capabilities(value));
    }

    private static NetworkDefinition definition(JsonObject value) {
        List<NetworkMember> members = BrowserNetworkJson.objects(value, "members").stream().map(BrowserNetworkOverviewProvider::member).toList();
        return new NetworkDefinition(
                BrowserNetworkJson.integer(value, "schemaVersion", 5),
                BrowserNetworkJson.string(value, "networkId"),
                BrowserNetworkJson.string(value, "name"),
                BrowserNetworkJson.longValue(value, "revision", 1),
                BrowserNetworkJson.string(value, "proxyInstanceId"),
                enumValue(BrowserNetworkJson.string(value, "desiredState"), NetworkDesiredState.STOPPED),
                forwarding(BrowserNetworkJson.object(value, "forwarding")),
                BrowserNetworkJson.objects(value, "entryPoints").stream().map(BrowserNetworkOverviewProvider::entryPoint).toList(),
                members,
                BrowserNetworkJson.objects(value, "routingGroups").stream().map(BrowserNetworkOverviewProvider::routingGroup).toList(),
                BrowserNetworkJson.objects(value, "syncRealms").stream().map(BrowserNetworkOverviewProvider::syncRealm).toList(),
                runtimePolicy(BrowserNetworkJson.object(value, "runtime")),
                BrowserNetworkJson.booleanMap(value, "features"),
                sharedDataPolicy(BrowserNetworkJson.object(value, "sharedDataPolicy")),
                BrowserNetworkJson.longValue(value, "createdAt", 0),
                BrowserNetworkJson.longValue(value, "updatedAt", 0));
    }

    private static NetworkMember member(JsonObject value) {
        return new NetworkMember(BrowserNetworkJson.string(value, "instanceId"), BrowserNetworkJson.string(value, "nodeId"),
                BrowserNetworkJson.string(value, "routeName"), enumValue(BrowserNetworkJson.string(value, "role"), NetworkMemberRole.CUSTOM),
                BrowserNetworkJson.string(value, "hostScope"), BrowserNetworkJson.string(value, "address"),
                BrowserNetworkJson.integer(value, "port", 0), BrowserNetworkJson.integer(value, "capacity", 0),
                BrowserNetworkJson.bool(value, "resyncEnabled", false),
                enumValue(BrowserNetworkJson.string(value, "management"), NetworkMemberManagement.MANAGED));
    }

    private static NetworkForwardingPolicy forwarding(JsonObject value) {
        return new NetworkForwardingPolicy(enumValue(BrowserNetworkJson.string(value, "mode"), ForwardingMode.MODERN),
                BrowserNetworkJson.bool(value, "proxyOnlineMode", true), BrowserNetworkJson.string(value, "secretReference"),
                BrowserNetworkJson.bool(value, "firewallVerified", false));
    }

    private static NetworkEntryPoint entryPoint(JsonObject value) {
        return new NetworkEntryPoint(BrowserNetworkJson.string(value, "id"), BrowserNetworkJson.string(value, "bindAddress"),
                BrowserNetworkJson.integer(value, "port", 0), new LinkedHashSet<>(BrowserNetworkJson.strings(value, "forcedHosts")));
    }

    private static RoutingGroup routingGroup(JsonObject value) {
        return new RoutingGroup(BrowserNetworkJson.string(value, "id"), BrowserNetworkJson.string(value, "name"),
                enumValue(BrowserNetworkJson.string(value, "strategy"), RoutingStrategy.ORDERED), BrowserNetworkJson.strings(value, "nodeIds"),
                BrowserNetworkJson.integerMap(value, "weights"), BrowserNetworkJson.string(value, "fallbackGroupId"),
                new LinkedHashSet<>(BrowserNetworkJson.strings(value, "forcedHosts")), BrowserNetworkJson.string(value, "permission"));
    }

    private static SyncRealm syncRealm(JsonObject value) {
        return new SyncRealm(BrowserNetworkJson.string(value, "id"), BrowserNetworkJson.string(value, "name"),
                new LinkedHashSet<>(BrowserNetworkJson.strings(value, "nodeIds")),
                BrowserNetworkJson.strings(value, "dataFamilies").stream().map(item -> enumValue(item, SyncDataFamily.PRESENCE)).collect(Collectors.toCollection(LinkedHashSet::new)),
                enumValue(BrowserNetworkJson.string(value, "locationPolicy"), SyncLocationPolicy.NEVER),
                new LinkedHashSet<>(BrowserNetworkJson.strings(value, "persistentDataNamespaces")),
                BrowserNetworkJson.integer(value, "retainedSnapshots", 10), BrowserNetworkJson.integer(value, "retentionDays", 30));
    }

    private static NetworkRuntimePolicy runtimePolicy(JsonObject value) {
        return new NetworkRuntimePolicy(BrowserNetworkJson.bool(value, "enabled", false), BrowserNetworkJson.string(value, "hubAddress"),
                BrowserNetworkJson.integer(value, "hubPort", 0), enumValue(BrowserNetworkJson.string(value, "security"), NetworkTransportSecurity.LOOPBACK),
                BrowserNetworkJson.bool(value, "transportReady", false));
    }

    private static NetworkSharedDataPolicy sharedDataPolicy(JsonObject value) {
        List<NetworkPathSync> pathSyncs = BrowserNetworkJson.objects(value, "pathSyncs").stream().map(BrowserNetworkOverviewProvider::pathSync).toList();
        return new NetworkSharedDataPolicy(enumValue(BrowserNetworkJson.string(value, "chatChannelMode"), NetworkSharedDataPolicy.SelectionMode.ALL),
                new LinkedHashSet<>(BrowserNetworkJson.strings(value, "chatChannels")), BrowserNetworkJson.longValue(value, "chatRetentionMillis", 120_000),
                enumValue(BrowserNetworkJson.string(value, "resourceTypeMode"), NetworkSharedDataPolicy.SelectionMode.ALL),
                new LinkedHashSet<>(BrowserNetworkJson.strings(value, "resourceTypes")), pathSyncs,
                enumValue(BrowserNetworkJson.string(value, "resourceConflictPolicy"), NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS),
                BrowserNetworkJson.integer(value, "maximumPayloadBytes", NetworkSharedDataPolicy.DEFAULT_MAXIMUM_PAYLOAD_BYTES));
    }

    private static NetworkPathSync pathSync(JsonObject value) {
        return new NetworkPathSync(BrowserNetworkJson.string(value, "id"), BrowserNetworkJson.string(value, "name"),
                BrowserNetworkJson.bool(value, "enabled", false), new LinkedHashSet<>(BrowserNetworkJson.strings(value, "nodeIds")),
                new LinkedHashSet<>(BrowserNetworkJson.strings(value, "paths")),
                enumValue(BrowserNetworkJson.string(value, "conflictPolicy"), NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS),
                BrowserNetworkJson.strings(value, "commands"));
    }

    private static NetworkDiscoveryResult discovery(JsonObject value, NetworkDefinition network) {
        List<NetworkMemberObservation> observations = BrowserNetworkJson.objects(value, "observations").stream().map(item -> new NetworkMemberObservation(
                BrowserNetworkJson.string(item, "nodeId"), BrowserNetworkJson.string(item, "instanceId"), BrowserNetworkJson.bool(item, "instanceAvailable", false),
                BrowserNetworkJson.string(item, "observedHostScope"), BrowserNetworkJson.integer(item, "observedPort", 0), BrowserNetworkJson.string(item, "software"),
                enumValue(BrowserNetworkJson.string(item, "state"), NetworkObservationState.UNKNOWN), BrowserNetworkJson.longValue(item, "observedAt", 0))).toList();
        List<PortReservation> reservations = BrowserNetworkJson.objects(value, "reservations").stream().map(item -> new PortReservation(
                BrowserNetworkJson.string(item, "hostScope"), BrowserNetworkJson.integer(item, "port", 0), BrowserNetworkJson.string(item, "ownerType"),
                BrowserNetworkJson.string(item, "ownerId"), BrowserNetworkJson.string(item, "label"))).toList();
        List<NetworkValidationIssue> issues = BrowserNetworkJson.objects(value, "issues").stream().map(BrowserNetworkOverviewProvider::issue).toList();
        return new NetworkDiscoveryResult(network, Map.of(), observations, reservations, issues);
    }

    private static NetworkValidationIssue issue(JsonObject value) {
        return new NetworkValidationIssue(enumValue(BrowserNetworkJson.string(value, "severity"), NetworkValidationIssue.Severity.INFO),
                BrowserNetworkJson.string(value, "code"), BrowserNetworkJson.string(value, "subject"), BrowserNetworkJson.string(value, "message"));
    }

    private static NetworkRuntimeSnapshot runtime(JsonObject value, String networkId) {
        Map<String, NetworkRuntimeNodePresence> nodes = new LinkedHashMap<>();
        JsonObject nodeMap = BrowserNetworkJson.object(value, "nodes");
        nodeMap.entrySet().forEach(entry -> {
            if (entry.getValue() != null && entry.getValue().isJsonObject()) {
                JsonObject item = entry.getValue().getAsJsonObject();
                NetworkRuntimeNodePresence presence = new NetworkRuntimeNodePresence(
                        BrowserNetworkJson.string(item, "networkId", networkId), BrowserNetworkJson.string(item, "nodeId", entry.getKey()),
                        enumValue(BrowserNetworkJson.string(item, "status"), NetworkRuntimeNodeStatus.OFFLINE), BrowserNetworkJson.integer(item, "players", 0),
                        BrowserNetworkJson.integer(item, "capacity", 0), BrowserNetworkJson.decimal(item, "tps", -1), BrowserNetworkJson.decimal(item, "mspt", -1),
                        BrowserNetworkJson.longValue(item, "heapUsed", 0), BrowserNetworkJson.longValue(item, "heapMaximum", 0), BrowserNetworkJson.longValue(item, "observedAt", 0));
                nodes.put(entry.getKey(), presence);
            }
        });
        return new NetworkRuntimeSnapshot(BrowserNetworkJson.string(value, "networkId", networkId),
                enumValue(BrowserNetworkJson.string(value, "state"), NetworkRuntimeConnectionState.DISABLED),
                BrowserNetworkJson.string(value, "message"), nodes, BrowserNetworkJson.longValue(value, "updatedAt", 0));
    }

    private static NetworkIncident incident(JsonObject value, String networkId) {
        String id = BrowserNetworkJson.string(value, "incidentId", UUID.randomUUID().toString());
        String resolvedNetworkId = BrowserNetworkJson.string(value, "networkId", networkId);
        long openedAt = BrowserNetworkJson.longValue(value, "openedAt", 0);
        long updatedAt = Math.max(openedAt, BrowserNetworkJson.longValue(value, "updatedAt", openedAt));
        NetworkIncidentStatus status = enumValue(BrowserNetworkJson.string(value, "status"), NetworkIncidentStatus.OPEN);
        long resolvedAt = BrowserNetworkJson.longValue(value, "resolvedAt", 0);
        if (status == NetworkIncidentStatus.OPEN) resolvedAt = 0;
        if (status == NetworkIncidentStatus.RESOLVED) resolvedAt = Math.max(updatedAt, resolvedAt);
        return new NetworkIncident(id, resolvedNetworkId, BrowserNetworkJson.string(value, "key", id), BrowserNetworkJson.string(value, "nodeId"),
                BrowserNetworkJson.string(value, "type", "network"), enumValue(BrowserNetworkJson.string(value, "source"), NetworkIncidentSource.RUNTIME),
                enumValue(BrowserNetworkJson.string(value, "severity"), NetworkIncidentSeverity.WARNING), status,
                BrowserNetworkJson.string(value, "summary", "Network Incident"), BrowserNetworkJson.string(value, "detail"), openedAt, updatedAt,
                resolvedAt, Math.max(1, BrowserNetworkJson.integer(value, "occurrences", 1)));
    }

    private static NetworkLifecycleJob lifecycleJob(JsonObject value) {
        List<NetworkLifecycleStep> steps = BrowserNetworkJson.objects(value, "steps").stream().map(item -> new NetworkLifecycleStep(
                BrowserNetworkJson.string(item, "stepId"), BrowserNetworkJson.string(item, "instanceId"), BrowserNetworkJson.string(item, "nodeId"),
                BrowserNetworkJson.string(item, "routeName"), enumValue(BrowserNetworkJson.string(item, "action"), NetworkLifecycleAction.START),
                enumValue(BrowserNetworkJson.string(item, "status"), NetworkLifecycleStepStatus.PENDING), BrowserNetworkJson.longValue(item, "startedAt", 0),
                BrowserNetworkJson.longValue(item, "completedAt", 0), BrowserNetworkJson.string(item, "message"))).toList();
        return new NetworkLifecycleJob(BrowserNetworkJson.integer(value, "schemaVersion", 1), BrowserNetworkJson.string(value, "jobId"),
                BrowserNetworkJson.string(value, "networkId"), BrowserNetworkJson.longValue(value, "networkRevision", 1),
                enumValue(BrowserNetworkJson.string(value, "operation"), NetworkLifecycleOperation.START),
                enumValue(BrowserNetworkJson.string(value, "status"), NetworkLifecycleStatus.READY), BrowserNetworkJson.string(value, "initiator"),
                BrowserNetworkJson.longValue(value, "createdAt", 0), BrowserNetworkJson.longValue(value, "updatedAt", 0),
                BrowserNetworkJson.integer(value, "attempt", 0), BrowserNetworkJson.string(value, "message"), steps);
    }

    private static NetworkJob networkJob(JsonObject value) {
        List<NetworkJobDocument> documents = BrowserNetworkJson.objects(value, "documents").stream().map(item -> new NetworkJobDocument(
                new NetworkConfigDocumentKey(BrowserNetworkJson.string(BrowserNetworkJson.object(item, "key"), "instanceId"),
                        BrowserNetworkJson.string(BrowserNetworkJson.object(item, "key"), "path")), BrowserNetworkJson.integer(item, "applyOrder", 0),
                BrowserNetworkJson.bool(item, "originalExists", false), BrowserNetworkJson.string(item, "originalHash"), BrowserNetworkJson.string(item, "desiredHash"),
                enumValue(BrowserNetworkJson.string(item, "state"), NetworkJobDocumentState.PENDING))).toList();
        return new NetworkJob(BrowserNetworkJson.integer(value, "schemaVersion", 1), BrowserNetworkJson.string(value, "jobId"),
                BrowserNetworkJson.string(value, "networkId"), BrowserNetworkJson.longValue(value, "networkRevision", 1),
                enumValue(BrowserNetworkJson.string(value, "type"), NetworkJobType.RECONCILE), enumValue(BrowserNetworkJson.string(value, "status"), NetworkJobStatus.PLANNING),
                BrowserNetworkJson.string(value, "initiator"), BrowserNetworkJson.longValue(value, "createdAt", 0), BrowserNetworkJson.longValue(value, "updatedAt", 0),
                BrowserNetworkJson.integer(value, "attempt", 0), BrowserNetworkJson.string(value, "message"), BrowserNetworkJson.stringMap(value, "context"), documents,
                BrowserNetworkJson.objects(value, "issues").stream().map(BrowserNetworkOverviewProvider::issue).toList());
    }

    private static NetworkPreflightReport preflight(JsonObject value) {
        List<NetworkPreflightCheck> checks = BrowserNetworkJson.objects(value, "checks").stream().map(item -> new NetworkPreflightCheck(
                BrowserNetworkJson.string(item, "id"), BrowserNetworkJson.string(item, "subject"), BrowserNetworkJson.string(item, "label"),
                enumValue(BrowserNetworkJson.string(item, "status"), NetworkPreflightCheckStatus.FAILED), BrowserNetworkJson.string(item, "detail"),
                BrowserNetworkJson.longValue(item, "checkedAt", 0))).toList();
        return new NetworkPreflightReport(BrowserNetworkJson.integer(value, "schemaVersion", 1), BrowserNetworkJson.string(value, "reportId"),
                BrowserNetworkJson.string(value, "networkId"), BrowserNetworkJson.longValue(value, "networkRevision", 1),
                enumValue(BrowserNetworkJson.string(value, "status"), NetworkPreflightStatus.RUNNING), BrowserNetworkJson.longValue(value, "startedAt", 0),
                BrowserNetworkJson.longValue(value, "completedAt", 0), BrowserNetworkJson.string(value, "summary"), checks);
    }

    private static String enumName(Enum<?> value, Enum<?> fallback) {
        return (value == null ? fallback : value).name();
    }

    private static <T extends Enum<T>> T enumValue(String value, T fallback) {
        if (fallback == null || value == null || value.isBlank()) return fallback;
        Map<String, ? extends Enum<?>> values = ENUM_VALUES.get(fallback);
        if (values == null) return fallback;
        Enum<?> candidate = values.get(value.trim().toUpperCase(Locale.ROOT));
        return candidate == null ? fallback : castEnum(candidate);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Enum<T>> T castEnum(Enum<?> value) {
        return (T) value;
    }

    private static <T extends Enum<T>> Map<String, T> enumValues(T... values) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            result.put(value.name(), value);
        }
        return Map.copyOf(result);
    }

    private static JsonObject object(JsonObject value, String key) {
        return BrowserNetworkJson.object(value, key);
    }

    private static final class BrowserNetworkJson {
        private BrowserNetworkJson() {
        }

        private static JsonObject save(SaveRequest request) {
            JsonObject result = new JsonObject();
            if (request == null) return result;
            put(result, "name", request.name());
            result.add("routingGroups", routingGroups(request.routingGroups()));
            result.add("syncRealms", syncRealms(request.syncRealms()));
            result.add("features", map(request.features()));
            result.add("sharedDataPolicy", sharedDataPolicy(request.sharedDataPolicy()));
            return result;
        }

        private static JsonArray routingGroups(List<RoutingGroup> values) {
            JsonArray result = new JsonArray();
            for (RoutingGroup value : values == null ? List.<RoutingGroup>of() : values) {
                JsonObject item = new JsonObject();
                put(item, "id", value.id()); put(item, "name", value.name()); put(item, "strategy", value.strategy().name());
                item.add("nodeIds", strings(value.nodeIds())); item.add("weights", map(value.weights())); put(item, "fallbackGroupId", value.fallbackGroupId());
                item.add("forcedHosts", strings(value.forcedHosts())); put(item, "permission", value.permission()); result.add(item);
            }
            return result;
        }

        private static JsonArray syncRealms(List<SyncRealm> values) {
            JsonArray result = new JsonArray();
            for (SyncRealm value : values == null ? List.<SyncRealm>of() : values) {
                JsonObject item = new JsonObject();
                put(item, "id", value.id()); put(item, "name", value.name()); item.add("nodeIds", strings(value.nodeIds()));
                item.add("dataFamilies", enums(value.dataFamilies())); put(item, "locationPolicy", value.locationPolicy().name());
                item.add("persistentDataNamespaces", strings(value.persistentDataNamespaces())); put(item, "retainedSnapshots", value.retainedSnapshots());
                put(item, "retentionDays", value.retentionDays()); result.add(item);
            }
            return result;
        }

        private static JsonObject sharedDataPolicy(NetworkSharedDataPolicy value) {
            JsonObject result = new JsonObject();
            if (value == null) return result;
            put(result, "chatChannelMode", value.chatChannelMode().name()); result.add("chatChannels", strings(value.chatChannels()));
            put(result, "chatRetentionMillis", value.chatRetentionMillis()); put(result, "resourceTypeMode", value.resourceTypeMode().name());
            result.add("resourceTypes", strings(value.resourceTypes())); put(result, "resourceConflictPolicy", value.resourceConflictPolicy().name());
            put(result, "maximumPayloadBytes", value.maximumPayloadBytes()); result.add("pathSyncs", pathSyncs(value.pathSyncs())); return result;
        }

        private static JsonArray pathSyncs(List<NetworkPathSync> values) {
            JsonArray result = new JsonArray();
            for (NetworkPathSync value : values == null ? List.<NetworkPathSync>of() : values) {
                JsonObject item = new JsonObject(); put(item, "id", value.id()); put(item, "name", value.name()); put(item, "enabled", value.enabled());
                item.add("nodeIds", strings(value.nodeIds())); item.add("paths", strings(value.paths())); put(item, "conflictPolicy", value.conflictPolicy().name());
                item.add("commands", strings(value.commands())); result.add(item);
            }
            return result;
        }

        private static JsonArray strings(Iterable<String> values) {
            JsonArray result = new JsonArray();
            if (values != null) for (String value : values) result.add(value == null ? "" : value);
            return result;
        }

        private static JsonArray enums(Iterable<? extends Enum<?>> values) {
            JsonArray result = new JsonArray();
            if (values != null) for (Enum<?> value : values) result.add(value == null ? "" : value.name());
            return result;
        }

        private static JsonObject map(Map<?, ?> values) {
            JsonObject result = new JsonObject();
            if (values != null) values.forEach((key, value) -> put(result, String.valueOf(key), value));
            return result;
        }

        private static void put(JsonObject value, String key, Object item) {
            if (item == null) value.add(key, JsonNull.INSTANCE);
            else if (item instanceof Boolean bool) value.addProperty(key, bool);
            else if (item instanceof Number number) value.addProperty(key, number);
            else value.addProperty(key, String.valueOf(item));
        }

        private static JsonObject object(JsonObject value, String key) {
            JsonElement element = value == null ? null : value.get(key);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        }

        private static List<JsonObject> objects(JsonObject value, String key) {
            JsonElement element = value == null ? null : value.get(key);
            if (element == null || !element.isJsonArray()) return List.of();
            List<JsonObject> result = new ArrayList<>();
            element.getAsJsonArray().forEach(item -> { if (item != null && item.isJsonObject()) result.add(item.getAsJsonObject()); });
            return result;
        }

        private static List<NetworkOverviewProvider.ServerView> servers(JsonObject value) {
            return objects(value, "servers").stream().map(item -> new NetworkOverviewProvider.ServerView(string(item, "id"), string(item, "name"),
                    bool(item, "proxy", false), bool(item, "managed", true), string(item, "hostScope"), string(item, "address"),
                    integer(item, "port", 0), string(item, "state"), string(item, "icon"))).toList();
        }

        private static String string(JsonObject value, String key) { return string(value, key, ""); }
        private static String string(JsonObject value, String key, String fallback) {
            JsonElement item = value == null ? null : value.get(key);
            return item == null || item.isJsonNull() ? fallback : item.isJsonPrimitive() ? item.getAsString() : fallback;
        }
        private static boolean bool(JsonObject value, String key, boolean fallback) {
            JsonElement item = value == null ? null : value.get(key);
            try { return item == null || item.isJsonNull() ? fallback : item.getAsBoolean(); } catch (RuntimeException ignored) { return fallback; }
        }
        private static int integer(JsonObject value, String key, int fallback) {
            JsonElement item = value == null ? null : value.get(key);
            try { return item == null || item.isJsonNull() ? fallback : item.getAsInt(); } catch (RuntimeException ignored) { return fallback; }
        }
        private static long longValue(JsonObject value, String key, long fallback) {
            JsonElement item = value == null ? null : value.get(key);
            try { return item == null || item.isJsonNull() ? fallback : item.getAsLong(); } catch (RuntimeException ignored) { return fallback; }
        }
        private static double decimal(JsonObject value, String key, double fallback) {
            JsonElement item = value == null ? null : value.get(key);
            try { return item == null || item.isJsonNull() ? fallback : item.getAsDouble(); } catch (RuntimeException ignored) { return fallback; }
        }
        private static List<String> strings(JsonObject value, String key) {
            JsonElement item = value == null ? null : value.get(key);
            if (item == null || !item.isJsonArray()) return List.of();
            List<String> result = new ArrayList<>(); item.getAsJsonArray().forEach(entry -> { if (entry != null && entry.isJsonPrimitive()) result.add(entry.getAsString()); }); return List.copyOf(result);
        }
        private static Map<String, String> stringMap(JsonObject value, String key) {
            Map<String, String> result = new LinkedHashMap<>(); object(value, key).entrySet().forEach(entry -> { if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) result.put(entry.getKey(), entry.getValue().getAsString()); }); return Map.copyOf(result);
        }
        private static Map<String, Boolean> booleanMap(JsonObject value, String key) {
            Map<String, Boolean> result = new LinkedHashMap<>(); object(value, key).entrySet().forEach(entry -> { if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) result.put(entry.getKey(), bool(object(value, key), entry.getKey(), false)); }); return Map.copyOf(result);
        }
        private static Map<String, Integer> integerMap(JsonObject value, String key) {
            Map<String, Integer> result = new LinkedHashMap<>(); object(value, key).entrySet().forEach(entry -> { if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) result.put(entry.getKey(), integer(object(value, key), entry.getKey(), 0)); }); return Map.copyOf(result);
        }

        private static Map<String, NetworkOverviewProvider.NetworkCapability> capabilities(JsonObject value) {
            Map<String, NetworkOverviewProvider.NetworkCapability> result = new LinkedHashMap<>();
            object(value, "capabilities").entrySet().forEach(entry -> {
                if (entry.getValue() == null || !entry.getValue().isJsonObject()) return;
                JsonObject capability = entry.getValue().getAsJsonObject();
                String operation = string(capability, "operation", entry.getKey());
                result.put(entry.getKey(), new NetworkOverviewProvider.NetworkCapability(operation,
                        bool(capability, "supported", false), string(capability, "reason"), string(capability, "transport")));
            });
            return Map.copyOf(result);
        }
    }
}
