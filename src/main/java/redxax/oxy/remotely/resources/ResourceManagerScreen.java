package redxax.oxy.remotely.resources;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.remotely.resources.providers.HangarAPI;
import redxax.oxy.remotely.resources.providers.ModrinthAPI;
import redxax.oxy.remotely.resources.providers.SpigetAPI;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.ui.ReScreen;
import redxax.oxy.remotely.ui.widgets.ResourceWidget;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static redxax.oxy.remotely.config.Config.loading;

public class ResourceManagerScreen extends ReScreen {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private boolean list = false;
    private int loadedCount = 0;
    private boolean hasMore = true;
    private boolean isLoadingMore = false;
    private String currentSearch = "";
    private int currentSortIndex = 0;
    private String currentSortParam = "downloads";
    private final Map<String, String[]> sortLabelsMap = new HashMap<>();
    private final Map<String, String[]> sortValuesMap = new HashMap<>();
    private final String sortIconPath = "/assets/remotely/icons/sort.png";
    private int sortButtonIndex = 0;
    private final List<IRemotelyResource> pendingResources = new CopyOnWriteArrayList<>();

    public ResourceManagerScreen(MinecraftClient mc, Screen parent, ServerInfo info) {
        super(Text.literal(info.isModServer() ? "Remotely - Mods Browser" : (info.isPluginServer() || info.isProxyServer() ? "Remotely - Plugins Browser" : "Remotely - Modpacks Browser")));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
    }

    private Container platformContainer() {
        return new Container(5, 60, width - 10, height - 5).columns(list ? 1 : 2).padding(2).layoutStyle(Container.LayoutStyle.MANAGED);
    }

    @Override
    protected void init() {
        super.init();
        sortLabelsMap.put("Modrinth", new String[]{"Relevance", "Downloads", "Most Followers", "Last Updated", "Newest"});
        sortValuesMap.put("Modrinth", new String[]{"relevance", "downloads", "follows", "updated", "newest"});
        sortLabelsMap.put("Spigot", new String[]{"Highest Rating", "Most Downloads", "Last Updated", "Newest"});
        sortValuesMap.put("Spigot", new String[]{"-rating", "-downloads", "-updateDate", "-releaseDate"});
        sortLabelsMap.put("Hangar", new String[]{"Most Stars", "Most Views", "Most Downloads", "Last Updated", "Newest"});
        sortValuesMap.put("Hangar", new String[]{"stars", "views", "downloads", "updated", "newest"});
        Container modrinthContainer = platformContainer();
        Container spigotContainer = platformContainer();
        Container hangarContainer = platformContainer();
        setActiveContainer(modrinthContainer);
        SearchMode search = new SearchMode(false);
        search.setOnSearchEnter(this::onSearch);
        sortButtonIndex = headerBuilder.leftButtons.size();
        headerBuilder.addLeft(sortIconPath, this::cycleSort, "Sort: " + sortLabelsMap.get("Modrinth")[0]).addLeft((BufferedImage) null, () -> {
                    list = !list;
                    modrinthContainer.columns(list ? 1 : 2);
                    spigotContainer.columns(list ? 1 : 2);
                    hangarContainer.columns(list ? 1 : 2);
                }, "Switch Listing")
                .addRight("/assets/remotely/icons/close.png", () -> minecraftClient.setScreen(parent), "Close Screen").setSearchMode(search, false).build();

        tabs().builder().allowReorder(true).allowAdd(false).position(5, 36).size(width - 5, 18).onTabSelected(tab -> onTabChange(tab.getName())).build();

        tabs().addTab("Modrinth", modrinthContainer);
        if (serverInfo.isPluginServer() || serverInfo.isProxyServer()) {
            tabs().addTab("Spigot", spigotContainer);
            tabs().addTab("Hangar", hangarContainer);
        }
        tabs().setActiveTab(0);
        onTabChange("Modrinth");
    }

    private void onSearch(String query) {
        loadResources(query, true);
    }

    private void onTabChange(String tabName) {
        currentSortIndex = 0;
        currentSortParam = sortValuesMap.get(tabName)[0];
        currentSearch = "";
        updateSortHint(tabName);
        loadResources("", true);
    }

    private void cycleSort() {
        String tabName = tabs().getActiveTab().getName();
        String[] values = sortValuesMap.get(tabName);
        currentSortIndex = (currentSortIndex + 1) % values.length;
        currentSortParam = values[currentSortIndex];
        updateSortHint(tabName);
        loadResources(currentSearch, true);
    }

    private void updateSortHint(String tabName) {
        headerBuilder.leftButtons.get(sortButtonIndex).hint = "Sort: " + sortLabelsMap.get(tabName)[currentSortIndex];
        headerBuilder.build();
    }

    private void loadResources(String query, boolean reset) {
        if (reset) {
            container().clearWidgets();
            loadedCount = 0;
            hasMore = true;
            isLoadingMore = false;
            currentSearch = query;
            pendingResources.clear();
        }
        if (!hasMore) return;
        loading = true;
        isLoadingMore = true;
        int limit = 30;
        String serverVersion = serverInfo.getVersion();
        String sortParam = currentSortParam;
        String activeTab = tabs().getActiveTab().getName();
        CompletableFuture<List<IRemotelyResource>> future;
        if (activeTab.equals("Modrinth")) {
            if (serverInfo.isModServer()) {
                future = ModrinthAPI.searchMods(query, serverVersion, limit, loadedCount, serverInfo.type, sortParam);
            } else if (serverInfo.isPluginServer()) {
                future = ModrinthAPI.searchPlugins(query, serverVersion, limit, loadedCount, serverInfo.type, sortParam);
            } else {
                future = ModrinthAPI.searchModpacks(query, serverVersion, limit, loadedCount, sortParam);
            }
        } else if (activeTab.equals("Spigot")) {
            int page = loadedCount / limit;
            future = SpigetAPI.searchPlugins(query, limit, page, sortParam);
        } else {
            int offset = loadedCount;
            future = HangarAPI.searchPlugins(query, limit, offset, sortParam);
        }
        future.thenAccept(fetched -> {
            pendingResources.addAll(fetched);
            loadedCount += fetched.size();
            if (fetched.size() < limit) hasMore = false;
            loading = false;
            isLoadingMore = false;
        }).exceptionally(e -> {
            loading = false;
            isLoadingMore = false;
            return null;
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean handled = super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        if (verticalAmount < 0 && !isLoadingMore && hasMore && hitBottom) {
            loadResources(currentSearch, false);
        }
        return handled;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        if (!pendingResources.isEmpty()) {
            List<IRemotelyResource> toProcess = new ArrayList<>(pendingResources);
            pendingResources.clear();
            for (IRemotelyResource resource : toProcess) {
                ResourceWidget widget = new ResourceWidget(resource, minecraftClient, serverInfo);
                container().addWidget(widget);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        executor.shutdownNow();
        super.removed();
    }
}
