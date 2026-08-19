package redxax.oxy.remotely.web.platform;

import restudio.rebase.backend.CapabilityDescriptor;
import restudio.rebase.backend.CapabilityIds;
import restudio.rebase.backend.RemoteFileSystemProvider;
import restudio.rebase.backend.RemotePath;
import restudio.rebase.backend.TransferSink;
import restudio.rebase.backend.TransferSource;
import restudio.rescreen.platform.Async;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BrowserServerFileRootProvider implements RemoteFileSystemProvider {
    private final Map<RemotePath, ServerRoot> roots;

    BrowserServerFileRootProvider(List<ServerRoot> servers) {
        Map<RemotePath, ServerRoot> values = new LinkedHashMap<>();
        if (servers != null) {
            for (ServerRoot server : servers) {
                if (server == null || server.id().isBlank()) continue;
                values.put(RemotePath.root().resolve(server.id()), server);
            }
        }
        roots = Map.copyOf(values);
    }

    @Override
    public Async<List<FileEntry>> ls(RemotePath path) {
        if (path != null && !path.isRoot()) return unavailable();
        return Async.completed(roots.entrySet().stream().map(entry -> new FileEntry(entry.getKey(), true, "", "", entry.getValue().name())).toList());
    }

    @Override
    public DirectoryTarget resolveDirectory(FileEntry entry) {
        if (entry == null) return null;
        ServerRoot root = roots.get(entry.path());
        return root == null ? null : new DirectoryTarget(root.provider(), RemotePath.root());
    }

    @Override
    public CapabilityDescriptor operationCapability(String operation, List<RemotePath> sources, RemotePath destination) {
        if ("files.list".equalsIgnoreCase(operation)) return CapabilityDescriptor.supported(CapabilityIds.FILES);
        return CapabilityDescriptor.unavailable(operation, "Select A Server First");
    }

    @Override
    public Map<String, CapabilityDescriptor> capabilities() {
        return Map.of(
                CapabilityIds.FILES, CapabilityDescriptor.supported(CapabilityIds.FILES),
                CapabilityIds.TRANSFER, CapabilityDescriptor.unavailable(CapabilityIds.TRANSFER, "Select A Server First"));
    }

    @Override
    public String getMetadata(String key) {
        return "type".equalsIgnoreCase(key) ? "BROWSER_SERVER_ROOT" : null;
    }

    @Override
    public Async<Void> copy(List<RemotePath> sources, RemotePath destination) {
        return unavailable();
    }

    @Override
    public Async<Void> move(List<RemotePath> sources, RemotePath destination) {
        return unavailable();
    }

    @Override
    public Async<Void> delete(List<RemotePath> paths) {
        return unavailable();
    }

    @Override
    public Async<String> read(RemotePath path) {
        return unavailable();
    }

    @Override
    public Async<Void> write(RemotePath path, String content) {
        return unavailable();
    }

    @Override
    public Async<Void> upload(List<TransferSource> sources, RemotePath destination) {
        return unavailable();
    }

    @Override
    public Async<Void> download(List<RemotePath> sources, TransferSink destination) {
        return unavailable();
    }

    @Override
    public Async<Void> rename(RemotePath oldPath, RemotePath newPath) {
        return unavailable();
    }

    @Override
    public Async<Void> createFile(RemotePath path) {
        return unavailable();
    }

    @Override
    public Async<Void> createDirectory(RemotePath path) {
        return unavailable();
    }

    @Override
    public Async<Boolean> exists(RemotePath path) {
        return Async.completed(path == null || path.isRoot() || roots.containsKey(path));
    }

    private static <T> Async<T> unavailable() {
        return Async.failed(new UnsupportedOperationException("Select A Server First"));
    }

    record ServerRoot(String id, String name, RemoteFileSystemProvider provider) {
        ServerRoot {
            id = id == null ? "" : id.strip();
            name = name == null || name.isBlank() ? id : name.strip();
        }
    }
}
