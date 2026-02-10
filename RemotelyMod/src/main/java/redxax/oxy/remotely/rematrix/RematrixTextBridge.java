package redxax.oxy.remotely.rematrix;

public interface RematrixTextBridge {
    int getWidth(String text);
    int getWidth(String text, Object font);
    String trimToWidth(String text, int maxWidth);
}
