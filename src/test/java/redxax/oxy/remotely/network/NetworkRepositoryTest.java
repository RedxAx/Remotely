package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
        String versionOne = Files.readString(file).replace("\"schemaVersion\": 2", "\"schemaVersion\": 1").replaceAll("(?s)  \"runtime\": \\{.*?  \\},\\R", "");
        Files.writeString(file, versionOne);

        NetworkDefinition migrated = repository.loadAll().getFirst();

        assertEquals(NetworkDefinition.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertTrue(migrated.runtime().enabled());
        assertEquals(NetworkTransportSecurity.LOOPBACK, migrated.runtime().security());
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
