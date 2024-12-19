package redxax.oxy.servers;

import com.google.gson.Gson;
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
    private static final Gson gson = new Gson();
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
    private static final String USER_AGENT = "Remotely";

    public static CompletableFuture<List<ModrinthResource>> searchResources(String query, ServerInfo info, int limit, int offset) {
        List<ModrinthResource> results = new ArrayList<>();
        String type = info.isModServer() ? "mod" : "plugin";
        String gameVersion = info.version;
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String facets = "[[\"project_type:" + type + "\"], [\"game_versions:" + gameVersion + "\"]]";
            String encodedFacets = URLEncoder.encode(facets, StandardCharsets.UTF_8);
            URI uri = new URI(MODRINTH_API_URL + "?query=" + encodedQuery + "&facets=" + encodedFacets + "&limit=" + limit + "&offset=" + offset);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", USER_AGENT).GET().build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonArray hits = jsonResponse.getAsJsonArray("hits");
                            List<CompletableFuture<ModrinthResource>> futures = new ArrayList<>();
                            for (int i = 0; i < hits.size(); i++) {
                                JsonObject hit = hits.get(i).getAsJsonObject();
                                String name = hit.has("title") ? hit.get("title").getAsString() : "Unknown";
                                String versionID = hit.has("latest_version") ? hit.get("latest_version").getAsString() : "Unknown";
                                String description = hit.has("description") ? hit.get("description").getAsString() : "No description";
                                String slug = hit.has("slug") ? hit.get("slug").getAsString() : "unknown";
                                String iconUrl = hit.has("icon_url") ? hit.get("icon_url").getAsString() : "";
                                int downloads = hit.has("downloads") ? hit.get("downloads").getAsInt() : 0;
                                CompletableFuture<String> downloadUrlFuture = fetchDownloadUrl(versionID);
                                CompletableFuture<List<String>> gameVersionsFuture = fetchGameVersions(slug);
                                CompletableFuture<ModrinthResource> resourceFuture = downloadUrlFuture.thenCombine(gameVersionsFuture, (downloadUrl, gameVersions) ->
                                        new ModrinthResource(name, versionID, description, slug + ".jar", iconUrl, downloads, slug, gameVersions)
                                );
                                futures.add(resourceFuture);
                            }
                            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                    .thenApply(v -> {
                                        for (CompletableFuture<ModrinthResource> f : futures) {
                                            results.add(f.join());
                                        }
                                        return results;
                                    });
                        } else {
                            System.err.println("Error: " + response.statusCode() + " - " + response.body());
                            return CompletableFuture.completedFuture(results);
                        }
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        return results;
                    });
        } catch (Exception e) {
            CompletableFuture<List<ModrinthResource>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    private static CompletableFuture<String> fetchDownloadUrl(String versionID) {
        try {
            URI uri = new URI("https://api.modrinth.com/v2/version/" + versionID);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", USER_AGENT).GET().build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject version = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonArray files = version.getAsJsonArray("files");
                            if (files.size() > 0) {
                                JsonObject file = files.get(0).getAsJsonObject();
                                return file.get("url").getAsString();
                            }
                        } else {
                            System.err.println("Error fetching download URL: " + response.statusCode() + " - " + response.body());
                        }
                        return "";
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        return "";
                    });
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture("");
        }
    }

    private static CompletableFuture<List<String>> fetchGameVersions(String slug) {
        List<String> gameVersions = new ArrayList<>();
        try {
            URI uri = new URI("https://api.modrinth.com/v2/project/" + slug);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", USER_AGENT).GET().build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject project = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonArray versions = project.getAsJsonArray("versions");
                            for (int i = 0; i < versions.size(); i++) {
                                JsonObject version = versions.get(i).getAsJsonObject();
                                JsonArray gameVersionsArray = version.getAsJsonArray("game_versions");
                                for (int j = 0; j < gameVersionsArray.size(); j++) {
                                    gameVersions.add(gameVersionsArray.get(j).getAsString());
                                }
                            }
                        } else {
                            System.err.println("Error fetching game versions: " + response.statusCode() + " - " + response.body());
                        }
                        return gameVersions;
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        return gameVersions;
                    });
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(gameVersions);
        }
    }
}
