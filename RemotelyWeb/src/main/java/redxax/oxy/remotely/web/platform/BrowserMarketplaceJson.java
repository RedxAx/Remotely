package redxax.oxy.remotely.web.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import restudio.rebase.restudio.api.models.MarketplaceModels;
import restudio.rebase.restudio.api.models.ReleaseModels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class BrowserMarketplaceJson {
    private static final String UNSUPPORTED_JSON_VALUE = "Unsupported Browser Marketplace JSON Value";

    private BrowserMarketplaceJson() {
    }

    static MarketplaceModels.PageResponse<MarketplaceModels.Listing> page(String json) {
        JsonObject root = BrowserJson.object(json);
        MarketplaceModels.PageResponse<MarketplaceModels.Listing> result = new MarketplaceModels.PageResponse<>();
        result.data = BrowserJson.objects(root, "data").stream().map(BrowserMarketplaceJson::listing).toList();
        result.page = BrowserJson.integer(root, "page", 0);
        result.size = BrowserJson.integer(root, "size", result.data.size());
        result.totalElements = BrowserJson.longValue(root, "totalElements", result.data.size());
        result.totalPages = BrowserJson.integer(root, "totalPages", result.data.isEmpty() ? 0 : 1);
        return result;
    }

    static MarketplaceModels.Listing listing(String json) {
        return listing(BrowserJson.object(json));
    }

    static MarketplaceModels.Listing listing(JsonObject root) {
        MarketplaceModels.Listing value = new MarketplaceModels.Listing();
        value.id = text(root, "id");
        value.marketplaceSlug = text(root, "marketplaceSlug");
        value.slug = text(root, "slug");
        value.title = text(root, "title");
        value.summary = text(root, "summary");
        value.description = text(root, "description");
        value.rights = text(root, "rights");
        value.type = text(root, "type");
        value.status = text(root, "status");
        value.visibility = text(root, "visibility");
        value.tagsText = text(root, "tagsText");
        value.categoriesText = text(root, "categoriesText");
        value.sourceUrl = text(root, "sourceUrl");
        value.docsUrl = text(root, "docsUrl");
        value.iconMediaId = text(root, "iconMediaId");
        value.metadataJson = text(root, "metadataJson");
        value.viewCount = BrowserJson.longValue(root, "viewCount", 0);
        value.likeCount = BrowserJson.longValue(root, "likeCount", 0);
        value.followCount = BrowserJson.longValue(root, "followCount", 0);
        value.downloadCount = BrowserJson.longValue(root, "downloadCount", 0);
        value.author = user(root, "author");
        value.reviewedBy = user(root, "reviewedBy");
        value.reviewNote = text(root, "reviewNote");
        value.createdAt = text(root, "createdAt");
        value.updatedAt = text(root, "updatedAt");
        return value;
    }

    static List<MarketplaceModels.Version> versions(String json) {
        List<MarketplaceModels.Version> result = new ArrayList<>();
        BrowserJson.array(json).forEach(value -> {
            if (value.isJsonObject()) result.add(version(value.getAsJsonObject()));
        });
        return List.copyOf(result);
    }

    static MarketplaceModels.Version version(String json) {
        return version(BrowserJson.object(json));
    }

    private static MarketplaceModels.Version version(JsonObject root) {
        MarketplaceModels.Version value = new MarketplaceModels.Version();
        value.id = text(root, "id");
        value.listingId = text(root, "listingId");
        value.listingSlug = text(root, "listingSlug");
        value.version = text(root, "version");
        value.channel = text(root, "channel");
        value.platform = text(root, "platform");
        value.status = text(root, "status");
        value.releaseId = text(root, "releaseId");
        value.fileName = text(root, "fileName");
        value.contentType = text(root, "contentType");
        value.fileSize = BrowserJson.longValue(root, "fileSize", 0);
        value.checksum = text(root, "checksum");
        value.changelog = text(root, "changelog");
        value.compatibilityJson = text(root, "compatibilityJson");
        value.metadataJson = text(root, "metadataJson");
        value.downloadCount = BrowserJson.longValue(root, "downloadCount", 0);
        value.uploadedBy = user(root, "uploadedBy");
        value.reviewedBy = user(root, "reviewedBy");
        value.reviewNote = text(root, "reviewNote");
        value.publishedAt = text(root, "publishedAt");
        value.createdAt = text(root, "createdAt");
        return value;
    }

    static MarketplaceModels.MediaAsset media(String json) {
        JsonObject root = BrowserJson.object(json);
        MarketplaceModels.MediaAsset value = new MarketplaceModels.MediaAsset();
        value.id = text(root, "id");
        value.projectId = text(root, "projectId");
        value.category = text(root, "category");
        value.fileName = text(root, "fileName");
        value.contentType = text(root, "contentType");
        value.fileSize = BrowserJson.longValue(root, "fileSize", 0);
        value.checksum = text(root, "checksum");
        value.visibility = text(root, "visibility");
        value.active = BrowserJson.bool(root, "active", false);
        value.downloadCount = BrowserJson.longValue(root, "downloadCount", 0);
        value.uploadedBy = user(root, "uploadedBy");
        value.embedUrl = text(root, "embedUrl");
        value.createdAt = text(root, "createdAt");
        return value;
    }

    static MarketplaceModels.ListingMedia listingMedia(String json) {
        JsonObject root = BrowserJson.object(json);
        MarketplaceModels.ListingMedia value = new MarketplaceModels.ListingMedia();
        value.id = text(root, "id");
        value.mediaAssetId = text(root, "mediaAssetId");
        value.kind = text(root, "kind");
        value.sortOrder = BrowserJson.integer(root, "sortOrder", 0);
        value.createdAt = text(root, "createdAt");
        return value;
    }

    static MarketplaceModels.ListingUserState userState(String json) {
        JsonObject root = BrowserJson.object(json);
        MarketplaceModels.ListingUserState value = new MarketplaceModels.ListingUserState();
        value.liked = BrowserJson.bool(root, "liked", false);
        value.followed = BrowserJson.bool(root, "followed", false);
        value.owner = BrowserJson.bool(root, "owner", false);
        value.publisher = BrowserJson.bool(root, "publisher", false);
        value.canManage = BrowserJson.bool(root, "canManage", false);
        value.canAnalyze = BrowserJson.bool(root, "canAnalyze", false);
        return value;
    }

    static MarketplaceModels.ReactionState reaction(String json) {
        JsonObject root = BrowserJson.object(json);
        MarketplaceModels.ReactionState value = new MarketplaceModels.ReactionState();
        value.active = BrowserJson.bool(root, "active", false);
        value.likes = BrowserJson.longValue(root, "likes", 0);
        value.follows = BrowserJson.longValue(root, "follows", 0);
        return value;
    }

    static MarketplaceModels.Report report(String json) {
        JsonObject root = BrowserJson.object(json);
        MarketplaceModels.Report value = new MarketplaceModels.Report();
        value.id = text(root, "id");
        value.targetType = text(root, "targetType");
        value.targetId = text(root, "targetId");
        value.marketplaceId = text(root, "marketplaceId");
        value.listingId = text(root, "listingId");
        value.reason = text(root, "reason");
        value.details = text(root, "details");
        value.status = text(root, "status");
        value.reporter = user(root, "reporter");
        value.reviewedBy = user(root, "reviewedBy");
        value.reviewNote = text(root, "reviewNote");
        value.createdAt = text(root, "createdAt");
        value.updatedAt = text(root, "updatedAt");
        return value;
    }

    static List<ReleaseModels.Release> releases(String json) {
        List<ReleaseModels.Release> result = new ArrayList<>();
        BrowserJson.array(json).forEach(item -> {
            if (!item.isJsonObject()) return;
            JsonObject root = item.getAsJsonObject();
            ReleaseModels.Release value = new ReleaseModels.Release();
            value.id = text(root, "id");
            value.projectId = text(root, "projectId");
            value.version = text(root, "version");
            value.channel = text(root, "channel");
            value.platform = text(root, "platform");
            value.fileName = text(root, "fileName");
            value.fileSize = BrowserJson.longValue(root, "fileSize", 0);
            value.checksum = text(root, "checksum");
            value.changelog = text(root, "changelog");
            value.downloadCount = BrowserJson.longValue(root, "downloadCount", 0);
            value.uploadedBy = user(root, "uploadedBy");
            value.createdAt = text(root, "createdAt");
            result.add(value);
        });
        return List.copyOf(result);
    }

    static String body(Object value) {
        return BrowserJson.write(element(value));
    }

    private static JsonElement element(Object value) {
        if (value == null) return BrowserJson.parse("null");
        if (value instanceof JsonElement element) return element;
        if (value instanceof String text) return new JsonPrimitive(text);
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof MarketplaceModels.ListingRequest request) return listingRequest(request);
        if (value instanceof Map<?, ?> map) {
            JsonObject result = new JsonObject();
            map.forEach((key, item) -> BrowserJson.put(result, String.valueOf(key), element(item)));
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            JsonArray result = new JsonArray();
            iterable.forEach(item -> result.add(element(item)));
            return result;
        }
        throw new IllegalArgumentException(UNSUPPORTED_JSON_VALUE);
    }

    private static JsonObject listingRequest(MarketplaceModels.ListingRequest value) {
        JsonObject result = new JsonObject();
        BrowserJson.put(result, "slug", value.slug);
        BrowserJson.put(result, "title", value.title);
        BrowserJson.put(result, "summary", value.summary);
        BrowserJson.put(result, "description", value.description);
        BrowserJson.put(result, "rights", value.rights);
        BrowserJson.put(result, "type", value.type);
        BrowserJson.put(result, "visibility", value.visibility);
        BrowserJson.put(result, "tagsText", value.tagsText);
        BrowserJson.put(result, "categoriesText", value.categoriesText);
        BrowserJson.put(result, "sourceUrl", value.sourceUrl);
        BrowserJson.put(result, "docsUrl", value.docsUrl);
        BrowserJson.put(result, "iconMediaId", value.iconMediaId);
        BrowserJson.put(result, "metadataJson", value.metadataJson);
        BrowserJson.put(result, "creationKey", value.creationKey);
        return result;
    }

    private static MarketplaceModels.UserSummary user(JsonObject root, String name) {
        JsonElement element = BrowserJson.element(root, name);
        if (element == null || !element.isJsonObject()) return null;
        JsonObject value = element.getAsJsonObject();
        MarketplaceModels.UserSummary result = new MarketplaceModels.UserSummary();
        result.id = text(value, "id");
        result.username = text(value, "username");
        result.displayName = text(value, "displayName");
        result.avatarUrl = text(value, "avatarUrl");
        return result;
    }

    private static String text(JsonObject root, String name) {
        return BrowserJson.string(root, name, null);
    }
}
