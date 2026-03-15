package redxax.oxy.remotely.mcassets;

import restudio.rescreen.platform.lwjgl.MinecraftAssetSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ManagedMinecraftAssetSource implements MinecraftAssetSource {
    private final MinecraftAssetsManager manager;

    public ManagedMinecraftAssetSource(MinecraftAssetsManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public byte[] readAsset(String assetPath) throws IOException {
        String normalized = normalizeAssetPath(assetPath);
        Path assetsDir = manager.getActiveAssetsDir();
        if (assetsDir == null) {
            throw new FileNotFoundException(normalized);
        }
        Path file = assetsDir.resolve(normalized.replace('/', java.io.File.separatorChar)).normalize();
        if (!file.startsWith(assetsDir) || !Files.isRegularFile(file)) {
            throw new FileNotFoundException(normalized);
        }
        return Files.readAllBytes(file);
    }

    public long getRevision() {
        return manager.getRevision();
    }

    private String normalizeAssetPath(String assetPath) {
        String normalized = assetPath == null ? "" : assetPath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.regionMatches(true, 0, "assets/", 0, 7)) {
            normalized = normalized.substring(7);
        }
        return normalized;
    }
}
