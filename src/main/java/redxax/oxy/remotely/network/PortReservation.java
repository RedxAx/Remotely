package redxax.oxy.remotely.network;

public record PortReservation(String hostScope, int port, String ownerType, String ownerId, String label) {
    public PortReservation {
        hostScope = hostScope == null || hostScope.isBlank() ? "local" : hostScope.trim();
        ownerType = ownerType == null ? "" : ownerType.trim();
        ownerId = ownerId == null ? "" : ownerId.trim();
        label = label == null ? "" : label.trim();
    }
}
