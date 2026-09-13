package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.packcontent.GlyphPreviewAccess;
import redxax.oxy.remotely.ui.server.ServerUiCapabilityProvider;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rescreen.platform.Async;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserGlyphPreviewAccessTest {
    @Test
    void keepsValidGlyphsWhenAnotherYamlCannotBeRead() {
        FakeFiles files = catalogFiles();
        files.file("plugins/Nexo/glyphs/broken.yml", Async.failed(new IllegalStateException("Unreadable")));
        BrowserGlyphPreviewAccess access = new BrowserGlyphPreviewAccess(null, files, server());

        access.refresh().join();

        List<GlyphPreviewAccess.Preview> previews = access.resolveGlyphs("ꐓ");
        assertEquals(1, previews.size());
        assertEquals("plugins/Nexo/pack/assets/custom/textures/icon.png", previews.getFirst().glyph().assetRef().resolvedPath());
    }

    @Test
    void ignoresRefreshCompletionAfterClose() {
        FakeFiles files = catalogFiles();
        Async<String> delayed = Async.pending();
        files.file("plugins/Nexo/glyphs/icons.yml", delayed);
        BrowserGlyphPreviewAccess access = new BrowserGlyphPreviewAccess(null, files, server());

        Async<Void> refresh = access.refresh();
        access.close();
        delayed.complete("test_icon:\n  texture: icon\n  char: 'ꐓ'\n  height: 8\n");
        refresh.join();

        assertTrue(access.resolveGlyphs("ꐓ").isEmpty());
    }

    @Test
    void keepsReferenceIndexInConfigAndTagPreviewMatches() {
        FakeFiles files = catalogFiles();
        files.file("plugins/Nexo/glyphs/icons.yml", Async.completed("test_icon:\n  texture: icon\n  char: 'ꐓ'\n  height: 8\ntest_alias:\n  reference: test_icon\n  index: 3\n"));
        BrowserGlyphPreviewAccess access = new BrowserGlyphPreviewAccess(null, files, server());

        access.refresh().join();

        assertEquals(Integer.valueOf(2), access.resolveGlyph("nexo", "test_alias", null).orElseThrow().match().indexStart());
        assertEquals(Integer.valueOf(2), access.resolveGlyphs("<g:test_alias>").getFirst().match().indexStart());
    }

    private static FakeFiles catalogFiles() {
        FakeFiles files = new FakeFiles();
        files.directory("plugins/Nexo/glyphs", entry("icons.yml", true), entry("broken.yml", true));
        files.directory("plugins/Nexo/pack/assets", entry("custom", false));
        files.directory("plugins/Nexo/pack/assets/minecraft/textures");
        files.directory("plugins/Nexo/pack/assets/nexo/textures");
        files.directory("plugins/Nexo/pack/assets/custom/textures", entry("icon.png", true));
        files.file("plugins/Nexo/glyphs/icons.yml", Async.completed("test_icon:\n  texture: icon\n  char: 'ꐓ'\n  height: 8\n"));
        return files;
    }

    private static ServerModels.ClientServerView server() {
        ServerModels.ClientServerView server = new ServerModels.ClientServerView();
        server.identifier = "server-1";
        return server;
    }

    private static ServerModels.PteroFileObjectAttributes entry(String name, boolean file) {
        ServerModels.PteroFileObjectAttributes entry = new ServerModels.PteroFileObjectAttributes();
        entry.name = name;
        entry.isFile = file;
        return entry;
    }

    private static final class FakeFiles implements ServerUiCapabilityProvider {
        private final Map<String, List<ServerModels.PteroFileObjectAttributes>> directories = new LinkedHashMap<>();
        private final Map<String, Async<String>> files = new LinkedHashMap<>();

        private void directory(String path, ServerModels.PteroFileObjectAttributes... entries) {
            directories.put(path, List.of(entries));
        }

        private void file(String path, Async<String> content) {
            files.put(path, content);
        }

        @Override
        public Async<List<ServerModels.PteroFileObjectAttributes>> listFiles(ServerModels.ClientServerView server, String directory) {
            List<ServerModels.PteroFileObjectAttributes> entries = directories.get(directory);
            return entries == null ? Async.failed(new IllegalArgumentException("Missing " + directory)) : Async.completed(entries);
        }

        @Override
        public Async<String> readFile(ServerModels.ClientServerView server, String path) {
            Async<String> content = files.get(path);
            return content == null ? Async.failed(new IllegalArgumentException("Missing " + path)) : content;
        }
    }
}
