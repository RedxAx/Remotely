package redxax.oxy.remotely.flow.ui.marketplace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.GuiElement;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.data.TriggerType;
import redxax.oxy.remotely.flow.ui.MinecraftUiPreviewRenderer;
import restudio.rebase.Rebase;
import restudio.rebase.cache.CacheManager;
import restudio.rebase.minecraft.GameVersion;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.restudio.api.models.MarketplaceModels;
import restudio.rebase.restudio.api.models.ReleaseModels;
import restudio.rebase.restudio.marketplace.MarketplaceService;
import restudio.rebase.ui.screens.marketplace.MarketplaceDetailsScreen;
import restudio.rebase.ui.widgets.editor.TextAreaWidget;
import restudio.rebase.ui.widgets.marketplace.MarketplaceListingWidget;
import restudio.rebase.ui.widgets.resources.ResourceWidget;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.IconMessage;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.FileUtils;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static restudio.rescreen.config.Config.desktopMode;
import static restudio.rescreen.config.Config.shadow;
import static restudio.rescreen.render.TextRenderer.tr;
import static restudio.rescreen.util.ImageUtils.loadImageId;

public class ReSyncMarketplaceScreen extends ReScreen {
    private static final String MARKETPLACE = "resync-community";
    private static final String CUSTOM_FUNCTION_PREFIX = "custom_function:";
    private static final List<String> BUNDLE_TAGS = List.of(
            "Admin",
            "Automation",
            "Chat",
            "Economy",
            "Events",
            "Gameplay",
            "Moderation",
            "Permissions",
            "Progression",
            "Quality Of Life",
            "Server Management",
            "Social",
            "Staff Tools",
            "Survival",
            "Utility",
            "Minigame",
            "PvE",
            "PvP",
            "Cosmetic",
            "Interface",
            "Items",
            "Blocks",
            "Starter Pack",
            "Template",
            "Example",
            "Experimental"
    );

    private final Screen parent;
    private final String serverId;
    private final Gson gson = new GsonBuilder().create();
    private Container discoverContainer;
    private Container bundleContainer;
    private Container installedContainer;
    private String mode = "discover";
    private String query = "";
    private String pendingQuery = null;
    private long searchDebounceTime = 0;
    private boolean discoverLoading;
    private boolean bundleSearchEmpty;
    private boolean installedSearchEmpty;
    private boolean publishing;
    private long discoverRequestSeq = 0;
    private Map<String, AssetEntry> assets = new LinkedHashMap<>();
    private final Set<String> selectedKeys = new HashSet<>();
    private final Set<String> dependencyKeys = new HashSet<>();
    private InfoWidget bundleInfoWidget;
    private IconMessage searchFailedMessage;
    private final Map<String, Identifier> installedBundleIcons = new HashMap<>();
    private final Set<String> installedBundleIconLoads = new HashSet<>();
    private final Map<String, MarketplaceModels.Version> installedBundleUpdates = new HashMap<>();
    private final Set<String> installedBundleUpdateChecks = new HashSet<>();
    private final Set<String> installedBundleUpdateChecked = new HashSet<>();

    public ReSyncMarketplaceScreen(Screen parent, String serverId) {
        this.parent = parent;
        this.serverId = serverId;
    }

    @Override
    public String getDesktopAppId() {
        return "resync-marketplace";
    }

    @Override
    public String getDesktopAppTitle() {
        return "Marketplace";
    }

    @Override
    public String getDesktopAppIconPath() {
        return "download.png";
    }

    @Override
    public void init() {
        super.init();
        if (searchFailedMessage == null) {
            searchFailedMessage = new IconMessage(0, 0, 64, 64, "No Results Found", "searchFailed.png");
        }
        if (!desktopMode) {
            header().addRight("close.png", this::closeScreen, "Back");
        }
        header().addRight("reload.png", this::refresh, "Refresh");
        header().addRight("upload.png", this::publishBundle, "Publish");
        SearchMode discoverSearchMode = createSearchMode("Search Marketplace...");
        SearchMode bundleSearchMode = createSearchMode("Search Bundle...");
        SearchMode installedSearchMode = createSearchMode("Search Installed...");
        header().setSearchMode(discoverSearchMode, true);
        header().build();

        tabs().builder().position(5, 36).size(width - 10, 18).onTabSelected(this::onTabSwitch).allowAdd(false).allowClose(false).allowReorder(false).allowRename(false).build();
        discoverContainer = createContainer("resync-marketplace-discover", 5, 60, width - 10, height - 65);
        discoverContainer.layout(new ManagedLayout()).padding(5).columns(3).scrolling(true);
        discoverContainer.setSearchMode(discoverSearchMode);
        bundleContainer = createContainer("resync-marketplace-bundle", 5, 60, width - 10, height - 65);
        bundleContainer.layout(new ManagedLayout()).padding(5).columns(1).scrolling(true);
        bundleContainer.setSearchMode(bundleSearchMode);
        installedContainer = createContainer("resync-marketplace-installed", 5, 60, width - 10, height - 65);
        installedContainer.layout(new ManagedLayout()).padding(5).columns(1).scrolling(true);
        installedContainer.setSearchMode(installedSearchMode);
        TabsManager.Tab discoverTab = tabs().addTab("Discover", discoverContainer);
        discoverTab.setData("discover");
        TabsManager.Tab bundleTab = tabs().addTab("Bundle", bundleContainer);
        bundleTab.setData("bundle");
        TabsManager.Tab installedTab = tabs().addTab("Installed", installedContainer);
        installedTab.setData("installed");

        loadAssets();
        tabsManager.setActiveTab(0);
        setActiveContainer(discoverContainer);
        loadDiscover(false);
    }

    private SearchMode createSearchMode(String placeholder) {
        SearchMode searchMode = new SearchMode(false);
        searchMode.setPlaceholder(placeholder);
        searchMode.setOnSearchEnter(this::performSearch);
        return searchMode;
    }

    private void refresh() {
        if ("discover".equals(mode)) {
            loadDiscover(true);
        } else if ("installed".equals(mode)) {
            installedBundleUpdates.clear();
            installedBundleUpdateChecks.clear();
            installedBundleUpdateChecked.clear();
            rebuildInstalled();
        } else {
            loadAssets();
            rebuildBundle();
        }
    }

    private void loadDiscover(boolean refresh) {
        if (ReStudio.getInstance().isAdmin()) {
            loadControlDiscover();
            return;
        }
        mode = "discover";
        discoverLoading = true;
        setLoading(true);
        long requestSeq = ++discoverRequestSeq;
        String requestedQuery = query;
        discoverContainer.clearWidgets();
        MarketplaceService.getInstance().browse(MARKETPLACE, "ALL", requestedQuery, 0, 50, refresh).thenAccept(page -> ScreenManager.getInstance().execute(() -> {
            if (requestSeq != discoverRequestSeq) {
                return;
            }
            discoverLoading = false;
            setLoading(false);
            discoverContainer.clearWidgets();
            if (page == null || page.data == null || page.data.isEmpty()) {
                loadControlDiscoverFallback();
                return;
            }
            for (MarketplaceModels.Listing listing : page.data) {
                discoverContainer.addWidget(new MarketplaceListingWidget(widgetWidth(discoverContainer), listing, () -> openDetails(listing)));
            }
        })).exceptionally(error -> {
            ScreenManager.getInstance().execute(() -> {
                if (requestSeq != discoverRequestSeq) {
                    return;
                }
                discoverLoading = false;
                setLoading(false);
                discoverContainer.clearWidgets();
            });
            return null;
        });
    }

