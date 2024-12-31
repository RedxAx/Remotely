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
) : Screen(Text.literal(resource.name)) {
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
    private var installingMrPack = false
    private val linkRegions: MutableList<LinkRegion> = ArrayList()

    private class ContentBlock {
        enum class Type {
            HEADER, SUBHEADER, SUBSUBHEADER, TEXT, IMAGE, LIST, TABLE, LINK
        }

        var type: Type
        var content: Any? = null

        constructor(type: Type, content: Any?) {
            this.type = type
            this.content = content
        }
    }

    private data class LinkRegion(val x: Int, val y: Int, val width: Int, val height: Int, val url: String)

    override fun init() {
        super.init()
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
                    parseDescription()
                    isProjectDataLoaded = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Notification.Builder("Failed to fetch project data: " + e.message, Notification.Type.ERROR).build()
            }
        }
    }

    private fun parseDescription() {
        val lines = fullDescription.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var listBuffer: MutableList<String>? = null
        var tableBuffer: MutableList<String>? = null
        for (rawLine in lines) {
            var line = rawLine.trim()
            when {
                line.startsWith("### ") -> {
                    contentBlocks.add(ContentBlock(ContentBlock.Type.SUBSUBHEADER, line.substring(4).trim()))
                }
                line.startsWith("## ") -> {
                    contentBlocks.add(ContentBlock(ContentBlock.Type.SUBHEADER, line.substring(3).trim()))
                }
                line.startsWith("# ") -> {
                    contentBlocks.add(ContentBlock(ContentBlock.Type.HEADER, line.substring(2).trim()))
                }
                line.startsWith("![") -> {
                    val start = line.indexOf("](")
                    val end = line.indexOf(")", start)
                    if (start != -1 && end != -1) {
                        val url = line.substring(start + 2, end).trim()
                        loadImage(url)
                        contentBlocks.add(ContentBlock(ContentBlock.Type.IMAGE, "oxy_mod_image_${contentBlocks.size}"))
                    }
                }
                line.contains("[") && line.contains("](") -> {
                    val linkStart = line.indexOf("[")
                    val linkMiddle = line.indexOf("](")
                    val linkEnd = line.indexOf(")", linkMiddle)
                    if (linkStart != -1 && linkMiddle != -1 && linkEnd != -1) {
                        val linkText = line.substring(linkStart + 1, linkMiddle)
                        val linkUrl = line.substring(linkMiddle + 2, linkEnd)
                        if (isImageUrl(linkUrl)) {
                            loadImage(linkUrl)
                            contentBlocks.add(ContentBlock(ContentBlock.Type.IMAGE, "oxy_mod_image_${contentBlocks.size}"))
                        } else {
                            contentBlocks.add(ContentBlock(ContentBlock.Type.LINK, Pair(linkText, linkUrl)))
                        }
                        val remaining = line.substring(linkEnd + 1).trim()
                        if (remaining.isNotEmpty()) {
                            contentBlocks.add(ContentBlock(ContentBlock.Type.TEXT, remaining))
                        }
                    } else {
                        contentBlocks.add(ContentBlock(ContentBlock.Type.TEXT, line))
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    if (listBuffer == null) {
                        listBuffer = mutableListOf()
                    }
                    listBuffer.add(line.substring(2).trim())
                }
                line.startsWith("|") && line.endsWith("|") -> {
                    if (tableBuffer == null) {
                        tableBuffer = mutableListOf()
                    }
                    tableBuffer.add(line)
                }
                line.isEmpty() -> {
                    if (listBuffer != null) {
                        contentBlocks.add(ContentBlock(ContentBlock.Type.LIST, ArrayList(listBuffer)))
                        listBuffer = null
                    }
                    if (tableBuffer != null) {
                        contentBlocks.add(ContentBlock(ContentBlock.Type.TABLE, ArrayList(tableBuffer)))
                        tableBuffer = null
                    }
                }
                else -> {
                    if (listBuffer != null) {
                        contentBlocks.add(ContentBlock(ContentBlock.Type.LIST, ArrayList(listBuffer)))
                        listBuffer = null
                    }
                    if (tableBuffer != null) {
                        contentBlocks.add(ContentBlock(ContentBlock.Type.TABLE, ArrayList(tableBuffer)))
                        tableBuffer = null
                    }
                    contentBlocks.add(ContentBlock(ContentBlock.Type.TEXT, line))
                }
            }
        }
        if (listBuffer != null) {
            contentBlocks.add(ContentBlock(ContentBlock.Type.LIST, ArrayList(listBuffer)))
        }
        if (tableBuffer != null) {
            contentBlocks.add(ContentBlock(ContentBlock.Type.TABLE, ArrayList(tableBuffer)))
        }
    }

    private fun isImageUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.getDefault())
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp")
    }

    private fun loadImage(url: String) {
        CompletableFuture.runAsync {
            try {
                URL(url).openStream().use { inputStream ->
                    val nativeImage = loadImage(inputStream, url)
                    if (nativeImage != null) {
                        val texture = NativeImageBackedTexture(nativeImage)
                        mc.textureManager.registerDynamicTexture("oxy_mod_image_${contentBlocks.size}", texture)
                        val identifierString = "oxy_mod_image:image_${contentBlocks.size}"
                        val textureId = Identifier.tryParse(identifierString)
                        if (textureId != null) {
                            mc.execute {
                                // Image is added as ContentBlock.Type.IMAGE in parseDescription
                            }
                        } else {
                            Notification.Builder("Invalid Identifier for image: $identifierString", Notification.Type.ERROR).build()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Notification.Builder("Failed to load image: " + e.message, Notification.Type.WARN).build()
            }
        }
    }

    @Throws(Exception::class)
    private fun loadImage(inputStream: InputStream, url: String): NativeImage? {
        return when {
            url.lowercase(Locale.getDefault()).endsWith(".webp") -> {
                ImageIO.createImageInputStream(inputStream).use { iis ->
                    val readers = ImageIO.getImageReadersByFormatName("webp")
                    if (!readers.hasNext()) throw IOException("No WEBP reader found")
                    val reader = readers.next()
                    reader.setInput(iis, false)
                    val img = reader.read(0)
                    val baos = ByteArrayOutputStream()
                    ImageIO.write(img, "png", baos)
                    ByteArrayInputStream(baos.toByteArray()).use { pngStream ->
                        NativeImage.read(pngStream)
                    }
                }
            }
            url.lowercase(Locale.getDefault()).endsWith(".gif") -> {
                ImageIO.createImageInputStream(inputStream).use { iis ->
                    val readers = ImageIO.getImageReadersByFormatName("gif")
                    if (!readers.hasNext()) throw IOException("No GIF reader found")
                    val reader = readers.next()
                    reader.setInput(iis, false)
                    val img = reader.read(0)
                    val baos = ByteArrayOutputStream()
                    ImageIO.write(img, "png", baos)
                    ByteArrayInputStream(baos.toByteArray()).use { pngStream ->
                        NativeImage.read(pngStream)
                    }
                }
            }
            else -> {
                NativeImage.read(inputStream)
            }
        }
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
        if (isProjectDataLoaded) {
            for (region in linkRegions) {
                if (mouseX.toInt() in region.x until (region.x + region.width) &&
                    mouseY.toInt() in region.y until (region.y + region.height)
                ) {
                    try {
                        val uri = URI(region.url)
                        val desktop = java.awt.Desktop.getDesktop()
                        desktop.browse(uri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Notification.Builder("Failed to open link: " + e.message, Notification.Type.ERROR).build()
                    }
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun installMrPack() {
        Thread {
            try {
                val exePath = "C:\\remotely\\mrpack-install-windows.exe"
                val serverDir = "C:\\remotely\\servers\\${resource.name}"
                val exe = Path.of(exePath)
                val serverPath = Path.of(serverDir)

                if (!Files.exists(serverPath)) {
                    Files.createDirectories(serverPath)
                }

                if (!Files.exists(exe)) {
                    try {
                        val url = URL("https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-windows.exe")
                        url.openStream().use { input ->
                            Files.copy(input, exe, StandardCopyOption.REPLACE_EXISTING)
                        }
                    } catch (e: Exception) {
                        System.err.println("Failed to download mrpack-install: " + e.message)
                        return@Thread
                    }
                }

                System.out.println("Resource ID: " + resource.projectId)
                System.out.println("Resource Version: " + resource.version)

                val pb = ProcessBuilder(
                    exePath,
                    resource.projectId,
                    resource.version,
                    "--server-dir",
                    serverDir,
                    "--server-file server.jar --verbose"
                )
                pb.directory(serverPath.toFile())
                val proc = pb.start()

                val errorStream = proc.errorStream.bufferedReader().readText()
                proc.waitFor()
                System.out.println("mrpack-install exit code: " + proc.exitValue())
                if (proc.exitValue() != 0) {
                    System.err.println("mrpack-install failed. Exit code: ${proc.exitValue()}")
                    System.err.println("Error details: $errorStream")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                System.err.println("Failed to install mrpack: " + e.message)
            }
            mc.execute {
                installingMrPack = false
                System.out.println("install finished.")
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
                    val file = files[0].asJsonObject
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
        targetOffset -= (verticalAmount * 20).toFloat()
        targetOffset = max(
            0.0f,
            min(
                targetOffset,
                (contentHeight - (this.height - 60)).toFloat().coerceAtLeast(0f)
            )
        )
        return true
    }

    private val contentHeight: Int
        get() {
            var height = 55
            for (block in contentBlocks) {
                height += when (block.type) {
                    ContentBlock.Type.HEADER -> textRenderer.fontHeight + 20
                    ContentBlock.Type.SUBHEADER -> textRenderer.fontHeight + 16
                    ContentBlock.Type.SUBSUBHEADER -> textRenderer.fontHeight + 12
                    ContentBlock.Type.TEXT -> {
                        val lines = wrapText(block.content as String, width - 20)
                        lines.size * (textRenderer.fontHeight + 4) + 10
                    }
                    ContentBlock.Type.LIST -> {
                        val items = block.content as List<*>
                        items.size * (textRenderer.fontHeight + 4) + 10
                    }
                    ContentBlock.Type.TABLE -> {
                        val rows = (block.content as List<*>).size
                        rows * (textRenderer.fontHeight + 4) + 10
                    }
                    ContentBlock.Type.IMAGE -> 117
                    ContentBlock.Type.LINK -> textRenderer.fontHeight + 4
                }
            }
            return height
        }

    private fun wrapText(text: String, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (textRenderer.getWidth(testLine) > maxWidth) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            } else {
                currentLine = testLine
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
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
            return
        }

        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed
        val listStartY = 40
        val listEndY = this.height - 20

        context.enableScissor(0, listStartY, this.width, listEndY)

        var currentY = listStartY + 15 - smoothOffset.toInt()
        linkRegions.clear()
        for (block in contentBlocks) {
            when (block.type) {
                ContentBlock.Type.HEADER -> {
                    context.drawText(this.textRenderer, Text.literal(block.content as String), 10, currentY, 0xFFAA00, false)
                    currentY += textRenderer.fontHeight + 20
                }
                ContentBlock.Type.SUBHEADER -> {
                    context.drawText(this.textRenderer, Text.literal(block.content as String), 10, currentY, 0xFFA0FF, false)
                    currentY += textRenderer.fontHeight + 16
                }
                ContentBlock.Type.SUBSUBHEADER -> {
                    context.drawText(this.textRenderer, Text.literal(block.content as String), 10, currentY, 0xA0A0FF, false)
                    currentY += textRenderer.fontHeight + 12
                }
                ContentBlock.Type.TEXT -> {
                    val lines = wrapText(block.content as String, this.width - 20)
                    for (line in lines) {
                        context.drawText(this.textRenderer, Text.literal(line), 10, currentY, 0xA0A0A0, false)
                        currentY += textRenderer.fontHeight + 4
                    }
                    currentY += 10
                }
                ContentBlock.Type.LIST -> {
                    val items = block.content as List<*>
                    for (item in items) {
                        context.drawText(this.textRenderer, Text.literal("• ${item as String}"), 20, currentY, 0xA0A0A0, false)
                        currentY += textRenderer.fontHeight + 4
                    }
                    currentY += 10
                }
                ContentBlock.Type.TABLE -> {
                    val rows = block.content as List<*>
                    for (row in rows) {
                        val columns = (row as String).trim().trim('|').split("|").map { it.trim() }
                        var currentX = 10
                        for (col in columns) {
                            context.drawText(this.textRenderer, Text.literal(col), currentX, currentY, 0xA0A0A0, false)
                            currentX += textRenderer.getWidth(col) + 20
                        }
                        currentY += textRenderer.fontHeight + 4
                    }
                    currentY += 10
                }
                ContentBlock.Type.IMAGE -> {
                    val imageIdStr = block.content as String
                    val textureId = Identifier.tryParse("oxy_mod_image:image_$imageIdStr")
                    if (textureId != null) {
                        mc.textureManager.bindTexture(textureId)
                        context.drawTexture(textureId, 10, currentY, 0f, 0f, 200, 112, 200, 112)
                        currentY += 117
                    } else {
                        Notification.Builder("Invalid image identifier: oxy_mod_image:image_$imageIdStr", Notification.Type.ERROR).build()
                    }
                }
                ContentBlock.Type.LINK -> {
                    val (text, url) = block.content as Pair<String, String>
                    val linkWidth = textRenderer.getWidth(text)
                    context.drawText(this.textRenderer, Text.literal(text), 10, currentY, 0x0000FF, false)
                    linkRegions.add(LinkRegion(10, currentY, linkWidth, textRenderer.fontHeight, url))
                    currentY += textRenderer.fontHeight + 4
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
    }

    private fun drawInnerBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, c: Int) {
        context.fill(x, y, x + w, y + 1, c)
        context.fill(x, y + h - 1, x + w, y + h, c)
        context.fill(x, y, x + 1, y + h, c)
        context.fill(x + w - 1, y, x + w, y + h, c)
    }
}
