package redxax.oxy.remotely.network;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record NetworkSyncConfiguration(NetworkSyncMode mode, Set<String> nodeIds, Set<SyncDataFamily> families, SyncLocationPolicy locationPolicy, Set<String> persistentDataNamespaces, int retainedSnapshots, int retentionDays) {
    public static final Set<SyncDataFamily> SHARED_SURVIVAL_FAMILIES = Set.of(SyncDataFamily.PRESENCE, SyncDataFamily.INVENTORY, SyncDataFamily.ENDER_CHEST, SyncDataFamily.EXPERIENCE, SyncDataFamily.VITALS, SyncDataFamily.EFFECTS, SyncDataFamily.PLAYER_STATE, SyncDataFamily.LOCATION);

    public NetworkSyncConfiguration {
        mode = mode == null ? NetworkSyncMode.PRESENCE_ONLY : mode;
        nodeIds = nodeIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(nodeIds));
        families = families == null ? Set.of(SyncDataFamily.PRESENCE) : Set.copyOf(new LinkedHashSet<>(families));
        locationPolicy = locationPolicy == null ? SyncLocationPolicy.REALM_RETURN_POINT : locationPolicy;
        persistentDataNamespaces = persistentDataNamespaces == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(persistentDataNamespaces));
        retainedSnapshots = Math.clamp(retainedSnapshots, 1, 10_000);
        retentionDays = Math.clamp(retentionDays, 1, 3650);
    }

    public static NetworkSyncConfiguration from(NetworkDefinition network) {
        Set<String> eligibleNodes = network.members().stream().filter(member -> !member.isProxy() && member.isManaged() && member.resyncEnabled()).map(NetworkMember::nodeId).collect(Collectors.toCollection(LinkedHashSet::new));
        SyncRealm realm = network.syncRealms().stream().filter(candidate -> candidate.dataFamilies().stream().anyMatch(family -> family != SyncDataFamily.PRESENCE)).findFirst().orElseGet(() -> network.syncRealms().stream().findFirst().orElse(null));
        if (realm == null) {
            return new NetworkSyncConfiguration(NetworkSyncMode.PRESENCE_ONLY, eligibleNodes, Set.of(SyncDataFamily.PRESENCE), SyncLocationPolicy.REALM_RETURN_POINT, Set.of(), 10, 30);
        }
        Set<String> nodes = realm.nodeIds().stream().filter(eligibleNodes::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (nodes.isEmpty()) nodes.addAll(eligibleNodes);
        NetworkSyncMode mode = realm.dataFamilies().equals(Set.of(SyncDataFamily.PRESENCE)) ? NetworkSyncMode.PRESENCE_ONLY : realm.dataFamilies().equals(SHARED_SURVIVAL_FAMILIES) ? NetworkSyncMode.SHARED_SURVIVAL : NetworkSyncMode.CUSTOM;
        SyncLocationPolicy location = realm.locationPolicy() == SyncLocationPolicy.NEVER ? SyncLocationPolicy.REALM_RETURN_POINT : realm.locationPolicy();
        return new NetworkSyncConfiguration(mode, nodes, realm.dataFamilies(), location, realm.persistentDataNamespaces(), realm.retainedSnapshots(), realm.retentionDays());
    }

    public List<SyncRealm> toRealms() {
        if (nodeIds.isEmpty()) throw new IllegalArgumentException("Choose At Least One ReSync Backend");
        if (mode == NetworkSyncMode.PRESENCE_ONLY) return List.of(SyncRealm.presence("presence", "Presence", nodeIds));
        Set<SyncDataFamily> selectedFamilies = new LinkedHashSet<>(mode == NetworkSyncMode.SHARED_SURVIVAL ? SHARED_SURVIVAL_FAMILIES : families);
        selectedFamilies.add(SyncDataFamily.PRESENCE);
        if (mode == NetworkSyncMode.CUSTOM && selectedFamilies.size() == 1) throw new IllegalArgumentException("Choose At Least One Player State");
        if (selectedFamilies.size() > 1 && nodeIds.size() < 2) throw new IllegalArgumentException("Player State Sync Requires At Least Two Backends");
        SyncLocationPolicy selectedLocation = selectedFamilies.contains(SyncDataFamily.LOCATION) ? locationPolicy : SyncLocationPolicy.NEVER;
        Set<String> namespaces = selectedFamilies.contains(SyncDataFamily.PERSISTENT_DATA) ? persistentDataNamespaces : Set.of();
        if (selectedFamilies.contains(SyncDataFamily.PERSISTENT_DATA) && namespaces.isEmpty()) throw new IllegalArgumentException("Persistent Data Sync Requires At Least One Namespace");
        String id = mode == NetworkSyncMode.SHARED_SURVIVAL ? "survival" : "custom";
        String name = mode == NetworkSyncMode.SHARED_SURVIVAL ? "Shared Survival" : "Custom Player Sync";
        return List.of(new SyncRealm(id, name, nodeIds, selectedFamilies, selectedLocation, namespaces, retainedSnapshots, retentionDays));
    }
}
