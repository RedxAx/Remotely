package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrowserWebSocketTransportTest {
    @Test
    void upgradesWebSocketSchemeForSecureOriginWithoutChangingEndpoint() {
        assertEquals("wss://user:secret@restudiomc.net:8443/ws/remotely-web/resync/server%201?ticket=value&mode=flow",
            BrowserWebSocketTransport.originSafeUri(
                "ws://user:secret@restudiomc.net:8443/ws/remotely-web/resync/server%201?ticket=value&mode=flow", true));
    }

    @Test
    void preservesSecureAndDevelopmentEndpoints() {
        assertEquals("wss://restudiomc.net/ws/remotely-web/resync/server",
            BrowserWebSocketTransport.originSafeUri("wss://restudiomc.net/ws/remotely-web/resync/server", true));
        assertEquals("ws://localhost:8080/ws/remotely-web/resync/server",
            BrowserWebSocketTransport.originSafeUri("ws://localhost:8080/ws/remotely-web/resync/server", false));
    }
}
