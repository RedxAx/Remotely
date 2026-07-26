package redxax.oxy.remotely;

import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.packcontent.RemotelyPackContentIntegration;
import restudio.rebase.Rebase;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.restudio.ReStudio;
import restudio.rescreen.Main;
import restudio.rescreen.config.Config;
import restudio.rescreen.config.AppStoragePaths;
import restudio.rescreen.logging.LogSettings;
import restudio.rescreen.logging.ReLog;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInit {
    private static final String MAC_RELAUNCH_PROPERTY = "remotely.macos.firstThreadReady";

    public static void initCommon() {
        initCommon(RemotelyApplication.APP);
    }

    public static void initCommon(RemotelyApplication application) {
        if (isRebaseInitialized()) {
            return;
        }

        RemotelyPaths.initializeApplicationDir();
        Config.applicationDir = remotelyDir;
        LogSettings logSettings = LogSettings.standard(application == RemotelyApplication.APP ? "Remotely" : "Remotely Mod", AppStoragePaths.logs(remotelyDir));
        ReLog.initialize(application == RemotelyApplication.MOD ? logSettings.withoutStandardStreamCapture() : logSettings);
        RemotelyConfigManager configManager = new RemotelyConfigManager(remotelyDir);
        Config.setConfigManager(configManager);
        InstanceManager.initialize(remotelyDir, configManager.getInstancesDir());
        InstanceManager.getInstance().addLegacyInstancesDir(RemotelyPaths.legacyAppDir());
        InstanceManager.getInstance().loadInstances();

        RemotelyManager remotelyManager = new RemotelyManager();
        Rebase.initialize(remotelyManager);
        ReStudio.getInstance().init(remotelyDir, application.reStudioClientId());
        RemotelyPackContentIntegration.install();
        RemotelyPaths.migrateLegacyAppDataAsync();
    }

    private static boolean isRebaseInitialized() {
        try {
            Rebase.get();
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static void initClient(ApplicationHost host) {
        new RemotelyClient(host).initialize();
    }

    public static void main(String[] args) {
        if (!ensureMacFirstThread(args)) {
            return;
        }
        Main.setEntryClass(RemotelyEntry.class);
        initCommon();
        Main.main(args);
    }

    private static boolean ensureMacFirstThread(String[] args) {
        if (!isMacOs() || Boolean.getBoolean(MAC_RELAUNCH_PROPERTY) || hasStartOnFirstThreadArg()) {
            return true;
        }

        List<String> command = new ArrayList<>();
        command.add(getJavaBinaryPath());
        command.add("-XstartOnFirstThread");
        command.add("-D" + MAC_RELAUNCH_PROPERTY + "=true");

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        for (String jvmArg : runtimeMXBean.getInputArguments()) {
            if (!"-XstartOnFirstThread".equals(jvmArg) && !jvmArg.startsWith("-D" + MAC_RELAUNCH_PROPERTY + "=")) {
                command.add(jvmArg);
            }
        }

        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(RemotelyInit.class.getName());
        command.addAll(List.of(args));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.inheritIO();

        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            System.exit(exitCode);
            return false;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to relaunch with -XstartOnFirstThread", e);
        }
    }

    private static String getJavaBinaryPath() {
        String executableName = isWindows() ? "java.exe" : "java";
        String currentCommand = ProcessHandle.current().info().command().orElse(null);
        if (currentCommand != null && !currentCommand.isBlank()) {
            Path current = Path.of(currentCommand).toAbsolutePath().normalize();
            if (Files.isRegularFile(current) && current.getFileName().toString().equalsIgnoreCase(executableName)) {
                return current.toString();
            }
        }
        Path javaHomeExecutable = Path.of(System.getProperty("java.home"), "bin", executableName);
        if (Files.isRegularFile(javaHomeExecutable)) {
            return javaHomeExecutable.toAbsolutePath().normalize().toString();
        }
        throw new IllegalStateException("Current Java Launcher Is Unavailable");
    }

    private static boolean hasStartOnFirstThreadArg() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-XstartOnFirstThread");
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
