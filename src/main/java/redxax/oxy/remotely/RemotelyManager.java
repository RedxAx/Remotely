package redxax.oxy.remotely;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rebase.IRebaseManager;
import restudio.rebase.account.AccountManager;
import restudio.rebase.backup.BackupManager;
import restudio.rebase.cache.CacheManager;
import restudio.rebase.config.RebaseConfigManager;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.java.JavaManager;
import restudio.rebase.preset.OptionsPresetManager;
import restudio.rebase.preset.ResourceListManager;
import restudio.rebase.resource.InstanceResourceManager;
import restudio.rebase.resource.ResourceMetadataManager;
import restudio.rebase.resource.UpdateManager;
import restudio.rebase.resource.provider.*;
import restudio.rebase.util.PlaytimeManager;

import java.nio.file.Path;
import java.util.List;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyManager implements IRebaseManager {
    private final InstanceManager instanceManager;
    private final RemotelyConfigManager configManager;
    private final BackupManager backupManager;
    private final CacheManager cacheManager;
    private final JavaManager javaManager;
    private final OptionsPresetManager optionsPresetManager;
    private final PlaytimeManager playtimeManager;
    private final ResourceListManager resourceListManager;
    private final ResourceMetadataManager resourceMetadataManager;
    private final InstanceResourceManager instanceResourceManager;
    private final UpdateManager updateManager;
    private final List<IResourceProvider> resourceProviders;

    public RemotelyManager() {
        Path applicationDir = remotelyDir;
        this.configManager = new RemotelyConfigManager(applicationDir);
        this.configManager.apply();

        this.instanceManager = InstanceManager.getInstance();
        this.backupManager = new BackupManager(applicationDir);
        this.cacheManager = new CacheManager(applicationDir);
        this.javaManager = new JavaManager(applicationDir);
        this.optionsPresetManager = new OptionsPresetManager(applicationDir);
        this.playtimeManager = new PlaytimeManager(applicationDir);
        this.resourceListManager = new ResourceListManager(applicationDir);
        this.resourceMetadataManager = new ResourceMetadataManager(applicationDir);

        this.resourceProviders = List.of(
                new ModrinthProvider(),
                new CurseForgeProvider(),
                new HangarProvider(),
                new SpigetProvider()
        );

        this.instanceResourceManager = new InstanceResourceManager(resourceMetadataManager, cacheManager, resourceProviders);
        this.updateManager = new UpdateManager(applicationDir);
        init();
    }

    private void init() {
        javaManager.refreshRuntimes();
    }


    @Override
    public InstanceManager getInstanceManager() {
        return instanceManager;
    }

    @Override
    public InstanceResourceManager getResourceManager() {
        return instanceResourceManager;
    }

    @Override
    public UpdateManager getUpdateManager() {
        return updateManager;
    }

    @Override
    public BackupManager getBackupManager() {
        return backupManager;
    }

    @Override
    public CacheManager getCacheManager() {
        return cacheManager;
    }

    @Override
    public ResourceMetadataManager getResourceMetadataManager() {
        return resourceMetadataManager;
    }

    @Override
    public List<IResourceProvider> getResourceProviders() {
        return resourceProviders;
    }

    @Override
    public IResourceProvider getResourceProvider(String name) {
        return resourceProviders.stream().filter(p -> p.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    @Override
    public AccountManager getAccountManager() {
        return null;
    }

    @Override
    public ResourceListManager getResourceListManager() {
        return resourceListManager;
    }

    @Override
    public OptionsPresetManager getOptionsPresetManager() {
        return optionsPresetManager;
    }

    @Override
    public PlaytimeManager getPlaytimeManager() {
        return playtimeManager;
    }

    @Override
    public JavaManager getJavaManager() {
        return javaManager;
    }

    @Override
    public RebaseConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public Path getLauncherJarPath() {
        throw new UnsupportedOperationException("getLauncherJarPath is not applicable for Remotely as a mod.");
    }

    @Override
    public void log(String message) {
        System.out.println("[Remotely/Rebase] " + message);
    }
}