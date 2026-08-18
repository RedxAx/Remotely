package redxax.oxy.remotely.web.platform;

import restudio.rebase.platform.Async;
import restudio.rebase.restudio.api.models.MarketplaceModels;
import restudio.rebase.resource.provider.AsyncReStudioMarketplaceProvider;

import java.util.List;

final class BrowserReStudioMarketplaceClient implements AsyncReStudioMarketplaceProvider.Client {
    private final BrowserReSyncMarketplaceApi api;

    BrowserReStudioMarketplaceClient(BrowserReSyncMarketplaceApi api) {
        this.api = api;
    }

    @Override
    public Async<MarketplaceModels.PageResponse<MarketplaceModels.Listing>> browse(String marketplaceSlug, String type,
                                                                                     String query, int page, int size) {
        return api.browse(marketplaceSlug, type, query, page, size, false);
    }

    @Override
    public Async<MarketplaceModels.Listing> listing(String marketplaceSlug, String projectId) {
        return api.getListing(marketplaceSlug, projectId, false);
    }

    @Override
    public Async<List<MarketplaceModels.Version>> versions(String marketplaceSlug, String projectId) {
        return api.getVersions(marketplaceSlug, projectId);
    }

    @Override
    public Async<MarketplaceModels.Version> version(String marketplaceSlug, String versionId) {
        return api.getVersion(marketplaceSlug, versionId);
    }

    @Override
    public Async<MarketplaceModels.Version> latest(String marketplaceSlug, String projectId, String channel,
                                                   String platform) {
        return api.getLatestVersion(marketplaceSlug, projectId, channel, platform);
    }

    @Override
    public Async<String> versionLink(String marketplaceSlug, String projectId, String versionId) {
        return api.getVersionLink(marketplaceSlug, projectId, versionId);
    }
}
