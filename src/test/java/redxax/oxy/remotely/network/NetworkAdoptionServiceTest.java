package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkAdoptionServiceTest {
    @Test
    void parsesAndMatchesVelocityRoutesWithoutReadingSecretContents() {
        Instance proxy = instance("Proxy", ModLoader.VELOCITY, 25565);
        Instance lobby = instance("Lobby", ModLoader.PAPER, 25566);
        Instance survival = instance("Survival", ModLoader.PAPER, 25567);
        String velocity = """
                bind = "0.0.0.0:25565"
                online-mode = true
                player-info-forwarding-mode = "modern"
                forwarding-secret-file = "forwarding.secret"

                [servers]
                lobby = "127.0.0.1:25566"
                survival = "localhost:25567"
                try = ["lobby", "survival"]

                [forced-hosts]
                "play.example.com" = ["lobby", "survival"]
                "survival.example.com" = ["survival"]
                """;

        NetworkAdoptionReport report = new NetworkAdoptionService().parse(proxy, velocity, List.of(proxy, lobby, survival), List.of());

        assertTrue(report.canAdopt());
        assertEquals(ForwardingMode.MODERN, report.forwardingMode());
        assertEquals("forwarding.secret", report.secretFile());
        assertEquals(List.of("lobby", "survival"), report.fallbackRoutes());
        assertEquals(2, report.routes().stream().filter(NetworkAdoptionRoute::matched).count());
        assertEquals(List.of("survival"), report.forcedHosts().get("survival.example.com"));
    }

    @Test
    void blocksUnknownBackendsAndBrokenFallbacks() {
        Instance proxy = instance("Proxy", ModLoader.VELOCITY, 25565);
        String velocity = """
                bind = "0.0.0.0:25565"
                player-info-forwarding-mode = "none"

                [servers]
                missing = "127.0.0.1:25570"
                try = ["missing", "unknown"]
                """;

        NetworkAdoptionReport report = new NetworkAdoptionService().parse(proxy, velocity, List.of(proxy), List.of());

        assertFalse(report.canAdopt());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("adoption.route.unknown")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("adoption.fallback.unknown")));
    }

    @Test
    void recognizesTheMultilineVelocityExampleAsAnEmptyFreshProxy() {
        Instance proxy = instance("Proxy", ModLoader.VELOCITY, 25565);
        String velocity = """
                bind = "0.0.0.0:25565"
                player-info-forwarding-mode = "none"

                [servers]
                lobby = "127.0.0.1:30066"
                factions = "127.0.0.1:30067"
                minigames = "127.0.0.1:30068"
                try = [
                    "lobby"
                ]

                [forced-hosts]
                "lobby.example.com" = [
                    "lobby"
                ]
                """;

        NetworkAdoptionReport report = new NetworkAdoptionService().parse(proxy, velocity, List.of(proxy), List.of());

        assertTrue(report.routes().isEmpty());
        assertTrue(report.fallbackRoutes().isEmpty());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("adoption.stock-config")));
        assertFalse(report.issues().stream().anyMatch(issue -> issue.code().equals("adoption.route.unknown")));
    }

    @Test
    void manuallyResolvesAnUnknownRouteWithoutChangingVelocityAddress() {
        Instance proxy = instance("Proxy", ModLoader.VELOCITY, 25565);
        Instance backend = instance("Imported Survival", ModLoader.PAPER, 25590);
        String velocity = """
                bind = "0.0.0.0:25565"
                player-info-forwarding-mode = "none"

                [servers]
                survival = "10.0.0.20:25570"
                try = ["survival"]
                """;
        NetworkAdoptionService service = new NetworkAdoptionService();
        NetworkAdoptionReport scanned = service.parse(proxy, velocity, List.of(proxy), List.of());

        NetworkAdoptionReport resolved = service.resolveRoute(scanned, "survival", backend, List.of(proxy, backend), List.of());

        assertTrue(resolved.canAdopt());
        assertEquals(backend.getInstanceId(), resolved.routes().getFirst().instanceId());
        assertEquals("10.0.0.20", resolved.routes().getFirst().address());
        assertEquals(25570, resolved.routes().getFirst().port());
        assertFalse(resolved.issues().stream().anyMatch(issue -> issue.code().startsWith("adoption.route.")));
    }

    @Test
    void registersAnUnknownRouteAsExternallyManaged() {
        Instance proxy = instance("Proxy", ModLoader.VELOCITY, 25565);
        String velocity = """
                bind = "0.0.0.0:25565"
                player-info-forwarding-mode = "none"

                [servers]
                minigames = "10.0.0.40:25580"
                try = ["minigames"]
                """;
        NetworkAdoptionService service = new NetworkAdoptionService();
        NetworkAdoptionReport scanned = service.parse(proxy, velocity, List.of(proxy), List.of());

        NetworkAdoptionReport resolved = service.resolveExternalRoute(scanned, "minigames");

        assertTrue(resolved.canAdopt());
        assertTrue(resolved.routes().getFirst().matched());
        assertEquals(NetworkMemberManagement.EXTERNAL, resolved.routes().getFirst().management());
        assertTrue(resolved.routes().getFirst().instanceId().startsWith("external:"));
        assertTrue(resolved.issues().stream().anyMatch(issue -> issue.code().equals("adoption.route.external")));
        assertFalse(resolved.issues().stream().anyMatch(NetworkValidationIssue::blocksPersistence));
    }

    @Test
    void parsesLegacyPrioritiesAndForcedHostsForVelocityMigration() {
        Instance proxy = instance("Waterfall", ModLoader.WATERFALL, 25565);
        Instance lobby = instance("Lobby", ModLoader.PAPER, 25566);
        Instance survival = instance("Survival", ModLoader.PAPER, 25567);
        String legacy = """
                online_mode: true
                ip_forward: true
                listeners:
                  - host: 0.0.0.0:25565
                    priorities:
                      - lobby
                      - survival
                    forced_hosts:
                      survival.example.com: survival
                servers:
                  lobby:
                    address: 127.0.0.1:25566
                    restricted: false
                  survival:
                    address: localhost:25567
                    restricted: false
                """;

        NetworkAdoptionReport report = new NetworkAdoptionService().parseLegacy(proxy, legacy, List.of(proxy, lobby, survival), List.of());

        assertTrue(report.canAdopt());
        assertEquals(ForwardingMode.LEGACY, report.forwardingMode());
        assertEquals(List.of("lobby", "survival"), report.fallbackRoutes());
        assertEquals(List.of("survival"), report.forcedHosts().get("survival.example.com"));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("migration.plugins.review")));
    }

    private Instance instance(String name, ModLoader loader, int port) {
        Instance instance = new Instance(name, "1.21.4", name.toLowerCase());
        instance.setServer(true);
        instance.setModLoader(loader);
        instance.getServerProperties().setProperty("server-port", String.valueOf(port));
        return instance;
    }
}
