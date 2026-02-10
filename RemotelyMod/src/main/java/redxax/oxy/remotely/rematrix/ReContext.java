package redxax.oxy.remotely.rematrix;

public interface ReContext {
    ReMatrixStack matrices();
    ReScissorStack scissors();
    ReTextureCache textures();
    ReTextBridge text();
    Object graphics();
}
