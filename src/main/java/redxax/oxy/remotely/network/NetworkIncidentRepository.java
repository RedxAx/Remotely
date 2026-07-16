package redxax.oxy.remotely.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class NetworkIncidentRepository {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final long MAXIMUM_HISTORY_BYTES = 16L * 1024 * 1024;
    private static final int MAXIMUM_LOADED_INCIDENTS = 2000;
    private final Path directory;

    public NetworkIncidentRepository(Path applicationDirectory) {
        if (applicationDirectory == null) {
            throw new IllegalArgumentException("Application Directory Is Required");
        }
        directory = applicationDirectory.resolve("network-incidents").toAbsolutePath().normalize();
    }

    public synchronized List<NetworkIncident> load(String networkId) {
        Path file = pathFor(networkId);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            if (Files.size(file) > MAXIMUM_HISTORY_BYTES) {
                throw new NetworkPersistenceException("Incident History Is Too Large For " + networkId);
            }
        } catch (IOException exception) {
            throw new NetworkPersistenceException("Failed To Inspect Incident History For " + networkId, exception);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            NetworkIncident[] incidents = GSON.fromJson(reader, NetworkIncident[].class);
            List<NetworkIncident> loaded = incidents == null ? List.of() : Arrays.asList(incidents);
            if (loaded.size() > MAXIMUM_LOADED_INCIDENTS || loaded.stream().anyMatch(incident -> incident == null || !incident.networkId().equals(networkId))) {
                throw new NetworkPersistenceException("Incident History Does Not Match Network " + networkId);
            }
            return List.copyOf(loaded);
        } catch (NetworkPersistenceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new NetworkPersistenceException("Failed To Read Incident History For " + networkId, exception);
        }
    }

    public synchronized void save(String networkId, List<NetworkIncident> incidents) {
        ensureDirectory();
        Path target = pathFor(networkId);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory, networkId + ".", ".tmp");
            Files.writeString(temporary, GSON.toJson(incidents) + System.lineSeparator(), StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
        } catch (IOException exception) {
            throw new NetworkPersistenceException("Failed To Save Incident History For " + networkId, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public synchronized void delete(String networkId) {
        try {
            Files.deleteIfExists(pathFor(networkId));
        } catch (IOException exception) {
            throw new NetworkPersistenceException("Failed To Delete Incident History For " + networkId, exception);
        }
    }

    private Path pathFor(String networkId) {
        String normalized;
        try {
            normalized = UUID.fromString(networkId).toString();
        } catch (IllegalArgumentException exception) {
            throw new NetworkPersistenceException("Invalid Network ID " + networkId, exception);
        }
        Path target = directory.resolve(normalized + ".json").toAbsolutePath().normalize();
        if (!target.getParent().equals(directory)) {
            throw new NetworkPersistenceException("Unsafe Incident History Path For " + networkId);
        }
        return target;
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new NetworkPersistenceException("Failed To Create Incident History Directory", exception);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
