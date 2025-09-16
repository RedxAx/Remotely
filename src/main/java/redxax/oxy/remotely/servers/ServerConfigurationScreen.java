package redxax.oxy.remotely.servers;

import net.minecraft.client.MinecraftClient;
import redxax.oxy.remotely.SSHManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.DoubleSliderWidget;
import restudio.rescreen.ui.widgets.TabSwitchWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static redxax.oxy.remotely.RemotelyClient.INSTANCE;
import static redxax.oxy.remotely.servers.ServerFactory.notification;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static restudio.rescreen.util.SoundUtils.playSound;

public class ServerConfigurationScreen extends ReScreen {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private final Screen parent;
    private final String settingsRoot;
    private final boolean isEditMode;
    private ServerInfo serverInfo;
    private final List<ServerSetting> settings = new ArrayList<>();

    public ServerConfigurationScreen(Screen parent, String settingsRoot, ServerInfo serverInfo) {
        super();
        this.parent = parent;
        this.settingsRoot = settingsRoot;
        this.isEditMode = serverInfo != null;
        this.serverInfo = serverInfo;

        defineSettings();

        if (isEditMode) {
            if (serverInfo.isRemote) {
                loadSettingsRemote(serverInfo);
            } else {
                loadSettingsFromFiles();
            }
            for (ServerSetting s : settings) {
                if (s.key.equalsIgnoreCase("server-name")) s.value = serverInfo.name;
                if (s.key.equalsIgnoreCase("server-type")) s.value = serverInfo.type;
                if (s.key.equalsIgnoreCase("server-version")) s.value = serverInfo.version;
            }
        }
    }

