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
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2";
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
    private static final String USER_AGENT = "Remotely";

    public static CompletableFuture<List<IRemotelyResource>> searchMods(String query, String serverVersion, int limit, int offset, String category, String sortParam) {
        return searchResources(query, "mod", serverVersion, limit, offset, category, sortParam);
    }

    public static CompletableFuture<List<IRemotelyResource>> searchPlugins(String query, String serverVersion, int limit, int offset, String category, String sortParam) {
        return searchResources(query, "plugin", serverVersion, limit, offset, category, sortParam);
    }

    public static CompletableFuture<List<IRemotelyResource>> searchModpacks(String query, String serverVersion, int limit, int offset, String sortParam) {
        return searchResources(query, "modpack", serverVersion, limit, offset, "fabric", sortParam);
    }

    private static CompletableFuture<List<IRemotelyResource>> searchResources(String query, String type, String serverVersion, int limit, int offset, String category, String sortParam) {
        List<IRemotelyResource> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String facets;
            if (type.equals("mod")) {
                facets = "[[\"project_type:mod\"], [\"versions:" + serverVersion + "\"], [\"categories:" + category + "\"], [\"server_side:required\",\"server_side:optional\"]]";
            } else if (type.equals("modpack")) {
                facets = "[[\"project_type:modpack\"], [\"server_side:required\",\"server_side:optional\"]]";
            } else {
                facets = "[[\"project_type:" + type + "\"], [\"versions:" + serverVersion + "\"]]";
            }
            String encodedFacets = URLEncoder.encode(facets, StandardCharsets.UTF_8);
            URI uri = new URI(MODRINTH_API_URL + "/search?query=" + encodedQuery + "&facets=" + encodedFacets + "&limit=" + limit + "&offset=" + offset + "&index=" + sortParam);
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
                                CompletableFuture<Void> future = fetchVersionDetails(versionId).thenAccept(versionDetails -> {
                                    if (versionDetails != null) {
                                        String versionNumber = versionDetails.has("version_number") ? versionDetails.get("version_number").getAsString() : "Unknown";
                                        int followers = versionDetails.has("followers") && !versionDetails.get("followers").isJsonNull() ? versionDetails.get("followers").getAsInt() : 0;
                                        ModrinthResource r = new ModrinthResource(
                                                name,
                                                versionNumber,
                                                description,
                                                slug + (type.equals("plugin") ? ".jar" : ".mrpack"),
                                                iconUrl,
                                                downloads,
                                                followers,
                                                slug,
                                                new ArrayList<>(),
                                                projectId,
                                                versionId
                                        );
                                        results.add(r);
                                    }
                                });
                                futures.add(future);
                            }
                            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> results);
                        } else {
                            return CompletableFuture.completedFuture(results);
                        }
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        return results;
                    });
        } catch (Exception e) {
            CompletableFuture<List<IRemotelyResource>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    private static CompletableFuture<JsonObject> fetchVersionDetails(String versionId) {
        try {
            URI uri = new URI(MODRINTH_API_URL + "/version/" + versionId);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", USER_AGENT).GET().build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            return JsonParser.parseString(response.body()).getAsJsonObject();
                        }
                        return null;
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        return null;
                    });
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(null);
        }
    }
}
