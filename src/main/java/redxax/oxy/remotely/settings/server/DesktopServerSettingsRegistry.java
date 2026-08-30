package redxax.oxy.remotely.settings.server;

import restudio.rescreen.util.WatchServiceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DesktopServerSettingsRegistry implements ServerSettingsRegistryStorage {
    private static final String BUILTIN_RESOURCE = "/server-settings/purpur.yml";
    private static final List<String> BUILTIN_RESOURCES = List.of(
            BUILTIN_RESOURCE,
            "/server-settings/bukkit.yml",
            "/server-settings/spigot.yml",
            "/server-settings/paper-global.yml",
            "/server-settings/paper-world.yml",
            "/server-settings/server-properties.yml",
            "/server-settings/velocity.yml"
    );
    private static final long DEFAULT_DEBOUNCE_MILLIS = 250L;
    private static final Logger LOGGER = Logger.getLogger(DesktopServerSettingsRegistry.class.getName());

    private final ServerSettingsRegistry registry;
    private final DesktopServerSettingsMetadataParser parser = new DesktopServerSettingsMetadataParser();
    private final ServerSettingsMetadataFileReader fileReader = new ServerSettingsMetadataFileReader(parser);
    private final Map<String, ServerSettingsMetadata> loadedExternal = new LinkedHashMap<>();
    private volatile Path externalDirectory;
    private volatile Consumer<WatchServiceManager.FileChangeEvent> externalWatcher;

    public DesktopServerSettingsRegistry(ServerSettingsRegistry registry) {
        this(registry, true);
    }

    public DesktopServerSettingsRegistry(ServerSettingsRegistry registry, boolean loadBuiltIns) {
        this.registry = Objects.requireNonNull(registry, "registry");
        if (loadBuiltIns) {
            loadBuiltIns();
        }
    }

    public DesktopServerSettingsRegistry(ServerSettingsRegistry registry, Path externalDirectory) {
        this(registry);
        watchExternalDirectory(externalDirectory);
    }

    @Override
    public void watchExternalDirectory(String directory) {
        watchExternalDirectory(Path.of(directory), DEFAULT_DEBOUNCE_MILLIS);
    }

    public void watchExternalDirectory(Path directory) {
        watchExternalDirectory(directory, DEFAULT_DEBOUNCE_MILLIS);
    }

    @Override
    public void watchExternalDirectory(String directory, long debounceMillis) {
        watchExternalDirectory(Path.of(directory), debounceMillis);
    }

    public void watchExternalDirectory(Path directory, long debounceMillis) {
        if (debounceMillis < 0) {
            throw new IllegalArgumentException("Debounce duration cannot be negative");
        }
        Objects.requireNonNull(directory, "directory");
        Path normalized = directory.toAbsolutePath().normalize();
        stopWatchingExternalDirectory();
        try {
            Files.createDirectories(normalized);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not create metadata directory: " + normalized, exception);
        }
        externalDirectory = normalized;
        reloadExternalDirectory();
        externalWatcher = WatchServiceManager.getInstance().registerIncrementalWithDebounce(
                normalized,
                ignored -> reloadExternalDirectory(),
                debounceMillis
        );
    }

    @Override
    public void reloadExternalDirectory() {
        Path directory = externalDirectory;
        if (directory == null) {
            return;
        }
        Map<String, ServerSettingsMetadata> discovered = new LinkedHashMap<>();
        try (var files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(DesktopServerSettingsRegistry::isMetadataFile)
                    .sorted()
                    .forEach(path -> loadExternalFile(path, discovered));
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Could not read external server settings metadata directory: " + directory, exception);
            return;
        }
        synchronized (loadedExternal) {
            loadedExternal.keySet().retainAll(discovered.keySet());
            loadedExternal.putAll(discovered);
            registry.clearExternal();
            loadedExternal.forEach(registry::registerExternal);
        }
    }

    @Override
    public void loadExternalFile(String file) {
        try {
            loadExternalFile(Path.of(file));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load external server settings metadata: " + file, exception);
        }
    }

    public void loadExternalFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path sourcePath = file.toAbsolutePath().normalize();
        String sourceKey = "external:" + sourcePath;
        ServerSettingsMetadata metadata = fileReader.read(sourcePath);
        synchronized (loadedExternal) {
            loadedExternal.put(sourceKey, metadata);
        }
        registry.registerExternal(sourceKey, metadata);
    }

    @Override
    public void close() {
        stopWatchingExternalDirectory();
    }

    private void loadBuiltIns() {
        boolean loaded = false;
        for (String resource : BUILTIN_RESOURCES) {
            try (InputStream input = DesktopServerSettingsRegistry.class.getResourceAsStream(resource)) {
                if (input == null) {
                    continue;
                }
                loaded = true;
                ServerSettingsMetadata metadata = parser.parse(input, resource);
                registry.registerBuiltin("builtin:" + resource.toLowerCase(Locale.ROOT), metadata);
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not load built-in server settings metadata: " + resource, exception);
            }
        }
        if (!loaded) {
            throw new IllegalStateException("Built-in server settings metadata is unavailable: " + BUILTIN_RESOURCE);
        }
    }

    private void loadExternalFile(Path path, Map<String, ServerSettingsMetadata> discovered) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        String sourceKey = "external:" + normalizedPath;
        try {
            discovered.put(sourceKey, fileReader.read(normalizedPath));
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Could not load external server settings metadata: " + normalizedPath, exception);
            synchronized (loadedExternal) {
                ServerSettingsMetadata previous = loadedExternal.get(sourceKey);
                if (previous != null) {
                    discovered.put(sourceKey, previous);
                }
            }
        }
    }

    private void stopWatchingExternalDirectory() {
        Path directory = externalDirectory;
        Consumer<WatchServiceManager.FileChangeEvent> watcher = externalWatcher;
        if (directory != null && watcher != null) {
            WatchServiceManager.getInstance().unregisterIncremental(directory, watcher);
        }
        if (directory != null) {
            synchronized (loadedExternal) {
                loadedExternal.clear();
            }
            registry.clearExternal();
        }
        externalWatcher = null;
        externalDirectory = null;
    }

    private static boolean isMetadataFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }
}
