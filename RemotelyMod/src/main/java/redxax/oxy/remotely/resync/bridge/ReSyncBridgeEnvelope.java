package redxax.oxy.remotely.resync.bridge;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record ReSyncBridgeEnvelope(byte protocol, byte type, UUID sessionId, int sequence, int chunkIndex, int chunkCount, byte[] payload) {
    public static final byte PROTOCOL = 1;
    public static final byte HELLO = 1;
    public static final byte AUTH_RESULT = 2;
    public static final byte DATA = 3;
    public static final byte CLOSE = 4;
    public static final byte ERROR = 5;

    public byte[] encode() {
        byte[] bytes = payload == null ? new byte[0] : payload;
        ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 16 + 4 + 4 + 4 + 4 + bytes.length);
        buffer.put(protocol);
        buffer.put(type);
        buffer.putLong(sessionId.getMostSignificantBits());
        buffer.putLong(sessionId.getLeastSignificantBits());
        buffer.putInt(sequence);
        buffer.putInt(chunkIndex);
        buffer.putInt(chunkCount);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
        return buffer.array();
    }

    public static ReSyncBridgeEnvelope decode(byte[] data) {
        if (data == null || data.length < 34) {
            throw new IllegalArgumentException("Bridge packet too short");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte protocol = buffer.get();
        byte type = buffer.get();
        UUID sessionId = new UUID(buffer.getLong(), buffer.getLong());
        int sequence = buffer.getInt();
        int chunkIndex = buffer.getInt();
        int chunkCount = buffer.getInt();
        int length = buffer.getInt();
        if (protocol != PROTOCOL || length < 0 || length != buffer.remaining()) {
            throw new IllegalArgumentException("Invalid bridge packet");
        }
        byte[] payload = new byte[length];
        buffer.get(payload);
        return new ReSyncBridgeEnvelope(protocol, type, sessionId, sequence, chunkIndex, chunkCount, payload);
    }

    public static byte[] text(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    public String payloadText() {
        return new String(payload == null ? new byte[0] : payload, StandardCharsets.UTF_8);
    }
}
