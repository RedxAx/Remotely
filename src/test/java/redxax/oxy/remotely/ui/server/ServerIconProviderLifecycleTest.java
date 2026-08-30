package redxax.oxy.remotely.ui.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import restudio.rescreen.platform.Async;
import restudio.rescreen.util.Identifier;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerIconProviderLifecycleTest {
    @Test
    void rejectedDesktopCustomizationCompletesExceptionally(@TempDir Path cache) {
        Async<Void> result = new DesktopServerIconProvider(cache).customizeIcon(null, null, Identifier.icon("ic_1.png"), null);

        assertTrue(result.isDone());
        assertNotNull(result.failure());
    }

    @Test
    void logicalCustomizationCompletesAndInvokesCallback() {
        AtomicInteger callbacks = new AtomicInteger();

        Async<Void> result = ServerIconProvider.logical().customizeIcon(
                new ServerIconProvider.LogicalServer("paper", "paper"), null, Identifier.icon("ic_1.png"), callbacks::incrementAndGet);

        assertTrue(result.isDone());
        assertEquals(1, callbacks.get());
    }
}
