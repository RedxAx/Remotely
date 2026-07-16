package redxax.oxy.remotely.network;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record NetworkLifecycleJob(int schemaVersion, String jobId, String networkId, long networkRevision, NetworkLifecycleOperation operation, NetworkLifecycleStatus status, String initiator, long createdAt, long updatedAt, int attempt, String message, List<NetworkLifecycleStep> steps) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public NetworkLifecycleJob {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        jobId = normalize(jobId);
        networkId = normalize(networkId);
        networkRevision = Math.max(1, networkRevision);
        operation = operation == null ? NetworkLifecycleOperation.START : operation;
        status = status == null ? NetworkLifecycleStatus.READY : status;
        initiator = normalize(initiator);
        long now = Instant.now().toEpochMilli();
        createdAt = createdAt <= 0 ? now : createdAt;
        updatedAt = updatedAt <= 0 ? createdAt : updatedAt;
        attempt = Math.max(0, attempt);
        message = normalize(message);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static NetworkLifecycleJob create(NetworkDefinition network, NetworkLifecycleOperation operation, String initiator, List<NetworkLifecycleStep> steps) {
        long now = Instant.now().toEpochMilli();
        return new NetworkLifecycleJob(CURRENT_SCHEMA_VERSION, UUID.randomUUID().toString(), network.networkId(), network.revision(), operation, NetworkLifecycleStatus.READY, initiator, now, now, 0, "Network operation is ready", steps);
    }

    public NetworkLifecycleJob startingAttempt() {
        return update(NetworkLifecycleStatus.RUNNING, operationMessage(), steps, attempt + 1);
    }

    public NetworkLifecycleJob withStep(NetworkLifecycleStep updatedStep) {
        List<NetworkLifecycleStep> updated = new ArrayList<>(steps.size());
        boolean found = false;
        for (NetworkLifecycleStep step : steps) {
            if (step.stepId().equals(updatedStep.stepId())) {
                updated.add(updatedStep);
                found = true;
            } else {
                updated.add(step);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Lifecycle step does not exist: " + updatedStep.stepId());
        }
        return update(status, updatedStep.message(), updated, attempt);
    }

    public NetworkLifecycleJob withStatus(NetworkLifecycleStatus updatedStatus, String updatedMessage) {
        return update(updatedStatus, updatedMessage, steps, attempt);
    }

    public boolean canResume() {
        return status == NetworkLifecycleStatus.INTERRUPTED || status == NetworkLifecycleStatus.FAILED;
    }

    private NetworkLifecycleJob update(NetworkLifecycleStatus updatedStatus, String updatedMessage, List<NetworkLifecycleStep> updatedSteps, int updatedAttempt) {
        return new NetworkLifecycleJob(schemaVersion, jobId, networkId, networkRevision, operation, updatedStatus, initiator, createdAt, Instant.now().toEpochMilli(), updatedAttempt, updatedMessage, updatedSteps);
    }

    private String operationMessage() {
        return switch (operation) {
            case START -> "Starting network";
            case STOP -> "Stopping network";
            case RESTART -> "Restarting network";
            case ROLLING_RESTART -> "Restarting Backends";
            case DRAIN -> "Draining network";
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
