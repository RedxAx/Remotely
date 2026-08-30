package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserServerScreenHostAuthenticationTest {
    @Test
    void sameAuthenticatedSubjectKeepsAuthenticationEpoch() {
        assertFalse(BrowserServerScreenHost.authenticationEpochChanged(true, true, "user-1", true, "user-1"));
    }

    @Test
    void subjectOrAuthenticationChangeAdvancesAuthenticationEpoch() {
        assertTrue(BrowserServerScreenHost.authenticationEpochChanged(true, true, "user-1", true, "user-2"));
        assertTrue(BrowserServerScreenHost.authenticationEpochChanged(true, true, "user-1", false, ""));
    }

    @Test
    void developerBindingCompletionRequiresSameAccountGeneration() {
        BrowserServerScreenHost.HostContext request = new BrowserServerScreenHost.HostContext(4, "user-1");

        assertTrue(BrowserServerScreenHost.sameHostContext(request,
                new BrowserServerScreenHost.HostContext(4, "user-1")));
        assertFalse(BrowserServerScreenHost.sameHostContext(request,
                new BrowserServerScreenHost.HostContext(5, "user-1")));
        assertFalse(BrowserServerScreenHost.sameHostContext(request,
                new BrowserServerScreenHost.HostContext(4, "user-2")));
        assertFalse(BrowserServerScreenHost.sameHostContext(request, null));
    }
}
