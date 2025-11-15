package redxax.oxy.remotely.ui.widgets.msmp;

import restudio.rebase.msmp.dto.Player;
import restudio.rebase.account.Account;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.util.Identifier;

import java.awt.image.BufferedImage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class PlayerEntryWidget extends MountableButtonWidget {

    private final Player player;
    private BufferedImage face;
    Identifier opIcon = Identifier.icon("op.png");
    Identifier deopIcon = Identifier.icon("deop.png");

    public PlayerEntryWidget(Player player, Consumer<Player> onKick, Consumer<Player> onBan, Consumer<Player> onToggleOp) {
        super(player.name, "", (player.isOperator ? "Operator" : ""), new CopyOnWriteArrayList<>(), null);
        this.player = player;
        this.animateElevation = false;
        this.xOffset = 30;

        Account tempAccount = new Account(player.name, player.uuid.toString(), null, 0);
        tempAccount.getFace();

        SquareButtonWidget kickButton = new SquareButtonWidget.Builder()
                .imagePath("delete.png").size(18, 18).hint("Kick Player")
                .accentType(ThemeManager.getAccent("danger"))
                .onClick(() -> onKick.accept(player)).build();

        SquareButtonWidget banButton = new SquareButtonWidget.Builder()
                .imagePath("close.png").size(18, 18).hint("Ban Player")
                .accentType(ThemeManager.getAccent("danger"))
                .onClick(() -> onBan.accept(player)).build();

        String opHint = player.isOperator ? "De-Op Player" : "Op Player";
        SquareButtonWidget opButton = new SquareButtonWidget.Builder()
                .identifier(player.isOperator ? deopIcon : opIcon).size(18, 18).hint(opHint)
                .accentType(ThemeManager.getAccent("calm"))
                .onClick(() -> onToggleOp.accept(player)).build();
        mountedWidgets.add(banButton);
        mountedWidgets.add(kickButton);
        mountedWidgets.add(opButton);
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
        super.drawContent(ctx, mouseX, mouseY);
        int iconSize = 26;
        if (face != null) {
            ctx.drawPixelArt(face, getX() + 2, getY() + (getHeight() - iconSize) / 2f, iconSize, iconSize);
        }
        name = player.name;
        description = (player.isOperator ? "Operator" : "");

        if (player.isOperator) {
            accentType = ThemeManager.getAccent("calm");
        }
    }
}