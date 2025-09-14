package redxax.oxy.remotely.servers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import redxax.oxy.remotely.ui.widgets.DesktopIconWidget;
import restudio.rescreen.platform.IDrawContext;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.config.SettingsScreen;
import redxax.oxy.remotely.explorer.FileExplorerScreen;
import redxax.oxy.remotely.resources.ResourceManagerScreen;
import redxax.oxy.remotely.terminal.MultiTerminalScreen;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.DesktopLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.config.SettingsScreen.defineSettings;
import static redxax.oxy.remotely.config.SettingsScreen.settings;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;
import static restudio.rescreen.util.SoundUtils.playSound;

public class ServerManagerScreen extends ReScreen {
    private final RemotelyClient remotelyClient;
    private static List<ServerInfo> localServers;
    private static final List<RemoteHostInfo> remoteHosts = new ArrayList<>();
    private static final Map<RemoteHostInfo, List<ServerInfo>> remoteServers = new HashMap<>();
    private int serverIndexForDeletion = -1;
    private PopupWidget addServerPopup;
    private PopupWidget deleteServerPopup;
    private PopupWidget remoteHostPopup;
    private ContextMenuWidget contextMenu;
    private TextInputWidget remoteHostNameInput;
    private TextInputWidget remoteHostUserInput;
    private TextInputWidget remoteHostIpInput;
    private TextInputWidget remoteHostPortInput;
    private TextInputWidget remoteHostPasswordInput;
    private AnimatedButton remoteHostConfirmButton;
    private AnimatedButton remoteHostDeleteButton;
    private final Screen parent;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static BufferedImage unknown, serverIcon, paper, vanilla, fabric, forge, neoforge, waterfall, velocity, leaf, quilt;

    public static List<RemoteHostInfo> getRemoteHosts() {
        return remoteHosts;
    }

    public static int getActiveTabIndex() {
        return RemotelyClient.INSTANCE.getSavedTabIndex();
    }

    public ServerManagerScreen(Screen parent, RemotelyClient remotelyClient, List<ServerInfo> servers) {
        super();
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        ServerManagerScreen.localServers = servers;
    }

