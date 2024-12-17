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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ModrinthAPI {
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/search";
    private static final Gson gson = new Gson();
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final String USER_AGENT = "YourAppName/1.0 (contact@example.com)";

    public static List<ModrinthResource> searchResources(String query, ServerInfo info) {
        List<ModrinthResource> results = new ArrayList<>();
        String type = info.isModServer() ? "mod" : "plugin";

        try {
            // Encode the query and facets parameters
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String facets = "[[\"project_type:" + type + "\"]]";
            String encodedFacets = URLEncoder.encode(facets, StandardCharsets.UTF_8);

            // Construct the URI with encoded parameters
            URI uri = new URI(MODRINTH_API_URL + "?query=" + encodedQuery + "&facets=" + encodedFacets);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray hits = jsonResponse.getAsJsonArray("hits");

                for (int i = 0; i < hits.size(); i++) {
                    JsonObject hit = hits.get(i).getAsJsonObject();
                    String name = hit.has("title") ? hit.get("title").getAsString() : "Unknown";
                    String version = hit.has("latest_version") ? hit.get("latest_version").getAsString() : "Unknown";
                    String description = hit.has("description") ? hit.get("description").getAsString() : "No description";
                    String slug = hit.has("slug") ? hit.get("slug").getAsString() : "unknown";
                    String downloadUrl = "https://modrinth.com/" + type + "/" + slug + "/download";

                    results.add(new ModrinthResource(name, version, Paths.get(downloadUrl), description, slug + ".jar"));
                }
            } else {
                System.err.println("Error: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }
}
