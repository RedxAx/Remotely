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

public class SpigetAPI {
    private static final String SPIGOT_API_URL = "https://api.spiget.org/v2";
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
    private static final String USER_AGENT = "Remotely";

    public static CompletableFuture<List<SpigetResource>> searchPlugins(String query, int limit, int page) {
        List<SpigetResource> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = new URI(SPIGOT_API_URL + "/resources/" + encodedQuery + "?size=" + limit + "&page=" + page);
            DevUtil.devPrint("uri: " + uri);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            JsonArray jsonResponse = JsonParser.parseString(response.body()).getAsJsonArray();
                            for (int i = 0; i < jsonResponse.size(); i++) {
                                JsonObject resource = jsonResponse.get(i).getAsJsonObject();
                                SpigetResource spigetResource = parseResource(resource);
                                results.add(spigetResource);
                            }
                        }
                        return results;
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        return results;
                    });
        } catch (Exception e) {
            CompletableFuture<List<SpigetResource>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    private static SpigetResource parseResource(JsonObject resource) {
        String name = resource.has("name") && resource.get("name").isJsonPrimitive() ? resource.get("name").getAsString() : "Unknown";
        String tag = resource.has("tag") && resource.get("tag").isJsonPrimitive() ? resource.get("tag").getAsString() : "Unknown";
        String description = resource.has("description") && resource.get("description").isJsonPrimitive() ? resource.get("description").getAsString() : "No description";
        String iconUrl = resource.has("icon") && resource.get("icon").isJsonObject() && resource.getAsJsonObject("icon").has("url") ? resource.getAsJsonObject("icon").get("url").getAsString() : "";
        int downloads = resource.has("downloads") && resource.get("downloads").isJsonPrimitive() ? resource.get("downloads").getAsInt() : 0;
        int id = resource.has("id") && resource.get("id").isJsonPrimitive() ? resource.get("id").getAsInt() : 0;

        return new SpigetResource(name, tag, description, iconUrl, downloads, id);
    }
}
