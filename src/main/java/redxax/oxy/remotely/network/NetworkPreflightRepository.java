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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class NetworkPreflightRepository {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path directory;

    public NetworkPreflightRepository(Path applicationDirectory) {
        if (applicationDirectory == null) {
            throw new IllegalArgumentException("Application directory is required");
        }
        directory = applicationDirectory.resolve("networks").resolve("preflight-reports").toAbsolutePath().normalize();
    }

    public synchronized List<NetworkPreflightReport> loadAll() {
        ensureDirectory();
        List<NetworkPreflightReport> reports = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(this::isReportFile).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                reports.add(read(file));
            }
        } catch (IOException exception) {
            throw new NetworkPersistenceException("Failed to list network preflight reports", exception);
        }
        return List.copyOf(reports);
    }

    public synchronized void save(NetworkPreflightReport report) {
        ensureDirectory();
        Path target = pathFor(report.reportId());
        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory, report.reportId() + ".", ".tmp");
            Files.writeString(temporary, GSON.toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
        } catch (IOException exception) {
            throw new NetworkPersistenceException("Failed to save network preflight report", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private NetworkPreflightReport read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int schemaVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
            if (schemaVersion > NetworkPreflightReport.CURRENT_SCHEMA_VERSION) {
                throw new NetworkPersistenceException("Unsupported preflight report schema " + schemaVersion);
            }
            NetworkPreflightReport report = GSON.fromJson(root, NetworkPreflightReport.class);
            if (!file.getFileName().toString().equals(report.reportId() + ".json")) {
                throw new NetworkPersistenceException("Preflight report file name does not match its ID");
            }
            return report;
        } catch (NetworkPersistenceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new NetworkPersistenceException("Failed to read network preflight report " + file, exception);
        }
    }

    private Path pathFor(String reportId) {
        try {
            String normalizedId = UUID.fromString(reportId).toString();
            Path target = directory.resolve(normalizedId + ".json").toAbsolutePath().normalize();
            if (!target.getParent().equals(directory)) {
                throw new NetworkPersistenceException("Unsafe preflight report path");
            }
            return target;
        } catch (IllegalArgumentException exception) {
            throw new NetworkPersistenceException("Invalid preflight report ID " + reportId, exception);
        }
    }

    private boolean isReportFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json");
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new NetworkPersistenceException("Failed to create preflight report directory", exception);
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
