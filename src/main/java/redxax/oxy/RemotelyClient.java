package redxax.oxy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.nio.file.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RemotelyClient implements ClientModInitializer {

    private KeyBinding openTerminalKeyBinding;
    private MultiTerminalScreen multiTerminalScreen;
    private static final Path TERMINAL_LOG_DIR = Paths.get(System.getProperty("user.dir"), "remotely", "logs");
    private static final Path SNIPPETS_FILE = Paths.get(System.getProperty("user.dir"), "remotely", "snippets", "snippets.json");
    private static final Gson GSON = new Gson();
    List<TerminalInstance> terminals = new ArrayList<>();
    List<String> tabNames = new ArrayList<>();
    int activeTerminalIndex = 0;
    float scale = 1.0f;
    int snippetPanelWidth = 150;
    boolean showSnippetsPanel = false;
    public static List<CommandSnippet> globalSnippets = new ArrayList<>();
    public static RemotelyClient INSTANCE;


    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        System.out.println("Remotely mod initialized on the client.");
        loadSnippets();
        openTerminalKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Open Terminal",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z, "Remotely"
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

    public void openMultiTerminalGUI(MinecraftClient client) {
        if (multiTerminalScreen == null || !client.isWindowFocused()) {
            multiTerminalScreen = new MultiTerminalScreen(client, this, terminals, tabNames);
            if (terminals.isEmpty()) {
                loadSavedTerminals();
            }
            client.setScreen(multiTerminalScreen);
        } else {
            multiTerminalScreen = new MultiTerminalScreen(client, this, terminals, tabNames);
            if (terminals.isEmpty()) {
                loadSavedTerminals();
            }
            client.setScreen(multiTerminalScreen);
        }
    }

    private void loadSavedTerminals() {
        if (Files.exists(TERMINAL_LOG_DIR) && Files.isDirectory(TERMINAL_LOG_DIR) && terminals.isEmpty()) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(TERMINAL_LOG_DIR, "*.log")) {
                for (Path entry : stream) {
                    String fileName = entry.getFileName().toString();
                    String tabName = fileName.substring(0, fileName.length() - 4);
                    TerminalInstance terminal = new TerminalInstance(MinecraftClient.getInstance(), multiTerminalScreen, UUID.randomUUID());
                    terminal.loadTerminalOutput(entry);
                    terminals.add(terminal);
                    tabNames.add(tabName);
                }
                if (!terminals.isEmpty()) {
                    multiTerminalScreen.activeTerminalIndex = activeTerminalIndex;
                }
            } catch (IOException e) {
                assert MinecraftClient.getInstance().player != null;
                MinecraftClient.getInstance().player.sendMessage(Text.literal("Failed to load saved terminals."), false);
            }
        }
    }

    public void shutdownAllTerminals() {
        for (TerminalInstance terminal : terminals) {
            terminal.shutdown();
        }
        terminals.clear();
        tabNames.clear();
        if (multiTerminalScreen != null) {
            multiTerminalScreen.shutdownAllTerminals();
            multiTerminalScreen = null;
        }
        saveSnippets();
        try {
            if (Files.exists(TERMINAL_LOG_DIR) && Files.isDirectory(TERMINAL_LOG_DIR)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(TERMINAL_LOG_DIR)) {
                    for (Path entry : stream) {
                        Files.deleteIfExists(entry);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to clear terminal log files.");
        }
    }

    public void onMultiTerminalScreenClosed() {
        multiTerminalScreen = null;
    }

    public static class CommandSnippet {
        public String name;
        public String commands;
        public String shortcut;
        public CommandSnippet(String name, String commands, String shortcut) {
            this.name = name;
            this.commands = commands;
            this.shortcut = shortcut;
        }
    }

    public void saveSnippets() {
        try {
            if (!Files.exists(SNIPPETS_FILE.getParent())) {
                Files.createDirectories(SNIPPETS_FILE.getParent());
            }
            String json = GSON.toJson(globalSnippets);
            Files.write(SNIPPETS_FILE, json.getBytes());
        } catch (IOException e) {
            System.out.println("Failed to save snippets: " + e.getMessage());
        }
    }

    public void loadSnippets() {
        if (Files.exists(SNIPPETS_FILE)) {
            try {
                String json = new String(Files.readAllBytes(SNIPPETS_FILE));
                globalSnippets = GSON.fromJson(json, new TypeToken<List<CommandSnippet>>(){}.getType());
                if (globalSnippets == null) globalSnippets = new ArrayList<>();
            } catch (IOException e) {
                System.out.println("Failed to load snippets: " + e.getMessage());
            }
        }
    }
}
