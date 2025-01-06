package redxax.oxy.servers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModrinthAPI {
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/search";
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
    private static final String USER_AGENT = "Remotely";

    public static CompletableFuture<List<ModrinthResource>> searchMods(String query, String serverVersion, int limit, int offset, String category) {
        return searchResources(query, "mod", serverVersion, limit, offset, category);
    }

    public static CompletableFuture<List<ModrinthResource>> searchPlugins(String query, String serverVersion, int limit, int offset, String category) {
        return searchResources(query, "plugin", serverVersion, limit, offset, category);
    }

    public static CompletableFuture<List<ModrinthResource>> searchModpacks(String query, String serverVersion, int limit, int offset) {
        List<ModrinthResource> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String facets = "[[\"project_type:modpack\"], [\"versions:" + serverVersion + "\"]]";
            String encodedFacets = URLEncoder.encode(facets, StandardCharsets.UTF_8);
            URI uri = new URI(MODRINTH_API_URL + "?query=" + encodedQuery + "&facets=" + encodedFacets + "&limit=" + limit + "&offset=" + offset);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", USER_AGENT).GET().build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonArray hits = jsonResponse.getAsJsonArray("hits");
                            List<CompletableFuture<Void>> futures = new ArrayList<>();
                            for (int i = 0; i < hits.size(); i++) {
                                JsonObject hit = hits.get(i).getAsJsonObject();
                                String name = hit.has("title") ? hit.get("title").getAsString() : "Unknown";
                                String versionId = hit.has("latest_version") ? hit.get("latest_version").getAsString() : "Unknown";
                                String projectId = hit.has("project_id") ? hit.get("project_id").getAsString() : "Unknown";
                                String description = hit.has("description") ? hit.get("description").getAsString() : "No description";
                                String slug = hit.has("slug") ? hit.get("slug").getAsString() : "unknown";
                                String iconUrl = hit.has("icon_url") ? hit.get("icon_url").getAsString() : "";
                                int downloads = hit.has("downloads") ? hit.get("downloads").getAsInt() : 0;
                                CompletableFuture<Void> future = fetchVersionDetails(versionId).thenAccept(version -> {
                                    ModrinthResource r = new ModrinthResource(
                                            name,
                                            version,
                                            description,
                                            slug + ".mrpack",
                                            iconUrl,
                                            downloads,
                                            slug,
                                            new ArrayList<>(),
                                            projectId,
                                            versionId
                                    );
                                    results.add(r);
                                });
                                futures.add(future);
                            }
                            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> results);
                        } else {
                            return CompletableFuture.completedFuture(results);
                        }
                    })
                    .exceptionally(e -> results);
        } catch (Exception e) {
            CompletableFuture<List<ModrinthResource>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    private static CompletableFuture<List<ModrinthResource>> searchResources(String query, String type, String serverVersion, int limit, int offset, String category) {
        List<ModrinthResource> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String facets;
            if (type.equals("mod")) {

                facets = "[[\"project_type:mod\"], [\"versions:" + serverVersion + "\"], [\"categories:" + category + "\"], [\"server_side:required\",\"server_side:optional\"]]";
            } else {
                facets = "[[\"project_type:" + type + "\"], [\"versions:" + serverVersion + "\"]]";
            }
            String encodedFacets = URLEncoder.encode(facets, StandardCharsets.UTF_8);
            URI uri = new URI(MODRINTH_API_URL + "?query=" + encodedQuery + "&facets=" + encodedFacets + "&limit=" + limit + "&offset=" + offset + "&index=downloads");
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", USER_AGENT).GET().build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonArray hits = jsonResponse.getAsJsonArray("hits");
                            List<CompletableFuture<Void>> futures = new ArrayList<>();
                            for (int i = 0; i < hits.size(); i++) {
                                JsonObject hit = hits.get(i).getAsJsonObject();
                                String name = hit.has("title") ? hit.get("title").getAsString() : "Unknown";
                                String versionId = hit.has("latest_version") ? hit.get("latest_version").getAsString() : "Unknown";
                                String projectId = hit.has("project_id") ? hit.get("project_id").getAsString() : "Unknown";
                                String description = hit.has("description") ? hit.get("description").getAsString() : "No description";
                                String slug = hit.has("slug") ? hit.get("slug").getAsString() : "unknown";
                                String iconUrl = hit.has("icon_url") ? hit.get("icon_url").getAsString() : "";
                                int downloads = hit.has("downloads") ? hit.get("downloads").getAsInt() : 0;
                                CompletableFuture<Void> future = fetchVersionDetails(versionId).thenAccept(version -> {
                                    ModrinthResource r = new ModrinthResource(
                                            name,
                                            version,
                                            description,
                                            slug + (type.equals("plugin") ? ".jar" : ".mrpack"),
                                            iconUrl,
                                            downloads,
                                            slug,
                                            new ArrayList<>(),
                                            projectId,
                                            versionId
                                    );
                                    results.add(r);
                                });
                                futures.add(future);
                            }
                            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> results);
                        } else {
                            return CompletableFuture.completedFuture(results);
                        }
                    })
                    .exceptionally(e -> results);
        } catch (Exception e) {
            CompletableFuture<List<ModrinthResource>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    private static CompletableFuture<String> fetchVersionDetails(String versionId) {
        try {
            URI uri = new URI("https://api.modrinth.com/v2/version/" + versionId);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", USER_AGENT).GET().build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject version = JsonParser.parseString(response.body()).getAsJsonObject();
                            return version.has("version_number") ? version.get("version_number").getAsString() : "Unknown";
                        }
                        return "Unknown";
                    })
                    .exceptionally(e -> "Unknown");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("Unknown");
        }
    }
}
