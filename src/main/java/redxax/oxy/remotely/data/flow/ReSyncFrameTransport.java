package redxax.oxy.remotely.data.flow;

import java.util.function.Consumer;

public interface ReSyncFrameTransport {
    enum State {
        NEW,
        CONNECTING,
        OPEN,
        CLOSING,
        CLOSED,
        FAILED
    }

    void setFrameHandler(Consumer<byte[]> handler);

    void setCloseHandler(Runnable handler);

    default void setCloseReasonHandler(Consumer<String> handler) {
    }

    default void setOpenHandler(Runnable handler) {
    }

    default void setErrorHandler(Consumer<Throwable> handler) {
    }

    default void connect() {
    }

    void send(byte[] frame);

    void close();

    boolean isOpen();

    default State state() {
        return isOpen() ? State.OPEN : State.CLOSED;
    }

    default boolean reconnectable() {
        return false;
    }
}
