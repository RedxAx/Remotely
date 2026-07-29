package redxax.oxy.remotely.packcontent;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface PackAssetProvider extends PackContentCapability {
    CompletableFuture<Optional<Path>> resolvePackAsset(PackContentContext context, String asset, boolean gif);
}
