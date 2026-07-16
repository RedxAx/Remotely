package redxax.oxy.remotely.network;

import java.util.UUID;

public record NetworkIncident(String incidentId, String networkId, String key, String nodeId, String type, NetworkIncidentSource source, NetworkIncidentSeverity severity, NetworkIncidentStatus status, String summary, String detail, long openedAt, long updatedAt, long resolvedAt, int occurrences) {
    public NetworkIncident {
        incidentId = requiredUuid(incidentId, "Incident ID");
        networkId = requiredUuid(networkId, "Network ID");
        key = bounded(required(key, "Incident Key"), 1024, "Incident Key");
        nodeId = bounded(normalized(nodeId), 256, "Incident Node ID");
        type = bounded(required(type, "Incident Type"), 256, "Incident Type");
        source = source == null ? NetworkIncidentSource.RUNTIME : source;
        severity = severity == null ? NetworkIncidentSeverity.WARNING : severity;
        status = status == null ? NetworkIncidentStatus.OPEN : status;
        summary = bounded(required(summary, "Incident Summary"), 512, "Incident Summary");
        detail = bounded(normalized(detail), 4096, "Incident Detail");
        if (openedAt < 0 || updatedAt < openedAt || resolvedAt < 0 || status == NetworkIncidentStatus.OPEN && resolvedAt != 0 || status == NetworkIncidentStatus.RESOLVED && resolvedAt < openedAt || occurrences < 1) {
            throw new IllegalArgumentException("Network Incident Timing Is Invalid");
        }
    }

    public static NetworkIncident open(String networkId, String key, String nodeId, String type, NetworkIncidentSource source, NetworkIncidentSeverity severity, String summary, String detail, long now) {
        return new NetworkIncident(UUID.randomUUID().toString(), networkId, key, nodeId, type, source, severity, NetworkIncidentStatus.OPEN, summary, detail, now, now, 0, 1);
    }

    public NetworkIncident refresh(NetworkIncidentSeverity nextSeverity, String nextSummary, String nextDetail, long now) {
        return new NetworkIncident(incidentId, networkId, key, nodeId, type, source, nextSeverity, NetworkIncidentStatus.OPEN, nextSummary, nextDetail, openedAt, now, 0, occurrences + 1);
    }

    public NetworkIncident resolve(long now) {
        return new NetworkIncident(incidentId, networkId, key, nodeId, type, source, severity, NetworkIncidentStatus.RESOLVED, summary, detail, openedAt, now, now, occurrences);
    }

    private static String requiredUuid(String value, String label) {
        try {
            return UUID.fromString(required(value, label)).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(label + " Is Invalid", exception);
        }
    }

    private static String required(String value, String label) {
        String normalized = normalized(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " Is Required");
        }
        return normalized;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static String bounded(String value, int maximumLength, String label) {
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(label + " Is Too Long");
        }
        return value;
    }
}