    private void defineSettings() {
        settings.clear();
        settings.add(new ServerSetting.Builder("Server Name", "The name of your server.", "General", "server-name", ServerSetting.Type.TEXT, "My Server").build());
        settings.add(new ServerSetting.Builder("Game Mode", "Select the default game mode for players.", "General", "gamemode", ServerSetting.Type.TAB_SWITCH, "Survival").file("server.properties").options(Arrays.asList("Survival", "Creative", "Adventure")).build());
        settings.add(new ServerSetting.Builder("Difficulty", "Set the difficulty level of the server.", "General", "difficulty", ServerSetting.Type.TAB_SWITCH, "Normal").file("server.properties").options(Arrays.asList("Peaceful", "Easy", "Normal", "Hard")).build());
        settings.add(new ServerSetting.Builder("End User License Agreement", "Do You Agree To Minecraft's EULA?", "General", "eula", ServerSetting.Type.TOGGLE, "true").file("eula.txt").build());
        settings.add(new ServerSetting.Builder("PvP", "Toggle player vs player combat.", "General", "pvp", ServerSetting.Type.TOGGLE, "true").file("server.properties").build());
        settings.add(new ServerSetting.Builder("Hardcore", "Toggle hardcore mode (one life).", "General", "hardcore", ServerSetting.Type.TOGGLE, "false").file("server.properties").build());
        settings.add(new ServerSetting.Builder("Server Type", "Choose the server software type.", "General", "server-type", ServerSetting.Type.SCROLL_SWITCH, "Paper").options(Arrays.asList("Paper", "Leaf", "Vanilla", "Fabric", "Neoforge", "Forge", "Quilt", "Velocity", "Waterfall")).build());
        settings.add(new ServerSetting.Builder("Server Version", "Specify the Minecraft server version to run.", "General", "server-version", ServerSetting.Type.TEXT, mc.getGameVersion()).build());
        settings.add(new ServerSetting.Builder("Max Players", "Max online players limit.", "Advanced", "max-players", ServerSetting.Type.SLIDER, "20").file("server.properties").range(1, 200).build());
        settings.add(new ServerSetting.Builder("MOTD", "Description for the server list.", "Advanced", "motd", ServerSetting.Type.TEXT, mc.getSession().getUsername() + "'s Server").file("server.properties").build());
        settings.add(new ServerSetting.Builder("Seed", "Enter a specific seed (optional).", "Advanced", "level-seed", ServerSetting.Type.TEXT, "").file("server.properties").build());
        settings.add(new ServerSetting.Builder("Spawn Protection", "Set the radius of spawn protection (set 0 to disable).", "Advanced", "spawn-protection", ServerSetting.Type.SLIDER, "16").file("server.properties").range(0, 32).build());
        settings.add(new ServerSetting.Builder("Max Build Height", "Set the maximum height players can build to.", "Advanced", "max-build-height", ServerSetting.Type.SLIDER, "320").file("server.properties").range(0, 2048).build());
        settings.add(new ServerSetting.Builder("Generate Structures", "Toggle whether structures are generated in the world.", "Advanced", "generate-structures", ServerSetting.Type.TOGGLE, "true").file("server.properties").build());
        settings.add(new ServerSetting.Builder("Port", "Set the port number on which the server will run.", "Advanced", "server-port", ServerSetting.Type.TEXT, "25565").file("server.properties").build());
        settings.add(new ServerSetting.Builder("Online Mode", "Authenticate with Minecraft (Secure).", "Advanced", "online-mode", ServerSetting.Type.TOGGLE, "true").file("server.properties").build());
        settings.add(new ServerSetting.Builder("Whitelist", "Enable or disable the server whitelist.", "Advanced", "white-list", ServerSetting.Type.TOGGLE, "false").file("server.properties").build());
        settings.add(new ServerSetting.Builder("Hide Online Players", "Hide online players from the server list.", "Advanced", "hide-online-players", ServerSetting.Type.TOGGLE, "false").file("server.properties").build());
        settings.add(new ServerSetting.Builder("Allow Nether", "Toggle whether the Nether dimension is accessible.", "Advanced", "allow-nether", ServerSetting.Type.TOGGLE, "true").file("server.properties").build());
        settings.add(new ServerSetting.Builder("Allow End", "Toggle whether the End dimension is accessible.", "Advanced", "settings.allow-end", ServerSetting.Type.TOGGLE, "true").file("bukkit.yml").dependency("server-type", "Paper", "Leaf", "Spigot", "Bukkit", "Purpur").build());
        settings.add(new ServerSetting.Builder("Use Custom Java", "Use a custom Java installation (Not recommended).", "Advanced", "usecustomjava", ServerSetting.Type.TOGGLE, "false").build());
        settings.add(new ServerSetting.Builder("Java Version", "Specify the Java version to use.", "Advanced", "launcher.java_version", ServerSetting.Type.TEXT, "").dependency("usecustomjava", "true").build());
        settings.add(new ServerSetting.Builder("View Distance", "Adjust the number of chunks visible to players.", "Performance", "view-distance", ServerSetting.Type.SLIDER, "8").file("server.properties").range(1, 64).build());
        settings.add(new ServerSetting.Builder("Simulation Distance", "Set the simulation distance (server tick radius).", "Performance", "simulation-distance", ServerSetting.Type.SLIDER, "8").file("server.properties").range(1, 64).build());
        settings.add(new ServerSetting.Builder("Memory", "Set the maximum memory allocation for the server.", "Performance", "memory", ServerSetting.Type.TEXT, "4G").build());
        settings.add(new ServerSetting.Builder("Aikars Flags", "Custom flags that highly optimizes server performance.", "Performance", "aikars_flags", ServerSetting.Type.TOGGLE, "true").build());
    }

