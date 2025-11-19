package redxax.oxy.remotely.data.integrations.luckperms;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import redxax.oxy.remotely.data.integrations.luckperms.LuckPermsDTOs.*;
import restudio.rebase.api.RebaseAPI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class LuckPermsService {
    private final RebaseAPI api;
    private final Path configPath;
    private LuckPermsApiClient client;
    private final Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private boolean enabled = false;
    private Config config;
    private final Map<UUID, User> userCache = new ConcurrentHashMap<>();
    private final Map<String, Group> groupCache = new ConcurrentHashMap<>();

    public static class Config {
        public boolean enabled = false;
        public String apiUrl = "http://localhost:8080";
        public String apiKey = "";
    }

    public LuckPermsService(RebaseAPI api, Path instancePath) {
        this.api = api;
        this.configPath = instancePath.resolve("Remotely").resolve("luckperms.json");
    }

    public CompletableFuture<Void> initialize() {
        return api.fileExists(configPath).thenCompose(exists -> {
            if (exists) {
                return api.readFile(configPath).thenAccept(content -> {
                    try {
                        this.config = gson.fromJson(content, Config.class);
                        if (this.config == null) this.config = new Config();
                        updateClient();
                    } catch (Exception e) {
                        this.config = new Config();
                    }
                });
            } else {
                this.config = new Config();
                return saveConfig();
            }
        });
    }

    public void updateConfig(Config newConfig) {
        this.config = newConfig;
        updateClient();
        saveConfig();
    }

    public Config getConfig() {
        return config;
    }

    public boolean isEnabled() {
        return enabled && client != null;
    }

    public LuckPermsApiClient getClient() {
        return client;
    }

    private void updateClient() {
        this.enabled = config.enabled && config.apiUrl != null && !config.apiUrl.isEmpty();
        if (enabled) {
            this.client = new LuckPermsApiClient(config.apiUrl, config.apiKey);
            clearCache();
        } else {
            this.client = null;
        }
    }

    private CompletableFuture<Void> saveConfig() {
        return api.writeFile(configPath, gson.toJson(config));
    }

    public void clearCache() {
        userCache.clear();
        groupCache.clear();
    }

    public CompletableFuture<User> getUser(UUID uuid) {
        if (!isEnabled()) return CompletableFuture.completedFuture(null);
        if (userCache.containsKey(uuid)) return CompletableFuture.completedFuture(userCache.get(uuid));
        return client.getUser(uuid).thenApply(user -> {
            if (user != null) userCache.put(uuid, user);
            return user;
        }).exceptionally(e -> null);
    }

    public CompletableFuture<Group> getGroup(String name) {
        if (!isEnabled()) return CompletableFuture.completedFuture(null);
        if (groupCache.containsKey(name)) return CompletableFuture.completedFuture(groupCache.get(name));
        return client.getGroup(name).thenApply(group -> {
            if (group != null) groupCache.put(name, group);
            return group;
        });
    }

    public void invalidateUser(UUID uuid) {
        userCache.remove(uuid);
    }

    public void invalidateGroup(String name) {
        groupCache.remove(name);
    }

    public CompletableFuture<Metadata> getUserMetadata(UUID uuid) {
        return getUser(uuid).thenApply(user -> user != null ? user.metadata : null);
    }

    public CompletableFuture<List<String>> getAllUsers() {
        if (!isEnabled()) return CompletableFuture.completedFuture(List.of());
        return client.getUserList();
    }

    public CompletableFuture<List<String>> getAllGroups() {
        if (!isEnabled()) return CompletableFuture.completedFuture(List.of());
        return client.getGroupList();
    }

    public CompletableFuture<List<String>> getAllTracks() {
        if (!isEnabled()) return CompletableFuture.completedFuture(List.of());
        return client.getTrackList();
    }

    public Integer getCachedGroupWeight(String name) {
        Group g = groupCache.get(name);
        if (g != null && g.weight != null) return g.weight;
        return 0;
    }
}
