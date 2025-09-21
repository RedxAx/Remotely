package redxax.oxy.remotely.ui.screens;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.servers.ReverseProxyManager;
import redxax.oxy.remotely.ui.widgets.InstanceResourceWidget;
import restudio.rebase.Rebase;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.resource.InstanceResource;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.instance.InstanceDetailsScreen;
import restudio.rebase.ui.screens.resources.ResourceBrowserScreen;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInstanceDetailsScreen extends InstanceDetailsScreen {

    private TerminalWidget terminalWidget;
    private net.minecraft.client.gui.screen.Screen mcParent;
    private final boolean isLocalTerminalMode;
    private List<InstanceResource> currentResources = new ArrayList<>();

    Container mainContainer = createContainer("terminal", 5, 60, width - 10, height - 65);
    Container resourcesContainer;

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
    public void init() {
        super.init();
        if (!isLocalTerminalMode) {
            loadResources();
        }
    }

    @Override
    protected void setupHeader() {
        header().addRight("close.png", this::closeScreen, "Close");
        if (!isLocalTerminalMode) {
            header().addLeft("explorer.png", this::exploreInstanceFiles, "File Explorer");

            ModLoader modLoader = instance.getModLoader();
            if (modLoader != null && modLoader != ModLoader.VELOCITY && modLoader != ModLoader.WATERFALL && modLoader != ModLoader.BUNGEECORD) {
                header().addLeft("resources.png", this::openInstanceResources, "Resources");
            }
            updateHeaderButtons();
        }
        header().build();
    }

    @Override
    protected void setupTabs() {
        tabs().builder().position(5, 35).size(width - 10, 18).build();
        mainContainer.backgroundDrawing(true).verticalSpacing(4);

        this.terminalWidget = TerminalWidget.getOrCreate(instance, 0, 0, mainContainer.getEffectiveWidth(), mainContainer.getHeight());
        terminalWidget.setScissorRegion(5, 60, width - 10, height - 65);
        mainContainer.addWidget(this.terminalWidget);
        terminalWidget.setIgnoreScissorRegion(false);

        tabs().addTab(instance.getName(), mainContainer);

        if (!isLocalTerminalMode) {
            resourcesContainer = new Container(5, 60, width - 10, height - 65);
            resourcesContainer.layout(new ManagedLayout()).columns(1).padding(2).scrolling(true);
            resourcesContainer.setScissorRegion(5, 60, width - 10, height - 65);
            mainContainer.addWidget(resourcesContainer);
        }
        tabs().setActiveTab(mainContainer);
    }



    private void loadResources() {
        if (resourcesContainer == null) return;
        resourcesContainer.clearWidgets();

        Rebase.get().getResourceManager().getResources(instance).thenCompose(resources -> Rebase.get().getUpdateManager().checkForUpdates(instance).thenApply(updates -> {
            for (InstanceResource resource : resources) {
                if (resource.getFileHash() != null && updates.containsKey(resource.getFileHash())) {
                    resource.availableUpdate = updates.get(resource.getFileHash());
                }
            }
            return resources;
        })).thenAccept(loadedResources -> client.execute(() -> {
            this.currentResources = loadedResources;
            resourcesContainer.clearWidgets();

            if (currentResources.isEmpty()) {
                resourcesContainer.addWidget(new AnimatedButton.Builder().label("No resources found.").active(false).build());
            } else {
                for (InstanceResource resource : currentResources) {
                    InstanceResourceWidget widget = new InstanceResourceWidget(this, instance, resource, this::loadResources);
                    widget.setHeight(30);
                    resourcesContainer.addWidget(widget);
                }
            }
            resourcesContainer.updateWidgetPositions();
        }));
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            loadResources();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

        if (instance.getState() == InstanceState.RUNNING || instance.getState() == InstanceState.STARTING) {
            if (instance.isRemote()) {
                terminalWidget.executeCommand("stop");
            } else {
                TerminalWidget.shutdown(instance.getInstanceId());
                instance.setState(InstanceState.STOPPED);
                updateHeaderButtons();
                Container terminalContainer = getContainer("terminal");
                terminalContainer.clearWidgets();
                this.terminalWidget = TerminalWidget.getOrCreate(instance, 0, 0, terminalContainer.getEffectiveWidth(), terminalContainer.getHeight());
                terminalContainer.addWidget(this.terminalWidget);
            }
        } else {
            if (instance.isRemote()) {
                try {
                    RebaseAPI api = RebaseApiFactory.get(instance);
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
}