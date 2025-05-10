package redxax.oxy.remotely.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class Shortcut {

    private static final MinecraftClient minecraftClient = MinecraftClient.getInstance();

    public static String humanReadableKey(String keyName) {
        if (keyName == null) return "";
        if (keyName.startsWith("key.keyboard.")) {
            String k = keyName.substring("key.keyboard.".length());
            return switch (k) {
                case "left.control", "right.control" -> "CTRL";
                case "left.shift", "right.shift" -> "SHIFT";
                case "left.alt", "right.alt" -> "ALT";
                default -> k.toUpperCase();
            };
        }
        return keyName.toUpperCase();
    }

    private boolean checkShortcut(String shortcut) {
        if (shortcut.isEmpty()) return false;
        String[] keys = shortcut.split("\\+");
        for (String k : keys) {
            if (!isKeyHeld(k)) return false;
        }
        return true;
    }

    public static boolean isKeyHeld(String k) {
        k = k.toLowerCase();
        switch (k) {
            case "ctrl" -> {
                return InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL);
            }
            case "shift" -> {
                return InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT);
            }
            case "alt" -> {
                return InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_ALT) || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_ALT);
            }
        }
        for (int code = 0; code < 400; code++) {
            InputUtil.Key testKey = InputUtil.fromKeyCode(code, 0);
            if (testKey != InputUtil.UNKNOWN_KEY && testKey.getTranslationKey().toLowerCase().endsWith("." + k.toLowerCase())) {
                if (InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), code)) return true;
            }
        }
        return false;
    }

}
