package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkLifecycleJobTest {
    @TempDir
    Path directory;

    @Test
    void persistsInterruptedLifecycleJobsForResume() {
        NetworkDefinition network = network();
        NetworkLifecycleJobRepository repository = new NetworkLifecycleJobRepository(directory);
        NetworkLifecycleJob running = NetworkLifecycleJob.create(network, NetworkLifecycleOperation.START, "Test", List.of(NetworkLifecycleStep.pending(network.members().get(1), NetworkLifecycleAction.START, 0))).startingAttempt();
        repository.save(running);

        NetworkLifecycleJobManager reloaded = new NetworkLifecycleJobManager(directory);
        NetworkLifecycleJob recovered = reloaded.getJob(running.jobId()).orElseThrow();

        assertEquals(NetworkLifecycleStatus.INTERRUPTED, recovered.status());
        assertTrue(recovered.canResume());
    }

    @Test
    void ordersFallbackBackendsBeforeProxyForStartAndProxyFirstForStop() {
        NetworkDefinition network = network();
        NetworkLifecycleJobManager manager = new NetworkLifecycleJobManager(directory);

        List<NetworkLifecycleStep> start = manager.plan(network, NetworkLifecycleOperation.START);
        List<NetworkLifecycleStep> stop = manager.plan(network, NetworkLifecycleOperation.STOP);

        assertEquals("lobby", start.getFirst().routeName());
        assertEquals("proxy", start.getLast().routeName());
        assertEquals("proxy", stop.getFirst().routeName());
        assertEquals("lobby", stop.getLast().routeName());
    }

    @Test
    void restartContainsAFullStopThenAFullStart() {
        NetworkDefinition network = network();
        NetworkLifecycleJobManager manager = new NetworkLifecycleJobManager(directory);

        List<NetworkLifecycleStep> steps = manager.plan(network, NetworkLifecycleOperation.RESTART);

        assertEquals(network.members().size() * 2, steps.size());
        assertTrue(steps.subList(0, network.members().size()).stream().allMatch(step -> step.action() == NetworkLifecycleAction.STOP));
        assertTrue(steps.subList(network.members().size(), steps.size()).stream().allMatch(step -> step.action() == NetworkLifecycleAction.START));
    }

    @Test
    void rollingRestartDrainsGameplayBeforeFallbackAndHealthGatesEveryBackend() {
        NetworkDefinition network = network();
        NetworkLifecycleJobManager manager = new NetworkLifecycleJobManager(directory);

        List<NetworkLifecycleStep> steps = manager.plan(network, NetworkLifecycleOperation.ROLLING_RESTART);

        assertEquals(14, steps.size());
        assertEquals("survival", steps.getFirst().routeName());
        assertEquals(List.of(NetworkLifecycleAction.CAPACITY_GATE, NetworkLifecycleAction.MAINTENANCE, NetworkLifecycleAction.DRAIN, NetworkLifecycleAction.STOP, NetworkLifecycleAction.START, NetworkLifecycleAction.HEALTH_GATE, NetworkLifecycleAction.RESUME), steps.subList(0, 7).stream().map(NetworkLifecycleStep::action).toList());
        assertEquals("lobby", steps.getLast().routeName());
        assertEquals(NetworkLifecycleAction.RESUME, steps.getLast().action());
    }

    private NetworkDefinition network() {
        String proxyInstanceId = UUID.randomUUID().toString();
        NetworkMember proxy = NetworkMember.proxy(proxyInstanceId, 25565);
        NetworkMember lobby = NetworkMember.backend(UUID.randomUUID().toString(), "lobby", NetworkMemberRole.LOBBY, 25566);
        NetworkMember survival = NetworkMember.backend(UUID.randomUUID().toString(), "survival", NetworkMemberRole.GAMEPLAY, 25567);
        NetworkDefinition base = NetworkDefinition.create("Network", proxyInstanceId, NetworkForwardingPolicy.secureDefault("secret"), List.of(NetworkEntryPoint.primary(25565)), List.of(proxy, lobby, survival));
        RoutingGroup fallback = new RoutingGroup("fallback", "Fallback", RoutingStrategy.ORDERED, List.of(lobby.nodeId()), Map.of(), "", Set.of(), "");
        return new NetworkDefinition(base.schemaVersion(), base.networkId(), base.name(), base.revision(), base.proxyInstanceId(), base.desiredState(), base.forwarding(), base.entryPoints(), base.members(), List.of(fallback), base.syncRealms(), base.runtime(), base.features(), base.createdAt(), base.updatedAt());
    }
}
