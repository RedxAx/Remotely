package redxax.oxy.remotely.data.integrations.luckperms;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.data.integrations.luckperms.LuckPermsDTOs.*;
import restudio.rescreen.util.HttpUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsApiClient {
    private final String baseUrl;
    private final String apiKey;
    private final Gson gson = new Gson();

    public LuckPermsApiClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    private Map<String, String> getHeaders() {
        return Map.of("Authorization", "Bearer " + apiKey, "Content-Type", "application/json");
    }

    public CompletableFuture<List<String>> getUserList() {
        return HttpUtils.get(baseUrl + "/user", getHeaders()).thenApply(json -> gson.fromJson(json, new TypeToken<List<String>>() {}.getType()));
    }

    public CompletableFuture<List<String>> getGroupList() {
        return HttpUtils.get(baseUrl + "/group", getHeaders()).thenApply(json -> gson.fromJson(json, new TypeToken<List<String>>() {}.getType()));
    }

    public CompletableFuture<List<String>> getTrackList() {
        return HttpUtils.get(baseUrl + "/track", getHeaders()).thenApply(json -> gson.fromJson(json, new TypeToken<List<String>>() {}.getType()));
    }

    public CompletableFuture<User> getUser(UUID uuid) {
        return HttpUtils.get(baseUrl + "/user/" + uuid.toString(), getHeaders()).thenApply(json -> gson.fromJson(json, User.class));
    }

    public CompletableFuture<Group> getGroup(String name) {
        return HttpUtils.get(baseUrl + "/group/" + name, getHeaders()).thenApply(json -> gson.fromJson(json, Group.class));
    }

    public CompletableFuture<Track> getTrack(String name) {
        return HttpUtils.get(baseUrl + "/track/" + name, getHeaders()).thenApply(json -> gson.fromJson(json, Track.class));
    }

    public CompletableFuture<Void> updateUserNodes(UUID uuid, List<Node> nodes) {
        String json = gson.toJson(toNewNodes(nodes));
        return HttpUtils.put(baseUrl + "/user/" + uuid.toString() + "/nodes", json, "application/json", getHeaders()).thenApply(r -> null);
    }

    public CompletableFuture<Void> updateGroupNodes(String name, List<Node> nodes) {
        String json = gson.toJson(toNewNodes(nodes));
        return HttpUtils.put(baseUrl + "/group/" + name + "/nodes", json, "application/json", getHeaders()).thenApply(r -> null);
    }

    public CompletableFuture<Void> updateTrack(Track track) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("groups", track.groups);
        String json = gson.toJson(payload);
        return HttpUtils.patch(baseUrl + "/track/" + track.name, json, "application/json", getHeaders()).thenApply(r -> null);
    }

    public CompletableFuture<Void> createUser(UUID uuid, String username) {
        User u = new User();
        u.uniqueId = uuid.toString();
        u.username = username;
        return HttpUtils.post(baseUrl + "/user", gson.toJson(u), "application/json", getHeaders()).thenApply(r -> null);
    }

    public CompletableFuture<Void> createGroup(String name) {
        Map<String, String> payload = Map.of("name", name);
        return HttpUtils.post(baseUrl + "/group", gson.toJson(payload), "application/json", getHeaders()).thenApply(r -> null);
    }

    public CompletableFuture<Void> createTrack(String name) {
        Map<String, String> payload = Map.of("name", name);
        return HttpUtils.post(baseUrl + "/track", gson.toJson(payload), "application/json", getHeaders()).thenApply(r -> null);
    }

    public CompletableFuture<Void> deleteGroup(String name) {
        return HttpUtils.delete(baseUrl + "/group/" + name, null, null, getHeaders()).thenApply(r -> null);
    }

    public CompletableFuture<Void> deleteTrack(String name) {
        return HttpUtils.delete(baseUrl + "/track/" + name, null, null, getHeaders()).thenApply(r -> null);
    }

    public CompletableFuture<List<UserSearchResult>> searchUsersByGroup(String group) {
        return HttpUtils.get(baseUrl + "/user/search?group=" + group, getHeaders()).thenApply(json -> gson.fromJson(json, new TypeToken<List<UserSearchResult>>() {}.getType()));
    }

    public CompletableFuture<List<GroupSearchResult>> searchGroupsByParent(String group) {
        return HttpUtils.get(baseUrl + "/group/search?group=" + group, getHeaders()).thenApply(json -> gson.fromJson(json, new TypeToken<List<GroupSearchResult>>() {}.getType()));
    }

    public CompletableFuture<Boolean> healthCheck() {
        return HttpUtils.get(baseUrl + "/health", getHeaders()).thenApply(json -> {
            Health h = gson.fromJson(json, Health.class);
            return h.health;
        }).exceptionally(e -> false);
    }

    private List<NewNode> toNewNodes(List<Node> nodes) {
        List<NewNode> out = new ArrayList<>();
        if (nodes == null) return out;
        for (Node n : nodes) {
            if (n == null) continue;
            NewNode nn = new NewNode();
            nn.key = n.key;
            nn.value = n.value != null ? n.value : Boolean.TRUE;
            nn.context = n.context;
            nn.expiry = n.expiry;
            out.add(nn);
        }
        return out;
    }

    private static class NewNode {
        String key;
        Boolean value;
        List<LuckPermsDTOs.Context> context;
        Long expiry;
    }
}