    @Override
    public void init() {
        if (localServers.isEmpty()) {
            loadSavedServers();
        }
        loadSavedRemoteHosts();
        loadIcons();
        createPopups();
        defineSettings();
        scanForUnknownServers();

        int taskbarHeight = 28;
        header().position(HeaderBuilder.Position.BOTTOM).size(taskbarHeight)
                .addLeft("terminal.png", () -> client.setScreen(new MultiTerminalScreen(this, remotelyClient)), "Terminal")
                .addLeft("explorer.png", this::openFileExplorer, "File Explorer")
                .addLeft("minibrowser.png", this::openBrowser, "Web Browser")
                .addLeft("remotely.png", () -> client.setScreen(new SettingsScreen("config", this, remotelyDir.toString(), settings)), "Settings")
                .build();

        tabs().builder()
                .position(width / 2, height - taskbarHeight + 4)
                .size(width / 2 - 5, 18)
                .rightToLeft(true)
                .allowAdd(true)
                .allowRename(false).allowReorder(false).allowClose(false)
                .onPlusButtonClicked(() -> openRemoteHostPopup(false))
                .onTabSelected(this::onHostTabSelected)
                .onTabClosed(tab -> saveRemoteHosts())
                .onTabsReordered(tabs -> {
                    List<RemoteHostInfo> reorderedHosts = new ArrayList<>();
                    for (int i = 1; i < tabs.size(); i++) {
                        reorderedHosts.add((RemoteHostInfo) tabs.get(i).getData());
                    }
                    remoteHosts.clear();
                    remoteHosts.addAll(reorderedHosts);
                    saveRemoteHosts();
                })
                .build();

        contextMenu = new ContextMenuWidget.Builder(this).build();
        addDrawableChild(contextMenu);

        Container desktopContainer = createContainer("desktop", 0, 0, width, height - taskbarHeight);
        desktopContainer.layout(new DesktopLayout()).backgroundDrawing(false).enableSelecting(true);

        setActiveContainer(desktopContainer);
        populateHostTabs();
        super.init();
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
        } catch (Exception e) {
            new Notification("Failed to load icons: " + e.getMessage(), Notification.Type.ERROR);
        }
    }

    private void populateHostTabs() {
        tabs().addTab("Local", activeContainer).setData(null);
        for (RemoteHostInfo host : remoteHosts) {
            Container c = createContainer("desktop_remote_" + host.name, 0, 0, width, height - 28);
            c.layout(new DesktopLayout()).backgroundDrawing(false).enableSelecting(true);
            tabs().addTab(host.name, c).setData(host);
        }
        int savedIndex = remotelyClient.getSavedTabIndex();
        tabs().setActiveTab(Math.min(savedIndex, tabs().getTabs().size() - 1));
        loadServersForCurrentTab();
    }

    private void loadServersForCurrentTab() {
        activeContainer.clearWidgets();
        List<ServerInfo> currentServers = getCurrentServers();
        for (ServerInfo server : currentServers) {
            addServerWidget(server, false);
        }
        addServerWidget(null, true);
        activeContainer.updateWidgetPositions();
    }

    private void addServerWidget(ServerInfo info, boolean isCreate) {
        DesktopIconWidget widget = new DesktopIconWidget.Builder(info, isCreate, isCreate ? serverIcon : getServerIcon(info)).onClick(this::onDesktopIconClick).build();
        activeContainer.addWidget(widget);
    }

    private void onHostTabSelected(TabsManager.Tab tab) {
        remotelyClient.saveTabIndex(tabs().getActiveTabIndex());
        if (tab.getContainer().getWidgets().isEmpty()) {
            loadServersForCurrentTab();
        }
        if (tabs().getActiveTabIndex() > 0) {
            RemoteHostInfo host = (RemoteHostInfo) tab.getData();
            if (!host.isConnected && !host.isConnecting) {
                connectRemoteHostAsync(host);
            }
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
                openServerScreen(widget.getServerInfo());
            }
        } else if (button == 1) {
            if (!widget.isCreateButton()) {
                activeContainer.clearSelection();
                activeContainer.addSelectedWidget(widget);
                contextMenu = new ContextMenuWidget.Builder(this)
                        .addHeaderButton("edit.png", () -> client.setScreen(new SettingsScreen("editServer", this, widget.getServerInfo().path, settings, widget.getServerInfo())), "Edit Server's Settings")
                        .addHeaderButton("explorer.png", () -> client.setScreen(new FileExplorerScreen(this, widget.getServerInfo(), false)), "Open Server's Folder")
                        .addHeaderButton("delete.png", () -> {
                            serverIndexForDeletion = getCurrentServers().indexOf(widget.getServerInfo());
                            deleteServerPopup.setX((this.width - deleteServerPopup.getWidth())/2);
                            deleteServerPopup.setY((this.height - deleteServerPopup.getHeight())/2);
                            deleteServerPopup.show();
                        }, "Show Deletion Options")
                        .build();
                contextMenu.show(widget.getX() + widget.getWidth() + 4, widget.getY() + 24);
            }
        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        addServerPopup.render(context, mouseX, mouseY, delta);
        deleteServerPopup.render(context, mouseX, mouseY, delta);
        remoteHostPopup.render(context, mouseX, mouseY, delta);
        contextMenu.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (remoteHostPopup.mouseClicked(mouseX, mouseY, button)) return true;
        if (addServerPopup.mouseClicked(mouseX, mouseY, button)) return true;
        if (deleteServerPopup.mouseClicked(mouseX, mouseY, button)) return true;
        if (contextMenu.mouseClicked(mouseX, mouseY, button)) return true;

        if (tabs().getActiveTab().getContainer() != activeContainer) {
            tabs().setActiveTab(activeContainer);
        }

        int activeTabIndex = tabs().getActiveTabIndex();
        if (button == 1 && activeTabIndex > 0) {
            List<TabsManager.Tab> tabs = tabs().getTabs();
            for (int i = 1; i < tabs.size(); i++) {
                if (tabs.get(i).getWidget().isMouseOver(mouseX, mouseY)) {
                    tabs().setActiveTab(i);
                    openRemoteHostPopup(true);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (remoteHostPopup.mouseReleased(mouseX, mouseY, button)) return true;
        if (addServerPopup.mouseReleased(mouseX, mouseY, button)) return true;
        if (deleteServerPopup.mouseReleased(mouseX, mouseY, button)) return true;
        if (contextMenu.mouseReleased(mouseX, mouseY, button)) return true;

        boolean wasDragging = activeContainer.mouseReleased(mouseX, mouseY, button);
        if (wasDragging) {
            List<ServerInfo> reorderedServers = new ArrayList<>();
            for (AnimatedWidget w : activeContainer.getWidgets()) {
                if (w instanceof DesktopIconWidget dtw && !dtw.isCreateButton()) {
                    reorderedServers.add(dtw.getServerInfo());
                }
            }
            if (tabs().getActiveTabIndex() == 0) {
                localServers = reorderedServers;
                saveServers();
            } else {
                RemoteHostInfo host = (RemoteHostInfo) tabs().getActiveTab().getData();
                remoteServers.put(host, reorderedServers);
                saveRemoteHosts();
            }
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (remoteHostPopup.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) return true;
        if (addServerPopup.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) return true;
        if (deleteServerPopup.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) return true;
        if (contextMenu.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (remoteHostPopup.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (addServerPopup.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (deleteServerPopup.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (contextMenu.keyPressed(keyCode, scanCode, modifiers)) return true;

        if (keyCode == GLFW.GLFW_KEY_S && modifiers == GLFW.GLFW_MOD_CONTROL) {
            client.setScreen(new SettingsScreen("config", this, "", null));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (remoteHostPopup.charTyped(chr, modifiers)) return true;
        if (addServerPopup.charTyped(chr, modifiers)) return true;
        if (deleteServerPopup.charTyped(chr, modifiers)) return true;
        if (contextMenu.charTyped(chr, modifiers)) return true;

        return super.charTyped(chr, modifiers);
    }
    private List<ServerInfo> getCurrentServers() {
        if (tabsManager == null || tabs().getActiveTabIndex() == 0) {
            return localServers;
        }
        if (tabs().getActiveTabIndex() > 0) {
            RemoteHostInfo host = (RemoteHostInfo) tabs().getActiveTab().getData();
            return remoteServers.getOrDefault(host, new ArrayList<>());
        }
        return new ArrayList<>();
    }

    private void openFileExplorer() {
        int activeTabIndex = tabs().getActiveTabIndex();
        if (activeTabIndex == 0) {
            client.setScreen(new FileExplorerScreen(this, new ServerInfo(remotelyDir.toString()), false));
        } else {
            RemoteHostInfo host = (RemoteHostInfo) tabs().getTabs().get(activeTabIndex).getData();
            client.setScreen(new FileExplorerScreen(this, host));
        }
    }

    private void openBrowser() {
        client.setScreen(new BrowserScreen(this, "www.google.com"));
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
                    client.setScreen(new SettingsScreen("createServer", this, Path.of(String.valueOf(remotelyDir), "servers").toString(), settings));
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
    }

    private void createDeleteServerPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Are You Sure?")
                .size(260, 140)
                .onClose(() -> deleteServerPopup.hide());

        AnimatedButton deleteTrashBtn = new AnimatedButton.Builder()
                .label(("Delete The Server"))
                .onClick(() -> {
                    playSound(Sound.DELETE);
                    deleteServerTrash(serverIndexForDeletion);
                    deleteServerPopup.hide();
                })
                .build();

        AnimatedButton deleteRemoveBtn = new AnimatedButton.Builder()
                .label(("Remove From List"))
                .onClick(() -> {
                    playSound(Sound.DELETE);
                    deleteServerRemove(serverIndexForDeletion);
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
        builder.addRow("", true, 27, deleteRemoveBtn);
        builder.addRow("", true, 27, cancelBtn);

        deleteServerPopup = builder.build();
        deleteServerPopup.hide();
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
    }
    private void connectRemoteHostAsync(RemoteHostInfo hostInfo) {
        hostInfo.isConnecting = true;
        new Thread(() -> {
            SSHManager sshManager = remotelyClient.getSSHManagerForHost(hostInfo);
            if (sshManager != null && sshManager.isSSH()) {
                hostInfo.isConnected = true;
                hostInfo.connectionError = null;
            } else {
                hostInfo.isConnected = false;
                hostInfo.connectionError = "Failed to connect.";
            }
            hostInfo.isConnecting = false;
        }).start();
    }

    private void openRemoteHostPopup(boolean isEditing) {
        int activeTabIndex = tabs().getActiveTabIndex();
        if (isEditing) {
            RemoteHostInfo host = (RemoteHostInfo) tabs().getTabs().get(activeTabIndex).getData();
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
        String name = remoteHostNameInput.getText().trim();
        String user = remoteHostUserInput.getText().trim();
        String ip = remoteHostIpInput.getText().trim();
        String portStr = remoteHostPortInput.getText().trim();
        String password = remoteHostPasswordInput.getText();

        if (name.isEmpty() || ip.isEmpty() || portStr.isEmpty()) {
            new Notification("All fields must be filled!", Notification.Type.ERROR);
            return;
        }

        boolean isEditing = remoteHostConfirmButton.getMessage().equals("Save");

        if (!isEditing && !testSSHConnection(user, ip, portStr, password)) {
            new Notification("Invalid details or Connection Failed", Notification.Type.ERROR);
            return;
        }

        RemoteHostInfo rh;
        if (isEditing) {
            rh = (RemoteHostInfo) tabs().getActiveTab().getData();
            rh.name = name;
            rh.user = user;
            rh.ip = ip;
            try {
                rh.port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                rh.port = 22;
            }
            rh.password = password;
            tabs().getActiveTab().setName(name);
            saveRemoteHosts();
            closeRemoteHostPopup();
        } else {
            rh = new RemoteHostInfo();
            rh.name = name;
            rh.user = user;
            rh.ip = ip;
            try {
                rh.port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                rh.port = 22;
            }
            rh.password = password;
            remoteHosts.add(rh);
            remoteServers.put(rh, new ArrayList<>());
            saveRemoteHosts();

            Container c = createContainer("desktop_remote_" + rh.name, 0, 0, width, height - 28);
            c.layout(new DesktopLayout()).backgroundDrawing(false).enableSelecting(true);
            TabsManager.Tab newTab = tabs().addTab(rh.name, c);
            newTab.setData(rh);
            tabs().setActiveTab(tabs().getTabs().size() - 1);

            closeRemoteHostPopup();
        }
    }

    private void onDeleteRemoteHost() {
        if (tabs().getActiveTabIndex() > 0) {
            RemoteHostInfo hostToRemove = (RemoteHostInfo) tabs().getActiveTab().getData();
            remoteHosts.remove(hostToRemove);
            remoteServers.remove(hostToRemove);
            tabs().removeTab(tabs().getActiveTabIndex());
            saveRemoteHosts();
            tabs().setActiveTab(0);
            closeRemoteHostPopup();
        }
    }

    private void openServerScreen(ServerInfo info) {
        client.setScreen(new MultiTerminalScreen(this, remotelyClient, info));
    }

    public static void openServerScreen(String path) {
        List<ServerInfo> allServers = new ArrayList<>(localServers);
        remoteServers.values().forEach(allServers::addAll);

        for (ServerInfo info : allServers) {
            if (info.path.equals(path)) {
                ScreenManager.getInstance().setScreen(new MultiTerminalScreen(ScreenManager.currentScreen, RemotelyClient.INSTANCE, info));
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
        if (tabs().getActiveTabIndex() == 0) {
            client.setScreen(new FileExplorerScreen(this, new ServerInfo(remotelyDir.toString()), true));
        } else {
            RemoteHostInfo remoteHost = (RemoteHostInfo) tabs().getActiveTab().getData();
            client.setScreen(new FileExplorerScreen(this, remoteHost));
        }
    }

    private void openModpackInstallation() {
        try {
            ServerInfo serverInfo = new ServerInfo("modpack");
            serverInfo.name = "Modpack Server";
            serverInfo.type = "modpack";
            serverInfo.version = "latest";
            if (tabs().getActiveTabIndex() > 0) {
                RemoteHostInfo remoteHost = (RemoteHostInfo) tabs().getActiveTab().getData();
                serverInfo.isRemote = true;
                serverInfo.remoteHost = remoteHost;
                serverInfo.path = remoteHost.getHomeDirectory() + "remotely/servers/" + serverInfo.name;
                SSHManager sshManager = remotelyClient.getSSHManagerForHost(remoteHost);
                if (sshManager == null || !sshManager.isSSH()) {
                    new Notification("Could not connect to remote host for modpack installation.", Notification.Type.ERROR);
                    return;
                }
            } else {
                serverInfo.isRemote = false;
                serverInfo.remoteHost = null;
                serverInfo.path = remotelyDir + "/servers/" + serverInfo.name;
            }
            ResourceManagerScreen modManagerScreen = new ResourceManagerScreen(this, serverInfo);
            client.setScreen(modManagerScreen);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void importServerJar(Path jarPath, String folderName) {
        Path parentDir = jarPath.getParent();
        if (parentDir != null) {
            ServerInfo newInfo = new ServerInfo(parentDir.toString());
            newInfo.name = folderName;
            newInfo.path = parentDir.toString();
            newInfo.type = "imported";
            newInfo.version = "unknown";
            newInfo.isRunning = false;
            if (tabs().getActiveTabIndex() == 0) {
                newInfo.isRemote = false;
                newInfo.remoteHost = null;
                localServers.add(newInfo);
                saveServers();
            } else {
                RemoteHostInfo host = (RemoteHostInfo) tabs().getActiveTab().getData();
                newInfo.isRemote = true;
                newInfo.remoteHost = host;
                remoteServers.get(host).add(newInfo);
                saveRemoteHosts();
            }
            loadServersForCurrentTab();
        }
    }

    @Override
    public void close() {
        remotelyClient.saveTabIndex(tabs().getActiveTabIndex());
        super.close();
    }

    private boolean testSSHConnection(String user, String ip, String portStr, String password) {
        int port = 22;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ignored) {}

        Session session = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, ip, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);
            return session.isConnected();
        } catch (JSchException e) {
            return false;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    public static void saveRemoteHosts() {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "assets/remotely", "servers");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve("remotehosts.json");

            JsonArray hostsArray = new JsonArray();
            for (RemoteHostInfo rh : remoteHosts) {
                JsonObject hostObject = new JsonObject();
                hostObject.addProperty("name", rh.name);
                hostObject.addProperty("user", rh.user);
                hostObject.addProperty("ip", rh.ip);
                hostObject.addProperty("port", rh.port);
                hostObject.addProperty("password", rh.password);

                JsonArray serversArray = new JsonArray();
                List<ServerInfo> servers = remoteServers.getOrDefault(rh, Collections.emptyList());
                for (ServerInfo info : servers) {
                    JsonObject serverObject = new JsonObject();
                    serverObject.addProperty("name", info.name);
                    serverObject.addProperty("path", info.path);
                    serverObject.addProperty("type", info.type);
                    serverObject.addProperty("version", info.version);
                    serverObject.addProperty("isRunning", info.isRunning);
                    serverObject.addProperty("isRemote", info.isRemote);
                    serversArray.add(serverObject);
                }
                hostObject.add("servers", serversArray);
                hostsArray.add(hostObject);
            }
            Files.writeString(file, GSON.toJson(hostsArray), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {}
    }

    private void loadSavedRemoteHosts() {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "assets/remotely", "servers");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve("remotehosts.json");
            if (!Files.exists(file)) return;

            String json = Files.readString(file);
            JsonArray hostsArray = JsonParser.parseString(json).getAsJsonArray();

            for (JsonElement hostElement : hostsArray) {
                JsonObject hostObject = hostElement.getAsJsonObject();
                RemoteHostInfo rh = new RemoteHostInfo();
                rh.name = hostObject.get("name").getAsString();
                rh.user = hostObject.get("user").getAsString();
                rh.ip = hostObject.get("ip").getAsString();
                rh.port = hostObject.get("port").getAsInt();
                rh.password = hostObject.get("password").getAsString();

                boolean exists = remoteHosts.stream().anyMatch(h -> h.ip.equals(rh.ip) && h.port == rh.port && h.user.equals(rh.user));
                if (!exists) {
                    remoteHosts.add(rh);

                    List<ServerInfo> servers = new ArrayList<>();
                    JsonArray serversArray = hostObject.getAsJsonArray("servers");
                    for (JsonElement serverElement : serversArray) {
                        JsonObject serverObject = serverElement.getAsJsonObject();
                        ServerInfo info = new ServerInfo(serverObject.get("path").getAsString());
                        info.name = serverObject.get("name").getAsString();
                        info.type = serverObject.get("type").getAsString();
                        info.version = serverObject.get("version").getAsString();
                        info.isRunning = serverObject.get("isRunning").getAsBoolean();
                        info.isRemote = serverObject.get("isRemote").getAsBoolean();
                        info.remoteHost = rh;
                        servers.add(info);
                    }
                    remoteServers.put(rh, servers);
                }
            }
        } catch (Exception ignored) {}
    }


    private void scanForUnknownServers() {
        Path serversDir = Paths.get(remotelyDir + "/servers/").toAbsolutePath().normalize();
        if (!Files.exists(serversDir)) {
            try {
                Files.createDirectories(serversDir);
            } catch (IOException e) {
                new Notification("Failed To Create Server Directory.",  Notification.Type.ERROR);
            }
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(serversDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    Path serverJarPath = entry.resolve("server.jar");
                    if (Files.exists(serverJarPath)) {
                        String folderName = entry.getFileName().toString();
                        String fullPath = entry.toAbsolutePath().toString().replace("/", "\\");
                        Path normalizedPath = Paths.get(fullPath).normalize();
                        if (!isServerRegistered(normalizedPath.toString())) {
                            String type = "imported";
                            String version = "unknown";
                            try (ZipFile zip = new ZipFile(serverJarPath.toFile())) {
                                Enumeration<? extends ZipEntry> entriesZip = zip.entries();
                                boolean hasPaper = false;
                                boolean hasWaterfall = false;
                                boolean hasVelocity = false;
                                boolean hasFabric = false;
                                boolean hasVanilla = false;
                                boolean hasForge = false;
                                boolean hasNeoforge = false;
                                boolean hasLeaf = false;
                                boolean hasQuilt = false;
                                while (entriesZip.hasMoreElements()) {
                                    ZipEntry ze = entriesZip.nextElement();
                                    String name = ze.getName();
                                    if (name.startsWith("io/papermc/")) {
                                        hasPaper = true;
                                    }
                                    if (name.startsWith("io/github/waterfallmc/")) {
                                        hasWaterfall = true;
                                    }
                                    if (name.startsWith("com/velocitypowered/")) {
                                        hasVelocity = true;
                                    }
                                    if (name.equals("install.properties")) {
                                        hasFabric = true;
                                    }
                                    if (name.startsWith("net/minecraftforge/")) {
                                        hasForge = true;
                                    }
                                    if (name.startsWith("dev/mcvapi/")) {
                                        hasNeoforge = true;
                                    }
                                    if (name.startsWith("cn/dreeam/")) {
                                        hasLeaf = true;
                                    }
                                    if (name.equals("lang/installer.properties")) {
                                        hasQuilt = true;
                                    }
                                    if (name.startsWith("net/minecraft/")) {
                                        hasVanilla = true;
                                    }
                                }
                                if (hasPaper) {
                                    type = "Paper";
                                    ZipEntry versionJson = zip.getEntry("version.json");
                                    if (versionJson != null) {
                                        try (InputStream is = zip.getInputStream(versionJson);
                                             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                                            version = obj.get("id").getAsString();
                                        }
                                    }
                                } else if (hasVelocity) {
                                    type = "Velocity";
                                } else if (hasWaterfall) {
                                    type = "Waterfall";
                                } else if (hasFabric) {
                                    type = "Fabric";
                                    ZipEntry installProps = zip.getEntry("install.properties");
                                    if (installProps != null) {
                                        Properties props = new Properties();
                                        try (InputStream is = zip.getInputStream(installProps)) {
                                            props.load(is);
                                            version = props.getProperty("game-version", "unknown");
                                        }
                                    }
                                } else if (hasForge) {
                                    type = "Forge";
                                    ZipEntry versionJson = zip.getEntry("version.json");
                                    if (versionJson != null) {
                                        try (InputStream is = zip.getInputStream(versionJson);
                                             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                                            version = obj.get("id").getAsString();
                                        }
                                    }
                                } else if (hasNeoforge) {
                                    type = "Neoforge";
                                    ZipEntry versionJson = zip.getEntry("metadata.json");
                                    if (versionJson != null) {
                                        try (InputStream is = zip.getInputStream(versionJson);
                                             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                                            version = obj.get("version").getAsString();
                                        }
                                    }
                                } else if (hasLeaf) {
                                    type = "Leaf";
                                    ZipEntry versionJson = zip.getEntry("version.json");
                                    if (versionJson != null) {
                                        try (InputStream is = zip.getInputStream(versionJson);
                                             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                                            version = obj.get("id").getAsString();
                                        }
                                    }
                                } else if (hasQuilt) {
                                    type = "Quilt";
                                    ZipEntry installerProps = zip.getEntry("lang/installer.properties");
                                    if (installerProps != null) {
                                        Properties props = new Properties();
                                        try (InputStream is = zip.getInputStream(installerProps)) {
                                            props.load(is);
                                        }
                                    }
                                } else if (hasVanilla) {
                                    type = "Vanilla";
                                    ZipEntry versionJson = zip.getEntry("version.json");
                                    if (versionJson != null) {
                                        try (InputStream is = zip.getInputStream(versionJson);
                                             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                                            version = obj.get("id").getAsString();
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            if (scanServers) addServer(folderName, normalizedPath.toString(), type, version, false, null);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isServerRegistered(String normalizedPath) {
        for (ServerInfo server : localServers) {
            String serverPath = Paths.get(server.path).toAbsolutePath().normalize().toString().replace("/", "\\");
            if (serverPath.equalsIgnoreCase(normalizedPath)) {
                return true;
            }
        }
        for (List<ServerInfo> serverList : remoteServers.values()) {
            for (ServerInfo server : serverList) {
                if (server.path.replace("/", "\\").equalsIgnoreCase(normalizedPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void addServer(String name, String path, String type, String version, boolean isRemote, RemoteHostInfo host) {
        ServerInfo newServer = new ServerInfo(path);
        newServer.name = name;
        newServer.path = path;
        newServer.type = type;
        newServer.version = version;
        newServer.isRunning = false;
        newServer.isRemote = isRemote;
        if (isRemote) {
            newServer.remoteHost = host;
            remoteServers.computeIfAbsent(host, k -> new ArrayList<>()).add(newServer);
            saveRemoteHosts();
        } else {
            localServers.add(newServer);
            saveServers();
        }
    }

    public static void saveServers() {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "assets/remotely", "servers");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve("servers.json");
            Files.writeString(file, GSON.toJson(localServers), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {}
    }

    private void loadSavedServers() {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "remotely", "servers");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve("servers.json");
            if (!Files.exists(file)) return;
            String json = Files.readString(file);
            ServerInfo[] loaded = GSON.fromJson(json, ServerInfo[].class);
            if(loaded != null) {
                localServers.addAll(Arrays.asList(loaded));
            }
        } catch (IOException ignored) {}
    }

    private BufferedImage getServerIcon(ServerInfo server) {
        try {
            if (!server.isRemote) {
                File iconFile = new File(server.path, "icon.png");
                if (iconFile.exists() && iconFile.isFile()) {
                    return ImageIO.read(iconFile);
                }
            }
        } catch (IOException e) {
            devPrint("Failed to load server icon: " + e.getMessage());
        }
        return switch (server.type.toLowerCase()) {
            case "vanilla" -> vanilla;
            case "fabric" -> fabric;
            case "forge" -> forge;
            case "paper" -> paper;
            case "neoforge" -> neoforge;
            case "velocity" -> velocity;
            case "waterfall" -> waterfall;
            case "leaf" -> leaf;
            case "quilt" -> quilt;
            default -> unknown;
        };
    }

    private void deleteServerTrash(int index) {
        List<ServerInfo> currentServers = getCurrentServers();
        if (index >= 0 && index < currentServers.size()) {
            ServerInfo s = currentServers.get(index);
            String dateOfDeletion = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());

            if (s.isRemote && s.remoteHost != null) {
                SSHManager ssh = s.remoteHost.getSSHManager();
                if (ssh != null && ssh.isSSH()) {
                    String trashDir = s.remoteHost.getHomeDirectory() + ".remotely/trash/";
                    String newRemotePath = trashDir + s.name + "-" + dateOfDeletion;
                    try {
                        ssh.runRemoteCommand("mkdir -p " + trashDir);
                        ssh.renameRemoteFile(s.path, newRemotePath);
                        new Notification("Server Moved To Trash.", Notification.Type.INFO);
                        currentServers.remove(index);
                    } catch (Exception e) {
                        new Notification("Failed To Trash Remote Server.", Notification.Type.ERROR);
                    }
                }
            } else {
                File trashDir = new File(System.getProperty("user.dir") + File.separator + "assets/remotely" + File.separator + "trash");
                if (!trashDir.exists()) trashDir.mkdirs();
                File folderPath = new File(s.path);
                if (folderPath.exists()) {
                    String newLocalName = s.name + "-" + dateOfDeletion;
                    File trashSub = new File(trashDir, newLocalName);
                    if (folderPath.renameTo(trashSub)) {
                        new Notification("Server Moved To Trash.", Notification.Type.INFO);
                        currentServers.remove(index);
                    } else {
                        new Notification("Failed To Move Server To Trash.", Notification.Type.ERROR);
                    }

                }
            }
            saveServers();
            saveRemoteHosts();
            loadServersForCurrentTab();
        }
    }

    private void deleteServerRemove(int index) {
        List<ServerInfo> currentServers = getCurrentServers();
        if (index >= 0 && index < currentServers.size()) {
            currentServers.remove(index);
            saveServers();
            saveRemoteHosts();
            loadServersForCurrentTab();
        }
    }

    @Override
    public void onDisplayed() {
        super.onDisplayed();
        playSound(Sound.SERVERMANAGER);
    }
}