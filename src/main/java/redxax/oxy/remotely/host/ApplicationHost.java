package redxax.oxy.remotely.host;

import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.util.Identifier;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.FlowManagerUiAdapter;
import redxax.oxy.remotely.data.flow.ReSyncFlowClientConfiguration;
import redxax.oxy.remotely.data.flow.ReSyncFlowClientFactory;
import redxax.oxy.remotely.data.flow.ReSyncNotificationLevel;
import redxax.oxy.remotely.flow.ui.ReSyncProvisioningService;
import redxax.oxy.remotely.ui.server.ServerScreenHost;
import restudio.rebase.platform.Async;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;

import java.util.function.Consumer;

public interface ApplicationHost {
    void setScreen(Screen screen);
    Screen getCurrentScreen();
    void ensureTextRenderer();
    MinecraftGameAssets getGameAssets();
    Object getFontIdentifier(String namespace, String path);
    void openParentScreen(Screen currentScreen, Object parent);
    void setClipboard(String text);
    boolean shouldCloseRootScreen();

    default void execute(Runnable task) {
        if (task != null) task.run();
    }

    default void notify(String title, String message, ReSyncNotificationLevel level) {
    }

    default boolean authenticated() {
        return false;
    }

    default void signIn(Screen current) {
    }

    default void signOut(Screen current) {
    }

    default void openMarketplaceListing(String marketplaceSlug, String listingSlug, boolean control) {
    }

    default void openPermissionManager(Screen parent, Object client) {
        notify("Permissions", "Permission Management Unavailable", ReSyncNotificationLevel.WARN);
    }

    default void chooseImage(String title, Consumer<HostImage> callback) {
    }

    default Identifier registerRemoteImage(String source) {
        return null;
    }

    default void releaseRemoteImage(Identifier image) {
    }

    record HostImage(String fileName, String contentType, byte[] content) {
        public HostImage {
            fileName = fileName == null ? "image.bin" : fileName;
            contentType = contentType == null ? "application/octet-stream" : contentType;
            content = content == null ? new byte[0] : content.clone();
        }
    }

    default void openRemoteFiles(Object parent, Object target) {
    }

    default void openTerminal(Object parent, Object instance, RemotelyClient client) {
    }

    default void openFileExplorer(Object parent, Object path, RemotelyClient client) {
        openRemoteFiles(parent, path);
    }

    default void openInstanceFiles(Object parent, Object instance, RemotelyClient client) {
        openRemoteFiles(parent, instance);
    }

    default void openServerTwin(Object parent, Object instance, RemotelyClient client) {
    }

    default void openReSyncStudio(Object parent, Object instance, Object serverView, RemotelyClient client) {
    }

    default void shutdownLocalTerminals() {
    }

    default void openLocalFiles(Object parent, Object target) {
    }

    default ServerScreenHost serverScreenHost(RemotelyClient client) {
        return ServerScreenHost.of(this);
    }

    default boolean openExternal() {
        return false;
    }

    default void shutdownLocalProcesses() {
    }

    default boolean supportsDesktopIntegrations() {
        return true;
    }

    default void setManagerActivity() {
    }

    default void setLocalTerminalActivity() {
    }

    default void setServerActivity(Object instance, String view) {
    }

    default void setStudioActivity(Object instance, String title, String subtitle) {
    }

    default void updateServerActivityMetrics(Object instance, int onlinePlayers, int maxPlayers, int cpuPercent,
                                              int memoryUsedMb, int memoryMaxMb) {
    }

    default void updateServerActivityMetrics(Object instance, int onlinePlayers, int maxPlayers, long uptimeMs,
                                              int cpuPercent, int memoryUsedMb, int memoryMaxMb) {
        updateServerActivityMetrics(instance, onlinePlayers, maxPlayers, cpuPercent, memoryUsedMb, memoryMaxMb);
    }

    default void shutdownDesktopIntegrations() {
    }

    default boolean managesPrimaryScreen() {
        return true;
    }

    default FlowManagerUiAdapter flowManagerUiAdapter() {
        return FlowManagerUiAdapter.generic();
    }

    default ReSyncFlowClientConfiguration configureFlowClient(Object client, FlowManager manager,
                                                               ReSyncFlowClientFactory requestedFactory,
                                                               ReSyncFlowClientConfiguration defaults) {
        return defaults;
    }

    default Object provisioningAdapter() {
        return null;
    }

    default void prepareReSyncServerContext(String serverId, ClientServerView server, String loaderHint) {
    }

    default Async<ReSyncProvisioningService.StartupProbeResult> prepareReSyncServerContextAsync(
            String serverId, ClientServerView server, String loaderHint) {
        prepareReSyncServerContext(serverId, server, loaderHint);
        return Async.completed(new ReSyncProvisioningService.StartupProbeResult(
            ReSyncProvisioningService.StartupStatus.READY, false, false));
    }

    default void reportReSyncPreparationFailure(String message) {
        notify("ReSync", message == null || message.isBlank() ? "ReSync Unavailable" : message,
            ReSyncNotificationLevel.WARN);
    }


    String getGameVersion();
    String getGameUserName();
    String getGameUUID();
}
