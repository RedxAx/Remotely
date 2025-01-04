package redxax.oxy.servers

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import redxax.oxy.explorer.FileExplorerScreen
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.math.max

class PluginModListScreen(
    private val mc: MinecraftClient,
    private val parent: Screen,
    private val serverInfo: ServerInfo
) :
    Screen(
        Text.literal(
            if (serverInfo.isPluginServer)
             "Installed Plugins" else "Installed Mods"
        )
    ) {
    private var entries: MutableList<EntryInfo>? = null
    private var smoothOffset = 0f
    private var targetOffset = 0f
    private val scrollSpeed = 0.2f
    private val baseColor = -0xe7e7e8
    private val lighterColor = -0xddddde
    private val borderColor = -0xcccccd
    private val highlightColor = -0xbbbbbc
    private val textColor = -0x1
    private val entryHeight = 40

    override fun init() {
        super.init()
        loadEntries()
    }

    private fun loadEntries() {
        entries = ArrayList()
        val dir: Path
        val isPlugin =
            !serverInfo.isPluginServer
        dir = if (isPlugin) {
            Paths.get(serverInfo.path, "Plugins")
        } else {
            Paths.get(serverInfo.path, "Mods")
        }
        try {
            if (Files.exists(dir)) {
                Files.newDirectoryStream(dir).use { stream ->
                    for (p in stream) {
                        if (p.toString().lowercase(Locale.getDefault()).endsWith(".jar")) {
                            val info = EntryInfo()
                            info.path = p
                            if (isPlugin) {
                                readPluginInfo(info)
                            } else {
                                readModInfo(info)
                            }
                            info.isPlugin = isPlugin
                            entries?.add(info)
                        }
                    }
                }
            }
        } catch (ignored: IOException) {
        }
    }

    private fun readPluginInfo(info: EntryInfo) {
        try {
            FileSystems.newFileSystem(info.path, null as ClassLoader?).use { fs ->
                val pluginYml: Path = fs.getPath("plugin.yml")
                val paperPluginYml: Path = fs.getPath("paper-plugin.yml")
                if (Files.exists(pluginYml)) {
                    readYaml(pluginYml, info)
                } else if (Files.exists(paperPluginYml)) {
                    readYaml(paperPluginYml, info)
                } else {
                    info.displayName = info.path!!.fileName.toString()
                    info.version = ""
                }
            }
        } catch (e: IOException) {
            info.displayName = info.path!!.fileName.toString()
            info.version = ""
        }
    }

    @kotlin.Throws(IOException::class)
    private fun readYaml(yml: Path, info: EntryInfo) {
        val lines = Files.readAllLines(yml)
        var name: String? = null
        var version: String? = null
        for (line in lines) {
            var ln = line.trim { it <= ' ' }
            if (ln.lowercase(Locale.getDefault()).startsWith("name:")) {
                name = ln.substring(ln.indexOf(":") + 1).trim { it <= ' ' }.replace("^['\"]|['\"]$".toRegex(), "")
            } else if (ln.lowercase(Locale.getDefault()).startsWith("version:")) {
                version =
                    ln.substring(ln.indexOf(":") + 1).trim { it <= ' ' }.replace("^['\"]|['\"]$".toRegex(), "")
            }
            if (name != null && version != null) break
        }
        info.displayName = name ?: info.path!!.fileName.toString()
        info.version = version ?: ""
    }

    private fun readModInfo(info: EntryInfo) {
        try {
            FileSystems.newFileSystem(info.path, null as ClassLoader?).use { fs ->
                val fabricJson: Path = fs.getPath("fabric.mod.json")
                if (Files.exists(fabricJson)) {
                    readFabricModJson(fabricJson, info)
                } else {
                    val quiltJson: Path = fs.getPath("quilt.mod.json")
                    if (Files.exists(quiltJson)) {
                        readFabricModJson(quiltJson, info)
                    } else {
                        val mcmodinfo: Path = fs.getPath("mcmod.info")
                        if (Files.exists(mcmodinfo)) {
                            readMcModInfo(mcmodinfo, info)
                        } else {
                            info.displayName = info.path!!.fileName.toString()
                            info.version = ""
                        }
                    }
                }
            }
        } catch (e: IOException) {
            info.displayName = info.path!!.fileName.toString()
            info.version = ""
        }
    }

    private fun readFabricModJson(jsonPath: Path, info: EntryInfo) {
        val gson = Gson()
        Files.newBufferedReader(jsonPath).use { reader ->
            val jsonObj = gson.fromJson(reader, JsonObject::class.java)
            if (jsonObj.has("name")) {
                info.displayName = jsonObj.get("name").asString
            } else {
                info.displayName = info.path!!.fileName.toString()
            }
            if (jsonObj.has("version")) {
                info.version = jsonObj.get("version").asString
            } else {
                info.version = ""
            }
        }
    }

    private fun readMcModInfo(jsonPath: Path, info: EntryInfo) {
        val gson = Gson()
        Files.newBufferedReader(jsonPath).use { reader ->
            val jsonArray = gson.fromJson(reader, JsonArray::class.java)
            if (jsonArray.size() > 0 && jsonArray[0].isJsonObject) {
                val modObj = jsonArray[0].asJsonObject
                if (modObj.has("name")) {
                    info.displayName = modObj.get("name").asString
                } else {
                    info.displayName = info.path!!.fileName.toString()
                }
                if (modObj.has("version")) {
                    info.version = modObj.get("version").asString
                } else {
                    info.version = ""
                }
            } else {
                info.displayName = info.path!!.fileName.toString()
                info.version = ""
            }
        }
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        val panelHeight = this.height - 60
        val maxScroll = max(0, (entries?.size?.times(entryHeight) ?: 0) - panelHeight)
        targetOffset -= (verticalAmount * entryHeight * 2).toFloat()
        if (targetOffset < 0) targetOffset = 0f
        if (targetOffset > maxScroll) targetOffset = maxScroll.toFloat()
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val backButtonX = this.width - 60
        val backButtonY = 5
        val btnW = 50
        val btnH = 20
        val downloadButtonX = backButtonX - (btnW + 10)
        val downloadButtonY = 5
        if (mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH && button == 0) {
            mc.setScreen(parent)
            return true
        }
        if (mouseX >= downloadButtonX && mouseX <= downloadButtonX + btnW && mouseY >= downloadButtonY && mouseY <= downloadButtonY + btnH && button == 0) {
            mc.setScreen(PluginModManagerScreen(mc, this, serverInfo))
            return true
        }
        val listY = 50
        val relativeY = mouseY.toInt() - listY + smoothOffset.toInt()
        val index = relativeY / entryHeight
        entries?.let {
            if (index >= 0 && index < it.size) {
                val e = it[index]
                val y = listY + (index * entryHeight) - smoothOffset.toInt()
                if (e.isPlugin) {
                    val folderBtnX = this.width - 30
                    val folderBtnW = 20
                    val folderBtnH = 20
                    val folderBtnY = y + entryHeight - folderBtnH - 5
                    if (mouseX >= folderBtnX && mouseX <= folderBtnX + folderBtnW && mouseY >= folderBtnY && mouseY <= folderBtnY + folderBtnH && button == 0) {
                        val dir = Paths.get(serverInfo.path, "plugins", e.displayName)
                        mc.setScreen(object : FileExplorerScreen(mc, this, serverInfo) {
                            override fun init() {
                                super.init()
                                loadDirectory(dir)
                            }
                        })
                        return true
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        context.fill(0, 0, this.width, 30, lighterColor)
        drawInnerBorder(context, 0, 0, this.width, 30, borderColor)
        context.drawText(this.textRenderer, this.title, 10, 10, textColor, false)
        val backButtonX = this.width - 60
        val backButtonY = 5
        val btnW = 50
        val btnH = 20
        val hoveredBack =
            mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH
        val bgBack = if (hoveredBack) highlightColor else lighterColor
        context.fill(backButtonX, backButtonY, backButtonX + btnW, backButtonY + btnH, bgBack)
        drawInnerBorder(context, backButtonX, backButtonY, btnW, btnH, borderColor)
        val twb = textRenderer.getWidth("Back")
        val txb = backButtonX + (btnW - twb) / 2
        val ty = backButtonY + (btnH - textRenderer.fontHeight) / 2
        context.drawText(this.textRenderer, Text.literal("Back"), txb, ty, textColor, false)
        val downloadButtonX = backButtonX - (btnW + 10)
        val downloadButtonY = 5
        val hoveredDownload =
            mouseX >= downloadButtonX && mouseX <= downloadButtonX + btnW && mouseY >= downloadButtonY && mouseY <= downloadButtonY + btnH
        val bgDownload = if (hoveredDownload) highlightColor else lighterColor
        context.fill(downloadButtonX, downloadButtonY, downloadButtonX + btnW, downloadButtonY + btnH, bgDownload)
        drawInnerBorder(context, downloadButtonX, downloadButtonY, btnW, btnH, borderColor)
        val twd = textRenderer.getWidth("Download")
        val txd = downloadButtonX + (btnW - twd) / 2
        context.drawText(this.textRenderer, Text.literal("Download"), txd, ty, textColor, false)
        val listY = 50
        val panelHeight = this.height - 60
        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed
        context.enableScissor(0, listY, this.width, listY + panelHeight)
        entries?.let {
            for (i in it.indices) {
                val e = it[i]
                val y: Int = listY + (i * entryHeight) - smoothOffset.toInt()
                if (y + entryHeight < listY || y > listY + panelHeight) continue
                val hovered = mouseX >= 10 && mouseX <= this.width - 10 && mouseY >= y && mouseY <= y + entryHeight
                val bg = if (hovered) highlightColor else lighterColor
                context.fill(10, y, this.width - 10, y + entryHeight, bg)
                drawInnerBorder(context, 10, y, this.width - 20, entryHeight, borderColor)
                if (e.isPlugin) {
                    context.drawText(this.textRenderer, Text.literal(e.displayName), 15, y + 5, textColor, false)
                    context.drawText(
                        this.textRenderer,
                        Text.literal(if (e.version?.isEmpty() == true) "Unknown Version" else e.version),
                        15,
                        y + 20,
                        -0x555556,
                        false
                    )
                    val folderBtnX = this.width - 40
                    val folderBtnY = y + entryHeight - 25
                    val folderBtnW = 20
                    val folderBtnH = 20
                    val folderHover =
                        mouseX >= folderBtnX && mouseX <= folderBtnX + folderBtnW && mouseY >= folderBtnY && mouseY <= folderBtnY + folderBtnH
                    val fbg = if (folderHover) highlightColor else lighterColor
                    context.fill(folderBtnX, folderBtnY, folderBtnX + folderBtnW, folderBtnY + folderBtnH, fbg)
                    drawInnerBorder(context, folderBtnX, folderBtnY, folderBtnW, folderBtnH, borderColor)
                    context.drawText(
                        this.textRenderer,
                        Text.literal("§6🗁"),
                        folderBtnX + 4,
                        folderBtnY + 4,
                        textColor,
                        false
                    )
                } else {
                    context.drawText(this.textRenderer, Text.literal(e.displayName), 15, y + 5, textColor, false)
                }
            }
        }
        context.disableScissor()
        if (smoothOffset > 0) {
            context.fillGradient(0, listY, this.width, listY + 10, -0x80000000, 0x00000000)
        }
        if (smoothOffset < max(0, (entries?.size?.times(entryHeight) ?: 0) - panelHeight)) {
            context.fillGradient(0, listY + panelHeight - 10, this.width, listY + panelHeight, 0x00000000, -0x80000000)
        }
    }

    private fun drawInnerBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, c: Int) {
        context.fill(x, y, x + w, y + 1, c)
        context.fill(x, y + h - 1, x + w, y + h, c)
        context.fill(x, y, x + 1, y + h, c)
        context.fill(x + w - 1, y, x + w, y + h, c)
    }

    override fun close() {
        mc.setScreen(parent)
    }

    private class EntryInfo {
        var path: Path? = null
        var displayName: String? = null
        var version: String? = null
        var isPlugin: Boolean = false
    }
}
