package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.DesktopNetworkAccess;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkMember;
import redxax.oxy.remotely.network.RoutingGroup;
import redxax.oxy.remotely.network.RoutingStrategy;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


import java.util.stream.Collectors;

public class NetworkRoutingScreen extends ReScreen {
    private final Screen parent;
    private final RemotelyClient remotelyClient;
    private final String networkId;
    private final List<RoutingGroup> initialGroups;
    private NetworkDefinition network;
    private List<RoutingGroup> groups = List.of();
    private List<Instance> instances = List.of();
    private boolean preparing;

    public NetworkRoutingScreen(Screen parent, RemotelyClient remotelyClient, String networkId) {
        this(parent, remotelyClient, networkId, null);
    }

    private NetworkRoutingScreen(Screen parent, RemotelyClient remotelyClient, String networkId, List<RoutingGroup> initialGroups) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.networkId = networkId;
        this.initialGroups = initialGroups == null ? null : List.copyOf(initialGroups);
    }

    public String getDesktopAppId() {
        return "network-routing";
    }

    public String getDesktopAppTitle() {
        return "Network Routing";
    }

    public String getDesktopAppIconPath() {
        return "network.png";
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
        network = DesktopNetworkAccess.capability(remotelyClient).getNetwork(networkId).orElse(null);
        if (network == null) {
            new Notification("Network Unavailable", Notification.Type.ERROR);
            client.setScreen(parent);
            return;
        }
        groups = initialGroups == null ? List.copyOf(network.routingGroups()) : initialGroups;
        instances = Rebase.get().getInstanceManager().getAllInstances();
        header().addLeft("close.png", () -> client.setScreen(parent), "Back").addRight("checkmark.png", this::review, "Review Routing").build();
        Container routing = createContainer("network_routing", 6, 38, width - 12, Math.max(80, height - 44)).columns(1).padding(8).verticalSpacing(4).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(true);
        populate(routing);
        setActiveContainer(routing);
    }

    private void populate(Container container) {
        RoutingGroup fallback = groups.stream().filter(group -> group.id().equals("fallback")).findFirst().orElse(null);
        String fallbackLabel = fallback == null ? "Fallback Missing" : "Fallback " + routeNames(fallback.nodeIds());
        MountableButtonWidget summary = new MountableButtonWidget.Builder("Player Routing")
            .description(groups.size() + " Groups • " + groups.stream().mapToInt(group -> group.forcedHosts().size()).sum() + " Domains")
            .hiddenText(fallbackLabel)
            .build();
        styleRow(container, summary, ThemeManager.getDefaultAccent(), 32);
        container.addWidget(summary);
        for (RoutingGroup group : groups) {
            String nodes = routeNames(group.nodeIds());
            List<String> details = new ArrayList<>();
            if (group.id().equals("fallback")) {
                details.add("Fallback");
            }
            if (!group.forcedHosts().isEmpty()) {
                details.add(group.forcedHosts().size() + " Domains");
            }
            if (!group.permission().isBlank()) {
                details.add("Permission Route");
            }
            if (!group.fallbackGroupId().isBlank()) {
                details.add("Falls Back To " + groupName(group.fallbackGroupId()));
            }
            MountableButtonWidget row = new MountableButtonWidget.Builder(group.name())
                .description(titleCase(group.strategy().name()) + " • " + (nodes.isBlank() ? "No Servers" : nodes))
                .hiddenText(String.join(" • ", details))
                .onClick(() -> editGroup(group))
                .addButton(rowAction("edit.png", "Edit", () -> editGroup(group)))
                .build();
            styleRow(container, row, ThemeManager.getDefaultAccent(), 34);
            container.addWidget(row);
        }
        MountableButtonWidget actions = new MountableButtonWidget.Builder("Routing Changes")
            .description("Create Routes Or Review Velocity Changes")
            .onClick(this::review)
            .addButton(rowAction("add.png", "Add Group", () -> editGroup(null)))
            .addButton(rowAction("checkmark.png", "Review", this::review))
            .build();
        styleRow(container, actions, ThemeManager.getDefaultAccent(), 34);
        container.addWidget(actions);
        container.updateWidgetPositions();
    }

    private void editGroup(RoutingGroup existing) {
        String[] id = {existing == null ? nextGroupId() : existing.id()};
        String[] name = {existing == null ? "Routing Group" : existing.name()};
        String[] routes = {existing == null ? "" : routeNames(existing.nodeIds())};
        String[] weights = {existing == null ? "" : routeWeights(existing)};
        String[] forcedHosts = {existing == null ? "" : String.join(", ", existing.forcedHosts())};
        String[] fallback = {existing == null ? "" : existing.fallbackGroupId()};
        String[] permission = {existing == null ? "" : existing.permission()};
        RoutingStrategy[] strategy = {existing == null ? RoutingStrategy.ORDERED : existing.strategy()};
        PopupWidget[] popup = new PopupWidget[1];
        AnimatedButton save = new AnimatedButton.Builder().size(90, 20).label("Save Group").accentType(ThemeManager.getDefaultAccent()).onClick(() -> {
            try {
                RoutingGroup updated = routingGroup(id[0], name[0], strategy[0], routes[0], weights[0], forcedHosts[0], fallback[0], permission[0]);
                List<RoutingGroup> draft = new ArrayList<>(groups);
                if (existing == null) {
                    draft.add(updated);
                } else {
                    int index = draft.indexOf(existing);
                    draft.set(index, updated);
                    if (!existing.id().equals(updated.id())) {
                        draft = draft.stream().map(group -> group.fallbackGroupId().equals(existing.id()) ? new RoutingGroup(group.id(), group.name(), group.strategy(), group.nodeIds(), group.weights(), updated.id(), group.forcedHosts(), group.permission()) : group).collect(Collectors.toCollection(ArrayList::new));
                    }
                }
                ensureUniqueGroupIds(draft);
                groups = List.copyOf(draft);
                popup[0].hide();
                refreshDraft();
            } catch (RuntimeException exception) {
                new Notification("Routing Invalid", rootMessage(exception), Notification.Type.ERROR);
            }
        }).build();
        PopupWidget.Builder builder = new PopupWidget.Builder(existing == null ? "Add Routing Group" : "Edit " + existing.name()).size(430, 320).setResizable(true).setExpandWithDropdowns(true).onClose(() -> popup[0].hide());
        builder.addTextField("Name", name[0], value -> name[0] = value);
        builder.addDropdown("Strategy", Arrays.asList(RoutingStrategy.values()), strategy[0], value -> titleCase(value.name()), value -> strategy[0] = value);
        builder.addTextField("Server Order", routes[0], value -> routes[0] = value);
        builder.addTextField("Weights (Server=Value)", weights[0], value -> weights[0] = value);
        builder.addTextField("Domains", forcedHosts[0], value -> forcedHosts[0] = value);
        List<String> fallbackGroups = new ArrayList<>();
        fallbackGroups.add("");
        fallbackGroups.addAll(groups.stream().filter(group -> existing == null || !group.id().equals(existing.id())).map(RoutingGroup::id).toList());
        builder.addDropdown("Fallback", fallbackGroups, fallback[0], value -> value.isBlank() ? "None" : groupName(value), value -> fallback[0] = value);
        builder.addTextField("Permission", permission[0], value -> permission[0] = value);
        if (existing == null) {
            builder.addTitleAction("Save", () -> save.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        } else {
            AnimatedButton delete = new AnimatedButton.Builder().size(90, 20).label("Delete Group").accentType(ThemeManager.getAccent("danger")).onClick(() -> {
                if (groups.stream().anyMatch(group -> group.fallbackGroupId().equals(existing.id()))) {
                    new Notification("Group In Use", "Remove Fallback References First", Notification.Type.ERROR);
                    return;
                }
                groups = groups.stream().filter(group -> !group.equals(existing)).toList();
                popup[0].hide();
                refreshDraft();
            }).build();
            builder.addTitleAction("Delete", () -> delete.onClick(0, 0, 0), PopupWidget.TitleActionRole.DESTRUCTIVE);
            builder.addTitleAction("Save", () -> save.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        }
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private SquareButtonWidget rowAction(String icon, String hint, Runnable action) {
        return new SquareButtonWidget.Builder().imagePath(icon).hint(hint).onClick(action).accentType(ThemeManager.getDefaultAccent()).animateElevation(false).size(18, 18).build();
    }

    private void styleRow(Container container, MountableButtonWidget row, Accent accent, int height) {
        row.setAccent(accent);
        row.setSize(Math.max(220, container.getEffectiveWidth() - 10), height);
    }

    private String groupName(String id) {
        return groups.stream().filter(group -> group.id().equals(id)).map(RoutingGroup::name).findFirst().orElse(titleCase(id));
    }

    private RoutingGroup routingGroup(String rawId, String rawName, RoutingStrategy strategy, String rawRoutes, String rawWeights, String rawHosts, String rawFallback, String permission) {
        String id = normalizeId(rawId);
        if (id.isBlank()) {
            throw new IllegalArgumentException("Group ID Is Required");
        }
        String name = rawName == null || rawName.isBlank() ? titleCase(id) : rawName.trim();
        Map<String, NetworkMember> membersByRoute = network.members().stream().filter(member -> !member.isProxy()).collect(Collectors.toMap(member -> member.routeName().toLowerCase(Locale.ROOT), member -> member, (first, second) -> first, LinkedHashMap::new));
        List<String> nodeIds = commaValues(rawRoutes).stream().map(route -> {
            NetworkMember member = membersByRoute.get(route.toLowerCase(Locale.ROOT));
            if (member == null) {
                throw new IllegalArgumentException("Unknown Route " + route);
            }
            return member.nodeId();
        }).distinct().toList();
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (String value : commaValues(rawWeights)) {
            int separator = value.indexOf('=');
            if (separator <= 0 || separator == value.length() - 1) {
                throw new IllegalArgumentException("Weights Use Route=Weight");
            }
            String route = value.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            NetworkMember member = membersByRoute.get(route);
            if (member == null || !nodeIds.contains(member.nodeId())) {
                throw new IllegalArgumentException("Weight References Unknown Route " + route);
            }
            try {
                int weight = Integer.parseInt(value.substring(separator + 1).trim());
                if (weight < 1 || weight > 10_000) {
                    throw new IllegalArgumentException("Weight Must Be Between 1 And 10000 For " + route);
                }
                weights.put(member.nodeId(), weight);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Weight Must Be A Number For " + route, exception);
            }
        }
        if (strategy == RoutingStrategy.WEIGHTED && nodeIds.stream().anyMatch(nodeId -> weights.getOrDefault(nodeId, 0) <= 0)) {
            throw new IllegalArgumentException("Weighted Routing Needs A Positive Weight For Every Server");
        }
        Set<String> hosts = commaValues(rawHosts).stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
        String fallback = normalizeId(rawFallback);
        if (fallback.equals(id)) {
            throw new IllegalArgumentException("A Group Cannot Fall Back To Itself");
        }
        if (!fallback.isBlank() && groups.stream().noneMatch(group -> group.id().equals(fallback))) {
            throw new IllegalArgumentException("Unknown Fallback Group " + fallback);
        }
        return new RoutingGroup(id, name, strategy, nodeIds, weights, fallback, hosts, permission);
    }

    private void review() {
        if (preparing) {
            return;
        }
        preparing = true;
        Notification notification = new Notification.Builder().message("Preparing Routing").description(network.name()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        DesktopNetworkAccess.capability(remotelyClient).prepareRouting(network, groups, instances).whenComplete((prepared, throwable) -> ScreenManager.getInstance().execute(() -> {
            preparing = false;
            if (throwable != null) {
                notification.update().message("Routing Review Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Routing Review Ready").description(prepared.prepared().plan().changes().size() + " Changes").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            client.setScreen(new NetworkPlanReviewScreen(this, remotelyClient, prepared));
        }));
    }

    private void ensureUniqueGroupIds(List<RoutingGroup> draft) {
        Set<String> ids = new LinkedHashSet<>();
        if (draft.stream().anyMatch(group -> !ids.add(group.id()))) {
            throw new IllegalArgumentException("Routing Group IDs Must Be Unique");
        }
    }

    private List<String> commaValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private String routeNames(List<String> nodeIds) {
        Map<String, String> routesByNode = network.members().stream().collect(Collectors.toMap(NetworkMember::nodeId, NetworkMember::routeName));
        return nodeIds.stream().map(routesByNode::get).filter(route -> route != null && !route.isBlank()).collect(Collectors.joining(", "));
    }

    private String routeWeights(RoutingGroup group) {
        Map<String, String> routesByNode = network.members().stream().collect(Collectors.toMap(NetworkMember::nodeId, NetworkMember::routeName));
        return group.nodeIds().stream().filter(group.weights()::containsKey).map(nodeId -> routesByNode.getOrDefault(nodeId, nodeId) + "=" + group.weights().get(nodeId)).collect(Collectors.joining(", "));
    }

    private String nextGroupId() {
        if (groups.stream().noneMatch(group -> group.id().equals("fallback"))) {
            return "fallback";
        }
        int index = 1;
        while (containsGroup("route-" + index)) {
            index++;
        }
        return "route-" + index;
    }

    private boolean containsGroup(String id) {
        return groups.stream().anyMatch(group -> group.id().equals(id));
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
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

    private void refreshDraft() {
        client.setScreen(new NetworkRoutingScreen(parent, remotelyClient, networkId, groups));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
