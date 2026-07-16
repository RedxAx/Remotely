package redxax.oxy.remotely.ui.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkRouteMappingFlowTest {
    @Test
    void validatesRouteNumbers() {
        assertEquals(1, NetworkRouteMappingFlow.parsePort(" 1 "));
        assertEquals(65535, NetworkRouteMappingFlow.parsePort("65535"));
        assertEquals(0, NetworkRouteMappingFlow.parseCapacity("0"));
        assertThrows(IllegalArgumentException.class, () -> NetworkRouteMappingFlow.parsePort("0"));
        assertThrows(IllegalArgumentException.class, () -> NetworkRouteMappingFlow.parsePort("port"));
        assertThrows(IllegalArgumentException.class, () -> NetworkRouteMappingFlow.parseCapacity("-1"));
    }

    @Test
    void normalizesRouteAndDisplayNames() {
        assertEquals("survival-server", NetworkRouteMappingFlow.routeName(" Survival Server "));
        assertEquals("server", NetworkRouteMappingFlow.routeName("***"));
        assertEquals("Proxy Offline Mode", NetworkRouteMappingFlow.titleCase("PROXY_OFFLINE_MODE"));
    }

    @Test
    void recognizesLoopbackAddressesAndUnwrapsAsyncFailures() {
        assertTrue(NetworkRouteMappingFlow.loopback("localhost"));
        assertTrue(NetworkRouteMappingFlow.loopback("127.0.0.1"));
        assertTrue(NetworkRouteMappingFlow.loopback("::1"));
        assertFalse(NetworkRouteMappingFlow.loopback("10.0.0.2"));
        assertEquals("Route Failed", NetworkRouteMappingFlow.rootMessage(new CompletionException(new IllegalStateException("Route Failed"))));
    }
}
