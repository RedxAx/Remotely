package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ServerConfigurationScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private TextFieldWidget ramField;
    private TextFieldWidget maxPlayersField;
    private TextFieldWidget onlineModeField;
    private TextFieldWidget portField;
    private ButtonWidget applyButton;
    private ButtonWidget backButton;
    private float ramAmount = 2.0f;   // Default in GB
    private float maxPlayers = 10.0f;
    private boolean onlineMode = true;
    private int serverPort = 25565;

    public ServerConfigurationScreen(MinecraftClient mc, Screen parent, ServerInfo info) {
        super(Text.literal("Server Configuration"));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
    }

    @Override
    protected void init() {
        super.init();
        int y = 50;
        ramField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, y, 200, 20, Text.literal("RAM (GB)"));
        ramField.setText(String.valueOf(ramAmount));
        this.addDrawableChild(ramField);

        y += 30;
        maxPlayersField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, y, 200, 20, Text.literal("Max Players"));
        maxPlayersField.setText(String.valueOf(maxPlayers));
        this.addDrawableChild(maxPlayersField);

        y += 30;
        onlineModeField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, y, 200, 20, Text.literal("Online Mode (true/false)"));
        onlineModeField.setText(String.valueOf(onlineMode));
        this.addDrawableChild(onlineModeField);

        y += 40;
        portField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, y, 200, 20, Text.literal("Server Port"));
        portField.setText(String.valueOf(serverPort));
        this.addDrawableChild(portField);

        y += 40;
        applyButton = ButtonWidget.builder(Text.literal("Apply"), (button) -> {
            applyChanges();
        }).dimensions(this.width / 2 - 104, y, 80, 20).build();
        this.addDrawableChild(applyButton);

        backButton = ButtonWidget.builder(Text.literal("Back"), (button) -> {
            this.minecraftClient.setScreen(parent);
        }).dimensions(this.width / 2 + 24, y, 80, 20).build();
        this.addDrawableChild(backButton);
    }

    private void applyChanges() {
        try {
            float gb = Float.parseFloat(ramField.getText());
            int mp = Integer.parseInt(maxPlayersField.getText());
            boolean onMode = Boolean.parseBoolean(onlineModeField.getText());
            int port = Integer.parseInt(portField.getText());

            serverInfo.ramGb = gb;
            serverInfo.maxPlayers = mp;
            serverInfo.onlineMode = onMode;
            serverInfo.port = port;

            Path serverProps = serverInfo.getServerPropertiesPath();
            if (Files.exists(serverProps)) {
                // Load existing server.properties lines
                java.util.List<String> lines = Files.readAllLines(serverProps);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.startsWith("max-players=")) {
                        lines.set(i, "max-players=" + mp);
                    } else if (line.startsWith("server-port=")) {
                        lines.set(i, "server-port=" + port);
                    } else if (line.startsWith("online-mode=")) {
                        lines.set(i, "online-mode=" + onMode);
                    }
                }
                Files.write(serverProps, lines);
            }
            this.minecraftClient.setScreen(parent);
        } catch (IOException e) {
            serverInfo.terminal.appendOutput("Failed to update server.properties: " + e.getMessage() + "\n");
        } catch (NumberFormatException e) {
            serverInfo.terminal.appendOutput("Invalid input. Please enter valid numbers for RAM, Max Players, and Port, and true/false for Online Mode.\n");
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawText(this.textRenderer, Text.literal("Server Configuration"), this.width / 2 - 80, 20, 0xFFFFFF, false);
    }
}