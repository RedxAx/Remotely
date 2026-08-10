package redxax.oxy.remotely.settings.server;

import restudio.rebase.instance.Instance;
import restudio.rescreen.util.WatchServiceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ServerSettingsRegistry implements AutoCloseable {
    private static final String BUILTIN_RESOURCE = "/server-settings/builtin.yml";
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
    private static final ServerSettingsRegistry INSTANCE = new ServerSettingsRegistry(true);
    private static final Logger LOGGER = Logger.getLogger(ServerSettingsRegistry.class.getName());

    private final ServerSettingsMetadataParser parser;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, ProviderRegistration> providers = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ServerSettingsSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private volatile ServerSettingsSnapshot currentSnapshot = new ServerSettingsSnapshot(List.of());
    private volatile Path externalDirectory;
    private volatile Consumer<WatchServiceManager.FileChangeEvent> externalWatcher;

    public ServerSettingsRegistry() {
        this(true);
    }

    public ServerSettingsRegistry(boolean loadBuiltIns) {
        parser = new ServerSettingsMetadataParser();
        if (loadBuiltIns) {
            loadBuiltIns();
        }
    }

    public static ServerSettingsRegistry getInstance() {
        return INSTANCE;
    }

    public static ServerSettingsRegistry empty() {
        return new ServerSettingsRegistry(false);
    }

    public void register(String providerId, int priority, Collection<ServerSettingsPack> packs) {
        String normalizedProvider = requiredProvider(providerId);
        List<ServerSettingsPack> immutablePacks = validatePacks(packs);
        ProviderRegistration registration = new ProviderRegistration(
                normalizedProvider,
                priority,
                immutablePacks,
                ProviderSource.PROGRAMMATIC,
                "programmatic:" + normalizedProvider,
                null
        );
        lock.writeLock().lock();
        try {
            providers.put(registration.sourceKey(), registration);
            rebuildLocked();
        } finally {
            lock.writeLock().unlock();
        }
        notifyListeners();
    }

    public void register(String providerId, int priority, ServerSettingsPack... packs) {
        register(providerId, priority, packs == null ? List.of() : List.of(packs));
    }

    public void register(ServerSettingsMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        register(metadata.providerId(), metadata.priority(), metadata.packs());
    }

    public void unregister(String providerId) {
        String normalizedProvider = requiredProvider(providerId);
        boolean changed;
        lock.writeLock().lock();
        try {
            changed = providers.remove("programmatic:" + normalizedProvider) != null;
            if (changed) {
                rebuildLocked();
            }
        } finally {
            lock.writeLock().unlock();
        }
        if (changed) {
            notifyListeners();
        }
    }

    public void registerProvider(String providerId, int priority, Collection<ServerSettingsPack> packs) {
        register(providerId, priority, packs);
    }

    public void unregisterProvider(String providerId) {
        unregister(providerId);
    }

    public ServerSettingsSnapshot snapshot() {
        return currentSnapshot;
    }

    public ServerSettingsSnapshot getSnapshot() {
        return snapshot();
    }

    public ServerSettingsSnapshot snapshot(Instance instance) {
        if (instance == null) {
            return new ServerSettingsSnapshot(List.of());
        }
        return new ServerSettingsSnapshot(currentSnapshot.packs().stream().filter(pack -> pack.appliesTo(instance)).toList());
    }

    public ServerSettingsSnapshot snapshotFor(Instance instance) {
        return snapshot(instance);
    }

    public ServerSettingsSnapshot getSnapshot(Instance instance) {
        return snapshot(instance);
    }

    public List<ServerSettingsPack> packs() {
        return currentSnapshot.packs();
    }

    public List<ServerSettingsPack> packs(Instance instance) {
        return snapshot(instance).packs();
    }

    public void addListener(Consumer<ServerSettingsSnapshot> listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeListener(Consumer<ServerSettingsSnapshot> listener) {
        listeners.remove(listener);
    }

    public void onChange(Consumer<ServerSettingsSnapshot> listener) {
        addListener(listener);
    }

    public void watchExternalDirectory(Path directory) {
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
        Consumer<WatchServiceManager.FileChangeEvent> watcher = WatchServiceManager.getInstance().registerIncrementalWithDebounce(
                normalized,
                ignored -> reloadExternalDirectory(),
                DEFAULT_DEBOUNCE_MILLIS
        );
        externalWatcher = watcher;
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

    public void reloadExternalDirectory() {
        Path directory = externalDirectory;
        if (directory == null) {
            return;
        }
        Map<String, ProviderRegistration> discovered = new LinkedHashMap<>();
        try (var files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(ServerSettingsRegistry::isMetadataFile)
                    .sorted()
                    .forEach(path -> loadExternalFile(path, discovered));
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Could not read external server settings metadata directory: " + directory, exception);
            return;
        }
        lock.writeLock().lock();
        try {
            providers.entrySet().removeIf(entry -> entry.getValue().source() == ProviderSource.EXTERNAL
                    && entry.getValue().sourcePath() != null
                    && entry.getValue().sourcePath().getParent().equals(directory));
            providers.putAll(discovered);
            rebuildLocked();
        } finally {
            lock.writeLock().unlock();
        }
        notifyListeners();
    }

    public void loadExternalFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        ServerSettingsMetadata metadata = parser.parse(file);
        Path sourcePath = file.toAbsolutePath().normalize();
        ProviderRegistration registration = new ProviderRegistration(
                requiredProvider(metadata.providerId()),
                metadata.priority(),
                validatePacks(metadata.packs()),
                ProviderSource.EXTERNAL,
                "external:" + sourcePath,
                sourcePath
        );
        lock.writeLock().lock();
        try {
            providers.put(registration.sourceKey(), registration);
            rebuildLocked();
        } finally {
            lock.writeLock().unlock();
        }
        notifyListeners();
    }

    @Override
    public void close() {
        stopWatchingExternalDirectory();
        listeners.clear();
    }

    private void loadBuiltIns() {
        boolean loaded = false;
        for (String resource : BUILTIN_RESOURCES) {
            try (InputStream input = ServerSettingsRegistry.class.getResourceAsStream(resource)) {
                if (input == null) {
                    continue;
                }
                loaded = true;
                ServerSettingsMetadata metadata = parser.parse(input, resource);
                ProviderRegistration registration = new ProviderRegistration(
                        requiredProvider(metadata.providerId()),
                        metadata.priority(),
                        validatePacks(metadata.packs()),
                        ProviderSource.BUILTIN,
                        "builtin:" + resource.toLowerCase(Locale.ROOT),
                        null
                );
                lock.writeLock().lock();
                try {
                    providers.put(registration.sourceKey(), registration);
                    rebuildLocked();
                } finally {
                    lock.writeLock().unlock();
                }
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not load built-in server settings metadata: " + resource, exception);
            }
        }
        if (!loaded) {
            throw new IllegalStateException("Built-in server settings metadata is unavailable: " + BUILTIN_RESOURCE);
        }
    }

    private void loadExternalFile(Path path, Map<String, ProviderRegistration> discovered) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        try {
            ServerSettingsMetadata metadata = parser.parse(normalizedPath);
            ProviderRegistration registration = new ProviderRegistration(
                    requiredProvider(metadata.providerId()),
                    metadata.priority(),
                    validatePacks(metadata.packs()),
                    ProviderSource.EXTERNAL,
                    "external:" + normalizedPath,
                    normalizedPath
            );
            discovered.put(registration.sourceKey(), registration);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Could not load external server settings metadata: " + normalizedPath, exception);
            lock.readLock().lock();
            try {
                providers.values().stream()
                        .filter(existing -> existing.source() == ProviderSource.EXTERNAL && normalizedPath.equals(existing.sourcePath()))
                        .findFirst()
                        .ifPresent(existing -> discovered.put(existing.sourceKey(), existing));
            } finally {
                lock.readLock().unlock();
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
            lock.writeLock().lock();
            try {
                boolean changed = providers.entrySet().removeIf(entry -> entry.getValue().source() == ProviderSource.EXTERNAL
                        && entry.getValue().sourcePath() != null
                        && directory.equals(entry.getValue().sourcePath().getParent()));
                if (changed) {
                    rebuildLocked();
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
        externalWatcher = null;
        externalDirectory = null;
    }

    private void rebuildLocked() {
        Map<String, List<PackCandidate>> candidates = new LinkedHashMap<>();
        for (ProviderRegistration provider : providers.values()) {
            for (ServerSettingsPack pack : provider.packs()) {
                candidates.computeIfAbsent(pack.id().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(new PackCandidate(provider, pack));
            }
        }
        List<ServerSettingsPack> selected = new ArrayList<>();
        for (List<PackCandidate> registrations : candidates.values()) {
            registrations.sort(packComparator());
            selected.add(registrations.getFirst().pack());
        }
        selected.sort(Comparator.comparingInt(ServerSettingsPack::priority).thenComparing(ServerSettingsPack::id, String.CASE_INSENSITIVE_ORDER));
        currentSnapshot = new ServerSettingsSnapshot(selected);
    }

    private static Comparator<PackCandidate> packComparator() {
        return (left, right) -> {
            int comparison = Integer.compare(right.provider().priority(), left.provider().priority());
            if (comparison != 0) return comparison;
            comparison = Integer.compare(right.pack().priority(), left.pack().priority());
            if (comparison != 0) return comparison;
            comparison = Integer.compare(right.provider().source().rank(), left.provider().source().rank());
            if (comparison != 0) return comparison;
            comparison = String.CASE_INSENSITIVE_ORDER.compare(left.provider().providerId(), right.provider().providerId());
            if (comparison != 0) return comparison;
            return String.CASE_INSENSITIVE_ORDER.compare(left.provider().sourceKey(), right.provider().sourceKey());
        };
    }

    private static List<ServerSettingsPack> validatePacks(Collection<ServerSettingsPack> packs) {
        if (packs == null || packs.isEmpty()) {
            throw new IllegalArgumentException("A settings provider needs at least one pack");
        }
        List<ServerSettingsPack> immutable = List.copyOf(packs);
        Set<String> ids = new HashSet<>();
        for (ServerSettingsPack pack : immutable) {
            if (pack == null || !ids.add(pack.id().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Settings pack IDs must be unique within a provider");
            }
        }
        return immutable;
    }

    private static String requiredProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("A provider ID is required");
        }
        return providerId.trim();
    }

    private static boolean isMetadataFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private void notifyListeners() {
        ServerSettingsSnapshot snapshot = currentSnapshot;
        for (Consumer<ServerSettingsSnapshot> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "A server settings registry listener failed", exception);
            }
        }
    }

    private record ProviderRegistration(String providerId, int priority, List<ServerSettingsPack> packs, ProviderSource source,
                                        String sourceKey, Path sourcePath) {
        private ProviderRegistration {
            packs = List.copyOf(packs);
        }
    }

    private record PackCandidate(ProviderRegistration provider, ServerSettingsPack pack) {
    }

    private enum ProviderSource {
        BUILTIN(0),
        PROGRAMMATIC(1),
        EXTERNAL(2);

        private final int rank;

        ProviderSource(int rank) {
            this.rank = rank;
        }

        private int rank() {
            return rank;
        }
    }
}
