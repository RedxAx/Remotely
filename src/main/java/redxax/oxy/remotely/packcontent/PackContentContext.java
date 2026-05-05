package redxax.oxy.remotely.packcontent;

import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.instance.Instance;

import java.nio.file.Path;
import java.util.Map;

public record PackContentContext(Instance instance, FileSystemProvider fileSystem, Path workspaceRoot, Path providerRoot, Map<String, String> metadata) {
    public PackContentContext withProviderRoot(Path root) {
        return new PackContentContext(instance, fileSystem, workspaceRoot, root, metadata);
    }
}
