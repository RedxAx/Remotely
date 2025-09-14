package redxax.oxy.remotely.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.servers.ServerInfo;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.MarkdownWidget;
import restudio.rescreen.util.Sound;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static restudio.rescreen.util.SoundUtils.playSound;

public class ResourcePageScreen extends ReScreen {
    private final Screen parent;
    private final IRemotelyResource resource;
    private List<Version> versions = new ArrayList<>();
    private static ServerInfo serverInfo;
    private boolean isDownloadingMrpack = false;
    private MarkdownWidget markdownWidget;
    private Container descriptionContainer;
    private Container versionsContainer;

    public ResourcePageScreen(ResourceManagerScreen parent, IRemotelyResource resource, ServerInfo serverInfo) {
        super();
        this.parent = parent;
        this.resource = resource;
        ResourcePageScreen.serverInfo = serverInfo;
        fetchVersions();
    }

    @Override
    public void init() {
        super.init();

        header().addRight("close.png", this::close, "Close")
                .addRight("external.png", this::openSite, "Open resource page")
                .addRight("download.png", this::downloadLatest, "Download latest compatible version")
                .build();

        descriptionContainer = createContainer("description", 5, 60, width - 10, height - 65).padding(5).columns(1);
        versionsContainer = createContainer("versions", 5, 60, width - 10, height - 65).padding(2).columns(1);

        tabs().builder()
                .position(5, 35)
                .size(width - 10, 18)
                .onTabSelected(tab -> setActiveContainer(tab.getContainer()))
                .build();

        tabs().addTab("Description", descriptionContainer);
        tabs().addTab("Versions", versionsContainer);

        tabs().setActiveTab(0);
        setActiveContainer(descriptionContainer);

        loadMarkdown();
    }

    public void close() {
        client.setScreen(parent);
    }

    private void openSite() {
        playSound(Sound.CLICK);
        String siteUrl = getSiteUrlForResource();
        if (!siteUrl.isEmpty()) {
            try {
                java.awt.Desktop.getDesktop().browse(new URI(siteUrl));
            } catch (Exception e) {
                devPrint("Failed to open browser: " + e.getMessage());
            }
        }
    }

    private void downloadLatest() {
        playSound(Sound.CLICK);
        if (resource.getFileName().toLowerCase(Locale.ROOT).endsWith(".mrpack")) {
            downloadMrpackResource();
        } else {
            Version compVersion = getLatestCompatibleVersion();
            if (compVersion != null) {
                downloadVersionResource(compVersion);
            }
        }
    }

    private void loadMarkdown() {
        loading = true;
        new Thread(() -> {
            String markdownContent;
            try {
                String url;
                if (resource.getSlug().startsWith("spigot_")) {
                    url = "https://api.spiget.org/v2/resources/" + resource.getProjectId();
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestProperty("User-Agent", "Remotely");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    InputStream in = conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int ch;
                    while ((ch = in.read()) != -1) {
                        sb.append((char) ch);
                    }
                    in.close();
                    markdownContent = new String(Base64.getDecoder().decode(JsonParser.parseString(sb.toString()).getAsJsonObject().get("description").getAsString()));
                } else if (resource.getSlug().startsWith("hangar_")) {
                    url = "https://hangar.papermc.io/api/v1/pages/main/" + resource.getProjectId();
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestProperty("User-Agent", "Remotely");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    InputStream in = conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int ch;
                    while ((ch = in.read()) != -1) {
                        sb.append((char) ch);
                    }
                    in.close();
                    markdownContent = sb.toString();
                } else {
                    url = "https://api.modrinth.com/v2/project/" + resource.getProjectId();
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestProperty("User-Agent", "Remotely");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    InputStream in = conn.getInputStream();
                    InputStreamReader reader = new InputStreamReader(in);
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    markdownContent = obj.has("body") ? obj.get("body").getAsString() : "";
                    in.close();
                }
            } catch (Exception e) {
                markdownContent = resource.getDescription();
            }
            final String finalContent = markdownContent;
            client.execute(() -> {
                markdownWidget = new MarkdownWidget(finalContent, 0, 0, 0, 0);
                descriptionContainer.addWidget(markdownWidget);
                loading = false;
            });
        }).start();
    }

