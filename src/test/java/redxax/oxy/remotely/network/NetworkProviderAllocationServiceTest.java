package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.feature.ServerInfoFeature;
import restudio.rebase.instance.Instance;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkProviderAllocationServiceTest {
    private final NetworkProviderAllocationService service = new NetworkProviderAllocationService();

    @Test
    void preservesTheExactProviderAllocation() {
        Instance instance = provider("Lobby", "server-one");

        NetworkProviderAllocation allocation = service.allocation(instance, new ServerInfoFeature.ServerConnectionInfo("10.0.0.12", "25572"));

        assertEquals(instance.getInstanceId(), allocation.instanceId());
        assertEquals("PTERO", allocation.provider());
        assertEquals("10.0.0.12", allocation.address());
        assertEquals(25572, allocation.port());
        assertEquals("ptero:panel-one:10.0.0.12", allocation.hostScope());
    }

    @Test
    void scopesCollisionsByControllerAndAllocationAddress() {
        Instance first = provider("Lobby", "server-one");
        Instance second = provider("Survival", "server-two");

        String firstScope = NetworkHostScope.resolveProviderAllocation(first, "10.0.0.12");
        String secondScope = NetworkHostScope.resolveProviderAllocation(second, "10.0.0.12");
        String otherAddressScope = NetworkHostScope.resolveProviderAllocation(second, "10.0.0.13");

        assertEquals(firstScope, secondScope);
        assertNotEquals(firstScope, otherAddressScope);
    }

    @Test
    void rejectsUnavailableProviderEndpoints() {
        Instance instance = provider("Lobby", "server-one");

        assertThrows(IllegalStateException.class, () -> service.allocation(instance, new ServerInfoFeature.ServerConnectionInfo("Unknown", "0")));
        assertThrows(IllegalStateException.class, () -> service.allocation(instance, new ServerInfoFeature.ServerConnectionInfo("10.0.0.12", "invalid")));
    }

    private Instance provider(String name, String identifier) {
        Instance instance = new Instance(name, "1.21.4", name.toLowerCase());
        instance.setBackendConfig(new BackendConfig("PTERO", Map.of("hostId", "panel-one", "identifier", identifier)));
        return instance;
    }
}
