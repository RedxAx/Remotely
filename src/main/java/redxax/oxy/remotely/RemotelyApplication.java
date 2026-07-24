package redxax.oxy.remotely;

public enum RemotelyApplication {
    APP("remotely-app"),
    MOD("remotely-mod");

    private final String reStudioClientId;

    RemotelyApplication(String reStudioClientId) {
        this.reStudioClientId = reStudioClientId;
    }

    public String reStudioClientId() {
        return reStudioClientId;
    }
}
