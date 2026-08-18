package redxax.oxy.remotely.data.flow;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReSyncConnectionManagerLiveSessionTest {

    @Test
    void repeatedActivationKeepsTheConnectingBridgeTransport() {
        ReSyncConnectionManager manager = new ReSyncConnectionManager(null, null, DesktopReSyncFlowClientFactory.create());
        TestTransport transport = new TestTransport();
        ReSyncLiveServerSession session = new ReSyncLiveServerSession("live:server:player", "Server", transport);

        ReSyncFlowClient first = manager.activateLiveSession(session);
        try {
            ReSyncFlowClient second = manager.activateLiveSession(session);

            assertSame(first, second);
            assertEquals(0, transport.closeCalls.get());
            assertEquals(1, transport.sentFrames.get());
        } finally {
            first.shutdown();
        }
    }

    @Test
    void closedLiveSessionIsReplacedByTheNextActivation() {
        ReSyncConnectionManager manager = new ReSyncConnectionManager(null, null, DesktopReSyncFlowClientFactory.create());
        TestTransport firstTransport = new TestTransport();
        ReSyncLiveServerSession firstSession = new ReSyncLiveServerSession("live:server:player", "Server", firstTransport);
        ReSyncFlowClient first = manager.activateLiveSession(firstSession);

        manager.closeServerConnection(firstSession.serverId(), () -> {
        });
        TestTransport secondTransport = new TestTransport();
        ReSyncFlowClient second = manager.activateLiveSession(new ReSyncLiveServerSession(
            firstSession.serverId(), "Server", secondTransport));
        try {
            assertNotSame(first, second);
            assertEquals(1, firstTransport.closeCalls.get());
            assertEquals(1, secondTransport.sentFrames.get());
        } finally {
            second.shutdown();
        }
    }

    @Test
    void disconnectedOpenBridgeTransportCanHandshakeAgain() {
        ReSyncConnectionManager manager = new ReSyncConnectionManager(null, null, DesktopReSyncFlowClientFactory.create());
        TestTransport transport = new TestTransport();
        ReSyncLiveServerSession session = new ReSyncLiveServerSession("live:server:player", "Server", transport);
        ReSyncFlowClient first = manager.activateLiveSession(session);
        transport.disconnect();

        ReSyncFlowClient reactivated = manager.activateLiveSession(session);
        try {
            assertSame(first, reactivated);
            assertEquals(2, transport.sentFrames.get());
            assertEquals(0, transport.closeCalls.get());
        } finally {
            reactivated.shutdown();
        }
    }

    private static final class TestTransport implements ReSyncFrameTransport {
        private final AtomicInteger sentFrames = new AtomicInteger();
        private final AtomicInteger closeCalls = new AtomicInteger();
        private Runnable closeHandler;

        @Override
        public void setFrameHandler(Consumer<byte[]> handler) {
        }

        @Override
        public void setCloseHandler(Runnable handler) {
            closeHandler = handler;
        }

        @Override
        public void send(byte[] frame) {
            sentFrames.incrementAndGet();
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            if (closeHandler != null) {
                closeHandler.run();
            }
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        private void disconnect() {
            if (closeHandler != null) {
                closeHandler.run();
            }
        }
    }
}