    @Override
    public void init() {
        super.init();
        header().position(HeaderBuilder.Position.TOP).size(30)
                .addLeft("close.png", this::close, "Cancel")
                .addRight("create.png", this::createServer, isEditMode ? "Apply Changes" : "Create Server")
                .build();

        Set<String> tabNames = new LinkedHashSet<>();
        for (ServerSetting s : settings) {
            tabNames.add(s.tab);
        }

        tabs().builder().allowAdd(false).allowClose(false).allowReorder(false).allowRename(false)
                .position(5, 36)
                .size(width - 10, 18)
                .onTabSelected(tab -> { if(tab != null) setActiveContainer(tab.getContainer()); })
                .build();

        for(String tabName : tabNames) {
            addCategory(tabName);
        }

        if (!tabs().getTabs().isEmpty()) {
            tabs().setActiveTab(0);
        }
    }

    private void addCategory(String name) {
        Container container = createContainer(name.toLowerCase(), 5, 60, width - 10, height - 5 - 60);
        container.layout(new ManagedLayout()).columns(1).padding(5).columns(2).scrolling(true);
        tabs().addTab(name, container);
        populateCategory(container, name);
    }

    private void populateCategory(Container container, String name) {
        container.clearWidgets();

        Setting.Builder settingBuilder = new Setting.Builder(name + " Settings");

        for(ServerSetting s : settings) {
            if(s.tab.equals(name) && dependencySatisfied(s)) {
                settingBuilder.addRow(s.name, true, 30, createWidgetForSetting(s));
            }
        }
        container.addWidget(settingBuilder.build());
        container.updateWidgetPositions();
    }

    private Widget createWidgetForSetting(ServerSetting s) {
        switch (s.type) {
            case TOGGLE:
                return new ToggleWidget.Builder()
                        .toggled(Boolean.parseBoolean(s.value))
                        .onChange(toggled -> {
                            s.value = String.valueOf(toggled);
                            refreshDependencies(s.key);
                        }).build();
            case SLIDER:
                AtomicReference<DoubleSliderWidget> sliderRef = new AtomicReference<>();
                double initialValue = (double) (s.getIntValue() - s.min) / (s.max - s.min);
                DoubleSliderWidget slider = new DoubleSliderWidget.Builder()
                        .value(initialValue)
                        .onChange(() -> {
                            double val = sliderRef.get().getValue();
                            int newValue = (int) Math.round(s.min + val * (s.max - s.min));
                            s.value = String.valueOf(newValue);
                            sliderRef.get().label = s.value;
                        })
                        .label(s.value)
                        .build();
                sliderRef.set(slider);
                return slider;
            case SCROLL_SWITCH:
            case TAB_SWITCH:
                return new TabSwitchWidget.Builder()
                        .options(s.options)
                        .currentIndex(s.getSelectedIndex())
                        .onChange(newIndex -> {
                            s.setOption(newIndex);
                            refreshDependencies(s.key);
                        }).build();
            case TEXT:
                return new TextInputWidget.Builder()
                        .text(s.value)
                        .onChange(newText -> s.value = newText)
                        .build();
        }
        return new TextInputWidget.Builder().text("UNIMPLEMENTED").build();
    }

    private void refreshDependencies(String changedKey) {
        for (ServerSetting setting : settings) {
            if (setting.dependencies.containsKey(changedKey)) {
                refreshTab(setting.tab);
            }
        }
    }

    private void refreshTab(String tabName) {
        for (TabsManager.Tab tab : tabsManager.getTabs()) {
            if (tab.getName().equalsIgnoreCase(tabName)) {
                populateCategory(tab.getContainer(), tabName);
                break;
            }
        }
    }

