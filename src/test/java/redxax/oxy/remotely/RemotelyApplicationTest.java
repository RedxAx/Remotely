package redxax.oxy.remotely;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RemotelyApplicationTest {
    @Test
    void appAndModUseDifferentReStudioIdentities() {
        assertEquals("remotely-app", RemotelyApplication.APP.reStudioClientId());
        assertEquals("remotely-mod", RemotelyApplication.MOD.reStudioClientId());
        assertNotEquals(RemotelyApplication.APP.reStudioClientId(), RemotelyApplication.MOD.reStudioClientId());
    }
}
