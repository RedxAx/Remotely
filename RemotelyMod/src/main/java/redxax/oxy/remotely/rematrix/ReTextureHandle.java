package redxax.oxy.remotely.rematrix;

public final class ReTextureHandle {
    private final Object id;
    private final int width;
    private final int height;

    public ReTextureHandle(Object id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    public Object getId() {
        return id;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
