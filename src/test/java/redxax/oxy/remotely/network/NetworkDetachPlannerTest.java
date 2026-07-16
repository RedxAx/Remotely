package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkDetachPlannerTest {
    @Test
    void refusesToDetachTheLastBackend() {
        Instance proxy = new Instance("Proxy", "1.21.4", "proxy");
        proxy.setServer(true);
        proxy.setModLoader(ModLoader.VELOCITY);
        Instance lobby = new Instance("Lobby", "1.21.4", "lobby");
        lobby.setServer(true);
        lobby.setModLoader(ModLoader.PAPER);
        NetworkMember proxyMember = NetworkMember.proxy(proxy.getInstanceId(), 25565);
        NetworkMember lobbyMember = NetworkMember.backend(lobby.getInstanceId(), "lobby", NetworkMemberRole.LOBBY, 25566);
        NetworkDefinition network = NetworkDefinition.create("Network", proxy.getInstanceId(), NetworkForwardingPolicy.secureDefault("secret"), List.of(NetworkEntryPoint.primary(25565)), List.of(proxyMember, lobbyMember));
        NetworkDiscoveryResult discovery = new NetworkDiscoveryResult(network, Map.of(proxy.getInstanceId(), proxy, lobby.getInstanceId(), lobby), List.of(), List.of(), List.of());

        NetworkReconciliationPlan plan = new NetworkDetachPlanner().plan(discovery, lobby.getInstanceId());

        assertFalse(plan.canApply());
        assertTrue(plan.mutations().isEmpty());
        assertTrue(plan.issues().stream().anyMatch(issue -> issue.code().equals("detach.last-backend")));
    }

    @Test
    void removesRoutesBeforeRestoringIndependentBackendSettings() {
        Instance proxy = new Instance("Proxy", "1.21.4", "proxy");
        proxy.setServer(true);
        proxy.setModLoader(ModLoader.VELOCITY);
        Instance lobby = new Instance("Lobby", "1.21.4", "lobby");
        lobby.setServer(true);
        lobby.setModLoader(ModLoader.PAPER);
        Instance survival = new Instance("Survival", "1.21.4", "survival");
        survival.setServer(true);
        survival.setModLoader(ModLoader.PAPER);
        NetworkMember proxyMember = NetworkMember.proxy(proxy.getInstanceId(), 25565);
        NetworkMember lobbyMember = NetworkMember.backend(lobby.getInstanceId(), "lobby", NetworkMemberRole.LOBBY, 25566);
        NetworkMember survivalMember = NetworkMember.backend(survival.getInstanceId(), "survival", NetworkMemberRole.GAMEPLAY, 25567);
        NetworkDefinition base = NetworkDefinition.create("Network", proxy.getInstanceId(), NetworkForwardingPolicy.secureDefault("secret"), List.of(NetworkEntryPoint.primary(25565)), List.of(proxyMember, lobbyMember, survivalMember));
        RoutingGroup fallback = new RoutingGroup("fallback", "Fallback", RoutingStrategy.ORDERED, List.of(lobbyMember.nodeId(), survivalMember.nodeId()), Map.of(), "", Set.of("play.example.com"), "");
        NetworkDefinition network = new NetworkDefinition(base.schemaVersion(), base.networkId(), base.name(), base.revision(), base.proxyInstanceId(), base.desiredState(), base.forwarding(), base.entryPoints(), base.members(), List.of(fallback), List.of(), base.runtime(), base.features(), base.createdAt(), base.updatedAt());
        NetworkDiscoveryResult discovery = new NetworkDiscoveryResult(network, Map.of(proxy.getInstanceId(), proxy, lobby.getInstanceId(), lobby, survival.getInstanceId(), survival), List.of(), List.of(), List.of());

        NetworkReconciliationPlan plan = new NetworkDetachPlanner().plan(discovery, survival.getInstanceId());

        assertEquals(NetworkPlanStrategy.DETACH, plan.strategy());
        assertTrue(plan.mutations().stream().anyMatch(mutation -> mutation.instanceId().equals(proxy.getInstanceId()) && mutation.key().equals("servers.survival") && mutation.action() == NetworkMutationAction.REMOVE));
        assertTrue(plan.mutations().stream().anyMatch(mutation -> mutation.instanceId().equals(survival.getInstanceId()) && mutation.key().equals("online-mode") && mutation.desiredValue().equals("true")));
        assertTrue(plan.mutations().stream().anyMatch(mutation -> mutation.key().equals("network.id") && mutation.action() == NetworkMutationAction.REMOVE));
        assertTrue(plan.mutations().stream().anyMatch(mutation -> mutation.instanceId().equals(proxy.getInstanceId()) && mutation.key().equals("nodes") && !mutation.desiredValue().contains(survivalMember.nodeId())));
        assertTrue(plan.mutations().stream().anyMatch(mutation -> mutation.instanceId().equals(proxy.getInstanceId()) && mutation.key().equals("node." + survivalMember.nodeId() + ".enrollment-token-hash") && mutation.action() == NetworkMutationAction.REMOVE));
        assertTrue(plan.mutations().stream().anyMatch(mutation -> mutation.instanceId().equals(proxy.getInstanceId()) && mutation.key().equals("routes") && mutation.desiredValue().equals("lobby")));
        assertTrue(plan.mutations().stream().anyMatch(mutation -> mutation.instanceId().equals(proxy.getInstanceId()) && mutation.key().equals("route.survival.port") && mutation.action() == NetworkMutationAction.REMOVE));
        assertTrue(plan.mutations().stream().anyMatch(mutation -> mutation.instanceId().equals(survival.getInstanceId()) && mutation.path().equals("plugins/ReSync/network/node.credential") && mutation.sensitive() && mutation.desiredValue().isEmpty()));
    }

    @Test
    void detachesExternalRoutesWithoutWritingBackendFiles() {
        Instance proxy = new Instance("Proxy", "1.21.4", "proxy");
        proxy.setServer(true);
        proxy.setModLoader(ModLoader.VELOCITY);
        Instance lobby = new Instance("Lobby", "1.21.4", "lobby");
        lobby.setServer(true);
        lobby.setModLoader(ModLoader.PAPER);
        NetworkMember proxyMember = NetworkMember.proxy(proxy.getInstanceId(), 25565);
        NetworkMember lobbyMember = NetworkMember.backend(lobby.getInstanceId(), "lobby", NetworkMemberRole.LOBBY, 25566);
        NetworkMember external = new NetworkMember("external:" + UUID.randomUUID(), UUID.randomUUID().toString(), "minigames", NetworkMemberRole.GAMEPLAY, "external:10.0.0.40", "10.0.0.40", 25580, 100, false, NetworkMemberManagement.EXTERNAL);
        NetworkDefinition base = NetworkDefinition.create("Network", proxy.getInstanceId(), NetworkForwardingPolicy.secureDefault("secret"), List.of(NetworkEntryPoint.primary(25565)), List.of(proxyMember, lobbyMember, external));
        RoutingGroup fallback = new RoutingGroup("fallback", "Fallback", RoutingStrategy.ORDERED, List.of(lobbyMember.nodeId(), external.nodeId()), Map.of(), "", Set.of(), "");
        NetworkDefinition network = new NetworkDefinition(base.schemaVersion(), base.networkId(), base.name(), base.revision(), base.proxyInstanceId(), base.desiredState(), base.forwarding(), base.entryPoints(), base.members(), List.of(fallback), List.of(), base.runtime(), base.features(), base.createdAt(), base.updatedAt());
        NetworkDiscoveryResult discovery = new NetworkDiscoveryResult(network, Map.of(proxy.getInstanceId(), proxy, lobby.getInstanceId(), lobby), List.of(), List.of(), List.of());

        NetworkReconciliationPlan plan = new NetworkDetachPlanner().plan(discovery, external.instanceId());

        assertTrue(plan.canApply());
        assertTrue(plan.mutations().stream().anyMatch(mutation -> mutation.instanceId().equals(proxy.getInstanceId()) && mutation.key().equals("servers.minigames") && mutation.action() == NetworkMutationAction.REMOVE));
        assertTrue(plan.mutations().stream().noneMatch(mutation -> mutation.instanceId().equals(external.instanceId())));
    }
}
