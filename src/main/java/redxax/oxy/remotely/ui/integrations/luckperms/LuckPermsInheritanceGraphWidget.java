package redxax.oxy.remotely.ui.integrations.luckperms;

import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReMouseButton;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class LuckPermsInheritanceGraphWidget extends AnimatedWidget {
    private static final int NODE_HEIGHT = 34;
    private static final int NODE_WIDTH = 190;
    private static final int NODE_GAP = 24;
    private static final int ROW_GAP = 52;
    private static final int PADDING = 20;

    public record GroupNode(String id, String name, Integer weight, Set<String> parents) {
        public GroupNode {
            id = id == null ? "" : id;
            name = name == null || name.isBlank() ? id : name;
            parents = parents == null ? Set.of() : Set.copyOf(parents);
        }
    }

    private final Consumer<String> onOpen;
    private final BiConsumer<String, String> onConnect;
    private final BiConsumer<String, String> onDisconnect;
    private final AnimatedButton surface;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private List<GroupNode> groups = List.of();
    private String connectingParent = "";
    private String disconnectingChild = "";
    private Node pressedNode;
    private Node draggingNode;
    private boolean wiring;
    private boolean layoutDirty = true;
    private boolean childrenActive = true;
    private double pressX;
    private double pressY;
    private double wireX;
    private double wireY;
    private int dragOffsetX;
    private int dragOffsetY;
    private int minimumHeight = 280;

    public LuckPermsInheritanceGraphWidget(int x, int y, int width, Consumer<String> onOpen, BiConsumer<String, String> onConnect,
        BiConsumer<String, String> onDisconnect) {
        super(x, y, width, 280, "");
        this.onOpen = onOpen;
        this.onConnect = onConnect;
        this.onDisconnect = onDisconnect;
        surface = new AnimatedButton.Builder().label("").active(false).animateElevation(false).roundedCorners(false).size(width, 280).build();
        surface.entranceAnimationEnabled = false;
        entranceAnimationEnabled = false;
        animateLayout = false;
        active = true;
    }

    public void apply(List<GroupNode> groups) {
        this.groups = groups == null ? List.of() : groups.stream().sorted(Comparator.comparing((GroupNode group) -> group.weight() == null ? Integer.MIN_VALUE : group.weight()).reversed()
            .thenComparing(GroupNode::name, String.CASE_INSENSITIVE_ORDER)).toList();
        Set<String> visible = this.groups.stream().map(GroupNode::id).collect(Collectors.toSet());
        nodes.keySet().removeIf(id -> !visible.contains(id));
        for (GroupNode group : this.groups) {
            Node node = nodes.computeIfAbsent(group.id(), ignored -> createNode(group));
            node.group = group;
            updateNode(node);
        }
        if (!visible.contains(connectingParent)) connectingParent = "";
        if (!visible.contains(disconnectingChild)) disconnectingChild = "";
        updateHeight();
        layoutDirty = true;
    }

    public void setMinimumHeight(int minimumHeight) {
        this.minimumHeight = Math.max(180, minimumHeight);
        updateHeight();
    }

    @Override
    public void setWidth(int width) {
        boolean changed = width != getWidth();
        super.setWidth(width);
        updateHeight();
        layoutDirty |= changed;
    }

    @Override
    public void setPosition(int x, int y) {
        boolean changed = x != getX() || y != getY();
        super.setPosition(x, y);
        layoutDirty |= changed;
    }

    private Node createNode(GroupNode group) {
        SquareButtonWidget connect = new SquareButtonWidget.Builder().imagePath("link.png").hint("Drag To Child Group").hintDelay(0.15f)
            .accentType(ThemeManager.getDefaultAccent()).animateElevation(false).size(18, 18).build();
        SquareButtonWidget disconnect = new SquareButtonWidget.Builder().imagePath("delete.png").hint("Remove Parent Connection").hintDelay(0.15f)
            .accentType(ThemeManager.getDefaultAccent()).animateElevation(false).size(18, 18).build();
        MountableButtonWidget button = new MountableButtonWidget.Builder(group.name()).iconPath("manager.png").addButton(connect).addButton(disconnect).build();
        button.setSize(NODE_WIDTH, NODE_HEIGHT);
        button.entranceAnimationEnabled = false;
        button.setAnimateLayout(false);
        return new Node(group, button, connect, disconnect);
    }

    private void updateNode(Node node) {
        node.button.setName(node.group.name());
        String identity = node.group.name().equalsIgnoreCase(node.group.id()) ? "" : node.group.id() + " • ";
        node.button.setDescription(identity + node.group.parents().size() + (node.group.parents().size() == 1 ? " Parent" : " Parents"));
        node.button.setHiddenText(node.group.weight() == null ? "Group" : "Priority " + node.group.weight());
        node.disconnect.setVisible(!node.group.parents().isEmpty());
        node.connect.selectable = true;
        node.disconnect.selectable = true;
        node.connect.setSelected(node.group.id().equals(connectingParent));
        node.disconnect.setSelected(node.group.id().equals(disconnectingChild));
        node.connect.setHint(node.group.id().equals(connectingParent) ? "Cancel Connection" : "Drag To Child Group");
        node.disconnect.setHint(node.group.id().equals(disconnectingChild) ? "Cancel Removal" : "Remove Parent Connection");
        boolean removalTarget = !disconnectingChild.isBlank() && nodes.containsKey(disconnectingChild)
            && nodes.get(disconnectingChild).group.parents().contains(node.group.id());
        if (node.group.id().equals(disconnectingChild) || removalTarget) node.button.setAccent(ThemeManager.getAccent("danger"));
        else if (node.group.id().equals(connectingParent)) node.button.setAccent(ThemeManager.getAccent("nice"));
        else node.button.setAccent(ThemeManager.getDefaultAccent());
    }

    private void beginConnection(Node node) {
        if (node == null) return;
        connectingParent = node.group.id().equals(connectingParent) ? "" : node.group.id();
        disconnectingChild = "";
        nodes.values().forEach(this::updateNode);
    }

    private void beginDisconnection(Node node) {
        if (node == null || node.group.parents().isEmpty()) return;
        if (node.group.parents().size() == 1) {
            disconnectingChild = "";
            connectingParent = "";
            if (onDisconnect != null) onDisconnect.accept(node.group.id(), node.group.parents().iterator().next());
            return;
        }
        disconnectingChild = node.group.id().equals(disconnectingChild) ? "" : node.group.id();
        connectingParent = "";
        nodes.values().forEach(this::updateNode);
    }

    private void activate(Node node) {
        if (node == null) return;
        if (!connectingParent.isBlank()) {
            String parent = connectingParent;
            connectingParent = "";
            nodes.values().forEach(this::updateNode);
            if (!parent.equalsIgnoreCase(node.group.id()) && onConnect != null) onConnect.accept(node.group.id(), parent);
            return;
        }
        if (!disconnectingChild.isBlank()) {
            String child = disconnectingChild;
            Node childNode = nodes.get(child);
            if (childNode != null && childNode.group.parents().contains(node.group.id())) {
                disconnectingChild = "";
                nodes.values().forEach(this::updateNode);
                if (onDisconnect != null) onDisconnect.accept(child, node.group.id());
            }
            return;
        }
        if (onOpen != null) onOpen.accept(node.group.id());
    }

    private void updateHeight() {
        int columns = columns(Math.max(1, getWidth()));
        int rows = groups.isEmpty() ? 1 : (int) Math.ceil(groups.size() / (double) columns);
        setHeight(Math.max(minimumHeight, PADDING * 2 + rows * NODE_HEIGHT + Math.max(0, rows - 1) * ROW_GAP));
    }

    private void layout() {
        if (!layoutDirty) return;
        layoutDirty = false;
        int columns = columns(getWidth());
        for (int row = 0; row * columns < groups.size(); row++) {
            int first = row * columns;
            int count = Math.min(columns, groups.size() - first);
            int available = Math.max(1, getWidth() - PADDING * 2);
            int nodeWidth = Math.max(140, Math.min(NODE_WIDTH, (available - Math.max(0, count - 1) * NODE_GAP) / Math.max(1, count)));
            int rowWidth = count * nodeWidth + Math.max(0, count - 1) * NODE_GAP;
            int rowX = getX() + (getWidth() - rowWidth) / 2;
            for (int column = 0; column < count; column++) {
                Node node = nodes.get(groups.get(first + column).id());
                if (node == null) continue;
                if (!node.positioned) {
                    node.relativeX = rowX + column * (nodeWidth + NODE_GAP) - getX();
                    node.relativeY = PADDING + row * (NODE_HEIGHT + ROW_GAP);
                }
                node.relativeX = Math.clamp(node.relativeX, PADDING, Math.max(PADDING, getWidth() - nodeWidth - PADDING));
                node.relativeY = Math.clamp(node.relativeY, PADDING, Math.max(PADDING, getHeight() - NODE_HEIGHT - PADDING));
                node.button.setPosition(getX() + node.relativeX, getY() + node.relativeY);
                node.button.setSize(nodeWidth, NODE_HEIGHT);
            }
        }
    }

    @Override
    protected void drawContent(IDrawContext context, int mouseX, int mouseY) {
        layout();
        surface.setPosition(getX(), getY());
        surface.setSize(getWidth(), getHeight());
        surface.render(context, mouseX, mouseY, 0f);
        drawConnections(context);
        orderedNodes().forEach(node -> node.button.render(context, mouseX, mouseY, 0f));
    }

    private void drawConnections(IDrawContext context) {
        int color = ThemeManager.getColor(ThemeColor.innerBorder);
        for (GroupNode child : groups) {
            Node childNode = nodes.get(child.id());
            if (childNode == null) continue;
            for (String parent : child.parents()) {
                Node parentNode = nodes.get(parent);
                if (parentNode != null) drawConnection(context, childNode.button.getX() + childNode.button.getWidth() / 2, childNode.button.getY(),
                    parentNode.button.getX() + parentNode.button.getWidth() / 2, parentNode.button.getY() + parentNode.button.getHeight(), color);
            }
        }
        if (wiring && !connectingParent.isBlank()) {
            Node parent = nodes.get(connectingParent);
            if (parent != null) drawConnection(context, parent.connect.getX() + parent.connect.getWidth() / 2, parent.connect.getY() + parent.connect.getHeight() / 2,
                (int) wireX, (int) wireY, ThemeManager.getAccent("nice").getAccentColor());
        }
    }

    private void drawConnection(IDrawContext context, int startX, int startY, int endX, int endY, int color) {
        int midY = startY + (endY - startY) / 2;
        context.fill(startX, Math.min(startY, midY), startX + 1, Math.max(startY, midY) + 1, color);
        context.fill(Math.min(startX, endX), midY, Math.max(startX, endX) + 1, midY + 1, color);
        context.fill(endX, Math.min(midY, endY), endX + 1, Math.max(midY, endY) + 1, color);
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        if (!isVisible() || !childrenActive || event.button() != ReMouseButton.LEFT) return super.mouseClicked(event);
        layout();
        List<Node> ordered = orderedNodes();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            Node node = ordered.get(i);
            if (node.connect.isMouseOver(event.x(), event.y())) {
                beginConnection(node);
                wiring = !connectingParent.isBlank();
                pressX = wireX = event.x();
                pressY = wireY = event.y();
                return event.finish(true);
            }
            if (node.disconnect.isVisible() && node.disconnect.isMouseOver(event.x(), event.y())) {
                beginDisconnection(node);
                return event.finish(true);
            }
            if (node.button.isMouseOver(event.x(), event.y())) {
                pressedNode = node;
                draggingNode = null;
                pressX = event.x();
                pressY = event.y();
                dragOffsetX = (int) event.x() - node.button.getX();
                dragOffsetY = (int) event.y() - node.button.getY();
                return event.finish(true);
            }
        }
        return super.mouseClicked(event);
    }

    @Override
    public boolean mouseDragged(ReMouseEvent event) {
        if (wiring) {
            wireX = event.x();
            wireY = event.y();
            return event.finish(true);
        }
        if (pressedNode == null) return super.mouseDragged(event);
        if (draggingNode == null && Math.hypot(event.x() - pressX, event.y() - pressY) >= 3) draggingNode = pressedNode;
        if (draggingNode != null) {
            draggingNode.positioned = true;
            draggingNode.relativeX = Math.clamp((int) event.x() - dragOffsetX - getX(), PADDING,
                Math.max(PADDING, getWidth() - draggingNode.button.getWidth() - PADDING));
            draggingNode.relativeY = Math.clamp((int) event.y() - dragOffsetY - getY(), PADDING,
                Math.max(PADDING, getHeight() - NODE_HEIGHT - PADDING));
            layoutDirty = true;
        }
        return event.finish(true);
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        if (wiring) {
            wireX = event.x();
            wireY = event.y();
            boolean dragged = Math.hypot(event.x() - pressX, event.y() - pressY) >= 3;
            wiring = false;
            if (dragged) {
                Node target = nodeAt(event.x(), event.y());
                if (target != null) activate(target);
            }
            return event.finish(true);
        }
        if (pressedNode != null) {
            Node released = pressedNode;
            boolean dragged = draggingNode != null;
            pressedNode = null;
            draggingNode = null;
            if (!dragged && released.button.isMouseOver(event.x(), event.y())) activate(released);
            return event.finish(true);
        }
        return super.mouseReleased(event);
    }

    private Node nodeAt(double x, double y) {
        List<Node> ordered = orderedNodes();
        for (int i = ordered.size() - 1; i >= 0; i--) if (ordered.get(i).button.isMouseOver(x, y)) return ordered.get(i);
        return null;
    }

    @Override
    public void renderHintOverlay(IDrawContext context) {
        super.renderHintOverlay(context);
        nodes.values().forEach(node -> node.button.renderHintOverlay(context));
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        surface.setVisible(visible);
        nodes.values().forEach(node -> node.button.setVisible(visible));
    }

    @Override
    public void setActive(boolean active) {
        super.setActive(active);
        childrenActive = active;
        nodes.values().forEach(node -> node.button.setActive(active));
    }

    private List<Node> orderedNodes() {
        List<Node> ordered = new ArrayList<>();
        groups.forEach(group -> {
            Node node = nodes.get(group.id());
            if (node != null) ordered.add(node);
        });
        return ordered;
    }

    private static int columns(int width) {
        return Math.clamp(Math.max(1, (width - PADDING * 2 + NODE_GAP) / (NODE_WIDTH + NODE_GAP)), 1, 5);
    }

    private static final class Node {
        private GroupNode group;
        private final MountableButtonWidget button;
        private final SquareButtonWidget connect;
        private final SquareButtonWidget disconnect;
        private int relativeX;
        private int relativeY;
        private boolean positioned;

        private Node(GroupNode group, MountableButtonWidget button, SquareButtonWidget connect, SquareButtonWidget disconnect) {
            this.group = group;
            this.button = button;
            this.connect = connect;
            this.disconnect = disconnect;
        }
    }
}
