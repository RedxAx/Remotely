package redxax.oxy.remotely.web.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import restudio.rebase.backend.CapabilityDescriptor;
import restudio.rebase.platform.Async;
import restudio.rebase.platform.TaskScheduler;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.resource.marketplace.ResourceBrowserContext;
import restudio.rebase.resource.marketplace.ResourceMarketplaceProvider;
import restudio.rebase.resource.marketplace.ResourceMarketplaceProviderAdapter;
import restudio.rebase.resource.provider.AsyncResourceProvider;
import restudio.rebase.resource.provider.OnlineResourceVersion;
import restudio.rebase.resource.provider.ResourceCompatibilityTokens;
import restudio.rebase.resource.provider.ResourceProviderException;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rebase.ui.screens.resources.ResourceOverviewScreen;
import restudio.rebase.ui.screens.resources.ResourceContainerCapabilities;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.util.Notification;
import redxax.oxy.remotely.config.RemotelyConfigStore;
import redxax.oxy.remotely.config.RemotelyGroup;
import redxax.oxy.remotely.ui.server.ServerUiCapabilityProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class BrowserResourceBrowserContext implements ResourceBrowserContext {
    private static final int RESOURCE_SEARCH_SCAN_PAGES = 2;
    private static final int RESOURCE_DETAILS_CONCURRENCY = 5;
    private static final Duration RESOURCE_METADATA_WAIT = Duration.ofMillis(650);
    private final ResourceMarketplaceProviderAdapter marketplace;
    private final BrowserRemotelyServerApi api;
    private final ServerUiCapabilityProvider serverCapabilities;
    private final ServerModels.ClientServerView server;
    private final String serverId;
    private final String version;
    private final String loader;
    private final Object remoteHost;
    private final RemotelyConfigStore configStore;
    private final Consumer<ModpackSelection> modpackSelection;
    private final List<Runnable> resourceListeners = new ArrayList<>();
    private final List<Consumer<BrowserResourceInventory>> inventoryListeners = new ArrayList<>();
    private final Map<String, BrowserRemotelyServerApi.HostedModpackCapabilities> modpackCapabilities = new LinkedHashMap<>();
    private final BrowserTaskScheduler scheduler = new BrowserTaskScheduler();
    private TaskScheduler.ScheduledTask pendingResourceNotification;
    private OperationFence pendingResourceNotificationFence;
    private boolean pendingLegacyResourceNotification;
    private TaskScheduler.ScheduledTask pendingResourceReconciliation;
    private OperationFence pendingResourceReconciliationFence;
    private long pendingResourceReconciliationGeneration;
    private ResourceCacheKey cacheKey;
    private ResourceDataCache dataCache;
    private String selectedProvider;
    private long lifecycleGeneration = 1L;
    private final Map<Async<?>, SharedRequest> sharedRequests = new IdentityHashMap<>();
    private final Map<Async<?>, SharedRequest> sharedViews = new IdentityHashMap<>();

    BrowserResourceBrowserContext(ResourceMarketplaceProviderAdapter marketplace, Object remoteHost) {
        this(marketplace, null, null, null, null, remoteHost, null, null, null);
    }

    BrowserResourceBrowserContext(ResourceMarketplaceProviderAdapter marketplace, BrowserRemotelyServerApi api,
                                  String serverId, String version, String loader, Object remoteHost) {
        this(marketplace, api, serverId, version, loader, remoteHost, null, null, null);
    }

    BrowserResourceBrowserContext(ResourceMarketplaceProviderAdapter marketplace, BrowserRemotelyServerApi api,
                                  String serverId, String version, String loader, Object remoteHost,
                                  RemotelyConfigStore configStore) {
        this(marketplace, api, serverId, version, loader, remoteHost, configStore, null, null);
    }

    BrowserResourceBrowserContext(ResourceMarketplaceProviderAdapter marketplace, BrowserRemotelyServerApi api,
                                  String serverId, String version, String loader, Object remoteHost,
                                  RemotelyConfigStore configStore, ServerUiCapabilityProvider serverCapabilities,
                                  ServerModels.ClientServerView server) {
        this(marketplace, api, serverId, version, loader, remoteHost, configStore, serverCapabilities, server, null);
    }

    BrowserResourceBrowserContext(ResourceMarketplaceProviderAdapter marketplace, BrowserRemotelyServerApi api,
                                  String serverId, String version, String loader, Object remoteHost,
                                  RemotelyConfigStore configStore, ServerUiCapabilityProvider serverCapabilities,
                                  ServerModels.ClientServerView server, Consumer<ModpackSelection> modpackSelection) {
        this.marketplace = marketplace;
        this.api = api;
        this.server = server == null ? serverView(serverId) : server;
        this.serverCapabilities = serverCapabilities == null
                ? api == null ? ServerUiCapabilityProvider.unavailable() : ServerUiCapabilityProvider.api(api)
                : serverCapabilities;
        this.serverId = serverId == null ? "" : serverId;
        this.version = version;
        this.loader = loader;
        this.remoteHost = remoteHost;
        this.configStore = configStore;
        this.modpackSelection = modpackSelection;
        this.cacheKey = resourceCacheKey();
        this.dataCache = new ResourceDataCache(cacheKey);
        if (serverCapabilities == null && api != null && !this.serverId.isBlank()) this.serverCapabilities.refresh(this.server);
        if (remoteHost instanceof BrowserServerScreenHost host) host.registerResourceContext(this);
    }

    static ResourceType defaultResourceType(String loader) {
        return ResourceCompatibilityTokens.compatibleTokens(loader).stream().anyMatch(ResourceCompatibilityTokens::isPlugin)
                ? ResourceType.PLUGIN : ResourceType.MOD;
    }

    static List<String> resourceDirectories(String loader) {
        return resourceDirectories(loader, "world");
    }

    static List<String> resourceDirectories(String loader, String worldName) {
        List<String> tokens = ResourceCompatibilityTokens.compatibleTokens(loader);
        boolean mods = tokens.stream().anyMatch(ResourceCompatibilityTokens::isModded);
        boolean plugins = tokens.stream().anyMatch(ResourceCompatibilityTokens::isPlugin);
        List<String> directories = new ArrayList<>();
        if (mods && plugins) {
            directories.add("/mods");
            directories.add("/plugins");
        } else if (plugins) {
            directories.add("/plugins");
        } else {
            directories.add("/mods");
        }
        String datapackDirectory = "/" + normalizedWorldName(worldName) + "/datapacks";
        if (!directories.contains(datapackDirectory)) directories.add(datapackDirectory);
        return List.copyOf(directories);
    }

    @Override
    public boolean hasInstance() {
        return api != null && !serverId.isBlank();
    }

    @Override
    public boolean isServer() {
        return hasInstance();
    }

    @Override
    public Async<ResourceMarketplaceProvider.Details> details(ResourceMarketplaceProvider.Card resource) {
        return details(resource, captureFence());
    }

    private Async<ResourceMarketplaceProvider.Details> details(ResourceMarketplaceProvider.Card resource, OperationFence fence) {
        if (resource == null) return Async.completed(null);
        if (marketplace == null) return Async.completed(null);
        return cachedDetails(resource, fence).thenApply(details -> {
            if (!isCurrent(fence)) return null;
            if (details == null) return null;
            ResourceMarketplaceProvider.Card detailedCard = details.card() == null ? resource : details.card();
            ResourceMarketplaceProvider.Card statefulCard = installedCard(detailedCard);
            return new ResourceMarketplaceProvider.Details(statefulCard, details.body(), details.projectUrl(), details.sourceUrl(),
                    details.issuesUrl(), details.wikiUrl(), details.discordUrl(), details.license(), details.createdAt(),
                    details.updatedAt(), details.gameVersions(), details.loaders(), details.links(), details.gallery(),
                    details.websiteUrl(), details.documentationUrl(), details.licenseUrl(), details.donations(),
                    details.creators(), details.averageRating(), details.ratingCount());
        });
    }

    @Override
    public Async<List<ResourceMarketplaceProvider.Version>> versions(ResourceMarketplaceProvider.Card resource) {
        return versions(resource, captureFence());
    }

    @Override
    public Async<State> state(ResourceMarketplaceProvider.Card resource) {
        if (resource == null) return Async.completed(State.standard());
        OperationFence fence = captureFence();
        return ensureInventory(fence).thenApply(ignored -> {
            List<InstalledFile> files = dataCache.installedFiles.getOrDefault(resourceKey(resource), List.of());
            InstalledFile installed = files.isEmpty() ? null : files.getFirst();
            Mode mode = ResourceType.getTypeFromString(resource.type()) == ResourceType.MODPACK && hasLinkedModpack()
                    ? Mode.LINKED : Mode.STANDARD;
            return new State(mode, installed == null ? "" : installed.versionId(),
                    installed == null ? "" : installed.filename(), hasInstance(), hasInstance() ? "" : "Server Target Is Unavailable");
        });
    }

    private Async<List<ResourceMarketplaceProvider.Version>> versions(ResourceMarketplaceProvider.Card resource, OperationFence fence) {
        if (resource == null) return Async.completed(List.of());
        if (marketplace == null) return Async.completed(List.of());
        return marketplace.versions(resource.provider(), resource.id()).thenCompose(versions -> {
            if (!isCurrent(fence)) return staleOperation();
            return Async.completed(versions == null ? List.of()
                    : versions.stream().filter(candidate -> matchesServer(candidate, resource)).toList());
        });
    }

    @Override
    public boolean supports(ResourceMarketplaceProvider.Action action, ResourceMarketplaceProvider.Card resource) {
        ensureDataCache();
        return action != null && resource != null && switch (action) {
            case INSTALL -> creationModpack(resource) || installSupported(resource);
            case UPDATE -> capabilitiesAvailable("files.read", "files.list", "files.pull", "files.delete") && hasUpdate(resource);
            case DELETE -> capabilitiesAvailable("files.read", "files.list", "files.delete") && hasInstalled(resource);
            case CHANGE_MODPACK -> api != null && hasInstance() && ResourceType.MODPACK == ResourceType.getTypeFromString(resource.type());
            case OPEN, SELECT -> true;
            default -> false;
        };
    }

    @Override
    public Availability availability(ActionRequest request) {
        ensureDataCache();
        if (request == null || request.action() == null || request.resource() == null) {
            return Availability.unavailable("Resource Action Is Unavailable");
        }
        if (request.action() == ResourceMarketplaceProvider.Action.CHANGE_MODPACK) {
            BrowserRemotelyServerApi.HostedModpackCapabilities capabilities = modpackCapabilities.get(request.resource().provider());
            if (capabilities == null) return Availability.unavailable("Checking Modpack Install Capability");
            BrowserRemotelyServerApi.HostedModpackAvailability value = hasLinkedModpack() ? capabilities.change() : capabilities.install();
            if (value == null || !value.supported()) {
                return Availability.unavailable(value == null || value.reason() == null || value.reason().isBlank()
                        ? "Change Modpack Is Unavailable For This Server" : value.reason());
            }
        }
        return supports(request.action(), request.resource()) ? Availability.supported() : Availability.unavailable(switch (request.action()) {
            case INSTALL -> "Install Is Unavailable For This Server";
            case UPDATE -> "Update Is Unavailable For This Resource";
            case DELETE -> "Remove Is Unavailable For This Resource";
            case CHANGE_MODPACK -> "Change Modpack Requires The Modpack Install Capability";
            case OPEN -> "Open Is Unavailable";
            case SELECT -> "Selection Is Unavailable";
        });
    }

    Async<Void> refreshModpackCapabilities(ResourceMarketplaceProvider.Card resource) {
        ensureDataCache();
        if (creationModpack(resource)) {
            return Async.completed(null);
        }
        if (api == null || resource == null || ResourceType.getTypeFromString(resource.type()) != ResourceType.MODPACK) {
            return Async.completed(null);
        }
        return api.getHostedModpackCapabilities(serverId, resource.provider()).thenApply(value -> {
            modpackCapabilities.put(resource.provider(), value == null
                    ? unavailableModpackCapabilities(resource.provider(), "Modpack Capabilities Are Unavailable") : value);
            return (Void) null;
        }).exceptionally(failure -> {
            modpackCapabilities.put(resource.provider(), unavailableModpackCapabilities(resource.provider(), failureMessage(failure)));
            return null;
        });
    }

    @Override
    public Map<String, CapabilityDescriptor> capabilities() {
        return Map.of(
                ResourceContainerCapabilities.GROUPING, configStore == null
                        ? CapabilityDescriptor.unavailable(ResourceContainerCapabilities.GROUPING, "Resource Group Storage Is Unavailable")
                        : CapabilityDescriptor.supported(ResourceContainerCapabilities.GROUPING),
                ResourceContainerCapabilities.NESTED, CapabilityDescriptor.supported(ResourceContainerCapabilities.NESTED),
                ResourceContainerCapabilities.DETACH, mutationCapability(ResourceContainerCapabilities.DETACH,
                        "Detaching Resources Requires Server File Editing", "files.read", "files.write"),
                ResourceContainerCapabilities.UPDATE_SELECTION, CapabilityDescriptor.supported(
                        ResourceContainerCapabilities.UPDATE_SELECTION),
                ResourceContainerCapabilities.BACKUP, mutationCapability(ResourceContainerCapabilities.BACKUP,
                        "Server Backups Are Unavailable", "backups.create"),
                ResourceContainerCapabilities.UPLOAD, CapabilityDescriptor.supported(
                        ResourceContainerCapabilities.UPLOAD));
    }

    @Override
    public void openExternal(String url) {
        if (url != null && !url.isBlank()) ScreenManager.getInstance().hostActions().openBrowser(url);
    }

    @Override
    public boolean isProxyServer() {
        return ResourceCompatibilityTokens.compatibleTokens(loader).stream().anyMatch(ResourceCompatibilityTokens::isProxy);
    }

    @Override
    public boolean supportsPlugins() {
        return ResourceCompatibilityTokens.compatibleTokens(loader).stream().anyMatch(ResourceCompatibilityTokens::isPlugin);
    }

    @Override
    public boolean supportsMods() {
        return ResourceCompatibilityTokens.compatibleTokens(loader).stream().anyMatch(ResourceCompatibilityTokens::isModded);
    }

    @Override
    public boolean hasLinkedModpack() {
        return server != null && server.environment != null && server.environment.get("MODPACK_PROJECT_ID") != null
                && !server.environment.get("MODPACK_PROJECT_ID").isBlank();
    }

    @Override
    public String modLoader() {
        return loader;
    }

    @Override
    public String serverSoftwareType() {
        return loader;
    }

    @Override
    public String versionId() {
        return version;
    }

    @Override
    public List<String> providerLoaderTokens(ResourceType type) {
        List<String> tokens = ResourceCompatibilityTokens.compatibleTokens(loader);
        return switch (type == null ? ResourceType.MODPACK : type) {
            case MOD -> tokens.stream().filter(ResourceCompatibilityTokens::isModded).toList();
            case PLUGIN -> tokens.stream().filter(ResourceCompatibilityTokens::isPlugin).toList();
            default -> List.of();
        };
    }

    @Override
    public boolean preserveUnknownResourceMetadata() {
        return true;
    }

    @Override
    public int resourceSearchScanBudget(ResourceType type) {
        return RESOURCE_SEARCH_SCAN_PAGES;
    }

    @Override
    public String normalizeCompatibilityToken(String value) {
        return ResourceCompatibilityTokens.normalize(value);
    }

    @Override
    public boolean isModdedCompatibilityToken(String value) {
        return ResourceCompatibilityTokens.isModded(value);
    }

    @Override
    public boolean isPluginCompatibilityToken(String value) {
        return ResourceCompatibilityTokens.isPlugin(value);
    }

    private boolean matchesServer(ResourceMarketplaceProvider.Version candidate, ResourceMarketplaceProvider.Card resource) {
        if (candidate == null || !matchesGameVersion(candidate.gameVersions())) return false;
        ResourceType type = ResourceType.getTypeFromString(resource.type());
        if (type != ResourceType.MOD && type != ResourceType.PLUGIN) return true;
        List<String> required = providerLoaderTokens(type);
        if (required.isEmpty() || candidate.loaders() == null || candidate.loaders().isEmpty()) return true;
        for (String candidateLoader : candidate.loaders()) {
            String token = normalizeCompatibilityToken(candidateLoader);
            if (token != null && required.contains(token)) return true;
        }
        return false;
    }

    private boolean matchesGameVersion(List<String> gameVersions) {
        if (version == null || version.isBlank() || gameVersions == null || gameVersions.isEmpty()) return true;
        return gameVersions.stream().anyMatch(candidate -> candidate != null && (candidate.equalsIgnoreCase(version)
                || candidate.equalsIgnoreCase("any") || candidate.equalsIgnoreCase("all") || candidate.equalsIgnoreCase("universal")));
    }

    @Override
    public String lastProvider(String key) {
        if (selectedProvider != null && marketplace.providers().contains(selectedProvider)) return selectedProvider;
        if (configStore != null) {
            String remembered = configStore.getResourceBrowserLastProvider(key);
            if (remembered != null && marketplace.providers().contains(remembered)) {
                selectedProvider = remembered;
                return remembered;
            }
        }
        selectedProvider = marketplace.providers().stream().findFirst().orElse(null);
        return selectedProvider;
    }

    @Override
    public void saveLastProvider(String key, String provider) {
        if (provider == null || provider.isBlank() || !marketplace.providers().contains(provider)) return;
        selectedProvider = provider;
        if (configStore == null) return;
        configStore.setResourceBrowserLastProvider(key, provider);
        configStore.save();
    }

    @Override
    public List<String> localBaseVersionIds() {
        return version == null || version.isBlank() ? List.of() : List.of(version);
    }

    @Override
    public Async<List<InstalledResource>> installedResources() {
        if (!hasInstance()) return Async.completed(List.of());
        OperationFence fence = captureFence();
        if (dataCache.inventoryRequest != null && isDataCurrent(fence)) return view(dataCache.inventoryRequest);
        dataCache.inventoryFence = fence;
        long requestGeneration = nextInventoryRequestGeneration();
        Async<List<InstalledResource>> request = withCapabilities(() -> loadInstalledResources(false, fence, requestGeneration), "files.read", "files.list");
        dataCache.inventoryRequest = request;
        request.whenComplete((ignored, failure) -> {
            if (failure == null) return;
            if (dataCache.inventoryRequest == request) dataCache.inventoryRequest = null;
        });
        return view(request);
    }

    Async<List<BrowserResourceEntry>> browserResourceEntries(boolean force) {
        return browserResourceEntries(force, false);
    }

    private Async<List<BrowserResourceEntry>> browserResourceEntries(boolean force, boolean awaitMetadata) {
        if (!hasInstance()) return Async.completed(List.of());
        OperationFence fence = captureFence();
        return withCapabilities(() -> {
            Async<List<InstalledResource>> inventory = force ? refreshInventory(false, fence)
                    : ensureInventory(fence).thenApply(ignored -> List.<InstalledResource>of());
            return inventory.thenCompose(ignored -> loadResourceFiles(fence));
        }, "files.read", "files.list").thenCompose(files -> {
        dataCache.resourceFiles = List.copyOf(files);
            long inventoryGeneration = dataCache.inventoryRequestGeneration;
            Map<String, InstalledFile> resolved = new LinkedHashMap<>();
            dataCache.installedFiles.values().stream().flatMap(List::stream).forEach(file ->
                    resolved.putIfAbsent(fileKey(file.directory(), file.filename()), file));
            return resolveResourceEntries(files, resolved, fence, inventoryGeneration, awaitMetadata).thenCompose(result -> {
                if (!isInventoryCurrent(fence, inventoryGeneration)) return staleOperation();
                dataCache.resourceEntries = List.copyOf(result);
                return Async.completed(result);
            });
        });
    }

    private Async<List<BrowserResourceEntry>> resolveResourceEntries(List<BrowserResourceFile> files,
                                                                      Map<String, InstalledFile> installed,
                                                                      OperationFence fence, long inventoryGeneration,
                                                                      boolean awaitMetadata) {
        if (files == null || files.isEmpty()) return Async.completed(List.of());
        long metadataDeadline = awaitMetadata ? System.currentTimeMillis() + RESOURCE_METADATA_WAIT.toMillis() : Long.MAX_VALUE;
        List<Async<BrowserResourceEntry>> requests = files.stream()
                .map(file -> resourceEntry(file, installed.get(fileKey(file.directory(), file.filename())), fence,
                        inventoryGeneration, awaitMetadata, metadataDeadline))
                .toList();
        return Async.allOf(requests.toArray(Async[]::new)).thenApply(ignored -> requests.stream()
                .map(Async::value).filter(Objects::nonNull).toList());
    }

    Async<BrowserResourceInventory> browserResourceInventory(boolean force) {
        OperationFence fence = captureFence();
        if (!force && dataCache.browserInventoryRequest != null && isDataCurrent(fence)) return view(dataCache.browserInventoryRequest);
        Async<BrowserResourceInventory> request = browserResourceEntries(force, true).thenApply(entries -> {
            if (!isCurrent(fence)) throw new Async.Cancellation();
            hydrateModpackProfile(entries, fence);
            return new BrowserResourceInventory(entries, dataCache.modpackProfile, resourceFailureSnapshot());
        });
        dataCache.browserInventoryRequest = request;
        request.whenComplete((ignored, failure) -> {
            if (failure == null) return;
            if (dataCache.browserInventoryRequest == request) dataCache.browserInventoryRequest = null;
        });
        return view(request);
    }

    Map<String, List<String>> resourceGroups() {
        if (configStore == null) return Map.of();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (RemotelyGroup group : configStore.getInstanceGroups(resourceGroupContext(serverId))) {
            if (group != null && !group.name().isBlank() && !group.members().isEmpty()) {
                groups.put(group.name(), group.members());
            }
        }
        return groups;
    }

    void saveResourceGroups(Map<String, List<String>> groups) {
        if (configStore == null) return;
        List<RemotelyGroup> values = new ArrayList<>();
        int index = 0;
        if (groups != null) {
            for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isEmpty()) continue;
                values.add(new RemotelyGroup("resource-" + index++, entry.getKey(), entry.getValue()));
            }
        }
        configStore.setInstanceGroups(resourceGroupContext(serverId), values);
        configStore.save();
    }

    Async<Void> createResourceBackup(String name) {
        return withCapabilities(() -> api.createBackup(serverId, name, List.of(), false).thenApply(ignored -> null), "backups.create");
    }

    Async<Boolean> detachResource(String path) {
        if (path == null || path.isBlank()) return Async.completed(false);
        return withCapabilities(() -> ensureModpackProfile().thenCompose(profile -> {
            if (profile == null || !profile.owns(path)) return Async.completed(false);
            BrowserModpackProfile detached = profile.detach(path);
            return serverCapabilities.writeFile(server, ".meta/modpack-profile.json", detached.json()).thenApply(ignored -> {
                clearInventoryCache();
                notifyResourceListeners(captureFence());
                return true;
            });
        }), "files.read", "files.write");
    }

    @Override
    public synchronized void addResourceListener(Runnable listener) {
        if (listener != null && !resourceListeners.contains(listener)) resourceListeners.add(listener);
    }

    @Override
    public synchronized void removeResourceListener(Runnable listener) {
        resourceListeners.remove(listener);
    }

    synchronized void addInventoryListener(Consumer<BrowserResourceInventory> listener) {
        if (listener != null && !inventoryListeners.contains(listener)) inventoryListeners.add(listener);
    }

    synchronized void removeInventoryListener(Consumer<BrowserResourceInventory> listener) {
        inventoryListeners.remove(listener);
    }

    void addCapabilityListener(Runnable listener) {
        serverCapabilities.addCapabilityListener(listener);
    }

    void removeCapabilityListener(Runnable listener) {
        serverCapabilities.removeCapabilityListener(listener);
    }

    synchronized BrowserResourceInventory inventorySnapshot() {
        return new BrowserResourceInventory(dataCache.resourceEntries, dataCache.modpackProfile, resourceFailureSnapshot());
    }

    @Override
    public Async<Void> action(ResourceMarketplaceProvider.Action action, ResourceMarketplaceProvider.Card resource,
                              ResourceMarketplaceProvider.Version version, Runnable refreshed) {
        if (action == null || resource == null) return Async.failed(new IllegalArgumentException("Resource Action Is Unavailable"));
        OperationFence fence = captureFence();
        return switch (action) {
            case INSTALL -> install(resource, version, null, refreshed, fence);
            case UPDATE -> update(resource, version, null, refreshed, fence);
            case DELETE -> delete(resource, refreshed, fence);
            case CHANGE_MODPACK -> changeModpack(resource, version, refreshed, fence);
            case OPEN -> details(resource, fence).thenApply(loaded -> {
                if (!isCurrent(fence)) return null;
                String url = loaded == null ? "" : loaded.projectUrl();
                if (url == null || url.isBlank()) throw new IllegalStateException("Resource Link Is Unavailable");
                openExternal(url);
                return null;
            });
            case SELECT -> Async.completed(null);
            default -> Async.failed(new UnsupportedOperationException("Managed Resource Actions Are Unavailable In Browser"));
        };
    }

    @Override
    public Async<Void> action(ActionRequest request, Runnable refreshed) {
        if (request == null) return Async.failed(new IllegalArgumentException("Resource Action Is Unavailable"));
        OperationFence fence = captureFence();
        return switch (request.action()) {
            case INSTALL -> install(request.resource(), request.version(), request.file(), refreshed, fence);
            case UPDATE -> update(request.resource(), request.version(), request.file(), refreshed, fence);
            case CHANGE_MODPACK -> changeModpack(request.resource(), request.version(), request.file(), refreshed, fence);
            default -> action(request.action(), request.resource(), request.version(), refreshed);
        };
    }

    private Async<Void> changeModpack(ResourceMarketplaceProvider.Card resource, ResourceMarketplaceProvider.Version selected,
                                      Runnable refreshed, OperationFence fence) {
        return changeModpack(resource, selected, null, refreshed, fence);
    }

    private Async<Void> changeModpack(ResourceMarketplaceProvider.Card resource, ResourceMarketplaceProvider.Version selected,
                                      ResourceMarketplaceProvider.VersionFile file, Runnable refreshed, OperationFence fence) {
        if (api == null || !hasInstance()) return Async.failed(new UnsupportedOperationException("Change Modpack Is Unavailable For This Server"));
        return latestVersion(resource, selected, fence).thenCompose(version -> api.getHostedModpackCapabilities(serverId, resource.provider())
                .thenCompose(capabilities -> {
                    if (capabilities == null) {
                        return Async.failed(new IllegalStateException("Modpack Capabilities Are Unavailable"));
                    }
                    modpackCapabilities.put(resource.provider(), capabilities);
                    boolean linked = hasLinkedModpack();
                    BrowserRemotelyServerApi.HostedModpackAvailability availability = linked ? capabilities.change() : capabilities.install();
                    if (availability == null || !availability.supported()) {
                        String reason = availability == null || availability.reason() == null || availability.reason().isBlank()
                                ? "Change Modpack Is Unavailable For This Server" : availability.reason();
                        return Async.failed(new UnsupportedOperationException(reason));
                    }
                    BrowserRemotelyServerApi.HostedModpackRequest request = new BrowserRemotelyServerApi.HostedModpackRequest(
                            resource.provider(), resource.id(), version.id(), file == null ? null : file.id(), version.number(), null, this.version,
                            loader, null, server == null ? null : server.dockerImage, UUID.randomUUID().toString());
                    Notification progress = progressNotification(linked ? "Changing Modpack" : "Installing Modpack");
                    Async<BrowserRemotelyServerApi.HostedModpackJob> preflight = capabilities.preflight() != null
                            && capabilities.preflight().supported()
                            ? api.preflightHostedModpack(serverId, request).thenCompose(job -> awaitModpackJob(job, progress, fence))
                            : Async.completed(null);
                    return preflight.thenCompose(ignored -> {
                        updateModpackProgress(progress, "Preparing Server", 0);
                        Async<BrowserRemotelyServerApi.HostedModpackJob> operation = linked
                                ? api.changeHostedModpack(serverId, request) : api.installHostedModpack(serverId, request);
                        return operation.thenCompose(job -> awaitModpackJob(job, progress, fence));
                    }).thenCompose(job -> {
                        if (!isCurrent(fence)) return Async.completed(null);
                        if (server != null) {
                            if (server.environment == null) server.environment = new LinkedHashMap<>();
                            server.environment.put("MODPACK_PROVIDER", resource.provider());
                            server.environment.put("MODPACK_PROJECT_ID", resource.id());
                            server.environment.put("MODPACK_VERSION_ID", version.id());
                            server.environment.put("MODPACK_VERSION_NUMBER", version.number());
                        }
                        return refreshInventory(true, fence).thenApply(ignored -> {
                            progress.update().message(linked ? "Modpack Changed" : "Modpack Installed")
                                    .description(resource.title()).type(Notification.Type.SUCCESS).loading(false)
                                    .autoSlideOut(true).progress(100, 100).commit();
                            if (refreshed != null) refreshed.run();
                            notifyResourceListeners(fence);
                            return (Void) null;
                        });
                    }).whenComplete((ignored, failure) -> {
                        if (failure == null) return;
                        progress.update().message(linked ? "Change Failed" : "Installation Failed")
                                .description(failureMessage(failure)).type(Notification.Type.ERROR).loading(false)
                                .autoSlideOut(true).commit();
                    });
                }));
    }

    private Async<BrowserRemotelyServerApi.HostedModpackJob> awaitModpackJob(
            BrowserRemotelyServerApi.HostedModpackJob job, Notification progress, OperationFence fence) {
        Async<BrowserRemotelyServerApi.HostedModpackJob> result = Async.pending();
        pollModpackJob(job, progress, fence, result);
        return result;
    }

    Async<Void> unlockModpack(String provider) {
        if (api == null || !hasInstance() || provider == null || provider.isBlank()) {
            return Async.failed(new UnsupportedOperationException("Unlock Modpack Is Unavailable For This Server"));
        }
        OperationFence fence = captureFence();
        return api.getHostedModpackCapabilities(serverId, provider).thenCompose(capabilities -> {
            BrowserRemotelyServerApi.HostedModpackAvailability availability = capabilities == null ? null : capabilities.unlock();
            if (availability == null || !availability.supported()) {
                String reason = availability == null || availability.reason() == null || availability.reason().isBlank()
                        ? "Unlock Modpack Is Unavailable For This Server" : availability.reason();
                return Async.failed(new UnsupportedOperationException(reason));
            }
            return api.unlockHostedModpack(serverId, provider, UUID.randomUUID().toString())
                    .thenCompose(job -> awaitModpackJob(job, null, fence)).thenCompose(job -> {
                        if (server != null && server.environment != null) {
                            server.environment.remove("MODPACK_PROVIDER");
                            server.environment.remove("MODPACK_PROJECT_ID");
                            server.environment.remove("MODPACK_VERSION_ID");
                            server.environment.remove("MODPACK_VERSION_NUMBER");
                        }
                        return refreshInventory(true, fence).thenApply(ignored -> {
                            notifyResourceListeners(fence);
                            return null;
                        });
                    });
        });
    }

    private void pollModpackJob(BrowserRemotelyServerApi.HostedModpackJob job, Notification progress,
                                OperationFence fence, Async<BrowserRemotelyServerApi.HostedModpackJob> result) {
        if (result.isDone()) return;
        if (!isCurrent(fence)) {
            result.fail(new IllegalStateException("Server Context Changed"));
            return;
        }
        if (job == null || job.id() == null || job.id().isBlank()) {
            result.fail(new IllegalStateException("Modpack Job Is Unavailable"));
            return;
        }
        String status = job.status() == null ? "" : job.status().toLowerCase(Locale.ROOT);
        if (progress != null) updateModpackProgress(progress, modpackJobDescription(job), job.progress());
        if (status.equals("completed") || status.equals("complete") || status.equals("succeeded") || status.equals("success")) {
            result.complete(job);
            return;
        }
        if (status.equals("failed") || status.equals("error") || status.equals("cancelled") || status.equals("canceled")) {
            result.fail(new IllegalStateException(job.error() == null || job.error().isBlank() ? "Modpack Operation Failed" : job.error()));
            return;
        }
        scheduler.schedule(() -> api.getHostedModpackJob(job.id()).whenComplete((next, failure) -> {
            if (failure != null) result.fail(failure);
            else pollModpackJob(next, progress, fence, result);
        }), Duration.ofSeconds(1));
    }

    private static Notification progressNotification(String message) {
        return new Notification.Builder().message(message).description("Checking Server")
                .type(Notification.Type.INFO).loading(true).autoSlideOut(false).progress(0, 100).build();
    }

    private static void updateModpackProgress(Notification notification, String description, int progress) {
        if (progress >= 0) notification.updateProgress(description, Math.min(progress, 100), 100);
        else notification.update().description(description).commit();
    }

    private static String modpackJobDescription(BrowserRemotelyServerApi.HostedModpackJob job) {
        String operation = job.operation() == null ? "Modpack" : switch (job.operation().toLowerCase(Locale.ROOT)) {
            case "preflight" -> "Checking Downloads";
            case "install" -> "Installing Modpack";
            case "change" -> "Changing Modpack";
            default -> "Preparing Server";
        };
        return operation;
    }

    private static String failureMessage(Throwable failure) {
        return failureMessage(failure, "Modpack Operation Failed");
    }

    private static String failureMessage(Throwable failure, String fallback) {
        Throwable current = ResourceProviderException.unwrap(failure);
        String message = current == null ? null : current.getMessage();
        if (message == null || message.isBlank()) message = failure == null ? null : failure.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    private BrowserRemotelyServerApi.HostedModpackCapabilities unavailableModpackCapabilities(String provider, String reason) {
        BrowserRemotelyServerApi.HostedModpackAvailability unavailable =
                new BrowserRemotelyServerApi.HostedModpackAvailability(false, reason, "");
        return new BrowserRemotelyServerApi.HostedModpackCapabilities(serverId, provider, false,
                unavailable, unavailable, unavailable, unavailable);
    }

    private Async<Void> install(ResourceMarketplaceProvider.Card resource, ResourceMarketplaceProvider.Version selected,
                                ResourceMarketplaceProvider.VersionFile selectedFile, Runnable refreshed, OperationFence fence) {
        if (creationModpack(resource)) {
            return latestVersion(resource, selected, fence).thenApply(version -> {
                if (!isCurrent(fence)) return null;
                String selectedLoader = version.loaders() == null || version.loaders().isEmpty() ? "" : version.loaders().getFirst();
                modpackSelection.accept(new ModpackSelection(resource.provider(), resource.id(), version.id(), version.number(), selectedLoader));
                if (refreshed != null) refreshed.run();
                return null;
            });
        }
        if (!installEligible(resource)) {
            return Async.failed(new UnsupportedOperationException("Resource Install Is Unavailable For This Server"));
        }
        return withCapabilities(() -> resourceDirectoriesAsync(fence).thenCompose(ignored -> installVersion(resource, selected, fence)
                .thenCompose(version -> resolveDownload(resource, version, selectedFile).thenCompose(download ->
                        pullFile(download.url(), resourceDirectory(resource), download.filename()))))
                .thenCompose(ignored -> refreshInventory(true, fence)).thenApply(ignored -> {
                    if (!isCurrent(fence)) return null;
                    if (refreshed != null) refreshed.run();
                    return null;
                }), "files.read", "files.list", "files.pull");
    }

    private Async<Void> update(ResourceMarketplaceProvider.Card resource, ResourceMarketplaceProvider.Version selected,
                               ResourceMarketplaceProvider.VersionFile selectedFile, Runnable refreshed, OperationFence fence) {
        if (!hasInstance() || !supportsResourceResolution(resource)) {
            return Async.failed(new UnsupportedOperationException("Resource Update Is Unavailable"));
        }
        return withCapabilities(() -> ensureInventory(fence).thenCompose(ignored -> {
            List<InstalledFile> files = dataCache.installedFiles.getOrDefault(resourceKey(resource), List.of());
            if (files.isEmpty()) return Async.failed(new IllegalStateException("Installed Resource Is Unavailable"));
            return latestVersion(resource, selected, fence).thenCompose(target -> resolveDownload(resource, target, selectedFile)
                    .thenCompose(download -> pullFile(download.url(), files.getFirst().directory(), download.filename())
                            .thenCompose(done -> removeObsoleteFiles(files, download.filename()))));
        }).thenCompose(ignored -> refreshInventory(true, fence)).thenApply(ignored -> {
            if (!isCurrent(fence)) return null;
            if (refreshed != null) refreshed.run();
            return null;
        }), "files.read", "files.list", "files.pull", "files.delete");
    }

    private Async<ResourceMarketplaceProviderAdapter.Download> resolveDownload(ResourceMarketplaceProvider.Card resource,
                                                                                ResourceMarketplaceProvider.Version version,
                                                                                ResourceMarketplaceProvider.VersionFile selectedFile) {
        if (selectedFile == null) return marketplace.resolveDownload(resource, version);
        return marketplace.resourceVersion(resource, version).thenCompose(found -> {
            if (found == null || found.files == null) return Async.failed(new IllegalStateException("Resource Download Is Unavailable"));
            OnlineResourceVersion.VersionFile target = found.files.stream().filter(Objects::nonNull)
                    .filter(candidate -> selectedFile.id() != null && !selectedFile.id().isBlank()
                            && selectedFile.id().equals(candidate.id)
                            || selectedFile.name() != null && selectedFile.name().equals(candidate.filename))
                    .findFirst().orElse(null);
            if (target == null || target.filename == null || target.filename.isBlank()) {
                return Async.failed(new IllegalStateException("Resource Download Is Unavailable"));
            }
            try {
                return marketplace.source(resource.provider()).getDirectFileURLAsync(found, target).thenCompose(url ->
                        url == null || url.isBlank() ? Async.failed(new IllegalStateException("Resource Download Is Unavailable"))
                                : Async.completed(new ResourceMarketplaceProviderAdapter.Download(url, target.filename)));
            } catch (Throwable failure) {
                return Async.failed(failure);
            }
        });
    }

    private Async<Void> delete(ResourceMarketplaceProvider.Card resource, Runnable refreshed, OperationFence fence) {
        if (!hasInstance() || !supportsResourceResolution(resource)) {
            return Async.failed(new UnsupportedOperationException("Resource Removal Is Unavailable"));
        }
        return withCapabilities(() -> ensureInventory(fence).thenCompose(ignored -> {
            List<InstalledFile> files = dataCache.installedFiles.getOrDefault(resourceKey(resource), List.of());
            if (files.isEmpty()) return Async.completed(null);
            Map<String, List<String>> grouped = new LinkedHashMap<>();
            for (InstalledFile file : files) grouped.computeIfAbsent(file.directory(), ignoredDirectory -> new ArrayList<>()).add(file.filename());
            Async<Void> result = Async.completed(null);
            for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
                result = result.thenCompose(done -> deleteFiles(entry.getKey(), entry.getValue()));
            }
            return result;
        }).thenCompose(ignored -> refreshInventory(true, fence)).thenApply(ignored -> {
            if (!isCurrent(fence)) return null;
            if (refreshed != null) refreshed.run();
            return null;
        }), "files.read", "files.list", "files.delete");
    }

    private Async<ResourceMarketplaceProvider.Version> latestVersion(ResourceMarketplaceProvider.Card resource,
                                                                      ResourceMarketplaceProvider.Version selected,
                                                                      OperationFence fence) {
        if (selected != null) return Async.completed(selected);
        return versions(resource, fence).thenCompose(available -> available == null || available.isEmpty()
                ? Async.failed(new IllegalStateException("No Compatible Resource Version Is Available"))
                : Async.completed(available.stream().max(Comparator.comparing(BrowserResourceBrowserContext::publishedAt)).orElse(available.getFirst())));
    }

    private Async<Void> removeObsoleteFiles(List<InstalledFile> files, String filename) {
        List<String> obsolete = files.stream().filter(file -> !Objects.equals(file.filename(), filename))
                .map(InstalledFile::filename).filter(name -> name != null && !name.isBlank()).distinct().toList();
        if (obsolete.isEmpty()) return Async.completed(null);
        return deleteFiles(files.getFirst().directory(), obsolete);
    }

    private Async<Void> ensureInventory(OperationFence fence) {
        if (!isCurrent(fence)) return staleOperation();
        return installedResources().thenApply(ignored -> null);
    }

    private Async<BrowserModpackProfile> ensureModpackProfile() {
        if (dataCache.modpackProfile != null) return Async.completed(dataCache.modpackProfile);
        if (dataCache.modpackProfileRequest != null) return view(dataCache.modpackProfileRequest);
        OperationFence fence = captureFence();
        return startModpackProfileHydration(dataCache.resourceEntries, fence);
    }

    private void hydrateModpackProfile(List<BrowserResourceEntry> entries, OperationFence fence) {
        if (!hasInstance() || dataCache.modpackProfile != null || dataCache.modpackProfileRequest != null) return;
        startModpackProfileHydration(entries, fence);
    }

    private Async<BrowserModpackProfile> startModpackProfileHydration(List<BrowserResourceEntry> entries, OperationFence fence) {
        if (!hasInstance() || dataCache.modpackProfile != null) return Async.completed(dataCache.modpackProfile);
        if (dataCache.modpackProfileRequest != null) return view(dataCache.modpackProfileRequest);
        Async<BrowserModpackProfile> request = loadModpackProfile(entries, fence);
        dataCache.modpackProfileRequest = request;
        request.whenComplete((profile, failure) -> {
            if (dataCache.modpackProfileRequest != request || !isCurrent(fence)) return;
            dataCache.modpackProfileRequest = null;
            if (failure != null) {
                dataCache.resourceFailureMessages.put(".meta/modpack-profile.json", failureMessage(failure,
                        "Modpack Metadata Unavailable"));
            } else {
                dataCache.resourceFailureMessages.remove(".meta/modpack-profile.json");
            }
            dataCache.modpackProfile = profile;
            dataCache.browserInventoryRequest = null;
            notifyProgressiveInventoryListeners(fence);
        });
        return view(request);
    }

    private Async<BrowserModpackProfile> loadModpackProfile(List<BrowserResourceEntry> entries) {
        return loadModpackProfile(entries, captureFence());
    }

    private Async<BrowserModpackProfile> loadModpackProfile(List<BrowserResourceEntry> entries, OperationFence fence) {
        if (!hasInstance()) return Async.completed(null);
        if (!isCurrent(fence)) return staleOperation();
        return api.getFileContentAllowMissing(serverId, ".meta/modpack-profile.json")
                .thenCompose(content -> {
                    if (!isCurrent(fence)) return staleOperation();
                    dataCache.modpackProfile = profile(content, entries, server);
                    return Async.completed(dataCache.modpackProfile);
                });
    }

    static BrowserModpackProfile profile(String content, List<BrowserResourceEntry> entries, ServerModels.ClientServerView server) {
        JsonObject root;
        try {
            root = BrowserJson.object(content);
        } catch (RuntimeException ignored) {
            return null;
        }
        boolean linked = server != null && server.environment != null
                && server.environment.get("MODPACK_PROJECT_ID") != null && !server.environment.get("MODPACK_PROJECT_ID").isBlank();
        List<JsonObject> declared = BrowserJson.objects(root, "content");
        if (!linked && declared.isEmpty() && BrowserJson.string(root, "projectId").isBlank()) return null;
        Set<String> preserved = new LinkedHashSet<>(BrowserJson.strings(root, "preservedConflicts").stream()
                .map(BrowserResourceBrowserContext::resourcePath).filter(value -> !value.isBlank()).toList());
        Set<String> owned = new LinkedHashSet<>();
        for (JsonObject item : declared) {
            String path = resourcePath(BrowserJson.string(item, "path"));
            if (!path.isBlank() && !preserved.contains(path)) owned.add(path);
        }
        if (declared.isEmpty() && linked && entries != null) {
            JsonArray values = new JsonArray();
            for (BrowserResourceEntry entry : entries) {
                if (entry == null) continue;
                String path = resourcePath(entry.key());
                if (path.isBlank()) continue;
                JsonObject item = new JsonObject();
                BrowserJson.put(item, "path", path);
                BrowserJson.put(item, "sha1", entry.hash());
                BrowserJson.put(item, "murmur2", entry.murmur2());
                values.add(item);
                if (!preserved.contains(path)) owned.add(path);
            }
            root.add("content", values);
        }
        String environmentProvider = environment(server, "MODPACK_PROVIDER");
        String environmentProject = environment(server, "MODPACK_PROJECT_ID");
        String environmentVersionId = environment(server, "MODPACK_VERSION_ID");
        String environmentVersion = environment(server, "MODPACK_VERSION_NUMBER");
        String name = first(BrowserJson.string(root, "name"), server == null ? null : server.name, "Installed Modpack");
        String provider = first(BrowserJson.string(root, "provider"), environmentProvider);
        String projectId = first(BrowserJson.string(root, "projectId"), environmentProject);
        String versionId = first(BrowserJson.string(root, "versionId"), environmentVersionId);
        String version = first(BrowserJson.string(root, "versionNumber"), environmentVersion, "Installed");
        BrowserJson.put(root, "name", name);
        BrowserJson.put(root, "provider", provider);
        BrowserJson.put(root, "projectId", projectId);
        BrowserJson.put(root, "versionId", versionId);
        BrowserJson.put(root, "versionNumber", version);
        JsonArray conflicts = new JsonArray();
        preserved.forEach(conflicts::add);
        root.add("preservedConflicts", conflicts);
        return new BrowserModpackProfile(name, provider, projectId, versionId, version, owned, preserved, root.toString());
    }

    private Async<List<InstalledResource>> loadInstalledResources(boolean notify, OperationFence fence, long requestGeneration) {
        if (!hasInstance()) return Async.completed(List.of());
        return resourceDirectoriesAsync(fence).thenCompose(directories -> {
            List<Async<List<ServerModels.PteroFileObjectAttributes>>> fileRequests = directories.stream()
                    .map(this::listResourceDirectory).toList();
            if (fileRequests.isEmpty()) {
                if (!isDataCurrent(fence) || requestGeneration != dataCache.inventoryRequestGeneration) return staleOperation();
                reconcileDirectoryResults(directories, List.of());
                return Async.completed(new InstalledResult(List.of(), List.of()));
            }
            return collectDirectoryResults(fileRequests).thenCompose(results -> {
                if (!isDataCurrent(fence) || requestGeneration != dataCache.inventoryRequestGeneration) return staleOperation();
                List<DirectoryResult<ServerModels.PteroFileObjectAttributes>> retained = reconcileDirectoryResults(directories, results);
                List<InstalledFile> files = new ArrayList<>();
                for (int index = 0; index < retained.size(); index++) {
                    List<ServerModels.PteroFileObjectAttributes> entries = retained.get(index).values();
                    if (entries == null) continue;
                    String directory = directories.get(index);
                    for (ServerModels.PteroFileObjectAttributes entry : entries) {
                        if (entry != null && entry.isFile && isResourceFileName(entry.name)) {
                            files.add(new InstalledFile(null, null, null, directory, entry.name, normalizeHash(entry.sha1),
                                    normalizeFingerprint(entry.murmur2), false));
                        }
                    }
                }
                cacheInstalledFiles(files);
                hydrateInstalledFiles(files, notify, fence, requestGeneration);
                return Async.completed(new InstalledResult(List.of(), files));
            });
        }).thenCompose(result -> {
            if (!isDataCurrent(fence) || requestGeneration != dataCache.inventoryRequestGeneration) return staleOperation();
            if (notify) notifyResourceListeners(fence);
            return Async.completed(result.resources());
        });
    }

    private void hydrateInstalledFiles(List<InstalledFile> files, boolean notify, OperationFence fence,
                                       long requestGeneration) {
        Async<List<InstalledFile>> request = resolveMissingHashes(files, fence, requestGeneration)
                .thenCompose(resolved -> {
                    if (!isDataCurrent(fence) || requestGeneration != dataCache.inventoryRequestGeneration) {
                        return staleOperation();
                    }
                    cacheInstalledFiles(resolved);
                    notifyProgressiveInventoryListeners(fence);
                    refreshResourceEntries(fence, requestGeneration);
                    return resolveInstalled(resolved, fence, requestGeneration).thenApply(result -> result.files());
                });
        dataCache.inventoryHydrationRequest = request;
        request.whenComplete((ignored, failure) -> {
            if (dataCache.inventoryHydrationRequest != request) return;
            if (failure != null && !(failure instanceof Async.Cancellation)) {
                String message = failureMessage(failure, "Resource Inventory Hydration Failed");
                dataCache.resourceFailureMessages.put("inventory", message);
                notifyProgressiveInventoryListeners(fence);
            }
        });
    }

    private void cacheInstalledFiles(List<InstalledFile> files) {
        dataCache.installedFiles.clear();
        if (files == null) return;
        for (InstalledFile file : files) {
            if (file == null) continue;
            dataCache.installedFiles.computeIfAbsent(resourceKey(file.provider(), file.projectId()),
                    ignored -> new ArrayList<>()).add(file);
        }
        dataCache.installedFiles.replaceAll((key, values) -> new ArrayList<>(values));
    }

    private void updateInstalledFile(InstalledFile updated) {
        if (updated == null) return;
        Map<String, InstalledFile> files = new LinkedHashMap<>();
        dataCache.installedFiles.values().stream().flatMap(List::stream).forEach(file ->
                files.put(fileKey(file.directory(), file.filename()), file));
        files.put(fileKey(updated.directory(), updated.filename()), updated);
        cacheInstalledFiles(List.copyOf(files.values()));
    }

    private Async<List<String>> resourceDirectoriesAsync() {
        return resourceDirectoriesAsync(captureFence());
    }

    private Async<List<String>> resourceDirectoriesAsync(OperationFence fence) {
        if (!hasInstance()) return Async.completed(List.of());
        if (dataCache.resourceDirectoriesRequest != null && isDataCurrent(fence)) return view(dataCache.resourceDirectoriesRequest);
        dataCache.resourceDirectoriesFence = fence;
        dataCache.resourceFailureMessages.remove("server.properties");
        dataCache.resourceFailureMessages.remove("/");
        Async<List<String>> request = api.getFileContentAllowMissing(serverId, "server.properties")
                .handle((content, failure) -> new OperationResult<>(content, failure))
                .thenCompose(result -> {
                    if (!isDataCurrent(fence)) return staleOperation();
                    if (result.failure() != null) {
                        recordResourceFailure("server.properties", result.failure());
                        dataCache.worldName = "world";
                        return Async.completed(resourceDirectories(loader, dataCache.worldName));
                    }
                    recordResourceFailure("server.properties", null);
                    dataCache.worldName = resolveWorldName(result.value());
                    List<String> directories = resourceDirectories(loader, dataCache.worldName);
                    return api.listFilesAllowMissingDirectory(serverId, "/")
                            .handle((root, failure) -> new OperationResult<>(root, failure))
                            .thenCompose(rootResult -> {
                                if (!isDataCurrent(fence)) return staleOperation();
                                if (rootResult.failure() != null) {
                                    recordResourceFailure("/", rootResult.failure());
                                    return Async.completed(directories);
                                }
                                recordResourceFailure("/", null);
                                return Async.completed(mergeResourceDirectories(directories, rootResult.value()));
                            });
                });
        dataCache.resourceDirectoriesRequest = request;
        request.whenComplete((ignored, failure) -> {
            if (failure == null) return;
            if (dataCache.resourceDirectoriesRequest == request) dataCache.resourceDirectoriesRequest = null;
        });
        return view(request);
    }

    List<String> mergeResourceDirectories(List<String> defaults,
                                          List<ServerModels.PteroFileObjectAttributes> rootEntries) {
        LinkedHashSet<String> directories = new LinkedHashSet<>(defaults == null ? List.of() : defaults);
        if (rootEntries == null) return List.copyOf(directories);
        String normalizedWorld = normalizedWorldName(dataCache.worldName);
        for (ServerModels.PteroFileObjectAttributes entry : rootEntries) {
            if (entry == null || entry.isFile || entry.name == null || entry.name.isBlank()) continue;
            String name = entry.name.strip();
            switch (name.toLowerCase(Locale.ROOT)) {
                case "mods", "plugins", "resourcepacks", "shaderpacks" -> directories.add("/" + name);
                default -> {
                    if (name.equals(normalizedWorld)) directories.add("/" + name + "/datapacks");
                }
            }
        }
        return List.copyOf(directories);
    }

    private Async<List<BrowserResourceFile>> loadResourceFiles() {
        return loadResourceFiles(captureFence());
    }

    private Async<List<BrowserResourceFile>> loadResourceFiles(OperationFence fence) {
        return resourceDirectoriesAsync(fence).thenCompose(directories -> {
            List<Async<List<ServerModels.PteroFileObjectAttributes>>> requests = directories.stream()
                    .map(this::listResourceDirectory).toList();
            return collectDirectoryResults(requests).thenCompose(results -> {
                if (!isCurrent(fence)) return staleOperation();
                List<DirectoryResult<ServerModels.PteroFileObjectAttributes>> retained = reconcileDirectoryResults(directories, results);
                List<BrowserResourceFile> files = new ArrayList<>();
                for (int index = 0; index < retained.size(); index++) {
                    List<ServerModels.PteroFileObjectAttributes> values = retained.get(index).values();
                    if (values == null) continue;
                    String directory = directories.get(index);
                    for (ServerModels.PteroFileObjectAttributes value : values) {
                        if (value != null && value.isFile && isResourceFileName(value.name)) {
                            files.add(new BrowserResourceFile(directory, value.name, normalizeHash(value.sha1),
                                    normalizeFingerprint(value.murmur2)));
                        }
                    }
                }
                return Async.completed(List.copyOf(files));
            });
        });
    }

    private Async<BrowserResourceEntry> resourceEntry(BrowserResourceFile file, InstalledFile installed, OperationFence fence,
                                                      long inventoryGeneration, boolean awaitMetadata, long metadataDeadline) {
        if (file == null) return Async.completed(null);
        ResourceType type = type(file.directory());
        String key = fileKey(file.directory(), file.filename());
        String path = hashPath(key);
        String hashFailure = dataCache.resourceHashFailures.getOrDefault(path, "");
        String metadataFailure = dataCache.resourceDetailFailures.getOrDefault(path, "");
        String sha1 = installed == null || normalizeHash(installed.sha1()).isBlank() ? file.sha1() : installed.sha1();
        String murmur2 = installed == null || normalizeFingerprint(installed.murmur2()).isBlank()
                ? file.murmur2() : installed.murmur2();
        if (installed == null || marketplace == null || installed.provider() == null || installed.provider().isBlank()
                || installed.projectId() == null || installed.projectId().isBlank()) {
            return Async.completed(resourceEntryForCard(file, installed, type, null, sha1, murmur2, hashFailure,
                    metadataFailure, false));
        }
        ResourceMarketplaceProvider.Card fallback = fallbackCard(installed, type, file.filename());
        ResourceMarketplaceProvider.Details details = dataCache.resourceDetailResults.get(resourceKey(fallback));
        if (details != null && details.card() != null) {
            return Async.completed(resourceEntryForCard(file, installed, type, details.card(), sha1, murmur2,
                    hashFailure, "", true));
        }
        Async<ResourceMarketplaceProvider.Details> metadata = null;
        String detailKey = resourceKey(fallback);
        if (metadataFailure.isBlank()) {
            metadata = dataCache.resourceDetailRequests.get(detailKey);
            if (metadata == null) metadata = hydrateResourceDetails(file, installed, fallback, fence, inventoryGeneration);
        }
        if (awaitMetadata && metadata != null) {
            long remaining = metadataDeadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return Async.completed(resourceEntryForCard(file, installed, type, fallback, sha1, murmur2, hashFailure,
                        metadataFailure, false));
            }
            return awaitMetadata(metadata, Duration.ofMillis(remaining)).thenApply(outcome -> {
                if (outcome.value() != null && outcome.value().card() != null) {
                    return resourceEntryForCard(file, installed, type, outcome.value().card(), sha1, murmur2,
                            hashFailure, "", true);
                }
                String failure = metadataFailure;
                if (failure.isBlank() && outcome.failure() != null && !(outcome.failure() instanceof Async.Cancellation)) {
                    failure = failureMessage(outcome.failure(), "Resource Metadata Unavailable");
                }
                if (failure.isBlank() && outcome.failure() == null) failure = "Resource Metadata Unavailable";
                return resourceEntryForCard(file, installed, type, fallback, sha1, murmur2, hashFailure, failure, false);
            });
        }
        return Async.completed(resourceEntryForCard(file, installed, type, fallback, sha1, murmur2, hashFailure,
                metadataFailure, false));
    }

    private BrowserResourceEntry resourceEntryForCard(BrowserResourceFile file, InstalledFile installed, ResourceType type,
                                                       ResourceMarketplaceProvider.Card card, String sha1, String murmur2,
                                                       String hashFailure, String metadataFailure, boolean metadataAvailable) {
        String filename = file.filename();
        String name = card == null || card.title() == null || card.title().isBlank() ? filename : card.title();
        String description = card == null || card.description() == null || card.description().isBlank()
                ? "Installed resource file" : card.description();
        if (!hashFailure.isBlank()) description = description + ". Hash Resolution Failed: " + hashFailure;
        if (!metadataFailure.isBlank()) description = description + ". Resource Metadata Unavailable: " + metadataFailure;
        String author = card == null || card.authors() == null || card.authors().isEmpty()
                ? metadataFailure.isBlank() ? "Unknown" : "Unavailable" : card.authors().getFirst();
        String installedVersion = installed == null ? "" : Objects.requireNonNullElse(installed.versionId(), "");
        String version = installedVersion.isBlank()
                ? metadataFailure.isBlank() ? "Installed" : "Unknown" : installedVersion;
        boolean enabled = !filename.toLowerCase(Locale.ROOT).endsWith(".disabled");
        return new BrowserResourceEntry(fileKey(file.directory(), filename), card, type, file.directory(), filename,
                name, description, version, author, metadataAvailable && card != null ? card.iconUrl() : null,
                sha1, murmur2, enabled, metadataAvailable && installed != null && installed.updateAvailable(),
                metadataAvailable, hashFailure, metadataFailure);
    }

    private Async<ResourceMarketplaceProvider.Details> hydrateResourceDetails(BrowserResourceFile file,
                                                                                InstalledFile installed,
                                                                                ResourceMarketplaceProvider.Card fallback,
                                                                                OperationFence fence,
                                                                                long inventoryGeneration) {
        if (fallback == null || !isInventoryCurrent(fence, inventoryGeneration)) return Async.completed(null);
        String path = hashPath(fileKey(file.directory(), file.filename()));
        String key = resourceKey(fallback);
        Async<ResourceMarketplaceProvider.Details> request = cachedDetails(fallback, fence);
        request.whenComplete((details, failure) -> {
            if (!isInventoryCurrent(fence, inventoryGeneration)) return;
            if (failure == null && details != null && details.card() != null) {
                dataCache.resourceDetailResults.put(key, details);
                dataCache.resourceDetailFailures.remove(path);
            } else {
                dataCache.resourceDetailResults.remove(key);
                dataCache.resourceDetailFailures.put(path, failure == null
                        ? "Resource Metadata Unavailable" : failureMessage(failure, "Resource Metadata Unavailable"));
            }
            refreshResourceEntries(fence, inventoryGeneration);
        });
        return view(request);
    }

    private void refreshResourceEntries(OperationFence fence, long inventoryGeneration) {
        if (!isInventoryCurrent(fence, inventoryGeneration) || dataCache.resourceFiles.isEmpty()) return;
        synchronized (this) {
            pendingResourceReconciliationFence = fence;
            pendingResourceReconciliationGeneration = inventoryGeneration;
            if (pendingResourceReconciliation != null) pendingResourceReconciliation.cancel();
            pendingResourceReconciliation = scheduler.schedule(this::dispatchResourceReconciliation, Duration.ofMillis(50));
        }
    }

    private void dispatchResourceReconciliation() {
        OperationFence fence;
        long inventoryGeneration;
        synchronized (this) {
            pendingResourceReconciliation = null;
            fence = pendingResourceReconciliationFence;
            pendingResourceReconciliationFence = null;
            inventoryGeneration = pendingResourceReconciliationGeneration;
            pendingResourceReconciliationGeneration = 0L;
        }
        if (!isInventoryCurrent(fence, inventoryGeneration) || dataCache.resourceFiles.isEmpty()) return;
        resolveResourceEntries(dataCache.resourceFiles, installedFileIndex(), fence, inventoryGeneration, false)
                .whenComplete((entries, failure) -> {
                    if (failure != null || !isInventoryCurrent(fence, inventoryGeneration)) return;
                    dataCache.resourceEntries = entries == null ? List.of() : List.copyOf(entries);
                    dataCache.browserInventoryRequest = null;
                    notifyProgressiveInventoryListeners(fence);
                });
    }

    private Async<OperationResult<ResourceMarketplaceProvider.Details>> awaitMetadata(
            Async<ResourceMarketplaceProvider.Details> request, Duration timeoutDuration) {
        Async<OperationResult<ResourceMarketplaceProvider.Details>> result = Async.pending();
        TaskScheduler.ScheduledTask[] timeout = {null};
        request.whenComplete((details, failure) -> {
            if (request.isCancelled()) {
                result.fail(new Async.Cancellation());
                return;
            }
            if (result.complete(new OperationResult<>(details, failure))) {
                TaskScheduler.ScheduledTask scheduled = timeout[0];
                if (scheduled != null) scheduled.cancel();
            }
        });
        timeout[0] = scheduler.schedule(() -> result.complete(new OperationResult<>(null, null)), timeoutDuration);
        if (result.isDone() && timeout[0] != null) timeout[0].cancel();
        return result;
    }

    private Map<String, InstalledFile> installedFileIndex() {
        Map<String, InstalledFile> files = new LinkedHashMap<>();
        dataCache.installedFiles.values().stream().flatMap(List::stream).forEach(file ->
                files.putIfAbsent(fileKey(file.directory(), file.filename()), file));
        return files;
    }

    private Async<ResourceMarketplaceProvider.Details> cachedDetails(ResourceMarketplaceProvider.Card resource,
                                                                      OperationFence fence) {
        if (resource == null || marketplace == null) return Async.completed(null);
        if (!isCurrent(fence)) return staleOperation();
        String key = resourceKey(resource.provider(), resource.id());
        ResourceMarketplaceProvider.Details result = dataCache.resourceDetailResults.get(key);
        if (result != null) return Async.completed(result);
        Async<ResourceMarketplaceProvider.Details> cached = dataCache.resourceDetailRequests.get(key);
        if (cached != null) return view(cached);
        Async<ResourceMarketplaceProvider.Details> request = Async.pending();
        dataCache.resourceDetailRequests.put(key, request);
        dataCache.resourceDetailRequestFences.put(key, fence);
        dataCache.resourceDetailResources.put(key, resource);
        dataCache.resourceDetailQueue.addLast(key);
        request.onCancel(() -> {
            Async<ResourceMarketplaceProvider.Details> transport = dataCache.resourceDetailTransports.get(key);
            if (transport != null) {
                transport.cancel();
            } else if (dataCache.resourceDetailRequests.get(key) == request) {
                dataCache.resourceDetailRequests.remove(key);
                dataCache.resourceDetailRequestFences.remove(key);
                dataCache.resourceDetailResources.remove(key);
                dataCache.resourceDetailQueue.remove(key);
                drainResourceDetails();
            }
        });
        drainResourceDetails();
        return view(request);
    }

    private void drainResourceDetails() {
        while (dataCache.resourceDetailActiveRequests < RESOURCE_DETAILS_CONCURRENCY) {
            String key = dataCache.resourceDetailQueue.pollFirst();
            if (key == null) return;
            Async<ResourceMarketplaceProvider.Details> result = dataCache.resourceDetailRequests.get(key);
            ResourceMarketplaceProvider.Card resource = dataCache.resourceDetailResources.get(key);
            OperationFence fence = dataCache.resourceDetailRequestFences.get(key);
            if (result == null || result.isDone() || resource == null) {
                dataCache.resourceDetailResources.remove(key);
                dataCache.resourceDetailRequestFences.remove(key);
                continue;
            }
            dataCache.resourceDetailActiveRequests++;
            Async<ResourceMarketplaceProvider.Details> transport;
            try {
                transport = marketplace.details(resource.provider(), resource.id());
                if (transport == null) throw new IllegalStateException("Resource Metadata Is Unavailable");
            } catch (Throwable failure) {
                transport = Async.failed(failure);
            }
            Async<ResourceMarketplaceProvider.Details> activeTransport = transport;
            dataCache.resourceDetailTransports.put(key, activeTransport);
            activeTransport.whenComplete((details, failure) -> {
                if (dataCache.resourceDetailTransports.get(key) == activeTransport) {
                    dataCache.resourceDetailTransports.remove(key);
                }
                dataCache.resourceDetailActiveRequests = Math.max(0, dataCache.resourceDetailActiveRequests - 1);
                if (dataCache.resourceDetailRequests.get(key) != result) {
                    drainResourceDetails();
                    return;
                }
                if (fence == null || !isCurrent(fence)) {
                    dataCache.resourceDetailRequests.remove(key);
                    dataCache.resourceDetailRequestFences.remove(key);
                    dataCache.resourceDetailResources.remove(key);
                    result.fail(new Async.Cancellation());
                    drainResourceDetails();
                    return;
                }
                dataCache.resourceDetailRequests.remove(key);
                dataCache.resourceDetailRequestFences.remove(key);
                dataCache.resourceDetailResources.remove(key);
                if (failure == null && details != null && details.card() != null) {
                    dataCache.resourceDetailResults.put(key, details);
                    result.complete(details);
                } else if (failure == null) {
                    result.complete(details);
                } else {
                    result.fail(failure);
                }
                drainResourceDetails();
            });
        }
    }

    private Async<List<ServerModels.PteroFileObjectAttributes>> listResourceDirectory(String directory) {
        String key = directory == null || directory.isBlank() ? "/" : directory;
        Async<List<ServerModels.PteroFileObjectAttributes>> cached = dataCache.resourceDirectoryRequests.get(key);
        if (cached != null) return view(cached);
        Async<List<ServerModels.PteroFileObjectAttributes>> request = api.listFilesAllowMissingDirectory(serverId, key);
        dataCache.resourceDirectoryRequests.put(key, request);
        request.whenComplete((ignored, failure) -> {
            if (failure == null) return;
            if (dataCache.resourceDirectoryRequests.get(key) == request) dataCache.resourceDirectoryRequests.remove(key);
        });
        return view(request);
    }

    private Async<List<InstalledFile>> resolveMissingHashes(List<InstalledFile> files, OperationFence fence,
                                                             long requestGeneration) {
        List<InstalledFile> values = files == null ? List.of() : files.stream().filter(Objects::nonNull)
                .map(file -> {
                    BrowserRemotelyServerApi.FileHash cached = dataCache.resourceHashResults.get(hashPath(
                            fileKey(file.directory(), file.filename())));
                    if (cached == null || !cached.resolved()) return file;
                    return mergeHash(file, cached);
                }).toList();
        values.stream().filter(file -> !normalizeFingerprint(file.murmur2()).isBlank())
                .filter(file -> fingerprintValue(file.murmur2()) == null)
                .forEach(file -> dataCache.resourceHashFailures.put(hashPath(fileKey(file.directory(), file.filename())),
                        "Invalid Murmur2 Fingerprint"));
        List<String> paths = values.stream().filter(file -> normalizeHash(file.sha1()).isBlank()
                        || normalizeFingerprint(file.murmur2()).isBlank())
                .map(file -> fileKey(file.directory(), file.filename())).filter(path -> !path.isBlank()).distinct().toList();
        if (paths.isEmpty()) return Async.completed(values);
        paths.forEach(path -> dataCache.resourceHashFailures.remove(hashPath(path)));
        return resolveHashBatches(paths, fence, requestGeneration).thenCompose(batchResults -> {
            if (!isDataCurrent(fence) || requestGeneration != dataCache.inventoryRequestGeneration) return staleOperation();
            Map<String, BrowserRemotelyServerApi.FileHash> resolved = new LinkedHashMap<>();
            for (HashBatchResult batch : batchResults) {
                Set<String> returned = new LinkedHashSet<>();
                if (!batch.failure().isBlank()) {
                    batch.paths().forEach(path -> dataCache.resourceHashFailures.put(hashPath(path), batch.failure()));
                }
                for (BrowserRemotelyServerApi.FileHash hash : batch.hashes()) {
                    if (hash == null) continue;
                    String path = hashPath(hash.path());
                    if (!batch.paths().contains(path)) continue;
                    returned.add(path);
                    if (hash.resolved()) {
                        resolved.putIfAbsent(path, hash);
                        dataCache.resourceHashResults.put(path, hash);
                        dataCache.resourceHashFailures.remove(path);
                    } else {
                        dataCache.resourceHashFailures.put(path, first(hash.error(), hash.status(), "Hash Resolution Failed"));
                    }
                }
                batch.paths().stream().map(BrowserResourceBrowserContext::hashPath).filter(path -> !returned.contains(path))
                        .filter(path -> !dataCache.resourceHashFailures.containsKey(path))
                        .forEach(path -> dataCache.resourceHashFailures.put(path, "Hash Not Returned"));
            }
            List<InstalledFile> merged = values.stream().map(file -> {
                if (!normalizeHash(file.sha1()).isBlank() && !normalizeFingerprint(file.murmur2()).isBlank()) return file;
                BrowserRemotelyServerApi.FileHash hash = resolved.get(hashPath(fileKey(file.directory(), file.filename())));
                if (hash == null) return file;
                return new InstalledFile(file.provider(), file.projectId(), file.versionId(), file.directory(), file.filename(),
                        first(normalizeHash(file.sha1()), normalizeHash(hash.sha1())),
                        first(normalizeFingerprint(file.murmur2()), normalizeFingerprint(hash.murmur2())),
                        file.updateAvailable());
            }).toList();
            return Async.completed(merged);
        });
    }

    private Async<List<HashBatchResult>> resolveHashBatches(List<String> paths, OperationFence fence,
                                                            long requestGeneration) {
        Async<List<HashBatchResult>> result = Async.pending();
        HashBatchScheduler scheduler = new HashBatchScheduler(paths, fence, requestGeneration, result);
        result.onCancel(scheduler::cancel);
        scheduler.advance();
        return result;
    }

    private static InstalledFile mergeHash(InstalledFile file, BrowserRemotelyServerApi.FileHash hash) {
        if (file == null || hash == null) return file;
        return new InstalledFile(file.provider(), file.projectId(), file.versionId(), file.directory(), file.filename(),
                first(normalizeHash(file.sha1()), normalizeHash(hash.sha1())),
                first(normalizeFingerprint(file.murmur2()), normalizeFingerprint(hash.murmur2())),
                file.updateAvailable());
    }

    private Async<HashBatchResult> resolveHashBatch(List<String> paths) {
        try {
            return api.resolveFileHashes(serverId, paths)
                    .handle((hashes, failure) -> new HashBatchResult(paths, hashes == null ? List.of() : hashes,
                            failure == null ? "" : failureMessage(failure, "Hash Resolution Failed")));
        } catch (Throwable failure) {
            return Async.completed(new HashBatchResult(paths, List.of(), failureMessage(failure, "Hash Resolution Failed")));
        }
    }

    static <T> Async<List<DirectoryResult<T>>> collectDirectoryResults(List<Async<List<T>>> requests) {
        if (requests == null || requests.isEmpty()) return Async.completed(List.of());
        List<Async<DirectoryResult<T>>> outcomes = requests.stream()
                .map(request -> request.handle((values, failure) -> new DirectoryResult<>(values, failure)))
                .toList();
        return Async.allOf(outcomes.toArray(Async[]::new))
                .thenApply(ignored -> outcomes.stream().map(Async::value).toList());
    }

    List<DirectoryResult<ServerModels.PteroFileObjectAttributes>> reconcileDirectoryResults(
            List<String> directories, List<DirectoryResult<ServerModels.PteroFileObjectAttributes>> results) {
        Set<String> activeDirectories = new LinkedHashSet<>(directories == null ? List.of() : directories);
        dataCache.resourceFailureMessages.keySet().removeIf(path -> !isDiscoveryFailure(path) && !activeDirectories.contains(path));
        dataCache.resourceDirectorySnapshots.keySet().removeIf(path -> !activeDirectories.contains(path));
        if (directories == null || directories.isEmpty()) return List.of();
        List<DirectoryResult<ServerModels.PteroFileObjectAttributes>> retained = new ArrayList<>();
        for (int index = 0; index < directories.size(); index++) {
            String directory = directories.get(index);
            DirectoryResult<ServerModels.PteroFileObjectAttributes> result = results != null && index < results.size()
                    ? results.get(index) : null;
            Throwable failure = result == null ? new IllegalStateException("Resource Folder Unavailable") : result.failure();
            List<ServerModels.PteroFileObjectAttributes> values;
            if (failure == null) {
                values = result.values();
                dataCache.resourceDirectorySnapshots.put(directory, values);
            } else {
                values = dataCache.resourceDirectorySnapshots.getOrDefault(directory, List.of());
            }
            recordResourceFailure(directory, failure);
            retained.add(new DirectoryResult<>(values, failure));
        }
        return List.copyOf(retained);
    }

    private void recordResourceFailure(String path, Throwable failure) {
        if (path == null || path.isBlank()) return;
        if (failure == null) {
            dataCache.resourceFailureMessages.remove(path);
        } else {
            dataCache.resourceFailureMessages.put(path, resourceFailureMessage(failure));
        }
    }

    List<ResourceFailure> resourceFailureSnapshot() {
        List<ResourceFailure> failures = new ArrayList<>();
        dataCache.resourceFailureMessages.forEach((path, message) -> failures.add(new ResourceFailure(path, message)));
        dataCache.resourceHashFailures.forEach((path, message) -> failures.add(new ResourceFailure("Hash " + path, message)));
        dataCache.resourceDetailFailures.forEach((path, message) -> failures.add(new ResourceFailure("Metadata " + path, message)));
        dataCache.providerLookupFailures.forEach((path, message) -> failures.add(new ResourceFailure("Provider " + path, message)));
        return List.copyOf(failures);
    }

    private static boolean isDiscoveryFailure(String path) {
        return "server.properties".equals(path) || "/".equals(path);
    }

    private static String resourceFailureMessage(Throwable failure) {
        return failureMessage(failure, "Resource Folder Unavailable");
    }

    private static boolean isResourceFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".jar") || normalized.endsWith(".zip") || normalized.endsWith(".jar.disabled")
                || normalized.endsWith(".zip.disabled");
    }

    private static ResourceMarketplaceProvider.Card fallbackCard(InstalledFile installed, ResourceType type, String filename) {
        return new ResourceMarketplaceProvider.Card(installed.provider(), installed.projectId(), installed.projectId(),
                type.getModrinthProjectType(), filename, "Installed resource file", List.of(), 0, 0,
                null, null, true, installed.updateAvailable());
    }

    private void notifyResourceListeners(OperationFence fence) {
        scheduleResourceNotification(fence, true);
    }

    private void notifyProgressiveInventoryListeners(OperationFence fence) {
        scheduleResourceNotification(fence, false);
    }

    private void scheduleResourceNotification(OperationFence fence, boolean legacy) {
        if (!isCurrent(fence)) return;
        synchronized (this) {
            pendingResourceNotificationFence = fence;
            pendingLegacyResourceNotification |= legacy;
            if (pendingResourceNotification != null) pendingResourceNotification.cancel();
            pendingResourceNotification = scheduler.schedule(this::dispatchResourceListeners, Duration.ofMillis(50));
        }
    }

    private void dispatchResourceListeners() {
        OperationFence fence;
        List<Runnable> listeners;
        List<Consumer<BrowserResourceInventory>> inventoryListeners;
        boolean notifyLegacy;
        synchronized (this) {
            pendingResourceNotification = null;
            fence = pendingResourceNotificationFence;
            pendingResourceNotificationFence = null;
            notifyLegacy = pendingLegacyResourceNotification;
            pendingLegacyResourceNotification = false;
            listeners = List.copyOf(resourceListeners);
            inventoryListeners = List.copyOf(this.inventoryListeners);
        }
        if (!isCurrent(fence)) return;
        BrowserResourceInventory snapshot = inventorySnapshot();
        inventoryListeners.forEach(listener -> listener.accept(snapshot));
        if (notifyLegacy) listeners.forEach(Runnable::run);
    }

    private Async<InstalledResult> resolveInstalled(List<InstalledFile> files, OperationFence fence,
                                                     long inventoryGeneration) {
        if (files == null || files.isEmpty()) return Async.completed(new InstalledResult(List.of(), List.of()));
        InventoryIndex inventory = inventoryIndex(files);
        if ((inventory.hashedFiles().isEmpty() && inventory.fingerprintedFiles().isEmpty()) || marketplace == null) {
            return Async.completed(new InstalledResult(List.of(), inventory.files()));
        }
        List<String> providers = inventoryProviders();
        dataCache.providerOrder = providers;
        dataCache.providerMatches.clear();
        dataCache.providerLookupFailures.clear();
        List<String> fingerprints = inventory.fingerprintedFiles().keySet().stream().toList();
        for (String provider : providers) {
            AsyncResourceProvider source;
            try {
                source = marketplace.source(provider);
            } catch (Throwable failure) {
                recordProviderLookupFailure(provider, false, inventory, failure);
                recordProviderLookupFailure(provider, true, inventory, failure);
                continue;
            }
            if (source == null) {
                IllegalStateException failure = new IllegalStateException("Resource Provider Is Unavailable");
                recordProviderLookupFailure(provider, false, inventory, failure);
                recordProviderLookupFailure(provider, true, inventory, failure);
                continue;
            }
            boolean supportsHash = false;
            boolean supportsFingerprint = false;
            try {
                supportsHash = source.supportsHashLookup();
                supportsFingerprint = source.supportsFingerprintLookup();
            } catch (Throwable failure) {
                recordProviderLookupFailure(provider, false, inventory, failure);
                recordProviderLookupFailure(provider, true, inventory, failure);
            }
            Map<String, OnlineResourceVersion> cachedHashes = cachedProviderMatches(provider, false,
                    inventory.hashedFiles().keySet());
            if (!cachedHashes.isEmpty()) {
                applyProviderLookup(new ProviderLookupResult(provider, false, cachedHashes, null), inventory, fence,
                        inventoryGeneration);
            }
            Map<String, OnlineResourceVersion> cachedFingerprints = cachedProviderMatches(provider, true,
                    inventory.fingerprintedFiles().keySet());
            if (!cachedFingerprints.isEmpty()) {
                applyProviderLookup(new ProviderLookupResult(provider, true, cachedFingerprints, null), inventory, fence,
                        inventoryGeneration);
            }
            if (supportsHash && !inventory.hashedFiles().isEmpty()) {
                List<String> unresolved = inventory.hashedFiles().keySet().stream()
                        .filter(hash -> !dataCache.providerHashResults.containsKey(providerResultKey(provider, hash)))
                        .toList();
                if (!unresolved.isEmpty()) {
                    scheduleProviderLookup(provider, false,
                            () -> marketplace.searchByHashes(provider, unresolved), inventory, fence, inventoryGeneration);
                }
            }
            if (supportsFingerprint && !fingerprints.isEmpty()) {
                List<Long> unresolved = fingerprints.stream()
                        .filter(hash -> !dataCache.providerFingerprintResults.containsKey(providerResultKey(provider, hash)))
                        .map(BrowserResourceBrowserContext::fingerprintValue).filter(Objects::nonNull).toList();
                if (!unresolved.isEmpty()) {
                    scheduleProviderLookup(provider, true, () -> marketplace.searchByFingerprints(provider, unresolved), inventory,
                            fence, inventoryGeneration);
                }
            }
        }
        return Async.completed(new InstalledResult(List.of(), inventory.files()));
    }

    private Map<String, OnlineResourceVersion> cachedProviderMatches(String provider, boolean fingerprint,
                                                                       Set<String> keys) {
        Map<String, OnlineResourceVersion> cached = new LinkedHashMap<>();
        if (keys == null) return cached;
        for (String key : keys) {
            String normalized = fingerprint ? normalizeFingerprint(key) : normalizeHash(key);
            if (normalized.isBlank()) continue;
            OnlineResourceVersion value = fingerprint
                    ? dataCache.providerFingerprintResults.get(providerResultKey(provider, normalized))
                    : dataCache.providerHashResults.get(providerResultKey(provider, normalized));
            if (value != null) cached.put(normalized, value);
        }
        return cached;
    }

    private void scheduleProviderLookup(String provider, boolean fingerprint, Supplier<Async<Map<String, OnlineResourceVersion>>> lookup,
                                        InventoryIndex inventory, OperationFence fence, long inventoryGeneration) {
        String requestKey = providerResultKey(provider, "request:" + (fingerprint ? "fingerprint" : "hash"));
        Async<ProviderLookupResult> existing = dataCache.providerLookupRequests.get(requestKey);
        if (existing != null) return;
        Async<ProviderLookupResult> request;
        try {
            Async<Map<String, OnlineResourceVersion>> result = lookup.get();
            request = result == null ? Async.completed(new ProviderLookupResult(provider, fingerprint, Map.of(),
                    new IllegalStateException("Resource Lookup Is Unavailable")))
                    : result.handle((matches, failure) -> new ProviderLookupResult(provider, fingerprint, matches, failure));
        } catch (Throwable failure) {
            request = Async.completed(new ProviderLookupResult(provider, fingerprint, Map.of(), failure));
        }
        Async<ProviderLookupResult> activeRequest = request;
        dataCache.providerLookupRequests.put(requestKey, activeRequest);
        activeRequest.whenComplete((result, failure) -> {
            if (dataCache.providerLookupRequests.get(requestKey) == activeRequest) {
                dataCache.providerLookupRequests.remove(requestKey);
            }
            if (!isInventoryCurrent(fence, inventoryGeneration)) return;
            ProviderLookupResult resolved = failure == null ? result
                    : new ProviderLookupResult(provider, fingerprint, Map.of(), failure);
            applyProviderLookup(resolved, inventory, fence, inventoryGeneration);
        });
    }

    private void applyProviderLookup(ProviderLookupResult result, InventoryIndex inventory, OperationFence fence,
                                     long inventoryGeneration) {
        if (result == null) return;
        Map<String, List<InstalledFile>> files = result.fingerprint() ? inventory.fingerprintedFiles() : inventory.hashedFiles();
        if (result.failure() != null) {
            recordProviderLookupFailure(result.provider(), result.fingerprint(), inventory, result.failure());
            notifyProgressiveInventoryListeners(fence);
            return;
        }
        files.values().stream().flatMap(List::stream).forEach(file ->
                dataCache.providerLookupFailures.remove(providerFailureKey(result.provider(), result.fingerprint(),
                        fileKey(file.directory(), file.filename()))));
        Map<String, OnlineResourceVersion> cache = result.fingerprint()
                ? dataCache.providerFingerprintResults : dataCache.providerHashResults;
        for (Map.Entry<String, OnlineResourceVersion> match : result.matches().entrySet()) {
            String lookupKey = result.fingerprint() ? normalizeFingerprint(match.getKey()) : normalizeHash(match.getKey());
            OnlineResourceVersion version = match.getValue();
            if (lookupKey.isBlank() || version == null || version.projectId == null || version.projectId.isBlank()) continue;
            cache.put(providerResultKey(result.provider(), lookupKey), version);
            for (InstalledFile file : files.getOrDefault(lookupKey, List.of())) {
                String path = fileKey(file.directory(), file.filename());
                ProviderMatch current = dataCache.providerMatches.get(path);
                if (current != null && providerRank(result.provider()) >= providerRank(current.provider())) continue;
                ProviderMatch identity = new ProviderMatch(result.provider(), version);
                dataCache.providerMatches.put(path, identity);
                InstalledFile identified = new InstalledFile(result.provider(), version.projectId, version.id,
                        file.directory(), file.filename(), file.sha1(), file.murmur2(), file.updateAvailable());
                updateInstalledFile(identified);
                hydrateLatestState(identified, identity, fence, inventoryGeneration);
            }
        }
        refreshResourceEntries(fence, inventoryGeneration);
        notifyProgressiveInventoryListeners(fence);
    }

    private void hydrateLatestState(InstalledFile file, ProviderMatch identity, OperationFence fence,
                                    long inventoryGeneration) {
        Async<OnlineResourceVersion> latest;
        try {
            latest = latestVersion(identity.provider(), identity.version().projectId, type(file.directory()), fence);
        } catch (Throwable failure) {
            latest = Async.completed(null);
        }
        latest.whenComplete((current, failure) -> {
            if (failure != null || !isInventoryCurrent(fence, inventoryGeneration)) return;
            String path = fileKey(file.directory(), file.filename());
            if (!Objects.equals(dataCache.providerMatches.get(path), identity)) return;
            InstalledFile updated = new InstalledFile(file.provider(), file.projectId(), file.versionId(), file.directory(),
                    file.filename(), file.sha1(), file.murmur2(), current != null && !Objects.equals(identity.version().id, current.id));
            updateInstalledFile(updated);
            refreshResourceEntries(fence, inventoryGeneration);
            notifyProgressiveInventoryListeners(fence);
        });
    }

    private void recordProviderLookupFailure(String provider, boolean fingerprint, InventoryIndex inventory, Throwable failure) {
        if (provider == null || provider.isBlank()) return;
        Map<String, List<InstalledFile>> files = fingerprint ? inventory.fingerprintedFiles() : inventory.hashedFiles();
        String capability = fingerprint ? "Murmur2" : "SHA1";
        String message = provider + " " + capability + " Lookup Failed: " + failureMessage(failure, "Hash Lookup Failed");
        files.values().stream().flatMap(List::stream).forEach(file -> {
            String path = fileKey(file.directory(), file.filename());
            dataCache.providerLookupFailures.put(providerFailureKey(provider, fingerprint, path), message);
        });
    }

    private int providerRank(String provider) {
        int index = dataCache.providerOrder.indexOf(provider);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static String providerFailureKey(String provider, boolean fingerprint, String path) {
        return Objects.requireNonNullElse(provider, "") + " " + (fingerprint ? "Murmur2" : "SHA1") + " "
                + hashPath(path);
    }

    private static String providerResultKey(String provider, String value) {
        return Objects.requireNonNullElse(provider, "").toLowerCase(Locale.ROOT) + "|"
                + Objects.requireNonNullElse(value, "").toLowerCase(Locale.ROOT);
    }

    private List<String> inventoryProviders() {
        List<String> providers = marketplace.providers() == null ? List.of() : List.copyOf(marketplace.providers());
        return prioritizeProvider(providers, lastProvider(providerPreferenceKey()));
    }

    static List<String> prioritizeProvider(List<String> providers, String preferred) {
        if (providers == null || providers.isEmpty()) return List.of();
        if (preferred == null || !providers.contains(preferred) || Objects.equals(providers.getFirst(), preferred)) return List.copyOf(providers);
        List<String> ordered = new ArrayList<>();
        ordered.add(preferred);
        providers.stream().filter(provider -> !Objects.equals(provider, preferred)).forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private String providerPreferenceKey() {
        if (loader != null && !loader.isBlank()) return loader.toLowerCase(Locale.ROOT);
        return isServer() ? "server" : "default";
    }

    static InventoryIndex inventoryIndex(List<InstalledFile> files) {
        List<InstalledFile> physicalFiles = files == null ? List.of() : files.stream().filter(Objects::nonNull).toList();
        Map<String, List<InstalledFile>> hashedFiles = new LinkedHashMap<>();
        Map<String, List<InstalledFile>> fingerprintedFiles = new LinkedHashMap<>();
        for (InstalledFile file : physicalFiles) {
            String sha1 = normalizeHash(file.sha1());
            if (!sha1.isBlank()) hashedFiles.computeIfAbsent(sha1, ignored -> new ArrayList<>()).add(file);
            String murmur2 = normalizeFingerprint(file.murmur2());
            if (!murmur2.isBlank() && fingerprintValue(murmur2) != null) {
                fingerprintedFiles.computeIfAbsent(murmur2, ignored -> new ArrayList<>()).add(file);
            }
        }
        hashedFiles.replaceAll((hash, matching) -> List.copyOf(matching));
        fingerprintedFiles.replaceAll((fingerprint, matching) -> List.copyOf(matching));
        return new InventoryIndex(List.copyOf(physicalFiles), Map.copyOf(hashedFiles), Map.copyOf(fingerprintedFiles));
    }

    static Map<String, ProviderMatch> authoritativeMatches(List<String> providers,
                                                           List<Map<String, OnlineResourceVersion>> providerMatches) {
        Map<String, ProviderMatch> authoritative = new LinkedHashMap<>();
        if (providers == null || providerMatches == null) return authoritative;
        for (int index = 0; index < Math.min(providers.size(), providerMatches.size()); index++) {
            String provider = providers.get(index);
            Map<String, OnlineResourceVersion> matches = providerMatches.get(index);
            if (provider == null || provider.isBlank() || matches == null) continue;
            for (Map.Entry<String, OnlineResourceVersion> match : matches.entrySet()) {
                String hash = normalizeHash(match.getKey());
                OnlineResourceVersion version = match.getValue();
                if (hash.isBlank() || version == null || version.projectId == null || version.projectId.isBlank()) continue;
                authoritative.putIfAbsent(hash, new ProviderMatch(provider, version));
            }
        }
        return authoritative;
    }

    private Async<OnlineResourceVersion> latestVersion(String provider, String projectId, ResourceType type,
                                                       OperationFence fence) {
        if (!isDataCurrent(fence)) return staleOperation();
        String key = latestResultKey(provider, projectId, type);
        OnlineResourceVersion cached = dataCache.latestVersionResults.get(key);
        if (cached != null) return Async.completed(cached);
        if (dataCache.latestVersionMisses.contains(key)) return Async.completed(null);
        Async<OnlineResourceVersion> existing = dataCache.latestVersionRequests.get(key);
        if (existing != null) {
            OperationFence existingFence = dataCache.latestVersionRequestFences.get(key);
            if (existingFence == null || isDataCurrent(existingFence)) return view(existing);
            cancel(existing);
            dataCache.latestVersionRequests.remove(key);
            dataCache.latestVersionRequestFences.remove(key);
        }
        Async<OnlineResourceVersion> request;
        try {
            AsyncResourceProvider source = marketplace.source(provider);
            request = source.getLatestCompatibleVersionAsync(projectId, providerLoaderTokens(type), localBaseVersionIds(), type)
                    .thenApply(latest -> latest == null || latest.isEmpty() ? null : latest.get());
        } catch (Throwable failure) {
            request = Async.failed(failure);
        }
        Async<OnlineResourceVersion> activeRequest = request;
        dataCache.latestVersionRequests.put(key, activeRequest);
        dataCache.latestVersionRequestFences.put(key, fence);
        activeRequest.whenComplete((latest, failure) -> {
            if (dataCache.latestVersionRequests.get(key) == activeRequest) {
                dataCache.latestVersionRequests.remove(key);
                dataCache.latestVersionRequestFences.remove(key);
            }
            if (!isDataCurrent(fence)) return;
            if (failure != null) return;
            if (latest == null) dataCache.latestVersionMisses.add(key);
            else dataCache.latestVersionResults.put(key, latest);
        });
        return view(activeRequest);
    }

    private String latestResultKey(String provider, String projectId, ResourceType type) {
        return providerResultKey(provider, Objects.requireNonNullElse(projectId, "")) + "|"
                + (type == null ? "" : type.name()) + "|" + Objects.requireNonNullElse(version, "") + "|"
                + Objects.requireNonNullElse(loader, "");
    }

    private boolean hasInstalled(ResourceMarketplaceProvider.Card resource) {
        return supportsResourceResolution(resource) && dataCache.installedFiles.containsKey(resourceKey(resource));
    }

    private boolean hasUpdate(ResourceMarketplaceProvider.Card resource) {
        return hasInstalled(resource) && dataCache.installedFiles.get(resourceKey(resource)).stream().anyMatch(InstalledFile::updateAvailable);
    }

    private ResourceMarketplaceProvider.Card installedCard(ResourceMarketplaceProvider.Card resource) {
        if (resource == null || !hasInstalled(resource)) return resource;
        boolean update = dataCache.installedFiles.get(resourceKey(resource)).stream().anyMatch(InstalledFile::updateAvailable);
        return new ResourceMarketplaceProvider.Card(resource.provider(), resource.id(), resource.slug(), resource.type(), resource.title(),
                resource.description(), resource.authors(), resource.downloads(), resource.followers(), resource.iconUrl(), resource.bannerUrl(),
                true, update, resource.categories(), resource.gameVersions(), resource.loaders(), resource.clientSide(), resource.serverSide());
    }

    private static ResourceType type(String directory) {
        String value = directory == null ? "" : directory.toLowerCase(Locale.ROOT);
        if (value.contains("resourcepack")) return ResourceType.RESOURCE_PACK;
        if (value.contains("shaderpack")) return ResourceType.SHADER_PACK;
        if (value.contains("datapack")) return ResourceType.DATA_PACK;
        return value.contains("plugin") ? ResourceType.PLUGIN : ResourceType.MOD;
    }

    private static String normalizedWorldName(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        List<String> parts = new ArrayList<>();
        for (String part : normalized.split("/")) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) continue;
            parts.add(part);
        }
        return parts.isEmpty() ? "world" : String.join("/", parts);
    }

    private static String resolveWorldName(String content) {
        if (content == null || content.isBlank()) return "world";
        for (String line : content.replace("\r", "").split("\n")) {
            String value = line.trim();
            if (value.isBlank() || value.startsWith("#")) continue;
            int separator = value.indexOf('=');
            if (separator <= 0 || !"level-name".equalsIgnoreCase(value.substring(0, separator).trim())) continue;
            return normalizedWorldName(value.substring(separator + 1));
        }
        return "world";
    }

    private static String normalizeHash(String hash) {
        if (hash == null) return "";
        String normalized = hash.strip().toLowerCase(Locale.ROOT);
        return normalized.startsWith("sha1:") ? normalized.substring(5) : normalized;
    }

    private static String normalizeFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return "";
        Long value = fingerprintValue(fingerprint);
        return value == null ? fingerprint.strip() : Long.toString(value);
    }

    private static Long fingerprintValue(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return null;
        try {
            long value = Long.parseLong(fingerprint.strip());
            return value >= 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Instant publishedAt(ResourceMarketplaceProvider.Version version) {
        if (version == null || version.publishedAt() == null || version.publishedAt().isBlank()) return Instant.EPOCH;
        try {
            return Instant.parse(version.publishedAt());
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static String resourceKey(ResourceMarketplaceProvider.Card resource) {
        return resource == null ? "" : resourceKey(resource.provider(), resource.id());
    }

    private static String resourceKey(String provider, String projectId) {
        return Objects.requireNonNullElse(provider, "") + ":" + Objects.requireNonNullElse(projectId, "");
    }

    private static String fileKey(String directory, String filename) {
        String normalizedDirectory = directory == null ? "" : directory.strip().replace('\\', '/');
        while (normalizedDirectory.startsWith("/")) normalizedDirectory = normalizedDirectory.substring(1);
        while (normalizedDirectory.endsWith("/")) normalizedDirectory = normalizedDirectory.substring(0, normalizedDirectory.length() - 1);
        String normalizedFilename = filename == null ? "" : filename.strip().replace('\\', '/');
        while (normalizedFilename.startsWith("/")) normalizedFilename = normalizedFilename.substring(1);
        return normalizedDirectory.isBlank() ? normalizedFilename : normalizedDirectory + "/" + normalizedFilename;
    }

    private static String hashPath(String value) {
        String normalized = Objects.requireNonNullElse(value, "").strip().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.contains("//")) normalized = normalized.replace("//", "/");
        return normalized;
    }

    private Async<List<InstalledResource>> refreshInventory(boolean notify, OperationFence fence) {
        if (!isCurrent(fence)) return staleOperation();
        clearInventoryCache();
        dataCache.inventoryFence = fence;
        long requestGeneration = nextInventoryRequestGeneration();
        Async<List<InstalledResource>> request = loadInstalledResources(notify, fence, requestGeneration);
        dataCache.inventoryRequest = request;
        request.whenComplete((ignored, failure) -> {
            if (failure == null) return;
            if (dataCache.inventoryRequest == request) dataCache.inventoryRequest = null;
        });
        return view(request);
    }

    private void clearInventoryCache() {
        List<Async<List<ServerModels.PteroFileObjectAttributes>>> directoryRequests =
                List.copyOf(dataCache.resourceDirectoryRequests.values());
        List<Async<ResourceMarketplaceProvider.Details>> detailRequests = List.copyOf(dataCache.resourceDetailRequests.values());
        List<Async<ResourceMarketplaceProvider.Details>> detailTransports = List.copyOf(dataCache.resourceDetailTransports.values());
        List<Async<ProviderLookupResult>> providerRequests = List.copyOf(dataCache.providerLookupRequests.values());
        List<Async<OnlineResourceVersion>> latestRequests = List.copyOf(dataCache.latestVersionRequests.values());
        cancelPendingResourceNotification();
        cancel(dataCache.inventoryRequest);
        cancel(dataCache.browserInventoryRequest);
        cancel(dataCache.inventoryHydrationRequest);
        cancel(dataCache.modpackProfileRequest);
        cancel(dataCache.resourceDirectoriesRequest);
        dataCache.inventoryRequest = null;
        dataCache.inventoryFence = null;
        dataCache.resourceDirectoriesRequest = null;
        dataCache.resourceDirectoriesFence = null;
        nextInventoryRequestGeneration();
        dataCache.installedFiles.clear();
        dataCache.browserInventoryRequest = null;
        dataCache.inventoryHydrationRequest = null;
        dataCache.modpackProfileRequest = null;
        dataCache.resourceEntries = List.of();
        dataCache.resourceFiles = List.of();
        dataCache.modpackProfile = null;
        directoryRequests.forEach(BrowserResourceBrowserContext::cancel);
        detailRequests.forEach(BrowserResourceBrowserContext::cancel);
        detailTransports.forEach(BrowserResourceBrowserContext::cancel);
        providerRequests.forEach(BrowserResourceBrowserContext::cancel);
        latestRequests.forEach(BrowserResourceBrowserContext::cancel);
        dataCache.resourceDirectoryRequests.clear();
        dataCache.resourceDirectorySnapshots.clear();
        dataCache.resourceHashFailures.clear();
        dataCache.resourceDetailFailures.clear();
        dataCache.resourceDetailQueue.clear();
        dataCache.resourceDetailRequests.clear();
        dataCache.resourceDetailRequestFences.clear();
        dataCache.resourceDetailTransports.clear();
        dataCache.resourceDetailResources.clear();
        dataCache.providerLookupRequests.clear();
        dataCache.providerMatches.clear();
        dataCache.providerLookupFailures.clear();
        dataCache.latestVersionRequests.clear();
        dataCache.latestVersionRequestFences.clear();
        dataCache.providerOrder = List.of();
        dataCache.resourceFailureMessages.keySet().removeIf(path -> !isDiscoveryFailure(path));
    }

    void invalidateFileCache() {
        captureFence();
        clearInventoryCache();
    }

    private static void clearDataCache(ResourceDataCache cache) {
        List<Async<List<ServerModels.PteroFileObjectAttributes>>> directoryRequests =
                List.copyOf(cache.resourceDirectoryRequests.values());
        List<Async<ResourceMarketplaceProvider.Details>> detailRequests = List.copyOf(cache.resourceDetailRequests.values());
        List<Async<ResourceMarketplaceProvider.Details>> detailTransports = List.copyOf(cache.resourceDetailTransports.values());
        List<Async<ProviderLookupResult>> providerRequests = List.copyOf(cache.providerLookupRequests.values());
        List<Async<OnlineResourceVersion>> latestRequests = List.copyOf(cache.latestVersionRequests.values());
        cancel(cache.inventoryRequest);
        cancel(cache.browserInventoryRequest);
        cancel(cache.inventoryHydrationRequest);
        cancel(cache.modpackProfileRequest);
        cancel(cache.resourceDirectoriesRequest);
        directoryRequests.forEach(BrowserResourceBrowserContext::cancel);
        detailRequests.forEach(BrowserResourceBrowserContext::cancel);
        detailTransports.forEach(BrowserResourceBrowserContext::cancel);
        providerRequests.forEach(BrowserResourceBrowserContext::cancel);
        latestRequests.forEach(BrowserResourceBrowserContext::cancel);
        cache.inventoryRequest = null;
        cache.inventoryFence = null;
        cache.resourceDirectoriesRequest = null;
        cache.resourceDirectoriesFence = null;
        cache.inventoryRequestGeneration++;
        cache.installedFiles.clear();
        cache.browserInventoryRequest = null;
        cache.inventoryHydrationRequest = null;
        cache.modpackProfileRequest = null;
        cache.resourceEntries = List.of();
        cache.resourceFiles = List.of();
        cache.modpackProfile = null;
        cache.resourceDirectoryRequests.clear();
        cache.resourceHashFailures.clear();
        cache.resourceDetailFailures.clear();
        cache.resourceDetailQueue.clear();
        cache.resourceDetailRequests.clear();
        cache.resourceDetailRequestFences.clear();
        cache.resourceDetailTransports.clear();
        cache.resourceDetailResources.clear();
        cache.resourceDetailResults.clear();
        cache.resourceHashResults.clear();
        cache.providerLookupRequests.clear();
        cache.providerHashResults.clear();
        cache.providerFingerprintResults.clear();
        cache.providerMatches.clear();
        cache.providerLookupFailures.clear();
        cache.latestVersionRequests.clear();
        cache.latestVersionRequestFences.clear();
        cache.latestVersionResults.clear();
        cache.latestVersionMisses.clear();
        cache.providerOrder = List.of();
        cache.resourceFailureMessages.clear();
        cache.resourceDirectorySnapshots.clear();
    }

    private long nextInventoryRequestGeneration() {
        return ++dataCache.inventoryRequestGeneration;
    }

    private static <T> Async<T> staleOperation() {
        return Async.failed(new Async.Cancellation());
    }

    private <T> Async<T> view(Async<T> shared) {
        if (shared == null) return Async.completed(null);
        Async<T> result = Async.pending();
        SharedRequest request;
        synchronized (this) {
            request = sharedViews.get(shared);
            if (request == null) request = sharedRequests.get(shared);
            if (request == null) {
                request = new SharedRequest(shared);
                sharedRequests.put(shared, request);
            }
            request.subscribers++;
            sharedViews.put(result, request);
        }
        SharedRequest subscription = request;
        shared.whenComplete((value, failure) -> {
            if (!result.isCancelled()) {
                if (shared.isCancelled()) result.fail(new Async.Cancellation());
                else if (failure == null) result.complete(value);
                else result.fail(failure);
            }
            releaseSharedView(result, subscription);
        });
        result.onCancel(() -> releaseSharedView(result, subscription));
        return result;
    }

    private void releaseSharedView(Async<?> view, SharedRequest request) {
        boolean cancel = false;
        synchronized (this) {
            if (sharedViews.remove(view) == null || request.subscribers <= 0) return;
            request.subscribers--;
            if (request.subscribers == 0) {
                sharedRequests.remove(request.shared, request);
                cancel = !request.shared.isDone() && !ownsSharedRequest(request.shared);
            }
        }
        if (cancel) request.shared.cancel();
    }

    private boolean ownsSharedRequest(Async<?> shared) {
        if (shared == null) return false;
        ResourceDataCache cache = dataCache;
        if (cache.inventoryRequest == shared || cache.browserInventoryRequest == shared
                || cache.inventoryHydrationRequest == shared || cache.modpackProfileRequest == shared
                || cache.resourceDirectoriesRequest == shared) return true;
        return cache.resourceDirectoryRequests.values().stream().anyMatch(value -> value == shared)
                || cache.resourceDetailRequests.values().stream().anyMatch(value -> value == shared)
                || cache.resourceDetailTransports.values().stream().anyMatch(value -> value == shared)
                || cache.providerLookupRequests.values().stream().anyMatch(value -> value == shared)
                || cache.latestVersionRequests.values().stream().anyMatch(value -> value == shared);
    }

    @Override
    public void invalidate() {
        retireResourceWork();
    }

    void invalidateForResourceSession() {
        retireResourceWork();
    }

    private void retireResourceWork() {
        cancelPendingResourceNotification();
        scheduler.cancelAll();
        clearDataCache(dataCache);
        modpackCapabilities.clear();
        synchronized (this) {
            sharedRequests.clear();
            sharedViews.clear();
        }
        cacheKey = null;
        lifecycleGeneration++;
        if (lifecycleGeneration <= 0) lifecycleGeneration = 1L;
    }

    private static void cancel(Async<?> request) {
        if (request != null && !request.isDone()) request.cancel();
    }

    private void cancelPendingResourceNotification() {
        synchronized (this) {
            if (pendingResourceNotification != null) pendingResourceNotification.cancel();
            if (pendingResourceReconciliation != null) pendingResourceReconciliation.cancel();
            pendingResourceNotification = null;
            pendingResourceNotificationFence = null;
            pendingLegacyResourceNotification = false;
            pendingResourceReconciliation = null;
            pendingResourceReconciliationFence = null;
            pendingResourceReconciliationGeneration = 0L;
        }
    }

    private OperationFence captureFence() {
        ensureDataCache();
        BrowserServerScreenHost host = remoteHost instanceof BrowserServerScreenHost value ? value : null;
        String subjectId = host == null ? BrowserLaunchSession.metadata().subjectId() : host.resourceSubjectId();
        if (subjectId == null || subjectId.isBlank()) subjectId = BrowserLaunchSession.metadata().subjectId();
        return new OperationFence(lifecycleGeneration, host, host == null ? 0L : host.resourceGeneration(),
                host == null ? 0L : host.resourceAuthGeneration(), BrowserLaunchSession.authenticated(), subjectId,
                BrowserLaunchSession.ticket(),
                ScreenManager.getInstance().getCurrentScreen());
    }

    private boolean isCurrent(OperationFence fence) {
        return isDataCurrent(fence) && isHostCurrent(fence) && fence.lifecycleGeneration() == lifecycleGeneration
                && (fence.screen() == null || ScreenManager.getInstance().isScreenActive(fence.screen()));
    }

    private boolean isInventoryCurrent(OperationFence fence, long inventoryGeneration) {
        return isCurrent(fence) && inventoryGeneration == dataCache.inventoryRequestGeneration;
    }

    private boolean isDataCurrent(OperationFence fence) {
        if (fence == null || dataCache.key != null && (!Objects.equals(dataCache.key.subjectId(), fence.subjectId())
                || !Objects.equals(dataCache.key.serverId(), serverId)
                || dataCache.key.hostGeneration() != fence.hostGeneration()
                || dataCache.key.authGeneration() != fence.authGeneration()
                || !Objects.equals(dataCache.key.ticket(), fence.ticket()))) return false;
        if (fence.authenticated() != BrowserLaunchSession.authenticated()) return false;
        if (!Objects.equals(fence.ticket(), BrowserLaunchSession.ticket())) return false;
        String currentSubjectId = BrowserLaunchSession.metadata().subjectId();
        if (!fence.subjectId().isBlank() && !currentSubjectId.isBlank() && !Objects.equals(fence.subjectId(), currentSubjectId)) {
            return false;
        }
        return true;
    }

    private boolean isHostCurrent(OperationFence fence) {
        if (fence.host() != null && (!fence.host().resourceAlive() || fence.host().resourceGeneration() != fence.hostGeneration()
                || fence.host().resourceAuthGeneration() != fence.authGeneration()
                || !Objects.equals(fence.host().resourceSubjectId(), fence.subjectId())
                || !Objects.equals(fence.host().resourceTicket(), fence.ticket()))) {
            return false;
        }
        return true;
    }

    private String resourceSubjectId() {
        BrowserServerScreenHost host = remoteHost instanceof BrowserServerScreenHost value ? value : null;
        String subjectId = host == null ? BrowserLaunchSession.metadata().subjectId() : host.resourceSubjectId();
        if (subjectId == null || subjectId.isBlank()) subjectId = BrowserLaunchSession.metadata().subjectId();
        return subjectId == null ? "" : subjectId;
    }

    private ResourceCacheKey resourceCacheKey() {
        if (serverId.isBlank()) return null;
        BrowserServerScreenHost host = remoteHost instanceof BrowserServerScreenHost value ? value : null;
        return new ResourceCacheKey(resourceSubjectId(), serverId, Objects.requireNonNullElse(version, ""),
                Objects.requireNonNullElse(loader, ""), host == null ? 0L : host.resourceGeneration(),
                host == null ? 0L : host.resourceAuthGeneration(), BrowserLaunchSession.ticket());
    }

    private void ensureDataCache() {
        ResourceCacheKey currentKey = resourceCacheKey();
        if (Objects.equals(cacheKey, currentKey)) return;
        ResourceDataCache previous = dataCache;
        cacheKey = currentKey;
        dataCache = new ResourceDataCache(currentKey);
        if (previous != null && previous != dataCache) {
            cancelPendingResourceNotification();
            scheduler.cancelAll();
            clearDataCache(previous);
        }
        modpackCapabilities.clear();
        lifecycleGeneration++;
        if (lifecycleGeneration <= 0) lifecycleGeneration = 1L;
    }

    private record ResourceCacheKey(String subjectId, String serverId, String version, String loader, long hostGeneration,
                                    long authGeneration, String ticket) {
    }

    private static final class ResourceDataCache {
        private final ResourceCacheKey key;
        private final Map<String, List<InstalledFile>> installedFiles = new LinkedHashMap<>();
        private final Map<String, String> resourceFailureMessages = new LinkedHashMap<>();
        private final Map<String, String> resourceHashFailures = new LinkedHashMap<>();
        private final Map<String, String> resourceDetailFailures = new LinkedHashMap<>();
        private final Map<String, List<ServerModels.PteroFileObjectAttributes>> resourceDirectorySnapshots = new LinkedHashMap<>();
        private final Map<String, Async<List<ServerModels.PteroFileObjectAttributes>>> resourceDirectoryRequests = new LinkedHashMap<>();
        private final Map<String, Async<ResourceMarketplaceProvider.Details>> resourceDetailRequests = new LinkedHashMap<>();
        private final Map<String, OperationFence> resourceDetailRequestFences = new LinkedHashMap<>();
        private final Map<String, Async<ResourceMarketplaceProvider.Details>> resourceDetailTransports = new LinkedHashMap<>();
        private final Map<String, ResourceMarketplaceProvider.Card> resourceDetailResources = new LinkedHashMap<>();
        private final ArrayDeque<String> resourceDetailQueue = new ArrayDeque<>();
        private final Map<String, ResourceMarketplaceProvider.Details> resourceDetailResults = new LinkedHashMap<>();
        private int resourceDetailActiveRequests;
        private final Map<String, BrowserRemotelyServerApi.FileHash> resourceHashResults = new LinkedHashMap<>();
        private final Map<String, OnlineResourceVersion> providerHashResults = new LinkedHashMap<>();
        private final Map<String, OnlineResourceVersion> providerFingerprintResults = new LinkedHashMap<>();
        private final Map<String, Async<ProviderLookupResult>> providerLookupRequests = new LinkedHashMap<>();
        private final Map<String, ProviderMatch> providerMatches = new LinkedHashMap<>();
        private final Map<String, String> providerLookupFailures = new LinkedHashMap<>();
        private final Map<String, Async<OnlineResourceVersion>> latestVersionRequests = new LinkedHashMap<>();
        private final Map<String, OperationFence> latestVersionRequestFences = new LinkedHashMap<>();
        private final Map<String, OnlineResourceVersion> latestVersionResults = new LinkedHashMap<>();
        private final Set<String> latestVersionMisses = new LinkedHashSet<>();
        private List<String> providerOrder = List.of();
        private Async<List<InstalledResource>> inventoryRequest;
        private Async<BrowserResourceInventory> browserInventoryRequest;
        private Async<List<InstalledFile>> inventoryHydrationRequest;
        private Async<BrowserModpackProfile> modpackProfileRequest;
        private OperationFence inventoryFence;
        private long inventoryRequestGeneration;
        private Async<List<String>> resourceDirectoriesRequest;
        private OperationFence resourceDirectoriesFence;
        private String worldName = "world";
        private BrowserModpackProfile modpackProfile;
        private List<BrowserResourceFile> resourceFiles = List.of();
        private List<BrowserResourceEntry> resourceEntries = List.of();

        private ResourceDataCache(ResourceCacheKey key) {
            this.key = key;
        }
    }

    private static final class SharedRequest {
        private final Async<?> shared;
        private int subscribers;

        private SharedRequest(Async<?> shared) {
            this.shared = shared;
        }
    }

    private record InstalledResult(List<InstalledResource> resources, List<InstalledFile> files) {
    }

    private record OperationResult<T>(T value, Throwable failure) {
    }

    private record HashBatchResult(List<String> paths, List<BrowserRemotelyServerApi.FileHash> hashes, String failure) {
        private HashBatchResult {
            paths = paths == null ? List.of() : List.copyOf(paths);
            hashes = hashes == null ? List.of() : hashes.stream().filter(Objects::nonNull).toList();
            failure = failure == null ? "" : failure;
        }
    }

    private final class HashBatchScheduler {
        private final List<String> paths;
        private final OperationFence fence;
        private final long requestGeneration;
        private final Async<List<HashBatchResult>> result;
        private final List<HashBatchResult> batches = new ArrayList<>();
        private int offset;
        private Async<HashBatchResult> active;
        private boolean cancelled;

        private HashBatchScheduler(List<String> paths, OperationFence fence, long requestGeneration,
                                   Async<List<HashBatchResult>> result) {
            this.paths = List.copyOf(paths);
            this.fence = fence;
            this.requestGeneration = requestGeneration;
            this.result = result;
        }

        private void advance() {
            while (!cancelled && !result.isDone()) {
                if (!isDataCurrent(fence) || requestGeneration != dataCache.inventoryRequestGeneration) {
                    result.fail(new Async.Cancellation());
                    return;
                }
                if (offset >= paths.size()) {
                    result.complete(List.copyOf(batches));
                    return;
                }
                int end = Math.min(paths.size(), offset + 64);
                List<String> batchPaths = List.copyOf(paths.subList(offset, end));
                Async<HashBatchResult> request;
                try {
                    request = resolveHashBatch(batchPaths);
                } catch (Throwable failure) {
                    result.fail(failure);
                    return;
                }
                if (request == null) {
                    result.fail(new IllegalStateException("Hash Batch Request Is Unavailable"));
                    return;
                }
                active = request;
                if (!request.isDone()) {
                    request.whenComplete((batch, failure) -> finish(end, batch, failure));
                    return;
                }
                Throwable failure;
                HashBatchResult batch;
                try {
                    failure = request.failure();
                    batch = failure == null ? request.value() : null;
                } catch (Throwable accessFailure) {
                    result.fail(accessFailure);
                    return;
                }
                if (!isDataCurrent(fence) || requestGeneration != dataCache.inventoryRequestGeneration) {
                    result.fail(new Async.Cancellation());
                    return;
                }
                if (failure != null) {
                    result.fail(failure);
                    return;
                }
                if (batch == null) {
                    result.fail(new IllegalStateException("Hash Batch Result Is Unavailable"));
                    return;
                }
                batches.add(batch);
                offset = end;
                active = null;
            }
        }

        private void finish(int end, HashBatchResult batch, Throwable failure) {
            active = null;
            if (cancelled || result.isDone()) return;
            if (!isDataCurrent(fence) || requestGeneration != dataCache.inventoryRequestGeneration) {
                result.fail(new Async.Cancellation());
                return;
            }
            if (failure != null) {
                result.fail(failure);
                return;
            }
            if (batch == null) {
                result.fail(new IllegalStateException("Hash Batch Result Is Unavailable"));
                return;
            }
            batches.add(batch);
            offset = end;
            advance();
        }

        private void cancel() {
            cancelled = true;
            Async<HashBatchResult> request = active;
            if (request != null && !request.isDone()) request.cancel();
        }
    }

    private record ProviderLookupResult(String provider, boolean fingerprint,
                                        Map<String, OnlineResourceVersion> matches, Throwable failure) {
        private ProviderLookupResult {
            if (matches == null || matches.isEmpty()) {
                matches = Map.of();
            } else {
                Map<String, OnlineResourceVersion> valid = new LinkedHashMap<>();
                matches.forEach((key, value) -> {
                    if (key != null && value != null) valid.put(key, value);
                });
                matches = Map.copyOf(valid);
            }
        }
    }

    record InstalledFile(String provider, String projectId, String versionId, String directory, String filename,
                         String sha1, String murmur2, boolean updateAvailable) {
        InstalledFile(String provider, String projectId, String versionId, String directory, String filename,
                      String sha1, boolean updateAvailable) {
            this(provider, projectId, versionId, directory, filename, sha1, "", updateAvailable);
        }

        String hash() {
            return sha1;
        }
    }

    record InventoryIndex(List<InstalledFile> files, Map<String, List<InstalledFile>> hashedFiles,
                          Map<String, List<InstalledFile>> fingerprintedFiles) {
        InventoryIndex(List<InstalledFile> files, Map<String, List<InstalledFile>> hashedFiles) {
            this(files, hashedFiles, Map.of());
        }
    }

    record ProviderMatch(String provider, OnlineResourceVersion version) {
    }

    record BrowserResourceEntry(String key, ResourceMarketplaceProvider.Card card, ResourceType type, String directory,
                                String filename, String name, String description, String version, String author,
                                String iconUrl, String hash, String murmur2, boolean enabled, boolean updateAvailable,
                                boolean recognized, String hashFailure, String metadataFailure) {
    }

    record ResourceFailure(String path, String message) {
        ResourceFailure {
            path = path == null || path.isBlank() ? "/" : path;
            message = message == null || message.isBlank() ? "Resource Unavailable" : message;
        }

        String detail() {
            return path + ": " + message;
        }
    }

    record DirectoryResult<T>(List<T> values, Throwable failure) {
        DirectoryResult {
            values = values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
        }
    }

    record BrowserResourceInventory(List<BrowserResourceEntry> entries, BrowserModpackProfile modpack,
                                    List<ResourceFailure> failures) {
        BrowserResourceInventory(List<BrowserResourceEntry> entries, BrowserModpackProfile modpack) {
            this(entries, modpack, List.of());
        }

        BrowserResourceInventory {
            entries = entries == null ? List.of() : List.copyOf(entries);
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        String warning() {
            return failures.stream().map(ResourceFailure::detail).reduce((left, right) -> left + "; " + right).orElse("");
        }
    }

    record BrowserModpackProfile(String name, String provider, String projectId, String versionId, String version,
                                 Set<String> ownedPaths, Set<String> preservedPaths, String json) {
        BrowserModpackProfile {
            ownedPaths = ownedPaths == null ? Set.of() : Set.copyOf(ownedPaths);
            preservedPaths = preservedPaths == null ? Set.of() : Set.copyOf(preservedPaths);
        }

        boolean owns(String path) {
            return ownedPaths.contains(resourcePath(path));
        }

        BrowserModpackProfile detach(String path) {
            String normalized = resourcePath(path);
            if (!ownedPaths.contains(normalized)) return this;
            Set<String> owned = new LinkedHashSet<>(ownedPaths);
            owned.remove(normalized);
            Set<String> preserved = new LinkedHashSet<>(preservedPaths);
            preserved.add(normalized);
            JsonObject root = BrowserJson.object(json);
            JsonArray values = new JsonArray();
            preserved.stream().sorted().forEach(values::add);
            root.add("preservedConflicts", values);
            return new BrowserModpackProfile(name, provider, projectId, versionId, version, owned, preserved, root.toString());
        }
    }

    private record BrowserResourceFile(String directory, String filename, String sha1, String murmur2) {
    }

    record ModpackSelection(String provider, String projectId, String versionId, String versionNumber, String loader) {
    }

    private Async<ResourceMarketplaceProvider.Version> installVersion(ResourceMarketplaceProvider.Card resource,
                                                                       ResourceMarketplaceProvider.Version selected,
                                                                       OperationFence fence) {
        if (selected != null) return Async.completed(selected);
        return versions(resource, fence).thenCompose(available -> available == null || available.isEmpty()
                ? Async.failed(new IllegalStateException("No Compatible Resource Version Is Available"))
                : Async.completed(available.getFirst()));
    }

    private boolean installSupported(ResourceMarketplaceProvider.Card resource) {
        return capabilitiesAvailable("files.read", "files.list", "files.pull") && installEligible(resource);
    }

    private boolean creationModpack(ResourceMarketplaceProvider.Card resource) {
        return !hasInstance() && modpackSelection != null && resource != null
                && ResourceType.getTypeFromString(resource.type()) == ResourceType.MODPACK;
    }

    private boolean installEligible(ResourceMarketplaceProvider.Card resource) {
        if (resource == null || api == null || serverId.isBlank() || marketplace == null
                || !marketplace.providers().contains(resource.provider())) return false;
        if (!marketplace.source(resource.provider()).supportsDownloads()) return false;
        ResourceType type = ResourceType.getTypeFromString(resource.type());
        if (!marketplace.supportsType(resource.provider(), type)) return false;
        return type == ResourceType.MOD && supportsMods() || type == ResourceType.PLUGIN && supportsPlugins()
                || type == ResourceType.DATA_PACK || type == ResourceType.RESOURCE_PACK || type == ResourceType.SHADER_PACK;
    }

    private boolean supportsResourceResolution(ResourceMarketplaceProvider.Card resource) {
        if (resource == null || marketplace == null || !marketplace.providers().contains(resource.provider())) return false;
        AsyncResourceProvider source = marketplace.source(resource.provider());
        return source.supportsHashLookup() || source.supportsFingerprintLookup();
    }

    private Async<Void> pullFile(String url, String directory, String filename) {
        return withCapabilities(() -> api.pullFile(serverId, url, directory, filename), "files.pull");
    }

    private Async<Void> deleteFiles(String directory, List<String> files) {
        return withCapabilities(() -> api.deleteFiles(serverId, directory, files), "files.delete");
    }

    private boolean capabilitiesAvailable(String... actions) {
        for (String action : actions) {
            if (!serverCapabilities.availability(server, action).available()) return false;
        }
        return true;
    }

    private CapabilityDescriptor mutationCapability(String id, String fallback, String... actions) {
        if (!hasInstance()) return CapabilityDescriptor.unavailable(id, fallback);
        ServerUiCapabilityProvider.Availability availability = unavailableCapability(actions);
        return availability == null ? CapabilityDescriptor.supported(id)
                : CapabilityDescriptor.unavailable(id, reason(availability, fallback));
    }

    private <T> Async<T> withCapabilities(Supplier<Async<T>> operation, String... actions) {
        ServerUiCapabilityProvider.Availability unavailable = unavailableCapability(actions);
        if (unavailable == null) return operation.get();
        if ("Server Capabilities Are Loading".equals(unavailable.reason())) {
            return serverCapabilities.refresh(server).thenCompose(ignored -> {
                ServerUiCapabilityProvider.Availability refreshed = unavailableCapability(actions);
                return refreshed == null ? operation.get() : Async.failed(new UnsupportedOperationException(reason(refreshed, "Server File Capability Is Unavailable")));
            });
        }
        return Async.failed(new UnsupportedOperationException(reason(unavailable, "Server File Capability Is Unavailable")));
    }

    private ServerUiCapabilityProvider.Availability unavailableCapability(String... actions) {
        for (String action : actions) {
            ServerUiCapabilityProvider.Availability availability = serverCapabilities.availability(server, action);
            if (!availability.available()) return availability;
        }
        return null;
    }

    private static String reason(ServerUiCapabilityProvider.Availability availability, String fallback) {
        return availability == null || availability.reason().isBlank() ? fallback : availability.reason();
    }

    static String resourceGroupContext(String serverId) {
        return "resources:" + Objects.requireNonNullElse(serverId, "");
    }

    private static String resourcePath(String value) {
        String normalized = Objects.requireNonNullElse(value, "").strip().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.contains("//")) normalized = normalized.replace("//", "/");
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.endsWith(".disabled") ? normalized.substring(0, normalized.length() - ".disabled".length()) : normalized;
    }

    private static String environment(ServerModels.ClientServerView server, String key) {
        return server == null || server.environment == null ? null : server.environment.get(key);
    }

    private static String first(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) return value;
            }
        }
        return "";
    }

    private static ServerModels.ClientServerView serverView(String serverId) {
        ServerModels.ClientServerView server = new ServerModels.ClientServerView();
        server.identifier = serverId == null ? "" : serverId;
        return server;
    }

    private String resourceDirectory(ResourceMarketplaceProvider.Card resource) {
        ResourceType type = ResourceType.getTypeFromString(resource.type());
        return type == ResourceType.PLUGIN ? "/plugins" : type == ResourceType.DATA_PACK
                ? "/" + normalizedWorldName(dataCache.worldName) + "/datapacks" : type == ResourceType.RESOURCE_PACK
                ? "/resourcepacks" : type == ResourceType.SHADER_PACK ? "/shaderpacks" : "/mods";
    }

    @Override
    public void saveCollection(Object collection, List<ResourceMarketplaceProvider.Card> entries, Runnable callback) {
        if (callback != null) callback.run();
    }

    @Override
    public void acceptSelection(Object callback, List<ResourceMarketplaceProvider.Card> entries) {
        if (!(callback instanceof Consumer<?> consumer)) {
            log("Resource Selection Unavailable", new IllegalArgumentException("Resource Selection Callback Is Unavailable"));
            return;
        }
        try {
            @SuppressWarnings("unchecked") Consumer<List<ResourceMarketplaceProvider.Card>> typed =
                    (Consumer<List<ResourceMarketplaceProvider.Card>>) consumer;
            typed.accept(entries == null ? List.of() : List.copyOf(entries));
        } catch (RuntimeException failure) {
            log("Resource Selection Failed", failure);
        }
    }

    @Override
    public void openResource(Screen parent, ResourceMarketplaceProvider source, ResourceMarketplaceProvider.Card resource,
                             ResourceType type, boolean server, Object remoteHost, boolean reStudioContext,
                             boolean replacement, Runnable changeCallback) {
        if (!(parent instanceof ReScreen reScreen)) return;
        OperationFence fence = captureFence();
        if (hasInstance() && (dataCache.inventoryRequest == null || !dataCache.inventoryRequest.isDone())) {
            installedResources().whenComplete((ignored, failure) -> ScreenManager.getInstance().execute(() -> {
                if (!isCurrent(fence)) return;
                if (failure != null) log("Installed Resource State Unavailable", failure);
                openResourceScreen(reScreen, source, resource, type, server, remoteHost, reStudioContext, replacement, changeCallback);
            }));
            return;
        }
        if (!isCurrent(fence)) return;
        openResourceScreen(reScreen, source, resource, type, server, remoteHost, reStudioContext, replacement, changeCallback);
    }

    private void openResourceScreen(ReScreen parent, ResourceMarketplaceProvider source, ResourceMarketplaceProvider.Card resource,
                                    ResourceType type, boolean server, Object remoteHost, boolean reStudioContext,
                                    boolean replacement, Runnable changeCallback) {
        ScreenManager.getInstance().setScreen(new ResourceOverviewScreen(parent,
                new BrowserResourceOverviewProvider(this, source, installedCard(resource), type, replacement, changeCallback)));
    }

    @Override
    public void log(String message, Throwable failure) {
        String title = message == null || message.isBlank() ? "Resource Operation Failed" : message;
        String detail = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                ? "The resource operation could not be completed." : failure.getMessage();
        new Notification(title, detail, Notification.Type.ERROR);
    }

    @Override
    public Object captureOperation() {
        return captureFence();
    }

    @Override
    public boolean isOperationCurrent(Object operation, Screen screen) {
        return operation instanceof OperationFence fence && isCurrent(fence)
                && (screen == null || ScreenManager.getInstance().isScreenActive(screen));
    }

    private record OperationFence(long lifecycleGeneration, BrowserServerScreenHost host, long hostGeneration,
                                  long authGeneration, boolean authenticated, String subjectId, String ticket, Screen screen) {
    }
}
