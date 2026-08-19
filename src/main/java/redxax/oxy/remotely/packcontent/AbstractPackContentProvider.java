package redxax.oxy.remotely.packcontent;

import restudio.rebase.backend.FileSystemProvider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import restudio.rescreen.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;

abstract class AbstractPackContentProvider implements PackContentProvider {
    protected final List<PackContentDiagnostic> diagnostics = new ArrayList<>();

    @Override
    public List<PackContentDiagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    protected Async<List<Path>> walk(FileSystemProvider fs, Path root) {
        return JvmAsyncBridge.fromFuture(fs.ls(root)).thenCompose(entries -> {
            List<Path> result = new ArrayList<>();
            List<Async<List<Path>>> children = new ArrayList<>();
            for (FileSystemProvider.FileEntry entry : entries) {
                result.add(entry.path);
                if (entry.isDirectory) {
                    children.add(walk(fs, entry.path));
                }
            }
            if (children.isEmpty()) {
                return Async.completed(result);
            }
            return Async.allOf(children.toArray(Async[]::new)).thenApply(v -> {
                for (Async<List<Path>> child : children) {
                    result.addAll(child.join());
                }
                return result;
            });
        }).exceptionally(e -> List.of());
    }

    protected Async<Boolean> exists(PackContentContext context, Path path) {
        return JvmAsyncBridge.fromFuture(context.fileSystem().exists(path)).exceptionally(e -> false);
    }
}
