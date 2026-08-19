package redxax.oxy.remotely.ui.settings.controllers;

import com.sun.management.OperatingSystemMXBean;
import restudio.rebase.Rebase;
import restudio.rebase.backend.feature.RemoteShellFeature;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.java.JavaManager;
import restudio.rebase.java.JavaRuntime;
import restudio.rescreen.platform.Async;
import redxax.oxy.remotely.ui.settings.controllers.ServerJvmSettingsProvider.RuntimeOption;
import redxax.oxy.remotely.util.DesktopAsyncTools;

import java.lang.management.ManagementFactory;
import java.util.List;

public final class DesktopServerJvmSettingsProvider implements ServerJvmSettingsProvider {
    private final Instance instance;
    private final JavaManager javaManager;

    public DesktopServerJvmSettingsProvider(Instance instance) {
        this.instance = instance;
        javaManager = Rebase.get().getJavaManager();
    }

    @Override public String jvmArgs() { return instance.getJvmArgs(); }
    @Override public void jvmArgs(String value) { instance.setJvmArgs(value); }
    @Override public String version() { return instance.getVersionId(); }
    @Override public boolean remote() { return instance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(instance.getBackendConfig().type); }

    @Override
    public int maximumMemoryMb() {
        long bytes = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class).getTotalMemorySize();
        return Math.max(512, (int) (bytes / 1024L / 1024L));
    }

    @Override public List<RuntimeOption> runtimes() { return javaManager.getRuntimes().stream().map(this::option).toList(); }

    @Override
    public List<RuntimeOption> remoteRuntimes() {
        return remoteHost() == null ? List.of() : javaManager.getRemoteRuntimes(remoteHost()).stream().map(this::option).toList();
    }

    @Override
    public Async<Void> refreshRemoteRuntimes() {
        return remoteHost() == null ? Async.completed(null) : DesktopAsyncTools.adapt(javaManager.refreshRemoteRuntimes(remoteHost())).thenApply(ignored -> null);
    }

    @Override public int minimumJavaVersion(String minecraftVersion) { return javaManager.getMinimumJavaVersion(minecraftVersion); }
    @Override public int maximumJavaVersion(String minecraftVersion) { return javaManager.getMaximumJavaVersion(minecraftVersion); }

    private RemoteHost remoteHost() {
        if (instance.getBackend() == null) return null;
        return instance.getBackend().getFeature(RemoteShellFeature.class).map(RemoteShellFeature::remoteHost).orElse(null);
    }

    private RuntimeOption option(JavaRuntime runtime) {
        return new RuntimeOption(runtime.getName(), runtime.getPath(), runtime.getMajorVersion());
    }
}
