package redxax.oxy.remotely.ui.widgets.msmp;

import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.data.managed.ManagedPlayer;
import redxax.oxy.remotely.data.managed.PlayerAction;
import restudio.rebase.account.Account;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.TimeUtils;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
        this.animateElevation = false;
        this.xOffset = 30;

        Account tempAccount = new Account(player.name, player.uuid.toString(), null, 0);
        tempAccount.getFace();

        boolean msmpConnected = controller.isMsmpConnected();
        boolean serverRunning = controller.isServerRunning();

        SquareButtonWidget kickButton = new SquareButtonWidget.Builder()
                .imagePath("delete.png").size(18, 18).hint("Kick Player").accentType(ThemeManager.getAccent("danger"))
                .onClick(() -> controller.kickPlayer(player, "Kicked by operator").thenRun(controller::fullRefresh).exceptionally(e -> {
                    new Notification("Error", "Failed to kick player: " + e.getMessage(), Notification.Type.ERROR);
                    return null;
                })).build();
        kickButton.active = player.isOnline && msmpConnected;

        String banHint = player.isBanned ? "Unban Player" : "Ban Player";
        SquareButtonWidget banButton = new SquareButtonWidget.Builder().imagePath(player.isBanned ? "heart.png" : "close.png").size(18, 18).hint(banHint)
                .accentType(player.isBanned ? ThemeManager.getAccent("nice") : ThemeManager.getAccent("danger")).onClick(() -> {
                    if (player.isBanned) {
                        controller.unbanPlayer(player).thenRun(controller::fullRefresh).exceptionally(e -> {
                            new Notification("Error", "Failed to unban player: " + e.getMessage(), Notification.Type.ERROR);
                            return null;
                        });
                    } else {
                        new BanPlayerPopup(ScreenManager.getInstance().getCurrentScreen(), player, controller);
                    }
                }).build();
        banButton.active = msmpConnected;


        String opHint = player.isOp ? "De-Op Player" : "Op Player";
        SquareButtonWidget opButton = new SquareButtonWidget.Builder()
                .identifier(player.isOp ? deopIcon : opIcon).size(18, 18).hint(opHint)
                .accentType(ThemeManager.getAccent("calm"))
                .onClick(() -> controller.toggleOp(player)).build();
        opButton.active = msmpConnected;

        List<AnimatedWidget> buttons = new CopyOnWriteArrayList<>();
        if (Config.customPlayerActions != null) {
            for (PlayerAction action : Config.customPlayerActions) {
                SquareButtonWidget actionButton = new SquareButtonWidget.Builder()
                        .identifier(Identifier.icon(action.icon))
                        .size(18, 18)
                        .hint(action.name)
                        .accentType(ThemeManager.getAccent("calm"))
                        .onClick(() -> controller.runCustomCommand(player, action.command).exceptionally(e -> {
                            new Notification("Error", "Command failed: " + e.getMessage(), Notification.Type.ERROR);
                            return null;
                        }))
                        .build();
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
            description = "§4Banned §rFor " + player.banInfo.reason + " | Expires: " + player.banInfo.expires + " | Type: " + (player.isIpBanned ? "IP Ban" : "Account Ban");
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

        super.drawContent(ctx, mouseX, mouseY);
    }
}