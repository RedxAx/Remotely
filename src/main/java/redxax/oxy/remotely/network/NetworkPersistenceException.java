package redxax.oxy.remotely.network;

public class NetworkPersistenceException extends RuntimeException {
    public NetworkPersistenceException(String message) {
        super(message);
    }

    public NetworkPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
