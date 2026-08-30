package redxax.oxy.remotely.packcontent;

import redxax.oxy.remotely.util.BrowserSafeState;

import redxax.oxy.remotely.DesktopRemotelyPaths;
import restudio.rebase.backend.FileSystemProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class PackContentAssetCache {
    private static final PackContentAssetCache INSTANCE = new PackContentAssetCache();
    private final Map<String, Path> localPaths = BrowserSafeState.map();
    private final Path cacheRoot = DesktopRemotelyPaths.appDir().resolve("cache").resolve("pack-content").resolve("assets");

    private PackContentAssetCache() {
    }

    public static PackContentAssetCache get() {
        return INSTANCE;
    }

    public Path localPath(PackContentContext context, Path path) throws Exception {
        if (context == null || context.fileSystem() == null || path == null) {
            return null;
        }
        if (isLocalProvider(context.fileSystem()) && Files.exists(path)) {
            return path;
        }
        String version = version(context.fileSystem(), path);
        String key = instanceKey(context) + "|" + providerKey(context.fileSystem()) + "|" + path.toString() + "|" + version;
        Path cached = localPaths.get(key);
        if (cached != null && Files.exists(cached)) {
            return cached;
        }
        Files.createDirectories(cacheRoot);
        Path target = cacheRoot.resolve(hash(key) + extension(path));
        if (!Files.exists(target)) {
            Path tempDir = Files.createTempDirectory(cacheRoot, "download-");
            try {
                context.fileSystem().download(List.of(path), tempDir).join();
                Path downloaded = tempDir.resolve(path.getFileName().toString());
                if (!Files.exists(downloaded)) {
                    try (var files = Files.list(tempDir)) {
                        downloaded = files.filter(Files::isRegularFile).findFirst().orElse(downloaded);
                    }
                }
                if (!Files.exists(downloaded)) {
                    return null;
                }
                Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                deleteQuietly(tempDir);
            }
        }
        localPaths.put(key, target);
        return target;
    }

    private String version(FileSystemProvider provider, Path path) {
        Path parent = path.getParent();
        if (parent == null || path.getFileName() == null) {
            return String.valueOf(System.currentTimeMillis());
        }
        try {
            for (FileSystemProvider.FileEntry entry : provider.ls(parent).join()) {
                if (entry.path != null && entry.path.getFileName() != null && entry.path.getFileName().equals(path.getFileName())) {
                    String sha1 = entry.metadata.get("sha1");
                    String murmur2 = entry.metadata.get("murmur2");
                    String modified = entry.metadata.get("modifiedEpoch");
                    String size = entry.metadata.get("sizeBytes");
                    String displaySize = entry.size != null ? entry.size : "";
                    if (sha1 != null && !sha1.isBlank()) {
                        return "sha1:" + sha1;
                    }
                    if (murmur2 != null && !murmur2.isBlank()) {
                        return "murmur2:" + murmur2;
                    }
                    if (modified != null || size != null) {
                        return "meta:" + String.valueOf(modified) + ":" + String.valueOf(size);
                    }
                    return "entry:" + displaySize;
                }
            }
        } catch (Exception ignored) {
        }
        return "fresh:" + (System.currentTimeMillis() / 2500L);
    }

    private String providerKey(FileSystemProvider provider) {
        StringBuilder key = new StringBuilder();
        for (String name : List.of("type", "host", "hostId", "user", "port", "serverId")) {
            String value = provider.getMetadata(name);
            if (value != null && !value.isBlank()) {
                key.append(name).append('=').append(value).append(';');
            }
        }
        if (key.isEmpty()) {
            key.append(provider.getClass().getName());
        }
        return key.toString();
    }

    private String instanceKey(PackContentContext context) {
        return context.instance() != null && context.instance().getInstanceId() != null ? context.instance().getInstanceId() : "local";
    }

    private boolean isLocalProvider(FileSystemProvider provider) {
        String type = provider.getMetadata("type");
        return type == null || "LOCAL".equalsIgnoreCase(type);
    }

    private String extension(Path path) {
        String name = path.getFileName() != null ? path.getFileName().toString() : "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot);
        }
        return ".asset";
    }

    private String hash(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void deleteQuietly(Path path) {
        try {
            if (path != null && Files.isDirectory(path)) {
                try (var files = Files.list(path)) {
                    files.forEach(this::deleteQuietly);
                }
                Files.deleteIfExists(path);
            } else if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
        }
    }
}
