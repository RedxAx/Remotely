package redxax.oxy.remotely.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class NetworkLifecycleJobRepository {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path directory;

    public NetworkLifecycleJobRepository(Path applicationDirectory) {
        if (applicationDirectory == null) {
            throw new IllegalArgumentException("Application directory is required");
        }
        this.directory = applicationDirectory.resolve("networks").resolve("lifecycle-jobs").toAbsolutePath().normalize();
    }

    public synchronized List<NetworkLifecycleJob> loadAll() {
        ensureDirectory();
        try (var files = Files.list(directory)) {
            return files.filter(this::isJobFile).sorted(Comparator.comparing(path -> path.getFileName().toString())).map(this::read).toList();
        } catch (IOException exception) {
            throw new NetworkPersistenceException("Failed to list network lifecycle jobs in " + directory, exception);
        }
    }

    public synchronized void save(NetworkLifecycleJob job) {
        validate(job);
        ensureDirectory();
        Path target = pathFor(job.jobId());
        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory, job.jobId() + ".", ".tmp");
            Files.writeString(temporary, GSON.toJson(job) + System.lineSeparator(), StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
        } catch (IOException exception) {
            throw new NetworkPersistenceException("Failed to save network lifecycle job " + job.jobId(), exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public Path getDirectory() {
        return directory;
    }

    private NetworkLifecycleJob read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int schemaVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
            if (schemaVersion > NetworkLifecycleJob.CURRENT_SCHEMA_VERSION) {
                throw new NetworkPersistenceException("Unsupported network lifecycle job schema " + schemaVersion + " in " + file);
            }
            NetworkLifecycleJob job = GSON.fromJson(root, NetworkLifecycleJob.class);
            validate(job);
            if (!file.getFileName().toString().equals(job.jobId() + ".json")) {
                throw new NetworkPersistenceException("Network lifecycle job file name must match its ID: " + file);
            }
            return job;
        } catch (NetworkPersistenceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new NetworkPersistenceException("Failed to read network lifecycle job " + file, exception);
        }
    }

    private void validate(NetworkLifecycleJob job) {
        if (job == null || !NetworkJob.isUuid(job.jobId())) {
            throw new NetworkPersistenceException("Network lifecycle job ID must be a UUID");
        }
        if (!NetworkJob.isUuid(job.networkId())) {
            throw new NetworkPersistenceException("Network lifecycle job network ID must be a UUID");
        }
        if (job.schemaVersion() > NetworkLifecycleJob.CURRENT_SCHEMA_VERSION) {
            throw new NetworkPersistenceException("Unsupported network lifecycle job schema " + job.schemaVersion());
        }
    }

    private Path pathFor(String jobId) {
        try {
            String normalizedId = UUID.fromString(jobId).toString();
            Path target = directory.resolve(normalizedId + ".json").toAbsolutePath().normalize();
            if (!target.getParent().equals(directory)) {
                throw new NetworkPersistenceException("Unsafe network lifecycle job path for " + jobId);
            }
            return target;
        } catch (IllegalArgumentException exception) {
            throw new NetworkPersistenceException("Invalid network lifecycle job ID " + jobId, exception);
        }
    }

    private boolean isJobFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json");
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new NetworkPersistenceException("Failed to create network lifecycle job directory " + directory, exception);
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
