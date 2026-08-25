package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.RemotelyCapabilityException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserDemoSessionExpiryTest {
    @Test
    void onlyTreatsStructuredDemoExpiryAsSessionExpiry() {
        IllegalStateException expiry = BrowserRemotelyServerApi.capabilityFailure(403,
                "{\"code\":\"reactor_demo_session_expired\",\"message\":\"Demo Ended\"}");
        assertTrue(BrowserServerScreenHost.isSessionExpired(expiry));
        assertFalse(BrowserServerScreenHost.isSessionExpired(new RemotelyCapabilityException(403, "reactor_demo_denied", "Access Denied")));
    }
}
