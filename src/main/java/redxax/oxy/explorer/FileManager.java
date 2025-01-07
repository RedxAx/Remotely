package redxax.oxy.explorer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import redxax.oxy.SSHManager;
import redxax.oxy.servers.ServerInfo;

public class FileManager {
    private List<Path> clipboard = new ArrayList<>();
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
            if (!Files.exists(tempUndoDir)) {
                Files.createDirectories(tempUndoDir);
            }
        } catch (IOException e) {
            callback.showNotification("Failed to create temp undo directory: " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
        }
    }

    public void copySelected(List<Path> selectedPaths) {
        clipboard.clear();
        clipboard.addAll(selectedPaths);
        isCut = false;
    }

    public void cutSelected(List<Path> selectedPaths) {
        clipboard.clear();
        clipboard.addAll(selectedPaths);
        isCut = true;
    }

    public void deleteSelected(List<Path> selectedPaths, Path currentPath) {
        List<Path> toRemove = new ArrayList<>();
        List<Path> deletedPaths = new ArrayList<>();
        List<Path> backupPaths = new ArrayList<>();
        if (serverInfo.isRemote) {
            for (Path path : selectedPaths) {
                try {
                    String remotePath = path.toString().replace("\\", "/");
                    String backupPath = tempUndoDir.resolve(path.getFileName()).toString().replace("\\", "/");
                    sshManager.downloadRemotePath(remotePath, Paths.get(backupPath));
                    sshManager.deleteRemoteDirectory(remotePath);
                    deletedPaths.add(path);
                    backupPaths.add(Paths.get(backupPath));
                    toRemove.add(path);
                } catch (Exception e) {
                    callback.showNotification("Error deleting " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                }
            }
        } else {
            for (Path path : selectedPaths) {
                try {
                    Path backupPath = tempUndoDir.resolve(currentPath.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                Path targetDir = backupPath.resolve(currentPath.relativize(dir));
                                if (!Files.exists(targetDir)) {
                                    Files.createDirectories(targetDir);
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.copy(file, backupPath.resolve(currentPath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                Files.delete(dir);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } else {
                        Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
                        Files.delete(path);
                        backupPaths.add(backupPath);
                    }
                    deletedPaths.add(path);
                    toRemove.add(path);
                } catch (IOException e) {
                    callback.showNotification("Error deleting " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                }
            }
        }
        if (!deletedPaths.isEmpty()) {
            undoStack.push(new DeleteAction(deletedPaths, backupPaths));
        }
        callback.refreshDirectory(currentPath);
    }

    public void paste(Path currentPath) {
        List<Path> toDelete = new ArrayList<>();
        List<Path> pastedPaths = new ArrayList<>();
        List<Path> backupPaths = new ArrayList<>();
        if (serverInfo.isRemote) {
            for (Path src : clipboard) {
                try {
                    String srcPath = src.toString().replace("\\", "/");
                    String destPath = currentPath.resolve(src.getFileName()).toString().replace("\\", "/");
                    if (isCut) {
                        sshManager.runRemoteCommand("mv " + srcPath + " " + destPath);
                        pastedPaths.add(Paths.get(destPath));
                        toDelete.add(src);
                    } else {
                        sshManager.runRemoteCommand("cp -r " + srcPath + " " + destPath);
                        pastedPaths.add(Paths.get(destPath));
                    }
                } catch (Exception e) {
                    callback.showNotification("Error pasting " + src.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                }
            }
        } else {
            for (Path src : clipboard) {
                try {
                    Path dest = currentPath.resolve(src.getFileName());
                    if (Files.exists(dest)) {
                        if (Files.isDirectory(src)) {
                            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                                @Override
                                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                    Path targetDir = dest.resolve(src.relativize(dir));
                                    if (!Files.exists(targetDir)) {
                                        Files.createDirectory(targetDir);
                                    }
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                    Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                                    pastedPaths.add(dest.resolve(src.relativize(file)));
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        } else {
                            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                            pastedPaths.add(dest);
                        }
                    } else {
                        if (Files.isDirectory(src)) {
                            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                                @Override
                                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                    Path targetDir = dest.resolve(src.relativize(dir));
                                    if (!Files.exists(targetDir)) {
                                        Files.createDirectory(targetDir);
                                    }
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                    Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                                    pastedPaths.add(dest.resolve(src.relativize(file)));
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        } else {
                            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                            pastedPaths.add(dest);
                        }
                    }
                    if (isCut && !src.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
                        toDelete.add(src);
                    }
                } catch (IOException e) {
                    callback.showNotification("Error pasting " + src.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                }
            }
        }
        if (!pastedPaths.isEmpty()) {
            undoStack.push(new PasteAction(pastedPaths));
            callback.refreshDirectory(currentPath);
        }
        if (serverInfo.isRemote) {
            if (isCut) {
                clipboard.clear();
                isCut = false;
            }
        } else {
            for (Path path : toDelete) {
                try {
                    Path backupPath = tempUndoDir.resolve(currentPath.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                Path targetDir = backupPath.resolve(currentPath.relativize(dir));
                                if (!Files.exists(targetDir)) {
                                    Files.createDirectories(targetDir);
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.copy(file, backupPath.resolve(currentPath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                Files.delete(dir);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } else {
                        Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
                        Files.delete(path);
                        backupPaths.add(backupPath);
                    }
                } catch (IOException e) {
                    callback.showNotification("Error deleting " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                }
            }
            if (isCut) {
                clipboard.clear();
                isCut = false;
            }
        }
    }

    public void undo(Path currentPath) {
        if (!undoStack.isEmpty()) {
            UndoableAction action = undoStack.pop();
            action.undo();
            callback.refreshDirectory(currentPath);
        }
    }

    interface UndoableAction {
        void undo();
    }

    class DeleteAction implements UndoableAction {
        private final List<Path> deletedPaths;
        private final List<Path> backupPaths;

        DeleteAction(List<Path> deletedPaths, List<Path> backupPaths) {
            this.deletedPaths = new ArrayList<>(deletedPaths);
            this.backupPaths = new ArrayList<>(backupPaths);
        }

        @Override
        public void undo() {
            if (serverInfo.isRemote) {
                for (int i = 0; i < deletedPaths.size(); i++) {
                    Path path = deletedPaths.get(i);
                    Path backup = backupPaths.get(i);
                    try {
                        String remotePath = path.toString().replace("\\", "/");
                        String backupPath = backup.toString().replace("\\", "/");
                        sshManager.uploadRemotePath(backupPath, remotePath);
                        Files.deleteIfExists(backup);
                    } catch (Exception e) {
                        callback.showNotification("Error undoing delete for " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                    }
                }
            } else {
                for (int i = 0; i < deletedPaths.size(); i++) {
                    Path path = deletedPaths.get(i);
                    Path backup = backupPaths.get(i);
                    try {
                        if (Files.isDirectory(backup)) {
                            Files.walkFileTree(backup, new SimpleFileVisitor<Path>() {
                                @Override
                                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                    Path targetDir = path.resolve(backup.relativize(dir));
                                    if (!Files.exists(targetDir)) {
                                        Files.createDirectories(targetDir);
                                    }
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                    Files.copy(file, path.resolve(backup.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        } else {
                            Files.copy(backup, path, StandardCopyOption.REPLACE_EXISTING);
                        }
                        Files.deleteIfExists(backup);
                    } catch (IOException e) {
                        callback.showNotification("Error undoing delete for " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                    }
                }
            }
        }
    }

    class PasteAction implements UndoableAction {
        private final List<Path> pastedPaths;

        PasteAction(List<Path> pastedPaths) {
            this.pastedPaths = new ArrayList<>(pastedPaths);
        }

        @Override
        public void undo() {
            if (serverInfo.isRemote) {
                for (Path path : pastedPaths) {
                    try {
                        String remotePath = path.toString().replace("\\", "/");
                        sshManager.deleteRemoteDirectory(remotePath);
                    } catch (Exception e) {
                        callback.showNotification("Error undoing paste for " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                    }
                }
            } else {
                for (Path path : pastedPaths) {
                    try {
                        if (Files.isDirectory(path)) {
                            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
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
                            });
                        } else {
                            Files.delete(path);
                        }
                    } catch (IOException e) {
                        callback.showNotification("Error undoing paste for " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                    }
                }
            }
        }
    }

    public interface FileManagerCallback {
        void showNotification(String message, FileExplorerScreen.Notification.Type type);
        void refreshDirectory(Path path);
    }
}
