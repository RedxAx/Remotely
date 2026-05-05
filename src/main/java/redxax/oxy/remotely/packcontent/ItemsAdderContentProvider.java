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
    public Optional<Path> detectRoot(PackContentContext context) {
        Path primary = context.workspaceRoot().resolve("plugins").resolve("ItemsAdder");
        if (exists(context, primary.resolve("contents")).join()) {
            return Optional.of(primary);
        }
        if (exists(context, context.workspaceRoot().resolve("contents")).join()) {
            return Optional.of(context.workspaceRoot());
        }
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Void> refresh(PackContentContext context) {
        root = context.providerRoot();
        diagnostics.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Optional<Path> resolvePackAsset(PackContentContext context, String asset, boolean gif) {
        if (root == null || asset == null || asset.isBlank()) {
            return Optional.empty();
        }
        String normalized = asset.replace('\\', '/');
        Path candidate = root.resolve("contents").resolve(normalized);
        return Optional.of(candidate);
    }
}
