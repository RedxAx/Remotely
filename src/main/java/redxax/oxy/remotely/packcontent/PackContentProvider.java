package redxax.oxy.remotely.packcontent;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import restudio.rescreen.platform.Async;

public interface PackContentProvider {
    String id();
    String displayName();
    Async<Optional<Path>> detectRoot(PackContentContext context);
    Async<Void> refresh(PackContentContext context);
    List<PackContentDiagnostic> diagnostics();

    default <T extends PackContentCapability> Optional<T> capability(Class<T> type) {
        return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
    }
}
