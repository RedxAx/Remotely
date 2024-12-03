package redxax.oxy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;

public class RemotelyClient implements ClientModInitializer {

    private KeyBinding openTerminalKeyBinding;
    private MultiTerminalScreen multiTerminalScreen;

    private static final Path LOG_DIR = Paths.get(System.getProperty("user.dir"), "remotely_logs");

    @Override
    public void onInitializeClient() {
        System.out.println("Remotely mod initialized on the client.");

        openTerminalKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.remotely.open_terminal",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "category.remotely"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client != null && client.player != null) {
                if (openTerminalKeyBinding.wasPressed()) {
                    openMultiTerminalGUI(client);
                }
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAllTerminals));
    }

    private void openMultiTerminalGUI(MinecraftClient client) {
        if (multiTerminalScreen == null || !client.isWindowFocused()) {
            multiTerminalScreen = new MultiTerminalScreen(client, this);
            client.setScreen(multiTerminalScreen);
            loadSavedTerminals();
            if (multiTerminalScreen.terminals.isEmpty()) {
                multiTerminalScreen.addNewTerminal();
            }
            multiTerminalScreen.refreshTabButtons();
        } else {
            multiTerminalScreen = new MultiTerminalScreen(client, this);
            client.setScreen(multiTerminalScreen);
            loadSavedTerminals();
            if (multiTerminalScreen.terminals.isEmpty()) {
                multiTerminalScreen.addNewTerminal();
            }
            multiTerminalScreen.refreshTabButtons();
        }
    }

    private void loadSavedTerminals() {
        if (Files.exists(LOG_DIR) && Files.isDirectory(LOG_DIR)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(LOG_DIR, "*.log")) {
                for (Path entry : stream) {
                    String fileName = entry.getFileName().toString();
                    String tabName = fileName.substring(0, fileName.length() - 4);
                    TerminalInstance terminal = new TerminalInstance(MinecraftClient.getInstance(), multiTerminalScreen, UUID.randomUUID());
                    terminal.loadTerminalOutput(entry);
                    multiTerminalScreen.terminals.add(terminal);
                    multiTerminalScreen.tabNames.add(tabName);
                }
                if (!multiTerminalScreen.terminals.isEmpty()) {
                    multiTerminalScreen.activeTerminalIndex = multiTerminalScreen.terminals.size() - 1;
                }
            } catch (IOException e) {
                MinecraftClient.getInstance().player.sendMessage(Text.literal("Failed to load saved terminals."), false);
            }
        }
    }

    public void shutdownAllTerminals() {
        if (multiTerminalScreen != null) {
            multiTerminalScreen.shutdownAllTerminals();
            try {
                if (Files.exists(LOG_DIR) && Files.isDirectory(LOG_DIR)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(LOG_DIR)) {
                        for (Path entry : stream) {
                            Files.deleteIfExists(entry);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Failed to clear terminal log files.");
            }
            multiTerminalScreen = null;
        }
    }

    public void onMultiTerminalScreenClosed() {
        multiTerminalScreen = null;
    }
}
