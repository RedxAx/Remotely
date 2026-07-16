package redxax.oxy.remotely.network;

public record NetworkMemberObservation(String nodeId, String instanceId, boolean instanceAvailable, String observedHostScope, int observedPort, String software, NetworkObservationState state, long observedAt) {
}
