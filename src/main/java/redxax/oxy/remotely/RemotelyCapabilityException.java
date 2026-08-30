package redxax.oxy.remotely;

public final class RemotelyCapabilityException extends IllegalStateException {
    private final int status;
    private final String code;

    public RemotelyCapabilityException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code == null ? "" : code.trim();
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
