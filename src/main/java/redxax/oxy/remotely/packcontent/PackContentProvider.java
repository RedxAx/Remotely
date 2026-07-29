package redxax.oxy.remotely.packcontent;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface PackContentProvider {
    String id();
    String displayName();
    CompletableFuture<Optional<Path>> detectRoot(PackContentContext context);
    CompletableFuture<Void> refresh(PackContentContext context);
    List<PackContentDiagnostic> diagnostics();

    default <T extends PackContentCapability> Optional<T> capability(Class<T> type) {
        return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
    }
}
