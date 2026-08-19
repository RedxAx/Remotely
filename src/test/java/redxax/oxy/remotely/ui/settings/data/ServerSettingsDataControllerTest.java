package redxax.oxy.remotely.ui.settings.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import restudio.rebase.platform.jvm.JvmAsyncBridge;
import restudio.rescreen.platform.Async;
import redxax.oxy.remotely.settings.server.ServerSettingsDocument;
import redxax.oxy.remotely.settings.server.ServerSettingsField;
import redxax.oxy.remotely.settings.server.ServerSettingsFieldType;
import redxax.oxy.remotely.settings.server.ServerSettingsFormat;
import redxax.oxy.remotely.settings.server.ServerSettingsPack;
import redxax.oxy.remotely.settings.server.ServerSettingsSnapshot;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.BackendFactory;
import restudio.rebase.backend.BackendFeature;
import restudio.rebase.backend.ExecutionProvider;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingEntryWidget;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.theme.ThemeManager;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerSettingsDataControllerTest {
    @BeforeAll
    static void initializeTheme() {
        ThemeManager.initBrowserDefaults();
        TextRenderer.ensureDefaultRenderer();
    }

    @Test
    void parsesAndWritesQuotedVelocityTomlValues() {
        Path root = Path.of("settings-source-" + UUID.randomUUID()).toAbsolutePath();
        MemoryFiles files = new MemoryFiles();
        Path documentPath = root.resolve("velocity.toml");
        files.put(documentPath, "[servers]\nlobby = \"127.0.0.1:25566\"\nother = \"keep\"\n");
        Instance instance = velocity(root);

        ServerSettingsDataController controller = controller(instance, files, ServerSettingsFormat.TOML,
                "velocity.toml", "servers.lobby", "127.0.0.1:25566", false);
        controller.load().join();

        ConfigOption<String> option = textOption(controller, "Proxy");
        assertEquals("127.0.0.1:25566", option.get());
        option.set("127.0.0.1:25570");
        option.apply();

        controller.save(instance).join();

        String updated = files.read(documentPath).join();
        assertTrue(updated.contains("lobby = \"127.0.0.1:25570\""));
        assertTrue(updated.contains("other = \"keep\""));
    }

    @Test
    void mergesUnrelatedExternalChanges() {
        Path root = Path.of("settings-source-" + UUID.randomUUID()).toAbsolutePath();
        MemoryFiles files = new MemoryFiles();
        Path documentPath = root.resolve("paper.properties");
        files.put(documentPath, "managed=old\nunmanaged=keep\n");
        Instance instance = velocity(root);
        ServerSettingsDataController controller = controller(instance, files, ServerSettingsFormat.PROPERTIES,
                "paper.properties", "managed", "old", false);
        controller.load().join();

        ConfigOption<String> option = textOption(controller, "Server");
        option.set("new");
        option.apply();
        files.put(documentPath, "managed=old\nunmanaged=external\n");

        controller.save(instance).join();

        String updated = files.read(documentPath).join();
        assertTrue(updated.contains("managed=new"));
        assertTrue(updated.contains("unmanaged=external"));
    }

    @Test
    void rejectsSameKeyExternalChanges() {
        Path root = Path.of("settings-source-" + UUID.randomUUID()).toAbsolutePath();
        MemoryFiles files = new MemoryFiles();
        Path documentPath = root.resolve("paper.properties");
        files.put(documentPath, "managed=old\nunmanaged=keep\n");
        Instance instance = velocity(root);
        ServerSettingsDataController controller = controller(instance, files, ServerSettingsFormat.PROPERTIES,
                "paper.properties", "managed", "old", false);
        controller.load().join();

        ConfigOption<String> option = textOption(controller, "Server");
        option.set("new");
        option.apply();
        files.put(documentPath, "managed=external\nunmanaged=keep\n");

        CompletableFuture<Void> save = JvmAsyncBridge.toFuture(controller.save(instance));
        CompletionException failure = assertThrows(CompletionException.class, () -> save.join());
        assertInstanceOf(ServerSettingsConflictException.class, failure.getCause());
        assertEquals(0, files.writeCount());
    }

    @Test
    void savesChangedValuesToDifferentNewTargetWithoutConflict() {
        Path sourceRoot = Path.of("settings-source-" + UUID.randomUUID()).toAbsolutePath();
        Path targetRoot = Path.of("settings-target-" + UUID.randomUUID()).toAbsolutePath();
        MemoryFiles sourceFiles = new MemoryFiles();
        MemoryFiles targetFiles = new MemoryFiles();
        Path sourceDocument = sourceRoot.resolve("paper.properties");
        Path targetDocument = targetRoot.resolve("paper.properties");
        sourceFiles.put(sourceDocument, "managed=old\n");
        Instance source = velocity(sourceRoot);
        ServerSettingsDataController controller = controller(source, sourceFiles, ServerSettingsFormat.PROPERTIES,
                "paper.properties", "managed", "old", false);
        controller.load().join();

        ConfigOption<String> option = textOption(controller, "Server");
        option.set("new");
        option.apply();

        BackendFactory.register("SETTINGS_MEMORY", (config, instance) -> new MemoryBackend(targetFiles));
        Instance target = velocity(targetRoot);
        target.setBackendConfig(new BackendConfig("SETTINGS_MEMORY", new HashMap<>()));

        controller.save(target).join();

        assertEquals("managed=new\n", targetFiles.read(targetDocument).join());
    }

    @Test
    void writesYamlTextAsText() {
        Path root = Path.of("settings-source-" + UUID.randomUUID()).toAbsolutePath();
        MemoryFiles files = new MemoryFiles();
        Path documentPath = root.resolve("plugin.yml");
        files.put(documentPath, "managed: old\n");
        Instance instance = velocity(root);
        ServerSettingsDataController controller = controller(instance, files, ServerSettingsFormat.YAML,
                "plugin.yml", "managed", "old", false);
        controller.load().join();

        ConfigOption<String> option = textOption(controller, "Server");
        option.set("true");
        option.apply();
        controller.save(instance).join();

        assertTrue(files.read(documentPath).join().contains("managed: 'true'"));
    }

    @Test
    void writesYamlListsWithoutQuotingAndKeepsSiblingValues() {
        Path root = Path.of("settings-source-" + UUID.randomUUID()).toAbsolutePath();
        MemoryFiles files = new MemoryFiles();
        Path documentPath = root.resolve("spigot.yml");
        files.put(documentPath, "commands:\n  replace-commands:\n  - setblock\n  keep: true\n");
        Instance instance = velocity(root);
        ServerSettingsField field = new ServerSettingsField("commands.replace", "commands.replace-commands", ServerSettingsFieldType.LIST,
                "Software Settings", "Spigot Commands", "Replaced Commands", "Commands replaced by the server.", List.of("setblock"), null, null, List.of());
        ServerSettingsDocument document = new ServerSettingsDocument("spigot.yml", ServerSettingsFormat.YAML, true, false, List.of(field));
        ServerSettingsPack pack = new ServerSettingsPack("spigot", "Spigot", "Spigot", 0, List.of("velocity"), List.of(document));
        ServerSettingsDataController controller = new DesktopServerSettingsDataController(instance, new ServerSettingsSnapshot(List.of(pack)), new MemoryApi(files));
        controller.load().join();

        ConfigOption<String> option = textOption(controller, "Software Settings");
        assertEquals("[setblock]", option.get());
        option.set("[setblock, summon]");
        option.apply();
        controller.save(instance).join();

        String updated = files.read(documentPath).join();
        assertTrue(updated.contains("replace-commands: [setblock, summon]"));
        assertTrue(updated.contains("keep: true"));
    }

    @Test
    void readsAndWritesYamlMapsAsFlowValues() {
        Path root = Path.of("settings-source-" + UUID.randomUUID()).toAbsolutePath();
        MemoryFiles files = new MemoryFiles();
        Path documentPath = root.resolve("spigot.yml");
        files.put(documentPath, "stats:\n  forced-stats: {}\n  disable-saving: false\n");
        Instance instance = velocity(root);
        ServerSettingsField field = new ServerSettingsField("stats.forced", "stats.forced-stats", ServerSettingsFieldType.MAP,
                "Software Settings", "Spigot Stats", "Forced Stats", "Forces selected statistics.", Map.of(), null, null, List.of());
        ServerSettingsDocument document = new ServerSettingsDocument("spigot.yml", ServerSettingsFormat.YAML, true, false, List.of(field));
        ServerSettingsPack pack = new ServerSettingsPack("spigot", "Spigot", "Spigot", 0, List.of("velocity"), List.of(document));
        ServerSettingsDataController controller = new DesktopServerSettingsDataController(instance, new ServerSettingsSnapshot(List.of(pack)), new MemoryApi(files));
        controller.load().join();

        ConfigOption<String> option = textOption(controller, "Software Settings");
        assertEquals("{}", option.get());
        option.set("{minecraft: 1}");
        option.apply();
        controller.save(instance).join();

        String updated = files.read(documentPath).join();
        assertTrue(updated.contains("forced-stats: {minecraft: 1}"));
        assertTrue(updated.contains("disable-saving: false"));
    }

    @Test
    void appliesRootMapBeforeNestedLeafWithoutLosingEitherMutation() {
        Path root = Path.of("settings-source-" + UUID.randomUUID()).toAbsolutePath();
        MemoryFiles files = new MemoryFiles();
        Path documentPath = root.resolve("spigot.yml");
        files.put(documentPath, "world-settings:\n  default:\n    enabled: false\n  overworld:\n    enabled: false\n");
        Instance instance = velocity(root);
        ServerSettingsField rootField = new ServerSettingsField("world-settings.dynamic", "world-settings", ServerSettingsFieldType.MAP,
                "Software Settings", "Dynamic Worlds", "World Settings", "World overrides.", Map.of(), null, null, List.of());
        ServerSettingsField leafField = new ServerSettingsField("world-settings.default.enabled", "world-settings.default.enabled", ServerSettingsFieldType.BOOLEAN,
                "Software Settings", "Default World", "Enabled", "Default world setting.", false, null, null, List.of());
        ServerSettingsDocument document = new ServerSettingsDocument("spigot.yml", ServerSettingsFormat.YAML, true, false, List.of(rootField, leafField));
        ServerSettingsPack pack = new ServerSettingsPack("spigot", "Spigot", "Spigot", 0, List.of("velocity"), List.of(document));
        ServerSettingsDataController controller = new DesktopServerSettingsDataController(instance, new ServerSettingsSnapshot(List.of(pack)), new MemoryApi(files));
        controller.load().join();

        ConfigOption<String> rootOption = textOption(controller, "Software Settings");
        rootOption.set("{default: {enabled: false}, overworld: {enabled: true}}");
        rootOption.apply();
        ConfigOption<Boolean> leafOption = booleanOption(controller, "Software Settings", 1);
        leafOption.set(true);
        leafOption.apply();
        controller.save(instance).join();

        String updated = files.read(documentPath).join();
        assertTrue(updated.contains("default: {enabled: true}"));
        assertTrue(updated.contains("overworld: {enabled: true}"));
    }

    @Test
    void mergesNestedMapMutationsWithEscapedDynamicKeys() {
        Path root = Path.of("settings-source-" + UUID.randomUUID()).toAbsolutePath();
        MemoryFiles files = new MemoryFiles();
        Path documentPath = root.resolve("spigot.yml");
        files.put(documentPath, "world-settings:\n  default:\n    enabled: false\n  world.one:\n    enabled: false\n");
        Instance instance = velocity(root);
        ServerSettingsField rootField = new ServerSettingsField("world-settings.dynamic", "world-settings", ServerSettingsFieldType.MAP,
                "Software Settings", "Dynamic Worlds", "World Settings", "World overrides.", Map.of(), null, null, List.of());
        ServerSettingsField leafField = new ServerSettingsField("world-settings.world-one.enabled", "world-settings.world\\.one.enabled", ServerSettingsFieldType.BOOLEAN,
                "Software Settings", "World One", "Enabled", "World one setting.", false, null, null, List.of());
        ServerSettingsDocument document = new ServerSettingsDocument("spigot.yml", ServerSettingsFormat.YAML, true, false, List.of(rootField, leafField));
        ServerSettingsPack pack = new ServerSettingsPack("spigot", "Spigot", "Spigot", 0, List.of("velocity"), List.of(document));
        ServerSettingsDataController controller = new DesktopServerSettingsDataController(instance, new ServerSettingsSnapshot(List.of(pack)), new MemoryApi(files));
        controller.load().join();

        ConfigOption<String> rootOption = textOption(controller, "Software Settings");
        rootOption.set("{default: {enabled: false}, world.one: {enabled: true}}");
        rootOption.apply();
        ConfigOption<Boolean> leafOption = booleanOption(controller, "Software Settings", 1);
        leafOption.set(true);
        leafOption.apply();
        controller.save(instance).join();

        String updated = files.read(documentPath).join();
        assertTrue(updated.contains("world.one: {enabled: true}"));
        assertTrue(updated.contains("default: {enabled: false}"));
    }

    @Test
    void doesNotRewriteEscapedServerPropertiesWithoutAnEdit() {
        Path root = Path.of("settings-source-" + UUID.randomUUID()).toAbsolutePath();
        MemoryFiles files = new MemoryFiles();
        files.put(root.resolve("server.properties"), "motd=Hello\\ World\n");
        Instance instance = velocity(root);
        ServerSettingsDataController controller = controller(instance, files, ServerSettingsFormat.PROPERTIES,
                "server.properties", "motd", "", false);
        controller.load().join();

        assertEquals("Hello World", textOption(controller, "Server").get());
        assertTrue(controller.changedFileContents().isEmpty());
    }

    @Test
    void browserDocumentStoreTargetsChangedServerProperty() {
        Map<String, String> documents = new LinkedHashMap<>();
        documents.put("server.properties", "# header\nmotd=Hello\\ World\nexternal=before\n");
        Map<String, String> properties = new LinkedHashMap<>();
        AtomicInteger writes = new AtomicInteger();
        ServerSettingsDocumentTarget target = new ServerSettingsDocumentTarget() {
            @Override public Collection<String> softwareTokens() { return List.of("velocity"); }
            @Override public String property(String key) { return properties.get(key); }
            @Override public void property(String key, String value) { properties.put(key, value); }
            @Override public void removeProperty(String key) { properties.remove(key); }
            @Override public void replaceProperties(Map<String, String> values) { properties.clear(); properties.putAll(values); }
        };
        ServerSettingsDocumentStore store = new ServerSettingsDocumentStore() {
            @Override public Async<Document> read(String relativePath) {
                String content = documents.get(relativePath);
                return Async.completed(content == null ? Document.missing() : new Document(true, content));
            }
            @Override public Async<Void> write(String relativePath, String content) {
                documents.put(relativePath, content);
                writes.incrementAndGet();
                return Async.completed(null);
            }
        };
        ServerSettingsField field = new ServerSettingsField("motd", "motd", ServerSettingsFieldType.TEXT, "Server",
                "Configuration", "Message", "Server message", "", null, null, List.of());
        ServerSettingsDocument document = new ServerSettingsDocument("server.properties", ServerSettingsFormat.PROPERTIES, true, false, List.of(field));
        ServerSettingsPack pack = new ServerSettingsPack("settings", "Settings", "Settings", 0, List.of("velocity"), List.of(document));
        ServerSettingsDataController controller = new ServerSettingsDocumentDataController(target, new ServerSettingsSnapshot(List.of(pack)), store);
        controller.load().join();

        ConfigOption<String> option = textOption(controller, "Server");
        option.set("Welcome Home");
        option.apply();
        documents.put("server.properties", "# header\nmotd=Hello\\ World\nexternal=after\n");
        controller.save(target).join();

        assertEquals(1, writes.get());
        assertTrue(documents.get("server.properties").contains("# header"));
        assertTrue(documents.get("server.properties").contains("motd=Welcome\\ Home"));
        assertTrue(documents.get("server.properties").contains("external=after"));
    }

    @Test
    void retriesOnlyDocumentsThatDidNotFinishWriting() {
        Path root = Path.of("settings-source-" + UUID.randomUUID()).toAbsolutePath();
        MemoryFiles files = new MemoryFiles();
        Path firstPath = root.resolve("first.properties");
        Path secondPath = root.resolve("second.properties");
        files.put(firstPath, "managed=old\n");
        files.put(secondPath, "managed=old\n");
        Instance instance = velocity(root);
        ServerSettingsDocument first = document("first.properties", "First");
        ServerSettingsDocument second = document("second.properties", "Second");
        ServerSettingsPack pack = new ServerSettingsPack("settings", "Settings", "Settings", 0, List.of("velocity"), List.of(first, second));
        ServerSettingsDataController controller = new DesktopServerSettingsDataController(instance, new ServerSettingsSnapshot(List.of(pack)), new MemoryApi(files));
        controller.load().join();

        ConfigOption<String> firstOption = textOption(controller, "First");
        firstOption.set("new");
        firstOption.apply();
        ConfigOption<String> secondOption = textOption(controller, "Second");
        secondOption.set("new");
        secondOption.apply();
        files.failNextWrite(secondPath);

        assertThrows(CompletionException.class, () -> JvmAsyncBridge.toFuture(controller.save(instance)).join());
        assertTrue(files.read(firstPath).join().contains("managed=new"));
        assertTrue(files.read(secondPath).join().contains("managed=old"));

        controller.save(instance).join();
        assertTrue(files.read(secondPath).join().contains("managed=new"));
    }

    private ServerSettingsDataController controller(Instance instance, MemoryFiles files, ServerSettingsFormat format,
                                                    String path, String key, String defaultValue, boolean createIfMissing) {
        String tab = format == ServerSettingsFormat.TOML ? "Proxy" : "Server";
        ServerSettingsField field = new ServerSettingsField("managed", key, ServerSettingsFieldType.TEXT, tab,
                "Configuration", "Managed", "Managed value", defaultValue, null, null, List.of());
        ServerSettingsDocument document = new ServerSettingsDocument(path, format, true, createIfMissing, List.of(field));
        ServerSettingsPack pack = new ServerSettingsPack("settings", "Settings", "Settings", 0, List.of("velocity"), List.of(document));
        return new DesktopServerSettingsDataController(instance, new ServerSettingsSnapshot(List.of(pack)), new MemoryApi(files));
    }

    private ServerSettingsDocument document(String path, String tab) {
        ServerSettingsField field = new ServerSettingsField("managed", "managed", ServerSettingsFieldType.TEXT, tab,
                "Configuration", "Managed", "Managed value", "old", null, null, List.of());
        return new ServerSettingsDocument(path, ServerSettingsFormat.PROPERTIES, true, false, List.of(field));
    }

    private ConfigOption<String> textOption(ServerSettingsDataController controller, String tab) {
        Setting setting = controller.settings(tab).getFirst();
        Widget widget = setting.getRows().getFirst().getWidgets().getFirst();
        return (ConfigOption<String>) ((SettingEntryWidget) widget).getOption();
    }

    private ConfigOption<Boolean> booleanOption(ServerSettingsDataController controller, String tab, int settingIndex) {
        Setting setting = controller.settings(tab).get(settingIndex);
        Widget widget = setting.getRows().getFirst().getWidgets().getFirst();
        return (ConfigOption<Boolean>) ((SettingEntryWidget) widget).getOption();
    }

    private Instance velocity(Path root) {
        Instance instance = new Instance("Velocity", "1.21.10", root.toString());
        instance.setServer(true);
        instance.setServerSoftwareType("velocity");
        return instance;
    }

    private static final class MemoryApi implements RebaseAPI {
        private final MemoryFiles files;

        private MemoryApi(MemoryFiles files) {
            this.files = files;
        }

        @Override
        public CompletableFuture<List<FileEntry>> listDirectory(Path path) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Void> copy(List<Path> sources, Path destination) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> move(List<Path> sources, Path destination) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> delete(List<Path> paths) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> rename(Path oldPath, Path newPath) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> createFile(Path path) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> createDirectory(Path path) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> upload(List<Path> localPaths, Path remotePath) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> download(List<Path> remotePaths, Path localPath) {
            return unsupported();
        }

        @Override
        public CompletableFuture<String> readFile(Path path) {
            return files.readValue(path);
        }

        @Override
        public CompletableFuture<Void> writeFile(Path path, String content) {
            return files.writeValue(path, content);
        }

        @Override
        public CompletableFuture<Boolean> fileExists(Path path) {
            return files.existsValue(path);
        }

        @Override
        public boolean canUndo() {
            return false;
        }

        @Override
        public CompletableFuture<Void> undo() {
            return unsupported();
        }

        @Override
        public Object createTtyConnector(Instance instance, int columns, int rows, java.util.function.Consumer<String> outputConsumer,
                                         java.util.function.Consumer<restudio.rebase.instance.InstanceState> stateConsumer) {
            return null;
        }

        @Override
        public CompletableFuture<String> launchServer(Instance instance) {
            return CompletableFuture.completedFuture("");
        }

        @Override
        public String getInitialDirectory(Instance instance) {
            return instance.getPath();
        }

        private CompletableFuture<Void> unsupported() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
    }

    private static final class MemoryFiles implements FileSystemProvider {
        private final Map<Path, String> files = new LinkedHashMap<>();
        private int writes;
        private Path failedWrite;

        private synchronized void put(Path path, String content) {
            files.put(normalize(path), content);
        }

        private synchronized int writeCount() {
            return writes;
        }

        private synchronized void failNextWrite(Path path) {
            failedWrite = normalize(path);
        }

        private synchronized CompletableFuture<String> readValue(Path path) {
            String value = files.get(normalize(path));
            return value == null ? CompletableFuture.failedFuture(new IllegalStateException("Missing test file")) : CompletableFuture.completedFuture(value);
        }

        private synchronized CompletableFuture<Void> writeValue(Path path, String content) {
            Path normalized = normalize(path);
            if (normalized.equals(failedWrite)) {
                failedWrite = null;
                return CompletableFuture.failedFuture(new IllegalStateException("Test write failure"));
            }
            files.put(normalized, content);
            writes++;
            return CompletableFuture.completedFuture(null);
        }

        private synchronized CompletableFuture<Boolean> existsValue(Path path) {
            return CompletableFuture.completedFuture(files.containsKey(normalize(path)));
        }

        private Path normalize(Path path) {
            return path.toAbsolutePath().normalize();
        }

        @Override
        public CompletableFuture<List<FileEntry>> ls(Path path) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Void> copy(List<Path> sources, Path destination) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> move(List<Path> sources, Path destination) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> delete(List<Path> paths) {
            return unsupported();
        }

        @Override
        public CompletableFuture<String> read(Path path) {
            return readValue(path);
        }

        @Override
        public CompletableFuture<Void> write(Path path, String content) {
            return writeValue(path, content);
        }

        @Override
        public CompletableFuture<Void> writeAtomic(Path path, String content) {
            return writeValue(path, content);
        }

        @Override
        public CompletableFuture<Void> upload(List<Path> localPaths, Path remotePath) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> download(List<Path> remotePaths, Path localPath) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> rename(Path oldPath, Path newPath) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> createFile(Path path) {
            return unsupported();
        }

        @Override
        public CompletableFuture<Void> createDirectory(Path path) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> exists(Path path) {
            return existsValue(path);
        }

        private CompletableFuture<Void> unsupported() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
    }

    private static final class MemoryBackend implements ServerBackend {
        private final MemoryFiles files;

        private MemoryBackend(MemoryFiles files) {
            this.files = files;
        }

        @Override
        public void connect() {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public FileSystemProvider getFileSystem() {
            return files;
        }

        @Override
        public ExecutionProvider getExecution() {
            return null;
        }

        @Override
        public <T extends BackendFeature> Optional<T> getFeature(Class<T> featureClass) {
            return Optional.empty();
        }

        @Override
        public <T extends BackendFeature> void registerFeature(Class<T> featureClass, T implementation) {
        }
    }

}
