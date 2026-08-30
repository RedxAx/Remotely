package redxax.oxy.remotely.data.flow;

import org.junit.jupiter.api.Test;
import restudio.resync.protocol.ReSyncProtocolException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReSyncDeflateCompressionTest {
    private final ReSyncDeflateCompression compression = new ReSyncDeflateCompression();

    @Test
    void delegatesCanonicalDeflateRoundTrip() {
        byte[] payload = "Remotely ReSync compression".repeat(32).getBytes(StandardCharsets.UTF_8);

        assertEquals("deflate", compression.algorithm());
        assertArrayEquals(payload, compression.decompress(compression.compress(payload), payload.length));
    }

    @Test
    void preservesMalformedPayloadRejection() {
        assertThrows(IllegalArgumentException.class,
            () -> compression.decompress(new byte[]{0x01, 0x02, 0x03}, 256));
    }

    @Test
    void preservesDecompressionBombLimit() {
        byte[] payload = new byte[1024];
        Arrays.fill(payload, (byte) 7);
        byte[] compressed = compression.compress(payload);

        ReSyncProtocolException failure = assertThrows(ReSyncProtocolException.class,
            () -> compression.decompress(compressed, 32));

        assertEquals(ReSyncProtocolException.Reason.PAYLOAD_TOO_LARGE, failure.reason());
    }
}
