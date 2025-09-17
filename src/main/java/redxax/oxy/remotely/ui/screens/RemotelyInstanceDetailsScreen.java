package redxax.oxy.remotely.ui.screens;

import net.minecraft.client.MinecraftClient;
import redxax.oxy.remotely.servers.ReverseProxyManager;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.instance.InstanceDetailsScreen;
import restudio.rebase.ui.screens.resources.ResourceBrowserScreen;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInstanceDetailsScreen extends InstanceDetailsScreen {

    private TerminalWidget terminalWidget;
    private net.minecraft.client.gui.screen.Screen mcParent;
    private final boolean isLocalTerminalMode;

    public RemotelyInstanceDetailsScreen(Screen parent, Instance instance) {
        super(parent, instance);
        this.isLocalTerminalMode = (instance == null);
    }

    public RemotelyInstanceDetailsScreen(net.minecraft.client.gui.screen.Screen mcParent, Instance instance) {
        super(null, instance);
        this.mcParent = mcParent;
        this.isLocalTerminalMode = (instance == null);
    }

    @Override
    protected void setupHeader() {
        header().addRight("close.png", this::closeScreen, "Close");
        if (!isLocalTerminalMode) {
            header().addLeft("explorer.png", this::exploreInstanceFiles, "File Explorer");
            header().addLeft("resources.png", this::openInstanceResources, "Resources");
            updateHeaderButtons();
        }
        header().build();
    }

    @Override
    protected void setupTabs() {
        tabs().builder()
                .position(5, 35)
                .size(width - 10, 18)
                .build();

        Container terminalContainer = createContainer("terminal", 5, 60, width - 10, height - 65);
        this.terminalWidget = new TerminalWidget.Builder().server(instance).size(terminalContainer.getEffectiveWidth(), terminalContainer.getHeight()).build();
        terminalContainer.addWidget(this.terminalWidget);
        if (isLocalTerminalMode) {
            this.terminalWidget.start();
        }
        tabs().addTab("Terminal", terminalContainer);

        if (!isLocalTerminalMode) {
            Container resourcesContainer = createContainer("resources", 5, 60, width - 10, height - 65);
            resourcesContainer.layout(new ManagedLayout()).columns(2).padding(5).scrolling(true);
            tabs().addTab("Resources", resourcesContainer);
        }

        tabs().setActiveTab(0);
        setActiveContainer(terminalContainer);
    }

    @Override
    protected void onStateChanged(InstanceState newState) {
        if (isLocalTerminalMode) return;
        updateHeaderButtons();
    }

    private void updateHeaderButtons() {
        if (isLocalTerminalMode) return;

        header().setButtonVisible("start.png", false);
        header().setButtonVisible("stop.png", false);
        header().setButtonVisible("reverse.png", false);
        header().setButtonVisible("closeReverse.png", false);

        boolean isRunning = instance.getState() == InstanceState.RUNNING || instance.getState() == InstanceState.STARTING;
        header().addLeft(isRunning ? "stop.png" : "start.png", this::launchOrStopInstance, isRunning ? "Stop Server" : "Start Server");

        boolean isProxy = instance.getModLoader() != null && List.of("velocity", "waterfall", "bungeecord").contains(instance.getModLoader().name().toLowerCase(Locale.getDefault()));
        header().setButtonVisible("resources.png", !isProxy);

        boolean isReversed = ReverseProxyManager.isPortForwarded(instance);
        header().addLeft(isReversed ? "closeReverse.png" : "reverse.png", () -> ReverseProxyManager.reverse(instance), isReversed ? "Close Reverse Proxy" : "Open Server To The Public");

        header().build();
    }

    private void launchOrStopInstance() {
        if (isLocalTerminalMode) return;
        RebaseAPI api = RebaseApiFactory.get(instance);
        if (instance.getState() == InstanceState.RUNNING || instance.getState() == InstanceState.STARTING) {
            terminalWidget.executeCommand("stop");
        } else {
            if (instance.isRemote()) {
                try {
                    api.launchServer(instance, command -> {
                        if (command != null && !command.isEmpty()) {
                            terminalWidget.executeCommand(command);
                        }
                    });
                } catch (Exception e) {
                    new Notification("Failed to start server", e.getMessage(), Notification.Type.ERROR);
                }
            } else {
                terminalWidget.startServerProcess();
            }
        }
    }

    private void exploreInstanceFiles() {
        if (isLocalTerminalMode) return;
        client.setScreen((new FileExplorerScreen(this, instance, Path.of(instance.getPath()), Path.of(remotelyDir.toString(), "data"), false)));
    }

    private void openInstanceResources() {
        if (isLocalTerminalMode) return;
        client.setScreen(new ResourceBrowserScreen(this, instance));
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (terminalWidget != null && activeContainer != null) {
            terminalWidget.setSize(activeContainer.getEffectiveWidth(), activeContainer.getHeight());
        }
    }

    @Override
    public void closeScreen() {
        if (parent != null) {
            client.setScreen(parent);
        } else if (mcParent != null) {
            MinecraftClient.getInstance().setScreen(mcParent);
        } else {
            super.closeScreen();
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (terminalWidget != null) {
            terminalWidget.shutdown();
        }
    }
}