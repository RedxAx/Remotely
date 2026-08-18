package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.platform.Async;

import java.util.List;

public interface ServerJvmSettingsProvider {
    String jvmArgs();

    void jvmArgs(String value);

    String version();

    boolean remote();

    int maximumMemoryMb();

    List<RuntimeOption> runtimes();

    List<RuntimeOption> remoteRuntimes();

    Async<Void> refreshRemoteRuntimes();

    int minimumJavaVersion(String minecraftVersion);

    int maximumJavaVersion(String minecraftVersion);

    record RuntimeOption(String name, String path, int majorVersion) {
    }
}
