package redxax.oxy.remotely.rematrix;

public interface RematrixContext {
    RematrixMatrixStack matrices();
    RematrixScissorStack scissors();
    RematrixTextureCache textures();
    RematrixTextBridge text();
    Object graphics();
}