    private void loadControlDiscoverFallback() {
        if (!ReStudio.getInstance().isAuthenticated()) {
            discoverContainer.clearWidgets();
            return;
        }
        loadControlDiscover();
    }

    private void loadControlDiscover() {
        mode = "discover";
        discoverLoading = true;
        setLoading(true);
        long requestSeq = ++discoverRequestSeq;
        String requestedQuery = query;
        discoverContainer.clearWidgets();
        MarketplaceService.getInstance().browseControl(MARKETPLACE, "ALL", "ALL", requestedQuery, 0, 50).thenAccept(page -> ScreenManager.getInstance().execute(() -> {
            if (requestSeq != discoverRequestSeq) {
                return;
            }
            discoverLoading = false;
            setLoading(false);
            discoverContainer.clearWidgets();
            if (page == null || page.data == null || page.data.isEmpty()) {
                return;
            }
            for (MarketplaceModels.Listing listing : page.data) {
                discoverContainer.addWidget(new MarketplaceListingWidget(widgetWidth(discoverContainer), listing, () -> openControlDetails(listing), true));
            }
        })).exceptionally(error -> {
            ScreenManager.getInstance().execute(() -> {
                if (requestSeq != discoverRequestSeq) {
                    return;
                }
                discoverLoading = false;
                setLoading(false);
                discoverContainer.clearWidgets();
            });
            return null;
        });
    }

    private void rebuildBundle() {
        mode = "bundle";
        resolveDependencies();
        bundleContainer.clearWidgets();
        bundleInfoWidget = new InfoWidget(bundleWidgetWidth(), selectedKeys.size() + " Selected", dependencyKeys.size() + " Dependencies");
        bundleContainer.addWidget(bundleInfoWidget);
        Map<String, List<AssetEntry>> groups = groupedAssets();
        bundleSearchEmpty = groups.isEmpty();
        if (groups.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<AssetEntry>> group : groups.entrySet()) {
            Setting.Builder builder = new Setting.Builder(group.getKey());
            for (AssetEntry asset : group.getValue()) {
                builder.addRow(new PopupWidget.PopupRow.Builder("", createAssetWidget(asset)).contentWidth().build());
            }
            bundleContainer.addWidget(builder.build());
        }
    }

    private Map<String, List<AssetEntry>> groupedAssets() {
        Map<String, List<AssetEntry>> groups = new LinkedHashMap<>();
        for (AssetEntry asset : assets.values()) {
            if (!matchesBundleQuery(asset)) {
                continue;
            }
            groups.computeIfAbsent(asset.groupName(), ignored -> new ArrayList<>()).add(asset);
        }
        return groups;
    }

    private boolean matchesBundleQuery(AssetEntry asset) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(asset.name, normalizedQuery)
                || containsIgnoreCase(asset.id, normalizedQuery)
                || containsIgnoreCase(asset.typeName(), normalizedQuery)
                || containsIgnoreCase(asset.groupName(), normalizedQuery);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private void rebuildInstalled() {
        mode = "installed";
        installedContainer.clearWidgets();
        ReSyncProjectMetadata metadata = projectMetadata();
        List<ReSyncProjectMetadata.InstalledBundleEntry> bundles = metadata == null
                ? List.of()
                : metadata.getInstalledBundles().stream().filter(this::matchesInstalledQuery).toList();
        installedSearchEmpty = bundles.isEmpty();
        if (bundles.isEmpty()) {
            return;
        }
        for (ReSyncProjectMetadata.InstalledBundleEntry bundle : bundles) {
            installedContainer.addWidget(createInstalledBundleWidget(bundle));
        }
    }

