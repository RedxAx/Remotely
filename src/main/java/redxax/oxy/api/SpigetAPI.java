package redxax.oxy.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redxax.oxy.util.DevUtil;

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
    private static final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
    private static final String USER_AGENT = "Remotely";

    public static CompletableFuture<List<SpigetResource>> searchPlugins(String query, int limit, int page, String sortParam) {
        List<SpigetResource> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri;
            if (!encodedQuery.isEmpty()) {
                uri = new URI(SPIGOT_API_URL + "/search/resources/" + encodedQuery + "?size=" + limit + "&page=" + page + "&sort=" + sortParam);
            } else {
                uri = new URI(SPIGOT_API_URL + "/resources/free?size=" + limit + "&page=" + page + "&sort=" + sortParam);
            }
            DevUtil.devPrint("uri: " + uri);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                var arr = JsonParser.parseString(response.body()).getAsJsonArray();
                                for (int i = 0; i < arr.size(); i++) {
                                    var resource = arr.get(i).getAsJsonObject();
                                    results.add(parseResource(resource));
                                }
                            } catch (Exception ignored) {
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
        String name = resource.has("name") ? resource.get("name").getAsString() : "Unknown";
        String tag = resource.has("tag") ? resource.get("tag").getAsString() : "Unknown";
        String iconUrl = "";
        if (resource.has("icon") && resource.get("icon").isJsonObject() && resource.getAsJsonObject("icon").has("url")) {
            iconUrl = "https://www.spigotmc.org/" + resource.getAsJsonObject("icon").get("url").getAsString();
        }
        int downloads = resource.has("downloads") ? resource.get("downloads").getAsInt() : 0;
        int id = resource.has("id") ? resource.get("id").getAsInt() : 0;
        double averageRating = 0.0;
        if (resource.has("rating")) {
            var ratingObj = resource.getAsJsonObject("rating");
            if (ratingObj.has("average")) {
                averageRating = ratingObj.get("average").getAsDouble();
            }
        }
        boolean external = false;
        String fileUrl = "";
        if (resource.has("external") && resource.get("external").isJsonPrimitive()) {
            external = resource.get("external").getAsBoolean();
        }
        if (resource.has("file") && resource.get("file").isJsonObject()) {
            JsonObject fileObj = resource.getAsJsonObject("file");
            if (external) {
                if (fileObj.has("url")) {
                    fileUrl = "https://www.spigotmc.org/" + fileObj.get("url").getAsString();
                }
            } else {
                fileUrl = "";
            }
        }
        return new SpigetResource(name, tag, iconUrl, downloads, id, averageRating, external, fileUrl);
    }
}
