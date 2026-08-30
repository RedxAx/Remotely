package redxax.oxy.remotely.host;

import redxax.oxy.remotely.RemotelyInit;
import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public final class DesktopProcessLauncher {
    private DesktopProcessLauncher() {
    }

    public static boolean openExternal() {
        try {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            String classpath = System.getProperty("java.class.path");
            String className = RemotelyInit.class.getName();

            File tempFile = File.createTempFile("remotely_args", ".txt");
            tempFile.deleteOnExit();
            try (PrintWriter writer = new PrintWriter(tempFile)) {
                writer.println("-cp");
                writer.println(classpath);
                writer.println(className);
            }

            new ProcessBuilder(javaBin, "@" + tempFile.getAbsolutePath()).start();
            return true;
        } catch (IOException e) {
            ReLog.logger(LogTypes.USER_INTERFACE).source(LogSource.application("Remotely")).component(DesktopProcessLauncher.class).operation("Open External Window").error("Could not open Remotely externally", e);
            return false;
        }
    }
}
