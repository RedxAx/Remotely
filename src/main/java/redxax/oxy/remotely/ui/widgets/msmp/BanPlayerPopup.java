package redxax.oxy.remotely.ui.widgets.msmp;

import redxax.oxy.remotely.data.managed.ManagedPlayer;
import restudio.rebase.msmp.IMSMPApi;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

public class BanPlayerPopup extends PopupWidget {

    public BanPlayerPopup(Screen parent, ManagedPlayer player, PlayerManagerController controller) {
        super(0, 0, 350, 200, "Ban " + player.name);
        setLayer(500);

        Builder builder = new Builder("Ban " + player.name)
                .size(350, 160)
                .setResizable(true);

        TextInputWidget reasonInput = new TextInputWidget.Builder().placeholder("Reason for ban").build();
        TextInputWidget expiresInput = new TextInputWidget.Builder().text("forever").build();
        ToggleWidget ipBanToggle = new ToggleWidget.Builder().toggled(false).build();

        builder.addRow("Reason", true, 20, reasonInput);
        builder.addRow("Expires (e.g., 1d, 2h, forever)", true, 20, expiresInput);
        if (player.isOnline && player.address != null && !player.address.isEmpty()) {
            builder.addRow("IP Ban", false, 20, ipBanToggle);
        }

        builder.addTitleButton(() -> {
            String reason = reasonInput.getText();
            String expires = expiresInput.getText();
            boolean ipBan = ipBanToggle.getValue();

            controller.banPlayer(player, reason, expires, ipBan).whenComplete((v, ex) -> {
                if (ex != null) {
                    new Notification("Error", "Failed to ban player: " + ex.getMessage(), Notification.Type.ERROR);
                } else {
                    new Notification("Success", player.name + " has been banned.", Notification.Type.SUCCESS);
                }
                controller.fullRefresh();
            });
            hide();

        }, "Confirm Ban", ThemeManager.getAccent("danger"));

        PopupWidget configuredPopup = builder.build();
        this.rows.addAll(configuredPopup.rows);
        this.titleButtons.addAll(configuredPopup.titleButtons);
        this.setSize(configuredPopup.getWidth(), configuredPopup.getHeight());
        this.setPosition(configuredPopup.getX(), configuredPopup.getY());
        this.setWidth(getWidth());

        parent.addDrawableChild(this);
        this.show();
    }
}