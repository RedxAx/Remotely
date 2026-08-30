package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserCapabilityIdempotencyTest {
    @Test
    void mutationsRetainAnExplicitLogicalIntentKey() {
        assertEquals("install-intent", BrowserRemotelyServerApi.requestIdempotencyKey("POST", "{\"idempotencyKey\":\"install-intent\"}"));
    }

    @Test
    void mutationsReceiveBoundedRetryKeysAndReadsDoNot() {
        String key = BrowserRemotelyServerApi.requestIdempotencyKey("DELETE", null);

        assertTrue(key.length() <= 128);
        assertNull(BrowserRemotelyServerApi.requestIdempotencyKey("GET", null));
    }
}
