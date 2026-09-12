package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;
import restudio.rebase.backend.TransferSource;
import restudio.rescreen.platform.browser.BrowserFile;
import restudio.rescreen.platform.browser.BrowserHostActionHandler;

import java.util.List;

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

    private static final class TrackingHost extends BrowserHostActionHandler {
        private int releases;

        @Override
        public void releaseFileSelection(int selectionId) {
            releases++;
        }
    }
}
