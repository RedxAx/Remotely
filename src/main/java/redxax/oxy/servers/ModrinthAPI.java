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

    public static CompletableFuture<List<ModrinthResource>> searchModpacks(String query, int limit, int offset) {
        List<ModrinthResource> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String facets = "[[\"project_type:modpack\"]]";
            String encodedFacets = URLEncoder.encode(facets, StandardCharsets.UTF_8);
            URI uri = new URI(MODRINTH_API_URL + "?query=" + encodedQuery + "&facets=" + encodedFacets + "&limit=" + limit + "&offset=" + offset);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", USER_AGENT).GET().build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonArray hits = jsonResponse.getAsJsonArray("hits");
                            for (int i = 0; i < hits.size(); i++) {
                                JsonObject hit = hits.get(i).getAsJsonObject();
                                String name = hit.has("title") ? hit.get("title").getAsString() : "Unknown";
                                String versionID = hit.has("latest_version") ? hit.get("latest_version").getAsString() : "Unknown";
                                String description = hit.has("description") ? hit.get("description").getAsString() : "No description";
                                String slug = hit.has("slug") ? hit.get("slug").getAsString() : "unknown";
                                String iconUrl = hit.has("icon_url") ? hit.get("icon_url").getAsString() : "";
                                int downloads = hit.has("downloads") ? hit.get("downloads").getAsInt() : 0;
                                ModrinthResource r = new ModrinthResource(name, versionID, description, slug + ".mrpack", iconUrl, downloads, slug, new ArrayList<>());
                                results.add(r);
                            }
                            return CompletableFuture.completedFuture(results);
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
}