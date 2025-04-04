package redxax.oxy.common;

public class NormalButton {
    public int x;
    public int y;
    public int width;
    public int height;
    public String label;
    public Runnable action;

    public NormalButton(String label, Runnable action, int width, int height) {
        this.label = label;
        this.action = action;
        this.width = width;
        this.height = height;
    }
}