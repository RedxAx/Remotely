package redxax.oxy.remotely.api;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import redxax.oxy.remotely.explorer.FileManager;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.servers.ServerState;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface RemotelyAPI {
    CompletableFuture<List<FileEntry>> listDirectory(Path path);
    List<FileManager.ClipboardEntry> getClipboard();
    CompletableFuture<Void> copy(List<Path> sources, Path destination);
    CompletableFuture<Void> cut(List<Path> sources, Path destination);
    CompletableFuture<Void> paste(Path destination);
    CompletableFuture<Void> delete(List<Path> paths);
    CompletableFuture<Void> rename(Path oldPath, Path newPath);
    CompletableFuture<Void> createFile(Path path);
    CompletableFuture<Void> createDirectory(Path path);
    CompletableFuture<Void> upload(List<Path> localPaths, Path remotePath);
    CompletableFuture<Void> download(List<Path> remotePaths, Path localPath);
    CompletableFuture<String> readFile(Path path);
    CompletableFuture<Void> writeFile(Path path, String content);
    boolean canUndo();
    CompletableFuture<Void> undo();

    TtyConnector createTtyConnector(ServerInfo serverInfo, TermSize initialSize, Consumer<String> outputConsumer, Consumer<ServerState> stateConsumer) throws Exception;
    void launchServer(ServerInfo serverInfo, Consumer<String> commandConsumer) throws Exception;
    String getInitialDirectory(ServerInfo serverInfo);

    class FileEntry {
        public Path path;
        public boolean isDirectory;
        public String size;
        public String created;
        public String displayName;

        public FileEntry(Path path, boolean isDirectory, String size, String created, String displayName) {
            this.path = path;
            this.isDirectory = isDirectory;
            this.size = size;
            this.created = created;
            this.displayName = displayName;
        }
    }

    class OperationProgress {
        public final String message;
        public final double progress;
        public final boolean completed;

        public OperationProgress(String message, double progress, boolean completed) {
            this.message = message;
            this.progress = progress;
            this.completed = completed;
        }
    }
}
