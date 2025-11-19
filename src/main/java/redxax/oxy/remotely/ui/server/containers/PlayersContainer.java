package redxax.oxy.remotely.ui.server.containers;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.data.managed.ManagedPlayer;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.ui.widgets.msmp.PlayerEntryWidget;
import redxax.oxy.remotely.ui.widgets.msmp.PlayerManagerController;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.*;

public class PlayersContainer extends Container {
    private final ReScreen host;
    private Instance instance;
    private final TerminalWidget terminalWidget;
    private PlayerManagerController controller;

    public PlayersContainer(ReScreen host, Instance instance, TerminalWidget terminalWidget, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.host = host;
        this.instance = instance;
        this.terminalWidget = terminalWidget;
        this.layout(new ManagedLayout()).columns(1).padding(2).enableSelecting(true).setRelativeScissor(- 1, - 1, - 1, - 3);
        controller = PlayerManagerController.getOrCreate(instance, RebaseApiFactory.get(instance));
        controller.setUiBindings(this);
    }

    public void setInstance(Instance newInstance) {
        this.instance = newInstance;
        controller = PlayerManagerController.getOrCreate(newInstance, RebaseApiFactory.get(newInstance));
        controller.setUiBindings(this);
    }

    public void rebuildPlayerWidgets() {
        if (controller != null) controller.rebuildPlayerWidgets();
    }

    public void fullRefresh() {
        if (controller != null) controller.fullRefresh();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (isMouseOver(mouseX, mouseY)) {
                java.util.List<AnimatedWidget> selected = getSelectedWidgets();

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
        java.util.List<AnimatedWidget> selected = getSelectedWidgets();
        if (selected.isEmpty()) return;
        java.util.List<ManagedPlayer> players = new java.util.ArrayList<>();
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

        java.util.List<PlayerAction> actions = controller.getPlayerActions();
        if (actions != null) {
            for (PlayerAction action : actions) {
                builder.addHeaderButton(action.icon, () -> showCustomActionMultiPopup(action, players), action.name);
            }
        }
        host.showContextMenu((int) mouseX, (int) mouseY, builder);
    }

    private void kickSelected(java.util.List<ManagedPlayer> players) {
        for (ManagedPlayer p : players) controller.kickPlayer(p, "Kicked by operator");
    }

    private void unbanSelected(java.util.List<ManagedPlayer> players) {
        for (ManagedPlayer p : players) controller.unbanPlayer(p);
    }

    private void toggleOpSelected(java.util.List<ManagedPlayer> players) {
        for (ManagedPlayer p : players) controller.toggleOp(p);
    }

    private java.util.List<String> findCustomVariables(String command) {
        java.util.List<String> variables = new java.util.ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$([a-zA-Z0-9_]+)");
        java.util.regex.Matcher matcher = pattern.matcher(command);
        while (matcher.find()) {
            String var = matcher.group(1);
            if (!"name".equalsIgnoreCase(var) && !"uuid".equalsIgnoreCase(var) && !variables.contains(var)) {
                variables.add(var);
            }
        }
        return new java.util.ArrayList<>(variables);
    }

    private void showCustomActionMultiPopup(PlayerAction action, java.util.List<ManagedPlayer> players) {
        java.util.List<String> variables = findCustomVariables(action.command);
        if (variables.isEmpty()) {
            for (ManagedPlayer p : players) controller.runCustomCommand(p, action.command);
            return;
        }
        PopupWidget.Builder builder = new PopupWidget.Builder("Execute: " + action.name)
            .size(300, 60 + variables.size() * 30).setAntiOutOfBound(true).setResizable(true);
        java.util.Map<String, TextInputWidget> inputs = new java.util.HashMap<>();
        Runnable execute = () -> {
            String template = action.command;
            for (java.util.Map.Entry<String, TextInputWidget> entry : inputs.entrySet()) {
                String value = entry.getValue().getText();
                template = template.replace("$" + entry.getKey(), value);
            }
            final String command = template;
            for (ManagedPlayer p : players) controller.runCustomCommand(p, command);
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

    private void showBanMultiPopup(java.util.List<ManagedPlayer> players) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Ban Selected")
            .size(320, 120).setAntiOutOfBound(true).setResizable(true);
        TextInputWidget reason = new TextInputWidget.Builder().placeholder("Reason").size(280, 18).build();
        ToggleWidget ipBan = new ToggleWidget.Builder().toggled(false).build();
        builder.addRow("Reason", false, 20, reason);
        builder.addRow("IP Ban?", false, 20, ipBan);
        builder.addTitleButton(() -> {
            String r = reason.getText().trim();
            boolean ip = ipBan.getValue();
            for (ManagedPlayer p : players) controller.banPlayer(p, r.isEmpty() ? "Banned by operator" : r, ip);
            builder.getWidget().setVisible(false);
        }, "Ban", ThemeManager.getAccent("danger"));
        PopupWidget popup = builder.build();
        host.addDrawableChild(popup);
        popup.show();
    }
}
