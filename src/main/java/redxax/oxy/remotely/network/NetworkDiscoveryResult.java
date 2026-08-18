package redxax.oxy.remotely.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NetworkDiscoveryResult {
    private final NetworkDefinition network;
    private final Map<String, Object> instancesById;
    private final List<NetworkMemberObservation> observations;
    private final List<PortReservation> reservations;
    private final List<NetworkValidationIssue> issues;

    public NetworkDiscoveryResult(NetworkDefinition network, Map<?, ?> instancesById,
                                  List<NetworkMemberObservation> observations, List<PortReservation> reservations,
                                  List<NetworkValidationIssue> issues) {
        this.network = network;
        Map<String, Object> normalizedInstances = new LinkedHashMap<>();
        if (instancesById != null) {
            instancesById.forEach((key, value) -> {
                if (key != null && value != null) {
                    normalizedInstances.put(String.valueOf(key), value);
                }
            });
        }
        this.instancesById = Map.copyOf(normalizedInstances);
        this.observations = observations == null ? List.of() : List.copyOf(observations);
        this.reservations = reservations == null ? List.of() : List.copyOf(reservations);
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public NetworkDefinition network() {
        return network;
    }

    public Map<String, Object> instancesById() {
        return instancesById;
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> instancesById(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Map<String, T> typedInstances = new LinkedHashMap<>();
        instancesById.forEach((id, instance) -> typedInstances.put(id, (T) instance));
        return Map.copyOf(typedInstances);
    }

    public List<NetworkMemberObservation> observations() {
        return observations;
    }

    public List<PortReservation> reservations() {
        return reservations;
    }

    public List<NetworkValidationIssue> issues() {
        return issues;
    }

    public boolean hasBlockingIssues() {
        return issues.stream().anyMatch(NetworkValidationIssue::blocksPersistence);
    }
}
