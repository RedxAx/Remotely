package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import restudio.rebase.backend.FileExplorerProviders;
import restudio.rebase.backend.RemoteFileSystemProvider;
import restudio.rebase.backend.RemotePath;
import restudio.rebase.backend.TransferSink;
import restudio.rebase.backend.TransferSource;
import restudio.rescreen.platform.Async;
import restudio.rescreen.platform.browser.BrowserHostActionHandler;
import restudio.rebase.ui.screens.editor.FileEditorScreen;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.theme.ThemeManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserFileExplorerAdaptersTest {
    @BeforeAll
    static void initializeUiRuntime() {
        ThemeManager.initBrowserDefaults();
        TextRenderer.ensureDefaultRenderer();
    }

    @Test
    void installedResolverUsesThePassedRemoteProvider() throws Exception {
        FileExplorerProviders.Snapshot previous = FileExplorerProviders.snapshot();
        Object owner = new Object();
        TextProvider provider = new TextProvider("server.properties");
        try {
            BrowserFileExplorerAdapters.install(owner);
            assertSame(owner, FileExplorerProviders.snapshot().owners().editorResolver());
            assertTrue(FileExplorerProviders.canOpenEditor(provider, RemotePath.of("/server.properties")).join());

            FileEditorScreen editor = BrowserFileExplorerAdapters.createEditor(null, null, provider,
                    RemotePath.root(), RemotePath.of("/server.properties"), RemotePath.root());
            Field workspaceProvider = FileEditorScreen.class.getDeclaredField("workspaceProvider");
            workspaceProvider.setAccessible(true);
            assertSame(provider, workspaceProvider.get(editor));
        } finally {
            FileExplorerProviders.restore(previous);
        }
    }

    @Test
    void binaryContentRemainsOutsideTheSharedEditor() {
        TextProvider provider = new TextProvider("\u0000\u0001\u0002\u0003\u0004\u0005");
        assertFalse(BrowserFileExplorerAdapters.resolver().canOpen(provider, RemotePath.of("/server.jar")).join());
        assertFalse(BrowserFileExplorerAdapters.resolver().openExternallyWhenRejected(provider, RemotePath.of("/server.jar")));
    }

    @Test
    void signedDownloadArmsOnlyTheHoveredFileAndClearsWhenItLeaves() {
        RecordingHost actions = new RecordingHost();
        BrowserFileExplorerAdapters.BrowserExternalDragPreparation preparation =
                new BrowserFileExplorerAdapters.BrowserExternalDragPreparation(actions);
        RemotePath path = RemotePath.of("/plugins/Example.jar");
        DragProvider provider = new DragProvider(Async.completed("https://downloads.example/Example.jar"));
        RemoteFileSystemProvider.FileEntry entry = new RemoteFileSystemProvider.FileEntry(path, false, "12", "", "Example.jar",
                Map.of("mimetype", "application/java-archive"));
        try {
            preparation.prepare(provider, entry, 10, 20, 300, 20);

            assertEquals("Example.jar", actions.name);
            assertEquals("application/java-archive", actions.contentType);
            assertEquals("https://downloads.example/Example.jar", actions.url);
            assertEquals(1, provider.signRequests);
            preparation.cancel(provider, path);
            assertEquals(actions.armedToken, actions.clearedToken);
        } finally {
            preparation.close();
        }
    }

    @Test
    void cancelledSigningCannotArmAStaleDownload() {
        RecordingHost actions = new RecordingHost();
        BrowserFileExplorerAdapters.BrowserExternalDragPreparation preparation =
                new BrowserFileExplorerAdapters.BrowserExternalDragPreparation(actions);
        Async<String> signedUrl = Async.pending();
        RemotePath path = RemotePath.of("/world/level.dat");
        DragProvider provider = new DragProvider(signedUrl);
        RemoteFileSystemProvider.FileEntry entry = new RemoteFileSystemProvider.FileEntry(path, false, "64", "", "level.dat");
        try {
            preparation.prepare(provider, entry, 10, 20, 300, 20);
            preparation.cancel(provider, path);
            signedUrl.complete("https://downloads.example/level.dat");

            assertNull(actions.armedToken);
        } finally {
            preparation.close();
        }
    }

    private static class TextProvider implements RemoteFileSystemProvider {
        private final String content;

        private TextProvider(String content) {
            this.content = content;
        }

        @Override
        public Async<List<FileEntry>> ls(RemotePath path) {
            return Async.completed(List.of());
        }

        @Override
        public Async<Void> copy(List<RemotePath> sources, RemotePath destination) {
            return Async.completed(null);
        }

        @Override
        public Async<Void> move(List<RemotePath> sources, RemotePath destination) {
            return Async.completed(null);
        }

        @Override
        public Async<Void> delete(List<RemotePath> paths) {
            return Async.completed(null);
        }

        @Override
        public Async<String> read(RemotePath path) {
            return Async.completed(content);
        }

        @Override
        public Async<Void> write(RemotePath path, String content) {
            return Async.completed(null);
        }

        @Override
        public Async<Void> upload(List<TransferSource> sources, RemotePath destination) {
            return Async.completed(null);
        }

        @Override
        public Async<Void> download(List<RemotePath> sources, TransferSink destination) {
            return Async.completed(null);
        }

        @Override
        public Async<Void> rename(RemotePath oldPath, RemotePath newPath) {
            return Async.completed(null);
        }

        @Override
        public Async<Void> createFile(RemotePath path) {
            return Async.completed(null);
        }

        @Override
        public Async<Void> createDirectory(RemotePath path) {
            return Async.completed(null);
        }

        @Override
        public Async<Boolean> exists(RemotePath path) {
            return Async.completed(true);
        }

        @Override
        public String getMetadata(String key) {
            return switch (key) {
                case "type" -> "RESTUDIO";
                case "serverId" -> "server-1";
                default -> "";
            };
        }
    }

    private static final class DragProvider extends TextProvider implements BrowserFileExplorerAdapters.BrowserDownloadDragSource {
        private final Async<String> signedUrl;
        private int signRequests;

        private DragProvider(Async<String> signedUrl) {
            super("");
            this.signedUrl = signedUrl;
        }

        @Override
        public Async<String> downloadUrl(RemotePath path) {
            signRequests++;
            return signedUrl;
        }
    }

    private static final class RecordingHost extends BrowserHostActionHandler {
        private String armedToken;
        private String clearedToken;
        private String name;
        private String contentType;
        private String url;

        @Override
        public boolean downloadDragSupported() {
            return true;
        }

        @Override
        public boolean armDownloadDrag(String token, String name, String contentType, String url,
                                       int x, int y, int width, int height) {
            this.armedToken = token;
            this.name = name;
            this.contentType = contentType;
            this.url = url;
            return true;
        }

        @Override
        public void clearDownloadDrag(String token) {
            clearedToken = token;
        }
    }
}
