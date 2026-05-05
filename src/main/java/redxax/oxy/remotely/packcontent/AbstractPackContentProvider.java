package redxax.oxy.remotely.packcontent;

import restudio.rebase.backend.FileSystemProvider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

abstract class AbstractPackContentProvider implements PackContentProvider {
    protected final List<PackContentDiagnostic> diagnostics = new ArrayList<>();

    @Override
    public List<PackContentDiagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    protected CompletableFuture<List<Path>> walk(FileSystemProvider fs, Path root) {
        return fs.ls(root).thenCompose(entries -> {
            List<Path> result = new ArrayList<>();
            List<CompletableFuture<List<Path>>> children = new ArrayList<>();
            for (FileSystemProvider.FileEntry entry : entries) {
                result.add(entry.path);
                if (entry.isDirectory) {
                    children.add(walk(fs, entry.path));
                }
            }
            if (children.isEmpty()) {
                return CompletableFuture.completedFuture(result);
            }
            return CompletableFuture.allOf(children.toArray(CompletableFuture[]::new)).thenApply(v -> {
                for (CompletableFuture<List<Path>> child : children) {
                    result.addAll(child.join());
                }
                return result;
            });
        }).exceptionally(e -> List.of());
    }

    protected CompletableFuture<Boolean> exists(PackContentContext context, Path path) {
        return context.fileSystem().exists(path).exceptionally(e -> false);
    }
}
