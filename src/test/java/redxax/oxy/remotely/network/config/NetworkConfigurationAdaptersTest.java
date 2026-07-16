package redxax.oxy.remotely.network.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkConfigurationAdaptersTest {
    @Test
    void propertiesPreservesUnmanagedLines() {
        PropertiesConfigurationAdapter adapter = new PropertiesConfigurationAdapter();
        String source = "# Server\r\nonline-mode=true\r\nmotd=Keep Me\r\n";

        String updated = adapter.apply(source, "online-mode", "false");

        assertEquals("false", adapter.read(updated, "online-mode"));
        assertTrue(updated.contains("motd=Keep Me"));
        assertTrue(updated.contains("\r\n"));
        assertTrue(adapter.remove(updated, "online-mode").contains("motd=Keep Me"));
    }

    @Test
    void tomlUpdatesSectionsAndQuotesForcedHosts() {
        TomlConfigurationAdapter adapter = new TomlConfigurationAdapter();
        String source = "bind = \"0.0.0.0:25565\"\n\n[servers]\nlobby = \"127.0.0.1:25566\"\ntry = [\"lobby\"]\n\n[advanced]\ncompression-threshold = 256\n";

        String updated = adapter.apply(source, "servers.lobby", "\"127.0.0.1:25570\"");
        updated = adapter.apply(updated, "forced-hosts.play.example.com", "[\"lobby\"]");

        assertEquals("\"127.0.0.1:25570\"", adapter.read(updated, "servers.lobby"));
        assertEquals("[\"lobby\"]", adapter.read(updated, "forced-hosts.play.example.com"));
        assertTrue(updated.contains("compression-threshold = 256"));
        assertTrue(updated.contains("\"play.example.com\" = [\"lobby\"]"));
        assertEquals("", adapter.read(adapter.remove(updated, "servers.lobby"), "servers.lobby"));
    }

    @Test
    void tomlReplacesAndRemovesWholeMultilineArrays() {
        TomlConfigurationAdapter adapter = new TomlConfigurationAdapter();
        String source = """
                [servers]
                lobby = "127.0.0.1:30066"
                try = [
                    "lobby"
                ]

                [forced-hosts]
                "lobby.example.com" = [
                    "lobby"
                ]
                """;

        String updated = adapter.apply(source, "servers.try", "[\"survival\"]");
        updated = adapter.remove(updated, "forced-hosts.lobby.example.com");

        assertEquals("[\"survival\"]", adapter.read(updated, "servers.try"));
        assertEquals("", adapter.read(updated, "forced-hosts.lobby.example.com"));
        assertTrue(updated.contains("lobby = \"127.0.0.1:30066\""));
        assertFalse(updated.contains("    \"lobby\""));
    }

    @Test
    void tomlClearsManagedSectionsWithoutLeavingMultilineValues() {
        TomlConfigurationAdapter adapter = new TomlConfigurationAdapter();
        String source = """
                [servers]
                lobby = "127.0.0.1:30066"
                try = [
                    "lobby"
                ]

                [advanced]
                compression-threshold = 256
                """;

        String updated = adapter.remove(source, "servers.*");

        assertTrue(adapter.contains(source, "servers.*"));
        assertFalse(adapter.contains(updated, "servers.*"));
        assertTrue(updated.contains("compression-threshold = 256"));
        assertFalse(updated.contains("127.0.0.1:30066"));
        assertFalse(updated.contains("    \"lobby\""));
    }

    @Test
    void tomlCreatesExplicitEmptySectionWhenClearingMissingDefaults() {
        TomlConfigurationAdapter adapter = new TomlConfigurationAdapter();
        String source = "config-version = \"2.8\"\n\n[servers]\nlobby = \"127.0.0.1:25566\"\n";

        String updated = adapter.remove(source, "forced-hosts.*");

        assertTrue(updated.contains("[forced-hosts]"));
        assertFalse(adapter.contains(updated, "forced-hosts.*"));
        assertEquals(updated, adapter.remove(updated, "forced-hosts.*"));
    }

    @Test
    void yamlUpdatesNestedValuesWithoutRewritingDocument() {
        YamlConfigurationAdapter adapter = new YamlConfigurationAdapter();
        String source = "settings:\n  bungeecord: true # Keep Note\nworld-settings:\n  default:\n    verbose: false\n";

        String updated = adapter.apply(source, "settings.bungeecord", "false");
        updated = adapter.apply(updated, "proxies.velocity.enabled", "true");
        updated = adapter.apply(updated, "proxies.velocity.secret", "a-secret");

        assertEquals("false", adapter.read(updated, "settings.bungeecord"));
        assertEquals("true", adapter.read(updated, "proxies.velocity.enabled"));
        assertEquals("a-secret", adapter.read(updated, "proxies.velocity.secret"));
        assertTrue(updated.contains("# Keep Note"));
        assertTrue(updated.contains("world-settings:\n  default:\n    verbose: false"));
        assertEquals("", adapter.read(adapter.remove(updated, "proxies.velocity.secret"), "proxies.velocity.secret"));
    }
}
