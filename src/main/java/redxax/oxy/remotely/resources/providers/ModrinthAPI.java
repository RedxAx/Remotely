package redxax.oxy.remotely.resources.providers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redxax.oxy.remotely.resources.IRemotelyResource;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class ModrinthAPI {
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2";
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
    private static final String USER_AGENT = "Remotely";
    private static final Semaphore REQUEST_SEMAPHORE = new Semaphore(10);
    private static final Executor API_EXECUTOR = Executors.newFixedThreadPool(4);

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
        List<IRemotelyResource> results = Collections.synchronizedList(new ArrayList<>());
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
                                String author = hit.has("author") ? hit.get("author").getAsString() : "Unknown";
                                String versionIdFromHit = hit.has("latest_version") ? hit.get("latest_version").getAsString() : null;
                                String projectId = hit.has("project_id") ? hit.get("project_id").getAsString() : "Unknown";
                                String description = hit.has("description") ? hit.get("description").getAsString() : "No description";
                                String slug = hit.has("slug") ? hit.get("slug").getAsString() : "unknown";
                                String iconUrl = hit.has("icon_url") ? hit.get("icon_url").getAsString() : "";
                                int downloads = hit.has("downloads") ? hit.get("downloads").getAsInt() : 0;
                                int projectFollowers = hit.has("follows") ? hit.get("follows").getAsInt() : 0;

                                CompletableFuture<JsonObject> versionDetailsFuture = fetchVersionDetails(versionIdFromHit);
                                CompletableFuture<JsonObject> projectDetailsFuture = fetchProjectDetails(projectId);

                                CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(versionDetailsFuture, projectDetailsFuture)
                                        .thenAccept(voidResult -> {
                                            JsonObject versionDetails = versionDetailsFuture.join();
                                            JsonObject projectDetails = projectDetailsFuture.join();

                                            String versionNumber = "Unknown";
                                            List<String> mcVersionsList = new ArrayList<>();
                                            List<String> loaderPlatformsList = new ArrayList<>();
                                            List<String> dependencyProjectIds = new ArrayList<>();

                                            if (versionDetails != null) {
                                                versionNumber = versionDetails.has("version_number") ? versionDetails.get("version_number").getAsString() : "Unknown";

                                                if (versionDetails.has("game_versions") && versionDetails.get("game_versions").isJsonArray()) {
                                                    JsonArray gameVersionsArray = versionDetails.getAsJsonArray("game_versions");
                                                    for (JsonElement versionElement : gameVersionsArray) {
                                                        mcVersionsList.add(versionElement.getAsString());
                                                    }
                                                }
                                                if (versionDetails.has("loaders") && versionDetails.get("loaders").isJsonArray()) {
                                                    JsonArray loadersArray = versionDetails.getAsJsonArray("loaders");
                                                    for (JsonElement loaderElement : loadersArray) {
                                                        loaderPlatformsList.add(loaderElement.getAsString());
                                                    }
                                                }
                                                if (versionDetails.has("dependencies") && versionDetails.get("dependencies").isJsonArray()) {
                                                    JsonArray depsArray = versionDetails.getAsJsonArray("dependencies");
                                                    for (JsonElement depElement : depsArray) {
                                                        JsonObject depObj = depElement.getAsJsonObject();
                                                        if (depObj.has("project_id") && !depObj.get("project_id").isJsonNull()) {
                                                            dependencyProjectIds.add(depObj.get("project_id").getAsString());
                                                        }
                                                    }
                                                }
                                            }
                                            String bannerUrl = "";
                                            if (projectDetails != null && projectDetails.has("gallery") && projectDetails.get("gallery").isJsonArray()) {
                                                JsonArray gallery = projectDetails.getAsJsonArray("gallery");
                                                if (gallery.size() > 0) {
                                                    JsonObject firstImage = gallery.get(0).getAsJsonObject();
                                                    if (firstImage.has("url") && !firstImage.get("url").isJsonNull()) {
                                                        bannerUrl = firstImage.get("url").getAsString();
                                                    }
                                                }
                                            }
                                            ModrinthResource r = new ModrinthResource(name, versionNumber, description, slug + (type.equals("plugin") ? ".jar" : ".mrpack"), iconUrl, downloads, projectFollowers, slug, dependencyProjectIds, projectId, versionIdFromHit != null ? versionIdFromHit : "Unknown", author, String.join(", ", mcVersionsList), String.join(", ", loaderPlatformsList), bannerUrl);
                                            results.add(r);
                                        }).exceptionally(e -> {
                                            e.printStackTrace();
                                            return null;
                                        });
                                futures.add(combinedFuture);
                            }
                            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> results);
                        } else {
                            System.err.println("Failed to search resources. Status: " + response.statusCode() + " Body: " + response.body());
                            return CompletableFuture.completedFuture(results);
                        }
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        return results;
                    });
        } catch (Exception e) {
            e.printStackTrace();
            CompletableFuture<List<IRemotelyResource>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    private static CompletableFuture<JsonObject> fetchVersionDetails(String versionId) {
        if (versionId == null || versionId.isEmpty() || versionId.equals("Unknown")) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            URI uri = new URI(MODRINTH_API_URL + "/version/" + versionId);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", USER_AGENT).GET().build();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    REQUEST_SEMAPHORE.acquire();
                    return client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                } finally {
                    REQUEST_SEMAPHORE.release();
                }
            }, API_EXECUTOR).thenApply(response -> {
                if (response != null && response.statusCode() == 200) {
                    return JsonParser.parseString(response.body()).getAsJsonObject();
                }
                System.err.println("Failed to fetch version details for " + versionId + ". Status: " + (response != null ? response.statusCode() : "null"));
                return null;
            }).exceptionally(e -> {
                e.printStackTrace();
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(null);
        }
    }

    private static CompletableFuture<JsonObject> fetchProjectDetails(String projectId) {
        if (projectId == null || projectId.isEmpty() || projectId.equals("Unknown")) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            URI uri = new URI(MODRINTH_API_URL + "/project/" + projectId);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", USER_AGENT).GET().build();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    REQUEST_SEMAPHORE.acquire();
                    return client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                } finally {
                    REQUEST_SEMAPHORE.release();
                }
            }, API_EXECUTOR).thenApply(response -> {
                if (response != null && response.statusCode() == 200) {
                    return JsonParser.parseString(response.body()).getAsJsonObject();
                }
                System.err.println("Failed to fetch project details for " + projectId + ". Status: " + (response != null ? response.statusCode() : "null"));
                return null;
            }).exceptionally(e -> {
                e.printStackTrace();
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(null);
        }
    }
}

