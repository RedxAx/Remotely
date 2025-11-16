package redxax.oxy.remotely.ui.widgets.msmp;

import redxax.oxy.remotely.data.managed.ManagedPlayer;
import redxax.oxy.remotely.data.managed.PlayerAction;
import restudio.rebase.account.Account;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.TimeUtils;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerEntryWidget extends MountableButtonWidget {

    private final ManagedPlayer player;
    private final PlayerManagerController controller;
    private BufferedImage face;
    private final Identifier opIcon = Identifier.icon("op.png");
    private final Identifier deopIcon = Identifier.icon("deop.png");

    public PlayerEntryWidget(ManagedPlayer player, PlayerManagerController controller) {
        super(player.name, "", "", new CopyOnWriteArrayList<>(), null);
        this.player = player;
        this.controller = controller;
        this.animateElevation = inClickableWhenInactive = false;
        this.xOffset = 28;

        Account tempAccount = new Account(player.name, player.uuid.toString(), null, 0);
        tempAccount.getFace();
        
        boolean serverRunning = controller.isServerRunning();

        SquareButtonWidget kickButton = new SquareButtonWidget.Builder()
                .imagePath("delete.png").size(18, 18).hint("Kick Player").accentType(ThemeManager.getAccent("danger"))
                .onClick(() -> controller.kickPlayer(player, "Kicked by operator")).build();
        kickButton.active = player.isOnline && serverRunning;

        String banHint = player.isBanned ? "Unban Player" : "Ban Player";
        SquareButtonWidget banButton = new SquareButtonWidget.Builder().imagePath(player.isBanned ? "heart.png" : "close.png").size(18, 18).hint(banHint)
                .accentType(player.isBanned ? ThemeManager.getAccent("nice") : ThemeManager.getAccent("danger")).onClick(() -> {
                    if (player.isBanned) {
                        controller.unbanPlayer(player);
                    } else {
                        new BanPlayerPopup(ScreenManager.getInstance().getCurrentScreen(), player, controller);
                    }
                }).build();
        banButton.active = serverRunning;


        String opHint = player.isOp ? "De-Op Player" : "Op Player";
        SquareButtonWidget opButton = new SquareButtonWidget.Builder()
                .identifier(player.isOp ? deopIcon : opIcon).size(18, 18).hint(opHint)
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

        buttons.add(banButton);
        buttons.add(kickButton);
        buttons.add(opButton);
        mountedWidgets.addAll(buttons);
    }

    @Override
    public void tick() {
        super.tick();
        if (face == null) {
            Account tempAccount = new Account(player.name, player.uuid.toString(), null, 0);
            BufferedImage fetchedFace = tempAccount.getFace();
            if (fetchedFace != null) {
                this.face = fetchedFace;
            }
        }
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        int iconSize = 26;
        if (face != null) {
            ctx.drawPixelArt(face, getX() + 2, getY() + (getHeight() - iconSize) / 2f, iconSize, iconSize);
        }

        name = player.name;

        if (player.isBanned || player.isIpBanned) {
            String reason = (player.banInfo != null) ? player.banInfo.reason : ((player.ipBanInfo != null) ? player.ipBanInfo.reason : "Unknown");
            String expires = (player.banInfo != null) ? player.banInfo.expires : ((player.ipBanInfo != null) ? player.ipBanInfo.expires : "Unknown");
            description = "Banned For " + reason + " | Expires: " + expires + " | Type: " + (player.isIpBanned ? "IP Ban" : "Account Ban");
            accentType = ThemeManager.getAccent("danger");
        } else if (player.isOnline) {
            description = "Online";
            if (player.isOp) {
                name += " | Operator (Level " + player.opLevel + ")";
                accentType = ThemeManager.getAccent("calm");
            }
            else accentType = ThemeManager.getDefaultAccent();
        } else {
            name += player.isOp ? " | Operator (Level " + player.opLevel + ")" : "";
            description = "Offline | Last seen: " + (player.lastSeen > 0 ? TimeUtils.timeSense(player.lastSeen) : "never");
            accentType = ThemeManager.getDefaultAccent();
        }
        titleColor = player.isOnline ? ThemeManager.getColor(ThemeColor.text) : ThemeManager.getColor(ThemeColor.textDark);

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

    private void showVariableInputPopup(ManagedPlayer player, PlayerAction action, List<String> variables) {
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
            builder.addRow("$" + var, true, 20, input);
        }

        builder.addTitleButton(execute, "Execute", ThemeManager.getAccent("nice"));

        PopupWidget popup = builder.build();
        ScreenManager.getInstance().getCurrentScreen().addDrawableChild(popup);
        popup.show();
    }
}