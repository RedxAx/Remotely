package redxax.oxy.remotely.ui.server;

import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class ServerIconManager {
    private final Path cacheDir;
    private final Set<String> remoteIconsLoaded = ConcurrentHashMap.newKeySet();

    private final Map<String, BufferedImage> defaultIcons = new HashMap<>();

    public ServerIconManager(Path cacheDir) {
        this.cacheDir = cacheDir.resolve("cache/icons");
        this.cacheDir.toFile().mkdirs();
    }

    public void setDefaultIcons(Map<String, BufferedImage> icons) {
        defaultIcons.putAll(icons);
    }

    public BufferedImage getIcon(Instance instance) {
        return getDefaultIcon(instance);
    }

    public void loadIconAsync(Instance instance, Consumer<BufferedImage> onLoaded) {
        CompletableFuture.runAsync(() -> {
            try {
                BufferedImage cachedIcon = loadFromCache(instance);
                if (cachedIcon != null) {
                    ScreenManager.getInstance().execute(() -> onLoaded.accept(cachedIcon));
                    return;
                }

                BufferedImage instanceIcon = loadFromInstance(instance);
                if (instanceIcon != null) {
                    ScreenManager.getInstance().execute(() -> onLoaded.accept(instanceIcon));
                    return;
                }

                ScreenManager.getInstance().execute(() -> onLoaded.accept(getDefaultIcon(instance)));
            } catch (Exception e) {
                devPrint("Failed to load icon: " + e.getMessage());
                ScreenManager.getInstance().execute(() -> onLoaded.accept(getDefaultIcon(instance)));
            }
        });
    }

    public void loadRemoteIconAsync(Instance instance, Runnable onComplete) {
        CompletableFuture.runAsync(() -> {
            try {
                BackendConfig backendConfig = instance.getBackendConfig();
                if (backendConfig == null || "LOCAL".equalsIgnoreCase(backendConfig.type)) {
                    return;
                }

                if (!remoteIconsLoaded.add(getInstanceUniqueId(instance))) {
                    return;
                }

                File tempDir = Files.createTempDirectory("icon_load").toFile();
                try {
                    Path iconPath = tempDir.toPath().resolve("icon.png");

                    RebaseApiFactory.get(instance)
                        .download(List.of(Path.of(instance.getPath(), "icon.png")), tempDir.toPath())
                        .join();

                    if (Files.exists(iconPath)) {
                        BufferedImage icon = ImageIO.read(iconPath.toFile());
                        saveToCache(instance, icon);

                        if (onComplete != null) {
                            ScreenManager.getInstance().execute(onComplete);
                        }
                    }
                } finally {
                    tempDir.delete();
                }
            } catch (Exception e) {
                devPrint("Failed to load remote icon: " + e.getMessage());
            }
        });
    }

    public void customizeIcon(Instance instance, RemoteHost remoteHost, BufferedImage icon, Runnable onComplete) {
        try {
            saveToCache(instance, icon);
            remoteIconsLoaded.remove(getInstanceUniqueId(instance));

            BackendConfig backendConfig = instance.getBackendConfig();
            if (backendConfig == null || "LOCAL".equalsIgnoreCase(backendConfig.type)) {
                File iconFile = new File(instance.getPath(), "icon.png");
                iconFile.getParentFile().mkdirs();
                ImageIO.write(icon, "png", iconFile);
                showSuccessNotification("Icon Updated", "Custom icon set.");
                if (onComplete != null) {
                    onComplete.run();
                }
            } else {
                uploadToRemote(instance, icon).thenAccept(success -> {
                    ScreenManager.getInstance().execute(() -> {
                        if (success) {
                            showSuccessNotification("Icon Updated", "Custom icon set.");
                        } else {
                            showErrorNotification("Upload Failed", "Failed to upload icon to remote server.");
                        }
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    });
                }).exceptionally(e -> {
                    ScreenManager.getInstance().execute(() -> showErrorNotification("Upload Error", e.getMessage()));
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    return null;
                });
            }
        } catch (IOException e) {
            showErrorNotification("Error", "Failed to save icon: " + e.getMessage());
        }
    }

    public void clearCache(Instance instance) {
        try {
            File cacheFile = getCachePath(instance);
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            remoteIconsLoaded.remove(getInstanceUniqueId(instance));
        } catch (Exception e) {
            devPrint("Failed to clear cache: " + e.getMessage());
        }
    }

    public void clearAllRemoteTracking() {
        remoteIconsLoaded.clear();
    }

    public List<BufferedImage> loadIconAssets() {
        List<BufferedImage> images = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            try {
                images.add(restudio.rescreen.util.ImageUtils.loadIcon("ic_" + i + ".png"));
            } catch (Exception ignored) {}
        }
        return images;
    }

    private File getCachePath(Instance instance) {
        return new File(cacheDir.toFile(), getInstanceUniqueId(instance) + ".png");
    }

    private String getInstanceUniqueId(Instance instance) {
        BackendConfig config = instance.getBackendConfig();
        if (config == null || "LOCAL".equalsIgnoreCase(config.type)) {
            return "local_" + instance.getInstanceId();
        } else if ("RESTUDIO".equalsIgnoreCase(config.type)) {
            return "restudio_" + config.credentials.getOrDefault("identifier", "unknown");
        } else {
            String host = config.credentials.getOrDefault("host", "unknown").replace(":", "_").replace("/", "_");
            return "ssh_" + host + "_" + instance.getInstanceId();
        }
    }

    private BufferedImage loadFromCache(Instance instance) {
        try {
            File cacheFile = getCachePath(instance);
            if (cacheFile.exists()) {
                return ImageIO.read(cacheFile);
            }
        } catch (IOException e) {
            devPrint("Failed to load from cache: " + e.getMessage());
        }
        return null;
    }

    private BufferedImage loadFromInstance(Instance instance) {
        try {
            BackendConfig backendConfig = instance.getBackendConfig();
            if (backendConfig == null || "LOCAL".equalsIgnoreCase(backendConfig.type)) {
                File iconFile = new File(instance.getPath(), "icon.png");
                if (iconFile.exists() && iconFile.isFile()) {
                    return ImageIO.read(iconFile);
                }
            }
        } catch (Exception e) {
            devPrint("Failed to load from instance: " + e.getMessage());
        }
        return null;
    }

    private void saveToCache(Instance instance, BufferedImage icon) throws IOException {
        File cacheFile = getCachePath(instance);
        cacheFile.getParentFile().mkdirs();
        ImageIO.write(icon, "png", cacheFile);
    }

    private BufferedImage getDefaultIcon(Instance instance) {
        String key = instance.getModLoader().name().toLowerCase(Locale.ROOT);
        return defaultIcons.getOrDefault(key, defaultIcons.get("unknown"));
    }

    private CompletableFuture<Boolean> uploadToRemote(Instance instance, BufferedImage icon) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File tempFile = File.createTempFile("icon_", ".png");
                try {
                    ImageIO.write(icon, "png", tempFile);

                    RebaseApiFactory.get(instance)
                        .upload(List.of(tempFile.toPath()), Path.of(instance.getPath(), "icon.png"))
                        .join();

                    return true;
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            } catch (Exception e) {
                devPrint("Failed to upload icon: " + e.getMessage());
                return false;
            }
        });
    }

    private void showSuccessNotification(String title, String message) {
        ScreenManager.getInstance().execute(() -> new Notification(title, message, Notification.Type.SUCCESS));
    }

    private void showErrorNotification(String title, String message) {
        ScreenManager.getInstance().execute(() -> new Notification(title, message, Notification.Type.ERROR));
    }
}
