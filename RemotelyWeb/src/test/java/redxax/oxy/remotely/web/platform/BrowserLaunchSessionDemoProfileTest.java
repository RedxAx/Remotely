package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserLaunchSessionDemoProfileTest {
    @Test
    void demoProfileIsExplicitlyScopeBound() {
        BrowserLaunchSession.Metadata demo = new BrowserLaunchSession.Metadata("lease", "ticket", "audience",
                Set.of("remotely.session", "remotely.demo"), "node", "expiry");
        BrowserLaunchSession.Metadata normal = new BrowserLaunchSession.Metadata("grant", "ticket", "audience",
                Set.of("remotely.session"), "node", "expiry");

        assertTrue(demo.demo());
        assertFalse(normal.demo());
    }

    @Test
    void demoLeaseDeadlineIsAvailableToCanonicalChrome() {
        BrowserLaunchSession.demoLeaseExpires("2030-01-01T00:00:00Z");

        assertEquals("2030-01-01T00:00:00Z", BrowserLaunchSession.demoLeaseExpiresAt());
        BrowserLaunchSession.demoLeaseExpires("");
    }

    @Test
    void exposesOnlyServerApprovedEditablePaths() {
        BrowserLaunchSession.demoEditablePaths("/server.properties\n/config/demo.yml");

        assertTrue(BrowserLaunchSession.demoPathEditable("server.properties", true));
        assertTrue(BrowserLaunchSession.demoPathEditable("/config/demo.yml", true));
        assertFalse(BrowserLaunchSession.demoPathEditable("/config/other.yml", true));
        assertFalse(BrowserLaunchSession.demoPathEditable("/server.properties", false));
        BrowserLaunchSession.demoEditablePaths("");
    }
}
