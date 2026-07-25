package redxax.oxy.remotely.ui.integrations.luckperms;

import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsNetworkClient.Delivery;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsNetworkClient.Snapshot;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsNetworkClient.Target;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReMouseButton;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class LuckPermsNetworkGraphWidget extends AnimatedWidget {
    private static final int NODE_HEIGHT = 30;
    private static final int NODE_GAP = 8;
    private static final int ROW_GAP = 24;
    private static final int PADDING = 6;
    private static final int TREE_MARGIN = 18;
    private static final int MIN_NODE_WIDTH = 178;
    private final Consumer<Map<String, Set<Delivery>>> onChange;
    private final LuckPermsServerIcons icons;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final MountableButtonWidget actions;
    private Snapshot snapshot;
    private boolean layoutDirty = true;
    private boolean childrenActive = true;

    public LuckPermsNetworkGraphWidget(int x, int y, int width, Snapshot snapshot, Consumer<Map<String, Set<Delivery>>> onChange) {
        this(x, y, width, snapshot, onChange, new LuckPermsServerIcons());
    }

    public LuckPermsNetworkGraphWidget(int x, int y, int width, Snapshot snapshot, Consumer<Map<String, Set<Delivery>>> onChange, LuckPermsServerIcons icons) {
        super(x, y, width, 1, "");
        this.snapshot = snapshot;
        this.onChange = onChange;
        this.icons = icons;
        actions = new MountableButtonWidget.Builder("Permission Delivery")
            .description(networkName(snapshot))
            .addButton(action("checkmark.png", "Receive On Ready Servers", this::receiveOnReadyServers))
            .addButton(action("server.png", "Source Only", this::sourceOnly))
            .build();
        actions.entranceAnimationEnabled = false;
        actions.setAnimateLayout(false);
        entranceAnimationEnabled = false;
        animateLayout = false;
        active = true;
        applySnapshot(snapshot);
    }

    public void applySnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
        icons.refresh();
        actions.setDescription(networkName(snapshot));
        Set<String> visible = snapshot.targets().stream().map(Target::instanceId).collect(java.util.stream.Collectors.toSet());
        nodes.keySet().removeIf(instanceId -> !visible.contains(instanceId));
        orderedTargets(snapshot).forEach(target -> {
            Node node = nodes.computeIfAbsent(target.instanceId(), ignored -> node(target));
            node.target = target;
            update(node);
            icons.load(target.instanceId(), node.button::setIcon);
        });
        setHeight(preferredHeight(getWidth(), Math.max(0, snapshot.targets().size() - 1)));
        layoutDirty = true;
    }

    public static int preferredHeight(int width, int receiverCount) {
        int rows = receiverCount == 0 ? 0 : (int) Math.ceil(receiverCount / (double) columns(width));
        return PADDING * 2 + NODE_HEIGHT * 2 + NODE_GAP + (rows == 0 ? 0 : 28 + rows * NODE_HEIGHT + Math.max(0, rows - 1) * ROW_GAP);
    }

    @Override
    public void setWidth(int width) {
        boolean changed = width != getWidth();
        super.setWidth(width);
        setHeight(preferredHeight(width, Math.max(0, snapshot.targets().size() - 1)));
        layoutDirty |= changed;
    }

    @Override
    public void setPosition(int x, int y) {
        boolean changed = x != getX() || y != getY();
        super.setPosition(x, y);
        layoutDirty |= changed;
    }

    private Node node(Target target) {
        Node[] reference = new Node[1];
        MountableButtonWidget button = new MountableButtonWidget.Builder(target.name())
            .icon(icons.icon(target.instanceId()))
            .build();
        SquareButtonWidget receive = action("checkmark.png", "Receive", () -> toggleReceive(reference[0]));
        SquareButtonWidget players = action("steve.png", "Players", () -> toggleDelivery(reference[0], Delivery.PLAYERS));
        SquareButtonWidget groups = action("manager.png", "Groups", () -> toggleDelivery(reference[0], Delivery.GROUPS));
        SquareButtonWidget tracks = action("graph.png", "Tracks", () -> toggleDelivery(reference[0], Delivery.TRACKS));
        button.addMountedWidget(receive);
        button.addMountedWidget(players);
        button.addMountedWidget(groups);
        button.addMountedWidget(tracks);
        button.setSize(MIN_NODE_WIDTH, NODE_HEIGHT);
        button.entranceAnimationEnabled = false;
        button.setAnimateLayout(false);
        Node node = new Node(target, button, receive, players, groups, tracks);
        reference[0] = node;
        return node;
    }

    private void update(Node node) {
        Target target = node.target;
        boolean source = target.instanceId().equals(snapshot.sourceInstanceId());
        node.button.setName(target.name().isBlank() ? target.instanceId() : target.name());
        node.button.setDescription(target.detail());
        node.button.setHiddenText(source ? "Source" : target.selected() ? deliveryText(target.deliveries()) : "Does Not Receive");
        node.button.setAccent(ThemeManager.getDefaultAccent());
        node.receive.setIcon(Identifier.icon(target.selected() ? "checkmark.png" : "close.png"));
        node.receive.setHint(source ? "Source Always Receives" : target.selected() ? "Do Not Receive" : "Receive");
        node.receive.setActive(!source);
        updateChoice(node.players, target.deliveries().contains(Delivery.PLAYERS), source, "Players");
        updateChoice(node.groups, target.deliveries().contains(Delivery.GROUPS), source, "Groups");
        updateChoice(node.tracks, target.deliveries().contains(Delivery.TRACKS), source, "Tracks");
    }

    private void updateChoice(SquareButtonWidget button, boolean selected, boolean source, String name) {
        button.setSelected(selected);
        button.selectable = true;
        button.setActive(!source);
        button.setHint(source ? name + " Always Included" : selected ? "Exclude " + name : "Include " + name);
    }

    private void toggleReceive(Node node) {
        if (node == null || node.target.instanceId().equals(snapshot.sourceInstanceId())) {
            return;
        }
        Map<String, Set<Delivery>> configured = configured();
        if (node.target.selected()) {
            configured.remove(node.target.instanceId());
        } else {
            configured.put(node.target.instanceId(), Delivery.all());
        }
        changed(configured);
    }

    private void toggleDelivery(Node node, Delivery delivery) {
        if (node == null || node.target.instanceId().equals(snapshot.sourceInstanceId())) {
            return;
        }
        Map<String, Set<Delivery>> configured = configured();
        EnumSet<Delivery> choices = node.target.deliveries().isEmpty()
            ? EnumSet.noneOf(Delivery.class) : EnumSet.copyOf(node.target.deliveries());
        if (!choices.add(delivery)) {
            choices.remove(delivery);
        }
        if (choices.isEmpty()) {
            configured.remove(node.target.instanceId());
        } else {
            configured.put(node.target.instanceId(), Set.copyOf(choices));
        }
        changed(configured);
    }

    private void receiveOnReadyServers() {
        Map<String, Set<Delivery>> configured = configured();
        snapshot.targets().stream().filter(Target::available).forEach(target -> configured.put(target.instanceId(), Delivery.all()));
        changed(configured);
    }

    private void sourceOnly() {
        changed(new LinkedHashMap<>(Map.of(snapshot.sourceInstanceId(), Delivery.all())));
    }

    private Map<String, Set<Delivery>> configured() {
        Map<String, Set<Delivery>> configured = new LinkedHashMap<>();
        snapshot.targets().stream().filter(Target::selected).forEach(target -> configured.put(target.instanceId(), target.deliveries()));
        configured.put(snapshot.sourceInstanceId(), Delivery.all());
        return configured;
    }

    private void changed(Map<String, Set<Delivery>> configured) {
        if (onChange != null) {
            onChange.accept(Map.copyOf(configured));
        }
    }

    private void layout() {
        if (!layoutDirty) {
            return;
        }
        layoutDirty = false;
        actions.setPosition(getX() + PADDING, getY() + PADDING);
        actions.setSize(Math.max(1, getWidth() - PADDING * 2), NODE_HEIGHT);
        List<Node> ordered = orderedNodes();
        if (ordered.isEmpty()) {
            return;
        }
        int sourceY = getY() + PADDING + NODE_HEIGHT + NODE_GAP;
        Node source = ordered.getFirst();
        int sourceWidth = Math.max(1, getWidth() - TREE_MARGIN * 2);
        source.button.setPosition(getX() + (getWidth() - sourceWidth) / 2, sourceY);
        source.button.setSize(sourceWidth, NODE_HEIGHT);
        int receiverCount = ordered.size() - 1;
        if (receiverCount == 0) {
            return;
        }
        int columns = columns(getWidth());
        int contentWidth = Math.max(1, getWidth() - TREE_MARGIN * 2);
        int gridY = sourceY + NODE_HEIGHT + 28;
        int rows = (int) Math.ceil(receiverCount / (double) columns);
        for (int row = 0; row < rows; row++) {
            int first = 1 + row * columns;
            int count = Math.min(columns, ordered.size() - first);
            int nodeWidth = Math.max(1, (contentWidth - Math.max(0, count - 1) * NODE_GAP) / count);
            int rowWidth = count * nodeWidth + Math.max(0, count - 1) * NODE_GAP;
            int rowX = getX() + (getWidth() - rowWidth) / 2;
            for (int column = 0; column < count; column++) {
                MountableButtonWidget button = ordered.get(first + column).button;
                button.setPosition(rowX + column * (nodeWidth + NODE_GAP), gridY + row * (NODE_HEIGHT + ROW_GAP));
                button.setSize(nodeWidth, NODE_HEIGHT);
            }
        }
    }

    @Override
    protected void drawContent(IDrawContext context, int mouseX, int mouseY) {
        layout();
        drawConnections(context);
        actions.render(context, mouseX, mouseY, 0f);
        orderedNodes().forEach(node -> node.button.render(context, mouseX, mouseY, 0f));
    }

    private void drawConnections(IDrawContext context) {
        List<Node> ordered = orderedNodes();
        if (ordered.size() < 2) {
            return;
        }
        MountableButtonWidget source = ordered.getFirst().button;
        int color = ThemeManager.getColor(ThemeColor.innerBorder);
        int sourceCenter = source.getX() + source.getWidth() / 2;
        Map<Integer, List<MountableButtonWidget>> rows = new LinkedHashMap<>();
        ordered.stream().skip(1).forEach(node -> rows.computeIfAbsent(node.button.getY(), ignored -> new ArrayList<>()).add(node.button));
        int lastBusY = rows.values().stream().mapToInt(row -> row.getFirst().getY() - 8).max().orElse(source.getY() + source.getHeight());
        context.fill(sourceCenter, source.getY() + source.getHeight(), sourceCenter + 1, lastBusY + 1, color);
        for (List<MountableButtonWidget> row : rows.values()) {
            int busY = row.getFirst().getY() - 8;
            int left = row.getFirst().getX() + row.getFirst().getWidth() / 2;
            int right = row.getLast().getX() + row.getLast().getWidth() / 2;
            context.fill(left, busY, right + 1, busY + 1, color);
            row.forEach(button -> {
                int center = button.getX() + button.getWidth() / 2;
                context.fill(center, busY, center + 1, button.getY(), color);
            });
        }
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        if (!isVisible() || !childrenActive || event.button() != ReMouseButton.LEFT) {
            return super.mouseClicked(event);
        }
        List<Node> ordered = orderedNodes();
        for (int index = ordered.size() - 1; index >= 0; index--) {
            if (ordered.get(index).button.mouseClicked(event)) {
                return true;
            }
        }
        return actions.mouseClicked(event) || super.mouseClicked(event);
    }

    @Override
    public void renderHintOverlay(IDrawContext context) {
        super.renderHintOverlay(context);
        actions.renderHintOverlay(context);
        nodes.values().forEach(node -> node.button.renderHintOverlay(context));
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        actions.setVisible(visible);
        nodes.values().forEach(node -> node.button.setVisible(visible));
    }

    @Override
    public void setActive(boolean active) {
        super.setActive(active);
        childrenActive = active;
        actions.setActive(active);
        nodes.values().forEach(node -> node.button.setActive(active));
        nodes.values().forEach(this::update);
    }

    private List<Node> orderedNodes() {
        return orderedTargets(snapshot).stream().map(target -> nodes.get(target.instanceId())).filter(node -> node != null).toList();
    }

    private static List<Target> orderedTargets(Snapshot snapshot) {
        return snapshot.targets().stream().sorted(Comparator
            .comparing((Target target) -> !target.instanceId().equals(snapshot.sourceInstanceId()))
            .thenComparing(Target::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static int columns(int width) {
        return Math.clamp(Math.max(1, (width - TREE_MARGIN * 2 + NODE_GAP) / (MIN_NODE_WIDTH + NODE_GAP)), 1, 4);
    }

    private static String deliveryText(Set<Delivery> deliveries) {
        if (deliveries.size() == Delivery.values().length) {
            return "Receives Everything";
        }
        List<String> names = new ArrayList<>();
        if (deliveries.contains(Delivery.PLAYERS)) {
            names.add("Players");
        }
        if (deliveries.contains(Delivery.GROUPS)) {
            names.add("Groups");
        }
        if (deliveries.contains(Delivery.TRACKS)) {
            names.add("Tracks");
        }
        return "Receives " + String.join(", ", names);
    }

    private static String networkName(Snapshot snapshot) {
        return snapshot.networkName().isBlank() ? "This Server" : snapshot.networkName();
    }

    private static SquareButtonWidget action(String icon, String hint, Runnable action) {
        return new SquareButtonWidget.Builder().imagePath(icon).hint(hint).hintDelay(0.15f).onClick(action)
            .accentType(ThemeManager.getDefaultAccent()).animateElevation(false).size(18, 18).build();
    }

    private static final class Node {
        private Target target;
        private final MountableButtonWidget button;
        private final SquareButtonWidget receive;
        private final SquareButtonWidget players;
        private final SquareButtonWidget groups;
        private final SquareButtonWidget tracks;

        private Node(Target target, MountableButtonWidget button, SquareButtonWidget receive, SquareButtonWidget players,
                     SquareButtonWidget groups, SquareButtonWidget tracks) {
            this.target = target;
            this.button = button;
            this.receive = receive;
            this.players = players;
            this.groups = groups;
            this.tracks = tracks;
        }
    }
}