    private ReSyncProjectMetadata projectMetadata() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null || serverId.isBlank()) {
            return null;
        }
        return manager.getProjectMetadata(serverId);
    }

    private boolean matchesInstalledQuery(ReSyncProjectMetadata.InstalledBundleEntry bundle) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(bundle.getTitle(), normalizedQuery)
                || containsIgnoreCase(bundle.getListingSlug(), normalizedQuery)
                || containsIgnoreCase(bundle.getMarketplaceSlug(), normalizedQuery)
                || containsIgnoreCase(bundle.getVersion(), normalizedQuery);
    }

    private ResourceWidget<ReSyncProjectMetadata.InstalledBundleEntry> createInstalledBundleWidget(ReSyncProjectMetadata.InstalledBundleEntry bundle) {
        AtomicReference<ResourceWidget<ReSyncProjectMetadata.InstalledBundleEntry>> widgetRef = new AtomicReference<>();
        ResourceWidget<ReSyncProjectMetadata.InstalledBundleEntry> widget = new ResourceWidget<>(bundle, new ResourceWidget.ResourceAdapter<>() {
            @Override
            public String name(ReSyncProjectMetadata.InstalledBundleEntry resource) {
                return resource.getTitle().isBlank() ? resource.getListingSlug() : resource.getTitle();
            }

            @Override
            public String description(ReSyncProjectMetadata.InstalledBundleEntry resource) {
                String state = resource.isEnabled() ? "Enabled" : "Disabled";
                return state + " | " + resource.getRootPath();
            }

            @Override
            public String version(ReSyncProjectMetadata.InstalledBundleEntry resource) {
                return resource.getVersion().isBlank() ? "Unknown" : resource.getVersion();
            }

            @Override
            public String author(ReSyncProjectMetadata.InstalledBundleEntry resource) {
                return "Marketplace";
            }

            @Override
            public Identifier iconId(ReSyncProjectMetadata.InstalledBundleEntry resource) {
                return iconForInstalledBundle(resource, widgetRef.get());
            }

            @Override
            public boolean enabled(ReSyncProjectMetadata.InstalledBundleEntry resource) {
                return resource.isEnabled();
            }

            @Override
            public boolean updateAvailable(ReSyncProjectMetadata.InstalledBundleEntry resource) {
                return installedBundleUpdates.containsKey(resource.key());
            }

            @Override
            public void toggle(ReSyncProjectMetadata.InstalledBundleEntry resource, ToggleWidget toggle, ResourceWidget.RenderingMode renderingMode) {
                if (toggle.getValue() != resource.isEnabled()) {
                    toggleInstalledBundle(resource);
                }
            }

            @Override
            public void update(ReSyncProjectMetadata.InstalledBundleEntry resource) {
                updateInstalledBundle(resource);
            }

            @Override
            public void open(ReSyncProjectMetadata.InstalledBundleEntry resource, int button) {
                openInstalledBundle(resource);
            }
        });
        widget.setHeight(30);
        widget.addMountedWidget(new SquareButtonWidget.Builder()
                .imagePath("delete.png")
                .hint("Delete")
                .accentType(ThemeManager.getAccent("danger"))
                .size(18, 18)
                .entranceAnimation(false)
                .onClick(() -> deleteInstalledBundle(bundle))
                .build());
        widgetRef.set(widget);
        checkInstalledBundleUpdate(bundle, widget);
        widget.refresh();
        return widget;
    }

    private void checkInstalledBundleUpdate(ReSyncProjectMetadata.InstalledBundleEntry bundle, ResourceWidget<ReSyncProjectMetadata.InstalledBundleEntry> widget) {
        String key = bundle.key();
        if (key.isBlank() || bundle.getMarketplaceSlug().isBlank() || bundle.getListingSlug().isBlank() || installedBundleUpdates.containsKey(key)
                || installedBundleUpdateChecked.contains(key) || !installedBundleUpdateChecks.add(key)) {
            return;
        }
        MarketplaceService.getInstance().getVersions(bundle.getMarketplaceSlug(), bundle.getListingSlug()).thenAccept(versions -> {
            ScreenManager.getInstance().execute(() -> {
                installedBundleUpdateChecks.remove(key);
                installedBundleUpdateChecked.add(key);
                MarketplaceModels.Version latest = latestApprovedVersion(versions);
                if (isInstalledBundleUpdate(bundle, latest)) {
                    installedBundleUpdates.put(key, latest);
                } else {
                    installedBundleUpdates.remove(key);
                }
                widget.refresh();
            });
        }).exceptionally(error -> {
            ScreenManager.getInstance().execute(() -> {
                installedBundleUpdateChecks.remove(key);
                installedBundleUpdateChecked.add(key);
                installedBundleUpdates.remove(key);
                widget.refresh();
            });
            return null;
        });
    }

    private boolean isInstalledBundleUpdate(ReSyncProjectMetadata.InstalledBundleEntry bundle, MarketplaceModels.Version latest) {
        if (latest == null || latest.id == null || latest.id.isBlank()) {
            return false;
        }
        if (!bundle.getVersionId().isBlank()) {
            return !bundle.getVersionId().equals(latest.id);
        }
        return !bundle.getVersion().isBlank() && latest.version != null && !bundle.getVersion().equals(latest.version);
    }

    private Identifier iconForInstalledBundle(ReSyncProjectMetadata.InstalledBundleEntry bundle, ResourceWidget<ReSyncProjectMetadata.InstalledBundleEntry> widget) {
        String listingSlug = bundle.getListingSlug();
        if (listingSlug.isBlank()) {
            return Identifier.icon("download.png");
        }
        Identifier cached = installedBundleIcons.get(listingSlug);
        if (cached != null) {
            return cached;
        }
        CacheManager cacheManager = Rebase.get().getCacheManager();
        Path iconPath = cacheManager.getIconPath("ReSyncMarketplace", listingSlug);
        if (Files.exists(iconPath)) {
            Identifier id = loadImageId(iconPath);
            if (id != null) {
                installedBundleIcons.put(listingSlug, id);
                return id;
            }
        }
        String iconMediaId = bundle.getIconMediaId();
        if (widget != null && installedBundleIconLoads.add(listingSlug)) {
            if (!iconMediaId.isBlank()) {
                cacheInstalledBundleIcon(listingSlug, iconMediaId, iconPath, widget);
            } else if (!bundle.getMarketplaceSlug().isBlank()) {
                MarketplaceService.getInstance().getListing(bundle.getMarketplaceSlug(), listingSlug, true).thenAccept(listing -> {
                    if (listing != null && listing.iconMediaId != null && !listing.iconMediaId.isBlank()) {
                        rememberInstalledBundleIconMedia(bundle, listing.iconMediaId);
                        cacheInstalledBundleIcon(listingSlug, listing.iconMediaId, iconPath, widget);
                    } else {
                        installedBundleIconLoads.remove(listingSlug);
                    }
                }).exceptionally(error -> {
                    installedBundleIconLoads.remove(listingSlug);
                    return null;
                });
            } else {
                installedBundleIconLoads.remove(listingSlug);
            }
        }
        return Identifier.icon("download.png");
    }

    private void cacheInstalledBundleIcon(String listingSlug, String iconMediaId, Path iconPath, ResourceWidget<ReSyncProjectMetadata.InstalledBundleEntry> widget) {
        String url = ReStudio.getInstance().getApi().getMediaDownloadUrl(iconMediaId);
        Rebase.get().getCacheManager().getOrFetchImageId(url, iconPath).thenAccept(iconId -> {
            if (iconId != null) {
                installedBundleIcons.put(listingSlug, iconId);
                ScreenManager.getInstance().execute(widget::refresh);
            }
        }).whenComplete((value, error) -> installedBundleIconLoads.remove(listingSlug));
    }

    private String safeIconKey(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void rememberInstalledBundleIconMedia(ReSyncProjectMetadata.InstalledBundleEntry bundle, String iconMediaId) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        ReSyncProjectMetadata.InstalledBundleEntry stored = metadata.findInstalledBundle(bundle.getMarketplaceSlug(), bundle.getListingSlug());
        if (stored != null) {
            stored.setIconMediaId(iconMediaId);
            manager.saveProjectMetadata(serverId, metadata);
        }
    }

    private void openInstalledBundle(ReSyncProjectMetadata.InstalledBundleEntry bundle) {
        if (bundle != null && !bundle.getMarketplaceSlug().isBlank() && !bundle.getListingSlug().isBlank()) {
            ScreenManager.getInstance().setScreen(new MarketplaceDetailsScreen(this, bundle.getMarketplaceSlug(), bundle.getListingSlug()));
        }
    }

    private void toggleInstalledBundle(ReSyncProjectMetadata.InstalledBundleEntry bundle) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || bundle == null) {
            return;
        }
        boolean enabled = !bundle.isEnabled();
        manager.setMarketplaceBundleEnabled(serverId, bundle, enabled);
        rebuildInstalled();
        new Notification(enabled ? "Bundle Enabled" : "Bundle Disabled", bundle.getTitle(), Notification.Type.INFO);
    }

    private void deleteInstalledBundle(ReSyncProjectMetadata.InstalledBundleEntry bundle) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || bundle == null) {
            return;
        }
        manager.deleteMarketplaceBundle(serverId, bundle);
        installedBundleUpdates.remove(bundle.key());
        installedBundleUpdateChecks.remove(bundle.key());
        installedBundleUpdateChecked.remove(bundle.key());
        rebuildInstalled();
        new Notification("Bundle Deleted", bundle.getTitle(), Notification.Type.SUCCESS);
    }

    private void updateInstalledBundle(ReSyncProjectMetadata.InstalledBundleEntry bundle) {
        if (bundle == null || bundle.getMarketplaceSlug().isBlank() || bundle.getListingSlug().isBlank()) {
            return;
        }
        setLoading(true);
        MarketplaceService service = MarketplaceService.getInstance();
        service.getListing(bundle.getMarketplaceSlug(), bundle.getListingSlug(), true)
                .thenCompose(listing -> service.getVersions(bundle.getMarketplaceSlug(), bundle.getListingSlug()).thenApply(versions -> new MarketplaceUpdate(listing, latestApprovedVersion(versions))))
                .thenAccept(update -> ScreenManager.getInstance().execute(() -> applyInstalledBundleUpdate(bundle, update)))
                .exceptionally(error -> {
                    ScreenManager.getInstance().execute(() -> {
                        setLoading(false);
                        new Notification("Update Failed", "Could Not Load Bundle", Notification.Type.ERROR);
                    });
                    return null;
                });
    }

    private void applyInstalledBundleUpdate(ReSyncProjectMetadata.InstalledBundleEntry bundle, MarketplaceUpdate update) {
        setLoading(false);
        if (update == null || update.listing == null || update.version == null) {
            new Notification("Update Failed", "No Version Found", Notification.Type.WARN);
            return;
        }
        if (!bundle.getVersionId().isBlank() && bundle.getVersionId().equals(update.version.id)) {
            new Notification("Up To Date", bundle.getTitle(), Notification.Type.INFO);
            return;
        }
        String payload = extractPayloadJson(update.version.metadataJson);
        if (payload == null) {
            payload = extractPayloadJson(update.listing.metadataJson);
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || payload == null || !manager.installMarketplaceBundle(serverId, update.listing, update.version, payload)) {
            new Notification("Update Failed", "Content Was Not Applied", Notification.Type.ERROR);
            return;
        }
        installedBundleUpdates.remove(bundle.key());
        installedBundleUpdateChecks.remove(bundle.key());
        installedBundleUpdateChecked.remove(bundle.key());
        rebuildInstalled();
        new Notification("Bundle Updated", update.listing.title, Notification.Type.SUCCESS);
    }

    private MarketplaceModels.Version latestApprovedVersion(List<MarketplaceModels.Version> versions) {
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        MarketplaceModels.Version latest = null;
        for (MarketplaceModels.Version version : versions) {
            if (version != null && "APPROVED".equals(version.status) && (latest == null || versionDate(version).compareTo(versionDate(latest)) > 0)) {
                latest = version;
            }
        }
        return latest != null ? latest : versions.getFirst();
    }

    private String versionDate(MarketplaceModels.Version version) {
        if (version == null) {
            return "";
        }
        if (version.publishedAt != null && !version.publishedAt.isBlank()) {
            return version.publishedAt;
        }
        return version.createdAt != null ? version.createdAt : "";
    }

    private String extractPayloadJson(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonObject object = gson.fromJson(metadataJson, JsonObject.class);
            if (object != null && object.has("payloadJson")) {
                return object.get("payloadJson").getAsString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void openDetails(MarketplaceModels.Listing listing) {
        if (listing != null) {
            ScreenManager.getInstance().setScreen(new MarketplaceDetailsScreen(this, listing.marketplaceSlug, listing.slug));
        }
    }

    private void openControlDetails(MarketplaceModels.Listing listing) {
        if (listing != null) {
            ScreenManager.getInstance().setScreen(new MarketplaceDetailsScreen(this, listing.marketplaceSlug, listing.slug, true));
        }
    }

    private void loadAssets() {
        assets = new LinkedHashMap<>();
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        for (ReSyncProjectMetadata.ResourceEntry resource : metadata.getResources()) {
            if (resource == null || resource.getId() == null || resource.getId().isBlank()) {
                continue;
            }
            String type = resource.getType();
            if (isPublishable(type)) {
                addAsset(manager, type, resource.getId(), resource.getDisplayName());
            }
        }
        for (TriggerBinding binding : manager.getBindings(serverId)) {
            if (binding != null && binding.getType() == TriggerType.COMMAND && binding.getFlowId() != null && !binding.getFlowId().isBlank()) {
                addAsset(manager, ReSyncResourceDragPayload.COMMAND, binding.getFlowId(), binding.getContext() == null || binding.getContext().isBlank() ? binding.getFlowId() : binding.getContext());
            }
        }
    }

    private void addAsset(FlowManager manager, String type, String id, String name) {
        AssetEntry asset = new AssetEntry(type, id, name == null || name.isBlank() ? id : name, iconFor(manager, type, id));
        assets.put(asset.key(), asset);
    }

    private String iconFor(FlowManager manager, String type, String id) {
        if (ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(type)) {
            CustomContentDefinition content = manager.getCustomContentForServer(serverId).get(id);
            String contentType = content != null && content.getType() != null ? content.getType().toLowerCase(Locale.ROOT) : "";
            return switch (contentType) {
                case "armor" -> "armor.png";
                case "block" -> "block.png";
                default -> "item.png";
            };
        }
        return switch (type) {
            case ReSyncResourceDragPayload.FUNCTION -> "snippets.png";
            case ReSyncResourceDragPayload.COMMAND -> "terminal.png";
            case ReSyncResourceDragPayload.GUI -> "fullPanel.png";
            case ReSyncResourceDragPayload.SCOREBOARD -> "panel.png";
            case ReSyncResourceDragPayload.TAB -> "topPanel.png";
            case ReSyncResourceDragPayload.DIALOG -> "VanillaButton.png";
            case ReSyncResourceDragPayload.TRADE_PROFILE -> "trade.png";
            case ReSyncResourceDragPayload.NPC_DEFINITION -> "steve.png";
            case ReSyncResourceDragPayload.LOOT_TABLE -> "resources.png";
            default -> "graph.png";
        };
    }

    private boolean isPublishable(String type) {
        return ReSyncResourceDragPayload.FLOW.equals(type)
                || ReSyncResourceDragPayload.FUNCTION.equals(type)
                || ReSyncResourceDragPayload.COMMAND.equals(type)
                || ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(type)
                || ReSyncResourceDragPayload.GUI.equals(type)
                || ReSyncResourceDragPayload.SCOREBOARD.equals(type)
                || ReSyncResourceDragPayload.TAB.equals(type)
                || ReSyncResourceDragPayload.DIALOG.equals(type)
                || ReSyncResourceDragPayload.TRADE_PROFILE.equals(type)
                || ReSyncResourceDragPayload.NPC_DEFINITION.equals(type)
                || ReSyncResourceDragPayload.LOOT_TABLE.equals(type);
    }

    private void toggleAsset(String key) {
        if (dependencyKeys.contains(key) && !selectedKeys.contains(key)) {
            selectedKeys.add(key);
        } else if (selectedKeys.contains(key)) {
            selectedKeys.remove(key);
        } else {
            selectedKeys.add(key);
        }
        resolveDependencies();
        refreshAssetSelectionStates();
    }

    private AssetWidget createAssetWidget(AssetEntry asset) {
        return new AssetWidget(bundleWidgetWidth(), asset, selectedKeys.contains(asset.key()), dependencyKeys.contains(asset.key()), () -> toggleAsset(asset.key()));
    }

    private void refreshAssetSelectionStates() {
        if (bundleInfoWidget != null) {
            bundleInfoWidget.setText(selectedKeys.size() + " Selected", dependencyKeys.size() + " Dependencies");
        }
        for (AnimatedWidget widget : bundleContainer.getWidgets()) {
            refreshAssetSelectionState(widget);
        }
    }

    private void refreshAssetSelectionState(Widget widget) {
        if (widget instanceof AssetWidget assetWidget) {
            String key = assetWidget.assetKey();
            assetWidget.setSelectionState(selectedKeys.contains(key), dependencyKeys.contains(key));
            return;
        }
        if (widget instanceof Setting setting) {
            for (PopupWidget.PopupRow row : setting.getRows()) {
                for (Widget child : row.getWidgets()) {
                    refreshAssetSelectionState(child);
                }
            }
        }
    }

    private void resolveDependencies() {
        dependencyKeys.clear();
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        Map<String, List<String>> idIndex = buildIdIndex();
        Set<String> resolved = new HashSet<>(selectedKeys);
        ArrayDeque<String> queue = new ArrayDeque<>(selectedKeys);
        while (!queue.isEmpty()) {
            String key = queue.removeFirst();
            AssetEntry asset = assets.get(key);
            if (asset == null) {
                continue;
            }
            Set<String> found = dependenciesFor(manager, asset, idIndex);
            for (String dependency : found) {
                if (assets.containsKey(dependency) && resolved.add(dependency)) {
                    dependencyKeys.add(dependency);
                    queue.add(dependency);
                }
            }
        }
        dependencyKeys.removeAll(selectedKeys);
    }

    private Map<String, List<String>> buildIdIndex() {
        Map<String, List<String>> index = new HashMap<>();
        for (AssetEntry asset : assets.values()) {
            index.computeIfAbsent(asset.id, ignored -> new ArrayList<>()).add(asset.key());
        }
        return index;
    }

    private Set<String> dependenciesFor(FlowManager manager, AssetEntry asset, Map<String, List<String>> idIndex) {
        Set<String> result = new HashSet<>();
        switch (asset.type) {
            case ReSyncResourceDragPayload.COMMAND, ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION ->
                collectGraphDependencies(manager.getGraph(serverId, ReSyncResourceType.byTypeId(asset.type), asset.id), idIndex, result);
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> {
                CustomContentDefinition contentDefinition = manager.getCustomContentForServer(serverId).get(asset.id);
                if (contentDefinition != null) {
                    includeId(contentDefinition.getFlowId(), idIndex, result);
                    collectGraphDependencies(contentDefinition.getGraph(), idIndex, result);
                }
            }
            case ReSyncResourceDragPayload.GUI -> collectGuiDependencies(manager.getGuisForServer(serverId).get(asset.id), idIndex, result);
            case ReSyncResourceDragPayload.SCOREBOARD -> collectJsonDependencies(gson.toJsonTree(manager.getScoreboardsForServer(serverId).get(asset.id)), idIndex, result);
            case ReSyncResourceDragPayload.TAB -> collectJsonDependencies(gson.toJsonTree(manager.getTabsForServer(serverId).get(asset.id)), idIndex, result);
            case ReSyncResourceDragPayload.DIALOG, ReSyncResourceDragPayload.TRADE_PROFILE, ReSyncResourceDragPayload.NPC_DEFINITION, ReSyncResourceDragPayload.LOOT_TABLE -> {
                ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(asset.type);
                if (resourceType != null) {
                    collectJsonDependencies(manager.getJsonResourcesForServer(serverId, resourceType).get(asset.id), idIndex, result);
                }
            }
            default -> {
            }
        }
        result.remove(asset.key());
        return result;
    }

    private void collectGuiDependencies(GuiDefinition gui, Map<String, List<String>> idIndex, Set<String> result) {
        if (gui == null || gui.getElements() == null) {
            return;
        }
        for (GuiElement element : gui.getElements()) {
            if (element == null) {
                continue;
            }
            includeId(element.getFlowId(), idIndex, result);
            includeId(element.getOpenGuiId(), idIndex, result);
            includeId(element.getCommand(), idIndex, result);
            if (element.getVisual() != null) {
                includeId(element.getVisual().getPresetReference(), idIndex, result);
            }
        }
        collectJsonDependencies(gson.toJsonTree(gui), idIndex, result);
    }

    private void collectGraphDependencies(FlowGraph graph, Map<String, List<String>> idIndex, Set<String> result) {
        if (graph == null) {
            return;
        }
        if (graph.getNodes() != null) {
            for (FlowNode node : graph.getNodes().values()) {
                if (node == null) {
                    continue;
                }
                String nodeType = node.getType();
                if (nodeType != null && nodeType.startsWith(CUSTOM_FUNCTION_PREFIX)) {
                    includeId(nodeType.substring(CUSTOM_FUNCTION_PREFIX.length()), idIndex, result);
                }
                collectJsonDependencies(gson.toJsonTree(node.getInputValues()), idIndex, result);
            }
        }
        collectJsonDependencies(gson.toJsonTree(graph), idIndex, result);
    }

    private void collectJsonDependencies(JsonElement element, Map<String, List<String>> idIndex, Set<String> result) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            includeId(element.getAsString(), idIndex, result);
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectJsonDependencies(child, idIndex, result);
            }
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> child : element.getAsJsonObject().entrySet()) {
                collectJsonDependencies(child.getValue(), idIndex, result);
            }
        }
    }

    private void includeId(String id, Map<String, List<String>> idIndex, Set<String> result) {
        if (id == null || id.isBlank()) {
            return;
        }
        List<String> keys = idIndex.get(id);
        if (keys != null) {
            result.addAll(keys);
        }
    }

    private void publishBundle() {
        if (!"bundle".equals(mode)) {
            tabsManager.setActiveTab(1);
            setActiveContainer(bundleContainer);
            rebuildBundle();
            return;
        }
        if (publishing) {
            return;
        }
        if (!ReStudio.getInstance().isAuthenticated()) {
            new Notification("Login Required", "Login To Publish", Notification.Type.WARN);
            return;
        }
        resolveDependencies();
        if (selectedKeys.isEmpty()) {
            new Notification("Bundle Empty", "Select Assets First", Notification.Type.WARN);
            return;
        }
        showPublishPopup();
    }

    private void showPublishPopup() {
        TextInputWidget titleInput = new TextInputWidget.Builder().placeholder("Bundle Title").size(220, 20).build();
        TextInputWidget summaryInput = new TextInputWidget.Builder().placeholder("Short Summary").size(300, 20).build();
        TextAreaWidget descriptionInput = new TextAreaWidget.Builder()
                .placeholder("Markdown Description")
                .wordWrap(true)
                .markdownImagePreview(true)
                .markdownImageUploadTarget(MARKETPLACE, "marketplace-description-image")
                .build();
        TextInputWidget versionInput = new TextInputWidget.Builder().text("1.0.0").placeholder("Version").size(120, 20).build();
        DropDownWidget<String> channelDropdown = new DropDownWidget.Builder<>(List.of("stable", "beta", "alpha"))
                .selectedItem("stable")
                .size(120, 20)
                .build();
        PublishBundleData publishData = new PublishBundleData();
        loadPublishBundleData(publishData);
        DropDownWidget<String> tagsDropdown = new DropDownWidget.Builder<>(BUNDLE_TAGS)
                .multiSelect(true)
                .size(220, 20)
                .maxVisibleItems(8)
                .build();
        TextInputWidget imageInput = new TextInputWidget.Builder().placeholder("Project Image").size(260, 20).build();
        Path[] imagePath = new Path[1];
        IconButton imageButton = new IconButton.Builder()
                .label("Image")
                .imagePath("upload.png")
                .size(90, 20)
                .onClick(() -> FileUtils.pickImageFileAsync("Select Project Image", path -> ScreenManager.getInstance().execute(() -> {
                    if (path != null) {
                        imagePath[0] = path;
                        imageInput.setText(path.toString());
                    }
                })))
                .build();
        PopupWidget.Builder builder = new PopupWidget.Builder("Publish Bundle").setResizable(false).setAntiOutOfBound(true);
        builder.addRow("Title", titleInput);
        builder.addRow("Summary", summaryInput);
        builder.addRow(new PopupWidget.PopupRow.Builder("Description", descriptionInput).minHeight(100).build());
        builder.addRow("Version", versionInput);
        builder.addRow("Channel", channelDropdown);
        builder.addRow("Tags", tagsDropdown);
        builder.addRow("Image", imageInput, imageButton);
        PopupWidget[] popupRef = new PopupWidget[1];
        builder.addTitleAction("Continue", () -> showPublishDataPopup(publishData, () -> submitBundle(titleInput.getText(), summaryInput.getText(), descriptionInput.getText(), tagsText(tagsDropdown), versionInput.getText(), channelDropdown.getSelectedItem(), publishData, resolveImagePath(imagePath[0], imageInput.getText()), popupRef[0])), PopupWidget.TitleActionRole.PRIMARY);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showPublishDataPopup(PublishBundleData data, Runnable submitAction) {
        DropDownWidget<String> minecraftDropdown = new DropDownWidget.Builder<>(data.availableMinecraftVersions)
                .multiSelect(true)
                .size(220, 20)
                .maxVisibleItems(8)
                .build();
        DropDownWidget<String> resyncDropdown = new DropDownWidget.Builder<>(data.availableReSyncVersions)
                .multiSelect(true)
                .size(220, 20)
                .maxVisibleItems(8)
                .build();
        minecraftDropdown.setSelectedItems(data.selectedMinecraftVersions, List.of());
        resyncDropdown.setSelectedItems(data.selectedReSyncVersions, List.of());
        PopupWidget.Builder builder = new PopupWidget.Builder("Bundle Data").setResizable(false).setExpandWithDropdowns(true).setAntiOutOfBound(true);
        builder.addRow("Minecraft", minecraftDropdown);
        builder.addRow("ReSync", resyncDropdown);
        PopupWidget[] popupRef = new PopupWidget[1];
        builder.addTitleAction("Save", () -> {
            data.selectedMinecraftVersions = minecraftDropdown.getSelectedItems();
            data.selectedReSyncVersions = resyncDropdown.getSelectedItems();
            if (popupRef[0] != null) {
                popupRef[0].hide();
            }
        }, PopupWidget.TitleActionRole.SECONDARY);
        builder.addTitleAction("Publish", () -> {
                    data.selectedMinecraftVersions = minecraftDropdown.getSelectedItems();
                    data.selectedReSyncVersions = resyncDropdown.getSelectedItems();
                    if (submitAction != null) {
                        submitAction.run();
                    }
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
        }, PopupWidget.TitleActionRole.PRIMARY);
        PopupWidget popup = builder.build();
        popupRef[0] = popup;
        addDrawableChild(popup);
        popup.show();
    }

    private void loadPublishBundleData(PublishBundleData data) {
        data.availableMinecraftVersions = Rebase.get().getLocalBaseVersions().stream()
                .filter(version -> version != null && "RELEASE".equalsIgnoreCase(version.getType()))
                .sorted(Comparator.comparing(GameVersion::getReleaseTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(GameVersion::getId)
                .toList();
        if (!data.availableMinecraftVersions.isEmpty()) {
            data.availableMinecraftVersions = new ArrayList<>(data.availableMinecraftVersions);
            data.selectedMinecraftVersions = List.of(data.availableMinecraftVersions.getFirst());
        }
        ReStudio.getInstance().getApi().getReSyncReleases().thenAccept(releases -> ScreenManager.getInstance().execute(() -> {
            data.availableReSyncVersions = releases == null ? new ArrayList<>() : releases.stream()
                    .filter(release -> release != null && release.version != null && !release.version.isBlank())
                    .sorted(Comparator.comparing((ReleaseModels.Release release) -> release.createdAt == null ? "" : release.createdAt).reversed())
                    .map(release -> release.version)
                    .distinct()
                    .toList();
            if (!data.availableReSyncVersions.isEmpty()) {
                data.availableReSyncVersions = new ArrayList<>(data.availableReSyncVersions);
                data.selectedReSyncVersions = List.of(data.availableReSyncVersions.getFirst());
            }
        })).exceptionally(error -> null);
    }

    private String tagsText(DropDownWidget<String> tagsDropdown) {
        if (tagsDropdown == null || tagsDropdown.getSelectedItems().isEmpty()) {
            return "";
        }
        return String.join(",", tagsDropdown.getSelectedItems());
    }

    private String compatibilityJson(PublishBundleData data) {
        JsonObject object = new JsonObject();
        object.add("minecraftVersions", stringArray(data == null ? List.of() : data.selectedMinecraftVersions));
        object.add("resyncVersions", stringArray(data == null ? List.of() : data.selectedReSyncVersions));
        return gson.toJson(object);
    }

    private JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    array.add(value);
                }
            }
        }
        return array;
    }

    private void submitBundle(String rawTitle, String rawSummary, String rawDescription, String rawTags, String rawVersion, String channel, PublishBundleData publishData, Path imagePath, PopupWidget popup) {
        if (publishing) {
            return;
        }
        String title = rawTitle == null ? "" : rawTitle.trim();
        String summary = rawSummary == null ? "" : rawSummary.trim();
        String version = rawVersion == null ? "" : rawVersion.trim();
        if (title.isBlank() || summary.isBlank()) {
            new Notification("Missing Fields", "Title And Summary Required", Notification.Type.WARN);
            return;
        }
        if (version.isBlank()) {
            new Notification("Missing Version", "Version Required", Notification.Type.WARN);
            return;
        }
        String description = rawDescription == null || rawDescription.isBlank() ? summary : rawDescription.trim();
        String slug = toUniqueSlug(title);
        JsonObject bundle = buildBundle(title);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("schemaVersion", "1");
        metadata.addProperty("contentType", "bundle");
        metadata.addProperty("payloadFormat", "json");
        metadata.addProperty("payloadJson", gson.toJson(bundle));

        publishing = true;
        if (popup != null) {
            popup.hide();
        }
        CompletableFuture<MarketplaceModels.MediaAsset> imageFuture = imagePath == null
                ? CompletableFuture.completedFuture(null)
                : ReStudio.getInstance().getApi().uploadMedia(MARKETPLACE, "marketplace-image", null, "PUBLIC", imagePath);
        imageFuture.thenCompose(image -> {
                    MarketplaceModels.ListingRequest request = new MarketplaceModels.ListingRequest();
                    request.slug = slug;
                    request.title = title;
                    request.summary = summary;
                    request.description = description;
                    request.rights = "";
                    request.type = "RESYNC_CONTENT";
                    request.visibility = "PUBLIC";
                    request.tagsText = rawTags == null ? "" : rawTags.trim();
                    request.iconMediaId = image == null ? null : image.id;
                    request.metadataJson = metadata.toString();
                    return ReStudio.getInstance().getApi().createMarketplaceListing(MARKETPLACE, request).thenCompose(listing -> attachListingImage(listing, image));
                })
                .thenCompose(listing -> ReStudio.getInstance().getApi().submitMarketplaceListing(MARKETPLACE, listing.slug))
                .thenCompose(listing -> ReStudio.getInstance().getApi().createMarketplaceVersion(MARKETPLACE, listing.slug, version, channel, "server", null, description, compatibilityJson(publishData), metadata.toString())
                        .thenCompose(createdVersion -> ReStudio.getInstance().getApi().submitMarketplaceVersion(MARKETPLACE, listing.slug, createdVersion.id))
                        .thenApply(createdVersion -> listing))
                .thenAccept(listing -> ScreenManager.getInstance().execute(() -> {
                    publishing = false;
                    new Notification("Submitted", "Bundle Submitted", Notification.Type.SUCCESS);
                    ScreenManager.getInstance().setScreen(new MarketplaceDetailsScreen(parent, MARKETPLACE, listing.slug, true));
                })).exceptionally(error -> {
                    ScreenManager.getInstance().execute(() -> {
                        publishing = false;
                        new Notification("Submit Failed", errorMessage(error), Notification.Type.ERROR);
                    });
                    return null;
                });
    }

    private Path resolveImagePath(Path selectedPath, String typedPath) {
        if (selectedPath != null) {
            return selectedPath;
        }
        if (typedPath == null || typedPath.isBlank()) {
            return null;
        }
        return Path.of(typedPath.trim());
    }

    private CompletableFuture<MarketplaceModels.Listing> attachListingImage(MarketplaceModels.Listing listing, MarketplaceModels.MediaAsset image) {
        if (listing == null || listing.slug == null || image == null || image.id == null) {
            return CompletableFuture.completedFuture(listing);
        }
        return ReStudio.getInstance().getApi().addMarketplaceListingMedia(MARKETPLACE, listing.slug, image.id, "IMAGE", 0).thenApply(media -> listing);
    }

    private JsonObject buildBundle(String title) {
        FlowManager manager = FlowManager.getInstance();
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", "1");
        root.addProperty("bundleId", toSlug(title));
        root.addProperty("folderName", title);
        JsonArray array = new JsonArray();
        Set<String> included = new HashSet<>(selectedKeys);
        included.addAll(dependencyKeys);
        for (String key : included) {
            AssetEntry asset = assets.get(key);
            if (asset == null) {
                continue;
            }
            JsonElement payload = payloadFor(manager, asset);
            if (payload == null || payload.isJsonNull()) {
                continue;
            }
            JsonObject object = new JsonObject();
            object.addProperty("type", asset.type);
            object.addProperty("id", asset.id);
            object.addProperty("displayName", asset.name);
            object.addProperty("dependency", dependencyKeys.contains(key));
            object.add("payload", payload);
            array.add(object);
        }
        root.add("assets", array);
        return root;
    }

    private JsonElement payloadFor(FlowManager manager, AssetEntry asset) {
        if (manager == null || asset == null) {
            return null;
        }
        return switch (asset.type) {
            case ReSyncResourceDragPayload.COMMAND, ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION ->
                gson.toJsonTree(manager.getGraph(serverId, ReSyncResourceType.byTypeId(asset.type), asset.id));
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> gson.toJsonTree(manager.getCustomContentForServer(serverId).get(asset.id));
            case ReSyncResourceDragPayload.GUI -> gson.toJsonTree(manager.getGuisForServer(serverId).get(asset.id));
            case ReSyncResourceDragPayload.SCOREBOARD -> gson.toJsonTree(manager.getScoreboardsForServer(serverId).get(asset.id));
            case ReSyncResourceDragPayload.TAB -> gson.toJsonTree(manager.getTabsForServer(serverId).get(asset.id));
            case ReSyncResourceDragPayload.DIALOG, ReSyncResourceDragPayload.TRADE_PROFILE, ReSyncResourceDragPayload.NPC_DEFINITION, ReSyncResourceDragPayload.LOOT_TABLE -> {
                ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(asset.type);
                yield resourceType != null ? manager.getJsonResourcesForServer(serverId, resourceType).get(asset.id) : null;
            }
            default -> null;
        };
    }

    private String toSlug(String value) {
        String slug = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        if (slug.isBlank()) {
            slug = "bundle";
        }
        return slug.length() > 90 ? slug.substring(0, 90) : slug;
    }

    private String toUniqueSlug(String value) {
        String slug = toSlug(value);
        String suffix = "-" + Long.toString(System.currentTimeMillis(), 36);
        int maxBaseLength = Math.max(1, 90 - suffix.length());
        return (slug.length() > maxBaseLength ? slug.substring(0, maxBaseLength) : slug) + suffix;
    }

    private String errorMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        if (message == null || message.isBlank()) {
            return "Could Not Publish Bundle";
        }
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    private void closeScreen() {
        ScreenManager.getInstance().setScreen(parent);
    }

    private void onTabSwitch(TabsManager.Tab tab) {
        if (tab == null || tab.getData() == null) {
            return;
        }
        if ("bundle".equals(tab.getData())) {
            setActiveContainer(bundleContainer);
            rebuildBundle();
        } else if ("installed".equals(tab.getData())) {
            setActiveContainer(installedContainer);
            rebuildInstalled();
        } else {
            mode = "discover";
            setActiveContainer(discoverContainer);
            if (discoverContainer.getWidgets().isEmpty()) {
                loadDiscover(false);
            }
        }
    }

    private void performSearch(String value) {
        pendingQuery = value == null ? "" : value.trim();
        searchDebounceTime = System.currentTimeMillis();
    }

    private void checkDebouncedSearch() {
        if (pendingQuery != null && System.currentTimeMillis() - searchDebounceTime >= 300) {
            query = pendingQuery;
            pendingQuery = null;
            if ("bundle".equals(mode)) {
                rebuildBundle();
            } else if ("installed".equals(mode)) {
                rebuildInstalled();
            } else {
                loadDiscover(true);
            }
        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        checkDebouncedSearch();
        super.render(context, mouseX, mouseY, delta);
        boolean noResults = activeContainer == discoverContainer && discoverContainer.getWidgets().isEmpty() && !discoverLoading
                || activeContainer == bundleContainer && bundleSearchEmpty && !discoverLoading
                || activeContainer == installedContainer && installedSearchEmpty && !discoverLoading;
        if (noResults) {
            searchFailedMessage.setPosition((width - searchFailedMessage.getWidth()) / 2, (height - searchFailedMessage.getHeight()) / 2);
            if (!widgets.contains(searchFailedMessage)) {
                addDrawableChild(searchFailedMessage);
            }
        } else if (widgets.contains(searchFailedMessage)) {
            searchFailedMessage.resetEntranceAnimation();
            remove(searchFailedMessage);
        }
    }

    private int widgetWidth(Container container) {
        return Math.max(200, container.getEffectiveWidth() / 3 - 10);
    }

    private int bundleWidgetWidth() {
        return Math.max(220, bundleContainer.getEffectiveWidth() - 12);
    }

    private record AssetEntry(String type, String id, String name, String iconPath) {
        String key() {
            return ReSyncProjectMetadata.resourceKey(type, id);
        }

        String typeName() {
            return switch (type) {
                case ReSyncResourceDragPayload.FUNCTION -> "Function";
                case ReSyncResourceDragPayload.COMMAND -> "Command";
                case ReSyncResourceDragPayload.CUSTOM_CONTENT -> "Content";
                case ReSyncResourceDragPayload.GUI -> "GUI";
                case ReSyncResourceDragPayload.SCOREBOARD -> "Scoreboard";
                case ReSyncResourceDragPayload.TAB -> "Tab";
                case ReSyncResourceDragPayload.DIALOG -> "Dialog";
                case ReSyncResourceDragPayload.TRADE_PROFILE -> "Trade";
                case ReSyncResourceDragPayload.NPC_DEFINITION -> "NPC";
                case ReSyncResourceDragPayload.LOOT_TABLE -> "Loot Table";
                default -> "Flow";
            };
        }

        String groupName() {
            return switch (type) {
                case ReSyncResourceDragPayload.FUNCTION -> "Functions";
                case ReSyncResourceDragPayload.COMMAND -> "Commands";
                case ReSyncResourceDragPayload.CUSTOM_CONTENT -> "Content";
                case ReSyncResourceDragPayload.GUI -> "UIs";
                case ReSyncResourceDragPayload.SCOREBOARD -> "Scoreboards";
                case ReSyncResourceDragPayload.TAB -> "Tab Lists";
                case ReSyncResourceDragPayload.DIALOG -> "Dialogs";
                case ReSyncResourceDragPayload.TRADE_PROFILE -> "Trades";
                case ReSyncResourceDragPayload.NPC_DEFINITION -> "NPCs";
                case ReSyncResourceDragPayload.LOOT_TABLE -> "Loot Tables";
                default -> "Flows";
            };
        }
    }

    private record MarketplaceUpdate(MarketplaceModels.Listing listing, MarketplaceModels.Version version) {
    }

    private static class PublishBundleData {
        private List<String> availableMinecraftVersions = new ArrayList<>();
        private List<String> availableReSyncVersions = new ArrayList<>();
        private List<String> selectedMinecraftVersions = new ArrayList<>();
        private List<String> selectedReSyncVersions = new ArrayList<>();
    }

    private static class AssetWidget extends AnimatedWidget {
        private final AssetEntry asset;
        private boolean selected;
        private boolean dependency;
        private final Runnable onClick;
        private final Identifier iconId;

        AssetWidget(int width, AssetEntry asset, boolean selected, boolean dependency, Runnable onClick) {
            super(0, 0, width, 30, asset.name);
            this.asset = asset;
            this.selected = selected;
            this.dependency = dependency;
            this.onClick = onClick;
            this.iconId = Identifier.icon(asset.iconPath);
            animateElevation = true;
            setCursorHoverReactive(true);
        }

        String assetKey() {
            return asset.key();
        }

        void setSelectionState(boolean selected, boolean dependency) {
            this.selected = selected;
            this.dependency = dependency;
        }

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
            int iconSize = 20;
            int iconX = getX() + 6;
            int iconY = getY() + (getHeight() - iconSize) / 2;
            int textX = iconX + iconSize + 8;
            int stateWidth = tr.getWidth(selectionLabel()) + 8;
            int textWidth = Math.max(40, getWidth() - textX + getX() - stateWidth - 12);
            if (selected || dependency) {
                accentType = ThemeManager.getAccent(selected ? "calm" : "nice");
            } else {
                accentType = ThemeManager.getDefaultAccent();
            }
            if (iconId != null) {
                MinecraftUiPreviewRenderer.drawImage(ctx, iconId, iconX, iconY, iconSize, iconSize);
            } else {
                ctx.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0x80000000);
            }
            ctx.enableScissor(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1);
            ctx.drawText(trimToWidth(asset.name, textWidth), textX, getY() + 5, ThemeManager.getColor(ThemeColor.text), shadow);
            ctx.drawText(asset.typeName(), textX, getY() + 17, ThemeManager.getColor(ThemeColor.textDark), shadow);
            String state = selectionLabel();
            int stateX = getX() + getWidth() - tr.getWidth(state) - 8;
            ctx.drawText(state, stateX, getY() + 10, hovered ? ThemeManager.getColor(ThemeColor.text) : ThemeManager.getColor(ThemeColor.textDark), shadow);
            ctx.disableScissor();
        }

        private String selectionLabel() {
            return selected ? "Selected" : dependency ? "Dependency" : "Select";
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            if (button == 0 && onClick != null) {
                onClick.run();
            }
        }

        private static String trimToWidth(String value, int width) {
            String text = value == null || value.isBlank() ? "Asset" : value;
            if (tr.getWidth(text) <= width) {
                return text;
            }
            String result = text;
            while (result.length() > 3 && tr.getWidth(result + "...") > width) {
                result = result.substring(0, result.length() - 1);
            }
            return result + "...";
        }
    }

    private static class InfoWidget extends AnimatedWidget {
        private String title;
        private String summary;

        InfoWidget(int width, String title, String summary) {
            super(0, 0, width, 42, title);
            this.title = title;
            this.summary = summary;
            animateElevation = false;
        }

        void setText(String title, String summary) {
            this.title = title;
            this.summary = summary;
        }

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
            ctx.drawText(title, getX() + 6, getY() + 7, ThemeManager.getColor(ThemeColor.text), true);
            ctx.drawText(summary, getX() + 6, getY() + 24, ThemeManager.getColor(ThemeColor.textDark), true);
        }
    }

}
