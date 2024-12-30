package redxax.oxy.servers

import com.google.gson.JsonParser
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.constraints.RelativeConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.markdown.MarkdownComponent
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.texture.NativeImage
import net.minecraft.text.Text
import redxax.oxy.Notification
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

class PluginModResourceScreen(
    private val mc: MinecraftClient,
    private val parent: Screen,
    private val resource: ModrinthResource,
    private val serverInfo: ServerInfo
) : Screen(Text.literal(resource.name)) {
    private val baseColor = -0xe7e7e8
    private val lighterColor = -0xddddde
    private val borderColor = -0xcccccd
    private val highlightColor = -0xbbbbbc
    private val textColor = -0x1
    private var installButtonText = "Install"
    private var fullDescription = ""
    private var isProjectDataLoaded = false
    private var installingMrPack = false

    private val window = UIBlock().constrain {
        x = 0.pixels()
        y = 0.pixels()
        width = RelativeConstraint(1f)
        height = RelativeConstraint(1f)
    }

    private val scroll = ScrollComponent().constrain {
        x = 2.pixels()
        y = 40.pixels()
        width = RelativeConstraint(1f) - 4.pixels()
        height = RelativeConstraint(1f) - 40.pixels()
    } childOf window

    private val markdownComponent = MarkdownComponent("").constrain {
        x = 0.pixels()
        y = 0.pixels()
        width = ChildBasedSizeConstraint()
        height = ChildBasedSizeConstraint()
    } childOf scroll

    override fun init() {
        super.init()
        this.clearChildren()
        window.addChild(scroll)
        scroll.addChild(markdownComponent)
        if (resource.fileName.endsWith(".mrpack", ignoreCase = true)) {
            installButtonText = "Install Modpack"
        } else {
            val dest = if (serverInfo.isModServer) {
                Path.of(serverInfo.path, "mods", resource.fileName)
            } else {
                Path.of(serverInfo.path, "plugins", resource.fileName)
            }
            if (Files.exists(dest)) {
                installButtonText = "Installed"
                try {
                    val attr = Files.readAttributes(dest, java.nio.file.attribute.BasicFileAttributes::class.java)
                    installButtonText = if (attr.size() > 0 && !installButtonNeedsUpdate(dest)) {
                        "Installed"
                    } else {
                        "Update"
                    }
                } catch (e: Exception) {
                    installButtonText = "Update"
                }
            }
        }
        fetchProjectData()
    }

    private fun installButtonNeedsUpdate(dest: Path): Boolean {
        val installedVersion = parseVersionFromFileName(dest.fileName.toString())
        val currentVersion = resource.version
        return !installedVersion.equals(currentVersion, ignoreCase = true)
    }

    private fun parseVersionFromFileName(filename: String): String {
        return filename.replace(".jar", "")
            .replace(".mrpack", "")
    }

    private fun fetchProjectData() {
        CompletableFuture.runAsync {
            try {
                val uri = URI("https://api.modrinth.com/v2/project/" + resource.slug)
                val client = HttpClient.newHttpClient()
                val request =
                    HttpRequest.newBuilder().uri(uri).header("User-Agent", "Remotely").GET().build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200) {
                    val project = JsonParser.parseString(response.body()).asJsonObject
                    if (project.has("body")) {
                        fullDescription = project["body"].asString
                    } else if (project.has("description")) {
                        fullDescription = project["description"].asString
                    }
                    isProjectDataLoaded = true
                    mc.execute {
                        markdownComponent.constrain {
                            textRenderer.draw(fullDescription, 0f, 0f, -1, false, null, null, null, 0xffffff, 0, false)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Notification.Builder("Failed to fetch project data: " + e.message, Notification.Type.ERROR).build()
            }
        }
    }

    @Throws(Exception::class)
    private fun loadImage(inputStream: InputStream, url: String): NativeImage? {
        if (url.lowercase(Locale.getDefault()).endsWith(".webp")) {
            ImageIO.createImageInputStream(inputStream).use { iis ->
                val readers = ImageIO.getImageReadersByFormatName("webp")
                if (!readers.hasNext()) throw IOException("No WEBP reader found")
                val reader = readers.next()
                reader.setInput(iis, false)
                val img = reader.read(0)
                val baos = ByteArrayOutputStream()
                ImageIO.write(img, "png", baos)
                ByteArrayInputStream(baos.toByteArray()).use { pngStream ->
                    return NativeImage.read(pngStream)
                }
            }
        } else if (url.lowercase(Locale.getDefault()).endsWith(".gif")) {
            ImageIO.createImageInputStream(inputStream).use { iis ->
                val readers = ImageIO.getImageReadersByFormatName("gif")
                if (!readers.hasNext()) throw IOException("No GIF reader found")
                val reader = readers.next()
                reader.setInput(iis, false)
                val img = reader.read(0)
                val baos = ByteArrayOutputStream()
                ImageIO.write(img, "png", baos)
                ByteArrayInputStream(baos.toByteArray()).use { pngStream ->
                    return NativeImage.read(pngStream)
                }
            }
        } else {
            return NativeImage.read(inputStream)
        }
        return null
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val installButtonX = this.width - 60
        val installButtonY = 5
        val btnW = 50
        val btnH = 20
        val hoveredInstall = mouseX >= installButtonX && mouseX <= installButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH
        if (hoveredInstall && button == 0 && !installButtonText.equals("Installed", ignoreCase = true)) {
            if (resource.fileName.endsWith(".mrpack", ignoreCase = true)) {
                if (!installingMrPack) {
                    installingMrPack = true
                    installButtonText = "Installing..."
                    installMrPack()
                }
            } else {
                fetchAndInstallResource()
            }
            return true
        }
        val backButtonX = installButtonX - (btnW + 10)
        val backButtonY = installButtonY
        val hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH
        if (hoveredBack && button == 0) {
            mc.setScreen(parent)
            return true
        }
        scroll.mouseClick(mouseX, mouseY, button)
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun installMrPack() {
        Thread {
            try {
                val exePath = "C:/remotely/mrpack-install-windows.exe"
                val serverDir = "C:/remotely/servers/" + resource.name.replace(" ", "_")
                val exe = Path.of(exePath)
                if (!Files.exists(exe)) {
                    try {
                        val url = URL("https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-windows.exe")
                        url.openStream().use { input ->
                            Files.copy(input, exe, StandardCopyOption.REPLACE_EXISTING)
                        }
                    } catch (e: Exception) {
                    }
                }
                val pb = ProcessBuilder(
                    exePath,
                    resource.slug,
                    resource.version,
                    "--server-dir",
                    serverDir
                )
                pb.directory(Path.of(serverDir).toFile())
                val proc = pb.start()
                proc.waitFor()
                Notification.Builder("Modpack installed to $serverDir", Notification.Type.INFO).build()
            } catch (e: Exception) {
                Notification.Builder("mrpack-install failed: " + e.message, Notification.Type.ERROR).build()
            }
            mc.execute {
                installingMrPack = false
                installButtonText = "Installed"
                mc.setScreen(parent)
            }
        }.start()
    }

    private fun fetchAndInstallResource() {
        Thread {
            try {
                val downloadUrl = fetchDownloadUrl(resource.version)
                if (downloadUrl.isEmpty()) {
                    Notification.Builder("Failed to fetch download URL for: " + resource.name, Notification.Type.ERROR).build()
                    mc.execute {
                        mc.setScreen(parent)
                    }
                    return@Thread
                }
                val dest = if (serverInfo.isModServer) {
                    Path.of(serverInfo.path, "mods", resource.fileName)
                } else {
                    Path.of(serverInfo.path, "plugins", resource.fileName)
                }
                Files.createDirectories(dest.parent)
                val httpClient = HttpClient.newHttpClient()
                val request = HttpRequest.newBuilder()
                    .uri(URI(downloadUrl))
                    .header("User-Agent", "Remotely")
                    .GET()
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
                if (response.statusCode() == 200) {
                    Files.copy(response.body(), dest, StandardCopyOption.REPLACE_EXISTING)
                    Notification.Builder("Installed: " + resource.name + " to " + dest, Notification.Type.INFO).build()
                } else {
                    Notification.Builder("Download failed: HTTP " + response.statusCode(), Notification.Type.ERROR).build()
                }
            } catch (e: Exception) {
                Notification.Builder("Install failed: " + e.message, Notification.Type.ERROR).build()
                e.printStackTrace()
            }
            mc.execute { mc.setScreen(parent) }
        }.start()
    }

    private fun fetchDownloadUrl(versionID: String): String {
        try {
            val uri = URI("https://api.modrinth.com/v2/version/$versionID")
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder().uri(uri).header("User-Agent", "Remotely").GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val version = JsonParser.parseString(response.body()).asJsonObject
                val files = version.getAsJsonArray("files")
                if (files.size() > 0) {
                    val file = files.get(0).asJsonObject
                    return file["url"].asString
                }
            } else {
                Notification.Builder("Error fetching download URL: " + response.statusCode(), Notification.Type.ERROR).build()
            }
        } catch (e: Exception) {
            Notification.Builder("Error fetching download URL: " + e.message, Notification.Type.ERROR).build()
            e.printStackTrace()
        }
        return ""
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scroll.mouseScroll(mouseX.toDouble())
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        Notification.updateAll(delta)
        Notification.renderAll(context)
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor)
        super.render(context, mouseX, mouseY, delta)
        context.fill(0, 0, this.width, 30, lighterColor)
        drawInnerBorder(context, 0, 0, this.width, 30, borderColor)
        context.drawText(this.textRenderer, Text.literal(resource.name + " - " + resource.version), 10, 10, textColor, false)

        val installButtonX = this.width - 60
        val installButtonY = 5
        val btnW = 50
        val btnH = 20
        val hoveredInstall = mouseX >= installButtonX && mouseX <= installButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH
        val bgInstall = if (hoveredInstall && !installButtonText.equals("Installed", ignoreCase = true)) highlightColor else lighterColor
        context.fill(installButtonX, installButtonY, installButtonX + btnW, installButtonY + btnH, bgInstall)
        drawInnerBorder(context, installButtonX, installButtonY, btnW, btnH, borderColor)
        val tw = textRenderer.getWidth(installButtonText)
        val tx = installButtonX + (btnW - tw) / 2
        val ty = installButtonY + (btnH - textRenderer.fontHeight) / 2
        context.drawText(this.textRenderer, Text.literal(installButtonText), tx, ty, textColor, false)

        val backButtonX = installButtonX - (btnW + 10)
        val backButtonY = installButtonY
        val hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH
        val bgBack = if (hoveredBack) highlightColor else lighterColor
        context.fill(backButtonX, backButtonY, backButtonX + btnW, backButtonY + btnH, bgBack)
        drawInnerBorder(context, backButtonX, backButtonY, btnW, btnH, borderColor)
        val twb = textRenderer.getWidth("Back")
        val txb = backButtonX + (btnW - twb) / 2
        context.drawText(this.textRenderer, Text.literal("Back"), txb, ty, textColor, false)

        if (!isProjectDataLoaded) {
            context.drawText(this.textRenderer, Text.literal("Loading project details..."), 10, 40, 0xA0A0A0, false)
        }

        window.draw()
    }

    private fun drawInnerBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, c: Int) {
        context.fill(x, y, x + w, y + 1, c)
        context.fill(x, y + h - 1, x + w, y + h, c)
        context.fill(x, y, x + 1, y + h, c)
        context.fill(x + w - 1, y, x + w, y + h, c)
    }
}
