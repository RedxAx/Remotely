package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkMember;
import redxax.oxy.remotely.network.SyncDataFamily;
import redxax.oxy.remotely.network.SyncLocationPolicy;
import redxax.oxy.remotely.network.SyncRealm;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.PopupWidget;
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
    private NetworkDefinition network;
    private List<SyncRealm> realms = List.of();
    private List<Instance> instances = List.of();
    private boolean preparing;

    public NetworkRealmScreen(Screen parent, RemotelyClient remotelyClient, String networkId) {
        this(parent, remotelyClient, networkId, null);
    }

    private NetworkRealmScreen(Screen parent, RemotelyClient remotelyClient, String networkId, List<SyncRealm> initialRealms) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.networkId = networkId;
        this.initialRealms = initialRealms == null ? null : List.copyOf(initialRealms);
    }

    public String getDesktopAppId() {
        return "network-realms";
    }

    public String getDesktopAppTitle() {
        return "State Realms";
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
        instances = Rebase.get().getInstanceManager().getAllInstances();
        header().addLeft("close.png", () -> client.setScreen(parent), "Back").addRight("checkmark.png", this::review, "Review Realms").build();
        Container container = createContainer("network_realms", 6, 38, width - 12, Math.max(80, height - 44)).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        populate(container);
        setActiveContainer(container);
    }

    private void populate(Container container) {
        List<NetworkMember> eligible = eligibleMembers();
        container.addWidget(summary(realms.size() + " Realms • " + eligible.size() + " Eligible Servers", "ReSync State Ownership", realms.isEmpty() ? "warning" : "nice"));
        if (realms.isEmpty()) {
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 26).label("Shared Survival").hint("Inventory, Ender Chest, Experience, Vitals, Effects, And Player State").imagePath("add.png").accentType(ThemeManager.getAccent("nice")).onClick(this::sharedSurvival).build());
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Presence Only").hint("Network Visibility Without Gameplay State").imagePath("info.png").accentType(ThemeManager.getAccent("calm")).onClick(this::presenceOnly).build());
        }
        for (SyncRealm realm : realms) {
            String servers = routeNames(realm.nodeIds());
            String families = realm.dataFamilies().stream().map(value -> titleCase(value.name())).collect(Collectors.joining(", "));
            String hint = servers + " • " + families + " • " + titleCase(realm.locationPolicy().name()) + " • " + realm.retainedSnapshots() + " Snapshots / " + realm.retentionDays() + " Days";
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 32).label(realm.name() + " • " + realm.id()).hint(hint).imagePath("merge.png").accentType(ThemeManager.getAccent(realm.nodeIds().size() < 2 ? "warning" : "calm")).onClick(() -> editRealm(realm)).build());
        }
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Add Realm").hint("Choose Servers, State Families, And Location Policy").imagePath("add.png").accentType(ThemeManager.getAccent("nice")).onClick(() -> editRealm(null)).build());
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Review Realms").hint("Review Exact ReSync Configuration Changes").imagePath("checkmark.png").accentType(ThemeManager.getAccent("nice")).onClick(this::review).build());
    }

    private void sharedSurvival() {
        Set<String> nodes = eligibleMembers().stream().map(NetworkMember::nodeId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<SyncDataFamily> families = new LinkedHashSet<>(List.of(SyncDataFamily.PRESENCE, SyncDataFamily.INVENTORY, SyncDataFamily.ENDER_CHEST, SyncDataFamily.EXPERIENCE, SyncDataFamily.VITALS, SyncDataFamily.EFFECTS, SyncDataFamily.PLAYER_STATE, SyncDataFamily.LOCATION));
        realms = List.of(new SyncRealm("survival", "Shared Survival", nodes, families, SyncLocationPolicy.REALM_RETURN_POINT, Set.of(), 20, 30));
        refreshDraft();
    }

    private void presenceOnly() {
        Set<String> nodes = eligibleMembers().stream().map(NetworkMember::nodeId).collect(Collectors.toCollection(LinkedHashSet::new));
        realms = List.of(SyncRealm.presence("network", "Network Presence", nodes));
        refreshDraft();
    }

    private void editRealm(SyncRealm existing) {
        String[] id = {existing == null ? nextRealmId() : existing.id()};
        String[] name = {existing == null ? "State Realm" : existing.name()};
        String[] servers = {existing == null ? "" : routeNames(existing.nodeIds())};
        String[] families = {existing == null ? "PRESENCE" : existing.dataFamilies().stream().map(Enum::name).collect(Collectors.joining(", "))};
        String[] namespaces = {existing == null ? "" : String.join(", ", existing.persistentDataNamespaces())};
        String[] snapshots = {Integer.toString(existing == null ? 20 : existing.retainedSnapshots())};
        String[] days = {Integer.toString(existing == null ? 30 : existing.retentionDays())};
        SyncLocationPolicy[] location = {existing == null ? SyncLocationPolicy.NEVER : existing.locationPolicy()};
        PopupWidget[] popup = new PopupWidget[1];
        AnimatedButton save = new AnimatedButton.Builder().size(90, 20).label("Save Realm").accentType(ThemeManager.getAccent("nice")).onClick(() -> {
            try {
                SyncRealm updated = realm(id[0], name[0], servers[0], families[0], location[0], namespaces[0], snapshots[0], days[0]);
                List<SyncRealm> draft = new ArrayList<>(realms);
                if (existing == null) {
                    draft.add(updated);
                } else {
                    draft.set(draft.indexOf(existing), updated);
                }
                ensureUniqueRealmIds(draft);
                realms = List.copyOf(draft);
                popup[0].hide();
                refreshDraft();
            } catch (RuntimeException exception) {
                new Notification("Realm Invalid", rootMessage(exception), Notification.Type.ERROR);
            }
        }).build();
        PopupWidget.Builder builder = new PopupWidget.Builder(existing == null ? "Add Realm" : "Edit " + existing.name()).size(440, 330).setResizable(true).setExpandWithDropdowns(true).onClose(() -> popup[0].hide());
        builder.addTextField("Name", name[0], value -> name[0] = value);
        builder.addTextField("ID", id[0], value -> id[0] = value);
        builder.addTextField("Servers", servers[0], value -> servers[0] = value);
        builder.addTextField("Families", families[0], value -> families[0] = value);
        builder.addDropdown("Location", Arrays.asList(SyncLocationPolicy.values()), location[0], value -> titleCase(value.name()), value -> location[0] = value);
        builder.addTextField("PDC Namespaces", namespaces[0], value -> namespaces[0] = value);
        builder.addTextField("Snapshot Count", snapshots[0], value -> snapshots[0] = value);
        builder.addTextField("Retention Days", days[0], value -> days[0] = value);
        if (existing == null) {
            builder.addRow("saveRealm", "", true, 24, save);
        } else {
            AnimatedButton delete = new AnimatedButton.Builder().size(90, 20).label("Delete Realm").accentType(ThemeManager.getAccent("danger")).onClick(() -> {
                realms = realms.stream().filter(realm -> !realm.equals(existing)).toList();
                popup[0].hide();
                refreshDraft();
            }).build();
            builder.addRow("realmActions", "", true, 24, delete, save);
        }
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
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
        remotelyClient.getNetworkManager().prepareRealms(network, realms, instances).whenComplete((prepared, throwable) -> ScreenManager.getInstance().execute(() -> {
            preparing = false;
            if (throwable != null) {
                notification.update().message("Realm Review Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Realm Review Ready").description(prepared.prepared().plan().changes().size() + " Changes").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
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

    private AnimatedButton summary(String label, String hint, String accent) {
        return new AnimatedButton.Builder().size(Math.max(220, width - 44), 22).label(label).hint(hint).accentType(ThemeManager.getAccent(accent)).build();
    }

    private void refreshDraft() {
        client.setScreen(new NetworkRealmScreen(parent, remotelyClient, networkId, realms));
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
