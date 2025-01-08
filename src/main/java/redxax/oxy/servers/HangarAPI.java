package redxax.oxy.servers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redxax.oxy.DevUtil;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HangarAPI {
    private static final String HANGAR_API_URL = "https://hangar.papermc.io/api/v1";
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
    private static final String USER_AGENT = "Remotely";

    public static CompletableFuture<List<HangarResource>> searchPlugins(String query, int limit, int offset) {
        List<HangarResource> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = new URI(HANGAR_API_URL + "/projects?query=" + encodedQuery + "&limit=" + limit + "&offset=" + offset);
            DevUtil.devPrint("uri: " + uri);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonArray projects = jsonResponse.getAsJsonArray("result");
                            for (int i = 0; i < projects.size(); i++) {
                                JsonObject project = projects.get(i).getAsJsonObject();
                                HangarResource hangarResource = parseResource(project);
                                results.add(hangarResource);
                            }
                        }
                        return results;
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        return results;
                    });
        } catch (Exception e) {
            CompletableFuture<List<HangarResource>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    private static HangarResource parseResource(JsonObject project) {
        String name = project.has("name") ? project.get("name").getAsString() : "Unknown";
        String description = project.has("description") ? project.get("description").getAsString() : "No description";
        String owner = project.has("owner") ? project.get("owner").getAsString() : "Unknown";
        String visibility = project.has("visibility") ? project.get("visibility").getAsString() : "Public";
        int stars = project.has("stars") ? project.get("stars").getAsInt() : 0;
        int watchers = project.has("watchers") ? project.get("watchers").getAsInt() : 0;
        int downloads = project.has("downloads") ? project.get("downloads").getAsInt() : 0;

        return new HangarResource(name, description, owner, visibility, stars, watchers, downloads);
    }
}
