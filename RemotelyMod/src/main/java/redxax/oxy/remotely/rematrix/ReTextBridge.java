package redxax.oxy.remotely.rematrix;

public interface ReTextBridge {
    int getWidth(String text);
    int getWidth(String text, Object font);
    String trimToWidth(String text, int maxWidth);
}
