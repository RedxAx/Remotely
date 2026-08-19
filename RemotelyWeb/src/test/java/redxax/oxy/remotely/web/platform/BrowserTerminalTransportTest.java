package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;
import restudio.rebase.backend.TerminalSize;
import restudio.rescreen.platform.Async;
import restudio.rescreen.platform.websocket.BinaryWebSocket;
import restudio.rescreen.platform.websocket.BinaryWebSocketListener;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserTerminalTransportTest {
    @Test
    void consoleEventsPreserveDesktopLineBoundaries() {
        assertEquals("First\r\n", BrowserRemotelyServerApi.BrowserTerminalTransport.normalizeConsoleOutput("First"));
        assertEquals("First\r\nSecond\r\n", BrowserRemotelyServerApi.BrowserTerminalTransport.normalizeConsoleOutput("First\nSecond"));
        assertEquals("First\r\nSecond\r\n", BrowserRemotelyServerApi.BrowserTerminalTransport.normalizeConsoleOutput("First\r\nSecond\r\n"));
        assertEquals("\r\n", BrowserRemotelyServerApi.BrowserTerminalTransport.normalizeConsoleOutput(""));
        assertEquals("", BrowserRemotelyServerApi.BrowserTerminalTransport.normalizeConsoleOutput(null));
    }

    @Test
    void terminalSocketIsOutputOnlyAndDisconnectsOnce() throws IOException {
        AtomicReference<BinaryWebSocketListener> socketListener = new AtomicReference<>();
        AtomicReference<String> output = new AtomicReference<>();
        AtomicReference<String> disconnect = new AtomicReference<>();
        AtomicInteger connections = new AtomicInteger();
        AtomicInteger removals = new AtomicInteger();
        TestSocket socket = new TestSocket();
        BrowserRemotelyServerApi.BrowserTerminalTransport transport = new BrowserRemotelyServerApi.BrowserTerminalTransport(listener -> {
            connections.incrementAndGet();
            socketListener.set(listener);
            return Async.completed(socket);
        }, removals::incrementAndGet);
        transport.onOutput(output::set);
        transport.onDisconnect(disconnect::set);

        transport.connect();
        socketListener.get().onText("{\"event\":\"console output\",\"args\":[\"Ready\"]}");
        assertEquals("Ready\r\n", output.get());
        assertFalse(transport.capability().supportsLineInput());
        assertFalse(transport.capability().supportsControlInput());
        assertFalse(transport.capability().supportsResize());
        transport.write("stop\r".getBytes());
        transport.resize(new TerminalSize(120, 32));

        socketListener.get().onClose(1006, "Connection Lost");
        assertEquals(1, connections.get());
        assertEquals(1, removals.get());
        assertEquals("Connection Lost", disconnect.get());
        assertFalse(socket.isOpen());
    }

    @Test
    void closeCancelsPendingConnectionAndDisposesOnce() {
        Async<BinaryWebSocket> pending = Async.pending();
        AtomicInteger removals = new AtomicInteger();
        BrowserRemotelyServerApi.BrowserTerminalTransport transport = new BrowserRemotelyServerApi.BrowserTerminalTransport(listener -> pending,
                removals::incrementAndGet);

        transport.connect();
        transport.close();

        assertTrue(pending.isCancelled());
        assertEquals(1, removals.get());
    }

    private static final class TestSocket implements BinaryWebSocket {
        private boolean open = true;

        @Override
        public Async<Void> sendText(String text) {
            return Async.completed(null);
        }

        @Override
        public Async<Void> sendBinary(byte[] bytes) {
            return Async.completed(null);
        }

        @Override
        public Async<Void> close(int statusCode, String reason) {
            open = false;
            return Async.completed(null);
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}
