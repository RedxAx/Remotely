package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.data.integrations.luckperms.LuckPermsService;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import restudio.rebase.account.Account;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.TimeUtils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerEntryWidget extends MountableButtonWidget {

    private UnifiedPlayer player;
    private final PlayerManagerController controller;
    private final Identifier opIcon = Identifier.icon("op.png");
    private final Identifier deopIcon = Identifier.icon("deop.png");
    private boolean faceRequested = false;

    private String cachedPrefix = "";
    private String cachedSuffix = "";
    private String cachedGroup = "";
    private boolean lpDataRequested = false;

    private SquareButtonWidget kickButton;
    private SquareButtonWidget banButton;
    private SquareButtonWidget opButton;

    public PlayerEntryWidget(UnifiedPlayer player, PlayerManagerController controller) {
        super(player.getName(), "", "", new CopyOnWriteArrayList<>(), null);
        this.player = player;
        this.controller = controller;
        this.ClickableWhenInactive = true;

        setupButtons();
    }

    private void setupButtons() {
        mountedWidgets.clear();
        boolean serverRunning = controller.isServerRunning();

        kickButton = new SquareButtonWidget.Builder()
            .imagePath("delete.png").size(18, 18).hint("Kick Player").accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> controller.kickPlayer(player, "Kicked by operator")).build();

        updateButtonsState(serverRunning);

        List<AnimatedWidget> buttons = new CopyOnWriteArrayList<>();
        List<PlayerAction> actions = controller.getPlayerActions();
        if (actions != null) {
            for (PlayerAction action : actions) {
                SquareButtonWidget actionButton = new SquareButtonWidget.Builder().identifier(Identifier.icon(action.icon)).size(18, 18).hint(action.name).onClick(() -> {
                    List<String> variables = findCustomVariables(action.command);
                    if (variables.isEmpty()) {
                        controller.runCustomCommand(player, action.command);
                    } else {
                        showVariableInputPopup(player, action, variables);
                    }
                }).build();
                actionButton.active = serverRunning;
                buttons.add(actionButton);
            }
        }

        if (banButton != null) buttons.add(banButton);
        if (kickButton != null) buttons.add(kickButton);
        if (opButton != null) buttons.add(opButton);
        mountedWidgets.addAll(buttons);
    }

    public void update(UnifiedPlayer newPlayerState) {
        this.player = newPlayerState;
        this.name = player.getName() != null ? player.getName() : "Unknown";
        boolean serverRunning = controller.isServerRunning();
        updateButtonsState(serverRunning);
    }

    private void updateButtonsState(boolean serverRunning) {
        kickButton.active = player.isOnline() && serverRunning;

        boolean isBanned = player.getBan().getValue() != null;
        String banHint = isBanned ? "Unban Player" : "Ban Player";
        banButton = new SquareButtonWidget.Builder().imagePath(isBanned ? "heart.png" : "close.png").size(18, 18).hint(banHint)
            .accentType(isBanned ? ThemeManager.getAccent("nice") : ThemeManager.getAccent("danger")).onClick(() -> {
                if (player.getBan().getValue() != null) {
                    controller.unbanPlayer(player);
                } else {
                    new BanPlayerPopup(ScreenManager.getInstance().getCurrentScreen(), player, controller);
                }
            }).build();
        banButton.active = serverRunning;

        boolean isOp = player.isOp();
        String opHint = isOp ? "De-Op Player" : "Op Player";
        opButton = new SquareButtonWidget.Builder()
            .identifier(isOp ? deopIcon : opIcon).size(18, 18).hint(opHint)
            .accentType(ThemeManager.getAccent("calm"))
            .onClick(() -> controller.toggleOp(player)).build();
        opButton.active = serverRunning;

        List<AnimatedWidget> buttons = new CopyOnWriteArrayList<>();
        List<PlayerAction> actions = controller.getPlayerActions();
        if (actions != null) {
             for (PlayerAction action : actions) {
                SquareButtonWidget actionButton = new SquareButtonWidget.Builder().identifier(Identifier.icon(action.icon)).size(18, 18).hint(action.name).onClick(() -> {
                    List<String> variables = findCustomVariables(action.command);
                    if (variables.isEmpty()) {
                        controller.runCustomCommand(player, action.command);
                    } else {
                        showVariableInputPopup(player, action, variables);
                    }
                }).build();
                actionButton.active = serverRunning;
                buttons.add(actionButton);
            }
        }
        if (banButton != null) buttons.add(banButton);
        if (kickButton != null) buttons.add(kickButton);
        if (opButton != null) buttons.add(opButton);

        mountedWidgets.clear();
        mountedWidgets.addAll(buttons);
    }

    public UnifiedPlayer getPlayer() {
        return player;
    }

    @Override
    public void tick() {
        super.tick();
        if (icon == null && !faceRequested) {
            faceRequested = true;
            CompletableFuture.runAsync(() -> {
                Account tempAccount = new Account(player.getName(), player.getUuid().toString(), null, 0);
                BufferedImage fetchedFace = tempAccount.getFace();
                if (fetchedFace != null) {
                    this.icon = fetchedFace;
                    this.iconSize = 26;
                }
            });
        }

        if (!lpDataRequested) {
            LuckPermsService lp = controller.getLuckPermsService();
            if (lp != null && lp.isEnabled()) {
                lpDataRequested = true;
                lp.getUserMetadata(player.getUuid()).thenAccept(meta -> {
                    if (meta != null) {
                        this.cachedPrefix = meta.prefix != null ? meta.prefix : "";
                        this.cachedSuffix = meta.suffix != null ? meta.suffix : "";
                        this.cachedGroup = meta.primaryGroup != null ? meta.primaryGroup : "";
                    }
                });
            }
        }
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        StringBuilder displayName = new StringBuilder();
        if (!cachedPrefix.isEmpty()) displayName.append(cachedPrefix);
        displayName.append(player.getName() != null ? player.getName() : "Unknown");
        if (!cachedSuffix.isEmpty()) displayName.append(cachedSuffix);

        name = displayName.toString().replace("&", "§");
        hiddenText = cachedGroup;

        boolean isBanned = player.getBan().getValue() != null;
        if (isBanned) {
            String reason = player.getBan().getValue().reason();
            String expires = player.getBan().getValue().expires();
            description = "Banned For " + reason + " | Expires: " + expires;
            accentType = ThemeManager.getAccent("danger");
        } else if (player.isOnline()) {
            description = "Online";
            if (player.isOp()) {
                description += " | Operator";
                accentType = ThemeManager.getAccent("calm");
            }
            else accentType = ThemeManager.getDefaultAccent();
            active = true;
        } else {
            description = "Offline";
            if (player.isOp()) description += " | Operator";
            long lastSeen = player.getLastSeenValue();
            if (lastSeen > 0) {
                description += " | Last seen: " + TimeUtils.timeSense(lastSeen);
            }
            accentType = ThemeManager.getDefaultAccent();
            active = false;
        }
        titleColor = player.isOnline() ? ThemeManager.getColor(ThemeColor.text) : ThemeManager.getColor(ThemeColor.textDark);

        super.drawContent(ctx, mouseX, mouseY);
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

    private void showVariableInputPopup(UnifiedPlayer player, PlayerAction action, List<String> variables) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Execute: " + action.name)
            .size(300, 60 + variables.size() * 30).setAntiOutOfBound(true).setResizable(true);

        Map<String, TextInputWidget> inputs = new HashMap<>();
        Runnable execute = () -> {
            String command = action.command;
            for (Map.Entry<String, TextInputWidget> entry : inputs.entrySet()) {
                String value = entry.getValue().getText();
                command = command.replace("$" + entry.getKey(), value);
            }
            controller.runCustomCommand(player, command);
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
        ScreenManager.getInstance().getCurrentScreen().addDrawableChild(popup);
        popup.show();
    }
}
