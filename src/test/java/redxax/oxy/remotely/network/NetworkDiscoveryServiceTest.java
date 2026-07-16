package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NetworkDiscoveryServiceTest {
    @Test
    void ignoresConflictsThatDoNotUseANetworkEndpoint() {
        Instance proxy = instance("Proxy", ModLoader.VELOCITY, 25570);
        Instance lobby = instance("Lobby", ModLoader.PAPER, 25571);
        lobby.getServerProperties().setProperty("online-mode", "false");
        Instance firstStandalone = instance("Dev", ModLoader.PAPER, 25565);
        Instance secondStandalone = instance("Test", ModLoader.PAPER, 25565);
        NetworkMember proxyMember = NetworkMember.proxy(proxy.getInstanceId(), 25570);
        NetworkMember lobbyMember = NetworkMember.backend(lobby.getInstanceId(), "lobby", NetworkMemberRole.LOBBY, 25571);
        NetworkDefinition network = NetworkDefinition.create("Network", proxy.getInstanceId(), NetworkForwardingPolicy.secureDefault("secret"), List.of(NetworkEntryPoint.primary(25570)), List.of(proxyMember, lobbyMember));

        NetworkDiscoveryResult result = new NetworkDiscoveryService(new NetworkPortAllocator()).discover(network, List.of(proxy, lobby, firstStandalone, secondStandalone), List.of(network), List.of());

        assertFalse(result.issues().stream().anyMatch(issue -> issue.code().equals("port.conflict")));
    }

    private Instance instance(String name, ModLoader loader, int port) {
        Instance instance = new Instance(name, "1.21.4", name.toLowerCase());
        instance.setServer(true);
        instance.setModLoader(loader);
        instance.getServerProperties().setProperty("server-port", String.valueOf(port));
        return instance;
    }
}
