package redxax.oxy.remotely.servers;

import net.minecraft.client.MinecraftClient;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.config.SettingsScreenFactory;
import redxax.oxy.remotely.ui.screens.RemotelyInstanceDetailsScreen;
import redxax.oxy.remotely.ui.widgets.DesktopIconWidget;
import restudio.rebase.Rebase;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.resources.ResourceBrowserScreen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.DesktopLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;
import static restudio.rescreen.util.SoundUtils.playSound;

public class ServerManagerScreen extends ReScreen {
    private final RemotelyClient remotelyClient;
    private Instance instanceForDeletion;
    private PopupWidget addServerPopup;
    private PopupWidget deleteServerPopup;
    private PopupWidget remoteHostPopup;
    private TextInputWidget remoteHostNameInput;
    private TextInputWidget remoteHostUserInput;
    private TextInputWidget remoteHostIpInput;
    private TextInputWidget remoteHostPortInput;
    private TextInputWidget remoteHostPasswordInput;
    private AnimatedButton remoteHostConfirmButton;
    private AnimatedButton remoteHostDeleteButton;
    private restudio.rescreen.ui.core.Screen parent;
    private net.minecraft.client.gui.screen.Screen mcParent;

    private static BufferedImage unknown, serverIcon, paper, vanilla, fabric, forge, neoforge, waterfall, velocity, leaf, quilt, spigot, bukkit, purpur;
    private InstanceManager instanceManager;

    public ServerManagerScreen(restudio.rescreen.ui.core.Screen parent, RemotelyClient remotelyClient) {
        super();
        this.parent = parent;
        this.mcParent = null;
        this.remotelyClient = remotelyClient;
    }

    public ServerManagerScreen(net.minecraft.client.gui.screen.Screen parent, RemotelyClient remotelyClient) {
        super();
        this.mcParent = parent;
        this.parent = null;
        this.remotelyClient = remotelyClient;
    }

    @Override
    public void init() {
        super.init();
        this.instanceManager = Rebase.get().getInstanceManager();
        instanceManager.loadInstances();
        loadIcons();
        createPopups();

        int taskbarHeight = 28;
        header().position(HeaderBuilder.Position.BOTTOM).size(taskbarHeight)
                .addLeft("terminal.png", () -> remotelyClient.openMultiTerminal(this), "Terminal")
                .addLeft("explorer.png", this::openFileExplorer, "File Explorer")
                .addLeft("minibrowser.png", () -> remotelyClient.openBrowser(this), "Web Browser")
                .addLeft("remotely.png", () -> client.setScreen(SettingsScreenFactory.createGlobalSettingsScreen( this,(RemotelyConfigManager) Rebase.get().getConfigManager())), "Settings")
                .build();

        tabs().builder()
                .position(width / 2, height - taskbarHeight + 4)
                .size(width / 2 - 5, 18)
                .rightToLeft(true)
                .allowAdd(true)
                .allowRename(true).allowReorder(true).allowClose(true)
                .onPlusButtonClicked(() -> openRemoteHostPopup(false))
                .onTabSelected(this::onHostTabSelected)
                .onTabClosed(this::onHostTabClosed)
                .onTabRenamed(this::onHostTabRenamed)
                .build();

        Container desktopContainer = createContainer("desktop", 0, 0, width, height - 35);
        desktopContainer.layout(new DesktopLayout()).backgroundDrawing(false).enableSelecting(true).enableDoubleClick(false).disableScissorRegion(true).enableDoubleClick(false);

        setActiveContainer(desktopContainer);
        populateHostTabs();
    }

