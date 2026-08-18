package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;
import restudio.rebase.platform.Async;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.resource.provider.OnlineResourceVersion;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rebase.ui.screens.resources.ResourceContainerItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserResourceBrowserContextTest {
    @Test
    void directoryAggregationKeepsSuccessfulFoldersWithFailureMetadata() {
        List<BrowserResourceBrowserContext.DirectoryResult<String>> results = BrowserResourceBrowserContext.collectDirectoryResults(List.of(
                Async.completed(List.of("first.jar")),
                Async.failed(new IllegalStateException("plugins unavailable")))).join();

        assertEquals(List.of("first.jar"), results.getFirst().values());
        assertTrue(results.getFirst().failure() == null);
        assertEquals("plugins unavailable", results.get(1).failure().getMessage());
        assertTrue(results.get(1).values().isEmpty());
    }

    @Test
    void invalidationMakesCapturedOperationsStale() {
        BrowserResourceBrowserContext context = new BrowserResourceBrowserContext(null, null, "server-a", null, null, null);
        Object operation = context.captureOperation();
        ServerModels.PteroFileObjectAttributes mods = file("a.jar");
        context.reconcileDirectoryResults(List.of("/mods"), List.of(
                new BrowserResourceBrowserContext.DirectoryResult<>(List.of(mods), null)));

        assertTrue(context.isOperationCurrent(operation, null));
        context.invalidate();
        assertFalse(context.isOperationCurrent(operation, null));
        List<BrowserResourceBrowserContext.DirectoryResult<ServerModels.PteroFileObjectAttributes>> afterInvalidation =
                context.reconcileDirectoryResults(List.of("/mods"), List.of(new BrowserResourceBrowserContext.DirectoryResult<>(
                        List.of(), new IllegalStateException("mods unavailable"))));
        assertTrue(afterInvalidation.getFirst().values().isEmpty());
        assertTrue(context.resourceFailureSnapshot().stream().anyMatch(value -> "/mods".equals(value.path())));
    }

    @Test
    void failedDirectoryReusesLastGoodListingAlongsideSuccessfulDirectory() {
        BrowserResourceBrowserContext context = new BrowserResourceBrowserContext(null, null, "server-a", null, null, null);
        ServerModels.PteroFileObjectAttributes mods = file("a.jar");
        ServerModels.PteroFileObjectAttributes plugins = file("b.jar");
        ServerModels.PteroFileObjectAttributes updatedPlugins = file("c.jar");
        List<String> directories = List.of("/mods", "/plugins");

        context.reconcileDirectoryResults(directories, List.of(
                new BrowserResourceBrowserContext.DirectoryResult<>(List.of(mods), null),
                new BrowserResourceBrowserContext.DirectoryResult<>(List.of(plugins), null)));
        List<BrowserResourceBrowserContext.DirectoryResult<ServerModels.PteroFileObjectAttributes>> retained =
                context.reconcileDirectoryResults(directories, List.of(
                        new BrowserResourceBrowserContext.DirectoryResult<>(List.of(), new IllegalStateException("mods unavailable")),
                        new BrowserResourceBrowserContext.DirectoryResult<>(List.of(updatedPlugins), null)));

        assertEquals(List.of("a.jar"), names(retained.getFirst().values()));
        assertEquals(List.of("c.jar"), names(retained.get(1).values()));
        assertEquals("mods unavailable", context.resourceFailureSnapshot().stream()
                .filter(value -> "/mods".equals(value.path())).findFirst().orElseThrow().message());
    }

    @Test
    void allFailedDirectoriesReuseTheCompleteLastGoodInventory() {
        BrowserResourceBrowserContext context = new BrowserResourceBrowserContext(null, null, "server-a", null, null, null);
        List<String> directories = List.of("/mods", "/plugins");
        context.reconcileDirectoryResults(directories, List.of(
                new BrowserResourceBrowserContext.DirectoryResult<>(List.of(file("a.jar")), null),
                new BrowserResourceBrowserContext.DirectoryResult<>(List.of(file("b.jar")), null)));

        List<BrowserResourceBrowserContext.DirectoryResult<ServerModels.PteroFileObjectAttributes>> retained =
                context.reconcileDirectoryResults(directories, List.of(
                        new BrowserResourceBrowserContext.DirectoryResult<>(List.of(), new IllegalStateException("mods unavailable")),
                        new BrowserResourceBrowserContext.DirectoryResult<>(List.of(), new IllegalStateException("plugins unavailable"))));

        assertEquals(List.of("a.jar"), names(retained.getFirst().values()));
        assertEquals(List.of("b.jar"), names(retained.get(1).values()));
        assertEquals(2, context.resourceFailureSnapshot().size());
    }

    @Test
    void inventoryWarningDescribesPartialDirectoryFailure() {
        BrowserResourceBrowserContext.BrowserResourceInventory inventory = new BrowserResourceBrowserContext.BrowserResourceInventory(
                List.of(), null, List.of(new BrowserResourceBrowserContext.ResourceFailure("/plugins", "plugins unavailable")));

        assertEquals("/plugins: plugins unavailable", inventory.warning());
    }

    @Test
    void inventoryRetainsBlankHashesAndIndexesEveryPhysicalHashMatch() {
        BrowserResourceBrowserContext.InstalledFile blank = file("/mods", "local.jar", "");
        BrowserResourceBrowserContext.InstalledFile mod = file("/mods", "shared.jar", "ABC123");
        BrowserResourceBrowserContext.InstalledFile plugin = file("/plugins", "shared.jar", "sha1:abc123");

        BrowserResourceBrowserContext.InventoryIndex inventory = BrowserResourceBrowserContext.inventoryIndex(List.of(blank, mod, plugin));

        assertEquals(List.of(blank, mod, plugin), inventory.files());
        assertFalse(inventory.hashedFiles().containsKey(""));
        assertEquals(List.of(mod, plugin), inventory.hashedFiles().get("abc123"));
    }

    @Test
    void preferredProviderWinsOneAuthoritativeIdentityPerHash() {
        OnlineResourceVersion modrinth = version("modrinth-project", "modrinth-version");
        OnlineResourceVersion curseForge = version("curse-project", "curse-version");
        List<String> providers = BrowserResourceBrowserContext.prioritizeProvider(
                List.of("Modrinth", "CurseForge", "Hangar"), "CurseForge");

        Map<String, BrowserResourceBrowserContext.ProviderMatch> matches = BrowserResourceBrowserContext.authoritativeMatches(providers,
                List.of(Map.of("ABC123", curseForge), Map.of("sha1:abc123", modrinth), Map.of()));

        assertEquals(List.of("CurseForge", "Modrinth", "Hangar"), providers);
        assertEquals(1, matches.size());
        assertEquals("CurseForge", matches.get("abc123").provider());
        assertSame(curseForge, matches.get("abc123").version());
    }

    @Test
    void modpackProfileBuildsCanonicalParentChildInventoryAndDetachesOwnedResource() {
        ServerModels.ClientServerView server = new ServerModels.ClientServerView();
        server.name = "Adventure Pack";
        server.environment = Map.of("MODPACK_PROVIDER", "modrinth", "MODPACK_PROJECT_ID", "pack-id");
        String profileJson = "{\"name\":\"Adventure Pack\",\"provider\":\"modrinth\",\"projectId\":\"pack-id\"," +
                "\"versionNumber\":\"1.2.0\",\"content\":[{\"path\":\"mods/owned.jar\"},{\"path\":\"mods/preserved.jar\"}]," +
                "\"preservedConflicts\":[\"mods/preserved.jar\"]}";
        BrowserResourceBrowserContext.BrowserModpackProfile profile = BrowserResourceBrowserContext.profile(profileJson, List.of(), server);
        ResourceContainerItem owned = new ResourceContainerItem("mods/owned.jar", ResourceType.MOD, "owned.jar", true);
        ResourceContainerItem preserved = new ResourceContainerItem("mods/preserved.jar", ResourceType.MOD, "preserved.jar", true);

        List<ResourceContainerItem> resources = BrowserResourceContainerProvider.hierarchy(List.of(owned, preserved), profile);

        assertEquals(2, resources.size());
        assertTrue(resources.getFirst().isModpack());
        assertEquals(List.of(owned), resources.getFirst().getChildren());
        assertSame(preserved, resources.get(1));
        BrowserResourceBrowserContext.BrowserModpackProfile detached = profile.detach("mods/owned.jar.disabled");
        assertFalse(detached.owns("mods/owned.jar"));
        assertTrue(detached.preservedPaths().contains("mods/owned.jar"));
    }

    @Test
    void resourceGroupsPersistPerServerThroughBrowserConfigStore() {
        MemoryStorage storage = new MemoryStorage();
        BrowserRemotelyConfigStore firstStore = new BrowserRemotelyConfigStore(storage);
        BrowserResourceBrowserContext first = new BrowserResourceBrowserContext(null, null, "server-a", null, null, null, firstStore);
        first.saveResourceGroups(Map.of("Performance", List.of("mods/lithium.jar", "mods/ferritecore.jar")));

        BrowserResourceBrowserContext restored = new BrowserResourceBrowserContext(null, null, "server-a", null, null, null,
                new BrowserRemotelyConfigStore(storage));
        BrowserResourceBrowserContext otherServer = new BrowserResourceBrowserContext(null, null, "server-b", null, null, null,
                new BrowserRemotelyConfigStore(storage));

        assertEquals(Map.of("Performance", List.of("mods/lithium.jar", "mods/ferritecore.jar")), restored.resourceGroups());
        assertTrue(otherServer.resourceGroups().isEmpty());
    }

    @Test
    void rootDiscoveryAddsResourceDirectoriesOutsideTheLoaderDefault() {
        ServerModels.PteroFileObjectAttributes mods = new ServerModels.PteroFileObjectAttributes();
        mods.name = "mods";
        mods.isFile = false;

        List<String> directories = BrowserResourceBrowserContext.resourceDirectories("PAPER", "world");
        List<String> discovered = new BrowserResourceBrowserContext(null, null, "server-a", null, "PAPER", null)
                .mergeResourceDirectories(directories, List.of(mods));

        assertTrue(discovered.contains("/plugins"));
        assertTrue(discovered.contains("/mods"));
    }

    private static BrowserResourceBrowserContext.InstalledFile file(String directory, String filename, String hash) {
        return new BrowserResourceBrowserContext.InstalledFile(null, null, null, directory, filename, hash, false);
    }

    private static ServerModels.PteroFileObjectAttributes file(String name) {
        ServerModels.PteroFileObjectAttributes value = new ServerModels.PteroFileObjectAttributes();
        value.name = name;
        value.isFile = true;
        return value;
    }

    private static List<String> names(List<ServerModels.PteroFileObjectAttributes> files) {
        return files.stream().map(value -> value.name).toList();
    }

    private static OnlineResourceVersion version(String projectId, String id) {
        OnlineResourceVersion version = new OnlineResourceVersion();
        version.projectId = projectId;
        version.id = id;
        return version;
    }

    private static final class MemoryStorage implements BrowserRemotelyConfigStore.Storage {
        private final Map<String, String> values = new LinkedHashMap<>();

        @Override
        public String read(String key) {
            return values.get(key);
        }

        @Override
        public void write(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void erase(String key) {
            values.remove(key);
        }
    }
}
