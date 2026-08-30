package redxax.oxy.remotely.web.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.teavm.jso.JSBody;
import restudio.rebase.backend.RemotePath;
import restudio.rebase.backend.TransferSink;
import restudio.rebase.backend.TransferSource;
import restudio.rescreen.platform.Async;
import restudio.rescreen.platform.http.HttpRequest;
import restudio.rescreen.platform.http.HttpResponse;
import restudio.rescreen.platform.http.HttpTransport;
import restudio.rebase.restudio.api.models.MarketplaceModels;
import restudio.rebase.restudio.api.models.ReleaseModels;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rebase.restudio.community.ReStudioCommunityProviders;
import restudio.rebase.restudio.marketplace.MarketplaceDetailsProvider;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BrowserMarketplaceDetailsProvider implements MarketplaceDetailsProvider {
    private final BrowserRemotelyServerApi serverApi;
    private final BrowserReSyncMarketplaceApi marketplace;
    private final HttpTransport transport;
    private volatile List<String> minecraftVersions = List.of();
    private boolean minecraftVersionsRequested;

    public BrowserMarketplaceDetailsProvider(BrowserRemotelyServerApi serverApi, BrowserReSyncMarketplaceApi marketplace,
                                             HttpTransport transport, BrowserLaunchSession.Metadata metadata) {
        this.serverApi = serverApi;
        this.marketplace = marketplace;
        this.transport = transport;
    }

    @Override
    public boolean isAuthenticated() {
        return marketplace.authenticated();
    }

    @Override
    public boolean isAdmin() {
        return marketplace.administrator() || ReStudioCommunityProviders.current().isAdmin();
    }

    @Override
    public String userId() {
        return ReStudioCommunityProviders.current().userId();
    }

    @Override
    public String username() {
        return ReStudioCommunityProviders.current().username();
    }

    @Override
    public synchronized List<String> minecraftVersions() {
        if (!minecraftVersionsRequested) {
            minecraftVersionsRequested = true;
            loadMinecraftVersions();
        }
        return minecraftVersions;
    }

    @Override
    public Async<MarketplaceModels.Listing> getListing(String marketplaceSlug, String listingSlug) {
        return marketplace.get(marketplacePath(marketplaceSlug, listingSlug), BrowserMarketplaceJson::listing);
    }

    @Override
    public Async<MarketplaceModels.Listing> getControlListing(String marketplaceSlug, String listingSlug) {
        return marketplace.get("/marketplaces/" + encode(marketplaceSlug) + "/control/listings/" + encode(listingSlug), BrowserMarketplaceJson::listing);
    }

    @Override
    public Async<List<MarketplaceModels.Version>> getVersions(String marketplaceSlug, String listingSlug) {
        return marketplace.getVersions(marketplacePath(marketplaceSlug, listingSlug) + "/versions");
    }

    @Override
    public Async<List<MarketplaceModels.Version>> getControlVersions(String marketplaceSlug, String listingSlug) {
        return marketplace.getVersions("/marketplaces/" + encode(marketplaceSlug) + "/control/listings/" + encode(listingSlug) + "/versions");
    }

    @Override
    public Async<MarketplaceModels.ListingUserState> getUserState(String marketplaceSlug, String listingSlug) {
        return marketplace.get(marketplacePath(marketplaceSlug, listingSlug) + "/me", BrowserMarketplaceJson::userState);
    }

    @Override
    public Async<MarketplaceModels.ReactionState> setLike(String marketplaceSlug, String listingSlug, boolean enabled) {
        return marketplace.post(marketplacePath(marketplaceSlug, listingSlug) + "/like", Map.of("enabled", enabled), BrowserMarketplaceJson::reaction);
    }

    @Override
    public Async<MarketplaceModels.ReactionState> setFollow(String marketplaceSlug, String listingSlug, boolean enabled) {
        return marketplace.post(marketplacePath(marketplaceSlug, listingSlug) + "/follow", Map.of("enabled", enabled), BrowserMarketplaceJson::reaction);
    }

    @Override
    public Async<String> getVersionLink(String marketplaceSlug, String listingSlug, String versionId) {
        return marketplace.getStringMap(marketplacePath(marketplaceSlug, listingSlug) + "/versions/" + encode(versionId) + "/link")
                .thenApply(value -> value.get("url"));
    }

    @Override
    public Async<String> getLatestLink(String marketplaceSlug, String listingSlug) {
        return marketplace.getStringMap(marketplacePath(marketplaceSlug, listingSlug) + "/versions/latest/link").thenApply(value -> value.get("url"));
    }

    @Override
    public Async<List<ServerModels.ClientServerView>> getServers() {
        return serverApi.getServers();
    }

    @Override
    public Async<Void> installExtension(String serverId, String url, RemotePath directory, String filename) {
        if (directory == null) return Async.failed(new IllegalArgumentException("Install Directory Is Required"));
        return serverApi.uploadFile(serverId, url, directory.asString(), filename);
    }

    @Override
    public Async<Void> download(String url, TransferSink destination) {
        if (url == null || url.isBlank() || destination == null) return Async.failed(new IllegalArgumentException("Download Is Required"));
        AsyncChain writes = new AsyncChain();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build();
        return transport.sendStreaming(request, bytes -> writes.value = writes.value.thenCompose(ignored -> destination.write(bytes)))
            .thenCompose(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return Async.failed(new IllegalStateException("Download Failed With Status " + response.statusCode()));
                }
                return writes.value.thenCompose(ignored -> destination.close());
            });
    }

    private static final class AsyncChain {
        private Async<Void> value = Async.completed(null);
    }

    @Override
    public Async<MarketplaceModels.MediaAsset> uploadMedia(String projectId, String category, String name, String visibility,
                                                            String filename, String contentType, TransferSource source) {
        return collect(source).thenCompose(content -> marketplace.uploadMedia(projectId, category,
            filename == null || filename.isBlank() ? name : filename, visibility, content, contentType));
    }

    @Override
    public Async<MarketplaceModels.Listing> updateListing(String marketplaceSlug, String listingSlug, MarketplaceModels.ListingRequest request) {
        return marketplace.patch(marketplacePath(marketplaceSlug, listingSlug), request, BrowserMarketplaceJson::listing);
    }

    @Override
    public Async<MarketplaceModels.ListingMedia> addListingMedia(String marketplaceSlug, String listingSlug, String mediaAssetId,
                                                                  String kind, int sortOrder) {
        return marketplace.addListingMedia(marketplaceSlug, listingSlug, mediaAssetId, kind, sortOrder);
    }

    @Override
    public Async<MarketplaceModels.Report> reportListing(String targetType, String targetId, String reason, String details) {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("targetType", targetType == null ? "LISTING" : targetType);
        request.put("targetId", targetId == null ? "" : targetId);
        request.put("reason", reason == null ? "" : reason);
        request.put("details", details == null ? "" : details);
        return marketplace.post("/marketplaces/reports", request, BrowserMarketplaceJson::report);
    }

    @Override
    public Async<List<ReleaseModels.Release>> getReSyncReleases() {
        return marketplace.reSyncReleases();
    }

    @Override
    public Async<MarketplaceModels.Version> createVersion(String marketplaceSlug, String listingSlug, String version, String channel,
                                                           String platform, String releaseId, String changelog, String compatibilityJson,
                                                           String metadataJson, String filename, String contentType, TransferSource source) {
        return collect(source).thenCompose(content -> marketplace.createVersion(marketplaceSlug, listingSlug, version, channel, platform,
            releaseId, changelog, compatibilityJson, metadataJson, filename, contentType, content));
    }

    @Override
    public Async<MarketplaceModels.Version> createVersion(String marketplaceSlug, String listingSlug, String version, String channel,
                                                           String platform, String releaseId, String changelog, String compatibilityJson,
                                                           String metadataJson) {
        return marketplace.createVersion(marketplaceSlug, listingSlug, version, channel, platform, releaseId, changelog,
            compatibilityJson, metadataJson);
    }

    @Override
    public Async<MarketplaceModels.Version> submitVersion(String marketplaceSlug, String listingSlug, String versionId) {
        return marketplace.submitVersion(marketplaceSlug, listingSlug, versionId);
    }

    @Override
    public Async<MarketplaceModels.Listing> reviewListing(String marketplaceSlug, String listingSlug, String status, String note) {
        return marketplace.patch(marketplacePath(marketplaceSlug, listingSlug) + "/review",
            Map.of("status", status, "note", note == null ? "" : note), BrowserMarketplaceJson::listing);
    }

    @Override
    public Async<MarketplaceModels.Version> reviewVersion(String marketplaceSlug, String listingSlug, String versionId, String status, String note) {
        return marketplace.patch(marketplacePath(marketplaceSlug, listingSlug) + "/versions/" + encode(versionId) + "/review",
            Map.of("status", status, "note", note == null ? "" : note), BrowserMarketplaceJson::version);
    }

    private void loadMinecraftVersions() {
        requestTextAbsolute("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").thenApply(BrowserJson::object).thenAccept(manifest -> {
            if (manifest == null || !manifest.has("versions")) return;
            JsonArray versions = manifest.getAsJsonArray("versions");
            List<String> releases = new ArrayList<>();
            versions.forEach(entry -> {
                JsonObject version = entry.getAsJsonObject();
                if ("release".equals(version.get("type").getAsString())) releases.add(version.get("id").getAsString());
            });
            minecraftVersions = List.copyOf(releases);
        });
    }

    private Async<String> requestText(String path) {
        return requestTextAbsolute(BrowserLaunchSession.apiBaseUrl() + path);
    }

    private Async<String> requestTextAbsolute(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json").timeout(Duration.ofSeconds(30));
        if (url.startsWith(BrowserLaunchSession.apiBaseUrl())) builder.header("X-Remotely-Web-Ticket", BrowserLaunchSession.ticket());
        return transport.sendAsync(builder.GET().build(), HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Request Failed With Status " + response.statusCode());
            }
            return response.body();
        });
    }

    private Async<byte[]> collect(TransferSource source) {
        if (source == null) return Async.completed(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        return collect(source, output).thenApply(ignored -> output.toByteArray());
    }

    private Async<Void> collect(TransferSource source, ByteArrayOutputStream output) {
        return source.next().thenCompose(chunk -> {
            byte[] bytes = chunk.bytes();
            output.write(bytes, 0, bytes.length);
            return chunk.last() ? Async.completed(null) : collect(source, output);
        });
    }

    private String marketplacePath(String marketplaceSlug, String listingSlug) {
        return "/marketplaces/" + encode(marketplaceSlug) + "/listings/" + encode(listingSlug);
    }

    @JSBody(params = {"value"}, script = "return encodeURIComponent(value || '');")
    private static native String encode(String value);

}
