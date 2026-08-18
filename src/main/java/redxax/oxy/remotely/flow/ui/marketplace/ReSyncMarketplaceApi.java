package redxax.oxy.remotely.flow.ui.marketplace;

import restudio.rebase.platform.Async;
import restudio.rebase.restudio.api.models.MarketplaceModels;
import restudio.rebase.restudio.api.models.ReleaseModels;

import java.util.List;

public interface ReSyncMarketplaceApi {
    default boolean authenticated() {
        return false;
    }

    default boolean administrator() {
        return false;
    }

    default Async<MarketplaceModels.PageResponse<MarketplaceModels.Listing>> browse(String marketplaceSlug, String type,
                                                                                     String query, int page, int size, boolean refresh) {
        return requestUnavailable();
    }

    default Async<MarketplaceModels.PageResponse<MarketplaceModels.Listing>> browseControl(String marketplaceSlug,
                                                                                            String status, String type,
                                                                                            String query, int page, int size) {
        return requestUnavailable();
    }

    default Async<MarketplaceModels.Listing> getListing(String marketplaceSlug, String listingSlug, boolean refresh) {
        return requestUnavailable();
    }

    default Async<List<MarketplaceModels.Version>> getVersions(String marketplaceSlug, String listingSlug) {
        return requestUnavailable();
    }

    default Async<MarketplaceModels.MediaAsset> uploadMedia(String projectId, String category, String fileName,
                                                             String visibility, byte[] content, String contentType) {
        return requestUnavailable();
    }

    default Async<MarketplaceModels.Listing> createListing(String marketplaceSlug, MarketplaceModels.ListingRequest request) {
        return requestUnavailable();
    }

    default Async<MarketplaceModels.Listing> submitListing(String marketplaceSlug, String listingSlug) {
        return requestUnavailable();
    }

    default Async<MarketplaceModels.ListingMedia> addListingMedia(String marketplaceSlug, String listingSlug,
                                                                   String mediaAssetId, String kind, int sortOrder) {
        return requestUnavailable();
    }

    default Async<MarketplaceModels.Version> createVersion(String marketplaceSlug, String listingSlug, String version,
                                                            String channel, String platform, String releaseId,
                                                            String changelog, String compatibilityJson, String metadataJson) {
        return requestUnavailable();
    }

    default Async<MarketplaceModels.Version> submitVersion(String marketplaceSlug, String listingSlug, String versionId) {
        return requestUnavailable();
    }

    default Async<List<ReleaseModels.Release>> reSyncReleases() {
        return requestUnavailable();
    }

    default String mediaUrl(String mediaAssetId) {
        return "";
    }

    default List<String> localMinecraftVersions() {
        return List.of();
    }

    default void openListing(String marketplaceSlug, String listingSlug, boolean control) {
    }

    static ReSyncMarketplaceApi unavailable() {
        return new ReSyncMarketplaceApi() {
        };
    }

    private static <T> Async<T> requestUnavailable() {
        return Async.failed(new UnsupportedOperationException("Marketplace Is Unavailable"));
    }
}