    private void loadIcons() {
        try {
            serverIcon = loadResourceIcon("server.png");
            unknown = loadResourceIcon("unknown.png");
            paper = loadResourceIcon("paper.png");
            vanilla = loadResourceIcon("vanilla.png");
            fabric = loadResourceIcon("fabric.png");
            forge = loadResourceIcon("forge.png");
            neoforge = loadResourceIcon("neoforge.png");
            waterfall = loadResourceIcon("waterfall.png");
            velocity = loadResourceIcon("velocity.png");
            leaf = loadResourceIcon("leaf.png");
            quilt = loadResourceIcon("quilt.png");
            spigot = loadResourceIcon("spigot.png");
            bukkit = loadResourceIcon("bukkit.png");
            purpur = loadResourceIcon("purpur.png");
        } catch (Exception e) {
            new Notification("Failed to load icons: " + e.getMessage(), Notification.Type.ERROR);
        }
    }

    private void populateHostTabs() {
        tabs().addTab("Local", activeContainer).setData(null);
        for (RemoteHost host : instanceManager.getRemoteHosts()) {
            Container c = createContainer("desktop_remote_" + host.name, 0, 0, width, height - 35);
            c.layout(new DesktopLayout()).backgroundDrawing(false).enableSelecting(true);
            tabs().addTab(host.name, c).setData(host);
        }
        int savedIndex = remotelyClient.getSavedTabIndex();
        tabs().setActiveTab(Math.min(savedIndex, tabs().getTabs().size() - 1));
        loadServersForCurrentTab();
    }

    private void loadServersForCurrentTab() {
        activeContainer.clearWidgets();
        for (Instance server : getCurrentServers()) {
            addServerWidget(server, false);
        }
        addServerWidget(null, true);
        activeContainer.updateWidgetPositions();
    }

    private void addServerWidget(Instance info, boolean isCreate) {
        DesktopIconWidget widget = new DesktopIconWidget.Builder(info, isCreate, isCreate ? serverIcon : getServerIcon(info)).onClick(this::onDesktopIconClick).build();
        activeContainer.addWidget(widget);
    }

    private void onHostTabSelected(TabsManager.Tab tab) {
        remotelyClient.saveTabIndex(tabs().getActiveTabIndex());
        if (tab.getContainer().getWidgets().isEmpty()) {
            loadServersForCurrentTab();
        }
        if (tabs().getActiveTabIndex() > 0) {
            RemoteHost host = (RemoteHost) tab.getData();
            if (!host.isConnected && !host.isConnecting) {
                connectRemoteHostAsync(host);
            }
        }
    }

    private void onHostTabClosed(TabsManager.Tab tab) {
        if (tab.getData() instanceof RemoteHost host) {
            instanceManager.removeRemoteHost(host);
        }
    }

    private void onHostTabRenamed(TabsManager.Tab tab) {
        if (tab.getData() instanceof RemoteHost) {
            openRemoteHostPopup(true);
        }
    }