    private boolean dependencySatisfied(ServerSetting s) {
        if (s.dependencies == null || s.dependencies.isEmpty()) return true;
        for (Map.Entry<String, List<String>> entry : s.dependencies.entrySet()) {
            String depKey = entry.getKey();
            List<String> depValues = entry.getValue();
            boolean found = false;
            for (ServerSetting setting : settings) {
                if (setting.key.equals(depKey) && depValues.contains(setting.value)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private void loadSettingsFromFiles() {
        for (ServerSetting s : settings) {
            if (!s.file.equals("none")) {
                try {
                    Path filePath = Paths.get(settingsRoot, s.file);
                    if (Files.exists(filePath)) {
                        List<String> lines = Files.readAllLines(filePath);
                        for (String line : lines) {
                            if (line.startsWith(s.key + "=")) {
                                String val = line.substring((s.key + "=").length()).trim();
                                if (!val.isEmpty()) s.value = val;
                            }
                        }
                    }
                } catch (Exception e) {
                    devPrint("Error loading setting " + s.key + ": " + e.getMessage());
                }
            }
        }
    }

    private void loadSettingsRemote(ServerInfo serverInfo) {
        if (serverInfo == null || !serverInfo.isRemote || serverInfo.remoteHost == null) return;
        try {
            RemoteHostInfo rh = serverInfo.remoteHost;
            SSHManager ssh = INSTANCE.getSSHManagerForHost(serverInfo.remoteHost);
            if (!ssh.isSFTPConnected()) {
                ssh.connectToRemoteHost(rh.getUser(), rh.getIp(), rh.getPort(), rh.getPassword());
                while (!ssh.isSFTPConnected()) Thread.sleep(100);
            }
            for (ServerSetting s : settings) {
                if (!s.file.equals("none")) {
                    String remoteFilePath = serverInfo.path + "/" + s.file;
                    String fileContent = ssh.readRemoteFile(remoteFilePath);
                    if (fileContent != null) {
                        String[] lines = fileContent.split("\n");
                        for (String line : lines) {
                            if (line.startsWith(s.key + "=")) {
                                String val = line.substring((s.key + "=").length()).trim();
                                if (!val.isEmpty()) s.value = val;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            devPrint("Error loading remote setting: " + e.getMessage());
        }
    }

    private void writeSettings(SSHManager manager, String basePath) {
        Map<String, List<ServerSetting>> fileGroups = new HashMap<>();
        for (ServerSetting st : settings) if (!st.file.equals("none") && dependencySatisfied(st) && !st.value.isEmpty()) fileGroups.computeIfAbsent(st.file, k -> new ArrayList<>()).add(st);
        for (String fileName : fileGroups.keySet()) {
            boolean isYaml = fileName.endsWith(".yml") || fileName.endsWith(".yaml");
            if (manager == null) {
                try {
                    Path filePath = Paths.get(basePath, fileName);
                    List<String> originalLines = Files.exists(filePath) ? Files.readAllLines(filePath) : new ArrayList<>();
                    if (isYaml) {
                        Map<String, Object> yamlMap = new LinkedHashMap<>();
                        for (ServerSetting st : fileGroups.get(fileName)) {
                            String[] parts = st.key.split("\\.");
                            Map<String, Object> current = yamlMap;
                            for (int i = 0; i < parts.length - 1; i++) current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
                            Object value;
                            if (st.value.equalsIgnoreCase("true") || st.value.equalsIgnoreCase("false")) value = Boolean.parseBoolean(st.value);
                            else {
                                try {
                                    value = Integer.parseInt(st.value);
                                } catch (NumberFormatException e) {
                                    value = st.value;
                                }
                            }
                            current.put(parts[parts.length - 1], value);
                        }
                        StringBuilder yamlBuilder = new StringBuilder();
                        writeYaml(yamlMap, yamlBuilder, 0);
                        Files.write(filePath, yamlBuilder.toString().getBytes());
                        devPrint("Wrote YAML settings to local file: " + filePath);
                    } else {
                        Map<String, String> newSettings = new LinkedHashMap<>();
                        for (ServerSetting st : fileGroups.get(fileName)) {
                            String value = st.value;
                            if (st.key.equalsIgnoreCase("gamemode") || st.key.equalsIgnoreCase("difficulty")) value = value.toLowerCase();
                            newSettings.put(st.key, st.key + "=" + value);
                        }
                        List<String> updatedLines = new ArrayList<>();
                        Set<String> keysUpdated = new HashSet<>();
                        for (String line : originalLines) {
                            boolean found = false;
                            for (String key : newSettings.keySet()) {
                                if (line.startsWith(key + "=")) {
                                    updatedLines.add(newSettings.get(key));
                                    keysUpdated.add(key);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) updatedLines.add(line);
                        }
                        for (String key : newSettings.keySet()) if (!keysUpdated.contains(key)) updatedLines.add(newSettings.get(key));
                        Files.write(filePath, String.join("\n", updatedLines).getBytes());
                        devPrint("Wrote settings to local file: " + filePath);
                    }
                } catch (Exception e) {
                    devPrint("Error writing local settings to file " + fileName + ": " + e.getMessage());
                }
            } else {
                if (isYaml) {
                    Map<String, Object> yamlMap = new LinkedHashMap<>();
                    for (ServerSetting st : fileGroups.get(fileName)) {
                        String[] parts = st.key.split("\\.");
                        Map<String, Object> current = yamlMap;
                        for (int i = 0; i < parts.length - 1; i++) current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
                        Object value;
                        if (st.value.equalsIgnoreCase("true") || st.value.equalsIgnoreCase("false")) value = Boolean.parseBoolean(st.value);
                        else {
                            try {
                                value = Integer.parseInt(st.value);
                            } catch (NumberFormatException e) {
                                value = st.value;
                            }
                        }
                        current.put(parts[parts.length - 1], value);
                    }
                    StringBuilder yamlBuilder = new StringBuilder();
                    writeYaml(yamlMap, yamlBuilder, 0);
                    manager.writeRemoteFile(basePath + "/" + fileName, yamlBuilder.toString());
                    devPrint("Wrote YAML settings to remote file: " + basePath + "/" + fileName);
                } else {
                    List<String> lines = new ArrayList<>();
                    for (ServerSetting st : fileGroups.get(fileName)) {
                        if (st.key.equalsIgnoreCase("gamemode") || st.key.equalsIgnoreCase("difficulty")) lines.add(st.key + "=" + st.value.toLowerCase());
                        else if (dependencySatisfied(st) && !st.value.isEmpty()) lines.add(st.key + "=" + st.value);
                    }
                    manager.writeRemoteFile(basePath + "/" + fileName, String.join("\n", lines));
                    devPrint("Wrote settings to remote file: " + basePath + "/" + fileName);
                }
            }
        }
    }

    private void writeYaml(Map<String, Object> map, StringBuilder builder, int indent) {
        String indentStr = "  ".repeat(indent);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nested) {
                builder.append(indentStr).append(entry.getKey()).append(":\n");
                writeYaml((Map<String, Object>) nested, builder, indent + 1);
            } else builder.append(indentStr).append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
    }

    public void createServer() {
        String serverName = "", serverType = "", serverVersion = "", ramAmount = "", aikarsFlags = "";
        for (ServerSetting s : settings) {
            if (s.key.equals("server-name")) serverName = s.value.trim();
            if (s.key.equals("server-type")) serverType = s.value.trim();
            if (s.key.equals("server-version")) serverVersion = s.value.trim();
            if (s.key.equals("memory")) {
                ramAmount = s.value.trim();
                if (ramAmount.toLowerCase().endsWith("g")) ramAmount = String.valueOf((int) Math.round(Double.parseDouble(ramAmount.substring(0, ramAmount.length() - 1)) * 1024));
                else if (ramAmount.toLowerCase().endsWith("m")) ramAmount = ramAmount.substring(0, ramAmount.length() - 1);
            }
            if (s.key.equals("aikars_flags")) aikarsFlags = s.value.equalsIgnoreCase("true") ? "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch" : "";
        }
        if (serverName.isEmpty()) serverName = "MyServer";
        if (serverVersion.isEmpty()) serverVersion = "latest";
        if (ramAmount.isEmpty()) ramAmount = "2048";
        if (isEditMode && serverInfo != null) {
            editServer(serverName, serverType.toLowerCase(), serverVersion.toLowerCase());
            close();
            return;
        }
        int tabIndex = ServerManagerScreen.getActiveTabIndex();
        if (tabIndex > 0) createRemoteServer(serverName, serverType.toLowerCase(), serverVersion.toLowerCase(), ramAmount, aikarsFlags);
        else {
            String finalServerName = serverName;
            ServerFactory.createServerAsync(serverName, serverType.toLowerCase(), serverVersion.toLowerCase(), settingsRoot, ramAmount, aikarsFlags, exitCode -> {
                if (exitCode == 0) {
                    notification.change(finalServerName + " Created Successfully!", "Click To Open", Notification.Type.SUCCESS, () -> ServerManagerScreen.openServerScreen(settingsRoot + File.separator + finalServerName));
                    writeSettings(null, settingsRoot + File.separator + finalServerName);
                } else errorNotification(exitCode, notification);
                close();
            });
        }
    }

    private void createRemoteServer(String serverName, String serverType, String serverVersion, String ramAmount, String aikarsFlags) {
        try {
            int tabIndex = ServerManagerScreen.getActiveTabIndex();
            RemoteHostInfo rh = ServerManagerScreen.getRemoteHosts().get(tabIndex - 1);
            String remoteHome = rh.getHomeDirectory(), remoteServersPath = remoteHome + "remotely/servers", remoteServerPath = remoteServersPath + "/" + serverName;
            notification = new Notification("Creating Remote Server...", "This Might Take Some Time..", Notification.Type.INFO);
            notification.loading = true;
            notification.autoSlideOut = false;
            SSHManager ssh = INSTANCE.getSSHManagerForHost(rh);
            if (!ssh.isSFTPConnected()) {
                ssh.connectToRemoteHost(rh.getUser(), rh.getIp(), rh.getPort(), rh.getPassword());
                while (!ssh.isSFTPConnected()) Thread.sleep(100);
            }
            ssh.prepareRemoteDirectorySync(remoteHome + "/remotely");
            ssh.prepareRemoteDirectorySync(remoteServersPath);
            ssh.prepareRemoteDirectorySync(remoteServerPath);
            String downloadURL = ServerFactory.getDownloadURL(serverType, serverVersion);
            if (downloadURL == null) {
                notification.change("Unsupported Server Type or Version!", "Please Try Again.", Notification.Type.ERROR, null);
                close();
                return;
            }
            String remoteCmd = "cd " + remoteServerPath + " && " + "wget -O server.jar \"" + downloadURL + "\"";
            String output = ssh.runRemoteCommandWithOutput(remoteCmd);
            devPrint("wget output: " + output);
            String memSettings = "-Xms" + ramAmount + "M -Xmx" + ramAmount + "M";
            String javaCommand = "java " + memSettings + " " + aikarsFlags + " -jar server.jar nogui";
            String shContent = "#!/bin/bash\ncd \"$(dirname \"$0\")\"\n" + javaCommand;
            ssh.writeRemoteFile(remoteServerPath + "/start.sh", shContent);
            ssh.runRemoteCommand("chmod +x " + remoteServerPath + "/start.sh");
            writeSettings(ssh, remoteServerPath);
            ServerManagerScreen.addServer(serverName, remoteServerPath, serverType, serverVersion, true, rh);
            String url = ServerFactory.getDownloadURL(serverType, serverVersion);
            if (url != null) ssh.runRemoteCommandWithOutput("cd " + remoteServerPath + " && wget -O server.jar \"" + url + "\"");
            else {
                notification.change("Failed To Get Download URL", "Unsupported Server Type / Version", Notification.Type.ERROR, null);
                return;
            }
            notification.change(serverName + " Created Successfully!", "Click To Open", Notification.Type.SUCCESS, () -> ServerManagerScreen.openServerScreen(remoteServerPath));
        } catch (Exception e) {
            devPrint("Failed to create remote server: " + e.getMessage());
            notification.change("Server Creation Failed", e.getMessage(), Notification.Type.ERROR, null);
        } finally {
            close();
        }
    }

    private void editServer(String serverName, String serverType, String serverVersion) {
        boolean shouldRebuild = !serverInfo.version.equals(serverVersion) || !serverInfo.type.equalsIgnoreCase(serverType);
        try {
            if (!serverInfo.isRemote) {
                File serverDir = new File(settingsRoot);
                writeSettings(null, serverDir.getAbsolutePath());
                if (shouldRebuild) {
                    ServerFactory.createServerAsync(serverName, serverType.toLowerCase(), serverVersion.toLowerCase(), settingsRoot, "", "", exitCode -> {
                        if (exitCode == 0) new Notification(serverName + " Updated Successfully!", Notification.Type.SUCCESS);
                        else errorNotification(exitCode, notification);
                    });
                } else new Notification(serverName + " Edited Successfully!", Notification.Type.SUCCESS);
            } else {
                RemoteHostInfo rh = serverInfo.remoteHost;
                String remoteHome = rh.getHomeDirectory(), remoteServersPath = remoteHome + "remotely/servers", remoteServerPath = remoteServersPath + "/" + serverName;
                SSHManager ssh = new SSHManager(rh);
                ssh.connectToRemoteHost(rh.getUser(), rh.getIp(), rh.getPort(), rh.getPassword());
                while (!ssh.isSFTPConnected()) Thread.sleep(100);
                ssh.prepareRemoteDirectorySync(remoteHome + "remotely");
                ssh.prepareRemoteDirectorySync(remoteServersPath);
                ssh.prepareRemoteDirectorySync(remoteServerPath);
                if (shouldRebuild) {
                    String downloadURL = ServerFactory.getDownloadURL(serverType, serverVersion);
                    if (downloadURL == null) {
                        new Notification("Failed to get download URL", "Unsupported server type or version", Notification.Type.ERROR);
                        return;
                    }
                    String remoteCmd = "mkdir -p " + remoteServerPath + " && " + "cd " + remoteServerPath + " && " + "wget -O server.jar \"" + downloadURL + "\"";
                    new Notification("Updating remote server...", "This might take some time", Notification.Type.INFO);
                    ssh.runRemoteCommand(remoteCmd);
                }
                String ramDigits = "2048", memSettings = "-Xms" + ramDigits + "M -Xmx" + ramDigits + "M";
                String javaCommand = "java " + memSettings + " -jar server.jar nogui";
                String shContent = "#!/bin/bash\ncd \"$(dirname \"$0\")\"\n" + javaCommand;
                ssh.writeRemoteFile(remoteServerPath + "/start.sh", shContent);
                ssh.runRemoteCommand("chmod +x " + remoteServerPath + "/start.sh");
                writeSettings(ssh, remoteServerPath);
                new Notification(serverName + " Updated Successfully!", Notification.Type.SUCCESS);
            }
            serverInfo.name = serverName;
            serverInfo.type = serverType;
            serverInfo.version = serverVersion;
            ServerManagerScreen.saveServers();
            ServerManagerScreen.saveRemoteHosts();
        } catch (Exception e) {
            devPrint("Failed to update server: " + e.getMessage());
            new Notification("Server Update Failed", e.getMessage(), Notification.Type.ERROR);
        }
    }

    private void errorNotification(int existCode, Notification notification) {
        if (existCode == 3) notification.change("Unsupported Server Type or Version!", "Please Try Again.", Notification.Type.ERROR, null);
        else if (existCode == 1) notification.change("Download Failed!", "Check Your Internet Connection.", Notification.Type.ERROR, null);
        else if (existCode == 2) notification.change("Failed To Create Start Script!", "", Notification.Type.ERROR, null);
        else notification.change("Server Creation Failed", "Exit Code:  + existCode", Notification.Type.ERROR, null);
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.SCREEN);
    }

    public void close() {
        client.setScreen(parent);
    }
}