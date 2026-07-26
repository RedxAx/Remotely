package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkMember;
import redxax.oxy.remotely.network.NetworkSharedDataPolicy;
import redxax.oxy.remotely.network.SyncDataFamily;
import redxax.oxy.remotely.network.SyncLocationPolicy;
import redxax.oxy.remotely.network.SyncRealm;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class NetworkRealmScreen extends ReScreen {
    private final Screen parent;
    private final RemotelyClient remotelyClient;
    private final String networkId;
    private final List<SyncRealm> initialRealms;
    private final Map<String, Boolean> initialFeatures;
    private final NetworkSharedDataPolicy initialSharedDataPolicy;
    private final String editingRealmId;
    private final boolean addingRealm;
    private NetworkDefinition network;
    private List<SyncRealm> realms = List.of();
    private Map<String, Boolean> features = Map.of();
    private NetworkSharedDataPolicy sharedDataPolicy = NetworkSharedDataPolicy.defaults();
    private List<Instance> instances = List.of();
    private boolean preparing;

    public NetworkRealmScreen(Screen parent, RemotelyClient remotelyClient, String networkId) {
        this(parent, remotelyClient, networkId, null, null, null, null, false);
    }

    private NetworkRealmScreen(Screen parent, RemotelyClient remotelyClient, String networkId, List<SyncRealm> initialRealms, Map<String, Boolean> initialFeatures, NetworkSharedDataPolicy initialSharedDataPolicy, String editingRealmId, boolean addingRealm) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.networkId = networkId;
        this.initialRealms = initialRealms == null ? null : List.copyOf(initialRealms);
        this.initialFeatures = initialFeatures == null ? null : Map.copyOf(new LinkedHashMap<>(initialFeatures));
        this.initialSharedDataPolicy = initialSharedDataPolicy;
        this.editingRealmId = editingRealmId;
        this.addingRealm = addingRealm;
    }

    public String getDesktopAppId() {
        return "network-shared-data";
    }

    public String getDesktopAppTitle() {
        return "Shared Data";
    }

    public String getDesktopAppIconPath() {
        return "merge.png";
    }

    void openNetworkManager() {
        client.setScreen(parent);
        if (parent instanceof ServerManagerScreen serverManager) {
            serverManager.showNetworkSettings(networkId);
        }
    }

    @Override
    public void init() {
        super.init();
        network = remotelyClient.getNetworkManager().getNetwork(networkId).orElse(null);
        if (network == null) {
            new Notification("Network Unavailable", Notification.Type.ERROR);
            client.setScreen(parent);
            return;
        }
        realms = initialRealms == null ? List.copyOf(network.syncRealms()) : initialRealms;
        features = initialFeatures == null ? new LinkedHashMap<>(network.features()) : new LinkedHashMap<>(initialFeatures);
        sharedDataPolicy = initialSharedDataPolicy == null ? network.sharedDataPolicy() : initialSharedDataPolicy;
        instances = Rebase.get().getInstanceManager().getAllInstances();
        if (addingRealm || editingRealmId != null) {
            header().addLeft("close.png", this::refreshDraft, "Back To Player Data").build();
        } else {
            header().addLeft("close.png", () -> client.setScreen(parent), "Back").addRight("checkmark.png", this::review, "Apply Player Data").build();
        }
        Container container = createContainer("network_shared_data", 6, 38, width - 12, Math.max(80, height - 44)).columns(1).padding(8).verticalSpacing(4).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(true);
        populate(container);
        setActiveContainer(container);
    }

    private void populate(Container container) {
        if (addingRealm || editingRealmId != null) {
            populateRealmEditor(container, editingRealmId == null ? null : realms.stream().filter(realm -> realm.id().equals(editingRealmId)).findFirst().orElse(null));
            return;
        }
        List<NetworkMember> eligible = eligibleMembers();
        container.addWidget(infoRow(container, "Player Data", eligible.size() + " ReSync Servers • Choose Which Servers Share Player State", realms.size() + " Realms"));
        for (SyncRealm realm : realms) {
            String servers = routeNames(realm.nodeIds());
            String families = realm.dataFamilies().stream().map(value -> titleCase(value.name())).collect(Collectors.joining(", "));
            String description = (servers.isBlank() ? "No Servers" : servers) + " • " + families;
            String detail = realm.retainedSnapshots() + " Snapshots • " + realm.retentionDays() + " Days";
            MountableButtonWidget row = new MountableButtonWidget.Builder(realm.name()).description(description).hiddenText(detail).onClick(() -> editRealm(realm)).addButton(rowAction("edit.png", "Edit", () -> editRealm(realm))).build();
            styleRow(container, row, ThemeManager.getDefaultAccent(), 34);
            container.addWidget(row);
        }
        MountableButtonWidget.Builder actions = new MountableButtonWidget.Builder(realms.isEmpty() ? "Set Up Player Data" : "Manage Player Data").description("Inventory, Progress, Location, And More").addButton(rowAction("add.png", "Add Realm", () -> editRealm(null)));
        if (realms.isEmpty()) {
            actions.addButton(rowAction("merge.png", "Shared Survival", this::sharedSurvival));
        }
        MountableButtonWidget actionRow = actions.build();
        styleRow(container, actionRow, ThemeManager.getDefaultAccent(), 34);
        container.addWidget(actionRow);
        container.updateWidgetPositions();
    }

    private MountableButtonWidget infoRow(Container container, String title, String description, String hiddenText) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title).description(description).hiddenText(hiddenText).build();
        styleRow(container, row, ThemeManager.getDefaultAccent(), 32);
        return row;
    }

    private SquareButtonWidget rowAction(String icon, String hint, Runnable action) {
        return new SquareButtonWidget.Builder().imagePath(icon).hint(hint).onClick(action).accentType(ThemeManager.getDefaultAccent()).animateElevation(false).size(18, 18).build();
    }

    private void styleRow(Container container, MountableButtonWidget row, Accent accent, int height) {
        row.setAccent(accent);
        row.setSize(Math.max(220, container.getEffectiveWidth() - 10), height);
    }

    private void sharedSurvival() {
        Set<String> nodes = eligibleMembers().stream().map(NetworkMember::nodeId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<SyncDataFamily> families = new LinkedHashSet<>(List.of(SyncDataFamily.PRESENCE, SyncDataFamily.INVENTORY, SyncDataFamily.ENDER_CHEST, SyncDataFamily.EXPERIENCE, SyncDataFamily.VITALS, SyncDataFamily.EFFECTS, SyncDataFamily.PLAYER_STATE, SyncDataFamily.LOCATION));
        realms = List.of(new SyncRealm("survival", "Shared Survival", nodes, families, SyncLocationPolicy.REALM_RETURN_POINT, Set.of(), 20, 30));
        refreshDraft();
    }

    private void editRealm(SyncRealm existing) {
        client.setScreen(new NetworkRealmScreen(parent, remotelyClient, networkId, realms, features, sharedDataPolicy, existing == null ? null : existing.id(), existing == null));
    }

    private void populateRealmEditor(Container container, SyncRealm existing) {
        TextInputWidget name = new TextInputWidget.Builder().text(existing == null ? "State Realm" : existing.name()).placeholder("Realm Name").maxLength(64).build();
        TextInputWidget id = new TextInputWidget.Builder().text(existing == null ? nextRealmId() : existing.id()).placeholder("Realm ID").maxLength(64).build();
        TextInputWidget servers = new TextInputWidget.Builder().text(existing == null ? "" : routeNames(existing.nodeIds())).placeholder("Lobby, Survival").build();
        TextInputWidget families = new TextInputWidget.Builder().text(existing == null ? "PRESENCE" : existing.dataFamilies().stream().map(Enum::name).collect(Collectors.joining(", "))).placeholder("Inventory, Experience").build();
        TextInputWidget namespaces = new TextInputWidget.Builder().text(existing == null ? "" : String.join(", ", existing.persistentDataNamespaces())).placeholder("Plugin Namespaces").build();
        TextInputWidget snapshots = new TextInputWidget.Builder().text(Integer.toString(existing == null ? 20 : existing.retainedSnapshots())).placeholder("20").build();
        TextInputWidget days = new TextInputWidget.Builder().text(Integer.toString(existing == null ? 30 : existing.retentionDays())).placeholder("30").build();
        SyncLocationPolicy[] location = {existing == null ? SyncLocationPolicy.NEVER : existing.locationPolicy()};
        ConfigOption<SyncLocationPolicy> locationOption = ConfigOption.<SyncLocationPolicy>builder("Location").description("Choose how a player's last compatible location follows them between these servers.").options(List.of(SyncLocationPolicy.values())).display(value -> titleCase(value.name())).bind(() -> location[0], value -> location[0] = value).defaultValue(SyncLocationPolicy.NEVER).resettable(false).build();

        Setting.Builder identity = new Setting.Builder(existing == null ? "Add Player Realm" : "Edit " + existing.name());
        identity.addRow("name", "Name", name);
        identity.addRow("id", "ID", id);
        container.addWidget(identity.build());

        Setting.Builder membership = new Setting.Builder("Shared State");
        membership.addRow("servers", "Servers", servers);
        membership.addRow("families", "Player Data", families);
        membership.addOption(locationOption);
        membership.addRow("namespaces", "Plugin Data", namespaces);
        container.addWidget(membership.build());

        Setting.Builder retention = new Setting.Builder("Recovery");
        retention.addRow("snapshots", "Snapshots", snapshots);
        retention.addRow("days", "Retention Days", days);
        IconButton save = new IconButton.Builder().label("Save Realm").imagePath("save.png").onClick(() -> {
            locationOption.apply();
            saveRealm(existing, id.getText(), name.getText(), servers.getText(), families.getText(), location[0], namespaces.getText(), snapshots.getText(), days.getText());
        }).build();
        if (existing == null) {
            retention.addRow("actions", "", save);
        } else {
            IconButton delete = new IconButton.Builder().label("Delete Realm").imagePath("delete.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> {
                realms = realms.stream().filter(realm -> !realm.equals(existing)).toList();
                refreshDraft();
            }).build();
            retention.addRow("actions", "", delete, save);
        }
        container.addWidget(retention.build());
        container.updateWidgetPositions();
    }

    private void saveRealm(SyncRealm existing, String id, String name, String servers, String families, SyncLocationPolicy location, String namespaces, String snapshots, String days) {
        try {
            SyncRealm updated = realm(id, name, servers, families, location, namespaces, snapshots, days);
            List<SyncRealm> draft = new ArrayList<>(realms);
            if (existing == null) {
                draft.add(updated);
            } else {
                draft.set(draft.indexOf(existing), updated);
            }
            ensureUniqueRealmIds(draft);
            realms = List.copyOf(draft);
            refreshDraft();
        } catch (RuntimeException exception) {
            new Notification("Realm Invalid", rootMessage(exception), Notification.Type.ERROR);
        }
    }

    private SyncRealm realm(String rawId, String rawName, String rawServers, String rawFamilies, SyncLocationPolicy location, String rawNamespaces, String rawSnapshots, String rawDays) {
        String id = normalizeId(rawId);
        if (id.isBlank()) {
            throw new IllegalArgumentException("Realm ID Is Required");
        }
        String name = rawName == null || rawName.isBlank() ? titleCase(id) : rawName.trim();
        Map<String, NetworkMember> byRoute = eligibleMembers().stream().collect(Collectors.toMap(member -> member.routeName().toLowerCase(Locale.ROOT), member -> member, (first, second) -> first, LinkedHashMap::new));
        Set<String> nodes = commaValues(rawServers).stream().map(route -> {
            NetworkMember member = byRoute.get(route.toLowerCase(Locale.ROOT));
            if (member == null) {
                throw new IllegalArgumentException("Unknown Eligible Server " + route);
            }
            return member.nodeId();
        }).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<SyncDataFamily> stateFamilies = commaValues(rawFamilies).stream().map(value -> {
            try {
                return SyncDataFamily.valueOf(value.trim().replace(' ', '_').replace('-', '_').toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown State Family " + value, exception);
            }
        }).collect(Collectors.toCollection(LinkedHashSet::new));
        if (stateFamilies.isEmpty()) {
            throw new IllegalArgumentException("Choose At Least One State Family");
        }
        Set<String> namespaces = commaValues(rawNamespaces).stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
        try {
            return new SyncRealm(id, name, nodes, stateFamilies, location, namespaces, Integer.parseInt(rawSnapshots.trim()), Integer.parseInt(rawDays.trim()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Snapshot Count And Retention Days Must Be Numbers", exception);
        }
    }

    private void review() {
        if (preparing) {
            return;
        }
        preparing = true;
        Notification notification = new Notification.Builder().message("Preparing Realms").description(network.name()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        remotelyClient.getNetworkManager().prepareSharedData(network, realms, features, sharedDataPolicy, instances).whenComplete((prepared, throwable) -> ScreenManager.getInstance().execute(() -> {
            preparing = false;
            if (throwable != null) {
                notification.update().message("Review Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Review Ready").description(prepared.prepared().plan().changes().size() + " Changes").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            client.setScreen(new NetworkPlanReviewScreen(this, remotelyClient, prepared));
        }));
    }

    private List<NetworkMember> eligibleMembers() {
        return network.members().stream().filter(member -> !member.isProxy() && member.isManaged() && member.resyncEnabled()).toList();
    }

    private void ensureUniqueRealmIds(List<SyncRealm> draft) {
        Set<String> ids = new LinkedHashSet<>();
        if (draft.stream().anyMatch(realm -> !ids.add(realm.id()))) {
            throw new IllegalArgumentException("Realm IDs Must Be Unique");
        }
    }

    private List<String> commaValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private String routeNames(Set<String> nodeIds) {
        Map<String, String> routes = network.members().stream().collect(Collectors.toMap(NetworkMember::nodeId, NetworkMember::routeName));
        return nodeIds.stream().map(routes::get).filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(", "));
    }

    private String nextRealmId() {
        int index = 1;
        while (containsRealm("realm-" + index)) {
            index++;
        }
        return "realm-" + index;
    }

    private boolean containsRealm(String id) {
        return realms.stream().anyMatch(realm -> realm.id().equals(id));
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
    }

    private void refreshDraft() {
        client.setScreen(new NetworkRealmScreen(parent, remotelyClient, networkId, realms, features, sharedDataPolicy, null, false));
    }

    private String titleCase(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (char character : normalized.toCharArray()) {
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return result.toString();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
