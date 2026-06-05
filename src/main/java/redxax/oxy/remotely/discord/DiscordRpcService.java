package redxax.oxy.remotely.discord;

import de.jcm.discordgamesdk.Core;
import de.jcm.discordgamesdk.CreateParams;
import de.jcm.discordgamesdk.LogLevel;
import de.jcm.discordgamesdk.activity.Activity;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.ui.server.ServerIconManager;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rebase.discord.RebaseDiscordRpcService;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class DiscordRpcService extends RebaseDiscordRpcService {
    private Core core;
    private final ServerIconManager iconManager = new ServerIconManager(remotelyDir);
    private final Set<String> remoteIconLoadRequests = ConcurrentHashMap.newKeySet();

    public DiscordRpcService(RemotelyConfigManager configManager, InstanceManager instanceManager) {
        super(configManager, instanceManager, new AppDefaults(
                "1512411398146363412",
                "Remotely",
                "remotely",
                "remotely.discord.appId",
                "discordRpc.manager.details",
                "discordRpc.manager.state",
                "In Remotely",
                "Managing Servers",
                "discordRpc.server.details",
                "discordRpc.server.state",
                "Viewing Server",
                "{server}",
                "discordRpc.running.details",
                "discordRpc.running.state",
                "Managing Server",
                "{players} Players | {server}",
                "discordRpc.resync.details",
                "discordRpc.resync.state",
                "Designing ReSync",
                "{server} | {studio}"
        ));
    }

    public void setManagerActive() {
        setHomeActive("Server Manager");
    }

    public void setGlobalSettingsActive() {
        setCustomActive(null, "Settings", "Settings", "In Settings", "Remotely");
    }

    public void setServerSettingsActive(Instance instance) {
        setCustomActive(instance, instance != null ? instance.getName() : "", "Server Settings", "Editing Server", "{server}");
    }

    public void setServerCreationActive() {
        setCustomActive(null, "New Server", "Create Server", "Creating Server", "New Server");
    }

    public void setServerActive(Instance instance, String viewName) {
        setInstanceActive(instance, viewName);
    }

    public void setReSyncStudioActive(Instance instance, String serverTitle, String studioName) {
        setStudioActive(instance, serverTitle, studioName);
    }

    public void updateServerMetrics(Instance instance, int playerCount, long uptimeMs, double cpuPercent, long memoryBytes, long memoryLimitBytes) {
        updateMetrics(instance, playerCount, uptimeMs, cpuPercent, memoryBytes, memoryLimitBytes);
    }

    @Override
    protected Instance adjustRunningPresenceInstance(Instance playing, ContextKind kind, Instance viewed) {
        if (kind == ContextKind.INSTANCE && isRunningPresenceState(viewed)) {
            return viewed;
        }
        return playing;
    }

    @Override
    protected boolean allowRunningPresenceFor(Instance playing, ContextKind kind, Instance viewed) {
        if (playing == null || viewed == null || kind != ContextKind.INSTANCE) {
            return false;
        }
        String playingId = playing.getInstanceId();
        String viewedId = viewed.getInstanceId();
        return playing == viewed || playingId != null && viewedId != null && playingId.equals(viewedId);
    }

    private boolean isRunningPresenceState(Instance instance) {
        if (instance == null || instance.getState() == null) {
            return false;
        }
        return switch (instance.getState()) {
            case RUNNING, STARTING, INSTALLING, SAVING, SAVED -> true;
            default -> false;
        };
    }

    @Override
    protected Optional<Path> resolveInstanceIconPath(Instance instance) {
        if (instance == null) {
            return Optional.empty();
        }
        Optional<Path> resolved = iconManager.resolveIconPath(instance, false, null);
        if (resolved.isPresent()) {
            return resolved;
        }
        String key = instanceIconLoadKey(instance);
        if (!remoteIconLoadRequests.add(key)) {
            return Optional.empty();
        }
        return iconManager.resolveIconPath(instance, true, () -> {
            remoteIconLoadRequests.remove(key);
            requestPresenceUpdate();
        });
    }

    private String instanceIconLoadKey(Instance instance) {
        String id = instance.getInstanceId();
        if (id != null && !id.isBlank()) {
            return id;
        }
        return instance.getPath() == null ? String.valueOf(System.identityHashCode(instance)) : instance.getPath();
    }

    @Override
    protected int resolvePlayerCount(Instance instance) {
        try {
            return PlayerManagerController.getOrCreate(instance).getOnlinePlayerCount();
        } catch (Exception ignored) {
            return 0;
        }
    }

    @Override
    protected void initializePresenceTransport(String appId) {
        shutdownPresenceTransport();
        try (CreateParams params = new CreateParams()) {
            params.setClientID(Long.parseLong(appId));
            params.setFlags(CreateParams.getDefaultFlags());
            core = new Core(params);
            core.setLogHook(LogLevel.ERROR, (level, message) -> {
            });
        } catch (Exception e) {
            core = null;
            throw new IllegalStateException("Failed to initialize Discord Game SDK", e);
        }
    }

    @Override
    protected void updatePresenceTransport(PresenceSpec spec) {
        if (core == null) return;

        try (Activity activity = new Activity()) {
            activity.setDetails(emptyToBlank(spec.details()));
            activity.setState(emptyToBlank(spec.state()));
            if (spec.startTimestamp() != null && spec.startTimestamp() > 0) {
                activity.timestamps().setStart(Instant.ofEpochSecond(spec.startTimestamp()));
            }
            setAssetFields(activity, spec);
            core.activityManager().updateActivity(activity);
        }
    }

    @Override
    protected void clearPresenceTransport() {
        if (core != null) {
            core.activityManager().clearActivity();
        }
    }

    @Override
    protected void runPresenceCallbacks() {
        if (core != null) {
            core.runCallbacks();
        }
    }

    @Override
    protected void shutdownPresenceTransport() {
        if (core != null) {
            try {
                core.activityManager().clearActivity();
                core.close();
            } catch (Throwable ignored) {
            }
        }
        core = null;
    }

    private void setAssetFields(Activity activity, PresenceSpec spec) {
        String largeImage = emptyToBlank(spec.largeImageKey());
        if (!largeImage.isBlank()) {
            activity.assets().setLargeImage(largeImage);
        }
        String largeText = emptyToBlank(spec.largeImageText());
        if (!largeText.isBlank()) {
            activity.assets().setLargeText(largeText);
        }
        String smallImage = emptyToBlank(spec.smallImageKey());
        if (!smallImage.isBlank()) {
            activity.assets().setSmallImage(smallImage);
        }
        String smallText = emptyToBlank(spec.smallImageText());
        if (!smallText.isBlank()) {
            activity.assets().setSmallText(smallText);
        }
    }

    private String emptyToBlank(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.isEmpty() ? "" : t;
    }
}
