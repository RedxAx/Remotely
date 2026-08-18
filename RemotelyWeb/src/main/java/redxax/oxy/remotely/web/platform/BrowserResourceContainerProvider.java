package redxax.oxy.remotely.web.platform;

import restudio.rebase.backend.CapabilityDescriptor;
import restudio.rebase.platform.Async;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.resource.marketplace.ResourceMarketplaceProvider;
import restudio.rebase.resource.marketplace.ResourceMarketplaceProviderAdapter;
import restudio.rebase.resource.provider.OnlineResourceVersion;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rebase.ui.screens.resources.ResourceContainerProvider;
import restudio.rebase.ui.screens.resources.ResourceContainerItem;
import restudio.rebase.ui.screens.resources.ResourceContainerUpdate;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class BrowserResourceContainerProvider implements ResourceContainerProvider {
    private final BrowserServerScreenHost screenHost;
    private final ServerModels.ClientServerView server;
    private final ResourceMarketplaceProviderAdapter marketplace;
    private final BrowserResourceBrowserContext resourceContext;
    private final Map<String, ResourceMarketplaceProvider.Card> cards = new LinkedHashMap<>();
    private final Map<String, String> icons = new LinkedHashMap<>();
    private final Map<String, Identifier> iconIds = new LinkedHashMap<>();
    private final Map<Consumer<List<ResourceContainerItem>>, Consumer<BrowserResourceBrowserContext.BrowserResourceInventory>> snapshotListeners = new LinkedHashMap<>();
    private String lastResourceWarning = "";

    BrowserResourceContainerProvider(BrowserServerScreenHost screenHost, ServerModels.ClientServerView server,
                                     ResourceMarketplaceProviderAdapter marketplace, BrowserResourceBrowserContext resourceContext) {
        this.screenHost = screenHost;
        this.server = server;
        this.marketplace = marketplace;
        this.resourceContext = resourceContext;
    }

    @Override
    public Object instance() {
        return server;
    }

    @Override
    public Async<List<ResourceContainerItem>> load(boolean force) {
        Async<Void> readiness = screenHost == null || server == null ? Async.completed(null)
                : screenHost.capabilities(server).refresh(server).handle((ignored, failure) -> null);
        return readiness.thenCompose(ignored -> resourceContext.browserResourceInventory(force)).thenApply(this::resourceSnapshot);
    }

    @Override
    public void addResourceSnapshotListener(Consumer<List<ResourceContainerItem>> listener) {
        if (listener == null || snapshotListeners.containsKey(listener)) return;
        Consumer<BrowserResourceBrowserContext.BrowserResourceInventory> bridge = inventory -> listener.accept(resourceSnapshot(inventory));
        snapshotListeners.put(listener, bridge);
        resourceContext.addInventoryListener(bridge);
    }

    @Override
    public void removeResourceSnapshotListener(Consumer<List<ResourceContainerItem>> listener) {
        Consumer<BrowserResourceBrowserContext.BrowserResourceInventory> bridge = snapshotListeners.remove(listener);
        if (bridge != null) resourceContext.removeInventoryListener(bridge);
    }

    private List<ResourceContainerItem> resourceSnapshot(BrowserResourceBrowserContext.BrowserResourceInventory inventory) {
        Map<String, String> previousIcons = new LinkedHashMap<>(icons);
        cards.clear();
        icons.clear();
        if (inventory == null) {
            releaseIcons();
            return List.of();
        }
        String warning = inventory.warning();
        if (!warning.equals(lastResourceWarning)) {
            lastResourceWarning = warning;
            if (!warning.isBlank()) {
                new Notification("Some Resources Could Not Be Loaded", "Showing Available Resources. " + warning,
                        Notification.Type.WARN);
            }
        }
        List<ResourceContainerItem> resources = inventory.entries().stream().filter(value -> value != null).map(this::resource).toList();
        List<ResourceContainerItem> nested = hierarchy(resources, inventory.modpack());
        BrowserResourceBrowserContext.BrowserModpackProfile profile = inventory.modpack();
        if (profile != null && !nested.isEmpty() && profile.provider() != null && !profile.provider().isBlank()
                && profile.projectId() != null && !profile.projectId().isBlank()) {
            ResourceMarketplaceProvider.Card card = new ResourceMarketplaceProvider.Card(profile.provider(), profile.projectId(),
                    profile.projectId(), ResourceType.MODPACK.getModrinthProjectType(), profile.name(),
                    nested.getFirst().getDescription(), List.of(), 0, 0, null, null, true, false);
            cards.put(nested.getFirst().path(), card);
        }
        List<Map.Entry<String, Identifier>> staleIcons = iconIds.entrySet().stream().filter(entry -> {
            String key = entry.getKey();
            String previous = previousIcons.get(key);
            String current = icons.get(key);
            return !(Objects.equals(previous, current) && current != null && !current.isBlank());
        }).toList();
        staleIcons.forEach(entry -> releaseIcon(entry.getKey(), entry.getValue()));
        return nested;
    }

    @Override
    public Async<Void> delete(ResourceContainerItem resource, boolean permanent) {
        ResourceMarketplaceProvider.Card card = card(resource);
        if (card != null && resourceContext.supports(ResourceMarketplaceProvider.Action.DELETE, card)) {
            return resourceContext.action(ResourceMarketplaceProvider.Action.DELETE, card, null, null);
        }
        if (!fileCapability("files.delete").available()) return failed("Resource Removal Is Unavailable");
        return screenHost.capabilities(server).deleteFiles(server, directory(resource), List.of(resource.getFileName()))
                .thenRun(resourceContext::invalidateFileCache);
    }

    @Override
    public Async<Boolean> detach(ResourceContainerItem resource) {
        return resourceContext.detachResource(key(resource));
    }

    @Override
    public Async<Map<String, OnlineResourceVersion>> checkUpdates(List<ResourceContainerItem> resources,
                                                                  BiConsumer<Integer, Integer> progress) {
        Map<String, OnlineResourceVersion> updates = new LinkedHashMap<>();
        List<ResourceContainerItem> values = resources == null ? List.of() : resources;
        for (int index = 0; index < values.size(); index++) {
            ResourceContainerItem resource = values.get(index);
            if (resource != null && resource.availableUpdate != null && resource.getProjectId() != null) {
                updates.put(resource.getProjectId(), resource.availableUpdate);
            }
            if (progress != null) progress.accept(index + 1, values.size());
        }
        return Async.completed(updates);
    }

    @Override
    public Async<OnlineResourceVersion> checkUpdate(ResourceContainerItem resource) {
        return Async.completed(resource == null ? null : resource.availableUpdate);
    }

    @Override
    public Async<Void> update(ResourceContainerItem resource, OnlineResourceVersion version,
                              BiConsumer<Long, Long> progress, Runnable refreshed, boolean backup) {
        ResourceMarketplaceProvider.Card card = card(resource);
        if (card == null || !resourceContext.supports(ResourceMarketplaceProvider.Action.UPDATE, card)) {
            return failed("Resource Update Is Unavailable");
        }
        Async<Void> prepared = backup ? resourceContext.createResourceBackup("Pre-Update backup | " + resource.getName())
                : Async.completed(null);
        return prepared.thenCompose(ignored -> resourceContext.action(ResourceMarketplaceProvider.Action.UPDATE, card, null, refreshed));
    }

    @Override
    public Async<Void> updateAll(List<ResourceContainerUpdate> updates, BiConsumer<String, Integer> progress,
                                 Runnable refreshed, boolean backup) {
        List<ResourceContainerUpdate> values = updates == null ? List.of() : updates;
        if (values.isEmpty()) return Async.completed(null);
        Async<Void> result = backup ? resourceContext.createResourceBackup("Pre-Update backup for " + values.size() + " resources")
                : Async.completed(null);
        for (int index = 0; index < values.size(); index++) {
            ResourceContainerUpdate update = values.get(index);
            int percentage = values.isEmpty() ? 100 : (index + 1) * 100 / values.size();
            result = result.thenCompose(ignored -> update(update.resource, update.newVersion, null, null, false))
                    .thenRun(() -> {
                        if (progress != null) progress.accept("Updating " + update.resource.getName(), percentage);
                    });
        }
        return result.thenRun(() -> {
            if (refreshed != null) refreshed.run();
        });
    }

    @Override
    public Async<Void> toggle(ResourceContainerItem resource, boolean enabled) {
        if (resource == null || !fileCapability("files.rename").available()) return failed("Resource Toggle Is Unavailable");
        String current = resource.getFileName();
        String lower = current == null ? "" : current.toLowerCase();
        String target = enabled && lower.endsWith(".disabled") ? current.substring(0, current.length() - ".disabled".length())
                : !enabled && !lower.endsWith(".disabled") ? current + ".disabled" : current;
        if (current == null || current.equals(target)) return Async.completed(null);
        return screenHost.capabilities(server).renameFiles(server, "/", List.of(rename(
                remotePath(directory(resource), current), remotePath(directory(resource), target))))
                .thenRun(resourceContext::invalidateFileCache);
    }

    @Override
    public Map<String, List<String>> groups() {
        return resourceContext.resourceGroups();
    }

    @Override
    public void saveGroups(Map<String, List<String>> groups) {
        resourceContext.saveResourceGroups(groups);
    }

    @Override
    public void openBrowser(ReScreen parent) {
        if (parent != null && screenHost != null && server != null) {
            screenHost.openServerResourceBrowser(parent, server, BrowserResourceBrowserContext.defaultResourceType(server.loader));
        }
    }

    @Override
    public void openResource(ReScreen parent, ResourceContainerItem resource, Runnable refreshed) {
        ResourceMarketplaceProvider.Card card = card(resource);
        if (card != null && card.provider() != null) {
            resourceContext.openResource(parent, marketplace, card, resource.getType(), true, screenHost, false, false, refreshed);
        }
    }

    @Override
    public void resolveIcon(ResourceContainerItem resource, Consumer<Identifier> resolved) {
        if (resolved == null) return;
        String resourceKey = key(resource);
        String icon = icons.get(resourceKey);
        Identifier current = iconIds.get(resourceKey);
        if (current != null && (icon == null || icon.isBlank())) {
            releaseIcon(resourceKey, current);
            current = null;
        }
        if (icon != null && !icon.isBlank() && current == null && screenHost != null && screenHost.application() != null) {
            current = screenHost.application().registerRemoteImage(icon);
            if (current != null) iconIds.put(resourceKey, current);
        }
        resolved.accept(current);
    }

    @Override
    public void addRefreshListener(Runnable listener) {
        resourceContext.addResourceListener(listener);
    }

    @Override
    public void removeRefreshListener(Runnable listener) {
        resourceContext.removeResourceListener(listener);
    }

    @Override
    public void stopWatching() {
        releaseIcons();
        resourceContext.invalidate();
    }

    private void releaseIcons() {
        if (screenHost == null || screenHost.application() == null) {
            iconIds.clear();
            return;
        }
        Map<String, Identifier> owned = new LinkedHashMap<>(iconIds);
        iconIds.clear();
        owned.values().forEach(screenHost.application()::releaseRemoteImage);
    }

    private void releaseIcon(String resourceKey, Identifier icon) {
        iconIds.remove(resourceKey, icon);
        if (screenHost != null && screenHost.application() != null) screenHost.application().releaseRemoteImage(icon);
    }

    @Override
    public CapabilityDescriptor capability(String id) {
        if (CAPABILITY_TOGGLE.equals(id)) return fileCapability("files.rename");
        if (CAPABILITY_DELETE.equals(id)) return fileCapability("files.delete");
        if (CAPABILITY_OPEN.equals(id)) return CapabilityDescriptor.supported(id);
        return resourceContext.capability(id);
    }

    private ResourceContainerItem resource(BrowserResourceBrowserContext.BrowserResourceEntry value) {
        ResourceContainerItem resource = new ResourceContainerItem(null, value.type() == null ? ResourceType.MOD : value.type(), value.filename(), value.enabled());
        resource.path(remotePath(value.directory(), value.filename()));
        resource.setName(value.name());
        resource.setDescription(value.description());
        resource.setVersion(value.version());
        resource.setAuthors(value.author() == null || value.author().isBlank() ? List.of() : List.of(value.author()));
        resource.setFileHash(value.hash());
        if (value.card() != null) {
            resource.setProviderName(value.card().provider());
            resource.setProjectId(value.card().id());
            cards.put(resource.path(), value.card());
        }
        if (value.updateAvailable()) {
            OnlineResourceVersion update = new OnlineResourceVersion();
            update.projectId = resource.getProjectId();
            update.versionNumber = "Latest";
            resource.availableUpdate = update;
        }
        if (value.iconUrl() != null) icons.put(resource.path(), value.iconUrl());
        return resource;
    }

    static List<ResourceContainerItem> hierarchy(List<ResourceContainerItem> resources,
                                                 BrowserResourceBrowserContext.BrowserModpackProfile profile) {
        List<ResourceContainerItem> flat = resources == null ? List.of() : resources.stream().filter(value -> value != null).toList();
        if (profile == null) return flat;
        List<ResourceContainerItem> children = flat.stream().filter(resource -> profile.owns(resource.path())).toList();
        List<ResourceContainerItem> independent = flat.stream().filter(resource -> !profile.owns(resource.path())).toList();
        ResourceContainerItem parent = new ResourceContainerItem(".meta/modpack-profile.json", ResourceType.MODPACK,
                "modpack-profile.json", true);
        parent.setModpack(true);
        parent.setName(profile.name());
        parent.setDescription(modpackDescription(children));
        parent.setVersion(profile.version());
        parent.setProviderName(profile.provider());
        parent.setProjectId(profile.projectId());
        parent.setVersionId(profile.versionId());
        parent.setChildren(children);
        List<ResourceContainerItem> nested = new ArrayList<>();
        nested.add(parent);
        nested.addAll(independent);
        return List.copyOf(nested);
    }

    private static String modpackDescription(List<ResourceContainerItem> children) {
        int mods = 0;
        int resourcePacks = 0;
        int shaders = 0;
        int other = 0;
        for (ResourceContainerItem child : children) {
            switch (child.getType()) {
                case MOD -> mods++;
                case RESOURCE_PACK -> resourcePacks++;
                case SHADER_PACK -> shaders++;
                default -> other++;
            }
        }
        return "Mods: " + mods + " · Resource Packs: " + resourcePacks + " · Shaders: " + shaders + " · Other: " + other;
    }

    private ResourceMarketplaceProvider.Card card(ResourceContainerItem resource) {
        return cards.get(key(resource));
    }

    private CapabilityDescriptor fileCapability(String action) {
        if (screenHost == null || server == null) return CapabilityDescriptor.unavailable(action, "Server File Capability Is Unavailable");
        var availability = screenHost.capabilities(server).availability(server, action);
        return availability.available() ? CapabilityDescriptor.supported(action)
                : CapabilityDescriptor.unavailable(action, availability.reason().isBlank()
                ? "Server File Capability Is Unavailable" : availability.reason());
    }

    private static String key(ResourceContainerItem resource) {
        return resource == null ? "" : resource.path();
    }

    private static String directory(ResourceContainerItem resource) {
        String path = key(resource).replace('\\', '/');
        int separator = path.lastIndexOf('/');
        return separator < 0 ? "/" : path.substring(0, separator);
    }

    private static ServerModels.PteroFileRenameItem rename(String from, String to) {
        ServerModels.PteroFileRenameItem value = new ServerModels.PteroFileRenameItem();
        value.from = from == null ? "" : from;
        value.to = to == null ? "" : to;
        return value;
    }

    private static String remotePath(String directory, String filename) {
        String root = directory == null ? "" : directory.strip().replace('\\', '/');
        while (root.startsWith("/")) root = root.substring(1);
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        return root.isBlank() ? filename : root + "/" + filename;
    }

    private static <T> Async<T> failed(String message) {
        return Async.failed(new UnsupportedOperationException(message));
    }
}
