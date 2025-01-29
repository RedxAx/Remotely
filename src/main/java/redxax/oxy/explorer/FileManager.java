package redxax.oxy.explorer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import redxax.oxy.SSHManager;
import redxax.oxy.servers.ServerInfo;

public class FileManager {
    private static class ClipboardEntry {
        final String sourcePath;
        final boolean isRemote;

        ClipboardEntry(String sourcePath, boolean isRemote) {
            this.sourcePath = sourcePath;
            this.isRemote = isRemote;
        }
    }

    private List<ClipboardEntry> clipboard = new ArrayList<>();
    private boolean isCut = false;
    private Deque<UndoableAction> undoStack = new ArrayDeque<>();
    private final FileManagerCallback callback;
    private final ServerInfo serverInfo;
    private final Path tempUndoDir;
    private final SSHManager sshManager;

    public FileManager(FileManagerCallback callback, ServerInfo serverInfo, SSHManager sshManager) {
        this.callback = callback;
        this.serverInfo = serverInfo;
        this.sshManager = sshManager;
        this.tempUndoDir = Paths.get(System.getProperty("java.io.tmpdir"), "file_explorer_undo");
        try {
            Files.createDirectories(tempUndoDir);
        } catch (IOException ignored) {}
    }

    public void copySelected(List<Path> selectedPaths) {
        clipboard = selectedPaths.stream()
                .map(p -> new ClipboardEntry(serverInfo.isRemote ? p.toString().replace("\\", "/") : p.toString(), serverInfo.isRemote))
                .collect(Collectors.toList());
        isCut = false;
    }

    public void cutSelected(List<Path> selectedPaths) {
        clipboard = selectedPaths.stream()
                .map(p -> new ClipboardEntry(serverInfo.isRemote ? p.toString().replace("\\", "/") : p.toString(), serverInfo.isRemote))
                .collect(Collectors.toList());
        isCut = true;
    }

