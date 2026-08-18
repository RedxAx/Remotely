package redxax.oxy.remotely.data.flow;

public record ReSyncSaveTarget(ReSyncResourceType type, String id, boolean shouldUpdateResourceState, long sequence) {
}
