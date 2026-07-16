package redxax.oxy.remotely.network;

import java.time.Instant;

public record NetworkPreflightCheck(String id, String subject, String label, NetworkPreflightCheckStatus status, String detail, long checkedAt) {
    public NetworkPreflightCheck {
        id = normalize(id);
        subject = normalize(subject);
        label = normalize(label);
        status = status == null ? NetworkPreflightCheckStatus.FAILED : status;
        detail = normalize(detail);
        checkedAt = checkedAt <= 0 ? Instant.now().toEpochMilli() : checkedAt;
    }

    public static NetworkPreflightCheck passed(String id, String subject, String label, String detail) {
        return new NetworkPreflightCheck(id, subject, label, NetworkPreflightCheckStatus.PASSED, detail, 0);
    }

    public static NetworkPreflightCheck warning(String id, String subject, String label, String detail) {
        return new NetworkPreflightCheck(id, subject, label, NetworkPreflightCheckStatus.WARNING, detail, 0);
    }

    public static NetworkPreflightCheck failed(String id, String subject, String label, String detail) {
        return new NetworkPreflightCheck(id, subject, label, NetworkPreflightCheckStatus.FAILED, detail, 0);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
