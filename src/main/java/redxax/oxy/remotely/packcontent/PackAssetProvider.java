package redxax.oxy.remotely.packcontent;

import java.nio.file.Path;
import java.util.Optional;

public interface PackAssetProvider extends PackContentCapability {
    Optional<Path> resolvePackAsset(PackContentContext context, String asset, boolean gif);
}
