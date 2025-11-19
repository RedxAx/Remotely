package redxax.oxy.remotely.ui.widgets.msmp;

import redxax.oxy.remotely.data.integrations.luckperms.LuckPermsService;
import redxax.oxy.remotely.data.managed.ManagedPlayer;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.data.player.standard.StandardPlayerDataProvider;
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

    private final ManagedPlayer player;
    private final PlayerManagerController controller;
    private volatile BufferedImage face;
    private final Identifier opIcon = Identifier.icon("op.png");
    private final Identifier deopIcon = Identifier.icon("deop.png");
    private boolean faceRequested = false;

    private String cachedPrefix = "";
    private String cachedSuffix = "";
    private String cachedGroup = "";
    private boolean lpDataRequested = false;

    public PlayerEntryWidget(ManagedPlayer player, PlayerManagerController controller) {
        super(player.name, "", "", new CopyOnWriteArrayList<>(), null);
        this.player = player;
        this.controller = controller;
        this.ClickableWhenInactive = true;
        this.xOffset = 28;

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

    public ManagedPlayer getPlayer() {
        return player;
    }

    @Override
    public void tick() {
        super.tick();
        if (face == null && !faceRequested) {
            faceRequested = true;
            CompletableFuture.runAsync(() -> {
                Account tempAccount = new Account(player.name, player.uuid.toString(), null, 0);
                BufferedImage fetchedFace = tempAccount.getFace();
                if (fetchedFace != null) {
                    this.face = fetchedFace;
                }
            });
        }

        if (!lpDataRequested && controller.getDataProvider() instanceof StandardPlayerDataProvider sdp) {
            LuckPermsService lp = sdp.getLuckPermsService();
            if (lp.isEnabled()) {
                lpDataRequested = true;
                lp.getUserMetadata(player.uuid).thenAccept(meta -> {
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
        int iconSize = 26;
        if (face != null) {
            ctx.drawPixelArt(face, getX() + 2, getY() + (getHeight() - iconSize) / 2f, iconSize, iconSize);
        }

        StringBuilder displayName = new StringBuilder();
        if (!cachedPrefix.isEmpty()) displayName.append(cachedPrefix);
        displayName.append(player.name);
        if (!cachedSuffix.isEmpty()) displayName.append(cachedSuffix);

        name = displayName.toString().replace("&", "\u00a7");
        hiddenText = cachedGroup;

        if (player.isBanned || player.isIpBanned) {
            String reason = (player.banInfo != null) ? player.banInfo.reason : ((player.ipBanInfo != null) ? player.ipBanInfo.reason : "Unknown");
            String expires = (player.banInfo != null) ? player.banInfo.expires : ((player.ipBanInfo != null) ? player.ipBanInfo.expires : "Unknown");
            description = "Banned For " + reason + " | Expires: " + expires + " | Type: " + (player.isIpBanned ? "IP Ban" : "Account Ban");
            accentType = ThemeManager.getAccent("danger");
        } else if (player.isOnline) {
            description = "Online";
            if (player.isOp) {
                description += " | Operator (Level " + player.opLevel + ")";
                accentType = ThemeManager.getAccent("calm");
            }
            else accentType = ThemeManager.getDefaultAccent();
        } else {
            description = "Offline";
            if (player.isOp) description += " | Operator";
            description += " | Last seen: " + (player.lastSeen > 0 ? TimeUtils.timeSense(player.lastSeen) : "never");
            accentType = ThemeManager.getDefaultAccent();
            active = false;
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
            builder.addRow("", true, 20, input);
        }
        builder.addTitleButton(execute, "Execute", ThemeManager.getAccent("nice"));
        PopupWidget popup = builder.build();
        ScreenManager.getInstance().getCurrentScreen().addDrawableChild(popup);
        popup.show();
    }
}
