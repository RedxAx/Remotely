package redxax.oxy.remotely.web.platform;

import org.teavm.jso.JSBody;
import redxax.oxy.remotely.flow.ui.marketplace.ReSyncMarketplaceApi;
import redxax.oxy.remotely.host.ApplicationHost;
import restudio.rebase.platform.Async;
import restudio.rebase.platform.http.HttpRequest;
import restudio.rebase.platform.http.HttpTransport;
import restudio.rebase.restudio.api.models.MarketplaceModels;
import restudio.rebase.restudio.api.models.ReleaseModels;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BrowserReSyncMarketplaceApi implements ReSyncMarketplaceApi {
    @FunctionalInterface
    interface ResponseDecoder<T> {
        T decode(String value);
    }

    private final HttpTransport transport;
    private final ApplicationHost host;

    public BrowserReSyncMarketplaceApi(HttpTransport transport, BrowserLaunchSession.Metadata metadata, ApplicationHost host) {
        this.transport = transport;
        this.host = host;
    }

    @Override
    public boolean authenticated() {
        return BrowserLaunchSession.authenticated();
    }

    @Override
    public boolean administrator() {
        return BrowserLaunchSession.metadata().scopes().contains("admin");
    }

    @Override
    public Async<MarketplaceModels.PageResponse<MarketplaceModels.Listing>> browse(String marketplaceSlug, String type,
                                                                                    String query, int page, int size, boolean refresh) {
        String path = "/marketplaces/" + encode(marketplaceSlug) + "/listings?page=" + page + "&size=" + size;
        if (type != null && !type.isBlank()) path += "&type=" + encode(type);
        if (query != null && !query.isBlank()) path += "&q=" + encode(query);
        return request("GET", path, null, null).thenApply(BrowserMarketplaceJson::page);
    }

    @Override
    public Async<MarketplaceModels.PageResponse<MarketplaceModels.Listing>> browseControl(String marketplaceSlug,
                                                                                           String status, String type,
                                                                                           String query, int page, int size) {
        String path = "/marketplaces/" + encode(marketplaceSlug) + "/control/listings?page=" + page + "&size=" + size;
        if (status != null && !status.isBlank()) path += "&status=" + encode(status);
        if (type != null && !type.isBlank()) path += "&type=" + encode(type);
        if (query != null && !query.isBlank()) path += "&q=" + encode(query);
        return request("GET", path, null, null).thenApply(BrowserMarketplaceJson::page);
    }

    @Override
    public Async<MarketplaceModels.Listing> getListing(String marketplaceSlug, String listingSlug, boolean refresh) {
        String control = refresh && administrator() ? "/control" : "";
        return get("/marketplaces/" + encode(marketplaceSlug) + control + "/listings/" + encode(listingSlug), BrowserMarketplaceJson::listing);
    }

    @Override
    public Async<List<MarketplaceModels.Version>> getVersions(String marketplaceSlug, String listingSlug) {
        return request("GET", "/marketplaces/" + encode(marketplaceSlug) + "/listings/" + encode(listingSlug) + "/versions", null, null)
                .thenApply(BrowserMarketplaceJson::versions);
    }

    public Async<MarketplaceModels.Version> getVersion(String marketplaceSlug, String versionId) {
        return get("/marketplaces/" + encode(marketplaceSlug) + "/versions/" + encode(versionId), BrowserMarketplaceJson::version);
    }

    public Async<MarketplaceModels.Version> getLatestVersion(String marketplaceSlug, String listingSlug, String channel, String platform) {
        String path = "/marketplaces/" + encode(marketplaceSlug) + "/listings/" + encode(listingSlug)
            + "/versions/latest?channel=" + encode(channel) + "&platform=" + encode(platform);
        return get(path, BrowserMarketplaceJson::version);
    }

    public Async<String> getVersionLink(String marketplaceSlug, String listingSlug, String versionId) {
        return getStringMap("/marketplaces/" + encode(marketplaceSlug) + "/listings/" + encode(listingSlug)
            + "/versions/" + encode(versionId) + "/link").thenApply(values -> values.get("url"));
    }

    @Override
    public Async<MarketplaceModels.MediaAsset> uploadMedia(String projectId, String category, String fileName,
                                                            String visibility, byte[] content, String contentType) {
        String boundary = "----RemotelyWebMedia" + System.currentTimeMillis();
        byte[] body = multipartFile(boundary, fileName, contentType, content);
        String path = "/media?project_id=" + encode(projectId == null ? "marketplace" : projectId)
            + "&category=" + encode(category == null ? "marketplace" : category)
            + "&name=" + encode(fileName == null ? "image.bin" : fileName)
            + "&visibility=" + encode(visibility == null ? "PUBLIC" : visibility.toUpperCase(Locale.ROOT));
        return request("POST", path, body, "multipart/form-data; boundary=" + boundary, mutationKey())
            .thenApply(BrowserMarketplaceJson::media);
    }

    @Override
    public Async<MarketplaceModels.Listing> createListing(String marketplaceSlug, MarketplaceModels.ListingRequest value) {
        return post("/marketplaces/" + encode(marketplaceSlug) + "/listings", value, BrowserMarketplaceJson::listing);
    }

    @Override
    public Async<MarketplaceModels.Listing> submitListing(String marketplaceSlug, String listingSlug) {
        return post("/marketplaces/" + encode(marketplaceSlug) + "/listings/" + encode(listingSlug) + "/submit", Map.of(), BrowserMarketplaceJson::listing);
    }

    @Override
    public Async<MarketplaceModels.ListingMedia> addListingMedia(String marketplaceSlug, String listingSlug,
                                                                  String mediaAssetId, String kind, int sortOrder) {
        return post("/marketplaces/" + encode(marketplaceSlug) + "/listings/" + encode(listingSlug) + "/media",
            Map.of("mediaAssetId", mediaAssetId, "kind", kind, "sortOrder", sortOrder), BrowserMarketplaceJson::listingMedia);
    }

    @Override
    public Async<MarketplaceModels.Version> createVersion(String marketplaceSlug, String listingSlug, String version,
                                                           String channel, String platform, String releaseId,
                                                           String changelog, String compatibilityJson, String metadataJson) {
        return createVersion(marketplaceSlug, listingSlug, version, channel, platform, releaseId, changelog,
            compatibilityJson, metadataJson, null, null, null);
    }

    Async<MarketplaceModels.Version> createVersion(String marketplaceSlug, String listingSlug, String version,
                                                    String channel, String platform, String releaseId,
                                                    String changelog, String compatibilityJson, String metadataJson,
                                                    String fileName, String contentType, byte[] content) {
        String boundary = "----RemotelyWebVersion" + System.currentTimeMillis();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("version", value(version));
        fields.put("channel", value(channel, "stable"));
        fields.put("platform", value(platform, "universal"));
        put(fields, "release_id", releaseId);
        put(fields, "changelog", changelog);
        put(fields, "compatibility_json", compatibilityJson);
        put(fields, "metadata_json", metadataJson);
        byte[] body = content == null ? multipartFields(boundary, fields)
            : multipartFieldsAndFile(boundary, fields, fileName, contentType, content);
        return request("POST", "/marketplaces/" + encode(marketplaceSlug) + "/listings/" + encode(listingSlug) + "/versions",
            body, "multipart/form-data; boundary=" + boundary, mutationKey()).thenApply(BrowserMarketplaceJson::version);
    }

    @Override
    public Async<MarketplaceModels.Version> submitVersion(String marketplaceSlug, String listingSlug, String versionId) {
        return post("/marketplaces/" + encode(marketplaceSlug) + "/listings/" + encode(listingSlug) + "/versions/" + encode(versionId) + "/submit",
            Map.of(), BrowserMarketplaceJson::version);
    }

    @Override
    public Async<List<ReleaseModels.Release>> reSyncReleases() {
        return request("GET", "/releases/resync", null, null).thenApply(BrowserMarketplaceJson::releases);
    }

    @Override
    public String mediaUrl(String mediaAssetId) {
        return BrowserLaunchSession.apiBaseUrl() + "/media/" + encode(mediaAssetId) + "/download";
    }

    @Override
    public void openListing(String marketplaceSlug, String listingSlug, boolean control) {
        host.openMarketplaceListing(marketplaceSlug, listingSlug, control);
    }

    <T> Async<T> get(String path, ResponseDecoder<T> decoder) {
        return request("GET", path, null, null).thenApply(value -> decode(value, decoder));
    }

    Async<Map<String, String>> getStringMap(String path) {
        return request("GET", path, null, null).thenApply(value -> {
            Map<String, String> result = new LinkedHashMap<>();
            BrowserJson.object(value).entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().isJsonNull() ? "" : entry.getValue().getAsString()));
            return Map.copyOf(result);
        });
    }

    Async<List<MarketplaceModels.Version>> getVersions(String path) {
        return request("GET", path, null, null).thenApply(BrowserMarketplaceJson::versions);
    }

    <T> Async<T> post(String path, Object body, ResponseDecoder<T> decoder) {
        return request("POST", path, BrowserMarketplaceJson.body(body).getBytes(StandardCharsets.UTF_8), "application/json", mutationKey())
            .thenApply(value -> decode(value, decoder));
    }

    <T> Async<T> patch(String path, Object body, ResponseDecoder<T> decoder) {
        return request("PATCH", path, BrowserMarketplaceJson.body(body).getBytes(StandardCharsets.UTF_8), "application/json", mutationKey())
            .thenApply(value -> decode(value, decoder));
    }

    private static <T> T decode(String value, ResponseDecoder<T> decoder) {
        if (decoder == null) {
            throw new IllegalArgumentException("Browser Marketplace Response Decoder Is Required");
        }
        return decoder.decode(value);
    }

    private Async<String> request(String method, String path, byte[] body, String contentType) {
        return request(method, path, body, contentType, null);
    }

    private Async<String> request(String method, String path, byte[] body, String contentType, String idempotencyKey) {
        return requestOnce(method, path, body, contentType, idempotencyKey, true);
    }

    private Async<String> requestOnce(String method, String path, byte[] body, String contentType, String idempotencyKey, boolean retry) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BrowserLaunchSession.apiBaseUrl() + webPath(path)))
            .header("Accept", "application/json");
        if (contentType != null) builder.header("Content-Type", contentType);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        return transport.sendAsync(builder.build()).thenCompose(response -> {
            if (response.statusCode() == 401 && retry && BrowserLaunchSession.authenticated()) {
                return BrowserLaunchSession.renewAsync()
                    .exceptionallyCompose(failure -> {
                        if (BrowserLaunchSession.isAuthenticationFailure(failure)) {
                            BrowserLaunchSession.expireSession();
                            if (host != null) host.signIn(host.getCurrentScreen());
                            return Async.failed(failure);
                        }
                        return Async.failed(failure);
                    })
                    .thenCompose(ignored -> requestOnce(method, path, body, contentType, idempotencyKey, false));
            }
            if (response.statusCode() == 401) {
                BrowserLaunchSession.expireSession();
                if (host != null) host.signIn(host.getCurrentScreen());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Async.failed(new IllegalStateException(response.body() == null || response.body().isBlank() ? "Marketplace Request Failed" : response.body()));
            }
            return Async.completed(response.body());
        });
    }

    private static String mutationKey() {
        return UUID.randomUUID().toString();
    }

    private static String webPath(String path) {
        if (path != null && (path.equals("/marketplaces") || path.startsWith("/marketplaces/")
                || path.equals("/media") || path.startsWith("/media/"))) {
            return "/remotely-web" + path;
        }
        return path;
    }

    private static byte[] multipartFile(String boundary, String fileName, String contentType, byte[] content) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + value(fileName, "image.bin")
                + "\"\r\nContent-Type: " + value(contentType, "application/octet-stream") + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(content == null ? new byte[0] : content);
            output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] multipartFields(String boundary, Map<String, String> fields) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(multipartFieldsPrefix(boundary, fields));
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] multipartFieldsAndFile(String boundary, Map<String, String> fields, String fileName,
                                                  String contentType, byte[] content) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(multipartFieldsPrefix(boundary, fields));
            output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + value(fileName, "artifact.bin") + "\"\r\nContent-Type: " + value(contentType, "application/octet-stream")
                + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(content);
            output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] multipartFieldsPrefix(String boundary, Map<String, String> fields) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            fields.forEach((name, value) -> {
                try {
                    output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void put(Map<String, String> fields, String name, String value) {
        if (value != null && !value.isBlank()) fields.put(name, value);
    }

    @JSBody(params = {"value"}, script = "return encodeURIComponent(value || '');")
    private static native String encode(String value);
}
