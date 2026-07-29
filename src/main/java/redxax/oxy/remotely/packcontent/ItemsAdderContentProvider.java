package redxax.oxy.remotely.packcontent;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ItemsAdderContentProvider extends AbstractPackContentProvider implements PackAssetProvider {
    private Path root;

    @Override
    public String id() {
        return "itemsadder";
    }

    @Override
    public String displayName() {
        return "ItemsAdder";
    }

    @Override
    public CompletableFuture<Optional<Path>> detectRoot(PackContentContext context) {
        Path primary = context.workspaceRoot().resolve("plugins").resolve("ItemsAdder");
        return exists(context, primary.resolve("contents")).thenCompose(primaryExists -> {
            if (primaryExists) {
                return CompletableFuture.completedFuture(Optional.of(primary));
            }
            return exists(context, context.workspaceRoot().resolve("contents"))
                    .thenApply(rootExists -> rootExists ? Optional.of(context.workspaceRoot()) : Optional.empty());
        });
    }

    @Override
    public CompletableFuture<Void> refresh(PackContentContext context) {
        root = context.providerRoot();
        diagnostics.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<Path>> resolvePackAsset(PackContentContext context, String asset, boolean gif) {
        if (root == null || asset == null || asset.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String normalized = asset.replace('\\', '/');
        Path candidate = root.resolve("contents").resolve(normalized);
        return CompletableFuture.completedFuture(Optional.of(candidate));
    }
}
