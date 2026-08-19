package redxax.oxy.remotely.network;

import restudio.rescreen.platform.Clock;

import java.util.List;
import java.util.UUID;

public record NetworkPreflightReport(int schemaVersion, String reportId, String networkId, long networkRevision, NetworkPreflightStatus status, long startedAt, long completedAt, String summary, List<NetworkPreflightCheck> checks) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public NetworkPreflightReport {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        reportId = normalize(reportId);
        networkId = normalize(networkId);
        networkRevision = Math.max(1, networkRevision);
        status = status == null ? NetworkPreflightStatus.RUNNING : status;
        startedAt = startedAt <= 0 ? Clock.system().millis() : startedAt;
        completedAt = Math.max(0, completedAt);
        summary = normalize(summary);
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    public static NetworkPreflightReport running(NetworkDefinition network) {
        return new NetworkPreflightReport(CURRENT_SCHEMA_VERSION, UUID.randomUUID().toString(), network.networkId(), network.revision(), NetworkPreflightStatus.RUNNING, 0, 0, "Checking Join Path", List.of());
    }

    public NetworkPreflightReport completed(List<NetworkPreflightCheck> completedChecks) {
        List<NetworkPreflightCheck> safeChecks = completedChecks == null ? List.of() : List.copyOf(completedChecks);
        long failed = safeChecks.stream().filter(check -> check.status() == NetworkPreflightCheckStatus.FAILED).count();
        long warnings = safeChecks.stream().filter(check -> check.status() == NetworkPreflightCheckStatus.WARNING).count();
        NetworkPreflightStatus completedStatus = failed == 0 ? NetworkPreflightStatus.SUCCEEDED : NetworkPreflightStatus.FAILED;
        String completedSummary = failed > 0 ? failed + " Checks Failed" : warnings > 0 ? "Join Path Ready With " + warnings + " Warnings" : "Join Path Ready";
        return new NetworkPreflightReport(schemaVersion, reportId, networkId, networkRevision, completedStatus, startedAt, Clock.system().millis(), completedSummary, safeChecks);
    }

    public NetworkPreflightReport interrupted() {
        return completed(List.of(NetworkPreflightCheck.failed("interrupted", networkId, "Preflight Interrupted", "Remotely Closed Before Verification Finished")));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