    private void fetchVersions() {
        new Thread(() -> {
            List<Version> fetched = new ArrayList<>();
            try {
                String url;
                if (resource.getSlug().startsWith("spigot_")) {
                    url = "https://api.spiget.org/v2/resources/" + resource.getProjectId() + "/versions?size=10000&sort=-releaseDate";
                } else if (resource.getSlug().startsWith("hangar_")) {
                    int offset = 0;
                    url = "https://hangar.papermc.io/api/v1/projects/" + resource.getProjectId()
                            + "/versions?includeHiddenChannels=true&channel=Release&platform=PAPER&limit=25&offset=" + offset;
                } else {
                    url = "https://api.modrinth.com/v2/project/" + resource.getProjectId() + "/version";
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", "Remotely");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                InputStream in = conn.getInputStream();
                StringBuilder sb = new StringBuilder();
                int ch;
                while ((ch = in.read()) != -1) {
                    sb.append((char) ch);
                }
                in.close();
                JsonElement elem = JsonParser.parseString(sb.toString());
                JsonArray arr = null;
                if (resource.getSlug().startsWith("hangar_")) {
                    if (elem.isJsonObject()) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (obj.has("result")) {
                            arr = obj.getAsJsonArray("result");
                        }
                    }
                } else {
                    if (elem.isJsonArray()) {
                        arr = elem.getAsJsonArray();
                    } else if (elem.isJsonObject()) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (obj.has("versions")) {
                            arr = obj.getAsJsonArray("versions");
                        }
                    }
                }
                if (arr != null) {
                    for (JsonElement je : arr) {
                        JsonObject verObj = je.getAsJsonObject();
                        String verNum = verObj.has("version_number") ? verObj.get("version_number").getAsString() : (verObj.has("name") ? verObj.get("name").getAsString() : "Unknown");
                        String mcVersions;
                        if (resource.getSlug().startsWith("spigot_")) {
                            mcVersions = verObj.has("testedVersions") ? verObj.get("testedVersions").getAsString() : "";
                        } else if (resource.getSlug().startsWith("hangar_")) {
                            mcVersions = verObj.has("gameVersion") ? verObj.get("gameVersion").getAsString() : "Unknown";
                        } else {
                            if (verObj.has("game_versions") && verObj.get("game_versions").isJsonArray()) {
                                JsonArray gameVersionsArray = verObj.getAsJsonArray("game_versions");
                                List<String> versionsList = new ArrayList<>();
                                for (JsonElement v : gameVersionsArray) {
                                    versionsList.add(v.getAsString());
                                }
                                mcVersions = String.join(", ", versionsList);
                            } else {
                                mcVersions = "Unknown";
                            }
                        }
                        String dateUploaded;
                        if (resource.getSlug().startsWith("spigot_")) {
                            dateUploaded = verObj.has("releaseDate") ? verObj.get("releaseDate").getAsString() : "Unknown";
                        } else {
                            dateUploaded = verObj.has("createdAt") ? verObj.get("createdAt").getAsString() : (verObj.has("date_published") ? verObj.get("date_published").getAsString() : "Unknown");
                        }
                        String fileUrl = "";
                        if (resource.getSlug().startsWith("spigot_")) {
                            int versionId = verObj.has("id") ? verObj.get("id").getAsInt() : 0;
                            fileUrl = "https://api.spiget.org/v2/resources/" + resource.getProjectId() + "/download?version=" + versionId;
                        } else if (resource.getSlug().startsWith("hangar_")) {
                            if (verObj.has("downloads")) {
                                JsonObject downloads = verObj.getAsJsonObject("downloads");
                                if (downloads.has("PAPER")) {
                                    JsonObject paperObj = downloads.getAsJsonObject("PAPER");
                                    fileUrl = paperObj.has("downloadUrl") ? paperObj.get("downloadUrl").getAsString() : "";
                                }
                            }
                        } else if (verObj.has("files")) {
                            JsonArray files = verObj.getAsJsonArray("files");
                            if (!files.isEmpty()) {
                                JsonObject fileObj = files.get(0).getAsJsonObject();
                                fileUrl = fileObj.has("url") ? fileObj.get("url").getAsString() : "";
                            }
                        } else if (verObj.has("downloadUrl")) {
                            fileUrl = verObj.get("downloadUrl").getAsString();
                        }
                        int downloadsCount = 0;
                        if (resource.getSlug().startsWith("hangar_")) {
                            if (verObj.has("stats")) {
                                JsonObject stats = verObj.getAsJsonObject("stats");
                                downloadsCount = stats.has("totalDownloads") ? stats.get("totalDownloads").getAsInt() : 0;
                            }
                        } else {
                            downloadsCount = verObj.has("downloads") ? verObj.get("downloads").getAsInt() : 0;
                        }
                        fetched.add(new Version(verNum, mcVersions, dateUploaded, fileUrl, downloadsCount));
                    }
                }
            } catch (Exception e) {
                devPrint("Failed to fetch versions: " + e.getMessage());
            }
            versions = fetched;
            client.execute(this::populateVersionsContainer);
        }).start();
    }

    private void populateVersionsContainer() {
        versionsContainer.clearWidgets();
        for (Version version : versions) {
            versionsContainer.addWidget(new VersionWidget(version));
        }
    }

    private Version getLatestCompatibleVersion() {
        if (versions == null || versions.isEmpty()) return null;
        for (Version ver : versions) {
            if (ver.mcVersions.contains(serverInfo.version) && ver.fileUrl.toLowerCase().contains(serverInfo.type.toLowerCase())) {
                return ver;
            }
        }
        for (Version ver : versions) {
            if (ver.mcVersions.contains(serverInfo.version)) {
                return ver;
            }
        }
        return versions.getFirst();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String getSiteUrlForResource() {
        String projectId = resource.getProjectId();
        if (resource.getSlug().startsWith("spigot_")) {
            return "https://www.spigotmc.org/resources/" + projectId;
        } else if (resource.getSlug().startsWith("hangar_")) {
            return "https://hangar.papermc.io/" + resource.getAuthor() + "/" + projectId;
        } else {
            return "https://modrinth.com/mod/" + projectId;
        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawText(resource.getName(), 10, 10, globalTextColor, shadow);
    }

    private String formatDownloads(int downloads) {
        if (downloads < 1000) {
            return String.valueOf(downloads);
        } else if (downloads < 1_000_000) {
            return String.format("%.1fK", downloads / 1000.0);
        } else if (downloads < 1_000_000_000) {
            return String.format("%.1fM", downloads / 1_000_000.0);
        } else {
            return String.format("%.1fB", downloads / 1_000_000_000.0);
        }
    }

    private String getRelativeTime(String dateStr) {
        try {
            Instant uploaded;
            if (dateStr.matches("\\d+")) {
                long epoch = Long.parseLong(dateStr);
                uploaded = Instant.ofEpochSecond(epoch);
            } else {
                uploaded = Instant.parse(dateStr);
            }
            Duration duration = Duration.between(uploaded, Instant.now());
            long days = duration.toDays();
            if (days < 1) return "Today";
            if (days < 30) return days + " Days Ago";
            long months = days / 30;
            return months + " Months Ago";
        } catch (Exception e) {
            return dateStr;
        }
    }

    private String formatMCVersions(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.split(",\\s*");
        Map<String, List<Integer>> groups = new HashMap<>();
        List<String> others = new ArrayList<>();
        for (String ver : parts) {
            String trimmed = ver.trim();
            String[] nums = trimmed.split("\\.");
            if (nums.length >= 2) {
                String key = nums[0] + "." + nums[1];
                int patch = 0;
                if (nums.length >= 3) {
                    try {
                        patch = Integer.parseInt(nums[2]);
                    } catch (Exception ignored) {}
                }
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(patch);
            } else {
                others.add(trimmed);
            }
        }
        List<String> results = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : groups.entrySet()) {
            List<Integer> patches = entry.getValue();
            Collections.sort(patches);
            if (patches.size() >= 3 && patches.getLast() - patches.getFirst() == patches.size() - 1) {
                results.add(entry.getKey() + ".x");
            } else if (patches.size() >= 2) {
                String first = entry.getKey() + "." + patches.getFirst();
                String last = entry.getKey() + "." + patches.getLast();
                results.add(first + " - " + last);
            } else {
                results.add(entry.getKey() + (patches.size() == 1 ? "." + patches.getFirst() : ""));
            }
        }
        results.addAll(others);
        return String.join(", ", results);
    }

    private void downloadVersionResource(Version ver) {
        new Thread(() -> {
            try {
                if (ver.fileUrl.isEmpty()) {
                    ver.isInstalled = "Failed";
                    return;
                }
                if (serverInfo.isRemote && serverInfo.remoteHost != null) {
                    SSHManager sshManager = RemotelyClient.INSTANCE.getSSHManagerForHost(serverInfo.remoteHost);
                    if (sshManager == null || !sshManager.isSSH()) {
                        ver.isInstalled = "Failed";
                        return;
                    }

                    URL url = new URL(ver.fileUrl);
                    HttpURLConnection headConn = (HttpURLConnection) url.openConnection();
                    headConn.setRequestProperty("User-Agent", "Remotely");
                    headConn.setRequestMethod("HEAD");
                    headConn.setConnectTimeout(5000);
                    headConn.setReadTimeout(5000);
                    int total = headConn.getContentLength();
                    ver.totalBytes = total;
                    String remoteDir = serverInfo.path + "/" + (serverInfo.isModServer() ? "mods" : serverInfo.isPluginServer() ? "plugins" : "");
                    String remotePath = remoteDir + "/" + resource.getFileName();
                    String command = "wget -O \"" + remotePath.replace("\\", "/") + "\" \"" + ver.fileUrl + "\"";
                    devPrint("Remote Download: " + command);
                    sshManager.runRemoteCommand(command);
                    ver.isDownloading = true;
                    long startTime = System.currentTimeMillis();
                    while (true) {
                        String sizeCommand = "stat -c%s \"" + remotePath.replace("\\", "/") + "\"";
                        String sizeOutput = sshManager.runRemoteCommandWithOutput(sizeCommand);
                        long remoteSize = 0;
                        try {
                            remoteSize = Long.parseLong(sizeOutput.trim());
                        } catch (Exception ignored) {}
                        ver.downloadedBytes = remoteSize;
                        if (total > 0) {
                            ver.progress = (double) remoteSize / total;
                        }
                        long currentTime = System.currentTimeMillis();
                        long timeElapsed = currentTime - startTime;
                        if (timeElapsed > 0) {
                            ver.speed = remoteSize / (timeElapsed / 1000.0);
                        }
                        if (remoteSize >= total && total > 0) {
                            break;
                        }
                    }
                    client.execute(() -> {
                        ver.isDownloading = false;
                        ver.isInstalled = "Installed";
                    });
                    return;
                }
                URL url = new URL(ver.fileUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Remotely");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                ver.totalBytes = conn.getContentLength();
                ver.downloadedBytes = 0;
                ver.progress = 0.0;
                ver.isDownloading = true;
                long startTime = System.currentTimeMillis();
                InputStream in = conn.getInputStream();
                String serverDir = serverInfo.path + File.separator + (serverInfo.isModServer() ? "mods" : serverInfo.isPluginServer() ? "plugins" : "");
                Path serverPath = Path.of(serverDir);
                if (!Files.exists(serverPath)) Files.createDirectories(serverPath);
                String fileName = resource.getFileName();
                File outFile = new File(serverDir, fileName);
                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    ver.downloadedBytes += bytesRead;
                    long currentTime = System.currentTimeMillis();
                    long timeElapsed = currentTime - startTime;
                    if (timeElapsed > 0) {
                        ver.speed = ver.downloadedBytes / (timeElapsed / 1000.0);
                    }
                    if (ver.totalBytes > 0) {
                        ver.progress = (double) ver.downloadedBytes / ver.totalBytes;
                    }
                }
                fos.close();
                in.close();
                ver.isDownloading = false;
                ver.isInstalled = "Installed";
            } catch (Exception e) {
                ver.isDownloading = false;
                ver.isInstalled = "Failed";
            }
        }).start();
    }

    private void downloadMrpackResource() {
        new Thread(() -> {
            try {
                if (serverInfo.isRemote && serverInfo.remoteHost != null) {
                    SSHManager sshManager = RemotelyClient.INSTANCE.getSSHManagerForHost(serverInfo.remoteHost);
                    if (sshManager != null && sshManager.isSSH()) {
                        sshManager.installMrPackOnRemote(serverInfo, resource);
                    }
                    client.execute(() -> isDownloadingMrpack = false);
                    return;
                }
                String exePath;
                String serverDir;
                URL url;
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("windows")) {
                    exePath = Path.of(String.valueOf(remotelyDir), "mrpack-install-windows.exe").toString();
                    serverDir = Path.of(String.valueOf(remotelyDir), "servers", resource.getName()).toString();
                    url = new URL("https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-windows.exe");
                } else if (os.contains("linux")) {
                    exePath = Path.of(String.valueOf(remotelyDir), "mrpack-install-linux").toString();
                    serverDir = Path.of(String.valueOf(remotelyDir), "servers", resource.getName()).toString();
                    url = new URL("https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-linux");
                } else if (os.contains("mac") || os.contains("darwin")) {
                    exePath = Path.of(String.valueOf(remotelyDir), "mrpack-install-macos").toString();
                    serverDir = Path.of(String.valueOf(remotelyDir), "servers", resource.getName()).toString();
                    url = new URL("https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-darwin");
                } else {
                    client.execute(() -> isDownloadingMrpack = false);
                    return;
                }
                Path exe = Path.of(exePath);
                Path serverPath = Path.of(serverDir);
                if (!Files.exists(serverPath)) Files.createDirectories(serverPath);
                if (!Files.exists(exe)) {
                    try (InputStream input = url.openStream()) {
                        Files.copy(input, exe, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                isDownloadingMrpack = true;
                ProcessBuilder pb = new ProcessBuilder(exePath, resource.getProjectId(), resource.getVersion(), "--server-dir", serverDir, "--server-file", "server.jar");
                pb.directory(serverPath.toFile());
                Process proc = pb.start();
                proc.waitFor();
                client.execute(() -> isDownloadingMrpack = false);
            } catch (Exception e) {
                client.execute(() -> isDownloadingMrpack = false);
            }
        }).start();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.SCREEN);
    }

    private class VersionWidget extends AnimatedWidget {
        private final Version version;

        public VersionWidget(Version version) {
            super(0, 0, 0, 35, "");
            this.version = version;
        }

        @Override
        protected void drawContent(IDrawContext context, int mouseX, int mouseY) {
            String title = resource.getName() + ": " + version.version;
            context.drawText(title, 4, 3, globalTextColor, Config.shadow);

            String desc = formatMCVersions(version.mcVersions);
            context.drawText(desc, 4, 15, borderColor, Config.shadow);

            String subDesc = getRelativeTime(version.dateUploaded) + " | " + formatDownloads(version.downloads) + " Downloads";
            context.drawText(subDesc, 4, 26, globalDarkTextColor, Config.shadow);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int btnX = getWidth() - 70;
            int btnY = (getHeight() - 20) / 2;
            if (mouseX >= getX() + btnX && mouseX <= getX() + btnX + 60 && mouseY >= getY() + btnY && mouseY <= getY() + btnY + 20) {
                playSound(Sound.CLICK);
                if (resource.getFileName().toLowerCase(Locale.ROOT).endsWith(".mrpack")) {
                    downloadMrpackResource();
                } else {
                    downloadVersionResource(version);
                }
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    private static class Version {
        public String isInstalled;
        String version;
        String mcVersions;
        String dateUploaded;
        String fileUrl;
        int downloads;
        boolean isDownloading;
        double progress;
        long downloadedBytes;
        long totalBytes;
        double speed;
        Version(String version, String mcVersions, String dateUploaded, String fileUrl, int downloads) {
            this.version = version;
            this.mcVersions = mcVersions;
            this.dateUploaded = dateUploaded;
            this.fileUrl = fileUrl;
            this.downloads = downloads;
            this.isDownloading = false;
            this.progress = 0;
            this.downloadedBytes = 0;
            this.totalBytes = 0;
            this.speed = 0;
            this.isInstalled = "Download";
        }
    }
}