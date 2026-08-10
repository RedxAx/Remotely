package redxax.oxy.remotely.settings.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSettingsRegistryTest {
    @Test
    void higherPriorityExternalPackOverridesProgrammaticPackWithoutDeletingIt(@TempDir Path directory) throws Exception {
        ServerSettingsRegistry registry = ServerSettingsRegistry.empty();
        ServerSettingsPack programmatic = pack("shared", "Programmatic", 0, "paper");
        registry.register("programmatic", 10, List.of(programmatic));

        Path external = directory.resolve("external.yml");
        Files.writeString(external, """
                providerId: external
                priority: 20
                packs:
                  - id: shared
                    name: External
                    description: External metadata.
                    applicableSoftwareIds: [paper]
                    documents:
                      - path: external.yml
                        format: YAML
                        fields: []
                """);
        registry.loadExternalFile(external);

        assertEquals("External", registry.snapshot().packs().getFirst().name());
        registry.unregister("programmatic");
        assertEquals("External", registry.snapshot().packs().getFirst().name());
    }

    @Test
    void filtersByLoaderTypeCompatibilityCategoriesAndWildcards() {
        ServerSettingsPack pack = pack("paper", "Paper", 0, "paper*");
        Instance instance = new Instance("Paper", "1.21", "paper");
        instance.setServer(true);
        instance.setModLoader(ModLoader.VANILLA);
        instance.setServerSoftwareType("Paper-Preview");
        instance.setServerSoftwareCategories(List.of("plugins"));
        instance.setServerSoftwareCompatibility(List.of("Paper-Preview"));

        assertTrue(pack.appliesTo(instance));
        assertTrue(pack.appliesTo(List.of("PAPER-DEV")));
    }

    @Test
    void keepsLastValidExternalMetadataUntilTheFileIsDeleted(@TempDir Path directory) throws Exception {
        Path external = directory.resolve("plugin.yml");
        Files.writeString(external, metadata("Plugin Settings"));
        try (ServerSettingsRegistry registry = ServerSettingsRegistry.empty()) {
            registry.watchExternalDirectory(directory, 5_000);
            assertEquals("Plugin Settings", registry.snapshot().packs().getFirst().name());

            Files.writeString(external, "packs: invalid");
            registry.reloadExternalDirectory();
            assertEquals("Plugin Settings", registry.snapshot().packs().getFirst().name());

            Files.delete(external);
            registry.reloadExternalDirectory();
            assertTrue(registry.snapshot().packs().isEmpty());
        }
    }

    @Test
    void builtInsAggregateAvailablePacksIncludingMinecraftServerProperties() {
        try (ServerSettingsRegistry registry = new ServerSettingsRegistry()) {
            assertTrue(registry.packs().stream().anyMatch(pack -> pack.id().equals("purpur")));
            assertTrue(registry.packs().stream().anyMatch(pack -> pack.id().equals("minecraft-server-properties")));
            assertTrue(registry.packs().stream().flatMap(pack -> pack.documents().stream())
                    .anyMatch(document -> document.relativePath().equals("server.properties")));
        }
    }

    private static String metadata(String name) {
        return """
                providerId: plugin
                packs:
                  - id: plugin
                    name: %s
                    description: Plugin metadata.
                    software: [paper]
                    documents:
                      - path: plugins/Plugin/config.yml
                        format: yaml
                        fields: []
                """.formatted(name);
    }

    private static ServerSettingsPack pack(String id, String name, int priority, String software) {
        return new ServerSettingsPack(id, name, "A test settings pack.", priority, List.of(software), List.of(
                new ServerSettingsDocument("config/settings.yml", ServerSettingsFormat.YAML, false, false)
        ));
    }
}
