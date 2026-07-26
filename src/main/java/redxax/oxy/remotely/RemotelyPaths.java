package redxax.oxy.remotely;

import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;
import restudio.rescreen.config.AppStoragePaths;
import restudio.rebase.instance.InstanceStorageLayout;
import restudio.rebase.util.ApplicationStorageMigrator;
import restudio.rebase.util.Executors;
import restudio.rebase.util.UserDataPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RemotelyPaths {
    private static final String LEGACY_MIGRATION_MARKER = ".storage-migration-v2-complete";
    private static final Set<String> EXCLUDED_MIGRATION_ROOTS = Set.of("instances", "quick-servers", "java-runtimes");
    private static final AtomicBoolean MIGRATION_STARTED = new AtomicBoolean();

    private RemotelyPaths() {
    }

    public static Path appDir() {
        return UserDataPaths.appDir("remotely");
    }

    public static Path instancesDir() {
        return UserDataPaths.instancesDir("remotely");
    }

    public static Path legacyAppDir() {
        return UserDataPaths.instanceStorageDir("remotely");
    }

    public static Path initializeApplicationDir() {
        Path appDir = appDir();
        try {
            Files.createDirectories(appDir);
            ApplicationStorageMigrator.organize(appDir);
        } catch (IOException exception) {
            ReLog.logger(LogTypes.FILESYSTEM).source(LogSource.application("Remotely")).component(RemotelyPaths.class).error("Could not organize Remotely application storage", exception);
        }
        try {
            InstanceStorageLayout.initialize(UserDataPaths.instanceStorageDir("remotely"), "remotely");
        } catch (IOException exception) {
            ReLog.logger(LogTypes.FILESYSTEM).source(LogSource.application("Remotely")).component(RemotelyPaths.class).error("Could not create Remotely storage", exception);
        }
        return appDir;
    }

    public static CompletableFuture<Void> migrateLegacyAppDataAsync() {
        if (!MIGRATION_STARTED.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> migrateLegacyAppData(appDir()), Executors.IO);
    }

    private static void migrateLegacyAppData(Path appDir) {
        Path legacyDir = legacyAppDir();
        if (!Files.isDirectory(legacyDir) || legacyDir.equals(appDir.toAbsolutePath().normalize())) {
            return;
        }
        Path marker = AppStoragePaths.meta(appDir).resolve("migrations").resolve(LEGACY_MIGRATION_MARKER);
        if (Files.exists(marker)) {
            return;
        }
        try {
            Files.createDirectories(appDir);
            boolean complete = true;
            try (var entries = Files.list(legacyDir)) {
                for (Path source : entries.toList()) {
                    if (isMigrationExcluded(source)) {
                        continue;
                    }
                    try {
                        ApplicationStorageMigrator.organizeLegacyEntry(appDir, source);
                    } catch (IOException exception) {
                        complete = false;
                        ReLog.logger(LogTypes.FILESYSTEM).source(LogSource.resource(source.toString(), "Legacy Data")).component(RemotelyPaths.class).operation("Migrate Storage").error("Could not migrate Remotely data", exception);
                    }
                }
            }
            ApplicationStorageMigrator.organize(appDir);
            if (complete) {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, "done");
            }
        } catch (IOException exception) {
            ReLog.logger(LogTypes.FILESYSTEM).source(LogSource.application("Remotely")).component(RemotelyPaths.class).operation("Migrate Storage").error("Could not migrate Remotely data", exception);
        }
    }

    private static boolean isMigrationExcluded(Path source) {
        Path name = source.getFileName();
        if (name != null && EXCLUDED_MIGRATION_ROOTS.contains(name.toString().toLowerCase(Locale.ROOT))) {
            return true;
        }
        return Files.isDirectory(source) && Files.isRegularFile(source.resolve("instance.properties"));
    }
}
