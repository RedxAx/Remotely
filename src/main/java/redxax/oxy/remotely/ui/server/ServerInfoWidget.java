package redxax.oxy.remotely.ui.server;

import restudio.rebase.backend.feature.ServerInfoFeature;
import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Timer;
import java.util.TimerTask;

public class ServerInfoWidget extends IconButton {
    private Instance instance;
    private String address = "Loading...";

    public ServerInfoWidget(Instance instance) {
        super(0, 0, 100, 18, "Loading...", null);
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
        this.instance = instance;
        loadInfo();
    }

    private void loadInfo() {
        if (instance != null && instance.getBackend() != null) {
            instance.getBackend().getFeature(ServerInfoFeature.class).ifPresentOrElse(
                feature -> feature.getConnectionInfo().thenAccept(info -> ScreenManager.getInstance().execute(() -> {
                    address = info.getDisplayString();
                    setMessage(address);
                    setOnClick(() -> {
                        try {
                            StringSelection selection = new StringSelection(address);
                            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                            new Notification.Builder().autoSlideOut(true).dismissAfterSeconds(1).type(Notification.Type.SUCCESS).message("IP Copied!");
                            new Timer().schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    ScreenManager.getInstance().execute(() -> setMessage(address));
                                }
                            }, 2000);
                        } catch (Exception ignored) {}
                    });
                })),
                () -> ScreenManager.getInstance().execute(() -> setMessage("Unknown"))
            );
        } else {
            setMessage("Local");
            setOnClick(null);
        }
    }
}
