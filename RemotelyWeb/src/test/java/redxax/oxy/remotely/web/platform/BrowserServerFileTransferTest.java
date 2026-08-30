package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;
import restudio.rebase.backend.RemoteFileSystemProvider;
import restudio.rebase.backend.RemotePath;
import restudio.rebase.backend.TransferSource;
import restudio.rescreen.platform.Async;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserServerFileTransferTest {
    @Test
    void oversizedUploadFailsBeforeTransport() {
        BrowserServerFileTransfer transfer = new BrowserServerFileTransfer(null, "server");
        TransferSource source = new TransferSource() {
            @Override
            public String name() {
                return "large.jar";
            }

            @Override
            public long size() {
                return BrowserServerFileTransfer.MAX_UPLOAD_BYTES + 1;
            }

            @Override
            public Async<Chunk> next() {
                throw new AssertionError("Oversized source must not be read");
            }
        };

        var result = transfer.upload(List.of(source), RemotePath.root(), (sent, total) -> {
        });

        assertTrue(result.isDone());
        assertTrue(result.failure() instanceof IllegalArgumentException);
    }

    @Test
    void virtualRootResolvesExistingServerProvider() {
        RemoteFileSystemProvider provider = RemoteFileSystemProvider.unavailable();
        BrowserServerFileRootProvider root = new BrowserServerFileRootProvider(List.of(
                new BrowserServerFileRootProvider.ServerRoot("server-1", "Survival", provider)));

        RemoteFileSystemProvider.FileEntry entry = root.ls(RemotePath.root()).value().getFirst();
        RemoteFileSystemProvider.DirectoryTarget target = root.resolveDirectory(entry);

        assertEquals("Survival", entry.displayName());
        assertSame(provider, target.provider());
        assertEquals(RemotePath.root(), target.path());
    }
}
