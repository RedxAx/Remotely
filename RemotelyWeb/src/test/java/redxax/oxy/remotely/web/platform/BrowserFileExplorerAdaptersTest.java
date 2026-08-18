package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import restudio.rebase.backend.FileExplorerProviders;
import restudio.rebase.backend.RemoteFileSystemProvider;
import restudio.rebase.backend.RemotePath;
import restudio.rebase.backend.TransferSink;
import restudio.rebase.backend.TransferSource;
import restudio.rebase.platform.Async;
import restudio.rebase.ui.screens.editor.FileEditorScreen;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.theme.ThemeManager;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final class TextProvider implements RemoteFileSystemProvider {
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
}
