package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;
import restudio.rebase.backend.TransferSource;
import restudio.rescreen.platform.Async;
import restudio.rescreen.platform.browser.BrowserFile;
import restudio.rescreen.platform.browser.BrowserHostActionHandler;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrowserTransferBridgeTest {
    @Test
    void retainsDroppedSelectionUntilEveryFileIsReleased() {
        TrackingHost host = new TrackingHost();
        List<TransferSource> sources = BrowserTransferBridge.sources(List.of(
                new BrowserFile("one.txt", "text/plain", 1, "", 7, 0),
                new BrowserFile("two.txt", "text/plain", 1, "", 7, 1)), host);

        BrowserTransferBridge.release(sources.get(0));
        assertEquals(0, host.releases);

        BrowserTransferBridge.release(sources.get(1));
        assertEquals(1, host.releases);
    }

    @Test
    void cancellingAFileReadReleasesTheSelection() {
        TrackingHost host = new TrackingHost();
        TransferSource source = BrowserTransferBridge.source(host, new BrowserFile("image.png", "image/png", 4, "", 9, 0));

        Async<TransferSource.Chunk> read = source.next();
        read.cancel();

        assertEquals(1, host.cancelledReads);
        assertEquals(1, host.releases);
    }

    private static final class TrackingHost extends BrowserHostActionHandler {
        private int releases;
        private int cancelledReads;

        @Override
        public int readFileChunk(BrowserFile file, long offset, int length, Consumer<BrowserFile.Chunk> callback) {
            return 12;
        }

        @Override
        public void cancelFileChunk(int readId) {
            assertEquals(12, readId);
            cancelledReads++;
        }

        @Override
        public void releaseFileSelection(int selectionId) {
            releases++;
        }
    }
}
