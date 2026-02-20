package redxax.oxy.remotely.rematrix;

public interface ReContext {
    ReMatrixStack matrices();
    ReScissorStack scissors();
    ReTextureCache textures();
    ReTextBridge text();
    Object graphics();
    void drawItem(Object item, int x, int y, int z);
}
