package redxax.oxy.remotely.rematrix;

public interface ReScissorStack {
    void pushState();
    void popState();
    void clear();
    void enable(float x1, float y1, float x2, float y2);
    void disable();
    boolean contains(int x, int y);
    ScissorBox getCurrent();

    final class ScissorBox {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        public ScissorBox(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}
