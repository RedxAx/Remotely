package redxax.oxy.servers

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import redxax.oxy.NotificationManager
import redxax.oxy.RemotelyClient
import redxax.oxy.ServerTerminalInstance
import redxax.oxy.explorer.FileExplorerScreen
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import kotlin.math.max
import kotlin.math.min

class ServerTerminalScreen(
    private val minecraftClient: MinecraftClient,
    private val remotelyClient: RemotelyClient,
    private val serverInfo: ServerInfo
) : Screen(Text.literal("${serverInfo.name} - Server Screen")) {

    private val baseColor = -0xe7e7e8
    private val lighterColor = -0xddddde
    private val borderColor = -0xcccccd
    private val highlightColor = -0xbbbbbc
    private val textColor = -0x1

    private var buttonX = 0
    private var buttonY = 0
    private var explorerButtonX = 0
    private var explorerButtonY = 0
    private var pluginButtonX = 0
    private var pluginButtonY = 0
    private val buttonW = 60
    private val buttonH = 20
    private var terminalScale = 1.0f
    private val topBarHeight = 30

    override fun init() {
        super.init()
    }

    override fun renderBackground(context: DrawContext?, mouseX: Int, mouseY: Int, delta: Float) {
        context?.fillGradient(0, 0, this.width, this.height, baseColor, baseColor)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        NotificationManager.updateAll(delta)
        NotificationManager.renderAll(context)
        super.render(context, mouseX, mouseY, delta)
        context.fill(0, 0, this.width, topBarHeight, lighterColor)
        drawInnerBorder(context, 0, 0, this.width, topBarHeight, borderColor)
        val stateText = when (serverInfo.state) {
            ServerState.RUNNING -> "Running"
            ServerState.STARTING -> "Starting"
            ServerState.STOPPED -> "Stopped"
            ServerState.CRASHED -> "Crashed"
            else -> "Unknown"
        }
        val hostStatus = if (serverInfo.isRemote && serverInfo.remoteHost != null) {
            val connected = (serverInfo.remoteSSHManager != null && serverInfo.remoteSSHManager!!.isSSH)
            if (connected) "Host: Connected" else "Host: Disconnected"
        } else {
            "Local Host"
        }
        val titleText = "${serverInfo.name} - $stateText | $hostStatus"
        context.drawText(minecraftClient.textRenderer, Text.literal(titleText), 10, 10, textColor, false)
        buttonX = this.width - buttonW - 10
        buttonY = 5
        val buttonLabel = if (serverInfo.state == ServerState.RUNNING || serverInfo.state == ServerState.STARTING) {
            "Stop"
        } else {
            "Start"
        }
        val buttonHovered = mouseX in buttonX..(buttonX + buttonW) && mouseY in buttonY..(buttonY + buttonH)
        drawButton(context, buttonX, buttonY, buttonLabel, buttonHovered)
        explorerButtonX = buttonX - (buttonW + 10)
        explorerButtonY = 5
        val explorerHovered = mouseX in explorerButtonX..(explorerButtonX + buttonW) &&
                mouseY in explorerButtonY..(explorerButtonY + buttonH)
        drawButton(context, explorerButtonX, explorerButtonY, "Explorer", explorerHovered)
        val isProxy = listOf("velocity", "waterfall", "bungeecord").contains(serverInfo.type.lowercase(Locale.getDefault()))
        if (!isProxy) {
            pluginButtonX = explorerButtonX - (buttonW + 10)
            pluginButtonY = 5
            val pluginLabel = if (serverInfo.type.equals("paper", ignoreCase = true)
                || serverInfo.type.equals("spigot", ignoreCase = true)
                || serverInfo.type.equals("velocity", ignoreCase = true)
                || serverInfo.type.equals("waterfall", ignoreCase = true)
                || serverInfo.type.equals("bungeecord", ignoreCase = true)
            ) "Plugins" else "Mods"
            val pluginHovered = mouseX in pluginButtonX..(pluginButtonX + buttonW) &&
                    mouseY in pluginButtonY..(pluginButtonY + buttonH)
            drawButton(context, pluginButtonX, pluginButtonY, pluginLabel, pluginHovered)
        }
        val terminalOffsetY = topBarHeight + 5
        val terminalAvailableHeight = this.height - topBarHeight - 10
        serverInfo.terminal?.render(context, this.width - 10, terminalAvailableHeight, terminalScale)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            if (mouseX in buttonX.toDouble()..(buttonX + buttonW).toDouble()
                && mouseY in buttonY.toDouble()..(buttonY + buttonH).toDouble()
            ) {
                if (serverInfo.state == ServerState.RUNNING || serverInfo.state == ServerState.STARTING) {
                    stopServer()
                } else {
                    startServer()
                }
                return true
            }
            if (mouseX in explorerButtonX.toDouble()..(explorerButtonX + buttonW).toDouble()
                && mouseY in explorerButtonY.toDouble()..(explorerButtonY + buttonH).toDouble()
            ) {
                minecraftClient.setScreen(FileExplorerScreen(minecraftClient, this, serverInfo))
                return true
            }
            val isProxy = listOf("velocity", "waterfall", "bungeecord").contains(serverInfo.type.lowercase(Locale.getDefault()))
            if (!isProxy) {
                if (mouseX in pluginButtonX.toDouble()..(pluginButtonX + buttonW).toDouble()
                    && mouseY in pluginButtonY.toDouble()..(pluginButtonY + buttonH).toDouble()
                ) {
                    minecraftClient.setScreen(PluginModListScreen(minecraftClient, this, serverInfo))
                    return true
                }
            }
        }
        serverInfo.terminal?.mouseClicked(mouseX, mouseY, button)
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (serverInfo.terminal?.mouseReleased(mouseX, mouseY, button) == true) {
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (serverInfo.terminal?.mouseDragged(mouseX, mouseY, button) == true) {
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        serverInfo.terminal?.let {
            val scaledHeight = this.height - (topBarHeight + 10)
            it.scroll(if (verticalAmount > 0) 1 else -1, scaledHeight)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_EQUAL && (modifiers and GLFW.GLFW_MOD_CONTROL) != 0) {
            terminalScale = min((terminalScale + 0.1f).toDouble(), 2.0).toFloat()
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS && (modifiers and GLFW.GLFW_MOD_CONTROL) != 0) {
            terminalScale = max((terminalScale - 0.1f).toDouble(), 0.1).toFloat()
            return true
        }
        if (serverInfo.terminal?.keyPressed(keyCode, modifiers) == true) {
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        return serverInfo.terminal?.charTyped(chr, modifiers) ?: super.charTyped(chr, modifiers)
    }

    private fun startServer() {
        if (serverInfo.state == ServerState.STOPPED || serverInfo.state == ServerState.CRASHED) {
            if (serverInfo.terminal == null || serverInfo.terminal !is ServerTerminalInstance) {
                val srvTerminal = ServerTerminalInstance(
                    minecraftClient, null, UUID.randomUUID(),
                    serverInfo
                )
                srvTerminal.isServerTerminal = true
                srvTerminal.serverName = serverInfo.name
                srvTerminal.serverJarPath = Paths.get(serverInfo.path, "server.jar").toString().replace("\\", "/")
                serverInfo.terminal = srvTerminal
            }
            NotificationManager.addNotification("Starting server...", NotificationManager.Type.INFO)
            if (serverInfo.terminal is ServerTerminalInstance) {
                val st = serverInfo.terminal as ServerTerminalInstance
                st.clearOutput()
                st.launchServerProcess()
            }
            serverInfo.state = ServerState.STARTING
        }
    }

    private fun stopServer() {
        if ((serverInfo.state == ServerState.RUNNING || serverInfo.state == ServerState.STARTING) && serverInfo.terminal != null) {
            NotificationManager.addNotification("Stopping server...", NotificationManager.Type.INFO)
            try {
                val sti = serverInfo.terminal as ServerTerminalInstance
                sti.processManager?.writer?.let {
                    it.write("stop\n")
                    it.flush()
                }
            } catch (_: IOException) {
            }
            serverInfo.state = ServerState.STOPPED
        }
    }

    private fun drawButton(context: DrawContext, x: Int, y: Int, text: String, hovered: Boolean) {
        val bg = if (hovered) highlightColor else lighterColor
        context.fill(x, y, x + buttonW, y + buttonH, bg)
        drawInnerBorder(context, x, y, buttonW, buttonH, borderColor)
        val tw = minecraftClient.textRenderer.getWidth(text)
        val tx = x + (buttonW - tw) / 2
        val ty = y + (buttonH - minecraftClient.textRenderer.fontHeight) / 2
        context.drawText(minecraftClient.textRenderer, Text.literal(text), tx, ty, textColor, false)
    }

    private fun drawInnerBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, c: Int) {
        context.fill(x, y, x + w, y + 1, c)
        context.fill(x, y + h - 1, x + w, y + h, c)
        context.fill(x, y, x + 1, y + h, c)
        context.fill(x + w - 1, y, x + w, y + h, c)
    }
}
