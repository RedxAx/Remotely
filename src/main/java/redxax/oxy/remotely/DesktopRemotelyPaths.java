package redxax.oxy.remotely;

import redxax.oxy.remotely.util.AsyncTools;
import redxax.oxy.remotely.util.BrowserSafeState;
import redxax.oxy.remotely.util.TaskSchedulers;
import restudio.rebase.instance.InstanceStorageLayout;
import restudio.rescreen.platform.Async;
import restudio.rebase.util.ApplicationStorageMigrator;
import restudio.rebase.util.UserDataPaths;
import restudio.rescreen.config.AppStoragePaths;
import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class DesktopRemotelyPaths {
    private static final String LEGACY_MIGRATION_MARKER = ".storage-migration-v2-complete";
    private static final Set<String> EXCLUDED_MIGRATION_ROOTS = Set.of("instances", "quick-servers", "java-runtimes");
    private static final BrowserSafeState.BooleanValue MIGRATION_STARTED = new BrowserSafeState.BooleanValue();

    private DesktopRemotelyPaths() {
    }

    public static Path appDir() {
        return UserDataPaths.appDir(RemotelyPaths.applicationId());
    }

    public static Path instancesDir() {
        return UserDataPaths.instancesDir(RemotelyPaths.applicationId());
    }

    public static Path legacyAppDir() {
        return UserDataPaths.instanceStorageDir(RemotelyPaths.applicationId());
    }

    public static Path dataDir(Path applicationDir) {
        return AppStoragePaths.data(applicationDir);
    }

    public static Path logsDir(Path applicationDir) {
        return AppStoragePaths.logs(applicationDir);
    }

    public static Path minecraftVersionsDir() {
        return UserDataPaths.minecraftDir().resolve("versions");
    }

    public static Path initializeApplicationDir() {
        Path appDir = appDir();
        try {
            Files.createDirectories(appDir);
            ApplicationStorageMigrator.organize(appDir);
        } catch (IOException exception) {
            ReLog.logger(LogTypes.FILESYSTEM).source(LogSource.application("Remotely")).component(DesktopRemotelyPaths.class).error("Could not organize Remotely application storage", exception);
        }
        try {
            InstanceStorageLayout.initialize(UserDataPaths.instanceStorageDir(RemotelyPaths.applicationId()), RemotelyPaths.applicationId());
        } catch (IOException exception) {
            ReLog.logger(LogTypes.FILESYSTEM).source(LogSource.application("Remotely")).component(DesktopRemotelyPaths.class).error("Could not create Remotely storage", exception);
        }
        return appDir;
    }

    public static Async<Void> migrateLegacyAppDataAsync() {
        if (!MIGRATION_STARTED.compareAndSet(false, true)) {
            return Async.completed(null);
        }
        return AsyncTools.run(TaskSchedulers.current(), () -> migrateLegacyAppData(appDir()));
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
                        ReLog.logger(LogTypes.FILESYSTEM).source(LogSource.resource(source.toString(), "Legacy Data")).component(DesktopRemotelyPaths.class).operation("Migrate Storage").error("Could not migrate Remotely data", exception);
                    }
                }
            }
            ApplicationStorageMigrator.organize(appDir);
            if (complete) {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, "done");
            }
        } catch (IOException exception) {
            ReLog.logger(LogTypes.FILESYSTEM).source(LogSource.application("Remotely")).component(DesktopRemotelyPaths.class).operation("Migrate Storage").error("Could not migrate Remotely data", exception);
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
