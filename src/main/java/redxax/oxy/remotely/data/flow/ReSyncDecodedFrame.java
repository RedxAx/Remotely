package redxax.oxy.remotely.data.flow;

public record ReSyncDecodedFrame(byte messageType, short channel, int sequence, byte[] payload, boolean compressed) {
}
