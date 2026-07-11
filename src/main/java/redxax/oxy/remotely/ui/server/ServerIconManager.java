package redxax.oxy.remotely.ui.server;

import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class ServerIconManager {
    private final Path cacheDir;
    private final Set<String> remoteIconsLoaded = ConcurrentHashMap.newKeySet();
    private final Map<String, Identifier> iconIdCache = new ConcurrentHashMap<>();

    private final Map<String, Identifier> defaultIconIds = new HashMap<>();

    public ServerIconManager(Path cacheDir) {
        this.cacheDir = cacheDir.resolve("cache/icons");
        this.cacheDir.toFile().mkdirs();
    }

    public void setDefaultIcons(Map<String, Identifier> icons) {
        defaultIconIds.clear();
        defaultIconIds.putAll(icons);
    }

    public Identifier getIconId(Instance instance) {
        String instanceKey = getInstanceUniqueId(instance);
        Identifier cached = iconIdCache.get(instanceKey);
        if (cached != null) {
            return cached;
        }
        Identifier cachedIcon = loadFromCache(instance);
        if (cachedIcon != null) {
            return cachedIcon;
        }
        Identifier instanceIcon = loadFromInstance(instance);
        return instanceIcon != null ? instanceIcon : getDefaultIconId(instance);
    }

    public Identifier getQuickIconId(Instance instance) {
        if (instance == null) {
            return null;
        }
        String instanceKey = getInstanceUniqueId(instance);
        Identifier cached = iconIdCache.get(instanceKey);
        if (cached != null) {
            return cached;
        }
        return getDefaultIconId(instance);
    }

    public void loadIconIdAsync(Instance instance, Consumer<Identifier> onLoaded) {
        Identifier cached = iconIdCache.get(getInstanceUniqueId(instance));
        if (cached != null) {
            ScreenManager.getInstance().execute(() -> onLoaded.accept(cached));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Identifier cachedIcon = loadFromCache(instance);
                if (cachedIcon != null) {
                    ScreenManager.getInstance().execute(() -> onLoaded.accept(cachedIcon));
                    return;
                }

                Identifier instanceIcon = loadFromInstance(instance);
                if (instanceIcon != null) {
                    ScreenManager.getInstance().execute(() -> onLoaded.accept(instanceIcon));
                    return;
                }

                ScreenManager.getInstance().execute(() -> onLoaded.accept(getDefaultIconId(instance)));
            } catch (Exception e) {
                devPrint("Failed to load icon: " + e.getMessage());
                ScreenManager.getInstance().execute(() -> onLoaded.accept(getDefaultIconId(instance)));
            }
        });
    }

    public void loadRemoteIconAsync(Instance instance, Runnable onComplete) {
        CompletableFuture.runAsync(() -> {
            String instanceKey = getInstanceUniqueId(instance);
            boolean loaded = false;
            try {
                BackendConfig backendConfig = instance.getBackendConfig();
                if (backendConfig == null || "LOCAL".equalsIgnoreCase(backendConfig.type)) {
                    return;
                }

                if (!remoteIconsLoaded.add(instanceKey)) {
                    return;
                }

                Path tempDir = Files.createTempDirectory("icon_load");
                try {
                    for (String candidate : List.of("icon.png", "server-icon.png")) {
                        Path candidatePath = tempDir.resolve(candidate);
                        try {
                            RebaseApiFactory.get(instance)
                                .download(List.of(Path.of(instance.getPath(), candidate)), tempDir)
                                .join();
                            if (!Files.exists(candidatePath)) {
                                continue;
                            }
                            BufferedImage icon = ImageIO.read(candidatePath.toFile());
                            if (icon != null) {
                                saveToCache(instance, icon);
                                loaded = true;
                                if (onComplete != null) {
                                    ScreenManager.getInstance().execute(onComplete);
                                }
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } finally {
                    deleteDirectoryQuietly(tempDir);
                }
            } catch (Exception ignored) {
            } finally {
                if (!loaded) {
                    remoteIconsLoaded.remove(instanceKey);
                }
            }
        });
    }

    public Optional<Path> resolveIconPath(Instance instance, boolean loadRemote, Runnable onLoaded) {
        if (instance == null) {
            return Optional.empty();
        }

        Optional<Path> cached = resolveCachedIconPath(instance);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<Path> local = resolveLocalIconPath(instance);
        if (local.isPresent()) {
            return local;
        }

        BackendConfig backendConfig = instance.getBackendConfig();
        if (loadRemote && backendConfig != null && !"LOCAL".equalsIgnoreCase(backendConfig.type)) {
            loadRemoteIconAsync(instance, onLoaded);
        }
        return Optional.empty();
    }

    public void customizeIcon(Instance instance, RemoteHost remoteHost, Identifier iconId, Runnable onComplete) {
        BufferedImage icon = ResourceManager.getInstance().resolveImage(iconId);
        if (icon == null) {
            showErrorNotification("Error", "Failed to resolve icon.");
            return;
        }
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
                uploadToRemote(instance, icon).thenAccept(success -> ScreenManager.getInstance().execute(() -> {
                    if (success) {
                        showSuccessNotification("Icon Updated", "Custom icon set.");
                    } else {
                        showErrorNotification("Upload Failed", "Failed to upload icon to remote server.");
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                })).exceptionally(e -> {
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
            String instanceKey = getInstanceUniqueId(instance);
            releaseImageId(iconIdCache.remove(instanceKey));
            remoteIconsLoaded.remove(getInstanceUniqueId(instance));
        } catch (Exception e) {
            devPrint("Failed to clear cache: " + e.getMessage());
        }
    }

    public void clearAllRemoteTracking() {
        remoteIconsLoaded.clear();
    }

    public List<Identifier> loadIconAssetIds() {
        List<Identifier> images = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            try {
                images.add(Identifier.icon("ic_" + i + ".png"));
            } catch (Exception ignored) {}
        }
        return images;
    }

    private File getCachePath(Instance instance) {
        return new File(cacheDir.toFile(), getInstanceUniqueId(instance) + ".png");
    }

    private Optional<Path> resolveCachedIconPath(Instance instance) {
        try {
            File cacheFile = getCachePath(instance);
            if (cacheFile.exists() && cacheFile.isFile()) {
                return Optional.of(cacheFile.toPath());
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private Optional<Path> resolveLocalIconPath(Instance instance) {
        try {
            BackendConfig backendConfig = instance.getBackendConfig();
            if (backendConfig != null && !"LOCAL".equalsIgnoreCase(backendConfig.type)) {
                return Optional.empty();
            }
            for (String candidate : List.of("icon.png", "server-icon.png")) {
                Path path = Path.of(instance.getPath(), candidate);
                if (Files.isRegularFile(path) && Files.size(path) > 0) {
                    return Optional.of(path);
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private String getInstanceUniqueId(Instance instance) {
        BackendConfig config = instance.getBackendConfig();
        String backendType = config == null || config.type == null ? "local" : config.type.toLowerCase(Locale.ROOT);
        Map<String, String> credentials = config != null && config.credentials != null ? config.credentials : Collections.emptyMap();
        String identity = switch (backendType) {
            case "restudio" -> credentials.getOrDefault("identifier", "unknown");
            case "ptero" -> credentials.getOrDefault("hostId", credentials.getOrDefault("host", "unknown")) + "|" + credentials.getOrDefault("identifier", "unknown");
            case "local" -> "local";
            default -> credentials.getOrDefault("host", credentials.getOrDefault("hostId", "unknown"));
        };
        String path = instance.getPath() == null ? "" : instance.getPath();
        return backendType + "_" + stableHash(identity + "|" + path);
    }

    private String stableHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(12, bytes.length); i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private Identifier loadFromCache(Instance instance) {
        try {
            File cacheFile = getCachePath(instance);
            if (cacheFile.exists()) {
                BufferedImage icon = ImageIO.read(cacheFile);
                if (icon != null) {
                    return cacheIcon(instance, icon);
                }
            }
        } catch (IOException e) {
            devPrint("Failed to load from cache: " + e.getMessage());
        }
        return null;
    }

    private Identifier loadFromInstance(Instance instance) {
        try {
            BackendConfig backendConfig = instance.getBackendConfig();
            if (backendConfig == null || "LOCAL".equalsIgnoreCase(backendConfig.type)) {
                File iconFile = new File(instance.getPath(), "icon.png");
                if (iconFile.exists() && iconFile.isFile()) {
                    BufferedImage icon = ImageIO.read(iconFile);
                    if (icon != null) {
                        return cacheIcon(instance, icon);
                    }
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
        cacheIcon(instance, icon);
    }

    private Identifier getDefaultIconId(Instance instance) {
        String software = instance.getServerSoftwareType();
        String key = software == null || software.isBlank() ? instance.getModLoader().name().toLowerCase(Locale.ROOT) : software.toLowerCase(Locale.ROOT);
        Identifier configured = defaultIconIds.get(key);
        if (configured != null) return configured;
        if (Set.of("vanilla", "fabric", "forge", "neoforge", "paper", "purpur", "quilt", "spigot", "bukkit", "leaf", "velocity", "waterfall").contains(key)) {
            return Identifier.icon(key + ".png");
        }
        return defaultIconIds.getOrDefault("unknown", Identifier.icon("unknown.png"));
    }

    private Identifier cacheIcon(Instance instance, BufferedImage icon) {
        String instanceKey = getInstanceUniqueId(instance);
        releaseImageId(iconIdCache.remove(instanceKey));
        Identifier id = ResourceManager.getInstance().registerImage(Identifier.generatedImage("remotely", "server-icons/instance/" + safeIconKey(instanceKey)), icon);
        iconIdCache.put(instanceKey, id);
        return id;
    }

    private String safeIconKey(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void releaseImageId(Identifier id) {
        if (id != null) {
            ResourceManager.getInstance().releaseImage(id);
        }
    }

    private CompletableFuture<Boolean> uploadToRemote(Instance instance, BufferedImage icon) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path tempDir = Files.createTempDirectory("icon_upload");
                Path tempFile = tempDir.resolve("icon.png");
                Path serverIconFile = tempDir.resolve("server-icon.png");
                try {
                    ImageIO.write(icon, "png", tempFile.toFile());
                    Files.copy(tempFile, serverIconFile, StandardCopyOption.REPLACE_EXISTING);

                    RebaseApiFactory.get(instance)
                        .upload(List.of(tempFile, serverIconFile), Path.of(instance.getPath()))
                        .join();

                    return true;
                } finally {
                    deleteDirectoryQuietly(tempDir);
                }
            } catch (Exception e) {
                devPrint("Failed to upload icon: " + e.getMessage());
                return false;
            }
        });
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void showSuccessNotification(String title, String message) {
        ScreenManager.getInstance().execute(() -> new Notification(title, message, Notification.Type.SUCCESS));
    }

    private void showErrorNotification(String title, String message) {
        ScreenManager.getInstance().execute(() -> new Notification(title, message, Notification.Type.ERROR));
    }
}
