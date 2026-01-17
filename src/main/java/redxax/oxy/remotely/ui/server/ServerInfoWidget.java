package redxax.oxy.remotely.ui.server;

import restudio.rebase.backend.feature.ServerInfoFeature;
import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

public class ServerInfoWidget extends IconButton {
    private Instance instance;
    private String address = "Loading...";

    public ServerInfoWidget(Instance instance) {
        super(0, 0, 100, 18, "", null);
        this.instance = instance;
        this.autoWidthOnTextChange = true;
        setIcon("clipboard");
        animateLayout = true;
        animateElevation = false;
        addOnHoveredChanged(
            (w, h) -> setMessage(h ? address : "")
        );
        loadInfo();
    }

    public void setInstance(Instance instance) {
        if (this.instance == instance) return;
        this.instance = instance;
        loadInfo();
    }

    private void loadInfo() {
        if (instance != null && instance.getBackend() != null) {
            instance.getBackend().getFeature(ServerInfoFeature.class).ifPresentOrElse(
                feature -> feature.getConnectionInfo().thenAccept(info -> ScreenManager.getInstance().execute(() -> {
                    address = info.getDisplayString();
                    setOnClick(() -> {
                        try {
                            StringSelection selection = new StringSelection(address);
                            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                            new Notification.Builder().autoSlideOut(true).dismissAfterSeconds(1).type(Notification.Type.SUCCESS).message("IP Copied!");
                        } catch (Exception ignored) {}
                    });
                })),
                () -> ScreenManager.getInstance().execute(() -> setMessage("Unknown"))
            );
        } else {
            setOnClick(null);
        }
    }
}
