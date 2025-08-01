package redxax.oxy.remotely.api;

import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.explorer.FileManager;
import redxax.oxy.remotely.servers.RemoteHostInfo;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class RemoteAPI implements RemotelyCoreAPI {
    private final RemoteHostInfo remoteHost;
    private final SSHManager sshManager;
    private static List<FileManager.ClipboardEntry> clipboard = new ArrayList<>();
    private static boolean isCutOperation = false;

    public RemoteAPI(RemoteHostInfo remoteHost) {
        this.remoteHost = remoteHost;
        this.sshManager = remoteHost.getSSHManager();
    }

    @Override
    public CompletableFuture<List<FileEntry>> listDirectory(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            List<FileEntry> entries = new ArrayList<>();
            try {
                ensureConnected();
                String remotePath = path.toString().replace("\\", "/");
                Map<String, Boolean> entriesWithTypes = sshManager.listRemoteDirectoryWithTypes(remotePath);

                for (Map.Entry<String, Boolean> entry : entriesWithTypes.entrySet()) {
                    String filename = entry.getKey();
                    boolean isDirectory = entry.getValue();
                    Path entryPath = path.resolve(filename);
                    entries.add(new FileEntry(entryPath, isDirectory, "", "", filename));
                }
            } catch (Exception e) {
                devPrint("Error listing remote directory: " + e.getMessage());
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
                    .map(p -> new FileManager.ClipboardEntry(p.toString().replace("\\", "/"), true))
                    .collect(Collectors.toList());
            isCutOperation = false;
        });
    }

    @Override
    public CompletableFuture<Void> cut(List<Path> sources, Path destination) {
        return CompletableFuture.runAsync(() -> {
            clipboard = sources.stream()
                    .map(p -> new FileManager.ClipboardEntry(p.toString().replace("\\", "/"), true))
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
                    ensureConnected();
                    String currentRemote = destination.toString().replace("\\", "/");
                    if (!currentRemote.endsWith("/")) {
                        currentRemote += "/";
                    }
                    String fileName = Paths.get(entry.sourcePath).getFileName().toString();
                    String remoteDest = currentRemote + fileName;

                    if (!isCutOperation && entry.isRemote && remoteDest.equals(entry.sourcePath)) {
                        remoteDest = getUniqueRemotePath(remoteDest);
                    }

                    if (entry.isRemote) {
                        if (isCutOperation) {
                            sshManager.renameRemote(entry.sourcePath, remoteDest);
                        } else {
                            sshManager.runRemoteCommand("cp -rf \"" + entry.sourcePath + "\" \"" + remoteDest + "\"");
                        }
                        operations.add(new FileManager.PathOperation(entry.sourcePath, remoteDest));
                    } else {
                        Path localSrc = Paths.get(entry.sourcePath);
                        sshManager.upload(localSrc, remoteDest);
                        if (isCutOperation) {
                            java.nio.file.Files.walkFileTree(localSrc, new FileManager.RecursiveFileDeleter());
                        }
                        operations.add(new FileManager.PathOperation(entry.sourcePath, remoteDest));
                    }
                } catch (Exception e) {
                    devPrint("Error pasting remote files: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            if (!operations.isEmpty()) {
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
            try {
                ensureConnected();
                String homeDir = remoteHost.getHomeDirectory();

                for (Path path : paths) {
                    String remotePath = path.toString().replace("\\", "/");
                    String fileName = Paths.get(remotePath).getFileName().toString();
                    String trashPath = homeDir + ".remotely/trash/" + fileName;

                    if (!sshManager.remoteFileExists(homeDir + ".remotely/trash")) {
                        sshManager.runRemoteCommand("mkdir -p " + homeDir + ".remotely/trash && mv -f \"" + remotePath + "\" \"" + trashPath + "\"");
                    } else {
                        sshManager.runRemoteCommand("mv -f \"" + remotePath + "\" \"" + trashPath + "\"");
                    }
                }
            } catch (Exception e) {
                devPrint("Error deleting remote files: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> rename(Path oldPath, Path newPath) {
        return CompletableFuture.runAsync(() -> {
            try {
                ensureConnected();
                String oldRemotePath = oldPath.toString().replace("\\", "/");
                String newRemotePath = newPath.toString().replace("\\", "/");
                sshManager.renameRemoteFile(oldRemotePath, newRemotePath);
            } catch (Exception e) {
                devPrint("Error renaming remote file: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> createFile(Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                ensureConnected();
                String remotePath = path.toString().replace("\\", "/");
                sshManager.writeRemoteFile(remotePath, "");
            } catch (Exception e) {
                devPrint("Error creating remote file: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> createDirectory(Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                ensureConnected();
                String remotePath = path.toString().replace("\\", "/");
                sshManager.sftpChannel.mkdir(remotePath);
            } catch (Exception e) {
                devPrint("Error creating remote directory: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> upload(List<Path> localPaths, Path remotePath) {
        return CompletableFuture.runAsync(() -> {
            try {
                ensureConnected();
                for (Path localPath : localPaths) {
                    String remoteDestination = remotePath.resolve(localPath.getFileName()).toString().replace("\\", "/");
                    sshManager.upload(localPath, remoteDestination);
                }
            } catch (Exception e) {
                devPrint("Error uploading files: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> download(List<Path> remotePaths, Path localPath) {
        return CompletableFuture.runAsync(() -> {
            try {
                ensureConnected();
                for (Path remotePath : remotePaths) {
                    Path localDestination = localPath.resolve(remotePath.getFileName());
                    sshManager.download(remotePath.toString().replace("\\", "/"), localDestination);
                }
            } catch (Exception e) {
                devPrint("Error downloading files: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<String> readFile(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureConnected();
                return sshManager.readRemoteFile(path.toString().replace("\\", "/"));
            } catch (Exception e) {
                throw new RuntimeException("Failed to read remote file: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> writeFile(Path path, String content) {
        return CompletableFuture.runAsync(() -> {
            try {
                ensureConnected();
                sshManager.writeRemoteFile(path.toString().replace("\\", "/"), content);
            } catch (Exception e) {
                throw new RuntimeException("Failed to write to remote file: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public boolean canUndo() {
        return false;
    }

    @Override
    public CompletableFuture<Void> undo() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Undo not supported for remote operations"));
    }

    public List<FileManager.ClipboardEntry> getClipboard() {
        return new ArrayList<>(clipboard);
    }

    public boolean isCutOperation() {
        return isCutOperation;
    }

    private void ensureConnected() {
        if (remoteHost == null) {
            throw new RuntimeException("Remote host is null");
        }
        try {
            if (!sshManager.isSSH()) {
                sshManager.connectToRemoteHost(
                        remoteHost.getUser(),
                        remoteHost.getIp(),
                        remoteHost.getPort(),
                        remoteHost.getPassword()
                );
            }
            if (!sshManager.isSFTPConnected()) {
                sshManager.connectSFTPSync();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure remote connection: " + e.getMessage());
        }
    }

    private String getUniqueRemotePath(String path) {
        String fileName = Paths.get(path).getFileName().toString();
        String dirPath = path.substring(0, path.length() - fileName.length());
        String baseName = getBaseName(fileName);
        String extension = getExtension(fileName);
        int counter = 2;
        String newPath = path;
        try {
            while (sshManager.remoteFileExists(newPath)) {
                String newName = baseName + " (" + counter + ")" + extension;
                newPath = dirPath + newName;
                counter++;
            }
        } catch (Exception e) {
            return path;
        }
        return newPath;
    }

    private String getBaseName(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
    }

    private String getExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex) : "";
    }
}