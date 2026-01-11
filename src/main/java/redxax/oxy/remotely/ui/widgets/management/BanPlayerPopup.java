package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

public class BanPlayerPopup extends PopupWidget {

    public BanPlayerPopup(Screen parent, UnifiedPlayer player, PlayerManagerController controller) {
        super(0, 0, 350, 200, "Ban " + player.getName());
        setLayer(500);

        Builder builder = new Builder("Ban " + player.getName()).size(350, 160).setResizable(true);

        var ref = new Object() {
            TextInputWidget reasonInput = new TextInputWidget.Builder().placeholder("Reason for ban").build();
        };
        ToggleWidget ipBanToggle = new ToggleWidget.Builder().toggled(false).build();
        Runnable banAction = () -> {
            String reason = ref.reasonInput.getText();
            boolean ipBan = ipBanToggle.getValue();
            controller.banPlayer(player, reason, ipBan);
            new Notification(player.getName() + " has been banned.", "Click Here To Unban", Notification.Type.SUCCESS, () -> controller.unbanPlayer(player));
            hide();
        };
        ref.reasonInput = new TextInputWidget.Builder().placeholder("Reason for ban").onEnter(banAction).build();

        builder.addRow("Reason", true, 20, ref.reasonInput);
        if (player.isOnline() && player.getIp().getValue() != null && !player.getIp().getValue().isEmpty()) {
            builder.addRow("IP Ban", false, 20, ipBanToggle);
        }

        builder.addTitleButton(banAction, "Confirm Ban", ThemeManager.getAccent("nice"));

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
