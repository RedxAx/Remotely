package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsAtomicNetworkDocument() throws Exception {
        NetworkRepository repository = new NetworkRepository(directory);
        NetworkDefinition network = NetworkValidatorTest.validNetwork();

        repository.save(network);
        NetworkDefinition loaded = repository.loadAll().getFirst();

        assertEquals(network, loaded);
        assertTrue(Files.exists(repository.getDirectory().resolve(network.networkId() + ".json")));
        try (var files = Files.list(repository.getDirectory())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void roundTripsSharedDataPolicy() {
        NetworkRepository repository = new NetworkRepository(directory);
        NetworkDefinition base = NetworkValidatorTest.validNetwork();
        NetworkSharedDataPolicy policy = new NetworkSharedDataPolicy(NetworkSharedDataPolicy.SelectionMode.DENY_LIST, Set.of("staff"), 3_600_000, NetworkSharedDataPolicy.SelectionMode.ALLOW_LIST, Set.of("functions", "items"), NetworkSharedDataPolicy.ConflictPolicy.LOCAL_WINS, 262_144);
        NetworkDefinition network = base.withSharedData(base.syncRealms(), base.features(), policy);

        repository.save(network);
        NetworkDefinition loaded = repository.loadAll().getFirst();

        assertEquals(policy, loaded.sharedDataPolicy());
    }

    @Test
    void rejectsUnsupportedSchema() throws Exception {
        NetworkRepository repository = new NetworkRepository(directory);
        Files.createDirectories(repository.getDirectory());
        NetworkDefinition network = NetworkValidatorTest.validNetwork();
        Path file = repository.getDirectory().resolve(network.networkId() + ".json");
        Files.writeString(file, "{\"schemaVersion\":999,\"networkId\":\"" + network.networkId() + "\"}");

        assertThrows(NetworkPersistenceException.class, repository::loadAll);
    }

    @Test
    void migratesVersionOneDocumentsToRuntimeSchema() throws Exception {
        NetworkRepository repository = new NetworkRepository(directory);
        NetworkDefinition network = NetworkValidatorTest.validNetwork();
        repository.save(network);
        Path file = repository.getDirectory().resolve(network.networkId() + ".json");
        String versionOne = Files.readString(file).replaceAll("\"schemaVersion\": \\d+", "\"schemaVersion\": 1").replaceAll("(?s)  \"runtime\": \\{.*?  \\},\\R", "");
        Files.writeString(file, versionOne);

        NetworkDefinition migrated = repository.loadAll().getFirst();

        assertEquals(NetworkDefinition.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertTrue(migrated.runtime().enabled());
        assertEquals(NetworkTransportSecurity.LOOPBACK, migrated.runtime().security());
        assertTrue(migrated.featureEnabled(NetworkDefinition.FEATURE_SHARED_CHAT));
        assertTrue(migrated.featureEnabled(NetworkDefinition.FEATURE_SHARED_RESOURCES));
        assertEquals(NetworkSharedDataPolicy.defaults(), migrated.sharedDataPolicy());
    }

    @Test
    void deletesOnlyCanonicalNetworkFile() {
        NetworkRepository repository = new NetworkRepository(directory);
        NetworkDefinition network = NetworkValidatorTest.validNetwork();
        repository.save(network);

        repository.delete(network.networkId());

        assertTrue(repository.loadAll().isEmpty());
        assertThrows(NetworkPersistenceException.class, () -> repository.delete("../../outside"));
    }
}