    private void onDesktopIconClick(DesktopIconWidget widget, int button) {
        if (button == 0) {
            if (widget.isCreateButton()) {
                playSound(Sound.CREATE);
                addServerPopup.setX((this.width - addServerPopup.getWidth())/2);
                addServerPopup.setY((this.height - addServerPopup.getHeight())/2);
                addServerPopup.show();
            } else {
                openServerScreen(widget.getInstance());
            }
        } else if (button == 1) {
            if (!widget.isCreateButton()) {
                activeContainer.clearSelection();
                activeContainer.addSelectedWidget(widget);
                ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this)
                        .addHeaderButton("edit.png", () -> client.setScreen(new ServerConfigurationScreen(this, widget.getInstance(), widget.getInstance().getRemoteHost())), "Edit Server's Settings")
                        .addHeaderButton("explorer.png", () -> remotelyClient.openFileExplorer(this, Path.of(widget.getInstance().getPath())), "Open Server's Folder")
                        .addHeaderButton("delete.png", () -> {
                            instanceForDeletion = widget.getInstance();
                            deleteServerPopup.setX((this.width - deleteServerPopup.getWidth())/2);
                            deleteServerPopup.setY((this.height - deleteServerPopup.getHeight())/2);
                            deleteServerPopup.show();
                        }, "Show Deletion Options");
                showContextMenu(widget.getX() + widget.getWidth() + 4, widget.getY() + 24, builder);
            }
        }
    }

    private List<Instance> getCurrentServers() {
        if (tabsManager == null || tabs().getActiveTabIndex() == 0) {
            return instanceManager.getLocalInstances();
        }
        if (tabs().getActiveTabIndex() > 0) {
            RemoteHost host = (RemoteHost) tabs().getActiveTab().getData();
            return instanceManager.getRemoteInstances(host);
        }
        return new ArrayList<>();
    }

    private void openFileExplorer() {
        remotelyClient.openFileExplorer(this, remotelyDir);
    }

    private void createPopups() {
        createAddServerPopup();
        createDeleteServerPopup();
        createRemoteHostPopup();
    }

    private void createAddServerPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Add a Server")
                .size(260, 140)
                .onClose(() -> addServerPopup.hide());

        RemoteHost currentHost = (tabs().getActiveTabIndex() > 0) ? (RemoteHost) tabs().getActiveTab().getData() : null;

        AnimatedButton createBtn = new AnimatedButton.Builder()
                .label(("Server Creation"))
                .onClick(() -> {
                    client.setScreen(new ServerConfigurationScreen(this, null, currentHost));
                    addServerPopup.hide();
                })
                .build();

        AnimatedButton importBtn = new AnimatedButton.Builder()
                .label(("Server Import"))
                .onClick(() -> {
                    addServerPopup.hide();
                    openImportFileExplorer();
                })
                .build();

        AnimatedButton modpackBtn = new AnimatedButton.Builder()
                .label(("Modpack Installation"))
                .onClick(() -> {
                    addServerPopup.hide();
                    openModpackInstallation();
                })
                .build();

        builder.addRow("", true, 27, createBtn);
        builder.addRow("", true, 27, importBtn);
        builder.addRow("", true, 27, modpackBtn);

        addServerPopup = builder.build();
        addServerPopup.hide();
        addDrawableChild(addServerPopup);
    }

    private void createDeleteServerPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Are You Sure?")
                .size(260, 140)
                .onClose(() -> deleteServerPopup.hide());

        AnimatedButton deleteTrashBtn = new AnimatedButton.Builder()
                .label(("Delete The Server"))
                .onClick(() -> {
                    playSound(Sound.DELETE);
                    instanceManager.removeInstance(instanceForDeletion);
                    loadServersForCurrentTab();
                    deleteServerPopup.hide();
                })
                .build();

        AnimatedButton cancelBtn = new AnimatedButton.Builder()
                .label(("Cancel"))
                .onClick(() -> {
                    playSound(Sound.CLICK);
                    deleteServerPopup.hide();
                })
                .build();

        builder.addRow("", true, 27, deleteTrashBtn);
        builder.addRow("", true, 27, cancelBtn);

        deleteServerPopup = builder.build();
        deleteServerPopup.hide();
        addDrawableChild(deleteServerPopup);
    }

    private void createRemoteHostPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Remote Host")
                .onClose(this::closeRemoteHostPopup)
                .size(360, 250)
                .setResizable(true)
                .setMinSize(360, 250);

        remoteHostNameInput = new TextInputWidget.Builder().build();
        builder.addRow("Host Name:", true, 20, remoteHostNameInput);

        remoteHostUserInput = new TextInputWidget.Builder().text("root").build();
        builder.addRow("User Name:", true, 20, remoteHostUserInput);

        remoteHostIpInput = new TextInputWidget.Builder().build();
        builder.addRow("IP | Domain:", true, 20, remoteHostIpInput);

        remoteHostPortInput = new TextInputWidget.Builder().text("22").build();
        builder.addRow("Port:", true, 20, remoteHostPortInput);

        remoteHostPasswordInput = new TextInputWidget.Builder().build();
        builder.addRow("Password:", true, 20, remoteHostPasswordInput);

        remoteHostConfirmButton = new AnimatedButton.Builder().label(("Test & Add")).onClick(this::onConfirmRemoteHost).build();
        AnimatedButton cancelButton = new AnimatedButton.Builder().label(("Cancel")).onClick(this::closeRemoteHostPopup).build();
        remoteHostDeleteButton = new AnimatedButton.Builder().label(("Delete")).onClick(this::onDeleteRemoteHost).accentType(restudio.rescreen.config.Config.AccentType.DANGER).build();
        builder.addRow("", false, 20, remoteHostConfirmButton, cancelButton, remoteHostDeleteButton);

        remoteHostPopup = builder.build();
        remoteHostPopup.hide();
        addDrawableChild(remoteHostPopup);
    }

    private void connectRemoteHostAsync(RemoteHost hostInfo) {
        hostInfo.isConnecting = true;
        new Thread(() -> {
            restudio.rebase.util.ssh.SSHManager sshManager = hostInfo.getSshManager();
            if (sshManager != null) {
                sshManager.connectToRemoteHost(hostInfo.getUser(), hostInfo.getIp(), hostInfo.getPort(), hostInfo.getPassword());
                hostInfo.isConnected = sshManager.isSSH();
            } else {
                hostInfo.isConnected = false;
            }
            hostInfo.connectionError = hostInfo.isConnected ? null : "Failed to connect.";
            hostInfo.isConnecting = false;
        }).start();
    }

    private void openRemoteHostPopup(boolean isEditing) {
        int activeTabIndex = tabs().getActiveTabIndex();
        if (isEditing && activeTabIndex > 0) {
            RemoteHost host = (RemoteHost) tabs().getTabs().get(activeTabIndex).getData();
            remoteHostConfirmButton.setMessage(("Save"));
            remoteHostDeleteButton.visible = true;

            remoteHostNameInput.setText(host.name);
            remoteHostUserInput.setText(host.user);
            remoteHostIpInput.setText(host.ip);
            remoteHostPortInput.setText(String.valueOf(host.port));
            remoteHostPasswordInput.setText(host.password);
        } else {
            remoteHostConfirmButton.setMessage(("Test & Add"));
            remoteHostDeleteButton.visible = false;
            remoteHostNameInput.setText("");
            remoteHostUserInput.setText("root");
            remoteHostIpInput.setText("");
            remoteHostPortInput.setText("22");
            remoteHostPasswordInput.setText("");
        }

        remoteHostPopup.setX((this.width - remoteHostPopup.getWidth()) / 2);
        remoteHostPopup.setY((this.height - remoteHostPopup.getHeight()) / 2);
        remoteHostPopup.show();
    }

    private void onConfirmRemoteHost() {
        RemoteHost host;
        boolean isEditing = tabs().getActiveTabIndex() > 0 && "Save".equals(remoteHostConfirmButton.getMessage());

        if (isEditing) {
            host = (RemoteHost) tabs().getActiveTab().getData();
        } else {
            host = new RemoteHost();
        }

        host.name = remoteHostNameInput.getText();
        host.user = remoteHostUserInput.getText();
        host.ip = remoteHostIpInput.getText();
        try {
            host.port = Integer.parseInt(remoteHostPortInput.getText());
        } catch (NumberFormatException e) {
            new Notification("Error", "Port must be a valid number.", Notification.Type.ERROR);
            return;
        }
        host.password = remoteHostPasswordInput.getText();

        if (host.name.isEmpty() || host.ip.isEmpty()) {
            new Notification("Error", "Host Name and IP cannot be empty.", Notification.Type.ERROR);
            return;
        }

        if (isEditing) {
            instanceManager.updateRemoteHost(host);
            tabs().getActiveTab().setName(host.name);
        } else {
            instanceManager.addRemoteHost(host);
            Container c = createContainer("desktop_remote_" + host.name, 0, 0, width, height - 35);
            c.layout(new DesktopLayout()).backgroundDrawing(false).enableSelecting(true);
            tabs().addTab(host.name, c).setData(host);
            tabs().setActiveTab(tabs().getTabs().size() - 1);
        }

        closeRemoteHostPopup();
    }

    private void onDeleteRemoteHost() {
        if (tabs().getActiveTabIndex() > 0) {
            RemoteHost hostToRemove = (RemoteHost) tabs().getActiveTab().getData();
            instanceManager.removeRemoteHost(hostToRemove);
            tabs().removeTab(tabs().getActiveTabIndex());
            tabs().setActiveTab(0);
            closeRemoteHostPopup();
        }
    }

    private void openServerScreen(Instance info) {
        if (mcParent != null) {
            remotelyClient.openInstanceInTerminal(mcParent, info);
        } else {
            remotelyClient.openInstanceInTerminal(parent, info);
        }
    }

    public static void openServerScreen(String path) {
        List<Instance> allInstances = new ArrayList<>(InstanceManager.getInstance().getLocalInstances());
        InstanceManager.getInstance().getRemoteHosts().forEach(h -> allInstances.addAll(InstanceManager.getInstance().getRemoteInstances(h)));
        for (Instance info : allInstances) {
            if (info.getPath().equals(path)) {
                RemotelyClient.INSTANCE.openInstanceInTerminal(ScreenManager.currentScreen, info);
                return;
            }
        }
    }

    private void closeRemoteHostPopup() {
        if(remoteHostPopup != null) {
            remoteHostPopup.hide();
        }
    }

    private void openImportFileExplorer() {
        client.setScreen(new FileExplorerScreen(parent, null, remotelyDir, Path.of(remotelyDir.toString(), "data"), true));
    }

    private void openModpackInstallation() {
        try {
            Instance serverInfo = new Instance("Modpack Server", "latest", "modpack");
            serverInfo.setModLoader(restudio.rebase.instance.loaders.ModLoader.FABRIC);

            if (tabs().getActiveTabIndex() > 0) {
                RemoteHost remoteHost = (RemoteHost) tabs().getActiveTab().getData();
                serverInfo.setRemote(true);
                serverInfo.setRemoteHost(remoteHost);
                serverInfo.setPath(remoteHost.getHomeDirectory() + "remotely/servers/" + serverInfo.getName());
            } else {
                serverInfo.setRemote(false);
                serverInfo.setRemoteHost(null);
                serverInfo.setPath(remotelyDir + "/servers/" + serverInfo.getName());
            }
            client.setScreen(new ResourceBrowserScreen(this, serverInfo));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removed() {
        remotelyClient.saveTabIndex(tabs().getActiveTabIndex());
        super.removed();
    }

    private BufferedImage getServerIcon(Instance server) {
        try {
            if (!server.isRemote()) {
                File iconFile = new File(server.getPath(), "icon.png");
                if (iconFile.exists() && iconFile.isFile()) {
                    return ImageIO.read(iconFile);
                }
            }
        } catch (IOException e) {
            devPrint("Failed to load server icon: " + e.getMessage());
        }
        return switch (server.getModLoader().name().toLowerCase(Locale.ROOT)) {
            case "vanilla" -> vanilla;
            case "fabric" -> fabric;
            case "forge" -> forge;
            case "neoforge" -> neoforge;
            case "paper" -> paper;
            case "purpur" -> purpur;
            case "spigot" -> spigot;
            case "bukkit" -> bukkit;
            case "leaf" -> leaf;
            case "velocity" -> velocity;
            case "waterfall" -> waterfall;
            default -> unknown;
        };
    }

    @Override
    public void onDisplayed() {
        super.onDisplayed();
        playSound(Sound.SERVERMANAGER);
    }

    @Override
    public void close() {
        if (parent != null) {
            client.setScreen(parent);
        } else if (mcParent != null) {
            MinecraftClient.getInstance().setScreen(mcParent);
        } else {
            super.close();
        }
    }
}