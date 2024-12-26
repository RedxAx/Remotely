package redxax.oxy.explorer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class FileManager {
    private List<Path> clipboard = new ArrayList<>();
    private boolean isCut = false;
    private Deque<UndoableAction> undoStack = new ArrayDeque<>();
    private final FileManagerCallback callback;

    public FileManager(FileManagerCallback callback) {
        this.callback = callback;
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
        for (Path path : selectedPaths) {
            try {
                if (Files.isDirectory(path)) {
                    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            deletedPaths.add(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            deletedPaths.add(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    Files.delete(path);
                    deletedPaths.add(path);
                }
                toRemove.add(path);
            } catch (IOException e) {
                callback.showNotification("Error deleting " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
            }
        }
        if (!deletedPaths.isEmpty()) {
            undoStack.push(new DeleteAction(deletedPaths));
        }
        callback.refreshDirectory(currentPath);
    }

    public void paste(Path currentPath) {
        List<Path> toDelete = new ArrayList<>();
        List<Path> pastedPaths = new ArrayList<>();
        for (Path src : clipboard) {
            Path dest = currentPath.resolve(src.getFileName());
            if (src.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
                continue;
            }
            try {
                if (Files.exists(dest)) {
                    if (Files.isDirectory(src)) {
                        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                Path targetDir = dest.resolve(currentPath.relativize(dir));
                                if (!Files.exists(targetDir)) {
                                    Files.createDirectory(targetDir);
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.copy(file, dest.resolve(currentPath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                                pastedPaths.add(dest.resolve(currentPath.relativize(file)));
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
                                Path targetDir = dest.resolve(currentPath.relativize(dir));
                                if (!Files.exists(targetDir)) {
                                    Files.createDirectory(targetDir);
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.copy(file, dest.resolve(currentPath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                                pastedPaths.add(dest.resolve(currentPath.relativize(file)));
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
        if (!pastedPaths.isEmpty()) {
            undoStack.push(new PasteAction(pastedPaths));
        }
        for (Path path : toDelete) {
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
                callback.showNotification("Error deleting " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
            }
        }
        if (isCut) {
            clipboard.clear();
            isCut = false;
        }
        callback.refreshDirectory(currentPath);
    }

    public void undo(Path currentPath) {
        if (!undoStack.isEmpty()) {
            UndoableAction action = undoStack.pop();
            action.undo();
            callback.refreshDirectory(currentPath);
        }
    }

    private interface UndoableAction {
        void undo();
    }

    private class DeleteAction implements UndoableAction {
        private final List<Path> deletedPaths;

        DeleteAction(List<Path> deletedPaths) {
            this.deletedPaths = new ArrayList<>(deletedPaths);
        }

        @Override
        public void undo() {
            for (Path path : deletedPaths) {
                try {
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(path);
                    } else {
                        Files.createFile(path);
                    }
                } catch (IOException e) {
                    callback.showNotification("Error undoing delete for " + path.getFileName() + ": " + e.getMessage(), FileExplorerScreen.Notification.Type.ERROR);
                }
            }
        }
    }

    private class PasteAction implements UndoableAction {
        private final List<Path> pastedPaths;

        PasteAction(List<Path> pastedPaths) {
            this.pastedPaths = new ArrayList<>(pastedPaths);
        }

        @Override
        public void undo() {
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

    public interface FileManagerCallback {
        void showNotification(String message, FileExplorerScreen.Notification.Type type);
        void refreshDirectory(Path path);
    }
}
