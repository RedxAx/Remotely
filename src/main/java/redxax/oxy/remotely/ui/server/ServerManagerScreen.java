package redxax.oxy.remotely.ui.server;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.config.SettingsScreenFactory;
import redxax.oxy.remotely.ui.widgets.DesktopIconWidget;
import restudio.rebase.api.unified.InstanceApi;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.ui.worldmap.WorldMapScreen;
import restudio.rebase.Rebase;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.screens.resources.ResourceBrowserScreen;
import restudio.rebase.util.RebaseLogger;
import restudio.rebase.util.ssh.SSHManager;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.DesktopLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;
import restudio.rescreen.util.ImageUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;
import static restudio.rescreen.render.Render.drawOuterBorder;
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
    private final Object parent;
    int bx1 = 0, by1 = 0, bx2 = 0, by2 = 0;

    private static BufferedImage unknown, serverIcon, paper, vanilla, fabric, forge, neoforge, waterfall, velocity, leaf, quilt, spigot, bukkit, purpur;
    private InstanceManager instanceManager;

    public ServerManagerScreen(Object parent, RemotelyClient remotelyClient) {
        super();
        this.parent = parent;
        this.remotelyClient = remotelyClient;
    }

    @Override
    public void init() {
        super.init();
        this.instanceManager = Rebase.get().getInstanceManager();
        reloadInstancesSmartly();
        loadIcons();
        createPopups();

        int taskbarHeight = 28;
        header().position(HeaderBuilder.Position.BOTTOM).size(taskbarHeight)
            .addLeft("terminal.png", () -> remotelyClient.openMultiTerminal(this), "Terminal")
            .addLeft("explorer.png", this::openFileExplorer, "File Explorer")
            .addLeft("remotely.png", () -> client.setScreen(SettingsScreenFactory.createGlobalSettingsScreen( this,(RemotelyConfigManager) Rebase.get().getConfigManager())), "Settings")
            .build();

        tabs().builder()
            .position(width / 2, height - taskbarHeight + 4)
            .size(width / 2 - 5, 18)
            .rightToLeft(true)
            .allowAdd(true)
            .allowRename(false).allowReorder(false).allowClose(false)
            .onPlusButtonClicked(() -> openRemoteHostPopup(false))
            .onTabSelected(this::onHostTabSelected)
            .onTabClosed(this::onHostTabClosed)
            .onTabRenamed(this::onHostTabRenamed)
            .build();

        Container desktopContainer = createContainer("desktop", 0, 0, width, height - 35);
        DesktopLayout localLayout = new DesktopLayout();
        localLayout.setOnReorder(() -> saveServerOrder(desktopContainer, null));
        desktopContainer.layout(localLayout).backgroundDrawing(false).enableSelecting(true).enableDoubleClick(false).disableScissorRegion(true).enableDoubleClick(false);

        setActiveContainer(desktopContainer);
        populateHostTabs();
    }

    private void reloadInstancesSmartly() {
        Map<String, SSHManager> activeSessions = new HashMap<>();
        if (instanceManager != null) {
            for (RemoteHost host : instanceManager.getRemoteHosts()) {
                SSHManager mgr = host.getExistingSshManager();
                if (mgr != null && mgr.isConnected()) {
                    activeSessions.put(host.hostId, mgr);
                }
            }

            instanceManager.loadInstances();

            for (RemoteHost host : instanceManager.getRemoteHosts()) {
                if (activeSessions.containsKey(host.hostId)) {
                    SSHManager oldMgr = activeSessions.get(host.hostId);
                    RemoteHost oldHost = oldMgr.getRemoteHost();

                    if (Objects.equals(host.getIp(), oldHost.getIp()) && Objects.equals(host.getUser(), oldHost.getUser()) && host.getPort() == oldHost.getPort()) {
                        oldMgr.updateHostReference(host);
                        host.setSshManager(oldMgr);
                    } else {
                        try { oldMgr.disconnect(); } catch (Exception ignored) {}
                    }
                }
            }
        } else {
            this.instanceManager = Rebase.get().getInstanceManager();
            instanceManager.loadInstances();
        }
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
            DesktopLayout remoteLayout = new DesktopLayout();
            remoteLayout.setOnReorder(() -> saveServerOrder(c, host));
            c.layout(remoteLayout).backgroundDrawing(false).enableSelecting(true).disableScissorRegion(true);
            tabs().addTab(host.name, c).setData(host);
        }
        int savedIndex = remotelyClient.getSavedTabIndex();
        tabs().setActiveTab(Math.min(savedIndex, tabs().getTabs().size() - 1));
        loadServersForCurrentTab();
    }

    private void loadServersForCurrentTab() {
        if (activeContainer == null) return;
        activeContainer.clearWidgets();

        List<Instance> instances = new ArrayList<>(getCurrentServers());

        String context = "local";
        if (tabs().getActiveTabIndex() > 0 && tabs().getActiveTab() != null) {
            Object data = tabs().getActiveTab().getData();
            if (data instanceof RemoteHost host) {
                context = "remote." + host.name;
            }
        }

        RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
        List<String> order = config.getInstanceOrder(context);

        if (!order.isEmpty()) {
            Map<String, Integer> orderMap = new HashMap<>();
            for (int i = 0; i < order.size(); i++) {
                orderMap.put(order.get(i), i);
            }

            instances.sort((a, b) -> {
                int idxA = orderMap.getOrDefault(a.getInstanceId(), Integer.MAX_VALUE);
                int idxB = orderMap.getOrDefault(b.getInstanceId(), Integer.MAX_VALUE);
                return Integer.compare(idxA, idxB);
            });
        }

        for (Instance server : instances) {
            addServerWidget(server, false);
        }
        addServerWidget(null, true);
        activeContainer.updateWidgetPositions();
    }

    private void saveServerOrder(Container container, RemoteHost host) {
        List<String> newOrderIds = new ArrayList<>();
        for (AnimatedWidget w : container.getWidgets()) {
            if (w instanceof DesktopIconWidget diw && !diw.isCreateButton()) {
                newOrderIds.add(diw.getInstance().getInstanceId());
            }
        }

        String context = "local";
        if (host != null) {
            context = "remote." + host.name;
        }

        RemotelyConfigManager config = (RemotelyConfigManager) Rebase.get().getConfigManager();
        config.setInstanceOrder(context, newOrderIds);
    }

    private void addServerWidget(Instance info, boolean isCreate) {
        DesktopIconWidget widget = new DesktopIconWidget.Builder(info, isCreate, isCreate ? serverIcon : getServerIcon(info)).onClick(this::onDesktopIconClick).build();
        activeContainer.addWidget(widget);
    }

    private void onHostTabSelected(TabsManager.Tab tab) {
        remotelyClient.saveTabIndex(tabs().getActiveTabIndex());
        setActiveContainer(tab.getContainer());

        Object data = tab.getData();
        if (data instanceof RemoteHost host) {
            if (!host.getSshManager().isConnected()) {
                if (tab.getWidget() != null) tab.getWidget().setAccent(ThemeManager.getAccent("calm"));
                connectRemoteHostAsync(host, () -> {
                    if (tab.getWidget() != null) tab.getWidget().setAccent(ThemeManager.getDefaultAccent());
                    instanceManager.fetchRemoteInstances(host)
                        .whenComplete((v, e) -> ScreenManager.getInstance().execute(this::loadServersForCurrentTab));
                }, () -> {
                    if (tab.getWidget() != null) tab.getWidget().setAccent(ThemeManager.getAccent("danger"));
                });
            } else if (instanceManager.getRemoteInstances(host).isEmpty()) {
                instanceManager.fetchRemoteInstances(host)
                    .whenComplete((v, e) -> ScreenManager.getInstance().execute(this::loadServersForCurrentTab));
            }
        }

        loadServersForCurrentTab();
    }

    private void onHostTabClosed(TabsManager.Tab tab) {
        System.out.println("Requesting deletion of remote host");
        if (tab != null && tab.getData() instanceof RemoteHost host) {
            ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this).addIconItem("Confirm Deletion", "delete.png", () -> {
                instanceManager.removeRemoteHost(host);
                tabs().removeTab(tab.getId());
            }, "Delete Remote Host", ThemeManager.getAccent("danger"));
            showContextMenu(getMouseX(), tabsManager.getY() + 2, builder);
        }
    }

    private void onHostTabRenamed(TabsManager.Tab tab) {
        if (tab != null && tab.getData() instanceof RemoteHost) {
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

                Instance inst = widget.getInstance();
                RemoteHost rh = null;
                if(inst.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(inst.getBackendConfig().type)) {
                    for(RemoteHost h : instanceManager.getRemoteHosts()) {
                        if(inst.getBackendConfig().credentials.getOrDefault("host", "").equals(h.getIp())) {
                            rh = h;
                            break;
                        }
                    }
                }

                RemoteHost finalRh = rh;
                ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this)
                    .addHeaderButton("edit.png", () -> client.setScreen(new ServerConfigurationScreen(this, widget.getInstance(), finalRh, remotelyClient)), "Edit Server's Settings")
                    .addHeaderButton("explorer.png", () -> client.setScreen(new FileExplorerScreen(this, widget.getInstance(), Path.of(widget.getInstance().getPath()), remotelyDir, false)), "Open Server's Folder");

                if (rh == null) {
                    builder.addHeaderButton("map.png", () -> openWorldScreen(widget.getInstance()), "View World Map");
                }

                builder.addHeaderButton("copy.png", () -> duplicateInstance(inst), "Duplicate Server")
                    .addHeaderButton("delete.png", () -> {
                        instanceForDeletion = widget.getInstance();
                        deleteServerPopup.setX((this.width - deleteServerPopup.getWidth())/2);
                        deleteServerPopup.setY((this.height - deleteServerPopup.getHeight())/2);
                        deleteServerPopup.show();
                    }, "Show Deletion Options", ThemeManager.getAccent("danger"));

                if (rh == null) {
                    builder.addIconItem("Customize Icon", "shades.png", () -> customizeIcon(inst), "");
                }
                showContextMenu(widget.getX() + widget.getWidth() + 4, widget.getY() + 24, builder);
            }
        }
    }

    private void duplicateInstance(Instance instance) {
        Instance newInstance = instanceManager.duplicateInstance(instance, instance.getName() + " - Copy");
        if (newInstance != null) {
            new Notification("Server Duplicated", "Server duplicated successfully.", Notification.Type.SUCCESS);
            loadServersForCurrentTab();
        } else {
            new Notification("Duplication Failed", "Could not duplicate server.", Notification.Type.ERROR);
        }
    }

    private void customizeIcon(Instance instance) {
        List<BufferedImage> images = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            try {
                images.add(restudio.rescreen.util.ImageUtils.loadIcon("ic_" + i + ".png"));
            } catch (Exception ignored) {}
        }

        if (images.isEmpty()) {
            new Notification("Error", "No icon assets found.", Notification.Type.ERROR);
            return;
        }

        List<Integer> tints = Arrays.asList(0xFFFFFF, 0xFF6F61, 0x6FCF97, 0x6CC4F1, 0xFFC800, 0x9B51E0, 0xDF3E23, 0xd6f264, 0x7FFBFF);
        IconCustomizerWidget popup = new IconCustomizerWidget("Icon Customizer", images, tints, result -> {
            try {
                File outputFile = new File(instance.getPath(), "icon.png");
                ImageIO.write(result, "png", outputFile);
                loadServersForCurrentTab();
                new Notification("Icon Updated", "Custom icon set.", Notification.Type.SUCCESS);
            } catch (IOException e) {
                new Notification("Error", "Failed to save icon.", Notification.Type.ERROR);
            }
        });

        addDrawableChild(popup);
        popup.show();
    }

    private List<Instance> getCurrentServers() {
        if (tabsManager == null) {
            return instanceManager.getLocalInstances();
        }
        TabsManager.Tab active = tabs().getActiveTab();
        if (active == null) {
            return instanceManager.getLocalInstances();
        }
        int idx = tabs().getActiveTabIndex();
        if (idx <= 0) {
            return instanceManager.getLocalInstances();
        }
        Object data = active.getData();
        if (!(data instanceof RemoteHost host)) {
            return instanceManager.getLocalInstances();
        }
        return instanceManager.getRemoteInstances(host);
    }

    private void openFileExplorer() {
        client.setScreen(new FileExplorerScreen(this, null, remotelyDir, remotelyDir, false));
    }

    public void openWorldScreen(Instance instance) {
        WorldMapScreen mapWidget = new WorldMapScreen(this, instance);
        client.setScreen(mapWidget);
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

        AnimatedButton createBtn = new AnimatedButton.Builder()
            .label(("Server Creation"))
            .onClick(() -> {
                RemoteHost currentHost = (tabs().getActiveTabIndex() > 0 && tabs().getActiveTab() != null) ? (RemoteHost) tabs().getActiveTab().getData() : null;
                client.setScreen(new ServerConfigurationScreen(this, null, currentHost, remotelyClient));
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
        remoteHostDeleteButton = new AnimatedButton.Builder().label(("Delete")).onClick(this::onDeleteRemoteHost).accentType(ThemeManager.getAccent("danger")).build();
        builder.addRow("", false, 20, remoteHostConfirmButton, cancelButton, remoteHostDeleteButton);

        remoteHostPopup = builder.build();
        remoteHostPopup.hide();
        addDrawableChild(remoteHostPopup);
    }

    private void connectRemoteHostAsync(RemoteHost hostInfo, Runnable onSuccess, Runnable onFailure) {
        new Thread(() -> {
            try {
                if (hostInfo.getSshManager().connect()) {
                    ScreenManager.getInstance().execute(onSuccess);
                } else {
                    throw new Exception("Connection failed silently.");
                }
            } catch (Exception e) {
                RebaseLogger.log("Failed to connect to remote host " + hostInfo.name + ": " + e.getMessage());
                ScreenManager.getInstance().execute(() -> {
                    new Notification("Connection Failed", e.getMessage(), Notification.Type.ERROR);
                    if (onFailure != null) {
                        onFailure.run();
                    }
                });
            }
        }).start();
    }

    private void openRemoteHostPopup(boolean isEditing) {
        int activeTabIndex = tabs().getActiveTabIndex();
        if (isEditing && activeTabIndex > 0 && tabs().getActiveTab() != null) {
            RemoteHost host = (RemoteHost) tabs().getTabs().get(activeTabIndex).getData();
            remoteHostConfirmButton.setMessage(("Save"));
            remoteHostDeleteButton.visible = true;

            remoteHostNameInput.setText(host.name);
            remoteHostUserInput.setText(host.user);
            remoteHostIpInput.setText(host.ip);
            remoteHostPortInput.setText(String.valueOf(host.port));
            remoteHostPasswordInput.setText(host.getPassword());
        } else {
            remoteHostConfirmButton.setMessage(("Test & Add"));
            remoteHostDeleteButton.visible = false;
            remoteHostNameInput.setText("");
            remoteHostUserInput.setText("root");
            remoteHostIpInput.setText("");
            remoteHostPortInput.setText("22");
            remoteHostPasswordInput.setText("");
        }

        remoteHostNameInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(remoteHostUserInput));
        remoteHostUserInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(remoteHostIpInput));
        remoteHostIpInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(remoteHostPortInput));
        remoteHostPortInput.addOnEnter((w) -> remoteHostPopup.setFocusedWidget(remoteHostPasswordInput));
        remoteHostPasswordInput.addOnEnter((w) -> onConfirmRemoteHost());

        remoteHostPopup.setX((this.width - remoteHostPopup.getWidth()) / 2);
        remoteHostPopup.setY((this.height - remoteHostPopup.getHeight()) / 2);
        remoteHostPopup.show();
    }

    private void onConfirmRemoteHost() {
        RemoteHost host;
        boolean isEditing = tabs().getActiveTabIndex() > 0 && "Save".equals(remoteHostConfirmButton.getMessage());

        if (isEditing && tabs().getActiveTab() != null) {
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
        host.setPassword(remoteHostPasswordInput.getText());

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
            DesktopLayout remoteLayout = new DesktopLayout();
            remoteLayout.setOnReorder(() -> saveServerOrder(c, host));
            c.layout(remoteLayout).backgroundDrawing(false).enableSelecting(true).disableScissorRegion(true);
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
        remotelyClient.openInstanceInTerminal(this, info);
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
        client.setScreen(new FileExplorerScreen(this, null, remotelyDir, remotelyDir, true));
    }

    private void openModpackInstallation() {
        RemoteHost currentHost = (tabs().getActiveTabIndex() > 0 && tabs().getActiveTab() != null) ? (RemoteHost) tabs().getActiveTab().getData() : null;
        client.setScreen(new ResourceBrowserScreen(this, null, ResourceType.MODPACK, true, currentHost));
    }

    @Override
    public void removed() {
        remotelyClient.saveTabIndex(tabs().getActiveTabIndex());
        super.removed();
    }

    private BufferedImage getServerIcon(Instance server) {
        try {
            boolean isRemote = server.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(server.getBackendConfig().type);
            if (!isRemote) {
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
            case "quilt" -> quilt;
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
        reloadInstancesSmartly();
        loadServersForCurrentTab();
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        tabs().setPosition(width - tabs().getWidth(), height - 28 + 4);
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (activeContainer != null && activeContainer.getLayout() instanceof DesktopLayout layout) {
            int accentColor = ThemeManager.getDefaultAccent().getAccentColor();
            int fill = ThemeManager.getAnimatedColor("selection_fill".hashCode(), layout.isSelecting() ? ((accentColor & 0x00FFFFFF) | 0x44000000) : 0x00000000);
            int border = ThemeManager.getAnimatedColor("selection_border".hashCode(), layout.isSelecting() ? ((accentColor & 0x00FFFFFF) | 0xAA000000) : 0x00000000);

            if (layout.isSelecting()) {
                double[] box = layout.getSelectionBox();
                if (box != null) {
                    bx1 = (int) box[0];
                    by1 = (int) box[1];
                    bx2 = (int) box[2];
                    by2 = (int) box[3];
                }
            }
            context.fill(bx1, by1, bx2, by2, fill);
            context.fillBorder(bx1, by1, bx2, by2, 1, border);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            reloadInstancesSmartly();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        remotelyClient.getHost().openParentScreen(this, parent);
    }
}
