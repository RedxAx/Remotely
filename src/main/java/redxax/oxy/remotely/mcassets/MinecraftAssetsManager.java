package redxax.oxy.remotely.mcassets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rebase.util.Executors;
import restudio.rebase.util.FileTransferProgress;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.util.FileUtils;
import restudio.rescreen.util.HttpUtils;
import restudio.rescreen.util.Notification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MinecraftAssetsManager {
    private static final String SETTINGS_TAB = "Minecraft Assets";
    private static final String NOTIFICATION_TITLE = "Minecraft Assets";
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static volatile MinecraftAssetsManager instance;

    private final RemotelyConfigManager configManager;
    private final ManagedMinecraftAssetSource assetSource;
    private final AtomicLong revision = new AtomicLong(1L);

    private volatile ProvisionState state = ProvisionState.IDLE;
    private volatile String detail = "";
    private volatile Notification notification;
    private volatile PopupWidget popup;
    private volatile boolean startupProvisionChecked;

    private MinecraftAssetsManager(RemotelyConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.assetSource = new ManagedMinecraftAssetSource(this);
        syncStateFromDisk();
    }

    public static MinecraftAssetsManager get(RemotelyConfigManager configManager) {
        MinecraftAssetsManager current = instance;
        if (current != null) {
            return current;
        }
        synchronized (MinecraftAssetsManager.class) {
            if (instance == null) {
                instance = new MinecraftAssetsManager(configManager);
            }
            return instance;
        }
    }

    public static void notifyConfigurationChanged() {
        MinecraftAssetsManager current = instance;
        if (current != null) {
            current.onConfigurationChanged();
        }
    }

    public ManagedMinecraftAssetSource getAssetSource() {
        return assetSource;
    }

    public long getRevision() {
        return revision.get();
    }

    public Path getBaseDir() {
        return configManager.getApplicationDir().resolve("data").resolve("mcassets");
    }

    public Path getActiveAssetsDir() {
        if (!configManager.isMinecraftAssetsEnabled()) {
            return null;
        }
        String activeVersion = getActiveVersion();
        if (activeVersion == null || activeVersion.isBlank()) {
            return null;
        }
        Path assetsDir = getBaseDir().resolve(activeVersion).resolve("assets");
        return Files.isDirectory(assetsDir) ? assetsDir : null;
    }

    public String getActiveVersion() {
        String resolvedVersion = normalizeVersion(configManager.getMinecraftAssetsResolvedVersion());
        if (resolvedVersion != null && Files.isDirectory(getBaseDir().resolve(resolvedVersion).resolve("assets"))) {
            return resolvedVersion;
        }
        String versionTarget = getVersionTarget();
        if (isExplicitVersion(versionTarget) && Files.isDirectory(getBaseDir().resolve(versionTarget).resolve("assets"))) {
            return versionTarget;
        }
        return findLatestInstalledVersion();
    }

    public String getVersionTarget() {
        String value = normalizeVersion(configManager.getMinecraftAssetsVersionTarget());
        return value != null ? value : "latest-release";
    }

    public ProvisionState getState() {
        return state;
    }

    public String getStatusBadge() {
        if (!configManager.isMinecraftAssetsEnabled()) {
            return "Off";
        }
        if (getActiveAssetsDir() != null) {
            return "Ready";
        }
        return switch (state) {
            case INSTALLING -> "Downloading";
            case WAITING_CONSENT -> "Setup";
            case FAILED -> "Error";
            case DECLINED -> "Later";
            case READY -> "Ready";
            default -> configManager.isMinecraftAssetsManagedEnabled() ? "Missing" : "Disabled";
        };
    }

    public String getStatusDescription() {
        if (state == ProvisionState.INSTALLING && detail != null && !detail.isBlank()) {
            return detail;
        }
        if (state == ProvisionState.WAITING_CONSENT && detail != null && !detail.isBlank()) {
            return detail;
        }
        if (state == ProvisionState.FAILED && detail != null && !detail.isBlank()) {
            return detail;
        }
        if (state == ProvisionState.DECLINED && detail != null && !detail.isBlank()) {
            return detail;
        }
        if (!configManager.isMinecraftAssetsEnabled()) {
            return "Standalone item rendering is disabled.";
        }
        String activeVersion = getActiveVersion();
        if (activeVersion != null) {
            return "Active " + activeVersion + " in data/mcassets/" + activeVersion + "/assets";
        }
        if (!configManager.isMinecraftAssetsManagedEnabled()) {
            return "Managed downloads are disabled. Add assets manually in data/mcassets/<version>/assets.";
        }
        return "Target " + getVersionTarget() + " is missing. Download managed assets for item rendering.";
    }

    public void requestStartupProvision(ReScreen screen) {
        if (startupProvisionChecked) {
            return;
        }
        startupProvisionChecked = true;
        if (!configManager.isMinecraftAssetsEnabled() || !configManager.isMinecraftAssetsManagedEnabled() || !configManager.isMinecraftAssetsAutoDownloadEnabled()) {
            syncStateFromDisk();
            return;
        }
        if (getActiveAssetsDir() != null) {
            syncStateFromDisk();
            return;
        }
        if (!configManager.isMinecraftAssetsAskBeforeDownload()) {
            requestProvision(screen, false, false);
            return;
        }
        syncStateFromDisk();
    }

    public void requestProvision(ReScreen screen, boolean forceInstall, boolean forcePrompt) {
        if (!configManager.isMinecraftAssetsEnabled()) {
            state = ProvisionState.IDLE;
            detail = "Minecraft assets are disabled.";
            refreshSettingsTab();
            return;
        }
        if (!forceInstall && getActiveAssetsDir() != null) {
            state = ProvisionState.READY;
            detail = getStatusDescription();
            refreshSettingsTab();
            return;
        }
        if (!configManager.isMinecraftAssetsManagedEnabled()) {
            state = ProvisionState.IDLE;
            detail = "Managed downloads are disabled.";
            refreshSettingsTab();
            return;
        }
        if (state == ProvisionState.INSTALLING) {
            return;
        }
        if (!forcePrompt && state == ProvisionState.DECLINED) {
            return;
        }
        if (configManager.isMinecraftAssetsAskBeforeDownload()) {
            if (popup != null && popup.isVisible()) {
                if (forcePrompt) {
                    popup.show();
                }
                return;
            }
            if (screen == null) {
                return;
            }
            state = ProvisionState.WAITING_CONSENT;
            detail = "Download " + getVersionTarget() + " assets into data/mcassets";
            refreshSettingsTab();
            popup = buildConsentPopup(screen, forceInstall);
            screen.addDrawableChild(popup);
            popup.show();
            return;
        }
        startInstall(forceInstall);
    }

    public void openAssetsDir() {
        try {
            Files.createDirectories(getBaseDir());
            FileUtils.openExplorer(getBaseDir().toString());
        } catch (IOException exception) {
            new Notification(NOTIFICATION_TITLE, exception.getMessage(), Notification.Type.ERROR);
        }
    }

    public void removeManagedAssets() {
        if (state == ProvisionState.INSTALLING) {
            new Notification(NOTIFICATION_TITLE, "Wait for the current download to finish.", Notification.Type.WARN);
            return;
        }
        deleteQuietly(getBaseDir());
        configManager.setMinecraftAssetsResolvedVersion("");
        state = ProvisionState.IDLE;
        detail = "Managed assets removed";
        bumpRevision();
        refreshSettingsTab();
        new Notification(NOTIFICATION_TITLE, "Managed assets removed", Notification.Type.INFO);
    }

    private void onConfigurationChanged() {
        syncStateFromDisk();
        bumpRevision();
        refreshSettingsTab();
    }

    private void syncStateFromDisk() {
        if (!configManager.isMinecraftAssetsEnabled()) {
            state = ProvisionState.IDLE;
            detail = "Minecraft assets are disabled.";
            return;
        }
        if (getActiveAssetsDir() != null) {
            state = ProvisionState.READY;
            detail = getStatusDescription();
            return;
        }
        if (state == ProvisionState.INSTALLING || state == ProvisionState.WAITING_CONSENT || state == ProvisionState.FAILED || state == ProvisionState.DECLINED) {
            return;
        }
        state = ProvisionState.IDLE;
        detail = getStatusDescription();
    }

    private PopupWidget buildConsentPopup(ReScreen screen, boolean forceInstall) {
        AnimatedButton download = new AnimatedButton.Builder()
                .label("Download")
                .accentType(restudio.rescreen.theme.ThemeManager.getAccent("nice"))
                .onClick(() -> {
                    if (popup != null) {
                        popup.hide();
                    }
                    startInstall(forceInstall);
                })
                .build();
        AnimatedButton later = new AnimatedButton.Builder()
                .label("Later")
                .accentType(restudio.rescreen.theme.ThemeManager.getDefaultAccent())
                .onClick(() -> {
                    state = ProvisionState.DECLINED;
                    detail = "Download skipped";
                    if (popup != null) {
                        popup.hide();
                    }
                    refreshSettingsTab();
                })
                .build();
        return new PopupWidget.Builder("Minecraft Assets")
                .size(360, 156)
                .addMarkdown("", buildConsentMarkdown(), 44)
                .addRow("", false, 18, download, later)
                .onClose(() -> {
                    if (state == ProvisionState.WAITING_CONSENT) {
                        state = ProvisionState.DECLINED;
                        detail = "Download skipped";
                        refreshSettingsTab();
                    }
                })
                .build();
    }

    private String buildConsentMarkdown() {
        return "Download **" + getVersionTarget() + "** assets for standalone item rendering.\n\nStored in **data/mcassets/<version>/assets**.";
    }

    private void startInstall(boolean forceInstall) {
        if (!forceInstall && getActiveAssetsDir() != null) {
            state = ProvisionState.READY;
            detail = getStatusDescription();
            refreshSettingsTab();
            return;
        }
        state = ProvisionState.INSTALLING;
        detail = "Preparing " + getVersionTarget();
        refreshSettingsTab();
        updateNotification(NOTIFICATION_TITLE, detail, Notification.Type.INFO, true, false);
        CompletableFuture.runAsync(this::installAssets, Executors.get()).whenComplete((ignored, error) ->
                ScreenManager.getInstance().execute(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        state = ProvisionState.FAILED;
                        detail = resolveErrorMessage(cause);
                        updateNotification(NOTIFICATION_TITLE, detail, Notification.Type.ERROR, false, true);
                        refreshSettingsTab();
                        return;
                    }
                    state = ProvisionState.READY;
                    String activeVersion = getActiveVersion();
                    detail = activeVersion == null ? "Minecraft assets ready" : activeVersion + " ready";
                    updateNotification(NOTIFICATION_TITLE, detail, Notification.Type.SUCCESS, false, true);
                    refreshSettingsTab();
                }));
    }

    private void installAssets() {
        ResolvedVersion resolvedVersion = resolveVersion(getVersionTarget());
        Path archive = getBaseDir().resolve("downloads").resolve("minecraft-" + resolvedVersion.id() + "-client.jar");
        Path tempDir = getBaseDir().resolve("tmp").resolve(resolvedVersion.id());
        try {
            Files.createDirectories(getBaseDir());
            deleteIfExists(tempDir);
            downloadWithProgress(resolvedVersion.clientUrl(), archive, "Downloading " + resolvedVersion.id());
            updateInstallDetail("Extracting assets for " + resolvedVersion.id());
            extractAssets(archive, tempDir);
            Path extractedAssets = tempDir.resolve("assets");
            if (!Files.isDirectory(extractedAssets)) {
                throw new IOException("Client jar did not contain assets");
            }
            Path targetAssetsDir = getBaseDir().resolve(resolvedVersion.id()).resolve("assets");
            replaceDirectory(extractedAssets, targetAssetsDir);
            deleteQuietly(tempDir);
            deleteQuietly(archive);
            configManager.setMinecraftAssetsResolvedVersion(resolvedVersion.id());
            bumpRevision();
        } catch (Exception exception) {
            deleteQuietly(tempDir);
            throw new RuntimeException("Failed to install Minecraft assets", exception);
        }
    }

    private ResolvedVersion resolveVersion(String versionTarget) {
        JsonObject manifest = JsonParser.parseString(HttpUtils.get(MANIFEST_URL).join()).getAsJsonObject();
        String versionId = resolveVersionId(manifest, versionTarget);
        JsonObject versionEntry = findVersionEntry(manifest, versionId);
        String metadataUrl = getRequiredString(versionEntry, "url");
        JsonObject metadata = JsonParser.parseString(HttpUtils.get(metadataUrl).join()).getAsJsonObject();
        JsonObject downloads = getRequiredObject(metadata, "downloads");
        JsonObject client = getRequiredObject(downloads, "client");
        return new ResolvedVersion(versionId, getRequiredString(client, "url"));
    }

    private String resolveVersionId(JsonObject manifest, String versionTarget) {
        String normalized = normalizeVersion(versionTarget);
        if (normalized == null || normalized.equals("latest") || normalized.equals("latest-release")) {
            return getRequiredString(getRequiredObject(manifest, "latest"), "release");
        }
        if (normalized.equals("latest-snapshot")) {
            return getRequiredString(getRequiredObject(manifest, "latest"), "snapshot");
        }
        findVersionEntry(manifest, normalized);
        return normalized;
    }

    private JsonObject findVersionEntry(JsonObject manifest, String versionId) {
        JsonArray versions = getRequiredArray(manifest, "versions");
        for (JsonElement element : versions) {
            JsonObject object = element.getAsJsonObject();
            if (versionId.equals(getRequiredString(object, "id"))) {
                return object;
            }
        }
        throw new IllegalStateException("Unknown Minecraft version: " + versionId);
    }

    private void downloadWithProgress(String url, Path target, String label) {
        FileTransferProgress progress = new FileTransferProgress(0L);
        HttpUtils.downloadFile(url, target, (transferred, total) -> {
            progress.update(transferred, total);
            detail = progress.formatProgress();
            ScreenManager.getInstance().execute(() -> updateNotification(NOTIFICATION_TITLE, label + " • " + progress.formatProgress(), Notification.Type.INFO, true, false));
        }).join();
    }

    private void updateInstallDetail(String message) {
        detail = message;
        ScreenManager.getInstance().execute(() -> updateNotification(NOTIFICATION_TITLE, message, Notification.Type.INFO, true, false));
    }

    private void updateNotification(String message, String description, Notification.Type type, boolean loading, boolean autoSlideOut) {
        if (notification == null) {
            notification = new Notification.Builder()
                    .message(message)
                    .description(description)
                    .type(type)
                    .loading(loading)
                    .autoSlideOut(autoSlideOut)
                    .build();
            return;
        }
        notification.update()
                .message(message)
                .description(description)
                .type(type)
                .loading(loading)
                .autoSlideOut(autoSlideOut)
                .commit();
    }

    private void extractAssets(Path archive, Path destination) throws IOException {
        deleteIfExists(destination);
        Files.createDirectories(destination);
        try (InputStream inputStream = Files.newInputStream(archive); ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            int extractedCount = 0;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.startsWith("assets/")) {
                    zipInputStream.closeEntry();
                    continue;
                }
                Path target = destination.resolve(name).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("Unsafe zip entry: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zipInputStream, target, StandardCopyOption.REPLACE_EXISTING);
                    extractedCount++;
                    if (extractedCount % 250 == 0) {
                        updateInstallDetail("Extracting assets • " + extractedCount + " files");
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private void replaceDirectory(Path source, Path target) throws IOException {
        deleteIfExists(target);
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteIfExists(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private void bumpRevision() {
        revision.incrementAndGet();
    }

    private void refreshSettingsTab() {
        ScreenManager.getInstance().execute(() -> {
            Screen current = ScreenManager.getInstance().getCurrentScreen();
            if (current instanceof SettingsScreen settingsScreen) {
                settingsScreen.refreshTab(SETTINGS_TAB);
            }
        });
    }

    private String findLatestInstalledVersion() {
        Path root = getBaseDir();
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (var stream = Files.list(root)) {
            List<Path> candidates = stream
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return !fileName.equals("downloads") && !fileName.equals("tmp") && Files.isDirectory(path.resolve("assets"));
                    })
                    .sorted(Comparator.comparingLong(this::lastModified).reversed())
                    .toList();
            return candidates.isEmpty() ? null : candidates.getFirst().getFileName().toString();
        } catch (IOException exception) {
            return null;
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    private boolean isExplicitVersion(String versionTarget) {
        if (versionTarget == null || versionTarget.isBlank()) {
            return false;
        }
        return !versionTarget.equals("latest") && !versionTarget.equals("latest-release") && !versionTarget.equals("latest-snapshot");
    }

    private String normalizeVersion(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private JsonObject getRequiredObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalStateException("Missing object: " + key);
        }
        return element.getAsJsonObject();
    }

    private JsonArray getRequiredArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalStateException("Missing array: " + key);
        }
        return element.getAsJsonArray();
    }

    private String getRequiredString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            throw new IllegalStateException("Missing value: " + key);
        }
        return element.getAsString();
    }

    private String resolveErrorMessage(Throwable throwable) {
        List<String> parts = new ArrayList<>();
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank() && !parts.contains(message)) {
                parts.add(message);
            }
            current = current.getCause();
        }
        return parts.isEmpty() ? throwable.getClass().getSimpleName() : parts.getFirst();
    }

    public enum ProvisionState {
        IDLE,
        WAITING_CONSENT,
        INSTALLING,
        READY,
        FAILED,
        DECLINED
    }

    private record ResolvedVersion(String id, String clientUrl) {
    }
}