    public void deleteSelected(List<Path> selectedPaths, Path currentPath) {
        List<Path> deletedPaths = new ArrayList<>();
        List<String> backupPaths = new ArrayList<>();

        if (serverInfo.isRemote) {
            String homeDir = serverInfo.remoteHost.getHomeDirectory();

            selectedPaths.parallelStream().forEach(path -> {
                try {
                    String remotePath = path.toString().replace("\\", "/");
                    String fileName = Paths.get(remotePath).getFileName().toString();
                    String trashPath = homeDir + "/remotely/data/trash/\"" + fileName + "\"";

                    if (sshManager.remoteFileExists(homeDir + "/remotely/data/trash")) {
                        sshManager.runRemoteCommand("mkdir -p " + homeDir + "/remotely/data/trash " + "mv -f \"" + remotePath + "\" \"" + trashPath + "\"");
                    } else {
                        sshManager.runRemoteCommand("mv -f \"" + remotePath + "\" \"" + trashPath + "\"");
                    }

                    synchronized (this) {
                        deletedPaths.add(path);
                        backupPaths.add(trashPath);
                    }
                } catch (Exception e) {
                    callback.showNotification("Error deleting " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                }
            });
        } else {
            selectedPaths.parallelStream().forEach(path -> {
                try {
                    Path backupPath = tempUndoDir.resolve(UUID.randomUUID().toString());
                    Files.walkFileTree(path, new RecursiveFileCopier(path, backupPath, true));
                    synchronized (this) {
                        deletedPaths.add(path);
                        backupPaths.add(backupPath.toString());
                    }
                } catch (IOException e) {
                    callback.showNotification("Error deleting " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                }
            });
        }

        if (!deletedPaths.isEmpty()) {
            undoStack.push(new DeleteAction(new ArrayList<>(deletedPaths), new ArrayList<>(backupPaths)));
            callback.refreshDirectory(currentPath);
        }
    }

    public void paste(Path currentPath) {
        List<PathOperation> operations = new ArrayList<>();

        for (ClipboardEntry entry : clipboard) {
            try {
                if (serverInfo.isRemote) {
                    String currentRemote = currentPath.toString().replace("\\", "/");
                    if (!currentRemote.endsWith("/")) currentRemote += "/";
                    String fileName = Paths.get(entry.sourcePath).getFileName().toString();
                    String remoteDest = currentRemote + fileName;

                    if (entry.isRemote) {
                        if (isCut) {
                            sshManager.renameRemote(entry.sourcePath, remoteDest);
                        } else {
                            sshManager.runRemoteCommand("cp -rf \"" + entry.sourcePath + "\" \"" + remoteDest + "\"");
                        }
                        operations.add(new PathOperation(entry.sourcePath, remoteDest));
                    } else {
                        Path localSrc = Paths.get(entry.sourcePath);
                        sshManager.upload(localSrc, remoteDest);
                        if (isCut) Files.walkFileTree(localSrc, new RecursiveFileDeleter());
                        operations.add(new PathOperation(entry.sourcePath, remoteDest));
                    }
                } else {
                    Path dest = currentPath.resolve(Paths.get(entry.sourcePath).getFileName());
                    Files.createDirectories(dest.getParent());

                    if (entry.isRemote) {
                        sshManager.download(entry.sourcePath, dest);
                        if (isCut) sshManager.deleteRemote(entry.sourcePath);
                        operations.add(new PathOperation(entry.sourcePath, dest.toString()));
                    } else {
                        Path src = Paths.get(entry.sourcePath);
                        if (isCut) {
                            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            Files.walkFileTree(src, new RecursiveFileCopier(src, dest, false));
                        }
                        operations.add(new PathOperation(src.toString(), dest.toString()));
                    }
                }
            } catch (Exception e) {
                callback.showNotification("Error pasting " + Paths.get(entry.sourcePath).getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
            }
        }

        if (!operations.isEmpty()) {
            undoStack.push(new PasteAction(operations, isCut));
            if (isCut) clipboard.clear();
            isCut = false;
            callback.refreshDirectory(currentPath);
        }
    }

    public void undo(Path currentPath) {
        if (!undoStack.isEmpty()) {
            undoStack.pop().undo();
            callback.refreshDirectory(currentPath);
        }
    }

    private static class RecursiveFileCopier extends SimpleFileVisitor<Path> {
        private final Path source;
        private final Path target;
        private final boolean deleteSource;

        RecursiveFileCopier(Path source, Path target, boolean deleteSource) {
            this.source = source;
            this.target = target;
            this.deleteSource = deleteSource;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            Path newDir = target.resolve(source.relativize(dir));
            Files.createDirectories(newDir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
            if (deleteSource) Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (deleteSource) Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    private static class RecursiveFileDeleter extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    interface UndoableAction {
        void undo();
    }

    private record PathOperation(String source, String destination) {}

    class DeleteAction implements UndoableAction {
        private final List<Path> deletedPaths;
        private final List<String> backupPaths;

        DeleteAction(List<Path> deletedPaths, List<String> backupPaths) {
            this.deletedPaths = deletedPaths;
            this.backupPaths = backupPaths;
        }

        @Override
        public void undo() {
            if (serverInfo.isRemote) {
                backupPaths.parallelStream().forEach(backup -> {
                    try {
                        int index = backupPaths.indexOf(backup);
                        sshManager.runRemoteCommand("mv -f \"" + backup + "\" \"" + deletedPaths.get(index) + "\"");
                    } catch (Exception e) {
                        callback.showNotification("Error undoing delete: " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                    }
                });
            } else {
                IntStream.range(0, deletedPaths.size()).parallel().forEach(i -> {
                    try {
                        Path dest = deletedPaths.get(i);
                        Path backup = Paths.get(backupPaths.get(i));
                        Files.walkFileTree(backup, new RecursiveFileCopier(backup, dest, true));
                    } catch (IOException e) {
                        callback.showNotification("Error undoing delete: " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                    }
                });
            }
        }
    }

    class PasteAction implements UndoableAction {
        private final List<PathOperation> operations;
        private final boolean wasCut;

        PasteAction(List<PathOperation> operations, boolean wasCut) {
            this.operations = operations;
            this.wasCut = wasCut;
        }

        @Override
        public void undo() {
            operations.parallelStream().forEach(op -> {
                try {
                    if (wasCut) {
                        if (serverInfo.isRemote) {
                            sshManager.renameRemote(op.destination(), op.source());
                        } else {
                            Files.move(Paths.get(op.destination()), Paths.get(op.source()), StandardCopyOption.REPLACE_EXISTING);
                        }
                    } else {
                        if (serverInfo.isRemote) {
                            sshManager.deleteRemote(op.destination());
                        } else {
                            Files.walkFileTree(Paths.get(op.destination()), new RecursiveFileDeleter());
                        }
                    }
                } catch (Exception e) {
                    callback.showNotification("Error undoing paste: " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                }
            });
        }
    }

    public interface FileManagerCallback {
        void showNotification(String message, FileExplorerScreen.Notification.Type type);
        void refreshDirectory(Path path);
    }
}