package redxax.oxy.remotely.api;

import redxax.oxy.remotely.explorer.FileManager;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class LocalAPI implements RemotelyCoreAPI {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static List<FileManager.ClipboardEntry> clipboard = new ArrayList<>();
    private static boolean isCutOperation = false;
    private final Deque<FileManager.UndoableAction> undoStack = new ArrayDeque<>();
    private final Path tempUndoDir;

    public LocalAPI() {
        this.tempUndoDir = Paths.get(System.getProperty("java.io.tmpdir"), "file_explorer_undo");
        try {
            Files.createDirectories(tempUndoDir);
        } catch (IOException ignored) {}
    }

    @Override
    public CompletableFuture<List<FileEntry>> listDirectory(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            List<FileEntry> entries = new ArrayList<>();
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                return entries;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    boolean isDirectory = Files.isDirectory(entry);
                    String size = isDirectory ? "-" : getFileSize(entry);
                    String created = getCreationDate(entry);
                    String displayName = entry.getFileName().toString();
                    entries.add(new FileEntry(entry, isDirectory, size, created, displayName));
                }
            } catch (IOException e) {
                devPrint("Error listing directory: " + e.getMessage());
            }

            entries.sort(Comparator.comparing((FileEntry x) -> !x.isDirectory)
                    .thenComparing(x -> x.path.getFileName().toString().toLowerCase()));

            return entries;
        });
    }

    @Override
    public CompletableFuture<Void> copy(List<Path> sources, Path destination) {
        return CompletableFuture.runAsync(() -> {
            clipboard = sources.stream()
                    .map(p -> new FileManager.ClipboardEntry(p.toString(), false))
                    .collect(Collectors.toList());
            isCutOperation = false;
        });
    }

    @Override
    public CompletableFuture<Void> cut(List<Path> sources, Path destination) {
        return CompletableFuture.runAsync(() -> {
            clipboard = sources.stream()
                    .map(p -> new FileManager.ClipboardEntry(p.toString(), false))
                    .collect(Collectors.toList());
            isCutOperation = true;
        });
    }

    @Override
    public CompletableFuture<Void> paste(Path destination) {
        return CompletableFuture.runAsync(() -> {
            if (clipboard.isEmpty()) return;

            List<FileManager.PathOperation> operations = new ArrayList<>();

            for (FileManager.ClipboardEntry entry : clipboard) {
                try {
                    Path src = Paths.get(entry.sourcePath);
                    Path dest = destination.resolve(src.getFileName());

                    if (!isCutOperation && src.getParent().equals(destination)) {
                        dest = getUniqueLocalPath(dest);
                    }

                    Files.createDirectories(dest.getParent());

                    if (isCutOperation) {
                        if (dest.toAbsolutePath().startsWith(src.toAbsolutePath())) {
                            throw new IOException("Cannot move folder into its own subdirectory");
                        }
                        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        if (dest.toAbsolutePath().startsWith(src.toAbsolutePath())) {
                            Files.createDirectories(dest);
                            Files.walkFileTree(src, new FileManager.RecursiveFileCopier(src, dest, false, dest));
                        } else {
                            Files.walkFileTree(src, new FileManager.RecursiveFileCopier(src, dest, false, null));
                        }
                    }
                    operations.add(new FileManager.PathOperation(src.toString(), dest.toString()));
                } catch (Exception e) {
                    devPrint("Error pasting " + Paths.get(entry.sourcePath).getFileName() + ": " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            if (!operations.isEmpty()) {
                undoStack.push(new PasteAction(operations, isCutOperation));
                if (isCutOperation) {
                    clipboard.clear();
                }
                isCutOperation = false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> delete(List<Path> paths) {
        return CompletableFuture.runAsync(() -> {
            List<Path> deletedPaths = new ArrayList<>();
            List<String> backupPaths = new ArrayList<>();

            for (Path path : paths) {
                try {
                    Path backupPath = tempUndoDir.resolve(UUID.randomUUID().toString());
                    Files.walkFileTree(path, new FileManager.RecursiveFileCopier(path, backupPath, true, null));
                    deletedPaths.add(path);
                    backupPaths.add(backupPath.toString());
                } catch (IOException e) {
                    devPrint("Error deleting " + path.getFileName() + ": " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            if (!deletedPaths.isEmpty()) {
                undoStack.push(new DeleteAction(new ArrayList<>(deletedPaths), new ArrayList<>(backupPaths)));
            }
        });
    }

    @Override
    public CompletableFuture<Void> rename(Path oldPath, Path newPath) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.move(oldPath, newPath);
                undoStack.push(new RenameAction(oldPath, newPath));
            } catch (IOException e) {
                devPrint("Error renaming file: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> createFile(Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createFile(path);
                undoStack.push(new CreateAction(path));
            } catch (IOException e) {
                devPrint("Error creating file: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> createDirectory(Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectory(path);
                undoStack.push(new CreateAction(path));
            } catch (IOException e) {
                devPrint("Error creating directory: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> upload(List<Path> localPaths, Path remotePath) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Upload not supported for local API"));
    }

    @Override
    public CompletableFuture<Void> download(List<Path> remotePaths, Path localPath) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Download not supported for local API"));
    }

    @Override
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    @Override
    public CompletableFuture<Void> undo() {
        return CompletableFuture.runAsync(() -> {
            if (!undoStack.isEmpty()) {
                undoStack.pop().undo();
            }
        });
    }

    public List<FileManager.ClipboardEntry> getClipboard() {
        return new ArrayList<>(clipboard);
    }

    public boolean isCutOperation() {
        return isCutOperation;
    }

    private String getFileSize(Path file) {
        try {
            long size = Files.size(file);
            return humanReadableByteCountBin(size);
        } catch (IOException e) {
            return "N/A";
        }
    }

    private String humanReadableByteCountBin(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    private String getCreationDate(Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            return dateFormat.format(new Date(attrs.creationTime().toMillis()));
        } catch (IOException e) {
            return "N/A";
        }
    }

    private Path getUniqueLocalPath(Path path) {
        String origName = path.getFileName().toString();
        String baseName = getBaseName(origName);
        String extension = getExtension(origName);
        Path uniquePath = path;
        int counter = 2;
        while (Files.exists(uniquePath)) {
            String newName = baseName + " (" + counter + ")" + extension;
            uniquePath = path.resolveSibling(newName);
            counter++;
        }
        return uniquePath;
    }

    private String getBaseName(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
    }

    private String getExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex) : "";
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new FileManager.RecursiveFileDeleter());
        } else {
            Files.delete(path);
        }
    }

    class DeleteAction implements FileManager.UndoableAction {
        private final List<Path> deletedPaths;
        private final List<String> backupPaths;

        DeleteAction(List<Path> deletedPaths, List<String> backupPaths) {
            this.deletedPaths = deletedPaths;
            this.backupPaths = backupPaths;
        }

        @Override
        public void undo() {
            for (int i = 0; i < deletedPaths.size(); i++) {
                try {
                    Path dest = deletedPaths.get(i);
                    Path backup = Paths.get(backupPaths.get(i));
                    Files.walkFileTree(backup, new FileManager.RecursiveFileCopier(backup, dest, true, null));
                } catch (IOException e) {
                    devPrint("Error undoing delete: " + e.getMessage());
                }
            }
        }
    }

    class PasteAction implements FileManager.UndoableAction {
        private final List<FileManager.PathOperation> operations;
        private final boolean wasCut;

        PasteAction(List<FileManager.PathOperation> operations, boolean wasCut) {
            this.operations = operations;
            this.wasCut = wasCut;
        }

        @Override
        public void undo() {
            for (FileManager.PathOperation op : operations) {
                try {
                    if (wasCut) {
                        Files.move(Paths.get(op.destination()), Paths.get(op.source()), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        deleteRecursively(Paths.get(op.destination()));
                    }
                } catch (Exception e) {
                    devPrint("Error undoing paste: " + e.getMessage());
                }
            }
        }
    }

    class RenameAction implements FileManager.UndoableAction {
        private final Path oldPath;
        private final Path newPath;

        RenameAction(Path oldPath, Path newPath) {
            this.oldPath = oldPath;
            this.newPath = newPath;
        }

        @Override
        public void undo() {
            try {
                Files.move(newPath, oldPath);
            } catch (IOException e) {
                devPrint("Error undoing rename: " + e.getMessage());
            }
        }
    }

    class CreateAction implements FileManager.UndoableAction {
        private final Path path;

        CreateAction(Path path) {
            this.path = path;
        }

        @Override
        public void undo() {
            try {
                deleteRecursively(path);
            } catch (IOException e) {
                devPrint("Error undoing create: " + e.getMessage());
            }
        }
    }
}