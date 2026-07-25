package redxax.oxy.remotely.data.integrations.luckperms;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.ReSyncFlowClient.ConnectionState;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkManager;
import redxax.oxy.remotely.network.NetworkMember;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.resync.permissions.LuckPermsManagementContract.ChangeSet;
import restudio.resync.permissions.LuckPermsManagementContract.EntityCreate;
import restudio.resync.permissions.LuckPermsManagementContract.EntityType;
import restudio.resync.permissions.LuckPermsManagementContract.SaveResult;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectChange;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectDetail;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectRef;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectType;
import restudio.resync.permissions.LuckPermsManagementContract.TrackChange;
import restudio.resync.permissions.LuckPermsManagementContract.TrackDetail;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public final class ReSyncLuckPermsNetworkClient {
    public enum Delivery {
        PLAYERS,
        GROUPS,
        TRACKS;

        public static Set<Delivery> all() {
            return Set.copyOf(EnumSet.allOf(Delivery.class));
        }
    }

    public record Target(String instanceId, String name, boolean selected, boolean connected, boolean available, String detail, Set<Delivery> deliveries) {
        public Target {
            instanceId = normalize(instanceId);
            name = normalize(name);
            detail = normalize(detail);
            deliveries = deliveries == null ? Set.of() : Set.copyOf(deliveries);
        }
    }

    public record Snapshot(String networkId, String networkName, String sourceInstanceId, List<Target> targets) {
        public Snapshot {
            networkId = normalize(networkId);
            networkName = normalize(networkName);
            sourceInstanceId = normalize(sourceInstanceId);
            targets = targets == null ? List.of() : List.copyOf(targets);
        }

        public Set<String> selectedInstanceIds() {
            return targets.stream().filter(Target::selected).map(Target::instanceId).collect(Collectors.toUnmodifiableSet());
        }
    }

    public record TargetResult(String instanceId, String name, boolean success, String message, SaveResult result) {
        public TargetResult {
            instanceId = normalize(instanceId);
            name = normalize(name);
            message = normalize(message);
        }
    }

    public record DistributionResult(String operationId, long startedAt, long completedAt, List<TargetResult> targets) {
        public DistributionResult {
            operationId = normalize(operationId);
            targets = targets == null ? List.of() : List.copyOf(targets);
        }

        public boolean success() {
            return !targets.isEmpty() && targets.stream().allMatch(TargetResult::success);
        }
    }

    private static final Type SELECTIONS_TYPE = new TypeToken<Map<String, Map<String, Set<Delivery>>>>() {
    }.getType();
    private final ReSyncLuckPermsClient source;
    private final Gson gson = new Gson();
    private final Path selectionsPath = remotelyDir.resolve("data").resolve("permissions").resolve("network-targets.json");
    private final Map<String, Map<String, Set<Delivery>>> selections = new LinkedHashMap<>();

    public ReSyncLuckPermsNetworkClient(ReSyncLuckPermsClient source) {
        this.source = source;
        load();
    }

    public CompletableFuture<Snapshot> snapshot() {
        NetworkDefinition network = network();
        if (network == null) {
            Target sourceTarget = target(source.serverId(), name(source.serverId()), Delivery.all());
            return CompletableFuture.completedFuture(new Snapshot("", "This Server", source.serverId(), List.of(sourceTarget)));
        }
        Map<String, Set<Delivery>> selected = selected(network);
        List<Target> targets = network.members().stream().filter(NetworkMember::isManaged).filter(NetworkMember::resyncEnabled)
            .filter(member -> !member.isProxy()).map(member -> target(member.instanceId(), name(member.instanceId()),
                member.instanceId().equals(source.serverId()) ? Delivery.all() : selected.getOrDefault(member.instanceId(), Set.of())))
            .sorted(Comparator.comparing(Target::name, String.CASE_INSENSITIVE_ORDER)).toList();
        return CompletableFuture.completedFuture(new Snapshot(network.networkId(), network.name(), source.serverId(), targets));
    }

    public CompletableFuture<Snapshot> select(Set<String> instanceIds) {
        NetworkDefinition network = network();
        if (network == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("This Server Is Not In A Remotely Network"));
        }
        Set<String> allowed = network.members().stream().filter(NetworkMember::isManaged).filter(NetworkMember::resyncEnabled)
            .filter(member -> !member.isProxy()).map(NetworkMember::instanceId).collect(Collectors.toSet());
        LinkedHashSet<String> updated = instanceIds == null ? new LinkedHashSet<>() : instanceIds.stream()
            .filter(Objects::nonNull).map(String::trim).filter(allowed::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!allowed.contains(source.serverId())) {
            return CompletableFuture.failedFuture(new IllegalStateException("The Current Server Cannot Receive ReSync Permission Changes"));
        }
        updated.add(source.serverId());
        synchronized (selections) {
            Map<String, Set<Delivery>> current = selected(network);
            Map<String, Set<Delivery>> configured = new LinkedHashMap<>();
            updated.forEach(instanceId -> configured.put(instanceId, instanceId.equals(source.serverId())
                ? Delivery.all() : current.getOrDefault(instanceId, Delivery.all())));
            selections.put(network.networkId(), Map.copyOf(configured));
            saveSelections();
        }
        return snapshot();
    }

    public CompletableFuture<Snapshot> configure(String instanceId, Set<Delivery> deliveries) {
        Map<String, Set<Delivery>> updated = new LinkedHashMap<>();
        snapshot().join().targets().stream().filter(Target::selected).forEach(target -> updated.put(target.instanceId(), target.deliveries()));
        if (deliveries == null || deliveries.isEmpty()) {
            updated.remove(normalize(instanceId));
        } else {
            updated.put(normalize(instanceId), Set.copyOf(deliveries));
        }
        return configure(updated);
    }

    public CompletableFuture<Snapshot> configure(Map<String, Set<Delivery>> deliveries) {
        NetworkDefinition network = network();
        if (network == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("This Server Is Not In A Remotely Network"));
        }
        Set<String> allowed = network.members().stream().filter(NetworkMember::isManaged).filter(NetworkMember::resyncEnabled)
            .filter(member -> !member.isProxy()).map(NetworkMember::instanceId).collect(Collectors.toSet());
        Map<String, Set<Delivery>> configured = new LinkedHashMap<>();
        if (deliveries != null) {
            deliveries.forEach((instanceId, choices) -> {
                String targetId = normalize(instanceId);
                if (!targetId.equals(source.serverId()) && allowed.contains(targetId) && choices != null && !choices.isEmpty()) {
                    configured.put(targetId, Set.copyOf(EnumSet.copyOf(choices)));
                }
            });
        }
        if (!allowed.contains(source.serverId())) {
            return CompletableFuture.failedFuture(new IllegalStateException("The Current Server Cannot Receive ReSync Permission Changes"));
        }
        configured.put(source.serverId(), Delivery.all());
        synchronized (selections) {
            selections.put(network.networkId(), Map.copyOf(configured));
            saveSelections();
        }
        return snapshot();
    }

    public CompletableFuture<DistributionResult> save(ChangeSet changes) {
        long startedAt = System.currentTimeMillis();
        return snapshot().thenCompose(snapshot -> {
            List<Target> selected = snapshot.targets().stream().filter(Target::selected).toList();
            if (selected.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Choose At Least One Server"));
            }
            Target sourceTarget = selected.stream().filter(target -> target.instanceId().equals(source.serverId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("The Current Server Must Be Selected"));
            return saveSource(sourceTarget, changes).thenCompose(sourceResult -> {
                if (!sourceResult.success()) {
                    List<TargetResult> results = new ArrayList<>();
                    results.add(sourceResult);
                    selected.stream().filter(target -> !target.instanceId().equals(source.serverId())).forEach(target ->
                        results.add(new TargetResult(target.instanceId(), target.name(), false, "The Current Server Must Save First", null)));
                    return CompletableFuture.completedFuture(new DistributionResult(changes.operationId(), startedAt, System.currentTimeMillis(), results));
                }
                List<CompletableFuture<TargetResult>> operations = selected.stream()
                    .filter(target -> !target.instanceId().equals(source.serverId())).map(target -> save(target, filtered(changes, target.deliveries()))).toList();
                return CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                    List<TargetResult> results = new ArrayList<>();
                    results.add(sourceResult);
                    operations.stream().map(CompletableFuture::join).forEach(results::add);
                    return new DistributionResult(changes.operationId(), startedAt, System.currentTimeMillis(), results);
                });
            });
        });
    }

    private CompletableFuture<TargetResult> saveSource(Target target, ChangeSet changes) {
        if (!target.connected() || !source.isAvailable()) {
            return CompletableFuture.completedFuture(new TargetResult(target.instanceId(), target.name(), false,
                target.connected() ? "Permission Management Is Unavailable" : "ReSync Is Not Connected", null));
        }
        return source.save(changes).handle((result, failure) -> failure == null
            ? new TargetResult(target.instanceId(), target.name(), result.applied(), result.applied() ? "Saved" : "Review Conflicts", result)
            : new TargetResult(target.instanceId(), target.name(), false, rootMessage(failure), null));
    }

    private CompletableFuture<TargetResult> save(Target target, ChangeSet changes) {
        if (!target.connected()) {
            return CompletableFuture.completedFuture(new TargetResult(target.instanceId(), target.name(), false, "ReSync Is Not Connected", null));
        }
        ReSyncLuckPermsClient client = client(target.instanceId());
        if (!client.isAvailable()) {
            return CompletableFuture.completedFuture(new TargetResult(target.instanceId(), target.name(), false, "Permission Management Is Unavailable", null));
        }
        return rebase(client, changes).thenCompose(client::save)
            .handle((result, failure) -> failure == null
                ? new TargetResult(target.instanceId(), target.name(), result.applied(), result.applied() ? "Saved" : "Review Conflicts", result)
                : new TargetResult(target.instanceId(), target.name(), false, rootMessage(failure), null));
    }

    private CompletableFuture<ChangeSet> rebase(ReSyncLuckPermsClient client, ChangeSet changes) {
        Set<String> createdSubjects = changes.creates().stream().filter(create -> create.type() != EntityType.TRACK)
            .map(create -> entityKey(create.type(), create.id())).collect(Collectors.toSet());
        Set<String> createdTracks = changes.creates().stream().filter(create -> create.type() == EntityType.TRACK)
            .map(EntityCreate::id).collect(Collectors.toSet());
        Map<SubjectChange, CompletableFuture<SubjectDetail>> subjectLoads = new LinkedHashMap<>();
        for (SubjectChange change : changes.subjects()) {
            EntityType type = change.subject().type() == SubjectType.GROUP ? EntityType.GROUP : EntityType.USER;
            if (!createdSubjects.contains(entityKey(type, change.subject().id()))) {
                subjectLoads.put(change, client.subject(change.subject()));
            }
        }
        CompletableFuture<List<TrackDetail>> tracks = changes.tracks().stream().anyMatch(change -> !createdTracks.contains(change.name()))
            ? client.tracks() : CompletableFuture.completedFuture(List.of());
        List<CompletableFuture<?>> loads = new ArrayList<>(subjectLoads.values());
        loads.add(tracks);
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            List<SubjectChange> subjects = changes.subjects().stream().map(change -> {
                CompletableFuture<SubjectDetail> load = subjectLoads.get(change);
                long baseRevision = load == null ? 0 : load.join().revision();
                return new SubjectChange(change.subject(), baseRevision, change.name(), change.primaryGroup(), change.weight(), change.nodes());
            }).toList();
            Map<String, Long> trackRevisions = tracks.join().stream().collect(Collectors.toMap(TrackDetail::name, TrackDetail::revision));
            List<TrackChange> trackChanges = changes.tracks().stream().map(change ->
                new TrackChange(change.name(), createdTracks.contains(change.name()) ? 0 : trackRevisions.getOrDefault(change.name(), change.baseRevision()),
                    change.groups())).toList();
            return new ChangeSet(changes.operationId(), subjects, trackChanges, changes.creates(), changes.deletes());
        });
    }

    private ChangeSet filtered(ChangeSet changes, Set<Delivery> deliveries) {
        boolean players = deliveries.contains(Delivery.PLAYERS);
        boolean groups = deliveries.contains(Delivery.GROUPS);
        boolean tracks = deliveries.contains(Delivery.TRACKS);
        return new ChangeSet(changes.operationId(),
            changes.subjects().stream().filter(change -> change.subject().type() == SubjectType.USER ? players : groups).toList(),
            tracks ? changes.tracks() : List.of(),
            changes.creates().stream().filter(create -> delivered(create.type(), players, groups, tracks)).toList(),
            changes.deletes().stream().filter(delete -> delivered(delete.type(), players, groups, tracks)).toList());
    }

    private boolean delivered(EntityType type, boolean players, boolean groups, boolean tracks) {
        return switch (type) {
            case USER -> players;
            case GROUP -> groups;
            case TRACK -> tracks;
        };
    }

    private Target target(String instanceId, String name, Set<Delivery> deliveries) {
        FlowManager flowManager = flowManager();
        if (flowManager == null) {
            return new Target(instanceId, name.isBlank() ? instanceId : name, !deliveries.isEmpty(), false, false,
                "ReSync Bridge Unavailable", deliveries);
        }
        ReSyncLuckPermsClient targetClient = client(instanceId);
        ConnectionState connection = flowManager.getFlowClientConnectionState(instanceId);
        boolean connected = connection == ConnectionState.CONNECTED;
        boolean available = connected && targetClient.isAvailable();
        String detail = switch (connection) {
            case CONNECTED -> available ? "Server Online • LuckPerms Ready" : "Server Online • LuckPerms Unavailable";
            case CONNECTING -> "ReSync Bridge Connecting";
            case DISCONNECTED -> "ReSync Bridge Unavailable";
        };
        return new Target(instanceId, name.isBlank() ? instanceId : name, !deliveries.isEmpty(), connected, available, detail, deliveries);
    }

    private ReSyncLuckPermsClient client(String instanceId) {
        if (source.serverId().equals(instanceId)) {
            return source;
        }
        FlowManager flowManager = flowManager();
        if (flowManager == null) {
            throw new IllegalStateException("ReSync Is Not Available");
        }
        return flowManager.ensureFlowClient(instanceId).luckPerms();
    }

    private Map<String, Set<Delivery>> selected(NetworkDefinition network) {
        synchronized (selections) {
            Map<String, Set<Delivery>> selected = selections.get(network.networkId());
            if (selected == null || selected.isEmpty()) {
                return Map.of(source.serverId(), Delivery.all());
            }
            return selected;
        }
    }

    private NetworkDefinition network() {
        RemotelyClient remotely = RemotelyClient.INSTANCE;
        NetworkManager manager = remotely == null ? null : remotely.getNetworkManager();
        return manager == null ? null : manager.getNetworkForInstance(source.serverId()).orElse(null);
    }

    private FlowManager flowManager() {
        RemotelyClient remotely = RemotelyClient.INSTANCE;
        return remotely == null ? null : remotely.getFlowManager();
    }

    private String name(String instanceId) {
        try {
            return Rebase.get().getInstanceManager().getAllInstances().stream().filter(instance -> instance != null)
                .filter(instance -> instanceId.equals(instance.getInstanceId())).map(Instance::getName).findFirst().orElse(instanceId);
        } catch (IllegalStateException exception) {
            return instanceId;
        }
    }

    private void load() {
        synchronized (selections) {
            selections.clear();
            if (!Files.isRegularFile(selectionsPath)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(selectionsPath)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> network : root.getAsJsonObject().entrySet()) {
                        selections.put(network.getKey(), readSelection(network.getValue()));
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                selections.clear();
            }
        }
    }

    private Map<String, Set<Delivery>> readSelection(JsonElement value) {
        Map<String, Set<Delivery>> migrated = new LinkedHashMap<>();
        if (value != null && value.isJsonArray()) {
            value.getAsJsonArray().forEach(instanceId -> migrated.put(instanceId.getAsString(), Delivery.all()));
            return Map.copyOf(migrated);
        }
        if (value == null || !value.isJsonObject()) {
            return Map.of();
        }
        JsonObject object = value.getAsJsonObject();
        object.entrySet().forEach(entry -> {
            Set<Delivery> deliveries = gson.fromJson(entry.getValue(), new TypeToken<Set<Delivery>>() {
            }.getType());
            if (deliveries != null && !deliveries.isEmpty()) {
                migrated.put(entry.getKey(), Set.copyOf(deliveries));
            }
        });
        return Map.copyOf(migrated);
    }

    private void saveSelections() {
        try {
            Files.createDirectories(selectionsPath.getParent());
            Path temporary = selectionsPath.resolveSibling(selectionsPath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                gson.toJson(selections, SELECTIONS_TYPE, writer);
            }
            try {
                Files.move(temporary, selectionsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(temporary, selectionsPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Network Selection Could Not Be Saved", exception);
        }
    }

    private String entityKey(EntityType type, String id) {
        return type.name() + ":" + normalize(id).toLowerCase();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank() ? "Permission Save Failed" : current.getMessage();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
