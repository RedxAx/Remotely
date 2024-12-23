package redxax.oxy.servers

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import redxax.oxy.Notification
import redxax.oxy.RemotelyClient
import redxax.oxy.ServerTerminalInstance
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import kotlin.math.max
import kotlin.math.min

class ServerTerminalScreen(
    private val minecraftClient: MinecraftClient,
    private val remotelyClient: RemotelyClient,
    private val serverInfo: ServerInfo
) :
    Screen(Text.literal(serverInfo.name + " - Server Screen")) {
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

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        Notification.updateAll(delta)
        Notification.renderAll(context)
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor)
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
        val titleText = serverInfo.name + " - " + stateText
        context.drawText(minecraftClient.textRenderer, Text.literal(titleText), 10, 10, textColor, false)

        buttonX = this.width - buttonW - 10
        buttonY = 5
        val buttonLabel =
            if (serverInfo.state == ServerState.RUNNING || serverInfo.state == ServerState.STARTING) "Stop" else "Start"
        val buttonHovered =
            mouseX >= buttonX && mouseX <= buttonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH
        drawButton(context, buttonX, buttonY, buttonLabel, buttonHovered)

        val configButtonX = buttonX - (buttonW + 10)
        val configButtonY = 5

        explorerButtonX = configButtonX - (buttonW + 10)
        explorerButtonY = 5
        val explorerHovered =
            mouseX >= explorerButtonX && mouseX <= explorerButtonX + buttonW && mouseY >= explorerButtonY && mouseY <= explorerButtonY + buttonH
        drawButton(context, explorerButtonX, explorerButtonY, "Explorer", explorerHovered)

        pluginButtonX = explorerButtonX - (buttonW + 10)
        pluginButtonY = 5
        val pluginLabel = if ((serverInfo.type.equals("paper", ignoreCase = true) || serverInfo.type.equals(
                "spigot",
                ignoreCase = true
            ))
        ) "Plugins" else "Mods"
        val pluginHovered =
            mouseX >= pluginButtonX && mouseX <= pluginButtonX + buttonW && mouseY >= pluginButtonY && mouseY <= pluginButtonY + buttonH
        drawButton(context, pluginButtonX, pluginButtonY, pluginLabel, pluginHovered)

        val terminalOffsetY = topBarHeight + 5
        val terminalAvailableHeight = this.height - topBarHeight - 10
        if (serverInfo.terminal != null) {
            serverInfo.terminal.render(context, this.width - 10, terminalAvailableHeight, terminalScale)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            if (mouseX >= buttonX && mouseX <= buttonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH) {
                if (serverInfo.state == ServerState.RUNNING || serverInfo.state == ServerState.STARTING) {
                    stopServer()
                } else {
                    startServer()
                }
                return true
            }
            if (mouseX >= explorerButtonX && mouseX <= explorerButtonX + buttonW && mouseY >= explorerButtonY && mouseY <= explorerButtonY + buttonH) {
                minecraftClient.setScreen(FileExplorerScreen(minecraftClient, this, serverInfo))
                return true
            }
            if (mouseX >= pluginButtonX && mouseX <= pluginButtonX + buttonW && mouseY >= pluginButtonY && mouseY <= pluginButtonY + buttonH) {
                minecraftClient.setScreen(PluginModListScreen(minecraftClient, this, serverInfo))
                return true
            }
        }
        if (serverInfo.terminal != null) {
            serverInfo.terminal.mouseClicked(mouseX, mouseY, button)
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (serverInfo.terminal != null) {
            if (serverInfo.terminal.mouseReleased(mouseX, mouseY, button)) {
                return true
            }
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (serverInfo.terminal != null) {
            if (serverInfo.terminal.mouseDragged(mouseX, mouseY, button)) {
                return true
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        if (serverInfo.terminal != null) {
            val scaledHeight = this.height - (topBarHeight + 10)
            serverInfo.terminal.scroll(if (verticalAmount > 0) 1 else -1, scaledHeight)
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
        if (serverInfo.terminal != null) {
            if (serverInfo.terminal.keyPressed(keyCode, modifiers)) {
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, keyCode: Int): Boolean {
        return super.charTyped(chr, keyCode)
    }

    private fun startServer() {
        if (serverInfo.state == ServerState.STOPPED || serverInfo.state == ServerState.CRASHED) {
            if (serverInfo.terminal == null || serverInfo.terminal !is ServerTerminalInstance) {
                val srvTerminal = ServerTerminalInstance(minecraftClient, null, UUID.randomUUID(), serverInfo)
                srvTerminal.isServerTerminal = true
                srvTerminal.serverName = serverInfo.name
                srvTerminal.serverJarPath = Paths.get(serverInfo.path, "server.jar").toString().replace("\\", "/")
                serverInfo.terminal = srvTerminal
            }
            val notification = Notification.Builder("Starting server...", Notification.Type.INFO).build()
            if (serverInfo.terminal is ServerTerminalInstance) {
                (serverInfo.terminal as ServerTerminalInstance).clearOutput()
                serverInfo.terminal.launchServerProcess()
            }
            serverInfo.state = ServerState.STARTING
        }
    }

    private fun stopServer() {
        if ((serverInfo.state == ServerState.RUNNING || serverInfo.state == ServerState.STARTING) && serverInfo.terminal != null) {
            val notification = Notification.Builder("Stopping server...", Notification.Type.INFO).build()
            try {
                val sti = serverInfo.terminal as ServerTerminalInstance
                if (serverInfo.terminal is ServerTerminalInstance && sti.processManager != null && sti.processManager.getWriter() != null) {
                    sti.processManager.getWriter().write("stop\n")
                    sti.processManager.getWriter().flush()
                }
            } catch (ignored: IOException) {
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
