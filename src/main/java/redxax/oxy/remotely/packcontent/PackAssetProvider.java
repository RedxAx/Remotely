package redxax.oxy.remotely.packcontent;

import java.nio.file.Path;
import java.util.Optional;
import restudio.rebase.platform.Async;

public interface PackAssetProvider extends PackContentCapability {
    Async<Optional<Path>> resolvePackAsset(PackContentContext context, String asset, boolean gif);
}
