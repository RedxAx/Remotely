package redxax.oxy.remotely.data.integrations.luckperms;

import redxax.oxy.remotely.data.flow.ReSyncFlowClient.ConnectionState;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsNetworkEnvironment.Member;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsNetworkEnvironment.Network;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import restudio.rescreen.platform.Async;

import java.util.stream.Collectors;

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

    private final ReSyncLuckPermsClient source;
    private final ReSyncLuckPermsNetworkEnvironment environment;
    private final Map<String, Map<String, Set<Delivery>>> selections = new LinkedHashMap<>();

    public ReSyncLuckPermsNetworkClient(ReSyncLuckPermsClient source) {
        this(source, ReSyncLuckPermsNetworkEnvironment.unavailable());
    }

    public ReSyncLuckPermsNetworkClient(ReSyncLuckPermsClient source, ReSyncLuckPermsNetworkEnvironment environment) {
        this.source = source;
        this.environment = environment == null ? ReSyncLuckPermsNetworkEnvironment.unavailable() : environment;
        selections.putAll(this.environment.loadSelections());
    }

    public Async<Snapshot> snapshot() {
        Network network = network();
        if (network == null) {
            Target sourceTarget = target(source.serverId(), source.serverId(), Delivery.all());
            return Async.completed(new Snapshot("", "This Server", source.serverId(), List.of(sourceTarget)));
        }
        Map<String, Set<Delivery>> selected = selected(network);
        List<Target> targets = network.members().stream().filter(Member::managed).filter(Member::reSyncEnabled)
            .filter(member -> !member.proxy()).map(member -> target(member.instanceId(), member.name(),
                member.instanceId().equals(source.serverId()) ? Delivery.all() : selected.getOrDefault(member.instanceId(), Set.of())))
            .sorted(Comparator.comparing(Target::name, String.CASE_INSENSITIVE_ORDER)).toList();
        return Async.completed(new Snapshot(network.networkId(), network.name(), source.serverId(), targets));
    }

    public Async<Snapshot> select(Set<String> instanceIds) {
        Network network = network();
        if (network == null) {
            return Async.failed(new IllegalStateException("This Server Is Not In A Remotely Network"));
        }
        Set<String> allowed = network.members().stream().filter(Member::managed).filter(Member::reSyncEnabled)
            .filter(member -> !member.proxy()).map(Member::instanceId).collect(Collectors.toSet());
        LinkedHashSet<String> updated = instanceIds == null ? new LinkedHashSet<>() : instanceIds.stream()
            .filter(Objects::nonNull).map(String::trim).filter(allowed::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!allowed.contains(source.serverId())) {
            return Async.failed(new IllegalStateException("The Current Server Cannot Receive ReSync Permission Changes"));
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

    public Async<Snapshot> configure(String instanceId, Set<Delivery> deliveries) {
        Map<String, Set<Delivery>> updated = new LinkedHashMap<>();
        snapshot().join().targets().stream().filter(Target::selected).forEach(target -> updated.put(target.instanceId(), target.deliveries()));
        if (deliveries == null || deliveries.isEmpty()) {
            updated.remove(normalize(instanceId));
        } else {
            updated.put(normalize(instanceId), Set.copyOf(deliveries));
        }
        return configure(updated);
    }

    public Async<Snapshot> configure(Map<String, Set<Delivery>> deliveries) {
        Network network = network();
        if (network == null) {
            return Async.failed(new IllegalStateException("This Server Is Not In A Remotely Network"));
        }
        Set<String> allowed = network.members().stream().filter(Member::managed).filter(Member::reSyncEnabled)
            .filter(member -> !member.proxy()).map(Member::instanceId).collect(Collectors.toSet());
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
            return Async.failed(new IllegalStateException("The Current Server Cannot Receive ReSync Permission Changes"));
        }
        configured.put(source.serverId(), Delivery.all());
        synchronized (selections) {
            selections.put(network.networkId(), Map.copyOf(configured));
            saveSelections();
        }
        return snapshot();
    }

    public Async<DistributionResult> save(ChangeSet changes) {
        long startedAt = System.currentTimeMillis();
        return snapshot().thenCompose(snapshot -> {
            List<Target> selected = snapshot.targets().stream().filter(Target::selected).toList();
            if (selected.isEmpty()) {
                return Async.failed(new IllegalStateException("Choose At Least One Server"));
            }
            Target sourceTarget = selected.stream().filter(target -> target.instanceId().equals(source.serverId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("The Current Server Must Be Selected"));
            return saveSource(sourceTarget, changes).thenCompose(sourceResult -> {
                if (!sourceResult.success()) {
                    List<TargetResult> results = new ArrayList<>();
                    results.add(sourceResult);
                    selected.stream().filter(target -> !target.instanceId().equals(source.serverId())).forEach(target ->
                        results.add(new TargetResult(target.instanceId(), target.name(), false, "The Current Server Must Save First", null)));
                    return Async.completed(new DistributionResult(changes.operationId(), startedAt, System.currentTimeMillis(), results));
                }
                List<Async<TargetResult>> operations = selected.stream()
                    .filter(target -> !target.instanceId().equals(source.serverId())).map(target -> save(target, filtered(changes, target.deliveries()))).toList();
                return Async.allOf(operations.toArray(Async[]::new)).thenApply(ignored -> {
                    List<TargetResult> results = new ArrayList<>();
                    results.add(sourceResult);
                    operations.stream().map(Async::join).forEach(results::add);
                    return new DistributionResult(changes.operationId(), startedAt, System.currentTimeMillis(), results);
                });
            });
        });
    }

    private Async<TargetResult> saveSource(Target target, ChangeSet changes) {
        if (!target.connected() || !source.isAvailable()) {
            return Async.completed(new TargetResult(target.instanceId(), target.name(), false,
                target.connected() ? "Permission Management Is Unavailable" : "ReSync Is Not Connected", null));
        }
        return source.save(changes).handle((result, failure) -> failure == null
            ? new TargetResult(target.instanceId(), target.name(), result.applied(), result.applied() ? "Saved" : "Review Conflicts", result)
            : new TargetResult(target.instanceId(), target.name(), false, rootMessage(failure), null));
    }

    private Async<TargetResult> save(Target target, ChangeSet changes) {
        if (!target.connected()) {
            return Async.completed(new TargetResult(target.instanceId(), target.name(), false, "ReSync Is Not Connected", null));
        }
        ReSyncLuckPermsClient client = client(target.instanceId());
        if (!client.isAvailable()) {
            return Async.completed(new TargetResult(target.instanceId(), target.name(), false, "Permission Management Is Unavailable", null));
        }
        return rebase(client, changes).thenCompose(client::save)
            .handle((result, failure) -> failure == null
                ? new TargetResult(target.instanceId(), target.name(), result.applied(), result.applied() ? "Saved" : "Review Conflicts", result)
                : new TargetResult(target.instanceId(), target.name(), false, rootMessage(failure), null));
    }

    private Async<ChangeSet> rebase(ReSyncLuckPermsClient client, ChangeSet changes) {
        Set<String> createdSubjects = changes.creates().stream().filter(create -> create.type() != EntityType.TRACK)
            .map(create -> entityKey(create.type(), create.id())).collect(Collectors.toSet());
        Set<String> createdTracks = changes.creates().stream().filter(create -> create.type() == EntityType.TRACK)
            .map(EntityCreate::id).collect(Collectors.toSet());
        Map<SubjectChange, Async<SubjectDetail>> subjectLoads = new LinkedHashMap<>();
        for (SubjectChange change : changes.subjects()) {
            EntityType type = change.subject().type() == SubjectType.GROUP ? EntityType.GROUP : EntityType.USER;
            if (!createdSubjects.contains(entityKey(type, change.subject().id()))) {
                subjectLoads.put(change, client.subject(change.subject()));
            }
        }
        Async<List<TrackDetail>> tracks = changes.tracks().stream().anyMatch(change -> !createdTracks.contains(change.name()))
            ? client.tracks() : Async.completed(List.of());
        List<Async<?>> loads = new ArrayList<>(subjectLoads.values());
        loads.add(tracks);
        return Async.allOf(loads.toArray(Async[]::new)).thenApply(ignored -> {
            List<SubjectChange> subjects = changes.subjects().stream().map(change -> {
                Async<SubjectDetail> load = subjectLoads.get(change);
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
        ReSyncLuckPermsClient targetClient = client(instanceId);
        if (targetClient == null) {
            return new Target(instanceId, name.isBlank() ? instanceId : name, !deliveries.isEmpty(), false, false,
                "ReSync Bridge Unavailable", deliveries);
        }
        ConnectionState connection = environment.connection(instanceId);
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
        return environment.client(instanceId);
    }

    private Map<String, Set<Delivery>> selected(Network network) {
        synchronized (selections) {
            Map<String, Set<Delivery>> selected = selections.get(network.networkId());
            if (selected == null || selected.isEmpty()) {
                return Map.of(source.serverId(), Delivery.all());
            }
            return selected;
        }
    }

    private Network network() {
        return environment.network(source.serverId());
    }

    private void saveSelections() {
        try {
            environment.saveSelections(Map.copyOf(selections));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Network Selection Could Not Be Saved", exception);
        }
    }

    private String entityKey(EntityType type, String id) {
        return type.name() + ":" + normalize(id).toLowerCase();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable.getCause() != null ? throwable.getCause() : throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank() ? "Permission Save Failed" : current.getMessage();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
