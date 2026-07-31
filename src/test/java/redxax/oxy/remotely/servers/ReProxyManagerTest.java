package redxax.oxy.remotely.servers;

import org.junit.jupiter.api.Test;
import restudio.rebase.instance.Instance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReProxyManagerTest {
    @Test
    void serverIdentityIsNotInferredFromSharedPathOrPort() {
        Instance first = new Instance("First", "1.21.8", "server");
        Instance second = new Instance("Second", "1.21.8", "server");
        first.getServerProperties().setProperty("server-port", "25565");
        second.getServerProperties().setProperty("server-port", "25565");

        assertTrue(ReProxyManager.sameInstance(first, first));
        assertFalse(ReProxyManager.sameInstance(first, second));
    }
}
