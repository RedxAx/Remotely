package redxax.oxy.remotely.ui.server.containers;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.ui.widgets.management.PlayerEntryWidget;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayersContainer extends Container {
    private ReScreen host;
    private final TerminalWidget terminalWidget;
    private PlayerManagerController controller;
    private IconMessage emptyMessage;
    private boolean loading = true;
    private String searchQuery = "";
    private List<UnifiedPlayer> lastSnapshot = new ArrayList<>();

    public PlayersContainer(ReScreen host, Instance instance, TerminalWidget terminalWidget, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.host = host;
        this.terminalWidget = terminalWidget;
        this.layout(new ManagedLayout()).columns(1).padding(2).enableSelecting(true).setRelativeScissor(- 1, - 1, - 1, - 3);
        controller = PlayerManagerController.getOrCreate(instance);
        controller.setUiBindings(this, terminalWidget);
        emptyMessage = new IconMessage(0, 0, 64, 64, "Loading Players", "remotely.png");
    }

    public void setInstance(Instance newInstance) {
        controller = PlayerManagerController.getOrCreate(newInstance);
        controller.setUiBindings(this, terminalWidget);
    }

    public void setHost(ReScreen host) {
        this.host = host;
    }

    public void fullRefresh() {
        loading = true;
        clearWidgets();
        updateWidgetPositions();
        if (controller != null) controller.fullRefresh();
    }

    public void syncUi(List<UnifiedPlayer> snapshot) {
        if (snapshot == null) return;
        loading = false;
        lastSnapshot = new ArrayList<>(snapshot);
        List<UnifiedPlayer> processingList = new ArrayList<>(snapshot);
        if (searchQuery != null && !searchQuery.isBlank()) {
            processingList.removeIf(p -> !matchesSearch(p));
        }
        List<AnimatedWidget> toRemove = new ArrayList<>();

        for (AnimatedWidget w : getWidgets()) {
            if (w instanceof PlayerEntryWidget pew) {
                UnifiedPlayer existing = pew.getPlayer();
                UnifiedPlayer match = processingList.stream()
                        .filter(p -> p.getUuid().equals(existing.getUuid()))
                        .findFirst().orElse(null);

                if (match != null) {
                    pew.update(match);
                    processingList.remove(match);
                } else {
                    toRemove.add(pew);
                }
            } else {
                if (!snapshot.isEmpty()) toRemove.add(w);
            }
        }

        for (AnimatedWidget w : toRemove) {
            this.removeWidget(w);
        }

        for (UnifiedPlayer p : processingList) {
            this.addWidget(new PlayerEntryWidget(p, controller));
        }

        this.sortWidgets((w1, w2) -> {
            if (w1 instanceof PlayerEntryWidget p1 && w2 instanceof PlayerEntryWidget p2) {
                UnifiedPlayer u1 = p1.getPlayer();
                UnifiedPlayer u2 = p2.getPlayer();
                if (u1.isOnline() != u2.isOnline()) return u1.isOnline() ? -1 : 1;
                String n1 = u1.getName() != null ? u1.getName() : "";
                String n2 = u2.getName() != null ? u2.getName() : "";
                return n1.compareToIgnoreCase(n2);
            }
            return 0;
        });

        this.updateWidgetPositions();
    }

    public void search(String query) {
        this.searchQuery = query == null ? "" : query.trim();
        syncUi(lastSnapshot);
    }

    private boolean matchesSearch(UnifiedPlayer player) {
        if (searchQuery == null || searchQuery.isBlank()) return true;
        String name = player.getName() != null ? player.getName() : "";
        String uuid = player.getUuid() != null ? player.getUuid().toString() : "";
        return restudio.rescreen.util.SearchUtils.isFuzzyMatch(name, searchQuery) || restudio.rescreen.util.SearchUtils.isFuzzyMatch(uuid, searchQuery);
    }

    @Override
    protected void drawContent(restudio.rescreen.platform.IDrawContext ctx, int mouseX, int mouseY) {
        if (emptyMessage != null && getWidgets().isEmpty()) {
            emptyMessage.setMessage(loading ? "Loading Players" : "No Players Found");
            emptyMessage.setIcon(loading ? "remotely.png" : "emptyFolder.png");
            emptyMessage.setPosition(getX() + (getWidth() - emptyMessage.getWidth()) / 2, getY() + (getHeight() - emptyMessage.getHeight()) / 2);
            emptyMessage.render(ctx, mouseX, mouseY, 0f);
        }
        super.drawContent(ctx, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (isMouseOver(mouseX, mouseY)) {
                List<AnimatedWidget> selected = getSelectedWidgets();

                boolean mouseOverSelected = false;
                for (AnimatedWidget widget : selected) {
                    if (widget.isMouseOver(mouseX, mouseY)) {
                        mouseOverSelected = true;
                        break;
                    }
                }

                if (mouseOverSelected) {
                    showPlayersContextMenu(mouseX, mouseY);
                    return true;
                } else {
                    showGeneralContextMenu(mouseX, mouseY);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void showGeneralContextMenu(double mouseX, double mouseY) {
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(host)
            .addHeaderButton("reload.png", this::fullRefresh, "Refresh List")
            .addItem("LuckPerms Dashboard", controller::openLuckPermsDashboard, "Open full editor")
            .addItem("LuckPerms Settings", controller::openLuckPermsSettings, "Configure integration");
        host.showContextMenu((int) mouseX, (int) mouseY, builder);
    }

    private void showPlayersContextMenu(double mouseX, double mouseY) {
        List<AnimatedWidget> selected = getSelectedWidgets();
        if (selected.isEmpty()) return;
        List<UnifiedPlayer> players = new ArrayList<>();
        for (AnimatedWidget w : selected) {
            if (w instanceof PlayerEntryWidget pew) {
                players.add(pew.getPlayer());
            }
        }
        if (players.isEmpty()) return;
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(host)
            .addHeaderButton("delete.png", () -> kickSelected(players), "Kick Selected", ThemeManager.getAccent("danger"))
            .addHeaderButton("close.png", () -> showBanMultiPopup(players), "Ban Selected")
            .addHeaderButton("heart.png", () -> unbanSelected(players), "Unban Selected")
            .addHeaderButton("op.png", () -> toggleOpSelected(players), "Toggle Op Selected");

        builder.addItem("LuckPerms Dashboard", controller::openLuckPermsDashboard, "Open full editor");

        List<PlayerAction> actions = controller.getPlayerActions();
        if (actions != null) {
            for (PlayerAction action : actions) {
                builder.addHeaderButton(action.icon, () -> showCustomActionMultiPopup(action, players), action.name);
            }
        }
        host.showContextMenu((int) mouseX, (int) mouseY, builder);
    }

    private void kickSelected(List<UnifiedPlayer> players) {
        for (UnifiedPlayer p : players) controller.kickPlayer(p, "Kicked by operator");
    }

    private void unbanSelected(List<UnifiedPlayer> players) {
        for (UnifiedPlayer p : players) controller.unbanPlayer(p);
    }

    private void toggleOpSelected(List<UnifiedPlayer> players) {
        for (UnifiedPlayer p : players) controller.toggleOp(p);
    }

    private List<String> findCustomVariables(String command) {
        List<String> variables = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\$([a-zA-Z0-9_]+)");
        Matcher matcher = pattern.matcher(command);
        while (matcher.find()) {
            String var = matcher.group(1);
            if (!"name".equalsIgnoreCase(var) && !"uuid".equalsIgnoreCase(var) && !variables.contains(var)) {
                variables.add(var);
            }
        }
        return new ArrayList<>(variables);
    }

    private void showCustomActionMultiPopup(PlayerAction action, List<UnifiedPlayer> players) {
        List<String> variables = findCustomVariables(action.command);
        if (variables.isEmpty()) {
            for (UnifiedPlayer p : players) controller.runCustomCommand(p, action.command);
            return;
        }
        PopupWidget.Builder builder = new PopupWidget.Builder("Execute: " + action.name)
            .size(300, 60 + variables.size() * 30).setAntiOutOfBound(true).setResizable(true);
        Map<String, TextInputWidget> inputs = new HashMap<>();
        Runnable execute = () -> {
            String template = action.command;
            for (Map.Entry<String, TextInputWidget> entry : inputs.entrySet()) {
                String value = entry.getValue().getText();
                template = template.replace("$" + entry.getKey(), value);
            }
            final String command = template;
            for (UnifiedPlayer p : players) controller.runCustomCommand(p, command);
            builder.getWidget().setVisible(false);
        };
        for (String var : variables) {
            TextInputWidget input = new TextInputWidget.Builder().placeholder("Enter value for $" + var).build();
            inputs.put(var, input);
            input.onEnter = () -> {
                int index = variables.indexOf(var);
                if (index >= 0 && index < variables.size() - 1) {
                    TextInputWidget nextWidget = inputs.get(variables.get(index + 1));
                    ((PopupWidget) builder.getWidget()).setFocusedWidget(nextWidget);
                } else {
                    execute.run();
                }
            };
            builder.addRow("", true, 20, input);
        }
        builder.addTitleButton(execute, "Execute", ThemeManager.getAccent("nice"));
        PopupWidget popup = builder.build();
        host.addDrawableChild(popup);
        popup.show();
    }

    private void showBanMultiPopup(List<UnifiedPlayer> players) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Ban Selected")
            .size(320, 120).setAntiOutOfBound(true).setResizable(true);
        TextInputWidget reason = new TextInputWidget.Builder().placeholder("Reason").size(280, 18).build();
        ToggleWidget ipBan = new ToggleWidget.Builder().toggled(false).build();
        builder.addRow("Reason", false, 20, reason);
        builder.addRow("IP Ban?", false, 20, ipBan);
        builder.addTitleButton(() -> {
            String r = reason.getText().trim();
            boolean ip = ipBan.getValue();
            for (UnifiedPlayer p : players) controller.banPlayer(p, r.isEmpty() ? "Banned by operator" : r, ip);
            builder.getWidget().setVisible(false);
        }, "Ban", ThemeManager.getAccent("danger"));
        PopupWidget popup = builder.build();
        host.addDrawableChild(popup);
        popup.show();
    }
}
