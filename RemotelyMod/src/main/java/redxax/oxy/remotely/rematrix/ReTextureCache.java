package redxax.oxy.remotely.rematrix;

import java.awt.image.BufferedImage;

public interface ReTextureCache {
    ReTextureHandle getTexture(BufferedImage image);
    void clear();
}
