package redxax.oxy.remotely.network;

import restudio.rebase.instance.Instance;

import java.util.List;
import java.util.Map;

public record NetworkDiscoveryResult(NetworkDefinition network, Map<String, Instance> instancesById, List<NetworkMemberObservation> observations, List<PortReservation> reservations, List<NetworkValidationIssue> issues) {
    public NetworkDiscoveryResult {
        instancesById = instancesById == null ? Map.of() : Map.copyOf(instancesById);
        observations = observations == null ? List.of() : List.copyOf(observations);
        reservations = reservations == null ? List.of() : List.copyOf(reservations);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean hasBlockingIssues() {
        return issues.stream().anyMatch(NetworkValidationIssue::blocksPersistence);
    }
}
