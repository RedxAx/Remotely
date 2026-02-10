package redxax.oxy.remotely.rematrix;

public interface ReMatrixStack {
    void push();
    void pop();
    void translate(float x, float y, float z);
    void scale(float x, float y, float z);
    void rotate(float angle, float x, float y, float z);
    void multiply(float angle);
}
