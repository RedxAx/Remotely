package redxax.oxy.remotely.data.flow;

import java.util.function.Consumer;

public interface ReSyncFrameTransport {
    void setFrameHandler(Consumer<byte[]> handler);

    void setCloseHandler(Runnable handler);

    void send(byte[] frame);

    void close();

    boolean isOpen();
}
