package redxax.oxy.remotely.resync.bridge;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReSyncBridgeChunker {
    public interface PacketSink {
        void send(ReSyncBridgeEnvelope envelope);
    }

    private static final int CHUNK_SIZE = 24_000;
    private static final int MAX_REASSEMBLED_BYTES = 4_194_304;
    private static final long TIMEOUT_MS = 10_000;
    private final Map<String, PendingChunks> pending = new HashMap<>();

    public void send(UUID sessionId, int sequence, byte type, byte[] payload, PacketSink sink) {
        byte[] data = payload == null ? new byte[0] : payload;
        if (data.length > MAX_REASSEMBLED_BYTES) {
            throw new IllegalArgumentException("Bridge payload too large");
        }
        int count = Math.max(1, (data.length + CHUNK_SIZE - 1) / CHUNK_SIZE);
        for (int index = 0; index < count; index++) {
            int start = index * CHUNK_SIZE;
            int end = Math.min(data.length, start + CHUNK_SIZE);
            byte[] chunk = new byte[end - start];
            System.arraycopy(data, start, chunk, 0, chunk.length);
            sink.send(new ReSyncBridgeEnvelope(ReSyncBridgeEnvelope.PROTOCOL, type, sessionId, sequence, index, count, chunk));
        }
    }

    public byte[] accept(ReSyncBridgeEnvelope envelope) {
        cleanup();
        if (envelope.chunkCount() <= 0 || envelope.chunkIndex() < 0 || envelope.chunkIndex() >= envelope.chunkCount()) {
            throw new IllegalArgumentException("Invalid bridge chunk");
        }
        if (envelope.chunkCount() > (MAX_REASSEMBLED_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE || envelope.payload().length > CHUNK_SIZE) {
            throw new IllegalArgumentException("Bridge payload too large");
        }
        if (envelope.chunkCount() == 1) {
            if (envelope.payload().length > MAX_REASSEMBLED_BYTES) {
                throw new IllegalArgumentException("Bridge payload too large");
            }
            return envelope.payload();
        }
        String key = envelope.sessionId() + ":" + envelope.sequence() + ":" + envelope.type();
        PendingChunks chunks = pending.computeIfAbsent(key, ignored -> new PendingChunks(envelope.chunkCount()));
        if (chunks.count() != envelope.chunkCount()) {
            pending.remove(key);
            throw new IllegalArgumentException("Invalid bridge chunk sequence");
        }
        chunks.put(envelope.chunkIndex(), envelope.payload());
        if (!chunks.complete()) {
            return null;
        }
        pending.remove(key);
        return chunks.join();
    }

    public void clear() {
        pending.clear();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(entry -> now - entry.getValue().createdAt > TIMEOUT_MS);
    }

    private static class PendingChunks {
        private final byte[][] chunks;
        private final long createdAt = System.currentTimeMillis();

        private PendingChunks(int count) {
            chunks = new byte[count][];
        }

        private void put(int index, byte[] payload) {
            chunks[index] = payload == null ? new byte[0] : payload;
        }

        private boolean complete() {
            for (byte[] chunk : chunks) {
                if (chunk == null) {
                    return false;
                }
            }
            return true;
        }

        private int count() {
            return chunks.length;
        }

        private byte[] join() {
            int total = 0;
            for (byte[] chunk : chunks) {
                total += chunk.length;
                if (total > MAX_REASSEMBLED_BYTES) {
                    throw new IllegalArgumentException("Bridge payload too large");
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(total);
            for (byte[] chunk : chunks) {
                out.writeBytes(chunk);
            }
            return out.toByteArray();
        }
    }
}
