package redxax.oxy.servers

import com.google.gson.JsonParser
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.text.Text
import net.minecraft.util.Identifier
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
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

class PluginModResourceScreen(
    private val mc: MinecraftClient,
    private val parent: Screen,
    private val resource: ModrinthResource,
    private val serverInfo: ServerInfo
) :
    Screen(Text.literal(resource.name)) {
    private val baseColor = -0xe7e7e8
    private val lighterColor = -0xddddde
    private val borderColor = -0xcccccd
    private val highlightColor = -0xbbbbbc
    private val textColor = -0x1
    private var installButtonText = "Install"
    private var fullDescription = ""
    private var isProjectDataLoaded = false
    private var smoothOffset = 0f
    private var targetOffset = 0f
    private val scrollSpeed = 0.2f
    private val contentBlocks: MutableList<ContentBlock> = ArrayList()

    private class ContentBlock {
        enum class Type {
            HEADER, SUBHEADER, TEXT, IMAGE
        }

        var type: Type
        var text: String? = null
        var imageId: Identifier? = null

        constructor(type: Type, text: String?) {
            this.type = type
            this.text = text
        }

        constructor(type: Type, imageId: Identifier?) {
            this.type = type
            this.imageId = imageId
        }
    }

    override fun init() {
        super.init()
        val dest = if (serverInfo.isModServer) {
            Path.of(serverInfo.path, "mods", resource.fileName)
        } else {
            Path.of(serverInfo.path, "plugins", resource.fileName)
        }
        if (Files.exists(dest)) {
            installButtonText = "Installed"
            try {
                val attr = Files.readAttributes(
                    dest,
                    BasicFileAttributes::class.java
                )
                installButtonText = if (attr.size() > 0 && !installButtonNeedsUpdate(dest)) {
                    "Installed"
                } else {
                    "Update"
                }
            } catch (e: Exception) {
                installButtonText = "Update"
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
    }

    private fun fetchProjectData() {
        CompletableFuture.runAsync {
            try {
                val uri = URI("https://api.modrinth.com/v2/project/" + resource.slug)
                val client = HttpClient.newHttpClient()
                val request =
                    HttpRequest.newBuilder().uri(uri).header("User-Agent", "Remotely").GET().build()
                val response =
                    client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200) {
                    val project = JsonParser.parseString(response.body()).asJsonObject
                    if (project.has("body")) {
                        fullDescription = project["body"].asString
                    } else if (project.has("description")) {
                        fullDescription = project["description"].asString
                    }
                    parseDescription()
                    isProjectDataLoaded = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val notification = Notification.Builder("Failed to fetch project data: " + e.message, Notification.Type.ERROR).build()
            }
        }
    }

    private fun parseDescription() {
        val lines = fullDescription.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (line in lines) {
            var line = line
            line = line.trim { it <= ' ' }
            if (line.startsWith("### ")) {
                contentBlocks.add(ContentBlock(ContentBlock.Type.SUBHEADER, line.substring(4).trim { it <= ' ' }))
            } else if (line.startsWith("# ")) {
                contentBlocks.add(ContentBlock(ContentBlock.Type.HEADER, line.substring(2).trim { it <= ' ' }))
            } else if (line.startsWith("![")) {
                val start = line.indexOf("](")
                val end = line.indexOf(")", start)
                if (start != -1 && end != -1) {
                    val url = line.substring(start + 2, end).trim { it <= ' ' }
                    loadImage(url)
                }
            } else if (!line.isEmpty()) {
                contentBlocks.add(ContentBlock(ContentBlock.Type.TEXT, line))
            }
        }
    }

    private fun loadImage(url: String) {
        CompletableFuture.runAsync {
            try {
                URL(url).openStream().use { inputStream ->
                    val nativeImage = loadImage(inputStream, url)
                    if (nativeImage != null) {
                        val texture = NativeImageBackedTexture(nativeImage)
                        val textureId =
                            mc.textureManager.registerDynamicTexture("oxy_mod_image_" + contentBlocks.size, texture)
                        mc.execute {
                            contentBlocks.add(
                                ContentBlock(
                                    ContentBlock.Type.IMAGE,
                                    textureId
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val notification =
                    Notification.Builder("Failed to load image: " + e.message, Notification.Type.WARN).build()
            }
        }
    }

    @Throws(Exception::class)
    private fun loadImage(inputStream: InputStream, url: String): NativeImage {
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
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val installButtonX = this.width - 60
        val installButtonY = 5
        val btnW = 50
        val btnH = 20
        val hoveredInstall =
            mouseX >= installButtonX && mouseX <= installButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH
        if (hoveredInstall && button == 0 && !installButtonText.equals("Installed", ignoreCase = true)) {
            fetchAndInstallResource()
            return true
        }
        val backButtonX = installButtonX - (btnW + 10)
        val backButtonY = installButtonY
        val hoveredBack =
            mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH
        if (hoveredBack && button == 0) {
            mc.setScreen(parent)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun fetchAndInstallResource() {
        Thread(Runnable {
            try {
                val downloadUrl = fetchDownloadUrl(resource.version)
                if (downloadUrl.isEmpty()) {
                    val notification =
                        Notification.Builder("Failed to fetch download URL for: " + resource.name, Notification.Type.ERROR).build()
                    return@Runnable
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
                    val notification =
                        Notification.Builder("Installed: " + resource.name + " to " + dest, Notification.Type.INFO).build()
                } else {
                    val notification =
                        Notification.Builder("Download failed: HTTP " + response.statusCode(), Notification.Type.ERROR).build()
                }
            } catch (e: Exception) {
                val notification = Notification.Builder("Install failed: " + e.message, Notification.Type.ERROR).build()
                e.printStackTrace()
            }
            mc.execute { mc.setScreen(parent) }
        }).start()
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
                    val file = files[0].asJsonObject
                    return file["url"].asString
                }
            } else {
                val notification =
                    Notification.Builder("Error fetching download URL: " + response.statusCode(), Notification.Type.ERROR).build()
            }
        } catch (e: Exception) {
            val notification = Notification.Builder("Error fetching download URL: " + e.message, Notification.Type.ERROR).build()
            e.printStackTrace()
        }
        return ""
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        targetOffset -= (verticalAmount * 20).toFloat()
        targetOffset = max(
            0.0,
            min(
                targetOffset.toDouble(),
                max(0.0, (contentHeight - (this.height - 60)).toDouble())
            )
        ).toFloat()
        return true
    }

    private val contentHeight: Int
        get() {
            var height = 55
            for (block in contentBlocks) {
                when (block.type) {
                    ContentBlock.Type.HEADER -> height += textRenderer.fontHeight + 10
                    ContentBlock.Type.SUBHEADER -> height += textRenderer.fontHeight + 8
                    ContentBlock.Type.TEXT -> {
                        val lines = textRenderer.wrapLines(
                            Text.literal(block.text),
                            width - 20
                        )
                        height += lines.size * (textRenderer.fontHeight + 2) + 5
                    }

                    ContentBlock.Type.IMAGE -> height += 117
                }
            }
            return height
        }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        Notification.updateAll(delta)
        Notification.renderAll(context)
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor)
        super.render(context, mouseX, mouseY, delta)
        context.fill(0, 0, this.width, 30, lighterColor)
        drawInnerBorder(context, 0, 0, this.width, 30, borderColor)
        context.drawText(
            this.textRenderer,
            Text.literal(resource.name + " - " + resource.version),
            10,
            10,
            textColor,
            false
        )

        val installButtonX = this.width - 60
        val installButtonY = 5
        val btnW = 50
        val btnH = 20
        val hoveredInstall =
            mouseX >= installButtonX && mouseX <= installButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH
        val bgInstall = if (hoveredInstall && !installButtonText.equals(
                "Installed",
                ignoreCase = true
            )
        ) highlightColor else lighterColor
        context.fill(installButtonX, installButtonY, installButtonX + btnW, installButtonY + btnH, bgInstall)
        drawInnerBorder(context, installButtonX, installButtonY, btnW, btnH, borderColor)
        val tw = textRenderer.getWidth(installButtonText)
        val tx = installButtonX + (btnW - tw) / 2
        val ty = installButtonY + (btnH - textRenderer.fontHeight) / 2
        context.drawText(this.textRenderer, Text.literal(installButtonText), tx, ty, textColor, false)

        val backButtonX = installButtonX - (btnW + 10)
        val backButtonY = installButtonY
        val hoveredBack =
            mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH
        val bgBack = if (hoveredBack) highlightColor else lighterColor
        context.fill(backButtonX, backButtonY, backButtonX + btnW, backButtonY + btnH, bgBack)
        drawInnerBorder(context, backButtonX, backButtonY, btnW, btnH, borderColor)
        val twb = textRenderer.getWidth("Back")
        val txb = backButtonX + (btnW - twb) / 2
        context.drawText(this.textRenderer, Text.literal("Back"), txb, ty, textColor, false)

        if (!isProjectDataLoaded) {
            context.drawText(this.textRenderer, Text.literal("Loading project details..."), 10, 40, 0xA0A0A0, false)
            return
        }

        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed
        val listStartY = 40
        val listEndY = this.height - 20

        context.enableScissor(0, listStartY, this.width, listEndY)

        var currentY = listStartY + 15 - smoothOffset.toInt()
        for (block in contentBlocks) {
            when (block.type) {
                ContentBlock.Type.HEADER -> {
                    context.drawText(this.textRenderer, Text.literal(block.text), 10, currentY, 0xFFFFA0, false)
                    currentY += textRenderer.fontHeight + 10
                }

                ContentBlock.Type.SUBHEADER -> {
                    context.drawText(this.textRenderer, Text.literal(block.text), 10, currentY, 0xFFA0FF, false)
                    currentY += textRenderer.fontHeight + 8
                }

                ContentBlock.Type.TEXT -> {
                    val lines =
                        textRenderer.wrapLines(Text.literal(block.text), this.width - 20)
                    for (line in lines) {
                        context.drawText(this.textRenderer, line, 10, currentY, 0xA0A0A0, false)
                        currentY += textRenderer.fontHeight + 2
                    }
                    currentY += 5
                }

                ContentBlock.Type.IMAGE -> if (block.imageId != null) {
                    mc.textureManager.bindTexture(block.imageId)
                    context.drawTexture(block.imageId, 10, currentY, 0f, 0f, 200, 112, 200, 112)
                    currentY += 117
                }
            }
        }

        context.disableScissor()

        if (smoothOffset > 0) {
            context.fillGradient(0, listStartY, this.width, listStartY + 10, -0x80000000, 0x00000000)
        }
        if (smoothOffset < contentHeight - (this.height - 60)) {
            context.fillGradient(0, listEndY - 10, this.width, listEndY, 0x00000000, -0x80000000)
        }

        loadMoreIfNeeded()
    }

    private fun loadMoreIfNeeded() {
    }

    private fun drawInnerBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, c: Int) {
        context.fill(x, y, x + w, y + 1, c)
        context.fill(x, y + h - 1, x + w, y + h, c)
        context.fill(x, y, x + 1, y + h, c)
        context.fill(x + w - 1, y, x + w, y + h, c)
    }
}
