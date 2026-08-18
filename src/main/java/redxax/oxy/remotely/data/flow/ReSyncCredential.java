package redxax.oxy.remotely.data.flow;

public record ReSyncCredential(String handshakeValue, boolean transportAuthenticated) {
    public ReSyncCredential {
        handshakeValue = handshakeValue == null ? "" : handshakeValue;
    }

    public static ReSyncCredential apiKey(String value) {
        return new ReSyncCredential(value, false);
    }

    public static ReSyncCredential browserTicket() {
        return new ReSyncCredential("", true);
    }

    public boolean usable() {
        return transportAuthenticated || !handshakeValue.isBlank();
    }

    @Override
    public String toString() {
        return "ReSyncCredential[transportAuthenticated=" + transportAuthenticated
            + ", handshakePresent=" + !handshakeValue.isBlank() + "]";
    }
}
