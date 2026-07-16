package redxax.oxy.remotely.ui.widgets;

import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkMember;
import redxax.oxy.remotely.network.NetworkMemberObservation;
import redxax.oxy.remotely.network.NetworkRuntimeSnapshot;
import redxax.oxy.remotely.network.NetworkTopologyHeat;
import redxax.oxy.remotely.network.RoutingGroup;
import restudio.resync.network.NetworkNodePresence;
import restudio.resync.network.NetworkNodeStatus;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReMouseButton;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.widgets.AnimatedWidget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class NetworkTopologyWidget extends AnimatedWidget {
    private static final int NODE_HEIGHT = 48;
    private static final int NODE_GAP = 12;
    private static final int ROW_GAP = 24;
    private final NetworkDefinition network;
    private final Map<String, NetworkMemberObservation> observations = new LinkedHashMap<>();
    private final List<NodeBounds> nodes = new ArrayList<>();
    private final Consumer<NetworkMember> onMemberSelected;
    private final Supplier<NetworkRuntimeSnapshot> runtimeSnapshot;
    private final Supplier<NetworkTopologyHeat> heatMode;
    private final Supplier<Map<String, Integer>> transferFailureHeat;

    public NetworkTopologyWidget(int x, int y, int width, int height, NetworkDefinition network, List<NetworkMemberObservation> observations, Supplier<NetworkRuntimeSnapshot> runtimeSnapshot, Consumer<NetworkMember> onMemberSelected) {
        this(x, y, width, height, network, observations, runtimeSnapshot, () -> NetworkTopologyHeat.STATUS, () -> Map.of(), onMemberSelected);
    }

    public NetworkTopologyWidget(int x, int y, int width, int height, NetworkDefinition network, List<NetworkMemberObservation> observations, Supplier<NetworkRuntimeSnapshot> runtimeSnapshot, Supplier<NetworkTopologyHeat> heatMode, Supplier<Map<String, Integer>> transferFailureHeat, Consumer<NetworkMember> onMemberSelected) {
        super(x, y, width, height, "");
        this.network = network;
        if (observations != null) {
            observations.forEach(observation -> this.observations.put(observation.nodeId(), observation));
        }
        this.runtimeSnapshot = runtimeSnapshot;
        this.heatMode = heatMode;
        this.transferFailureHeat = transferFailureHeat;
        this.onMemberSelected = onMemberSelected;
        this.entranceAnimationEnabled = false;
        this.animateLayout = false;
    }

    public static int preferredHeight(int width, int memberCount) {
        int columns = columns(width);
        int backendCount = Math.max(0, memberCount - 1);
        int rows = Math.max(1, (int) Math.ceil(backendCount / (double) columns));
        return 112 + rows * (NODE_HEIGHT + ROW_GAP) + 12;
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        nodes.clear();
        NetworkMember proxy = network.proxyMember();
        if (proxy == null) {
            ctx.drawText("Proxy Unavailable", getX() + 12, getY() + 12, ThemeManager.getAccent("danger").getAccentColor(), Config.shadow);
            return;
        }
        int proxyWidth = Math.min(176, Math.max(116, getWidth() / 4));
        int proxyX = getX() + (getWidth() - proxyWidth) / 2;
        int proxyY = getY() + 14;
        NodeBounds proxyBounds = new NodeBounds(proxyX, proxyY, proxyWidth, NODE_HEIGHT, proxy);
        nodes.add(proxyBounds);
        List<NetworkMember> backends = network.members().stream().filter(member -> !member.isProxy()).toList();
        int columns = columns(getWidth());
        int contentWidth = getWidth() - 24;
        int nodeWidth = Math.max(104, Math.min(156, (contentWidth - (columns - 1) * NODE_GAP) / columns));
        int gridWidth = columns * nodeWidth + (columns - 1) * NODE_GAP;
        int gridX = getX() + (getWidth() - gridWidth) / 2;
        int gridY = proxyY + NODE_HEIGHT + 64;
        for (int index = 0; index < backends.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            nodes.add(new NodeBounds(gridX + column * (nodeWidth + NODE_GAP), gridY + row * (NODE_HEIGHT + ROW_GAP), nodeWidth, NODE_HEIGHT, backends.get(index)));
        }
        drawConnections(ctx, proxyBounds);
        drawRoutingLayer(ctx, proxyBounds, backends, gridY);
        for (NodeBounds node : nodes) {
            drawNode(ctx, node, mouseX, mouseY);
        }
    }

    private void drawConnections(IDrawContext ctx, NodeBounds proxy) {
        int proxyCenter = proxy.x() + proxy.width() / 2;
        int trunkTop = proxy.y() + proxy.height();
        int busY = trunkTop + 34;
        int neutral = ThemeManager.getColor(ThemeColor.innerBorder);
        ctx.fill(proxyCenter, trunkTop, proxyCenter + 2, busY + 1, neutral);
        List<NodeBounds> backends = nodes.stream().filter(node -> !node.member().isProxy()).toList();
        if (backends.isEmpty()) {
            return;
        }
        int left = backends.stream().mapToInt(node -> node.x() + node.width() / 2).min().orElse(proxyCenter);
        int right = backends.stream().mapToInt(node -> node.x() + node.width() / 2).max().orElse(proxyCenter);
        ctx.fill(left, busY, right + 2, busY + 2, neutral);
        for (NodeBounds node : backends) {
            int center = node.x() + node.width() / 2;
            int color = stateColor(node.member());
            ctx.fill(center, busY, center + 2, node.y(), color);
        }
    }

    private void drawRoutingLayer(IDrawContext ctx, NodeBounds proxy, List<NetworkMember> backends, int gridY) {
        String summary;
        if (network.routingGroups().isEmpty()) {
            summary = "Direct Routes";
        } else {
            summary = network.routingGroups().stream().map(RoutingGroup::name).limit(3).reduce((left, right) -> left + " • " + right).orElse("Routes");
            if (network.routingGroups().size() > 3) {
                summary += " • +" + (network.routingGroups().size() - 3);
            }
        }
        String fitted = TextRenderer.tr.trimToWidth(summary, Math.max(80, getWidth() - 32));
        int x = getX() + (getWidth() - TextRenderer.tr.getWidth(fitted)) / 2;
        int y = proxy.y() + proxy.height() + 17;
        ctx.drawText(fitted, x, y, ThemeManager.getColor(ThemeColor.textDark), Config.shadow);
        if (backends.isEmpty()) {
            String empty = "No Backends";
            ctx.drawText(empty, getX() + (getWidth() - TextRenderer.tr.getWidth(empty)) / 2, gridY, ThemeManager.getAccent("warning").getAccentColor(), Config.shadow);
        }
    }

    private void drawNode(IDrawContext ctx, NodeBounds node, int mouseX, int mouseY) {
        boolean hovered = node.contains(mouseX, mouseY);
        int background = ThemeManager.getColor(hovered ? ThemeColor.elementHoverBackground : ThemeColor.elementBackground);
        int border = hovered ? stateColor(node.member()) : ThemeManager.getColor(ThemeColor.elementBorder);
        ctx.fillRoundedRectWithBorders(node.x(), node.y(), node.width(), node.height(), 4, background, border, border);
        int stateColor = stateColor(node.member());
        ctx.fill(node.x() + 8, node.y() + 9, node.x() + 12, node.y() + 13, stateColor);
        String title = TextRenderer.tr.trimToWidth(node.member().isProxy() ? network.name() : node.member().routeName(), Math.max(40, node.width() - 28));
        ctx.drawText(title, node.x() + 17, node.y() + 6, ThemeManager.getColor(ThemeColor.textHover), Config.shadow);
        String group = groupName(node.member());
        String role = node.member().isProxy() ? "Velocity Proxy" : (node.member().isManaged() ? "" : "External • ") + titleCase(node.member().role().name()) + (group.isBlank() ? "" : " • " + group);
        ctx.drawText(TextRenderer.tr.trimToWidth(role, Math.max(40, node.width() - 16)), node.x() + 8, node.y() + 21, ThemeManager.getColor(ThemeColor.textDark), Config.shadow);
        String detail = heatDetail(node.member());
        ctx.drawText(TextRenderer.tr.trimToWidth(detail, Math.max(40, node.width() - 16)), node.x() + 8, node.y() + 34, ThemeManager.getColor(ThemeColor.textDark), Config.shadow);
    }

    private int stateColor(NetworkMember member) {
        NetworkTopologyHeat mode = activeHeat();
        if (mode != NetworkTopologyHeat.STATUS) {
            return heatColor(heatRatio(member, mode));
        }
        return statusColor(member);
    }

    private int statusColor(NetworkMember member) {
        NetworkNodeStatus liveStatus = livePresence(member).map(NetworkNodePresence::status).orElse(null);
        if (liveStatus != null) {
            return switch (liveStatus) {
                case ONLINE -> ThemeManager.getAccent("nice").getAccentColor();
                case DRAINING, MAINTENANCE -> ThemeManager.getAccent("warning").getAccentColor();
                case OFFLINE, REVOKED -> ThemeManager.getAccent("danger").getAccentColor();
            };
        }
        NetworkMemberObservation observation = observations.get(member.nodeId());
        if (observation == null) {
            return ThemeManager.getAccent("warning").getAccentColor();
        }
        return switch (observation.state()) {
            case HEALTHY -> ThemeManager.getAccent("nice").getAccentColor();
            case UNKNOWN, DRIFTED -> ThemeManager.getAccent("warning").getAccentColor();
            case DEGRADED, INSECURE, UNREACHABLE -> ThemeManager.getAccent("danger").getAccentColor();
        };
    }

    private double heatRatio(NetworkMember member, NetworkTopologyHeat mode) {
        NetworkNodePresence presence = livePresence(member).orElse(null);
        return switch (mode) {
            case STATUS -> 0;
            case PLAYERS -> {
                if (presence == null) {
                    yield 0;
                }
                int maximum = presence.capacity() > 0 ? presence.capacity() : runtimeSnapshot.get().nodes().values().stream().mapToInt(NetworkNodePresence::players).max().orElse(1);
                yield maximum < 1 ? 0 : Math.clamp((double) presence.players() / maximum, 0, 1);
            }
            case MSPT -> presence == null || presence.mspt() < 0 ? 0 : Math.clamp((presence.mspt() - 20) / 60, 0, 1);
            case MEMORY -> presence == null || presence.heapMaximum() < 1 ? 0 : Math.clamp((double) presence.heapUsed() / presence.heapMaximum(), 0, 1);
            case TRANSFER_FAILURES -> Math.clamp(transferFailures(member) / 5.0, 0, 1);
        };
    }

    private int heatColor(double ratio) {
        int nice = ThemeManager.getAccent("nice").getAccentColor();
        int warning = ThemeManager.getAccent("warning").getAccentColor();
        int danger = ThemeManager.getAccent("danger").getAccentColor();
        return ratio <= 0.5 ? blend(nice, warning, ratio * 2) : blend(warning, danger, (ratio - 0.5) * 2);
    }

    private int blend(int first, int second, double ratio) {
        double amount = Math.clamp(ratio, 0, 1);
        int alpha = (int) Math.round((first >>> 24) + ((second >>> 24) - (first >>> 24)) * amount);
        int red = (int) Math.round((first >> 16 & 255) + ((second >> 16 & 255) - (first >> 16 & 255)) * amount);
        int green = (int) Math.round((first >> 8 & 255) + ((second >> 8 & 255) - (first >> 8 & 255)) * amount);
        int blue = (int) Math.round((first & 255) + ((second & 255) - (first & 255)) * amount);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private Optional<NetworkNodePresence> livePresence(NetworkMember member) {
        NetworkRuntimeSnapshot snapshot = runtimeSnapshot == null ? null : runtimeSnapshot.get();
        return snapshot == null || !snapshot.connected() ? Optional.empty() : snapshot.node(member.nodeId());
    }

    private String presenceDetail(NetworkNodePresence presence) {
        if (presence.status() != NetworkNodeStatus.ONLINE) {
            return titleCase(presence.status().name());
        }
        if (presence.capacity() < 1 && presence.tps() < 0) {
            return "Online";
        }
        String players = presence.capacity() > 0 ? presence.players() + "/" + presence.capacity() + " Players" : presence.players() + " Players";
        return presence.tps() < 0 ? players : players + " • " + String.format(Locale.ROOT, "%.1f TPS", presence.tps());
    }

    private String heatDetail(NetworkMember member) {
        NetworkTopologyHeat mode = activeHeat();
        NetworkNodePresence presence = livePresence(member).orElse(null);
        if (mode == NetworkTopologyHeat.TRANSFER_FAILURES) {
            int failures = transferFailures(member);
            return failures + (failures == 1 ? " Transfer Failure" : " Transfer Failures") + " • 24 Hours";
        }
        if (presence == null) {
            return member.address() + ":" + member.port();
        }
        return switch (mode) {
            case STATUS -> presenceDetail(presence);
            case PLAYERS -> presence.capacity() > 0 ? presence.players() + "/" + presence.capacity() + " Players" : presence.players() + " Players";
            case MSPT -> presence.mspt() < 0 ? "MSPT Unavailable" : String.format(Locale.ROOT, "%.1f MSPT", presence.mspt());
            case MEMORY -> presence.heapMaximum() < 1 ? "Memory Unavailable" : Math.round((double) presence.heapUsed() / presence.heapMaximum() * 100) + "% Heap Used";
            case TRANSFER_FAILURES -> throw new IllegalStateException("Transfer Failure Heat Was Already Resolved");
        };
    }

    private int transferFailures(NetworkMember member) {
        Map<String, Integer> heat = transferFailureHeat == null ? Map.of() : transferFailureHeat.get();
        return heat == null ? 0 : heat.getOrDefault(member.nodeId(), 0);
    }

    private NetworkTopologyHeat activeHeat() {
        NetworkTopologyHeat selected = heatMode == null ? null : heatMode.get();
        return selected == null ? NetworkTopologyHeat.STATUS : selected;
    }

    private String groupName(NetworkMember member) {
        return network.routingGroups().stream().filter(group -> group.nodeIds().contains(member.nodeId())).map(RoutingGroup::name).findFirst().orElse("");
    }

    private String titleCase(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (char character : normalized.toCharArray()) {
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return result.toString();
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        if (!isVisible() || !isActive() || event.button() != ReMouseButton.LEFT) {
            return super.mouseClicked(event);
        }
        for (NodeBounds node : nodes) {
            if (node.contains(event.x(), event.y())) {
                if (onMemberSelected != null) {
                    onMemberSelected.accept(node.member());
                }
                return true;
            }
        }
        return super.mouseClicked(event);
    }

    private static int columns(int width) {
        return Math.clamp(Math.max(1, (width - 24) / 152), 1, 6);
    }

    private record NodeBounds(int x, int y, int width, int height, NetworkMember member) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}
