package redxax.oxy.remotely.host;

import org.junit.jupiter.api.Test;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.instance.Instance;
import restudio.rebase.restudio.api.models.ServerModels;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopServerHostIdentityTest {
    @Test
    void remoteIdentityRequiresTheOwningHost() {
        Instance instance = new Instance("Shared Name", "1.21.8", "/servers/shared");
        instance.setBackendConfig(new BackendConfig("SSH", new LinkedHashMap<>()));
        instance.getBackendConfig().credentials.put("hostId", "host-a");

        ServerModels.ClientServerView server = new ServerModels.ClientServerView();
        server.identifier = instance.getInstanceId();
        server.environment = new LinkedHashMap<>();
        server.environment.put("remotely.desktopHostId", "host-a");

        assertTrue(DesktopServerHost.matchesDesktopIdentity(server, instance));

        server.environment.put("remotely.desktopHostId", "host-b");

        assertFalse(DesktopServerHost.matchesDesktopIdentity(server, instance));
    }

    @Test
    void terminalInputRoutesPreserveEachBackendTransport() {
        assertEquals(DesktopServerHost.TerminalInputRoute.DIRECT, DesktopServerHost.terminalInputRoute("LOCAL"));
        assertEquals(DesktopServerHost.TerminalInputRoute.DIRECT, DesktopServerHost.terminalInputRoute("RESTUDIO"));
        assertEquals(DesktopServerHost.TerminalInputRoute.DIRECT, DesktopServerHost.terminalInputRoute("SSH"));
        assertEquals(DesktopServerHost.TerminalInputRoute.BACKEND_API, DesktopServerHost.terminalInputRoute("PTERO"));
        assertEquals(DesktopServerHost.TerminalInputRoute.BACKEND_API, DesktopServerHost.terminalInputRoute("CALAGOPUS"));
    }

    @Test
    void unresolvedDesktopInstanceRetainsTheServerViewTransport() {
        ServerModels.ClientServerView server = new ServerModels.ClientServerView();
        server.backendType = "SSH";

        assertEquals("SSH", DesktopServerHost.terminalBackendType(server, null));
        assertEquals(DesktopServerHost.TerminalInputRoute.DIRECT,
                DesktopServerHost.terminalInputRoute(DesktopServerHost.terminalBackendType(server, null)));
    }
}
