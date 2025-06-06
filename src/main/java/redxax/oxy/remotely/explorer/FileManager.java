package redxax.oxy.remotely.explorer;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public class FileManager {
    public static class ClipboardEntry {
        public final String sourcePath;
        public final boolean isRemote;

        public ClipboardEntry(String sourcePath, boolean isRemote) {
            this.sourcePath = sourcePath;
            this.isRemote = isRemote;
        }
    }

    public interface UndoableAction {
        void undo();
    }

    public record PathOperation(String source, String destination) {}

    public static class RecursiveFileCopier extends SimpleFileVisitor<Path> {
        private final Path source;
        private final Path target;
        private final boolean deleteSource;
        private final Path skipPath;

        public RecursiveFileCopier(Path source, Path target, boolean deleteSource, Path skipPath) {
            this.source = source;
            this.target = target;
            this.deleteSource = deleteSource;
            this.skipPath = skipPath;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (skipPath != null && dir.toAbsolutePath().startsWith(skipPath.toAbsolutePath()))
                return FileVisitResult.SKIP_SUBTREE;
            Path newDir = target.resolve(source.relativize(dir));
            Files.createDirectories(newDir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (skipPath != null && file.toAbsolutePath().startsWith(skipPath.toAbsolutePath()))
                return FileVisitResult.CONTINUE;
            Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
            if (deleteSource)
                Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (deleteSource)
                Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    public static class RecursiveFileDeleter extends SimpleFileVisitor<Path> {
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
}