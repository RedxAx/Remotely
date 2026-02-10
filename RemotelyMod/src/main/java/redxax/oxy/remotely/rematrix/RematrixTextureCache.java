package redxax.oxy.remotely.rematrix;

import java.awt.image.BufferedImage;

public interface RematrixTextureCache {
    RematrixTextureHandle getTexture(BufferedImage image);
    void clear();
}
