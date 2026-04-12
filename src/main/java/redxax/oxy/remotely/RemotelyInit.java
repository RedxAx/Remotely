package redxax.oxy.remotely;

import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rebase.Rebase;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.util.RebaseLogger;
import restudio.rescreen.Main;
import restudio.rescreen.config.Config;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInit {
    private static final String MAC_RELAUNCH_PROPERTY = "remotely.macos.firstThreadReady";

    public static void initCommon() {
        try {
            if (Rebase.get() != null) return;
        } catch (IllegalStateException ignored) {
            System.out.println("Rebase already initialized, skipping...");
        }

        InstanceManager.initialize(remotelyDir);

        Config.applicationDir = remotelyDir;
        Config.setConfigManager(new RemotelyConfigManager(remotelyDir));

        ReStudio.getInstance().init(remotelyDir);

        RemotelyManager remotelyManager = new RemotelyManager();
        Rebase.initialize(remotelyManager);
        RebaseLogger.setLogger(remotelyManager::log);
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
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static boolean hasStartOnFirstThreadArg() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-XstartOnFirstThread");
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }
}
