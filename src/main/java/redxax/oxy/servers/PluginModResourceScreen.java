package redxax.oxy.servers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public class PluginModResourceScreen extends Screen {
    private final MinecraftClient mc;
    private final Screen parent;
    private final ModrinthResource resource;
    private final ServerInfo serverInfo;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private String installButtonText = "Install";

    public PluginModResourceScreen(MinecraftClient mc, Screen parent, ModrinthResource resource, ServerInfo info) {
        super(Text.literal(resource.name));
        this.mc = mc;
        this.parent = parent;
        this.resource = resource;
        this.serverInfo = info;
    }

    @Override
    protected void init() {
        super.init();
        Path dest;
        if (serverInfo.isModServer()) {
            dest = Path.of(serverInfo.path, "mods", resource.fileName);
        } else {
            dest = Path.of(serverInfo.path, "plugins", resource.fileName);
        }
        if (Files.exists(dest)) {
            installButtonText = "Installed";
            try {
                BasicFileAttributes attr = Files.readAttributes(dest, BasicFileAttributes.class);
                if (attr.size() > 0 && !installButtonNeedsUpdate(dest)) {
                    installButtonText = "Installed";
                } else {
                    installButtonText = "Update";
                }
            } catch (Exception e) {
                installButtonText = "Update";
            }
        }
    }

    private boolean installButtonNeedsUpdate(Path dest) {
        String installedVersion = parseVersionFromFileName(dest.getFileName().toString());
        String currentVersion = resource.version;
        return !installedVersion.equalsIgnoreCase(currentVersion);
    }

    private String parseVersionFromFileName(String filename) {
        return filename.replace(".jar", "");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int installButtonX = this.width - 60;
        int installButtonY = 5;
        int btnW = 50;
        int btnH = 20;
        boolean hoveredInstall = mouseX >= installButtonX && mouseX <= installButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH;
        if (hoveredInstall && button == 0 && !installButtonText.equalsIgnoreCase("Installed")) {
            fetchAndInstallResource();
            return true;
        }
        int backButtonX = installButtonX - (btnW + 10);
        boolean hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH;
        if (hoveredBack && button == 0) {
            mc.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void fetchAndInstallResource() {
        new Thread(() -> {
            try {
                String downloadUrl = fetchDownloadUrl(resource.fileName.replace(".jar", ""));
                if (downloadUrl.isEmpty()) {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("Failed to fetch download URL for: " + resource.name + "\n");
                    }
                    return;
                }
                Path dest;
                if (serverInfo.isModServer()) {
                    dest = Path.of(serverInfo.path, "mods", resource.fileName);
                } else {
                    dest = Path.of(serverInfo.path, "plugins", resource.fileName);
                }
                Files.createDirectories(dest.getParent());
                HttpClient httpClient = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(downloadUrl))
                        .header("User-Agent", "Remotely")
                        .GET()
                        .build();
                System.out.println("Downloading from URL: " + downloadUrl); // Log the URL
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    Files.copy(response.body(), dest, StandardCopyOption.REPLACE_EXISTING);
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("Installed: " + resource.name + " to " + dest + "\n");
                    }
                } else {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("Download failed: HTTP " + response.statusCode() + "\n");
                    }
                }
            } catch (Exception e) {
                if (serverInfo.terminal != null) {
                    serverInfo.terminal.appendOutput("Install failed: " + e.getMessage() + "\n");
                }
                e.printStackTrace();
            }
            mc.execute(() -> mc.setScreen(parent));
        }).start();
    }

    private String fetchDownloadUrl(String slug) {
        try {
            URI uri = new URI("https://api.modrinth.com/v2/project/" + slug + "/version");
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("User-Agent", "Remotely").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
                if (versions.size() > 0) {
                    JsonObject latestVersion = versions.get(0).getAsJsonObject();
                    JsonArray files = latestVersion.getAsJsonArray("files");
                    if (files.size() > 0) {
                        JsonObject file = files.get(0).getAsJsonObject();
                        return file.get("url").getAsString();
                    }
                }
            } else {
                System.err.println("Error fetching download URL: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, 30, lighterColor);
        drawInnerBorder(context, 0, 0, this.width, 30, borderColor);
        context.drawText(this.textRenderer, Text.literal(resource.name + " - " + resource.version), 10, 10, textColor, false);

        int installButtonX = this.width - 60;
        int installButtonY = 5;
        int btnW = 50;
        int btnH = 20;
        boolean hoveredInstall = mouseX >= installButtonX && mouseX <= installButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH;
        int bgInstall = hoveredInstall && !installButtonText.equalsIgnoreCase("Installed") ? highlightColor : lighterColor;
        context.fill(installButtonX, installButtonY, installButtonX + btnW, installButtonY + btnH, bgInstall);
        drawInnerBorder(context, installButtonX, installButtonY, btnW, btnH, borderColor);
        int tw = this.textRenderer.getWidth(installButtonText);
        int tx = installButtonX + (btnW - tw) / 2;
        int ty = installButtonY + (btnH - this.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, Text.literal(installButtonText), tx, ty, textColor, false);

        int backButtonX = installButtonX - (btnW + 10);
        boolean hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH;
        int bgBack = hoveredBack ? highlightColor : lighterColor;
        context.fill(backButtonX, installButtonY, backButtonX + btnW, installButtonY + btnH, bgBack);
        drawInnerBorder(context, backButtonX, installButtonY, btnW, btnH, borderColor);
        int twb = this.textRenderer.getWidth("Back");
        int txb = backButtonX + (btnW - twb) / 2;
        context.drawText(this.textRenderer, Text.literal("Back"), txb, ty, textColor, false);

        context.drawText(this.textRenderer, Text.literal("Description: " + resource.description), 10, 40, 0xA0A0A0, false);
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }
}